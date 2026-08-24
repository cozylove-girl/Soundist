/*
 * soundist_audio.c
 *
 * Soundist 原生音频核心 —— miniaudio 迁移（阶段 A：安全加固版）。
 *
 * 阶段 1：生命周期 + 命令队列骨架 + 可验证最小功能（版本字符串）。
 * 阶段 2：环境声循环混音。ma_device + SoundistTrack[64]，回调里做
 *         gain/pan（一阶 ramp + equal-power pan）混合，循环播放。
 * 阶段 3：采样器（预解码 one-shot）。SoundistSample[128] + 触发命令。
 * 阶段 4：生成调度 Voice 骨架。SoundistVoice[64] 正弦合成（非 18 音色迁移）。
 *
 * ---- 阶段 A 安全加固（本次改动）----
 * 1. native-owned PCM：addTrack/addSample 由 JNI（控制线程）把 jfloatArray 复制进
 *    calloc 的堆内存，此后 Kotlin 不持有任何底层内存引用；回调只读该堆指针。
 * 2. 退休确认（retire-ack）：回调内绝不 free。回调在消费 ADD/REMOVE 时把被替换/被删除
 *    的旧指针推入 retire 环形缓冲（SPSC，回调生产、控制线程消费），控制线程在后续任意
 *    JNI 调用里 drain 并 free。停止/释放时 ma_device_stop 保证回调不再运行，控制线程
 *    可直接 free 全部剩余指针。
 * 3. generation id：每个 track/sample 槽带 generation，ADD 时 +1。REMOVE 命令携带其
 *    签发时的 generation，回调仅在槽当前 generation 一致时才退休——防止「旧 remove 删
 *    新资源」。同时控制线程用独立 shadow 数组精确计算每次要退休的指针（不在退休时读
 *    回调写的槽字段），从根源消除 use-after-free。
 * 4. 生命周期状态机：UNINITIALIZED→INITIALIZED→STARTING→RUNNING↔STOPPING，终止态
 *    FAILED/RELEASED。init/start/stop/release 幂等；release 后禁止 add/remove/set/trigger。
 * 5. 命令队列：固定容量 SPSC 无锁。生产者唯一（NativeAudioCore 专用控制 HandlerThread
 *    串行投递）；满时返回明确错误码，绝不静默丢关键 remove/stop。
 * 6. 音频格式：track/sample 在控制线程先统一到设备格式（设备采样率 × 设备声道，交错
 *    float32），回调只读设备格式交错 PCM，真实保留 mono/stereo（不再只读第一声道）。
 *
 * ---- 实时线程安全设计 ----
 * 所有共享状态（tracks/samples/voices）只在音频线程（data callback）内修改。
 * 控制线程（JNI 调用方）通过 SPSC 命令队列投递，回调开头一次性消费。控制线程额外
 * 维护 control_tracks/control_samples shadow（仅供控制线程读写，回调不碰），用于精确
 * 计算 retire 指针与 generation。回调内禁止：文件 IO、解码、内存分配/释放、等 mutex、
 * sleep、日志拼接、创建集合/字符串。当前回调零分配、零锁、零日志、零 JNI。
 *
 * 包路径：com.soundist.app.NativeAudioCore。
 */

#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdatomic.h>
#include <math.h>

#include "miniaudio.h"

/* ---------- 常量 ---------- */
#define SOUNDIST_CMD_CAPACITY        256
#define SOUNDIST_CMD_MASK            (SOUNDIST_CMD_CAPACITY - 1)
/* 退休队列容量必须 ≥ 命令队列容量：每个命令在回调里最多退休 1 个指针，且控制线程在每次
 * 投递命令前都会先 drain 退休队列，因此回调一次最多（= 命令容量）个退休指针在途。
 * 取 2× 留出安全余量，结合下面的 _Static_assert 静态约束，证明退休队列永不溢出。 */
#define SOUNDIST_RETIRE_CAPACITY     512
#define SOUNDIST_RETIRE_MASK         (SOUNDIST_RETIRE_CAPACITY - 1)
#define SOUNDIST_MAX_TRACKS          64
#define SOUNDIST_MAX_SAMPLES         128
#define SOUNDIST_MAX_SAMPLE_VOICES   32
#define SOUNDIST_MAX_VOICES          64
#define SOUNDIST_PI                  3.14159265358979323846
/* 一阶指数 ramp 时间常数（秒）：gain/pan/master 增益以固定时间常数逼近目标，与 sampleRate/frameCount 无关。
 * 替代旧的固定 0.08 因子（后者随 block 尺寸/sampleRate 变化，违反「不依赖固定 512 帧」要求）。 */
#define SOUNDIST_RAMP_TAU_SECONDS    0.02f
#define SOUNDIST_MASTER_RAMP_TAU_SECONDS 0.012f
/* MasterChain：DC 阻断截止频率（Hz）。系数按设备采样率计算 R=exp(-2π·cutoff/sampleRate)，
 * 不用固定 0.995（0.995 在 48k 下截止约 38Hz，会错误削弱雷声/水流/列车/低频 drone）。 */
#define SOUNDIST_DC_CUTOFF_HZ        2.0f
/* 单轨解码帧数上限（约 2 分钟 @48kHz），防异常超长文件把内存打爆。 */
#define SOUNDIST_MAX_DECODED_FRAMES  5760000u

/* 释放路径去重数组上限：槽(音频+shadow) + 采样声部持有的 PCM + 队列(arg1/retire) + 退休队列，取宽松上界。
 * 采样声部最多 32 个，各自可能持有已脱离 slot 的 PCM 指针（删除/替换时），必须计入，否则越界写栈。 */
#define SOUNDIST_MAX_OWNED \
    (2 * (SOUNDIST_MAX_TRACKS + SOUNDIST_MAX_SAMPLES) + SOUNDIST_MAX_SAMPLE_VOICES + 2 * SOUNDIST_CMD_CAPACITY + SOUNDIST_RETIRE_CAPACITY)

/* 静态约束：退休队列容量不可能小于命令队列容量（否则回调消费命令时可能溢出）。 */
_Static_assert(SOUNDIST_RETIRE_CAPACITY >= SOUNDIST_CMD_CAPACITY, "retire capacity must be >= command capacity");

/* ---------- 生命周期状态机 ---------- */
typedef enum SoundistState {
    SOUNDIST_STATE_UNINITIALIZED = 0,
    SOUNDIST_STATE_INITIALIZED   = 1,
    SOUNDIST_STATE_STARTING      = 2,
    SOUNDIST_STATE_RUNNING       = 3,
    SOUNDIST_STATE_STOPPING      = 4,
    SOUNDIST_STATE_RELEASED      = 5,
    SOUNDIST_STATE_FAILED        = 6,
} SoundistState;

/* ---------- 命令 ---------- */

typedef enum SoundistCommandType {
    SOUNDIST_CMD_NONE           = 0,
    SOUNDIST_CMD_SET_GAIN       = 1,   /* arg0=轨道, arg1=float bits */
    SOUNDIST_CMD_SET_PAN        = 2,   /* arg0=轨道, arg1=float bits */
    SOUNDIST_CMD_SET_PLAYING    = 3,   /* arg0=轨道, arg1=0/1 */
    SOUNDIST_CMD_ADD_TRACK      = 4,   /* arg0=id, arg1=pcm*, arg2=frames, arg3=channels, arg4=generation, retire=旧pcm */
    SOUNDIST_CMD_REMOVE_TRACK   = 5,   /* arg0=id, arg4=generation, retire=待退休pcm */
    SOUNDIST_CMD_TRIGGER_SAMPLE = 6,   /* arg0=sampleIndex, arg1=频率bits */
    SOUNDIST_CMD_REMOVE_SAMPLE  = 7,   /* arg0=sampleIndex, arg4=generation, retire=待退休pcm */
    SOUNDIST_CMD_TRIGGER_VOICE  = 8,   /* arg1=频率bits, arg2=durationFrames, f0=gain, f1=pan */
    SOUNDIST_CMD_ADD_SAMPLE     = 9,   /* arg0=index, arg1=pcm*, arg2=frames, arg3=channels, arg4=generation, retire=旧pcm, f0=sampleRate, f1=baseFreq */
    SOUNDIST_CMD_SET_MASTER_GAIN = 10, /* arg1=float bits（master×ambient×duck×mute，0=静音），回调内一阶 ramp */
} SoundistCommandType;

typedef struct SoundistCommand {
    int32_t  type;
    int32_t  arg0;      /* 轨道 id / 采样槽下标 */
    int64_t  arg1;      /* pcm 指针 / float bits（int32 存低 32 位） */
    int64_t  arg2;      /* frames / durationFrames */
    int32_t  arg3;      /* channels */
    uint32_t arg4;      /* generation */
    int64_t  retire;    /* 待退休（free）的旧 pcm 指针，0 = 无 */
    float    f0;        /* 通用 float：sampleRate / voice gain */
    float    f1;        /* 通用 float：baseFreq / voice pan */
} SoundistCommand;

/* ---------- 数据结构 ---------- */

/* 环境声循环轨道。回调只写（经命令），控制线程只读 shadow，绝不直接读本结构的 pcm 算退休。 */
typedef struct SoundistTrack {
    int         active;
    int         id;
    uint32_t    generation;
    float*      pcm;     /* native-owned，设备格式交错 float32，回调只读 */
    uint64_t    frames;
    uint64_t    pos;     /* 当前播放位置（帧），到 frames 归 0 循环 */
    uint32_t    channels;
    float       gain;    /* 目标 */
    float       gain_cur;/* 当前（ramp） */
    float       pan;     /* 目标，-1..1 */
    float       pan_cur; /* 当前（ramp） */
    int         playing;
} SoundistTrack;

/* 采样器：预解码 one-shot 源（native-owned PCM）。
 * deleting=1 时阻止新触发。PCM 所有权：正常由采样槽持有；删除/替换时转移到「退休表」（retiring）并按
 * 活跃 voice 数做真实引用计数，最后一个 voice 结束时退休一次——杜绝多 voice 同采样时的 double-free。 */
typedef struct SoundistSample {
    int         active;
    int         deleting;
    uint32_t    generation;
    float*      pcm;
    uint64_t    frames;
    uint32_t    channels;
    uint32_t    sample_rate;   /* 设备采样率（Kotlin 已统一到设备格式） */
    float       base_frequency;
} SoundistSample;

/* 退休中的采样：PCM 已脱离采样槽、仍被若干活跃 voice 引用。refcount 归零时退休一次。 */
typedef struct SoundistRetiringSample {
    float* pcm;
    int    refcount;
} SoundistRetiringSample;
#define SOUNDIST_MAX_RETIRING  32

/* 采样 one-shot 播放实例（正式采样器，阶段 C）。
 * 分数播放位置 pos + 线性插值 + 音高 step=frequency/base_frequency + ADSR 包络 + gain/pan + 低通滤波。 */
typedef struct SoundistSampleVoice {
    int          active;
    int          releasing;         /* 已进入 release（自然结束或 fast-stop/删除触发） */
    int          retiring_index;    /* 退休表下标；-1 = PCM 由采样槽持有，结束时不退休 */
    uint32_t     sample_index;      /* 引用的采样槽（删除处理用） */
    const float* pcm;
    uint64_t     frames;
    uint32_t     channels;
    double       pos;               /* 分数播放位置（帧） */
    double       step;              /* 播放速率 = frequency / base_frequency */
    float        frequency;         /* 触发频率（同音重触发检测） */
    float        gain;              /* 目标峰值增益 */
    float        pan;               /* -1..1 */
    float        cutoff;            /* 低通 cutoff（Hz），0=无滤波 */
    uint64_t     attack_frames;
    uint64_t     release_frames;
    uint64_t     age;               /* 触发后已产帧数 */
    uint64_t     release_start;     /* 进入 release 的 age；UINT64_MAX = 未开始 */
    /* 低通滤波（biquad，每声道独立历史，最多 2 声道） */
    float        lp_b0, lp_b1, lp_b2, lp_a1, lp_a2;
    float        lp_x1[2], lp_x2[2], lp_y1[2], lp_y2[2];
} SoundistSampleVoice;

/* 生成 voice（阶段 4 骨架：简单正弦）。 */
typedef struct SoundistVoice {
    int      active;
    uint64_t start_frame;  /* 绝对帧号（core->frame_count 坐标系） */
    uint64_t end_frame;
    float    frequency;
    float    gain;
    float    pan;
    double   phase;        /* 相位累加器，仅回调内更新 */
    uint64_t pos;          /* 已合成帧数 */
} SoundistVoice;

/* MasterChain 状态（仅回调内读写；目标 gain 由控制线程经 SET_MASTER_GAIN 命令写入）。
 * 混音后统一走：master 增益（一阶 ramp）→ DC 阻断 → tanh 安全 soft clip（防削波）。 */
typedef struct SoundistMaster {
    float gain;         /* 目标 master 增益（master×ambient×duck×mute），0=静音 */
    float gain_cur;     /* 当前（ramp） */
    float dc_coeff;     /* DC 阻断系数 R=exp(-2π·cutoff/sampleRate)，设备启动时按采样率计算 */
    float dc_x1[2];     /* DC 阻断输入历史（L/R） */
    float dc_y1[2];     /* DC 阻断输出历史（L/R） */
} SoundistMaster;

/* 控制线程 shadow（仅控制线程读写，回调绝不触碰）。用于精确计算 retire 指针与 generation。 */
typedef struct SoundistControlTrack {
    int      active;
    uint32_t generation;
    float*   pcm;
} SoundistControlTrack;
typedef struct SoundistControlSample {
    int      active;
    uint32_t generation;
    float*   pcm;
} SoundistControlSample;

/* 固定容量 SPSC 无锁环形缓冲（命令）。head 仅生产者（控制线程）写，tail 仅消费者（回调）写。 */
typedef struct SoundistCommandQueue {
    atomic_uint_fast32_t head;
    atomic_uint_fast32_t tail;
    SoundistCommand      buf[SOUNDIST_CMD_CAPACITY];
} SoundistCommandQueue;

/* 退休指针环形缓冲（retire-ack）。head 仅回调（生产者）写，tail 仅控制线程（消费者）写。
 * overflow：诊断计数，仅在满时递增（静态约束下不应发生）。控制线程读到非零即判定内存安全
 * 已被破坏，应进入安全失败状态。 */
typedef struct SoundistRetireQueue {
    atomic_uint_fast32_t head;
    atomic_uint_fast32_t tail;
    atomic_uint_fast32_t overflow;
    void*                buf[SOUNDIST_RETIRE_CAPACITY];
} SoundistRetireQueue;

typedef struct SoundistAudioCore {
    ma_context context;
    atomic_int state;
    int        initialized;         /* ma_context 是否已初始化（仅控制线程读写） */
    int        last_error;
    char       last_error_msg[256];
    SoundistCommandQueue cmdq;
    SoundistRetireQueue  retireq;
    ma_device  device;
    int        device_started;
    uint64_t   frame_count;         /* 绝对帧号，回调内递增 */
    uint32_t   device_sample_rate;
    SoundistMaster      master;     /* MasterChain 状态（回调只读写，目标经命令写入） */
    SoundistTrack       tracks[SOUNDIST_MAX_TRACKS];
    SoundistSample      samples[SOUNDIST_MAX_SAMPLES];
    SoundistSampleVoice sample_voices[SOUNDIST_MAX_SAMPLE_VOICES];
    SoundistRetiringSample retiring[SOUNDIST_MAX_RETIRING]; /* 采样删除/替换的退休表（回调只读写） */
    SoundistVoice       voices[SOUNDIST_MAX_VOICES];
    /* 控制线程 shadow：仅供 JNI（控制线程）读写，回调不碰。 */
    SoundistControlTrack  control_tracks[SOUNDIST_MAX_TRACKS];
    SoundistControlSample control_samples[SOUNDIST_MAX_SAMPLES];
} SoundistAudioCore;

/* ---------- 内部工具 ---------- */

#if defined(__GNUC__)
#define SOUNDIST_UNUSED __attribute__((unused))
#else
#define SOUNDIST_UNUSED
#endif

static void soundist_set_error(SoundistAudioCore* core, ma_result result, const char* msg) {
    core->last_error = (int)result;
    if (msg != NULL) {
        snprintf(core->last_error_msg, sizeof(core->last_error_msg), "%s", msg);
    } else {
        core->last_error_msg[0] = '\0';
    }
}

/* 一阶指数 ramp 系数：在 blockSeconds（秒）内以时间常数 tauSeconds 逼近目标。
 * 由 sampleRate/frameCount 与时间常数计算，与具体 block 尺寸无关（不依赖固定 512 帧）。 */
static float soundist_ramp_coeff(float blockSeconds, float tauSeconds) {
    if (tauSeconds <= 0.0f) return 1.0f;
    return 1.0f - expf(-blockSeconds / tauSeconds);
}

/* RBJ 低通 biquad 系数（a0 已归一化）。out[0..4] = b0,b1,b2,a1,a2。 */
static void soundist_lowpass_coeff(float freq, float sr, float q, float* out) {
    float w0 = 2.0f * SOUNDIST_PI * freq / sr;
    float cosw = cosf(w0);
    float sinw = sinf(w0);
    float alpha = sinw / (2.0f * q);
    float a0 = 1.0f + alpha;
    out[0] = ((1.0f - cosw) * 0.5f) / a0;
    out[1] = (1.0f - cosw) / a0;
    out[2] = ((1.0f - cosw) * 0.5f) / a0;
    out[3] = (-2.0f * cosw) / a0;
    out[4] = (1.0f - alpha) / a0;
}

/* 采样声部低通（每声道独立历史）。 */
static float soundist_biquad_process(SoundistSampleVoice* v, int c, float x) {
    float y = v->lp_b0 * x + v->lp_b1 * v->lp_x1[c] + v->lp_b2 * v->lp_x2[c]
            - v->lp_a1 * v->lp_y1[c] - v->lp_a2 * v->lp_y2[c];
    v->lp_x2[c] = v->lp_x1[c]; v->lp_x1[c] = x;
    v->lp_y2[c] = v->lp_y1[c]; v->lp_y1[c] = y;
    return y;
}

/* float <-> int32 bits（命令 arg1 里传递） */
static float soundist_bits_to_float(int32_t bits) {
    float f;
    memcpy(&f, &bits, sizeof(f));
    return f;
}
static int32_t soundist_float_to_bits(float f) {
    int32_t bits;
    memcpy(&bits, &f, sizeof(bits));
    return bits;
}

/* ---------- 生命周期状态 ---------- */

static int soundist_state_get(SoundistAudioCore* core) {
    return atomic_load_explicit(&core->state, memory_order_acquire);
}
static void soundist_state_set(SoundistAudioCore* core, int s) {
    atomic_store_explicit(&core->state, s, memory_order_release);
}

/* 终止态（FAILED/RELEASED）：此后禁止再投递任何 add/remove/set/trigger 命令。 */
static int soundist_state_is_dead(SoundistAudioCore* core) {
    int st = soundist_state_get(core);
    return st == SOUNDIST_STATE_FAILED || st == SOUNDIST_STATE_RELEASED;
}

/* 停止并反初始化设备，检查 ma_device_stop 返回值。返回 0 成功；非 0 = 停止失败（设备可能仍在运行，
 * 调用方不得再 free PCM，否则回调可能读到已释放内存）。 */
static int soundist_stop_device(SoundistAudioCore* core) {
    if (!core->device_started) return 0;
    ma_result r = ma_device_stop(&core->device);
    if (r != MA_SUCCESS) {
        soundist_set_error(core, r, ma_result_description(r));
        return (int)r;
    }
    ma_device_uninit(&core->device);
    core->device_started = 0;
    return 0;
}

/* ---------- 命令队列（SPSC 无锁，单生产者=控制线程） ---------- */

static int soundist_cmdq_post(SoundistCommandQueue* q, const SoundistCommand* cmd) {
    uint_fast32_t head = atomic_load_explicit(&q->head, memory_order_relaxed);
    uint_fast32_t tail = atomic_load_explicit(&q->tail, memory_order_acquire);
    if ((head - tail) >= SOUNDIST_CMD_CAPACITY) {
        return -1; /* 满：返回明确错误，不静默丢弃 */
    }
    q->buf[head & SOUNDIST_CMD_MASK] = *cmd;
    atomic_store_explicit(&q->head, head + 1, memory_order_release);
    return 0;
}

static int soundist_cmdq_pop(SoundistCommandQueue* q, SoundistCommand* out) {
    uint_fast32_t tail = atomic_load_explicit(&q->tail, memory_order_relaxed);
    uint_fast32_t head = atomic_load_explicit(&q->head, memory_order_acquire);
    if (tail == head) {
        return -1; /* 空 */
    }
    *out = q->buf[tail & SOUNDIST_CMD_MASK];
    atomic_store_explicit(&q->tail, tail + 1, memory_order_release);
    return 0;
}

static uint_fast32_t soundist_cmdq_count(const SoundistCommandQueue* q) {
    uint_fast32_t head = atomic_load_explicit(&q->head, memory_order_acquire);
    uint_fast32_t tail = atomic_load_explicit(&q->tail, memory_order_relaxed);
    return head - tail;
}

/* 清空命令队列（仅设备停止、回调不再运行时由控制线程调用），避免 stop→restart 时重放陈旧命令
 * 导致已 free 指针被再次 free 或再次被回调引用（use-after-free / double-free）。 */
static void soundist_cmdq_reset(SoundistCommandQueue* q) {
    atomic_store_explicit(&q->head, 0, memory_order_relaxed);
    atomic_store_explicit(&q->tail, 0, memory_order_relaxed);
}

/* ---------- 退休队列（SPSC 无锁，生产者=回调，消费者=控制线程） ---------- */

/* 仅回调调用：把待 free 指针交给控制线程。静态约束（容量 512 ≥ 命令 256 + 每次投递前 drain）
 * 证明退休队列永不溢出；若仍满（只能是程序缺陷），记录原子诊断计数而非静默丢弃——此时该指针
 * 已从 audio slot / control shadow 移出且命令已被消费，free_all_pcm 无法再找到它，属不可恢复泄漏，
 * 故控制层读到非零计数后必须进入安全失败状态（停止并报错），不得继续运行。 */
static void soundist_retire_post(SoundistRetireQueue* q, void* p) {
    if (p == NULL) return;
    uint_fast32_t head = atomic_load_explicit(&q->head, memory_order_relaxed);
    uint_fast32_t tail = atomic_load_explicit(&q->tail, memory_order_acquire);
    if ((head - tail) >= SOUNDIST_RETIRE_CAPACITY) {
        atomic_fetch_add_explicit(&q->overflow, 1, memory_order_relaxed);
        return;
    }
    q->buf[head & SOUNDIST_RETIRE_MASK] = p;
    atomic_store_explicit(&q->head, head + 1, memory_order_release);
}

/* 仅控制线程调用：drain 并 free 所有已退休指针。 */
static void soundist_retire_drain(SoundistRetireQueue* q) {
    for (;;) {
        uint_fast32_t tail = atomic_load_explicit(&q->tail, memory_order_relaxed);
        uint_fast32_t head = atomic_load_explicit(&q->head, memory_order_acquire);
        if (tail == head) break;
        void* p = q->buf[tail & SOUNDIST_RETIRE_MASK];
        atomic_store_explicit(&q->tail, tail + 1, memory_order_release);
        free(p);
    }
}

/* ---------- 命令应用（仅音频线程调用） ---------- */

/* 释放采样槽（删除/替换）：deleting=1 阻止新触发；活跃 voice 转入 release，PCM 转移到退休表
 * （retiring）并按活跃 voice 数做真实引用计数，最后一个 voice 结束时退休一次——杜绝多 voice 同采样 double-free。 */
static void soundist_release_sample_slot(SoundistAudioCore* core, int idx) {
    SoundistSample* s = &core->samples[idx];
    s->deleting = 1;
    int active = 0;
    for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
        SoundistSampleVoice* v = &core->sample_voices[i];
        if (v->active && v->sample_index == (uint32_t)idx) active++;
    }
    if (active == 0) {
        float* pcm = s->pcm;
        memset(s, 0, sizeof(*s));
        soundist_retire_post(&core->retireq, pcm);
        return;
    }
    /* 找空闲退休表槽。理论上 32 个退休槽足够：活跃 voice ≤ 32，且每个退休采样至少被 1 个 voice 引用。 */
    int r = -1;
    for (int i = 0; i < SOUNDIST_MAX_RETIRING; i++) {
        if (core->retiring[i].pcm == NULL) { r = i; break; }
    }
    if (r < 0) {
        /* 防御：退休表满（不应发生）。退化为立即停用 + 退休，至少不泄漏/UAF。 */
        for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
            SoundistSampleVoice* v = &core->sample_voices[i];
            if (v->active && v->sample_index == (uint32_t)idx) v->active = 0;
        }
        float* pcm = s->pcm;
        memset(s, 0, sizeof(*s));
        soundist_retire_post(&core->retireq, pcm);
        return;
    }
    core->retiring[r].pcm = s->pcm;
    core->retiring[r].refcount = active;
    for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
        SoundistSampleVoice* v = &core->sample_voices[i];
        if (v->active && v->sample_index == (uint32_t)idx) {
            v->retiring_index = r;
            if (!v->releasing) { v->releasing = 1; v->release_start = v->age; }
        }
    }
    memset(s, 0, sizeof(*s));
}

/* voice 结束时的引用计数递减：若 voice 引用退休表，refcount 归零则退休 PCM 一次。 */
static void soundist_voice_finished(SoundistAudioCore* core, SoundistSampleVoice* v) {
    if (v->retiring_index >= 0 && v->retiring_index < SOUNDIST_MAX_RETIRING) {
        SoundistRetiringSample* r = &core->retiring[v->retiring_index];
        if (r->refcount > 0) r->refcount--;
        if (r->refcount <= 0 && r->pcm != NULL) {
            soundist_retire_post(&core->retireq, r->pcm);
            r->pcm = NULL;
            r->refcount = 0;
        }
        v->retiring_index = -1;
    }
}

static void soundist_apply_add_track(SoundistAudioCore* core, const SoundistCommand* cmd) {
    int id = cmd->arg0;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS) return;
    if (cmd->arg1 == 0) return;
    /* 先退休旧指针（如果有），再覆盖槽。 */
    soundist_retire_post(&core->retireq, (void*)(intptr_t)cmd->retire);
    SoundistTrack* t = &core->tracks[id];
    memset(t, 0, sizeof(*t));
    t->active     = 1;
    t->id         = id;
    t->generation = cmd->arg4;
    t->pcm        = (float*)(intptr_t)cmd->arg1;
    t->frames     = (uint64_t)cmd->arg2;
    t->pos        = 0;
    t->channels   = (uint32_t)cmd->arg3 > 0 ? (uint32_t)cmd->arg3 : 1;
    t->gain       = 1.0f;
    t->gain_cur   = 1.0f;
    t->pan        = 0.0f;
    t->pan_cur    = 0.0f;
    t->playing    = 0;
}

static void soundist_apply_remove_track(SoundistAudioCore* core, const SoundistCommand* cmd) {
    int id = cmd->arg0;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS) return;
    SoundistTrack* t = &core->tracks[id];
    if (!t->active) return;
    /* generation 防御：只退休该 remove 签发时对应的资源，防止旧 remove 删新资源。 */
    if (t->generation != cmd->arg4) return;
    soundist_retire_post(&core->retireq, (void*)(intptr_t)cmd->retire);
    memset(t, 0, sizeof(*t));
}

static void soundist_apply_add_sample(SoundistAudioCore* core, const SoundistCommand* cmd) {
    int idx = cmd->arg0;
    if (idx < 0 || idx >= SOUNDIST_MAX_SAMPLES) return;
    if (cmd->arg1 == 0) return;
    /* 替换已有采样：释放旧采样槽（deleting + 活跃 voice 接管 PCM 所有权，引用计数语义）。 */
    if (core->samples[idx].active) {
        soundist_release_sample_slot(core, idx);
    }
    SoundistSample* s = &core->samples[idx];
    memset(s, 0, sizeof(*s));
    s->active         = 1;
    s->deleting       = 0;
    s->generation     = cmd->arg4;
    s->pcm            = (float*)(intptr_t)cmd->arg1;
    s->frames         = (uint64_t)cmd->arg2;
    s->channels       = (uint32_t)cmd->arg3 > 0 ? (uint32_t)cmd->arg3 : 1;
    s->sample_rate    = (uint32_t)cmd->f0 > 0 ? (uint32_t)cmd->f0 : 0;
    s->base_frequency = cmd->f1 > 0.0f ? cmd->f1 : 440.0f;
}

static void soundist_apply_remove_sample(SoundistAudioCore* core, const SoundistCommand* cmd) {
    int idx = cmd->arg0;
    if (idx < 0 || idx >= SOUNDIST_MAX_SAMPLES) return;
    SoundistSample* s = &core->samples[idx];
    if (!s->active) return;
    if (s->generation != cmd->arg4) return;
    soundist_release_sample_slot(core, idx);
}

static void soundist_apply_trigger_sample(SoundistAudioCore* core, const SoundistCommand* cmd) {
    int idx = cmd->arg0;
    if (idx < 0 || idx >= SOUNDIST_MAX_SAMPLES) return;
    SoundistSample* s = &core->samples[idx];
    if (!s->active || s->deleting || s->pcm == NULL || s->frames == 0 || s->base_frequency <= 0.0f) return;

    float frequency = soundist_bits_to_float((int32_t)cmd->arg1);
    if (frequency <= 0.0f) return;
    double step = (double)frequency / (double)s->base_frequency; /* 音高比 */
    if (step <= 0.0) return;
    float cutoff = soundist_bits_to_float((int32_t)cmd->arg4);
    uint64_t attack_frames = (uint64_t)(cmd->arg2 > 0 ? cmd->arg2 : 0);
    uint64_t release_frames = (uint64_t)(cmd->arg3 > 0 ? cmd->arg3 : 1024); /* 默认 ~21ms @48k */
    float gain = cmd->f0; /* 尊重 0：静音触发不应满音量播放（Kotlin 默认 1f，故无「未提供」情形） */
    float pan = cmd->f1;

    /* 同音重触发：同一采样+同一频率的活跃 voice 直接重启（pos=0，包络/滤波重置）。 */
    for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
        SoundistSampleVoice* v = &core->sample_voices[i];
        if (v->active && v->sample_index == (uint32_t)idx && v->frequency == frequency) {
            v->pos = 0.0; v->age = 0; v->releasing = 0; v->release_start = UINT64_MAX;
            v->step = step; v->gain = gain; v->pan = pan; v->cutoff = cutoff;
            v->attack_frames = attack_frames; v->release_frames = release_frames;
            memset(v->lp_x1, 0, sizeof(v->lp_x1)); memset(v->lp_x2, 0, sizeof(v->lp_x2));
            memset(v->lp_y1, 0, sizeof(v->lp_y1)); memset(v->lp_y2, 0, sizeof(v->lp_y2));
            if (cutoff > 0.0f && cutoff < core->device_sample_rate * 0.45f) {
                soundist_lowpass_coeff(cutoff, (float)core->device_sample_rate, 0.7071f, &v->lp_b0);
            } else {
                v->lp_b0 = 1.0f; v->lp_b1 = 0; v->lp_b2 = 0; v->lp_a1 = 0; v->lp_a2 = 0;
            }
            return;
        }
    }

    /* 找空闲 voice；找不到就 steal 最老的（age 最大）。 */
    SoundistSampleVoice* v = NULL;
    for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
        if (!core->sample_voices[i].active) { v = &core->sample_voices[i]; break; }
    }
    if (v == NULL) {
        uint64_t oldest = 0;
        for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
            SoundistSampleVoice* c = &core->sample_voices[i];
            if (c->age >= oldest) { oldest = c->age; v = c; }
        }
        /* 被偷的 voice 若引用退休表（删除/替换接管），按引用计数递减。 */
        soundist_voice_finished(core, v);
    }

    memset(v, 0, sizeof(*v));
    v->active = 1;
    v->releasing = 0;
    v->retiring_index = -1; /* -1 = PCM 由采样槽持有，结束时不退休 */
    v->sample_index = (uint32_t)idx;
    v->pcm = s->pcm;
    v->frames = s->frames;
    v->channels = s->channels;
    v->pos = 0.0;
    v->step = step;
    v->frequency = frequency;
    v->gain = gain;
    v->pan = pan;
    v->cutoff = cutoff;
    v->attack_frames = attack_frames;
    v->release_frames = release_frames;
    v->age = 0;
    v->release_start = UINT64_MAX;
    if (cutoff > 0.0f && cutoff < core->device_sample_rate * 0.45f) {
        soundist_lowpass_coeff(cutoff, (float)core->device_sample_rate, 0.7071f, &v->lp_b0);
    } else {
        v->lp_b0 = 1.0f; v->lp_b1 = 0; v->lp_b2 = 0; v->lp_a1 = 0; v->lp_a2 = 0;
    }
}

static void soundist_apply_trigger_voice(SoundistAudioCore* core, const SoundistCommand* cmd) {
    float frequency = soundist_bits_to_float((int32_t)cmd->arg1);
    if (frequency <= 0.0f) return;
    for (int i = 0; i < SOUNDIST_MAX_VOICES; i++) {
        SoundistVoice* v = &core->voices[i];
        if (!v->active) {
            memset(v, 0, sizeof(*v));
            v->active      = 1;
            v->start_frame = core->frame_count;
            v->end_frame   = core->frame_count + (uint64_t)(cmd->arg2 > 0 ? cmd->arg2 : 1);
            v->frequency   = frequency;
            v->gain        = cmd->f0;
            v->pan         = cmd->f1;
            v->phase       = 0.0;
            v->pos         = 0;
            break;
        }
    }
}

static void soundist_apply_command(SoundistAudioCore* core, const SoundistCommand* cmd) {
    switch (cmd->type) {
    case SOUNDIST_CMD_SET_GAIN:
        if (cmd->arg0 >= 0 && cmd->arg0 < SOUNDIST_MAX_TRACKS) {
            core->tracks[cmd->arg0].gain = soundist_bits_to_float((int32_t)cmd->arg1);
        }
        break;
    case SOUNDIST_CMD_SET_PAN:
        if (cmd->arg0 >= 0 && cmd->arg0 < SOUNDIST_MAX_TRACKS) {
            core->tracks[cmd->arg0].pan = soundist_bits_to_float((int32_t)cmd->arg1);
        }
        break;
    case SOUNDIST_CMD_SET_PLAYING:
        if (cmd->arg0 >= 0 && cmd->arg0 < SOUNDIST_MAX_TRACKS) {
            core->tracks[cmd->arg0].playing = (cmd->arg1 != 0) ? 1 : 0;
        }
        break;
    case SOUNDIST_CMD_ADD_TRACK:
        soundist_apply_add_track(core, cmd);
        break;
    case SOUNDIST_CMD_REMOVE_TRACK:
        soundist_apply_remove_track(core, cmd);
        break;
    case SOUNDIST_CMD_ADD_SAMPLE:
        soundist_apply_add_sample(core, cmd);
        break;
    case SOUNDIST_CMD_REMOVE_SAMPLE:
        soundist_apply_remove_sample(core, cmd);
        break;
    case SOUNDIST_CMD_TRIGGER_SAMPLE:
        soundist_apply_trigger_sample(core, cmd);
        break;
    case SOUNDIST_CMD_TRIGGER_VOICE:
        soundist_apply_trigger_voice(core, cmd);
        break;
    case SOUNDIST_CMD_SET_MASTER_GAIN:
        core->master.gain = soundist_bits_to_float((int32_t)cmd->arg1);
        break;
    default:
        break;
    }
}

/* ---------- 数据回调（实时线程：零分配、零锁、零日志、零 IO、零 JNI） ---------- */

static void soundist_data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount) {
    (void)pInput;
    SoundistAudioCore* core = (SoundistAudioCore*)pDevice->pUserData;
    float* out = (float*)pOutput;
    uint32_t ch = pDevice->playback.channels;
    if (ch == 0) ch = 1;
    double sr = pDevice->sampleRate > 0 ? (double)pDevice->sampleRate : 44100.0;
    /* 一阶 ramp 系数：按 sampleRate/frameCount 与固定时间常数计算（不依赖固定 512 帧）。 */
    float blockSeconds = (float)frameCount / (float)sr;
    float rampCoeff = soundist_ramp_coeff(blockSeconds, SOUNDIST_RAMP_TAU_SECONDS);
    float masterRampCoeff = 1.0f - expf(-1.0f / ((float)sr * SOUNDIST_MASTER_RAMP_TAU_SECONDS));

    /* 1. 消费命令：所有共享状态只在音频线程修改 */
    SoundistCommand cmd;
    while (soundist_cmdq_pop(&core->cmdq, &cmd) == 0) {
        soundist_apply_command(core, &cmd);
    }

    /* 2. 清零输出 */
    memset(out, 0, (size_t)frameCount * ch * sizeof(float));

    /* 3. 混合环境声循环轨道（设备格式交错 PCM，真实保留 mono/stereo） */
    for (int t = 0; t < SOUNDIST_MAX_TRACKS; t++) {
        SoundistTrack* tr = &core->tracks[t];
        if (!tr->active || tr->pcm == NULL || !tr->playing || tr->frames == 0) continue;

        tr->gain_cur += (tr->gain - tr->gain_cur) * rampCoeff;
        tr->pan_cur  += (tr->pan  - tr->pan_cur)  * rampCoeff;
        float g = tr->gain_cur;
        /* equal-power balance：左右声道分别缩放，保留立体声两声道。 */
        float lg = (tr->pan_cur <= 0.0f) ? 1.0f : sqrtf(1.0f - tr->pan_cur);
        float rg = (tr->pan_cur >= 0.0f) ? 1.0f : sqrtf(1.0f + tr->pan_cur);

        if (ch == 1) {
            for (uint32_t f = 0; f < frameCount; f++) {
                if (tr->pos >= tr->frames) tr->pos = 0; /* 循环 */
                out[f] += tr->pcm[tr->pos * tr->channels] * g;
                tr->pos++;
            }
        } else {
            for (uint32_t f = 0; f < frameCount; f++) {
                if (tr->pos >= tr->frames) tr->pos = 0;
                float l = tr->pcm[tr->pos * tr->channels + 0] * g * lg;
                float r = tr->pcm[tr->pos * tr->channels + 1] * g * rg;
                out[f * ch + 0] += l;
                out[f * ch + 1] += r;
                tr->pos++;
            }
        }
    }

    /* 4. 混合采样 one-shot（正式采样器：分数位置 + 线性插值 + 音高 + ADSR + 低通） */
    for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
        SoundistSampleVoice* sv = &core->sample_voices[i];
        if (!sv->active || sv->pcm == NULL || sv->frames == 0) continue;
        float lg = (sv->pan <= 0.0f) ? 1.0f : sqrtf(1.0f - sv->pan);
        float rg = (sv->pan >= 0.0f) ? 1.0f : sqrtf(1.0f + sv->pan);
        int stereo = (sv->channels >= 2);
        for (uint32_t f = 0; f < frameCount; f++) {
            /* 自然结束 → 进入 release */
            if (!sv->releasing && sv->pos >= (double)sv->frames) {
                sv->releasing = 1;
                sv->release_start = sv->age;
            }
            /* ADSR 包络（attack→sustain=1→release） */
            float env;
            if (sv->age < sv->attack_frames) {
                env = (sv->attack_frames > 0) ? (float)sv->age / (float)sv->attack_frames : 1.0f;
            } else if (!sv->releasing) {
                env = 1.0f;
            } else {
                uint64_t r = sv->age - sv->release_start;
                if (r >= sv->release_frames) { env = 0.0f; }
                else env = 1.0f - (float)r / (float)sv->release_frames;
            }
            if (env <= 0.0f) {
                /* voice 结束：引用退休表则按引用计数递减，最后一个 voice 退休 PCM 一次。 */
                soundist_voice_finished(core, sv);
                sv->active = 0;
                break;
            }
            /* 线性插值取采样（越界夹到末帧，release 期间保持末帧值） */
            uint64_t i0 = (uint64_t)sv->pos;
            if (i0 >= sv->frames) i0 = sv->frames - 1;
            uint64_t i1 = i0 + 1;
            if (i1 >= sv->frames) i1 = sv->frames - 1;
            float frac = (float)(sv->pos - (double)i0);
            if (frac > 1.0f) frac = 1.0f;
            float sL, sR;
            if (stereo) {
                float a0 = sv->pcm[i0 * 2 + 0], a1 = sv->pcm[i1 * 2 + 0];
                float b0 = sv->pcm[i0 * 2 + 1], b1 = sv->pcm[i1 * 2 + 1];
                sL = a0 + (a1 - a0) * frac;
                sR = b0 + (b1 - b0) * frac;
            } else {
                float a0 = sv->pcm[i0], a1 = sv->pcm[i1];
                sL = sR = a0 + (a1 - a0) * frac;
            }
            if (sv->cutoff > 0.0f) {
                sL = soundist_biquad_process(sv, 0, sL);
                sR = soundist_biquad_process(sv, 1, sR);
            }
            float g = sv->gain * env;
            if (ch == 1) {
                out[f] += sL * g;
            } else {
                out[f * ch + 0] += sL * g * lg;
                out[f * ch + 1] += sR * g * rg;
            }
            sv->pos += sv->step;
            sv->age++;
        }
    }

    /* 5. 合成 voice（阶段 4 骨架：正弦，非 18 音色/调度/MasterChain） */
    uint64_t cb_begin = core->frame_count;
    uint64_t cb_end   = cb_begin + frameCount;
    for (int i = 0; i < SOUNDIST_MAX_VOICES; i++) {
        SoundistVoice* vo = &core->voices[i];
        if (!vo->active) continue;
        uint64_t v_start = vo->start_frame > cb_begin ? vo->start_frame : cb_begin;
        uint64_t v_end   = vo->end_frame   < cb_end   ? vo->end_frame   : cb_end;
        if (v_start >= v_end) {
            if (cb_begin >= vo->end_frame) vo->active = 0;
            continue;
        }
        uint64_t f_begin = v_start - cb_begin;
        uint64_t f_end   = v_end   - cb_begin;
        float lg = (vo->pan <= 0.0f) ? 1.0f : sqrtf(1.0f - vo->pan);
        float rg = (vo->pan >= 0.0f) ? 1.0f : sqrtf(1.0f + vo->pan);
        double phase_inc = 2.0 * SOUNDIST_PI * (double)vo->frequency / sr;
        if (ch == 1) {
            for (uint64_t f = f_begin; f < f_end; f++) {
                out[f] += vo->gain * sinf((float)vo->phase);
                vo->phase += phase_inc;
                vo->pos++;
            }
        } else {
            for (uint64_t f = f_begin; f < f_end; f++) {
                float s = vo->gain * sinf((float)vo->phase);
                out[f * ch + 0] += s * lg;
                out[f * ch + 1] += s * rg;
                vo->phase += phase_inc;
                vo->pos++;
            }
        }
        if (vo->pos >= (vo->end_frame - vo->start_frame)) vo->active = 0;
    }

    /* 6. MasterChain：master 增益（一阶 ramp）→ DC 阻断 → tanh 安全 soft clip（防削波）。
     *    仅数学运算（乘加/expf/tanhf），零分配、零锁、零 JNI。 */
    {
        uint32_t nc = ch <= 2 ? ch : 2; /* 设备声道 1..2（Kotlin 侧 PcmFormat 约定） */
        for (uint32_t f = 0; f < frameCount; f++) {
            core->master.gain_cur += (core->master.gain - core->master.gain_cur) * masterRampCoeff;
            float g = core->master.gain_cur;
            for (uint32_t c = 0; c < nc; c++) {
                float x = out[f * ch + c] * g;
                float y = x - core->master.dc_x1[c] + core->master.dc_coeff * core->master.dc_y1[c];
                core->master.dc_x1[c] = x;
                core->master.dc_y1[c] = y;
                out[f * ch + c] = tanhf(y);
            }
        }
    }

    /* 7. 推进绝对帧号 */
    core->frame_count = cb_end;
}

/* ---------- 释放全部 native-owned PCM（仅控制线程，设备已停时调用） ---------- */

static int soundist_owned_has(void* const* list, int count, void* p) {
    for (int i = 0; i < count; i++) if (list[i] == p) return 1;
    return 0;
}

/* 释放全部 native-owned PCM（仅控制线程，设备已停时调用）。
 * 关键点：同一指针可能同时存在于 audio slot、control shadow、未消费命令的 arg1/retire、
 * 退休队列中的多处（例如 add A→add B→add C 且回调只消费了 A 时，B 只存在于队列命令里）。
 * 因此先遍历所有这些来源、按指针去重收集，再统一 free 恰好一次，最后重置所有槽与队列。
 * 仅 free 槽+shadow 会泄漏「排队中的中间指针」，仅 free 队列会 double-free 已退休指针。 */
static void soundist_free_all_pcm(SoundistAudioCore* core) {
    void* owned[SOUNDIST_MAX_OWNED];
    int n = 0;
    #define SOUNDIST_OWNED_ADD(p) \
        do { void* _p = (void*)(p); if (_p != NULL && !soundist_owned_has(owned, n, _p)) owned[n++] = _p; } while (0)

    /* 1. 音频槽 + 控制 shadow（二者通常相同，去重）。 */
    for (int i = 0; i < SOUNDIST_MAX_TRACKS; i++) {
        SOUNDIST_OWNED_ADD(core->tracks[i].pcm);
        SOUNDIST_OWNED_ADD(core->control_tracks[i].pcm);
    }
    for (int i = 0; i < SOUNDIST_MAX_SAMPLES; i++) {
        SOUNDIST_OWNED_ADD(core->samples[i].pcm);
        SOUNDIST_OWNED_ADD(core->control_samples[i].pcm);
    }
    /* 采样声部持有的 PCM（删除/替换后槽已清空、PCM 由 voice 引用）。 */
    for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) {
        if (core->sample_voices[i].active) {
            SOUNDIST_OWNED_ADD(core->sample_voices[i].pcm);
        }
    }
    /* 退休表中的 PCM（被活跃 voice 引用，但为防御 double-free 显式再收一次，去重）。 */
    for (int i = 0; i < SOUNDIST_MAX_RETIRING; i++) {
        SOUNDIST_OWNED_ADD(core->retiring[i].pcm);
    }
    /* 2. 未消费命令里的 native-owned 指针（ADD 的 arg1=newPcm + 任意命令的 retire=旧Pcm）。 */
    uint_fast32_t tail = atomic_load_explicit(&core->cmdq.tail, memory_order_relaxed);
    uint_fast32_t head = atomic_load_explicit(&core->cmdq.head, memory_order_acquire);
    while (tail != head) {
        const SoundistCommand* c = &core->cmdq.buf[tail & SOUNDIST_CMD_MASK];
        if (c->type == SOUNDIST_CMD_ADD_TRACK || c->type == SOUNDIST_CMD_ADD_SAMPLE) {
            SOUNDIST_OWNED_ADD((intptr_t)c->arg1);
        }
        SOUNDIST_OWNED_ADD((intptr_t)c->retire);
        tail++;
    }
    /* 3. 退休队列里已退休但尚未 drain 的指针。 */
    uint_fast32_t rtail = atomic_load_explicit(&core->retireq.tail, memory_order_relaxed);
    uint_fast32_t rhead = atomic_load_explicit(&core->retireq.head, memory_order_acquire);
    while (rtail != rhead) {
        SOUNDIST_OWNED_ADD(core->retireq.buf[rtail & SOUNDIST_RETIRE_MASK]);
        rtail++;
    }
    #undef SOUNDIST_OWNED_ADD

    for (int i = 0; i < n; i++) free(owned[i]);

    /* 重置所有槽/voice/队列（命令队列清空避免 stop→restart 重放陈旧命令）。 */
    for (int i = 0; i < SOUNDIST_MAX_TRACKS; i++) {
        memset(&core->tracks[i], 0, sizeof(core->tracks[i]));
        memset(&core->control_tracks[i], 0, sizeof(core->control_tracks[i]));
    }
    for (int i = 0; i < SOUNDIST_MAX_SAMPLES; i++) {
        memset(&core->samples[i], 0, sizeof(core->samples[i]));
        memset(&core->control_samples[i], 0, sizeof(core->control_samples[i]));
    }
    for (int i = 0; i < SOUNDIST_MAX_SAMPLE_VOICES; i++) memset(&core->sample_voices[i], 0, sizeof(core->sample_voices[i]));
    for (int i = 0; i < SOUNDIST_MAX_RETIRING; i++) memset(&core->retiring[i], 0, sizeof(core->retiring[i]));
    for (int i = 0; i < SOUNDIST_MAX_VOICES; i++) memset(&core->voices[i], 0, sizeof(core->voices[i]));
    /* 清空 MasterChain 状态（gain/gain_cur/DC 历史），stop/restart 时不留残余直流或增益。 */
    memset(&core->master, 0, sizeof(core->master));
    soundist_cmdq_reset(&core->cmdq);
    /* 退休队列内容已全部收集并 free，仅重置 head/tail；overflow 计数保留作诊断。 */
    atomic_store_explicit(&core->retireq.head, 0, memory_order_relaxed);
    atomic_store_explicit(&core->retireq.tail, 0, memory_order_relaxed);
}

/* ---------- JNI 绑定（阶段 1：生命周期） ---------- */

JNIEXPORT jlong JNICALL Java_com_soundist_app_NativeAudioCore_nativeCreate(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)calloc(1, sizeof(SoundistAudioCore));
    if (core == NULL) return 0;
    atomic_init(&core->state, SOUNDIST_STATE_UNINITIALIZED);
    soundist_set_error(core, MA_SUCCESS, "OK");
    return (jlong)(intptr_t)core;
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeInitialize(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return (jint)MA_INVALID_ARGS;
    int st = soundist_state_get(core);
    if (st == SOUNDIST_STATE_INITIALIZED || st == SOUNDIST_STATE_STARTING || st == SOUNDIST_STATE_RUNNING || st == SOUNDIST_STATE_STOPPING) {
        return 0; /* 幂等 */
    }
    if (st == SOUNDIST_STATE_FAILED) {
        return (jint)MA_INVALID_ARGS; /* 失败态需重建 */
    }
    ma_result result = ma_context_init(NULL, 0, NULL, &core->context);
    if (result != MA_SUCCESS) {
        soundist_set_error(core, result, ma_result_description(result));
        soundist_state_set(core, SOUNDIST_STATE_FAILED);
        return (jint)result;
    }
    core->initialized = 1;
    soundist_state_set(core, SOUNDIST_STATE_INITIALIZED);
    soundist_set_error(core, MA_SUCCESS, "OK");
    return 0;
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return (jint)MA_INVALID_ARGS;
    soundist_state_set(core, SOUNDIST_STATE_STOPPING);
    if (soundist_stop_device(core) != 0) {
        /* 停止失败：设备可能仍在运行，跳过 free，避免回调读已释放内存。 */
        soundist_state_set(core, SOUNDIST_STATE_FAILED);
        return (jint)MA_INVALID_OPERATION;
    }
    /* 停设备后回调不再运行，安全释放全部 PCM（含未退休的）。 */
    soundist_free_all_pcm(core);
    if (core->initialized) {
        ma_context_uninit(&core->context);
        core->initialized = 0;
    }
    soundist_state_set(core, SOUNDIST_STATE_RELEASED);
    free(core);
    return 0;
}

JNIEXPORT jstring JNICALL Java_com_soundist_app_NativeAudioCore_nativeVersionString(JNIEnv* env, jobject thiz, jlong handle) {
    (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return (*env)->NewStringUTF(env, "");
    return (*env)->NewStringUTF(env, ma_version_string());
}

JNIEXPORT jstring JNICALL Java_com_soundist_app_NativeAudioCore_nativeLastErrorMessage(JNIEnv* env, jobject thiz, jlong handle) {
    (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return (*env)->NewStringUTF(env, "invalid native handle");
    return (*env)->NewStringUTF(env, core->last_error_msg[0] != '\0' ? core->last_error_msg : "OK");
}

/* 通用命令投递（阶段 1 命令队列接口）。返回 0 成功，-1 满，-2 非法句柄。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativePostCommand(JNIEnv* env, jobject thiz, jlong handle, jint type, jint arg0, jlong arg1) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    soundist_retire_drain(&core->retireq); /* 顺带释放上一批已退休指针 */
    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type = type;
    cmd.arg0 = arg0;
    cmd.arg1 = arg1;
    return (jint)soundist_cmdq_post(&core->cmdq, &cmd);
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeCommandCount(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return 0;
    return (jint)soundist_cmdq_count(&core->cmdq);
}

/* 控制线程显式 drain 已退休指针（供 NativeAudioCore 在批次末尾调用）。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeDrainRetired(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    soundist_retire_drain(&core->retireq);
    return 0;
}

/* 退休队列溢出诊断计数（静态约束下恒为 0；非零 = 内存安全已破坏，控制层应安全失败）。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeRetireOverflowCount(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return 0;
    return (jint)atomic_load_explicit(&core->retireq.overflow, memory_order_relaxed);
}

/* 统一 fail-safe：进入 FAILED 终止态，停止设备（ma_device_stop 保证回调不再运行）并释放全部
 * PCM（在控制线程，绝不在回调线程）。此后所有 add/remove/set/trigger 因 soundist_state_is_dead
 * 返回 -2。幂等：已 FAILED/RELEASED 时仅重复清理。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeFailSafe(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    soundist_state_set(core, SOUNDIST_STATE_FAILED);
    if (soundist_stop_device(core) != 0) {
        return -3; /* 停止失败：跳过 free，避免回调读已释放内存 */
    }
    soundist_free_all_pcm(core);
    core->frame_count = 0;
    return 0;
}

/* ---------- JNI 绑定（阶段 2：设备 + 环境声轨道） ---------- */

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeStartDevice(JNIEnv* env, jobject thiz, jlong handle, jint sampleRate, jint channels) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    int st = soundist_state_get(core);
    if (st == SOUNDIST_STATE_RUNNING) return 0; /* 幂等 */
    if (st != SOUNDIST_STATE_INITIALIZED) return -2; /* 必须 INITIALIZED 才能启动 */
    soundist_state_set(core, SOUNDIST_STATE_STARTING);

    ma_device_config cfg = ma_device_config_init(ma_device_type_playback);
    cfg.playback.format    = ma_format_f32;
    cfg.playback.channels  = channels > 0 ? (ma_uint32)channels : 2;
    cfg.sampleRate         = sampleRate > 0 ? (ma_uint32)sampleRate : 44100;
    cfg.periodSizeInFrames = 512;
    cfg.dataCallback       = soundist_data_callback;
    cfg.pUserData          = core;

    ma_result r = ma_device_init(&core->context, &cfg, &core->device);
    if (r != MA_SUCCESS) {
        soundist_set_error(core, r, ma_result_description(r));
        soundist_state_set(core, SOUNDIST_STATE_FAILED);
        return (jint)r;
    }
    core->device_sample_rate = core->device.sampleRate;
    core->frame_count = 0;
    /* MasterChain 从静音起步（gain=0），待控制线程 setMasterGain 后一阶 ramp 到目标，避免开机爆音。
     * DC 阻断系数按设备采样率计算（cutoff 2Hz），清空 DC 历史。 */
    memset(&core->master, 0, sizeof(core->master));
    core->master.dc_coeff = expf(-2.0f * SOUNDIST_PI * SOUNDIST_DC_CUTOFF_HZ / (float)core->device_sample_rate);

    r = ma_device_start(&core->device);
    if (r != MA_SUCCESS) {
        soundist_set_error(core, r, ma_result_description(r));
        ma_device_uninit(&core->device);
        soundist_state_set(core, SOUNDIST_STATE_FAILED);
        return (jint)r;
    }
    core->device_started = 1;
    soundist_state_set(core, SOUNDIST_STATE_RUNNING);
    soundist_set_error(core, MA_SUCCESS, "OK");
    return 0;
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeStopDevice(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    int st = soundist_state_get(core);
    if (st != SOUNDIST_STATE_RUNNING && st != SOUNDIST_STATE_STARTING && st != SOUNDIST_STATE_STOPPING) {
        return 0; /* 幂等：未在运行，无需停止 */
    }
    soundist_state_set(core, SOUNDIST_STATE_STOPPING);
    if (soundist_stop_device(core) != 0) {
        /* 停止失败：设备可能仍在运行，跳过 free 并进入 FAILED。 */
        soundist_state_set(core, SOUNDIST_STATE_FAILED);
        return -3;
    }
    /* 设备已停，回调不再运行：安全释放全部 PCM（含未退休的）。 */
    soundist_free_all_pcm(core);
    core->frame_count = 0;
    soundist_state_set(core, SOUNDIST_STATE_INITIALIZED);
    return 0;
}

/* 注册一个 native-owned 设备格式轨道（pcm 已由调用方分配并持有）：更新 shadow + 投 ADD_TRACK。
 * 队列满时释放新 PCM 并返回 -1（调用方决定重试）；成功返回 0。供 nativeAddTrack / nativeAddTrackFromEncoded 共用。 */
static int soundist_register_track(SoundistAudioCore* core, int id, float* pcm, uint64_t frames, uint32_t channels) {
    SoundistControlTrack* ctl = &core->control_tracks[id];
    float* old_pcm = ctl->active ? ctl->pcm : NULL;
    uint32_t gen = ctl->generation + 1;

    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type   = SOUNDIST_CMD_ADD_TRACK;
    cmd.arg0   = id;
    cmd.arg1   = (int64_t)(intptr_t)pcm;
    cmd.arg2   = (int64_t)frames;
    cmd.arg3   = (int32_t)channels;
    cmd.arg4   = gen;
    cmd.retire = (int64_t)(intptr_t)old_pcm;

    int rc = soundist_cmdq_post(&core->cmdq, &cmd);
    if (rc != 0) {
        free(pcm);
        return rc;
    }
    ctl->active     = 1;
    ctl->generation = gen;
    ctl->pcm        = pcm;
    return 0;
}

/* 注册环境声轨道。pcm 为 jfloatArray（设备格式交错 float32）；native 复制并持有，回调只读。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeAddTrack(JNIEnv* env, jobject thiz, jlong handle, jint id, jfloatArray pcm, jlong frames, jint channels) {
    (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS || pcm == NULL || frames <= 0 || channels <= 0) return -4;
    if (soundist_state_get(core) != SOUNDIST_STATE_RUNNING) return -2; /* 设备未运行 */
    soundist_retire_drain(&core->retireq);

    jsize len = (*env)->GetArrayLength(env, pcm);
    if (len < 0 || (jlong)len < frames * (jlong)channels) return -4; /* 数据不足 */
    float* src = (*env)->GetFloatArrayElements(env, pcm, NULL);
    if (src == NULL) return -3;
    size_t bytes = (size_t)(frames * (jlong)channels) * sizeof(float);
    float* dst = (float*)calloc(1, bytes);
    if (dst == NULL) {
        (*env)->ReleaseFloatArrayElements(env, pcm, src, JNI_ABORT);
        return -5; /* OOM */
    }
    memcpy(dst, src, bytes);
    (*env)->ReleaseFloatArrayElements(env, pcm, src, JNI_ABORT);

    return (jint)soundist_register_track(core, id, dst, (uint64_t)frames, (uint32_t)channels);
}

/* 从编码音频（MP3/WAV/FLAC，miniaudio 内建解码）解码到设备格式并注册为循环环境声轨。
 *
 * 所有权规则（关键）：ma_decoder_init_memory 不复制输入，decoder 在整个解码期都会读入参指针。
 * 因此先把 jbyteArray 复制到 native-owned encoded buffer，Release 掉 JNI 数组，解码全程只读
 * 该 native buffer，ma_decoder_uninit 之后再 free——绝不出现 decoder 引用已 Release 的 JNI 指针。
 * 所有错误/OOM/超长/提前返回路径都正确释放 encoded buffer 与 pcm。
 *
 * 解码在控制线程（非实时）完成：ma_decoder 直接输出 f32@设备采样率@设备声道，复用注册路径
 * （native-owned + retire-ack）。增长式读入兼容 VBR/未知长度；区分 MA_AT_END（正常结束）与真实
 * 解码错误（真实错误不把部分 PCM 当成功）。
 *
 * 返回 0 成功；-2 未运行/非法句柄；-3 解码失败；-4 参数非法；-5 OOM；-6 解码帧数超上限。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeAddTrackFromEncoded(JNIEnv* env, jobject thiz, jlong handle, jint id, jbyteArray encoded, jint channels, jint sampleRate) {
    (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS || encoded == NULL) return -4;
    if (soundist_state_get(core) != SOUNDIST_STATE_RUNNING) return -2;
    soundist_retire_drain(&core->retireq);

    jsize len = (*env)->GetArrayLength(env, encoded);
    if (len <= 0) return -4;
    jbyte* src = (*env)->GetByteArrayElements(env, encoded, NULL);
    if (src == NULL) return -3;

    /* 1. native-owned 编码数据副本：decoder 全程读它，uninit 后释放。 */
    unsigned char* encodedBuf = (unsigned char*)malloc((size_t)len);
    if (encodedBuf == NULL) {
        (*env)->ReleaseByteArrayElements(env, encoded, src, JNI_ABORT);
        soundist_set_error(core, MA_OUT_OF_MEMORY, "encoded buffer alloc");
        return -5;
    }
    memcpy(encodedBuf, src, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, encoded, src, JNI_ABORT);

    uint32_t outCh = (uint32_t)(channels > 0 ? channels : 2);
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, outCh, (ma_uint32)(sampleRate > 0 ? sampleRate : 44100));
    ma_decoder decoder;
    ma_result r = ma_decoder_init_memory(encodedBuf, (size_t)len, &cfg, &decoder);
    if (r != MA_SUCCESS) {
        free(encodedBuf);
        soundist_set_error(core, r, ma_result_description(r));
        return -3;
    }

    /* 2. 增长式解码到 native-owned PCM。cap 扩容与帧数上限都检查 size_t 溢出。 */
    size_t cap = 16384;
    float* pcm = (float*)malloc(cap * outCh * sizeof(float));
    if (pcm == NULL) { ma_decoder_uninit(&decoder); free(encodedBuf); return -5; }
    size_t totalFrames = 0;
    ma_result readResult = MA_SUCCESS;
    for (;;) {
        if (totalFrames + 4096 > cap) {
            if (cap > SIZE_MAX / 2) { /* 翻倍溢出 */ free(pcm); ma_decoder_uninit(&decoder); free(encodedBuf); return -6; }
            size_t ncap = cap * 2;
            if (ncap > SIZE_MAX / (outCh * sizeof(float))) { free(pcm); ma_decoder_uninit(&decoder); free(encodedBuf); return -6; }
            float* np = (float*)realloc(pcm, ncap * outCh * sizeof(float));
            if (np == NULL) { free(pcm); ma_decoder_uninit(&decoder); free(encodedBuf); return -5; }
            pcm = np; cap = ncap;
        }
        if (totalFrames + 4096 > SOUNDIST_MAX_DECODED_FRAMES) {
            /* 超长文件：明确错误，不默默截断也不 OOM。 */
            soundist_set_error(core, MA_INVALID_ARGS, "decoded track exceeds frame cap");
            free(pcm); ma_decoder_uninit(&decoder); free(encodedBuf);
            return -6;
        }
        ma_uint64 got = 0;
        readResult = ma_decoder_read_pcm_frames(&decoder, pcm + totalFrames * outCh, cap - totalFrames, &got);
        totalFrames += (size_t)got;
        if (readResult != MA_SUCCESS) break; /* MA_AT_END（正常结束）或真实错误 */
        if (got == 0) break; /* 防御：无进展则退出，避免死循环 */
    }
    ma_decoder_uninit(&decoder);
    free(encodedBuf);

    if (readResult != MA_SUCCESS && readResult != MA_AT_END) {
        /* 真实解码错误：不把部分 PCM 当成功。 */
        char msg[256];
        snprintf(msg, sizeof(msg), "decode read: %s", ma_result_description(readResult));
        soundist_set_error(core, readResult, msg);
        free(pcm);
        return -3;
    }
    if (totalFrames == 0) {
        soundist_set_error(core, MA_NO_DATA_AVAILABLE, "decoded 0 frames");
        free(pcm);
        return -3;
    }

    return (jint)soundist_register_track(core, id, pcm, (uint64_t)totalFrames, outCh);
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeRemoveTrack(JNIEnv* env, jobject thiz, jlong handle, jint id) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS) return -4;
    soundist_retire_drain(&core->retireq);

    SoundistControlTrack* ctl = &core->control_tracks[id];
    if (!ctl->active || ctl->pcm == NULL) return 0; /* 幂等：没有该轨道 */

    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type   = SOUNDIST_CMD_REMOVE_TRACK;
    cmd.arg0   = id;
    cmd.arg4   = ctl->generation;
    cmd.retire = (int64_t)(intptr_t)ctl->pcm;

    int rc = soundist_cmdq_post(&core->cmdq, &cmd);
    if (rc != 0) {
        return rc; /* 满：关键 remove 不得静默丢，返回错误让调用方重试 */
    }
    ctl->active     = 0;
    ctl->pcm        = NULL;
    ctl->generation++; /* 使任何在途的旧 remove 无法再匹配 */
    return 0;
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetTrackGain(JNIEnv* env, jobject thiz, jlong handle, jint id, jfloat gain) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS) return -4;
    soundist_retire_drain(&core->retireq);
    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type = SOUNDIST_CMD_SET_GAIN;
    cmd.arg0 = id;
    cmd.arg1 = (int64_t)soundist_float_to_bits(gain);
    return (jint)soundist_cmdq_post(&core->cmdq, &cmd);
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetTrackPan(JNIEnv* env, jobject thiz, jlong handle, jint id, jfloat pan) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS) return -4;
    soundist_retire_drain(&core->retireq);
    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type = SOUNDIST_CMD_SET_PAN;
    cmd.arg0 = id;
    cmd.arg1 = (int64_t)soundist_float_to_bits(pan);
    return (jint)soundist_cmdq_post(&core->cmdq, &cmd);
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetTrackPlaying(JNIEnv* env, jobject thiz, jlong handle, jint id, jint playing) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    if (id < 0 || id >= SOUNDIST_MAX_TRACKS) return -4;
    soundist_retire_drain(&core->retireq);
    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type = SOUNDIST_CMD_SET_PLAYING;
    cmd.arg0 = id;
    cmd.arg1 = (playing != 0) ? 1 : 0;
    return (jint)soundist_cmdq_post(&core->cmdq, &cmd);
}

/* 设置 MasterChain 目标 master 增益（masterVolume × ambientVolume × duckMultiplier，静音/duck 时为 0）。
 * 回调内一阶 ramp 平滑到目标，无爆音。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetMasterGain(JNIEnv* env, jobject thiz, jlong handle, jfloat gain) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    soundist_retire_drain(&core->retireq);
    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type = SOUNDIST_CMD_SET_MASTER_GAIN;
    cmd.arg1 = (int64_t)soundist_float_to_bits(gain);
    return (jint)soundist_cmdq_post(&core->cmdq, &cmd);
}

/* ---------- JNI 绑定（阶段 3：采样器） ---------- */

/* 注册采样。slot 下标由控制线程（NativeAudioCore）分配并传入；native 复制并持有。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeAddSample(JNIEnv* env, jobject thiz, jlong handle, jint index, jfloatArray pcm, jlong frames, jint channels, jint sampleRate, jfloat baseFrequency) {
    (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (index < 0 || index >= SOUNDIST_MAX_SAMPLES || pcm == NULL || frames <= 0 || channels <= 0) return -4;
    if (soundist_state_get(core) != SOUNDIST_STATE_RUNNING) return -2;
    soundist_retire_drain(&core->retireq);

    jsize len = (*env)->GetArrayLength(env, pcm);
    if (len < 0 || (jlong)len < frames * (jlong)channels) return -4;
    float* src = (*env)->GetFloatArrayElements(env, pcm, NULL);
    if (src == NULL) return -3;
    size_t bytes = (size_t)(frames * (jlong)channels) * sizeof(float);
    float* dst = (float*)calloc(1, bytes);
    if (dst == NULL) {
        (*env)->ReleaseFloatArrayElements(env, pcm, src, JNI_ABORT);
        return -5;
    }
    memcpy(dst, src, bytes);
    (*env)->ReleaseFloatArrayElements(env, pcm, src, JNI_ABORT);

    SoundistControlSample* ctl = &core->control_samples[index];
    float* old_pcm = ctl->active ? ctl->pcm : NULL;
    uint32_t gen = ctl->generation + 1;

    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type   = SOUNDIST_CMD_ADD_SAMPLE;
    cmd.arg0   = index;
    cmd.arg1   = (int64_t)(intptr_t)dst;
    cmd.arg2   = (int64_t)frames;
    cmd.arg3   = channels;
    cmd.arg4   = gen;
    cmd.retire = (int64_t)(intptr_t)old_pcm;
    cmd.f0     = (float)(sampleRate > 0 ? sampleRate : 0);
    cmd.f1     = baseFrequency;

    int rc = soundist_cmdq_post(&core->cmdq, &cmd);
    if (rc != 0) {
        free(dst);
        return rc;
    }
    ctl->active     = 1;
    ctl->generation = gen;
    ctl->pcm        = dst;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeRemoveSample(JNIEnv* env, jobject thiz, jlong handle, jint index) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    if (index < 0 || index >= SOUNDIST_MAX_SAMPLES) return -4;
    soundist_retire_drain(&core->retireq);

    SoundistControlSample* ctl = &core->control_samples[index];
    if (!ctl->active || ctl->pcm == NULL) return 0;

    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type   = SOUNDIST_CMD_REMOVE_SAMPLE;
    cmd.arg0   = index;
    cmd.arg4   = ctl->generation;
    cmd.retire = (int64_t)(intptr_t)ctl->pcm;

    int rc = soundist_cmdq_post(&core->cmdq, &cmd);
    if (rc != 0) return rc;
    ctl->active     = 0;
    ctl->pcm        = NULL;
    ctl->generation++;
    return 0;
}

/* 触发采样 one-shot（正式采样器，阶段 C）：frequency 决定音高（frequency/baseFrequency），
 * gain/pan/attack/release/cutoff 一起下发。资源缺失（控制 shadow 未激活）返回 -4，不静默变正弦。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeTriggerSample(JNIEnv* env, jobject thiz, jlong handle, jint index, jfloat frequency, jfloat gain, jfloat pan, jfloat attackSeconds, jfloat releaseSeconds, jfloat cutoffHz) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    if (index < 0 || index >= SOUNDIST_MAX_SAMPLES) return -4;
    if (!core->control_samples[index].active) return -4; /* 资源缺失：明确错误 */
    soundist_retire_drain(&core->retireq);

    float sr = (float)core->device_sample_rate;
    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type = SOUNDIST_CMD_TRIGGER_SAMPLE;
    cmd.arg0 = index;
    cmd.arg1 = (int64_t)soundist_float_to_bits(frequency);
    cmd.arg2 = (int64_t)(attackSeconds > 0.0f ? (attackSeconds * sr) : 0.0f);
    cmd.arg3 = (int32_t)(releaseSeconds > 0.0f ? (releaseSeconds * sr) : 1024.0f); /* 默认 ~21ms */
    cmd.arg4 = (uint32_t)soundist_float_to_bits(cutoffHz);
    cmd.f0 = gain;
    cmd.f1 = pan;
    return (jint)soundist_cmdq_post(&core->cmdq, &cmd);
}

/* ---------- JNI 绑定（阶段 4：生成 voice 骨架） ---------- */

JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeAddVoice(JNIEnv* env, jobject thiz, jlong handle, jfloat frequency, jfloat gain, jfloat pan, jlong durationFrames) {
    (void)env; (void)thiz;
    SoundistAudioCore* core = (SoundistAudioCore*)(intptr_t)handle;
    if (core == NULL) return -2;
    if (soundist_state_is_dead(core)) return -2;
    if (frequency <= 0.0f) return -4;
    soundist_retire_drain(&core->retireq);
    SoundistCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.type = SOUNDIST_CMD_TRIGGER_VOICE;
    cmd.arg0 = 0;
    cmd.arg1 = (int64_t)soundist_float_to_bits(frequency);
    cmd.arg2 = (int64_t)(durationFrames > 0 ? durationFrames : 44100);
    cmd.f0   = gain;
    cmd.f1   = pan;
    return (jint)soundist_cmdq_post(&core->cmdq, &cmd);
}
