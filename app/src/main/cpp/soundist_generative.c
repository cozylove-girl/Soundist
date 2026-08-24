/*
 * soundist_generative.c — 生成电台 native 迁移（阶段 D）。
 *
 * 本文件是第一片：确定性调度决策核心。逐行对照
 * NativeGeneratedAudioRenderer.kt 的 sectionState / applyTimelineScene / shouldScheduleAtTick /
 * eventChance / gatePasses / schedulerGolden 及 10 个预设的 PROFILES/PLANS/层数据，
 * 目标是与 Kotlin 的 schedulerGolden(stationId, ticks) 产出完全一致的黄金调度字符串。
 *
 * 尚未迁移（后续片）：synthRaw 逐音色振荡器、Voice 包络/滤波、MasterChain 压缩/压限、
 * 完整渲染循环、JNI 渲染入口。旧 NativeGeneratedAudioRenderer 仍是正式实现与回退。
 */

#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#define GEN_PI 3.14159265358979323846

/* ---------- 确定性随机（对照 Mulberry32 / fnv1a） ---------- */

typedef struct { uint32_t s; } GenMulberry32;

static double gen_mulberry32_next(GenMulberry32* r) {
    /* Kotlin Mulberry32.next()：seed += 0x6d2b79f5；v = (v ^ v>>>15) * (v | 1)；
     * v = v ^ (v + ((v ^ v>>>7) * (v | 61)))；返回 (v ^ v>>>14) 的 32 位值 / 2^32。 */
    r->s += 0x6d2b79f5u;
    uint32_t v = r->s;
    v = (v ^ (v >> 15)) * (v | 1u);
    v = v ^ (v + ((v ^ (v >> 7)) * (v | 61u)));
    v = v ^ (v >> 14);
    return (double)v / 4294967296.0;
}

static uint32_t gen_fnv1a(const char* s) {
    uint32_t h = 0x811c9dc5u;
    for (; *s; s++) { h ^= (uint8_t)(*s); h *= 16777619u; }
    return h;
}

static char* gen_strdup(const char* s) {
    if (s == NULL) return NULL;
    size_t n = strlen(s) + 1;
    char* out = (char*)malloc(n);
    if (out != NULL) memcpy(out, s, n);
    return out;
}

/* 从 String[] 的第 i 项拷贝一个 UTF-8 字符串（NULL 元素 → NULL），返回堆内存（调用方释放）。 */
static char* gen_jstring_dup(JNIEnv* env, jobjectArray arr, jsize i) {
    jstring s = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
    if (s == NULL) return NULL;
    const char* c = (*env)->GetStringUTFChars(env, s, NULL);
    char* out = (c != NULL) ? gen_strdup(c) : NULL;
    if (c != NULL) (*env)->ReleaseStringUTFChars(env, s, c);
    (*env)->DeleteLocalRef(env, s);
    return out;
}

/* ---------- 枚举与结构 ---------- */

typedef enum { GEN_KIND_NOTES = 0, GEN_KIND_RHYTHM, GEN_KIND_ATMOSPHERE, GEN_KIND_VOICE, GEN_KIND_AMBIENT } GenKind;
typedef enum { GEN_REL_INDEPENDENT = 0, GEN_REL_FOLLOW, GEN_REL_AVOID, GEN_REL_ALTERNATE } GenRelationship;
typedef enum { GEN_ARC_BREATHING = 0, GEN_ARC_STEADY, GEN_ARC_JOURNEY } GenArc;
typedef enum { GEN_REG_LOW = 0, GEN_REG_MIDDLE, GEN_REG_HIGH, GEN_REG_WIDE } GenRegister;
typedef enum { GEN_SCALE_PENTATONIC = 0, GEN_SCALE_MAJOR, GEN_SCALE_MINOR, GEN_SCALE_DORIAN } GenScale;

typedef struct {
    int tempo; int density; int variation; double entryDelay; double eventDuration;
    int gain; int ambientResponse; int adaptive;
} GenSettings;

typedef struct { double color, center, spread, resonance; } GenNoise;

typedef struct {
    double root;
    const int* intervals; int intervalCount;
    double brightness;
    int cutoff;
    double room;
    const GenNoise* noise; /* nullable，对照 Profile.noise（仅 night-train rail-click 用） */
} GenProfile;

typedef struct {
    int cycle;
    const int* notes; int notesCount;
    const int* rhythm; int rhythmCount;
    const int* atmosphere; int atmosphereCount;
    const int* voice; int voiceCount;
    const int* signal; int signalCount;
} GenPlan;

typedef struct GenLayer {
    const char* id;
    GenKind kind;
    const char* timbre;
    int volume;
    int probability;
    int density;
    double duration;
    double attack;
    double release;
    double pan;
    double entryDelay;
    int rhythm;
    GenRelationship relationship;
    const char* target;
    GenRegister reg;
    GenScale scale;
    int ambientResponse;
    int enabled;
    int solo;
    int intro;
} GenLayer;

typedef struct {
    const char* id; /* 元数据（对照 Scene.id），DSP 不读，仅保真 */
    int durationMinutes;
    double energy, density, brightness, space;
} GenScene;

typedef struct {
    const char* seed;
    int tempo;
    int sectionMinutes;
    GenArc arc;
    GenProfile profile;
    GenPlan plan;
    GenLayer* layers; int layerCount;
    GenScene* scenes; int sceneCount; /* 默认 NULL/0：applyTimelineScene 直接返回 state */
} GenArrangement;

/* 释放 JNI 拷贝的层字符串（id/timbre/target）。layersOwned=1 时调用；内置层指向静态字面量不可释放。 */
static void gen_free_layer_strings(GenLayer* layers, int count) {
    for (int i = 0; i < count; i++) {
        free((char*)layers[i].id);
        free((char*)layers[i].timbre);
        free((char*)layers[i].target);
    }
}

/* 释放 JNI 拷贝的场景（含 id 字符串）。 */
static void gen_free_scenes(GenScene* scenes, int count) {
    for (int i = 0; i < count; i++) free((char*)scenes[i].id);
    free(scenes);
}

typedef struct {
    double energy, density; int registerShift; double cutoff; int chordShift;
    double durationScale, panSpread, eventGate;
    double notesWeight, rhythmWeight, atmosphereWeight, voiceWeight, noiseWeight;
} GenSection;

typedef struct {
    double level, water, air, warm, city, noise;
} GenAmbientBias;

typedef struct {
    double last[64]; int lastCount;
    const char* lastId[64];
    double lastMusical;
    const char* lastLayerId;
} GenSchedulerMemory;

/* ---------- 工具 ---------- */

static double gen_clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }

static int gen_int_contains(const int* arr, int n, int v) {
    for (int i = 0; i < n; i++) if (arr[i] == v) return 1;
    return 0;
}

static int gen_is_signal_timbre(const char* t) {
    return strcmp(t, "sonar-ping") == 0 || strcmp(t, "glass-bell") == 0;
}

static double gen_layer_weight(const GenSection* s, GenKind kind) {
    switch (kind) {
        case GEN_KIND_NOTES: return s->notesWeight;
        case GEN_KIND_RHYTHM: return s->rhythmWeight;
        case GEN_KIND_ATMOSPHERE: return s->atmosphereWeight;
        case GEN_KIND_VOICE: return s->voiceWeight;
        default: return 0.0;
    }
}

/* ---------- sectionState + applyTimelineScene ---------- */

/* 取当前生效 scene（按时长游标），忽略 durationMinutes <= 0 的无效 scene；无有效 scene 返回 NULL。
 * 对照 Kotlin applyTimelineScene 里的 scenes.filter{>0} + firstOrNull{...} ?: scenes.last()。 */
static const GenScene* gen_active_scene(const GenArrangement* a, double elapsed) {
    int count = 0;
    double totalSeconds = 0.0;
    for (int i = 0; i < a->sceneCount; i++) {
        if (a->scenes[i].durationMinutes > 0) { count++; totalSeconds += a->scenes[i].durationMinutes * 60.0; }
    }
    if (count == 0) return NULL;
    if (totalSeconds < 1.0) totalSeconds = 1.0;
    double cursor = fmod(elapsed, totalSeconds);
    for (int i = 0; i < a->sceneCount; i++) {
        const GenScene* sc = &a->scenes[i];
        if (sc->durationMinutes <= 0) continue;
        double duration = sc->durationMinutes * 60.0;
        if (cursor < duration) return sc;
        cursor -= duration;
    }
    for (int i = a->sceneCount - 1; i >= 0; i--) if (a->scenes[i].durationMinutes > 0) return &a->scenes[i];
    return NULL;
}

/* 对照 Kotlin applyTimelineScene：scene 参数 0-100，/50 归一后叠加到 base state。 */
static GenSection gen_apply_timeline_scene(GenSection state, const GenArrangement* a, double elapsed) {
    const GenScene* scene = gen_active_scene(a, elapsed);
    if (scene == NULL) return state;
    double energy = gen_clamp(scene->energy / 50.0, 0.2, 1.8);
    double density = gen_clamp(scene->density / 50.0, 0.2, 1.8);
    double brightness = gen_clamp(scene->brightness / 50.0, 0.35, 1.65);
    double space = gen_clamp(scene->space / 50.0, 0.35, 1.65);
    state.energy = gen_clamp(state.energy * energy, 0.08, 1.4);
    state.density = gen_clamp(state.density * density, 0.08, 1.6);
    state.cutoff = gen_clamp(state.cutoff * brightness, 0.45, 1.5);
    state.durationScale = gen_clamp(state.durationScale * (0.72 + space * 0.28), 0.45, 1.8);
    state.panSpread = gen_clamp(state.panSpread * space, 0.25, 1.5);
    state.eventGate = gen_clamp(state.eventGate * density, 0.08, 1.35);
    /* registerShift / chordShift / 各层权重不变 */
    return state;
}

/* 对照 Kotlin currentSceneBrightness：返回当前 scene 原始亮度（0-100），供热更新滤波 ramp 用。 */
static double gen_current_scene_brightness(const GenArrangement* a, double elapsed) {
    const GenScene* scene = gen_active_scene(a, elapsed);
    return scene != NULL ? scene->brightness : 0.0;
}

static GenSection gen_section_state(const GenArrangement* a, double seconds) {
    double elapsed = seconds > 0.0 ? seconds : 0.0;
    double period = 90.0 > (a->sectionMinutes * 60.0) ? 90.0 : (double)(a->sectionMinutes * 60.0);
    double phase = fmod(elapsed, period) / period;
    int sectionIndex = (int)(elapsed / period);
    GenSection base;
    memset(&base, 0, sizeof(base));

    if (a->arc == GEN_ARC_STEADY) {
        double phrasePhase = fmod(elapsed, 32.0) / 32.0;
        double phraseRest = phrasePhase > 0.92 ? 0.68 : 1.0;
        base.energy = 0.66 + sin(elapsed / 31.0) * 0.024;
        base.density = 0.9; base.registerShift = 0; base.cutoff = 0.95;
        base.chordShift = (sectionIndex % 4 == 3) ? 2 : 0;
        base.durationScale = 1.0; base.panSpread = 0.72; base.eventGate = phraseRest;
        base.notesWeight = 1.0; base.rhythmWeight = 0.92; base.atmosphereWeight = 0.96;
        base.voiceWeight = 0.72; base.noiseWeight = 0.64;
    } else if (a->arc == GEN_ARC_BREATHING) {
        double breathPeriod = gen_clamp(period / 8.0, 22.0, 42.0);
        double breath = (cos(elapsed / breathPeriod * GEN_PI * 2.0 + GEN_PI) + 1.0) / 2.0;
        double shaped = breath * breath * (3.0 - 2.0 * breath);
        base.energy = 0.4 + shaped * 0.42;
        base.density = 0.5 + shaped * 0.58;
        base.registerShift = shaped > 0.78 ? 2 : (shaped < 0.16 ? -2 : 0);
        base.cutoff = 0.8 + shaped * 0.34;
        base.chordShift = (sectionIndex % 2 == 0) ? 0 : 5;
        base.durationScale = 1.22 - shaped * 0.2;
        base.panSpread = 0.72 + shaped * 0.38;
        base.eventGate = shaped < 0.1 ? 0.16 : 0.58 + shaped * 0.5;
        base.notesWeight = 0.34 + shaped * 0.94;
        base.rhythmWeight = 0.2 + shaped * 0.68;
        base.atmosphereWeight = 1.12 - shaped * 0.14;
        base.voiceWeight = 0.58 + (1.0 - shaped) * 0.34;
        base.noiseWeight = 0.35 + shaped * 0.28;
    } else { /* JOURNEY */
        if (phase < 0.2) {
            base.energy = 0.42 + phase * 0.72; base.density = 0.5 + phase * 0.9; base.registerShift = -5;
            base.cutoff = 0.76; base.chordShift = 0; base.durationScale = 1.24; base.panSpread = 0.62;
            base.eventGate = 0.72; base.notesWeight = 0.44; base.rhythmWeight = 0.18;
            base.atmosphereWeight = 1.24; base.voiceWeight = 0.36; base.noiseWeight = 0.28;
        } else if (phase < 0.5) {
            base.energy = 0.58 + (phase - 0.2) * 0.64; base.density = 0.74 + (phase - 0.2) * 0.72; base.registerShift = 0;
            base.cutoff = 1.02; base.chordShift = 5; base.durationScale = 1.04; base.panSpread = 0.9;
            base.eventGate = 0.94; base.notesWeight = 1.0; base.rhythmWeight = 0.58;
            base.atmosphereWeight = 0.96; base.voiceWeight = 0.68; base.noiseWeight = 0.62;
        } else if (phase < 0.76) {
            base.energy = 0.82; base.density = 1.06; base.registerShift = 5;
            base.cutoff = 1.2; base.chordShift = 7; base.durationScale = 0.9; base.panSpread = 1.16;
            base.eventGate = 1.08; base.notesWeight = 1.08; base.rhythmWeight = 1.16;
            base.atmosphereWeight = 0.78; base.voiceWeight = 0.88; base.noiseWeight = 0.9;
        } else {
            base.energy = 0.74 - (phase - 0.76) * 1.08; base.density = 0.78 - (phase - 0.76) * 0.72; base.registerShift = -2;
            base.cutoff = 0.86; base.chordShift = 3; base.durationScale = 1.28; base.panSpread = 0.68;
            base.eventGate = 0.64; base.notesWeight = 0.52; base.rhythmWeight = 0.14;
            base.atmosphereWeight = 1.16; base.voiceWeight = 0.56; base.noiseWeight = 0.24;
        }
    }
    return gen_apply_timeline_scene(base, a, elapsed);
}

/* ---------- 调度闸门 ---------- */

static int gen_should_schedule_at_tick(const GenLayer* layer, const char* timbre, int tick, const GenPlan* plan) {
    int phase = tick % plan->cycle;
    if (layer->rhythm > 1 && tick % layer->rhythm != 0) return 0;
    switch (layer->kind) {
        case GEN_KIND_RHYTHM: return gen_int_contains(plan->rhythm, plan->rhythmCount, phase);
        case GEN_KIND_ATMOSPHERE: return gen_int_contains(plan->atmosphere, plan->atmosphereCount, phase);
        case GEN_KIND_VOICE: return gen_int_contains(plan->voice, plan->voiceCount, phase);
        default: return gen_is_signal_timbre(timbre)
            ? gen_int_contains(plan->signal, plan->signalCount, phase)
            : gen_int_contains(plan->notes, plan->notesCount, phase);
    }
}

static double gen_event_chance(const GenLayer* layer, const char* timbre, const GenSection* state,
                               const GenAmbientBias* bias, const GenSettings* settings) {
    double response = settings->adaptive
        ? (layer->ambientResponse / 100.0) * (settings->ambientResponse / 100.0)
        : 0.0;
    double timbreBias = 0.0;
    if (strcmp(timbre, "bamboo-flute") == 0 || strcmp(timbre, "vsco-flute") == 0 || strcmp(timbre, "vsco-harp") == 0) timbreBias = bias->air * 0.34;
    else if (strcmp(timbre, "rail-click") == 0 || strcmp(timbre, "muted-pulse") == 0) timbreBias = bias->city * 0.42;
    else if (strcmp(timbre, "sonar-ping") == 0 || strcmp(timbre, "deep-drone") == 0) timbreBias = bias->water * 0.38;
    else if (strcmp(timbre, "warm-strings") == 0 || strcmp(timbre, "electric-piano") == 0 || strcmp(timbre, "vsco-cello") == 0 || strcmp(timbre, "vsco-violin") == 0 || strcmp(timbre, "vsco-upright-piano") == 0) timbreBias = bias->warm * 0.22;

    double masking = bias->noise * response * 0.36;
    double density = gen_clamp(layer->density * settings->density / 42.0 * state->density * (1.0 - masking), 4.0, 100.0) / 100.0;
    return gen_clamp(layer->probability * density * state->energy * state->eventGate * gen_layer_weight(state, layer->kind) * (1.0 + timbreBias * response), 1.0, 96.0) / 100.0;
}

static double gen_memory_last(GenSchedulerMemory* m, const char* id) {
    for (int i = 0; i < m->lastCount; i++) if (strcmp(m->lastId[i], id) == 0) return m->last[i];
    return -INFINITY;
}

static void gen_memory_set(GenSchedulerMemory* m, const char* id, double at) {
    for (int i = 0; i < m->lastCount; i++) {
        if (strcmp(m->lastId[i], id) == 0) { m->last[i] = at; return; }
    }
    if (m->lastCount < 64) { m->lastId[m->lastCount] = id; m->last[m->lastCount] = at; m->lastCount++; }
}

static int gen_gate_passes(const GenLayer* layer, const char* timbre, double at, const GenSection* state,
                           const GenAmbientBias* bias, GenMulberry32* random, const GenSettings* settings,
                           GenSchedulerMemory* memory) {
    double lastEvent = gen_memory_last(memory, layer->id);
    int introFirstFire = layer->intro && isinf(lastEvent);
    if (at < (introFirstFire ? 0.0 : settings->entryDelay + layer->entryDelay)) return 0;
    /* Kotlin's nullable target lookup falls back to lastMusical when the target
     * layer has not fired yet; do not treat a missing target as -INF here. */
    double targetEvent = memory->lastMusical;
    if (layer->target != NULL) {
        double targetLast = gen_memory_last(memory, layer->target);
        if (isfinite(targetLast)) targetEvent = targetLast;
    }
    double cooldown;
    if (layer->kind == GEN_KIND_ATMOSPHERE) cooldown = (4.0 > layer->duration * 0.72) ? 4.0 : layer->duration * 0.72;
    else if (layer->relationship == GEN_REL_AVOID) cooldown = 1.8;
    else cooldown = 0.3;
    if (at - lastEvent < cooldown) return 0;
    if (layer->relationship == GEN_REL_AVOID && at - targetEvent < 0.9) return 0;
    if (layer->relationship == GEN_REL_ALTERNATE && (!isfinite(targetEvent) || targetEvent <= lastEvent || at - targetEvent > 4.2)) return 0;
    if (layer->relationship == GEN_REL_FOLLOW && (!isfinite(targetEvent) || at - targetEvent > 2.6)) return 0;
    double chance = introFirstFire ? 1.0 : gen_event_chance(layer, timbre, state, bias, settings);
    if (gen_mulberry32_next(random) > chance) return 0;
    gen_memory_set(memory, layer->id, at);
    memory->lastMusical = at;
    memory->lastLayerId = layer->id;
    return 1;
}

/* ---------- 预设数据 ---------- */

static int GEN_ARR_I0[] = {0, 7, 12, 17, 19};
static int GEN_ARR_I1[] = {0, 3, 7, 10, 14};
static int GEN_ARR_I2[] = {0, 4, 7, 11, 14};
static int GEN_ARR_I3[] = {0, 5, 7, 12};
static int GEN_ARR_I4[] = {0, 3, 7, 10};
static int GEN_ARR_I5[] = {0, 4, 7, 9, 12};
static int GEN_ARR_I6[] = {0, 5, 7, 12, 17};
static int GEN_ARR_I7[] = {0, 2, 7, 9, 14, 16};
static int GEN_ARR_I8[] = {0, 2, 5, 7, 9, 12};
static int GEN_ARR_I9[] = {0, 2, 4, 7, 9, 12, 14};

static int GEN_PLAN_N0[] = {18, 50}; static int GEN_PLAN_R0[] = {0, 32}; static int GEN_PLAN_A0[] = {0, 32}; static int GEN_PLAN_V0[] = {24}; static int GEN_PLAN_S0[] = {20, 52};
static int GEN_PLAN_N1[] = {0, 7, 13, 22, 29}; static int GEN_PLAN_R1[] = {0, 8, 16, 24}; static int GEN_PLAN_A1[] = {0, 16}; static int GEN_PLAN_V1[] = {12}; static int GEN_PLAN_S1[] = {11, 27};
static int GEN_PLAN_N2[] = {0, 9, 17, 30, 41}; static int GEN_PLAN_R2[] = {0, 12, 24, 36}; static int GEN_PLAN_A2[] = {0, 24}; static int GEN_PLAN_V2[] = {16, 40}; static int GEN_PLAN_S2[] = {14, 38};
static int GEN_PLAN_N3[] = {0, 8}; static int GEN_PLAN_R3[] = {0, 4, 8, 12}; static int GEN_PLAN_A3[] = {0}; static int GEN_PLAN_V3[] = {8}; static int GEN_PLAN_S3[] = {6, 14};
static int GEN_PLAN_N4[] = {6, 14}; static int GEN_PLAN_R4[] = {0, 2, 4, 6, 8, 10, 12, 14}; static int GEN_PLAN_A4[] = {0}; static int GEN_PLAN_V4[] = {8}; static int GEN_PLAN_S4[] = {6, 14};
static int GEN_PLAN_N5[] = {0, 8, 18, 26}; static int GEN_PLAN_R5[] = {0, 4, 8, 12, 16, 20, 24, 28}; static int GEN_PLAN_A5[] = {0, 16}; static int GEN_PLAN_V5[] = {12, 28}; static int GEN_PLAN_S5[] = {10, 24};
static int GEN_PLAN_N6[] = {14, 46}; static int GEN_PLAN_R6[] = {0, 32}; static int GEN_PLAN_A6[] = {0, 32}; static int GEN_PLAN_V6[] = {24, 56}; static int GEN_PLAN_S6[] = {16, 48};
static int GEN_PLAN_N7[] = {0, 3, 10, 15, 21}; static int GEN_PLAN_R7[] = {0, 6, 12, 18}; static int GEN_PLAN_A7[] = {0, 12}; static int GEN_PLAN_V7[] = {8, 20}; static int GEN_PLAN_S7[] = {3, 10, 15, 21};
static int GEN_PLAN_N8[] = {0, 11, 25, 37, 44}; static int GEN_PLAN_R8[] = {0, 12, 24, 36}; static int GEN_PLAN_A8[] = {0, 24}; static int GEN_PLAN_V8[] = {18, 42}; static int GEN_PLAN_S8[] = {13, 35};
static int GEN_PLAN_N9[] = {0, 6, 12, 19, 27}; static int GEN_PLAN_R9[] = {0, 4, 8, 12, 16, 20, 24, 28}; static int GEN_PLAN_A9[] = {0, 16}; static int GEN_PLAN_V9[] = {8, 24}; static int GEN_PLAN_S9[] = {10, 26};

static GenLayer GEN_LAYERS_DEEP_SEA[] = {
    {"deep-sea-floor", GEN_KIND_ATMOSPHERE, "deep-drone", 54, 76, 18, 18.0, 5.0, 7.0, 0.0, 0.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_LOW, GEN_SCALE_PENTATONIC, 64, 1, 0, 1},
    {"deep-sea-sonar", GEN_KIND_NOTES, "sonar-ping", 38, 28, 20, 2.8, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_AVOID, NULL, GEN_REG_WIDE, GEN_SCALE_PENTATONIC, 86, 1, 0, 0},
    {"deep-sea-bell", GEN_KIND_NOTES, "glass-bell", 30, 34, 25, 5.5, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_ALTERNATE, NULL, GEN_REG_HIGH, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_RAIN_PIANO[] = {
    {"rain-piano-notes", GEN_KIND_NOTES, "vsco-upright-piano", 58, 60, 42, 4.2, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 92, 1, 0, 1},
    {"rain-piano-strings", GEN_KIND_ATMOSPHERE, "vsco-cello", 30, 54, 16, 14.0, 3.5, 4.5, 0.0, 0.0, 1, GEN_REL_AVOID, NULL, GEN_REG_LOW, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"rain-piano-bell", GEN_KIND_NOTES, "glass-bell", 24, 25, 22, 3.4, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_ALTERNATE, NULL, GEN_REG_HIGH, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_MORNING_MIST[] = {
    {"mist-flute", GEN_KIND_NOTES, "vsco-flute", 42, 38, 30, 5.8, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_HIGH, GEN_SCALE_PENTATONIC, 90, 1, 0, 1},
    {"mist-harp", GEN_KIND_NOTES, "vsco-harp", 44, 54, 36, 2.4, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_ALTERNATE, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"mist-air", GEN_KIND_ATMOSPHERE, "air-pad", 32, 58, 16, 15.0, 3.5, 4.5, 0.0, 0.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_WIDE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_ENDLESS_FOCUS[] = {
    {"focus-electric", GEN_KIND_NOTES, "electric-piano", 48, 46, 42, 3.2, 0.12, 2.2, 0.0, 2.0, 8, GEN_REL_INDEPENDENT, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 1},
    {"focus-pulse", GEN_KIND_RHYTHM, "muted-pulse", 28, 76, 62, 0.65, 0.12, 2.2, 0.0, 2.0, 4, GEN_REL_FOLLOW, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"focus-strings", GEN_KIND_ATMOSPHERE, "vsco-cello", 26, 54, 14, 16.0, 3.5, 4.5, 0.0, 0.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_LOW, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_NIGHT_TRAIN[] = {
    {"train-drone", GEN_KIND_ATMOSPHERE, "deep-drone", 38, 70, 18, 16.0, 3.5, 4.5, 0.0, 0.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_LOW, GEN_SCALE_PENTATONIC, 82, 1, 0, 1},
    {"train-rail", GEN_KIND_RHYTHM, "rail-click", 34, 82, 68, 0.45, 0.12, 2.2, 0.0, 2.0, 2, GEN_REL_INDEPENDENT, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 96, 1, 0, 0},
    {"train-signal", GEN_KIND_NOTES, "sonar-ping", 30, 24, 18, 2.4, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_AVOID, NULL, GEN_REG_HIGH, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_WARM_STUDY[] = {
    {"study-ep", GEN_KIND_NOTES, "electric-piano", 54, 58, 44, 3.6, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 74, 1, 0, 1},
    {"study-harp", GEN_KIND_NOTES, "vsco-harp", 30, 34, 24, 2.1, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_ALTERNATE, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"study-pulse", GEN_KIND_RHYTHM, "muted-pulse", 22, 62, 50, 0.8, 0.12, 2.2, 0.0, 2.0, 4, GEN_REL_FOLLOW, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_MOON_TIDE[] = {
    {"moon-drone", GEN_KIND_ATMOSPHERE, "deep-drone", 50, 78, 14, 22.0, 7.0, 8.0, 0.0, 0.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_LOW, GEN_SCALE_PENTATONIC, 92, 1, 0, 1},
    {"moon-choir", GEN_KIND_VOICE, "vowel-choir", 24, 32, 15, 10.0, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_AVOID, NULL, GEN_REG_WIDE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"moon-bell", GEN_KIND_NOTES, "glass-bell", 24, 22, 16, 6.0, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_AVOID, NULL, GEN_REG_HIGH, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_SIGNAL_GARDEN[] = {
    {"signal-bell", GEN_KIND_NOTES, "glass-bell", 46, 62, 54, 2.8, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_WIDE, GEN_SCALE_PENTATONIC, 64, 1, 0, 1},
    {"signal-harp", GEN_KIND_NOTES, "harp-pluck", 32, 48, 40, 1.8, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_ALTERNATE, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"signal-pulse", GEN_KIND_RHYTHM, "muted-pulse", 20, 58, 48, 0.8, 0.12, 2.2, 0.0, 2.0, 2, GEN_REL_FOLLOW, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_BAMBOO[] = {
    {"bamboo-flute", GEN_KIND_NOTES, "vsco-flute", 50, 44, 30, 6.5, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_HIGH, GEN_SCALE_PENTATONIC, 92, 1, 0, 1},
    {"bamboo-strings", GEN_KIND_ATMOSPHERE, "vsco-cello", 34, 62, 18, 16.0, 3.5, 4.5, 0.0, 0.0, 1, GEN_REL_AVOID, NULL, GEN_REG_LOW, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"bamboo-harp", GEN_KIND_NOTES, "vsco-harp", 28, 32, 24, 2.2, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_ALTERNATE, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};
static GenLayer GEN_LAYERS_CUSTOM_LAB[] = {
    {"lab-piano", GEN_KIND_NOTES, "vsco-upright-piano", 52, 52, 38, 3.4, 0.12, 2.2, 0.0, 2.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 1},
    {"lab-pad", GEN_KIND_ATMOSPHERE, "air-pad", 34, 62, 18, 14.0, 3.5, 4.5, 0.0, 0.0, 1, GEN_REL_INDEPENDENT, NULL, GEN_REG_LOW, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
    {"lab-pulse", GEN_KIND_RHYTHM, "muted-pulse", 22, 58, 46, 0.8, 0.12, 2.2, 0.0, 2.0, 4, GEN_REL_INDEPENDENT, NULL, GEN_REG_MIDDLE, GEN_SCALE_PENTATONIC, 64, 1, 0, 0},
};

/* night-train 的 rail-click 专用噪声规格（对照 Profile.noise = Noise(.72,1460,560,4.2)）。 */
static GenNoise GEN_NOISE_NIGHT_TRAIN = {0.72, 1460.0, 560.0, 4.2};

static GenArrangement GEN_ARRANGEMENTS[] = {
    {"deep-sea-soundist-02", 54, 8, GEN_ARC_BREATHING, {55.0, GEN_ARR_I0, 5, 0.18, 620, 0.8, NULL}, {64, GEN_PLAN_N0, 2, GEN_PLAN_R0, 2, GEN_PLAN_A0, 2, GEN_PLAN_V0, 1, GEN_PLAN_S0, 2}, GEN_LAYERS_DEEP_SEA, 3, NULL, 0},
    {"rain-piano-soundist-02", 54, 5, GEN_ARC_BREATHING, {110.0, GEN_ARR_I1, 5, 0.48, 1750, 0.56, NULL}, {32, GEN_PLAN_N1, 5, GEN_PLAN_R1, 4, GEN_PLAN_A1, 2, GEN_PLAN_V1, 1, GEN_PLAN_S1, 2}, GEN_LAYERS_RAIN_PIANO, 3, NULL, 0},
    {"morning-mist-soundist-02", 54, 5, GEN_ARC_BREATHING, {98.0, GEN_ARR_I2, 5, 0.62, 2200, 0.48, NULL}, {48, GEN_PLAN_N2, 5, GEN_PLAN_R2, 4, GEN_PLAN_A2, 2, GEN_PLAN_V2, 2, GEN_PLAN_S2, 2}, GEN_LAYERS_MORNING_MIST, 3, NULL, 0},
    {"endless-focus-soundist-02", 54, 5, GEN_ARC_STEADY, {65.41, GEN_ARR_I3, 4, 0.34, 1180, 0.32, NULL}, {16, GEN_PLAN_N3, 2, GEN_PLAN_R3, 4, GEN_PLAN_A3, 1, GEN_PLAN_V3, 1, GEN_PLAN_S3, 2}, GEN_LAYERS_ENDLESS_FOCUS, 3, NULL, 0},
    {"night-train-soundist-02", 54, 4, GEN_ARC_JOURNEY, {73.42, GEN_ARR_I4, 4, 0.28, 920, 0.42, &GEN_NOISE_NIGHT_TRAIN}, {16, GEN_PLAN_N4, 2, GEN_PLAN_R4, 8, GEN_PLAN_A4, 1, GEN_PLAN_V4, 1, GEN_PLAN_S4, 2}, GEN_LAYERS_NIGHT_TRAIN, 3, NULL, 0},
    {"warm-study-soundist-02", 54, 5, GEN_ARC_STEADY, {82.41, GEN_ARR_I5, 5, 0.46, 1580, 0.38, NULL}, {32, GEN_PLAN_N5, 4, GEN_PLAN_R5, 8, GEN_PLAN_A5, 2, GEN_PLAN_V5, 2, GEN_PLAN_S5, 2}, GEN_LAYERS_WARM_STUDY, 3, NULL, 0},
    {"moon-tide-soundist-02", 54, 8, GEN_ARC_BREATHING, {49.0, GEN_ARR_I6, 5, 0.14, 480, 0.9, NULL}, {64, GEN_PLAN_N6, 2, GEN_PLAN_R6, 2, GEN_PLAN_A6, 2, GEN_PLAN_V6, 2, GEN_PLAN_S6, 2}, GEN_LAYERS_MOON_TIDE, 3, NULL, 0},
    {"signal-garden-soundist-02", 54, 5, GEN_ARC_JOURNEY, {130.81, GEN_ARR_I7, 6, 0.74, 2600, 0.62, NULL}, {24, GEN_PLAN_N7, 5, GEN_PLAN_R7, 4, GEN_PLAN_A7, 2, GEN_PLAN_V7, 2, GEN_PLAN_S7, 5}, GEN_LAYERS_SIGNAL_GARDEN, 3, NULL, 0},
    {"bamboo-strings-soundist-02", 54, 5, GEN_ARC_BREATHING, {73.42, GEN_ARR_I8, 6, 0.5, 1850, 0.58, NULL}, {48, GEN_PLAN_N8, 5, GEN_PLAN_R8, 4, GEN_PLAN_A8, 2, GEN_PLAN_V8, 2, GEN_PLAN_S8, 2}, GEN_LAYERS_BAMBOO, 3, NULL, 0},
    {"custom-lab-soundist-02", 54, 5, GEN_ARC_BREATHING, {65.41, GEN_ARR_I9, 7, 0.44, 1500, 0.48, NULL}, {32, GEN_PLAN_N9, 5, GEN_PLAN_R9, 8, GEN_PLAN_A9, 2, GEN_PLAN_V9, 2, GEN_PLAN_S9, 2}, GEN_LAYERS_CUSTOM_LAB, 3, NULL, 0},
};
#define GEN_ARRANGEMENT_COUNT 10

/* preset id（Kotlin 的 key，如 "deep-sea"）→ 本机索引。 */
static const char* GEN_PRESET_IDS[GEN_ARRANGEMENT_COUNT] = {
    "deep-sea", "rain-piano", "morning-mist", "endless-focus", "night-train",
    "warm-study", "moon-tide", "signal-garden", "bamboo-strings", "custom-lab",
};

/* ---------- 黄金调度（对照 schedulerGolden） ---------- */

static void gen_apply_with_target(GenArrangement* a) {
    /* 对照 withTarget：非 INDEPENDENT 层 target = 前面反向第一个 kind 不同的层 id，否则 layers[0].id。 */
    for (int i = 0; i < a->layerCount; i++) {
        GenLayer* layer = &a->layers[i];
        if (layer->relationship == GEN_REL_INDEPENDENT) continue;
        const char* t = a->layers[0].id;
        for (int j = i - 1; j >= 0; j--) {
            if (a->layers[j].kind != layer->kind) { t = a->layers[j].id; break; }
        }
        layer->target = t;
    }
}

/* 返回黄金调度字符串，写入 buf（调用方释放）。 */
static char* gen_scheduler_golden(const char* presetId, int ticks) {
    if (ticks < 0) return NULL;
    int idx = -1;
    for (int i = 0; i < GEN_ARRANGEMENT_COUNT; i++) {
        if (strcmp(GEN_PRESET_IDS[i], presetId) == 0) { idx = i; break; }
    }
    if (idx < 0) return NULL;

    GenArrangement a = GEN_ARRANGEMENTS[idx];
    gen_apply_with_target(&a);

    GenMulberry32 random; random.s = gen_fnv1a(a.seed);
    GenSchedulerMemory memory; memset(&memory, 0, sizeof(memory));
    memory.lastMusical = -INFINITY;

    GenSettings settings = {54, 42, 48, 1.4, 3.2, 72, 70, 1};
    GenAmbientBias bias = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};

    /* 预估容量：每个 tick 最多 layerCount 个 "tick:id" 片段。 */
    size_t cap = (size_t)ticks * (size_t)(a.layerCount * 96 + 1) + 1;
    char* buf = (char*)malloc(cap);
    if (buf == NULL) return NULL;
    buf[0] = '\0';
    size_t used = 0;

    for (int tick = 0; tick < ticks; tick++) {
        double at = tick * 60.0 / a.tempo / 4.0;
        GenSection s = gen_section_state(&a, at);
        int hasSolo = 0;
        for (int li = 0; li < a.layerCount; li++) hasSolo |= a.layers[li].solo != 0;
        for (int li = 0; li < a.layerCount; li++) {
            GenLayer* layer = &a.layers[li];
            if (hasSolo && !layer->solo) continue;
            const char* timbre = layer->timbre;
            int introPending = layer->intro && isinf(gen_memory_last(&memory, layer->id));
            if (introPending || gen_should_schedule_at_tick(layer, timbre, tick, &a.plan)) {
                if (gen_gate_passes(layer, timbre, at, &s, &bias, &random, &settings, &memory)) {
                    int n = snprintf(buf + used, cap - used, "%s%d:%s", used ? "," : "", tick, layer->id);
                    if (n > 0) used += (size_t)n;
                }
            }
        }
    }
    return buf;
}

/* JNI：返回 presetId 前 ticks 个 tick 的黄金调度字符串（逗号分隔 "tick:layerId"）。 */
JNIEXPORT jstring JNICALL Java_com_soundist_app_NativeAudioCore_nativeSchedulerGolden(JNIEnv* env, jobject thiz, jstring presetId, jint ticks) {
    (void)thiz;
    const char* id = (*env)->GetStringUTFChars(env, presetId, NULL);
    if (id == NULL) return (*env)->NewStringUTF(env, "");
    char* out = gen_scheduler_golden(id, ticks);
    (*env)->ReleaseStringUTFChars(env, presetId, id);
    if (out == NULL) return (*env)->NewStringUTF(env, "");
    jstring result = (*env)->NewStringUTF(env, out);
    free(out);
    return result;
}

/* ==========================================================================
 * 第二片：逐音色振荡器 + 包络 + 滤波 + 压缩/压限（对照 NativeGeneratedAudioRenderer.kt）
 * 全部用 double，与 Kotlin 的 Double 精度一致，保证听感等价。
 * ========================================================================== */

#define GEN_SAMPLE_RATE 44100.0

static double gen_sine(double f, double t) { return sin(2.0 * GEN_PI * f * t); }
static double gen_triangle(double f, double t) { return 2.0 / GEN_PI * asin(sin(2.0 * GEN_PI * f * t)); }
static double gen_saw(double f, double t) { return 2.0 * ((t * f) - floor(0.5 + t * f)); }
static double gen_cents(double c) { return pow(2.0, c / 1200.0); }

/* ---------- RBJ biquad（double，对照 BiquadLp / Bandpass） ---------- */
typedef struct {
    double b0, b1, b2, a1, a2;
    double x1, x2, y1, y2;
    double lastFreq; /* LP 系数缓存（对照 BiquadLp.lastFreq，cutoff 变化 <1Hz 跳过重算） */
} GenBiquad;

static void gen_biquad_lp(GenBiquad* b, double freq, double q) {
    /* 对照 Kotlin BiquadLp.lastFreq：cutoff 变化 <1Hz 跳过系数重算（避免每采样点重算 sin/cos）。 */
    if (fabs(freq - b->lastFreq) < 1.0) return;
    b->lastFreq = freq;
    double w0 = 2.0 * GEN_PI * freq / GEN_SAMPLE_RATE;
    double cosw = cos(w0), sinw = sin(w0);
    double alpha = sinw / (2.0 * fmax(0.0001, q));
    double a0 = 1.0 + alpha;
    b->b0 = ((1.0 - cosw) / 2.0) / a0;
    b->b1 = (1.0 - cosw) / a0;
    b->b2 = ((1.0 - cosw) / 2.0) / a0;
    b->a1 = (-2.0 * cosw) / a0;
    b->a2 = (1.0 - alpha) / a0;
}

static void gen_biquad_bp(GenBiquad* b, double freq, double q) {
    double w0 = 2.0 * GEN_PI * freq / GEN_SAMPLE_RATE;
    double cosw = cos(w0), sinw = sin(w0);
    double alpha = sinw / (2.0 * fmax(0.0001, q));
    double a0 = 1.0 + alpha;
    b->b0 = alpha / a0;
    b->b1 = 0.0;
    b->b2 = -alpha / a0;
    b->a1 = (-2.0 * cosw) / a0;
    b->a2 = (1.0 - alpha) / a0;
}

static double gen_biquad_process(GenBiquad* b, double x) {
    double y = b->b0 * x + b->b1 * b->x1 + b->b2 * b->x2 - b->a1 * b->y1 - b->a2 * b->y2;
    b->x2 = b->x1; b->x1 = x;
    b->y2 = b->y1; b->y1 = y;
    return y;
}

/* 后续片（完整 Voice 渲染）会用到：线性包络与滤波状态复位。标记 unused 避免告警。 */
#if defined(__GNUC__)
#define GEN_UNUSED __attribute__((unused))
#else
#define GEN_UNUSED
#endif

static GEN_UNUSED void gen_biquad_reset_state(GenBiquad* b) { b->x1 = b->x2 = b->y1 = b->y2 = 0.0; }

/* ---------- 回声 / 拨弦 / 合唱共振峰 / 噪声 ---------- */

typedef struct {
    int frames;
    double* buffer;
    int idx;
} GenEcho;

typedef struct {
    int frames;
    double* buffer;
    int idx;
    GenBiquad damp;
} GenHarpString;

typedef struct {
    GenBiquad bands[3];
} GenChoirFormants;

static GenEcho* gen_echo_new(double delaySeconds, double gain) {
    GenEcho* e = (GenEcho*)calloc(1, sizeof(GenEcho));
    if (!e) return NULL;
    e->frames = (int)(delaySeconds * GEN_SAMPLE_RATE);
    if (e->frames < 2) e->frames = 2;
    e->buffer = (double*)calloc((size_t)e->frames, sizeof(double));
    if (!e->buffer) { free(e); return NULL; }
    e->idx = 0;
    (void)gain;
    return e;
}

static double gen_echo_process(GenEcho* e, double x, double gain) {
    double d = e->buffer[e->idx];
    e->buffer[e->idx] = x;
    e->idx = (e->idx + 1) % e->frames;
    return d * gain;
}

static GenHarpString* gen_harp_new(double delaySeconds, double dampingCutoff) {
    GenHarpString* h = (GenHarpString*)calloc(1, sizeof(GenHarpString));
    if (!h) return NULL;
    h->frames = (int)(delaySeconds * GEN_SAMPLE_RATE);
    if (h->frames < 2) h->frames = 2;
    h->buffer = (double*)calloc((size_t)h->frames, sizeof(double));
    if (!h->buffer) { free(h); return NULL; }
    h->idx = 0;
    gen_biquad_lp(&h->damp, dampingCutoff, 0.5);
    return h;
}

static double gen_harp_process(GenHarpString* h, double input) {
    double read = h->buffer[h->idx];
    double damped = gen_biquad_process(&h->damp, read);
    h->buffer[h->idx] = input + 0.92 * damped;
    h->idx = (h->idx + 1) % h->frames;
    return damped;
}

static void gen_choir_new(GenChoirFormants* c) {
    gen_biquad_bp(&c->bands[0], 620.0, 2.4);
    gen_biquad_bp(&c->bands[1], 1180.0, 3.1);
    gen_biquad_bp(&c->bands[2], 2450.0, 4.0);
}

static double gen_choir_process(GenChoirFormants* c, double f, double t) {
    double f0 = f * gen_cents(-4.0);
    double f1 = f * 1.5 * gen_cents(4.0);
    double f2 = f * 2.0 * gen_cents(8.0);
    return gen_biquad_process(&c->bands[0], gen_triangle(f0, t))
         + gen_biquad_process(&c->bands[1], gen_sine(f1, t))
         + gen_biquad_process(&c->bands[2], gen_sine(f2, t));
}

static void gen_noise_buffer(GenMulberry32* r, double seconds, double color, double* out, int n) {
    (void)seconds;
    double smoothing = gen_clamp(color, 0.0, 0.94);
    double previous = 0.0;
    for (int i = 0; i < n; i++) {
        double white = gen_mulberry32_next(r) * 2.0 - 1.0;
        previous = previous * smoothing + white * (1.0 - smoothing);
        out[i] = white * (1.0 - smoothing * 0.72) + previous * smoothing * 1.4;
    }
}

/* 全局 burst 噪声缓存（对照 burstNoiseCache，固定 seed 0x9e3779b9）。 */
static double* gen_burst_cache = NULL;
static int gen_burst_cache_len = 0;

static double gen_burst_sample(double t, double color) {
    if (gen_burst_cache == NULL) {
        GenMulberry32 r; r.s = 0x9e3779b9u;
        gen_burst_cache_len = (int)(0.028 * GEN_SAMPLE_RATE);
        gen_burst_cache = (double*)malloc((size_t)gen_burst_cache_len * sizeof(double));
        gen_noise_buffer(&r, 0.028, color, gen_burst_cache, gen_burst_cache_len);
    }
    int idx = (int)(t * GEN_SAMPLE_RATE);
    if (idx < 0) idx = 0; else if (idx >= gen_burst_cache_len) idx = gen_burst_cache_len - 1;
    return gen_burst_cache[idx];
}

/* ---------- 逐音色额外状态（对照 TimbreExtra） ---------- */
typedef struct {
    double* railNoise; int railNoiseLen;
    GenBiquad railBand;
    GenHarpString* harp;
    double vibratoFreq;
    double* breathNoise; int breathNoiseLen;
    GenBiquad breathBand;
    GenChoirFormants choir;
} GenExtra;

static void gen_build_extra(const char* timbre, double frequency, double cutoffIn, const GenProfile* profile, GenMulberry32* random, GenExtra* extra) {
    memset(extra, 0, sizeof(*extra));
    if (strcmp(timbre, "rail-click") == 0) {
        /* 对照 Kotlin buildExtra：优先 profile.noise（night-train 显式 Noise），否则按 brightness/cutoff 推导 fallback。 */
        const GenNoise* ns = (profile != NULL) ? profile->noise : NULL;
        double color, center, spread, resonance;
        if (ns != NULL) {
            color = ns->color; center = ns->center; spread = ns->spread; resonance = ns->resonance;
        } else {
            double pb = (profile != NULL) ? profile->brightness : 0.5;
            double pc = (profile != NULL) ? (double)profile->cutoff : cutoffIn;
            color = gen_clamp(0.28 + (1.0 - pb) * 0.38, 0.18, 0.78);
            center = gen_clamp(pc * 1.25, 720.0, 2400.0);
            spread = 420.0;
            resonance = 3.2;
        }
        gen_biquad_bp(&extra->railBand, center + (gen_mulberry32_next(random) - 0.5) * spread, resonance);
        extra->railNoiseLen = (int)(0.12 * GEN_SAMPLE_RATE);
        extra->railNoise = (double*)malloc((size_t)extra->railNoiseLen * sizeof(double));
        gen_noise_buffer(random, 0.12, color, extra->railNoise, extra->railNoiseLen);
    } else if (strcmp(timbre, "harp-pluck") == 0) {
        extra->harp = gen_harp_new(gen_clamp(1.0 / fmax(70.0, frequency), 0.002, 0.055), gen_clamp(cutoffIn * 1.45, 900.0, 6200.0));
    } else if (strcmp(timbre, "bamboo-flute") == 0) {
        extra->vibratoFreq = 4.3 + gen_mulberry32_next(random);
        extra->breathNoiseLen = (int)(0.16 * GEN_SAMPLE_RATE);
        extra->breathNoise = (double*)malloc((size_t)extra->breathNoiseLen * sizeof(double));
        gen_noise_buffer(random, 0.16, 0.82, extra->breathNoise, extra->breathNoiseLen);
        gen_biquad_bp(&extra->breathBand, gen_clamp(frequency * 5.0, 900.0, 4200.0), 1.4);
    } else if (strcmp(timbre, "vowel-choir") == 0) {
        gen_choir_new(&extra->choir);
    }
}

static void gen_free_extra(GenExtra* extra) {
    if (extra->railNoise) { free(extra->railNoise); extra->railNoise = NULL; }
    if (extra->breathNoise) { free(extra->breathNoise); extra->breathNoise = NULL; }
    if (extra->harp) {
        free(extra->harp->buffer);
        free(extra->harp);
        extra->harp = NULL;
    }
}

/* ---------- synthRaw（对照逐音色振荡器） ---------- */

static double gen_synth_raw(const char* timbre, double f, double t, double duration, const GenExtra* extra) {
    if (strcmp(timbre, "soft-piano") == 0) {
        return gen_triangle(f, t) + 0.28 * gen_sine(f * 2.01 * gen_cents(3.0), t);
    } else if (strcmp(timbre, "electric-piano") == 0) {
        double depth = fmin(42.0, f * 0.12);
        double fm = f * 2.0;
        double phaseMod = depth / (2.0 * GEN_PI * fm);
        return sin(2.0 * GEN_PI * f * t + phaseMod * (1.0 - cos(2.0 * GEN_PI * fm * t)));
    } else if (strcmp(timbre, "glass-bell") == 0) {
        return gen_sine(f, t) + 0.26 * gen_sine(f * 2.01 * gen_cents(3.0), t) + 0.09 * gen_sine(f * 3.94 * gen_cents(6.0), t);
    } else if (strcmp(timbre, "harp-pluck") == 0) {
        if (extra->harp == NULL) return 0.0;
        return gen_harp_process(extra->harp, t < 0.028 ? gen_burst_sample(t, 0.16) : 0.0);
    } else if (strcmp(timbre, "bamboo-flute") == 0) {
        double vf = extra->vibratoFreq > 0.0 ? extra->vibratoFreq : 4.3;
        double phaseMod = 1.8 / (2.0 * GEN_PI * vf);
        double v = sin(2.0 * GEN_PI * f * t + phaseMod * (1.0 - cos(2.0 * GEN_PI * vf * t)));
        if (extra->breathNoise != NULL && t < 0.16) {
            int idx = (int)(t * GEN_SAMPLE_RATE);
            if (idx < 0) idx = 0; else if (idx >= extra->breathNoiseLen) idx = extra->breathNoiseLen - 1;
            v += gen_biquad_process((GenBiquad*)&extra->breathBand, extra->breathNoise[idx]) * 0.035;
        }
        return v;
    } else if (strcmp(timbre, "warm-strings") == 0) {
        return gen_saw(f * gen_cents(-7.0), t) + gen_saw(f, t) + gen_saw(f * gen_cents(7.0), t) + gen_triangle(f * 1.5 * gen_cents(3.0), t);
    } else if (strcmp(timbre, "vowel-choir") == 0) {
        return gen_choir_process((GenChoirFormants*)&extra->choir, f, t);
    } else if (strcmp(timbre, "air-pad") == 0) {
        return gen_sine(f * gen_cents(-5.0), t) + gen_triangle(f * 1.5 * gen_cents(5.0), t) + gen_sine(f * 2.0 * gen_cents(2.0), t);
    } else if (strcmp(timbre, "deep-drone") == 0) {
        return gen_sine(f * 0.5 * gen_cents(-4.0), t) + gen_triangle(f * 0.75 * gen_cents(4.0), t);
    } else if (strcmp(timbre, "muted-pulse") == 0) {
        double target = fmax(34.0, f * 0.32);
        double span = fmin(0.18, duration);
        double p = t >= span ? target : f * 0.5 * pow(target / (f * 0.5), t / span);
        return (sin(2.0 * GEN_PI * p * t) >= 0.0 ? 1.0 : -1.0) + gen_sine(f * 0.25 * gen_cents(-2.0), t);
    } else if (strcmp(timbre, "sonar-ping") == 0) {
        double glideTo = fmax(42.0, f * 1.45);
        double span = fmin(duration, 1.6);
        double g = t >= span ? glideTo : f * 2.0 * pow(glideTo / (f * 2.0), t / span);
        return gen_sine(g, t);
    } else if (strcmp(timbre, "rail-click") == 0) {
        if (extra->railNoise == NULL || t >= 0.12) return 0.0;
        int idx = (int)(t * GEN_SAMPLE_RATE);
        if (idx < 0) idx = 0; else if (idx >= extra->railNoiseLen) idx = extra->railNoiseLen - 1;
        return gen_biquad_process((GenBiquad*)&extra->railBand, extra->railNoise[idx]);
    } else {
        return gen_sine(f, t);
    }
}

/* ---------- 包络（对照 expEnv / linearEnv） ---------- */

static double gen_exp_env(double t, double gain, double attack, double duration, double release) {
    if (gain <= 0.0) return 0.0;
    double sustainAt = fmax(0.025, attack);
    double releaseAt = fmax(sustainAt + 0.04, duration);
    if (t <= 0.0) return 0.0001;
    if (t < sustainAt) return 0.0001 * pow(gain / 0.0001, t / sustainAt);
    if (t < releaseAt) return gain * pow(0.42, (t - sustainAt) / fmax(0.0001, releaseAt - sustainAt));
    if (t < releaseAt + release) return gain * 0.42 * pow(0.0001 / (gain * 0.42), (t - releaseAt) / fmax(0.0001, release));
    return 0.0001;
}

static GEN_UNUSED double gen_linear_env(double t, double attack, double duration, double release) {
    if (t >= duration + release) return 0.0;
    if (t < attack) return attack > 0.0 ? t / attack : 1.0;
    if (t < duration) return 1.0;
    return gen_clamp((duration + release - t) / fmax(0.0001, release), 0.0, 1.0);
}

static double gen_current_cutoff(const char* timbre, double t, double base, double duration, double room) {
    if (strcmp(timbre, "air-pad") == 0) {
        return base + gen_clamp(base * 0.12, 28.0, 240.0) * sin(2.0 * GEN_PI * (0.06 + room * 0.05) * t);
    } else if (strcmp(timbre, "muted-pulse") == 0) {
        double target = fmax(180.0, base * 0.36);
        double span = fmin(0.2, duration);
        if (t >= span) return target;
        return base * pow(target / base, t / span);
    } else {
        return base;
    }
}

/* ---------- 压缩器 / 主链路（对照 Compressor / MasterChain） ---------- */

typedef struct {
    double thr, knee, ratio, aCoeff, rCoeff;
    double envDb;
} GenCompressor;

static void gen_compressor_init(GenCompressor* c, double thresholdDb, double kneeDb, double ratio, double attackSec, double releaseSec) {
    c->thr = thresholdDb; c->knee = kneeDb; c->ratio = ratio;
    c->aCoeff = attackSec > 0.0 ? exp(-1.0 / (attackSec * GEN_SAMPLE_RATE)) : 0.0;
    c->rCoeff = releaseSec > 0.0 ? exp(-1.0 / (releaseSec * GEN_SAMPLE_RATE)) : 0.0;
    c->envDb = 0.0;
}

static void gen_compressor_process(GenCompressor* c, double l, double r, double* ol, double* orr) {
    double absL = fabs(l), absR = fabs(r);
    double level = absL > absR ? absL : absR;
    double desired = 0.0;
    if (level > 1e-6) {
        double levelDb = 20.0 * log10(level);
        double reduction;
        if (levelDb < c->thr - c->knee / 2.0) reduction = 0.0;
        else if (levelDb < c->thr + c->knee / 2.0) { double delta = levelDb - c->thr + c->knee / 2.0; reduction = delta * delta / (2.0 * c->knee); }
        else reduction = levelDb - c->thr;
        desired = -reduction * (c->ratio - 1.0) / c->ratio;
    }
    c->envDb = desired > c->envDb ? c->envDb + c->aCoeff * (desired - c->envDb) : c->envDb + c->rCoeff * (desired - c->envDb);
    double g = pow(10.0, c->envDb / 20.0);
    *ol = l * g; *orr = r * g;
}

typedef struct {
    double trimGain;
    double vol;
    GenCompressor compressor;
    GenCompressor limiter;
} GenMasterChain;

static void gen_master_chain_set_volume(GenMasterChain* m, double outputVolume, int gain) {
    m->vol = outputVolume * gain / 100.0 * m->trimGain;
}

static void gen_master_chain_init(GenMasterChain* m, double outputVolume, int gain, double trimDb) {
    m->trimGain = pow(10.0, trimDb / 20.0);
    gen_master_chain_set_volume(m, outputVolume, gain);
    gen_compressor_init(&m->compressor, -22.0, 20.0, 4.0, 0.02, 0.65);
    gen_compressor_init(&m->limiter, -1.0, 0.0, 20.0, 0.002, 0.12);
}

static void gen_master_chain_process(GenMasterChain* m, double l, double r, double* ol, double* orr) {
    double cl, cr;
    gen_compressor_process(&m->compressor, l * m->vol, r * m->vol, &cl, &cr);
    gen_compressor_process(&m->limiter, cl, cr, ol, orr);
}

/* JNI：渲染一个合成音色声部（振荡器→低通→包络→主链路），返回 float PCM（供设备/集成测试）。 */
JNIEXPORT jfloatArray JNICALL Java_com_soundist_app_NativeAudioCore_nativeRenderSynthVoice(
        JNIEnv* env, jobject thiz, jstring timbre, jdouble frequency, jdouble durationSeconds,
        jdouble attackSeconds, jdouble releaseSeconds, jdouble cutoffHz, jdouble room,
        jdouble gain, jint seed, jint outputFrames) {
    (void)thiz;
    const char* tm = (*env)->GetStringUTFChars(env, timbre, NULL);
    if (tm == NULL || outputFrames <= 0 || outputFrames > INT_MAX / 2) {
        if (tm) (*env)->ReleaseStringUTFChars(env, timbre, tm);
        return (*env)->NewFloatArray(env, 0);
    }

    jfloatArray result = (*env)->NewFloatArray(env, outputFrames);
    if (result == NULL) { (*env)->ReleaseStringUTFChars(env, timbre, tm); return NULL; }
    jfloat* out = (*env)->GetFloatArrayElements(env, result, NULL);
    if (out == NULL) { (*env)->ReleaseStringUTFChars(env, timbre, tm); return result; }

    GenMulberry32 random; random.s = (uint32_t)seed;
    GenExtra extra;
    GenProfile probeProfile = {261.63, NULL, 0, 0.4, 1800, room, NULL};
    gen_build_extra(tm, frequency, cutoffHz, &probeProfile, &random, &extra);

    GenBiquad lp; memset(&lp, 0, sizeof(lp));
    gen_biquad_lp(&lp, cutoffHz, 0.45);
    GenEcho* echo = NULL;
    if (strcmp(tm, "sonar-ping") == 0) echo = gen_echo_new(0.42 + room * 0.18, 0.16);

    GenMasterChain master;
    gen_master_chain_init(&master, 0.8, 72, 0.0);

    for (int i = 0; i < outputFrames; i++) {
        double t = (double)i / GEN_SAMPLE_RATE;
        double base = cutoffHz;
        double raw = gen_synth_raw(tm, frequency, t, durationSeconds, &extra);
        if (strcmp(tm, "air-pad") == 0 || strcmp(tm, "muted-pulse") == 0) {
            gen_biquad_lp(&lp, gen_current_cutoff(tm, t, base, durationSeconds, room), 0.45);
        }
        double filtered = gen_biquad_process(&lp, raw);
        double env = gen_exp_env(t, gain, attackSeconds, durationSeconds, releaseSeconds);
        double o = filtered * env;
        if (echo != NULL) o += gen_echo_process(echo, filtered, 0.16);
        double l, r;
        gen_master_chain_process(&master, o, o, &l, &r);
        double v = l;
        out[i] = (jfloat)v;
    }

    (*env)->ReleaseFloatArrayElements(env, result, out, 0);
    (*env)->ReleaseStringUTFChars(env, timbre, tm);
    gen_free_extra(&extra);
    if (echo != NULL) { free(echo->buffer); free(echo); }
    return result;
}

/* ==========================================================================
 * 第三片：完整 Voice 状态机 + scheduleLayer/createVoice（声部创建，合成路径）+ 渲染循环。
 * 对照 NativeGeneratedAudioRenderer.kt 的 Voice.sample/finished/ramp 与 scheduleLayer/createVoice。
 * 说明：采样路径（VSCO）尚未迁移；合成音色（15/18）完整，采样音色走 SAMPLE_FALLBACKS 合成回退。
 * ========================================================================== */

#define GEN_VOLUME_RAMP_TAU        0.03
#define GEN_LAYER_RELEASE_SECONDS  0.15
#define GEN_MAX_VOICES             1024

static double gen_semitone(double base, double interval) { return base * pow(2.0, interval / 12.0); }

static int GEN_SCALE_PENT_NOTES[] = {0, 2, 4, 7, 9, 12};
static int GEN_SCALE_MAJOR_NOTES[] = {0, 2, 4, 5, 7, 9, 11, 12};
static int GEN_SCALE_MINOR_NOTES[] = {0, 2, 3, 5, 7, 8, 10, 12};
static int GEN_SCALE_DORIAN_NOTES[] = {0, 2, 3, 5, 7, 9, 10, 12};

static const int* gen_scale(GenScale scale, int* count) {
    switch (scale) {
        case GEN_SCALE_MAJOR: *count = 8; return GEN_SCALE_MAJOR_NOTES;
        case GEN_SCALE_MINOR: *count = 8; return GEN_SCALE_MINOR_NOTES;
        case GEN_SCALE_DORIAN: *count = 8; return GEN_SCALE_DORIAN_NOTES;
        default: *count = 6; return GEN_SCALE_PENT_NOTES;
    }
}

/* 采样音色 → 合成回退 + 音色级 trimDb（对照 SAMPLE_FALLBACKS + TIMBRE_DEFINITIONS.gainTrimDb）。 */
static const char* gen_fallback_timbre(const char* timbre) {
    if (strcmp(timbre, "vsco-upright-piano") == 0) return "soft-piano";
    if (strcmp(timbre, "vsco-harp") == 0) return "harp-pluck";
    if (strcmp(timbre, "vsco-flute") == 0) return "bamboo-flute";
    if (strcmp(timbre, "vsco-violin") == 0) return "warm-strings";
    if (strcmp(timbre, "vsco-cello") == 0) return "warm-strings";
    return timbre;
}

static double gen_trim_db(const char* timbre) {
    if (strcmp(timbre, "vsco-upright-piano") == 0) return 9.0;
    if (strcmp(timbre, "vsco-cello") == 0) return 2.0;
    return 0.0;
}

/* 对照 synthEnvelope：返回 (attack, duration, release)。 */
static void gen_synth_envelope(const char* timbre, double attack, double duration, double release,
                               double* oa, double* od, double* orr) {
    if (strcmp(timbre, "rail-click") == 0) { *oa = 0.002; *od = fmin(duration, 0.09); *orr = 0.05; }
    else if (strcmp(timbre, "muted-pulse") == 0) { *oa = 0.004; *od = fmin(duration, 0.3); *orr = 0.22; }
    else if (strcmp(timbre, "soft-piano") == 0) { *oa = fmin(attack, 0.018); *od = fmin(duration, 2.8); *orr = fmax(release, 1.6); }
    else if (strcmp(timbre, "electric-piano") == 0) { *oa = fmin(attack, 0.025); *od = fmin(duration, 3.4); *orr = fmax(release, 2.1); }
    else if (strcmp(timbre, "harp-pluck") == 0) { *oa = 0.003; *od = fmin(duration, 1.5); *orr = fmax(0.8, fmin(release, 1.8)); }
    else if (strcmp(timbre, "glass-bell") == 0) { *oa = 0.004; *od = fmin(duration, 2.8); *orr = fmax(release, 3.2); }
    else if (strcmp(timbre, "sonar-ping") == 0) { *oa = 0.006; *od = fmin(duration, 1.8); *orr = fmax(release, 1.8); }
    else if (strcmp(timbre, "bamboo-flute") == 0) { *oa = fmax(0.16, fmin(attack, 0.65)); *od = fmax(duration, 2.2); *orr = fmax(release, 1.2); }
    else if (strcmp(timbre, "warm-strings") == 0) { *oa = fmax(0.85, attack); *od = fmax(duration, 5.5); *orr = fmax(release, 3.4); }
    else if (strcmp(timbre, "vowel-choir") == 0) { *oa = fmax(1.35, attack); *od = fmax(duration, 6.0); *orr = fmax(release, 4.0); }
    else if (strcmp(timbre, "air-pad") == 0) { *oa = fmax(2.0, attack); *od = fmax(duration, 7.0); *orr = fmax(release, 4.8); }
    else if (strcmp(timbre, "deep-drone") == 0) { *oa = fmax(2.8, attack); *od = fmax(duration, 10.0); *orr = fmax(release, 5.5); }
    else { *oa = attack; *od = duration; *orr = release; }
}

/* ---------- 采样注册表（对照 SAMPLE_TIMBRES + sampleFor 就近选样） ---------- */
typedef struct {
    char timbre[64];
    double baseFrequency;
    double sampleRate;
    float* pcm;      /* 归一化 float（16-bit / 32768），交错；这里按 Kotlin 只读第 0 声道 */
    int frames;
    int channels;
    int refs;
    int pendingClear;
} GenSample;

#define GEN_MAX_SAMPLES 32
static GenSample gen_samples[GEN_MAX_SAMPLES];
static int gen_sample_count = 0;

/* 注册一个 VSCO 采样。契约：pcm 为 16-bit 交错 PCM（共 frames*channels 个采样），
 * frames = 帧数 = 总采样数 / channels（对照 Kotlin decodeWav 的 frames.size/channels）。 */
static void gen_register_sample(const char* timbre, double baseFrequency, double sampleRate, const short* pcm, int frames, int channels) {
    if (timbre == NULL || pcm == NULL || frames <= 0 || channels <= 0 || channels > 2 || sampleRate <= 0.0 || baseFrequency <= 0.0) return;
    GenSample* s = NULL;
    for (int i = 0; i < gen_sample_count; i++) {
        if (gen_samples[i].pcm == NULL && gen_samples[i].refs == 0) { s = &gen_samples[i]; break; }
    }
    if (s == NULL) {
        if (gen_sample_count >= GEN_MAX_SAMPLES) return;
        s = &gen_samples[gen_sample_count++];
    }
    memset(s, 0, sizeof(*s));
    strncpy(s->timbre, timbre, sizeof(s->timbre) - 1);
    s->timbre[sizeof(s->timbre) - 1] = '\0';
    s->baseFrequency = baseFrequency;
    s->sampleRate = sampleRate;
    s->frames = frames;
    s->channels = channels;
    s->pcm = (float*)malloc((size_t)frames * (size_t)channels * sizeof(float));
    if (s->pcm == NULL) return;
    for (int i = 0; i < frames * channels; i++) s->pcm[i] = (float)pcm[i] / 32768.0f;
}

static void gen_clear_samples(void) {
    for (int i = 0; i < gen_sample_count; i++) {
        gen_samples[i].pendingClear = 1;
        if (gen_samples[i].refs == 0) {
            free(gen_samples[i].pcm);
            gen_samples[i].pcm = NULL;
        }
    }
    while (gen_sample_count > 0 && gen_samples[gen_sample_count - 1].pcm == NULL && gen_samples[gen_sample_count - 1].refs == 0) gen_sample_count--;
}

/* 就近选样：abs(12 * log2(frequency/baseFrequency)) 最小（对照 sampleFor）。 */
static const GenSample* gen_select_sample(const char* timbre, double frequency) {
    const GenSample* best = NULL;
    double bestDist = 1e30;
    for (int i = 0; i < gen_sample_count; i++) {
        if (gen_samples[i].pendingClear || gen_samples[i].pcm == NULL) continue;
        if (strcmp(gen_samples[i].timbre, timbre) != 0) continue;
        double dist = fabs(12.0 * log(frequency / gen_samples[i].baseFrequency) / log(2.0));
        if (dist < bestDist) { bestDist = dist; best = &gen_samples[i]; }
    }
    return best;
}

/* ---------- GenVoice（完整状态机，对照 Voice：合成 + 采样路径） ---------- */
typedef struct {
    int active;
    int64_t startFrame;
    double duration, attack, release, gain, pan, frequency, cutoff, room;
    const char* timbre;
    GenExtra extra;
    const char* layerId;
    int voiceGroup;
    int baseLayerVolume;
    int64_t end;
    double q;
    GenBiquad lp;
    GenEcho* echo;
    int64_t releaseStart;
    int64_t releaseFrames;
    double gainScale, gainScaleTarget; int64_t gainScaleAt;
    double panTarget; int64_t panAt;
    /* 采样路径（isSample=1 时有效） */
    int isSample;
    const float* samplePcm;
    int sampleFrames;
    int sampleChannels;
    double sampleBase;
    double sampleRate;
    GenSample* sampleRef;
    double cutoffTarget; int64_t cutoffAt;
} GenVoice;

static double gen_voice_ramped_cutoff(GenVoice* v, int64_t at) {
    if (v->cutoffAt == INT64_MAX || at <= v->cutoffAt) return v->cutoff;
    double ramp = 1.0 - exp(-(double)(at - v->cutoffAt) / GEN_SAMPLE_RATE / GEN_VOLUME_RAMP_TAU);
    double c = v->cutoff + (v->cutoffTarget - v->cutoff) * ramp;
    if (ramp > 0.99) { v->cutoff = v->cutoffTarget; v->cutoffAt = INT64_MAX; }
    return c;
}

static double gen_voice_current_pan(GenVoice* v, int64_t at) {
    if (v->panAt == INT64_MAX || at <= v->panAt) return v->pan;
    double ramp = 1.0 - exp(-(double)(at - v->panAt) / GEN_SAMPLE_RATE / GEN_VOLUME_RAMP_TAU);
    double p = v->pan + (v->panTarget - v->pan) * ramp;
    if (ramp > 0.99) { v->pan = v->panTarget; v->panAt = INT64_MAX; }
    return p;
}

static double gen_voice_current_gain_scale(GenVoice* v, int64_t at) {
    if (v->gainScaleAt == INT64_MAX || at <= v->gainScaleAt) return v->gainScale;
    double ramp = 1.0 - exp(-(double)(at - v->gainScaleAt) / GEN_SAMPLE_RATE / GEN_VOLUME_RAMP_TAU);
    double s = v->gainScale + (v->gainScaleTarget - v->gainScale) * ramp;
    if (ramp > 0.99) { v->gainScale = v->gainScaleTarget; v->gainScaleAt = INT64_MAX; }
    return s;
}

static int gen_voice_finished(GenVoice* v, int64_t at) {
    if (v->releaseStart != INT64_MAX && at >= v->releaseStart + v->releaseFrames) return 1;
    return at >= v->end;
}

/* ---------- 热更新参数 ramp 设置（对照 Voice 的 beginRelease/setGainScale/setPanTarget/setCutoffTarget） ---------- */

static void gen_voice_begin_release(GenVoice* v, int64_t at) {
    if (v->releaseStart > at) v->releaseStart = at;
}

static void gen_voice_set_gain_scale(GenVoice* v, double target, int64_t at) {
    v->gainScale = gen_voice_current_gain_scale(v, at);
    v->gainScaleTarget = target;
    v->gainScaleAt = at;
}

static void gen_voice_set_pan_target(GenVoice* v, double target, int64_t at) {
    v->pan = gen_voice_current_pan(v, at);
    v->panTarget = target;
    v->panAt = at;
}

static void gen_voice_set_cutoff_target(GenVoice* v, double target, int64_t at) {
    v->cutoff = gen_voice_ramped_cutoff(v, at);
    v->cutoffTarget = target;
    v->cutoffAt = at;
}

/* 对照 Voice.sample(at)：合成路径（振荡器→低通→指数包络→回声）+ 采样路径（整数索引→低通→线性包络）。 */
static double gen_voice_sample(GenVoice* v, int64_t at) {
    if (at < v->startFrame) return 0.0;
    double t = (double)(at - v->startFrame) / GEN_SAMPLE_RATE;
    double base = gen_voice_ramped_cutoff(v, at);
    double o;
    if (v->isSample) {
        /* 采样路径：ratio = frequency/sampleBase * sampleRate/SAMPLE_RATE；整数索引，无插值（对照 Kotlin）。 */
        double ratio = v->frequency / v->sampleBase * v->sampleRate / GEN_SAMPLE_RATE;
        int index = (int)(t * GEN_SAMPLE_RATE * ratio);
        double raw = (index < v->sampleFrames) ? (double)v->samplePcm[index * v->sampleChannels] : 0.0;
        double env = gen_linear_env(t, v->attack, v->duration, v->release);
        gen_biquad_lp(&v->lp, base, v->q);
        o = gen_biquad_process(&v->lp, raw) * env * v->gain;
    } else {
        double raw = gen_synth_raw(v->timbre, v->frequency, t, v->duration, &v->extra);
        if (strcmp(v->timbre, "air-pad") == 0 || strcmp(v->timbre, "muted-pulse") == 0) {
            gen_biquad_lp(&v->lp, gen_current_cutoff(v->timbre, t, base, v->duration, v->room), v->q);
        }
        double filtered = gen_biquad_process(&v->lp, raw);
        double env = gen_exp_env(t, v->gain, v->attack, v->duration, v->release);
        o = filtered * env;
        if (v->echo != NULL) o += gen_echo_process(v->echo, filtered, 0.16);
    }
    double result = o;
    if (v->gainScaleAt != INT64_MAX) {
        double scale = gen_voice_current_gain_scale(v, at);
        result *= scale;
    }
    if (at >= v->releaseStart) {
        double fade = 1.0 - (double)(at - v->releaseStart) / (double)v->releaseFrames;
        if (fade < 0.0) fade = 0.0; else if (fade > 1.0) fade = 1.0;
        result *= fade;
    }
    return result;
}

/* 对照 createVoice：合成路径（sample 恒 null → SAMPLE_FALLBACKS 回退 + trim）。 */
static void gen_create_voice(GenVoice* v, const char* timbre, double frequency, double startSeconds, double duration,
                             double peak, double attackIn, double releaseIn, double pan, double cutoffIn,
                             const GenProfile* profile, GenMulberry32* random, const char* layerId, int voiceGroup, int baseLayerVolume) {
    memset(v, 0, sizeof(*v));
    v->active = 1;
    v->startFrame = (int64_t)(startSeconds * GEN_SAMPLE_RATE);
    v->frequency = frequency;
    v->pan = pan;
    v->room = profile->room;
    v->layerId = layerId;
    v->voiceGroup = voiceGroup;
    v->baseLayerVolume = baseLayerVolume;
    v->releaseStart = INT64_MAX;
    v->releaseFrames = (int64_t)(GEN_LAYER_RELEASE_SECONDS * GEN_SAMPLE_RATE);
    v->gainScale = 1.0; v->gainScaleTarget = 1.0; v->gainScaleAt = INT64_MAX;
    v->panTarget = pan; v->panAt = INT64_MAX;

    /* 采样路径：就近选样命中则用真实采样（velocity + 音量 dB + 线性包络）。 */
    const GenSample* sample = gen_select_sample(timbre, frequency);
    if (sample != NULL) {
        double trimGain = pow(10.0, gen_trim_db(timbre) / 20.0);
        double velocity = gen_clamp(0.48 + peak * 7.0, 0.45, 0.86);
        double volume = gen_clamp(peak * 8.5, 0.08, 0.78);
        double attack = gen_clamp(attackIn, 0.01, 4.0);
        double release = gen_clamp(releaseIn, 0.08, 8.0);
        v->isSample = 1;
        v->timbre = timbre;
        v->duration = duration > 0.3 ? duration : 0.3;
        v->attack = attack;
        v->release = release;
        v->gain = velocity * volume * trimGain;
        v->cutoff = gen_clamp(cutoffIn, 320.0, 9000.0);
        v->end = v->startFrame + (int64_t)((v->duration + release) * GEN_SAMPLE_RATE);
        v->q = 0.45;
        gen_biquad_lp(&v->lp, v->cutoff, v->q);
        v->samplePcm = sample->pcm;
        v->sampleFrames = sample->frames;
        v->sampleChannels = sample->channels;
        v->sampleBase = sample->baseFrequency;
        v->sampleRate = sample->sampleRate;
        ((GenSample*)sample)->refs++;
        v->sampleRef = (GenSample*)sample;
        v->cutoffTarget = v->cutoff; v->cutoffAt = INT64_MAX;
        return;
    }

    double trimGain = pow(10.0, gen_trim_db(timbre) / 20.0);
    const char* synthTimbre = gen_fallback_timbre(timbre);
    double shapedAttack, shapedDuration, shapedRelease;
    gen_synth_envelope(synthTimbre, attackIn, duration, releaseIn, &shapedAttack, &shapedDuration, &shapedRelease);
    double safePeak = gen_clamp(peak, 0.001, 0.075);
    double cutoff = gen_clamp(cutoffIn, 220.0, 7200.0);

    v->timbre = synthTimbre;
    v->duration = shapedDuration;
    v->attack = shapedAttack;
    v->release = shapedRelease;
    v->gain = safePeak * trimGain;
    v->cutoff = cutoff;
    v->end = v->startFrame + (int64_t)((shapedDuration + shapedRelease) * GEN_SAMPLE_RATE);
    v->q = (strcmp(synthTimbre, "muted-pulse") == 0) ? 1.2 : ((strcmp(synthTimbre, "vowel-choir") == 0) ? 0.8 : 0.45);
    gen_biquad_lp(&v->lp, cutoff, v->q);
    if (strcmp(synthTimbre, "sonar-ping") == 0) v->echo = gen_echo_new(0.42 + profile->room * 0.18, 0.16);
    v->cutoffTarget = cutoff; v->cutoffAt = INT64_MAX;

    gen_build_extra(synthTimbre, frequency, cutoffIn, profile, random, &v->extra);
}

static void gen_free_voice(GenVoice* v) {
    if (v->sampleRef != NULL) {
        GenSample* sample = v->sampleRef;
        if (sample->refs > 0) sample->refs--;
        if (sample->pendingClear && sample->refs == 0) {
            free(sample->pcm);
            sample->pcm = NULL;
        }
        v->sampleRef = NULL;
    }
    gen_free_extra(&v->extra);
    if (v->echo != NULL) { free(v->echo->buffer); free(v->echo); v->echo = NULL; }
}

/* ---------- 完整 scheduleLayer（对照，合成路径） ---------- */

static int gen_schedule_layer(const GenLayer* layer, const char* timbre, int tick, double at, const GenSection* state,
                              const GenAmbientBias* bias, GenMulberry32* random, const GenSettings* settings,
                              GenSchedulerMemory* memory, const GenProfile* profile,
                              GenVoice* out, int* outCount) {
    *outCount = 0;
    if (!layer->enabled || layer->kind == GEN_KIND_AMBIENT) return 0;
    double response = settings->adaptive ? (layer->ambientResponse / 100.0) * (settings->ambientResponse / 100.0) : 0.0;
    double ambientPresence = bias->level * response;
    double lastEvent = gen_memory_last(memory, layer->id);
    int introFirstFire = layer->intro && isinf(lastEvent);
    if (at < (introFirstFire ? 0.0 : settings->entryDelay + layer->entryDelay)) return 0;
    double targetEvent = memory->lastMusical;
    if (layer->target != NULL) {
        double targetLast = gen_memory_last(memory, layer->target);
        if (isfinite(targetLast)) targetEvent = targetLast;
    }
    double cooldown;
    if (layer->kind == GEN_KIND_ATMOSPHERE) cooldown = fmax(4.0, layer->duration * 0.72);
    else if (layer->relationship == GEN_REL_AVOID) cooldown = 1.8;
    else cooldown = 0.3;
    if (at - lastEvent < cooldown) return 0;
    if (layer->relationship == GEN_REL_AVOID && at - targetEvent < 0.9) return 0;
    if (layer->relationship == GEN_REL_ALTERNATE && (!isfinite(targetEvent) || targetEvent <= lastEvent || at - targetEvent > 4.2)) return 0;
    if (layer->relationship == GEN_REL_FOLLOW && (!isfinite(targetEvent) || at - targetEvent > 2.6)) return 0;
    double chance = introFirstFire ? 1.0 : gen_event_chance(layer, timbre, state, bias, settings);
    if (gen_mulberry32_next(random) > chance) return 0;

    int reg = 0;
    switch (layer->reg) {
        case GEN_REG_LOW: reg = -12; break;
        case GEN_REG_HIGH: reg = 12; break;
        case GEN_REG_WIDE: reg = gen_mulberry32_next(random) > 0.5 ? 12 : 0; break;
        default: reg = 0; break;
    }
    int scaleCount; const int* scale = gen_scale(layer->scale, &scaleCount);
    int variationCount = (int)ceil(scaleCount * gen_clamp(settings->variation, 0, 100) / 100.0);
    if (variationCount < 2) variationCount = 2;
    if (variationCount > scaleCount) variationCount = scaleCount;
    int randomInterval = scale[(int)(gen_mulberry32_next(random) * variationCount)];
    int motifPosition = tick / (layer->kind == GEN_KIND_RHYTHM ? 4 : 8);
    int motifTarget = profile->intervals[motifPosition % profile->intervalCount];
    int motif = scale[0];
    double best = 1e30;
    for (int i = 0; i < variationCount; i++) { double d = fabs((double)scale[i] - motifTarget); if (d < best) { best = d; motif = scale[i]; } }
    int phrase = (layer->kind != GEN_KIND_RHYTHM && gen_mulberry32_next(random) < 0.7) ? motif : randomInterval;
    int interval = phrase + reg + state->registerShift + state->chordShift;
    double start = at + gen_mulberry32_next(random) * 0.08;
    double baseDuration = layer->duration;
    double eventScale = gen_clamp(settings->eventDuration / 3.2, 0.25, 3.0);
    double evolvedDuration = baseDuration * eventScale * state->durationScale * (0.84 + gen_mulberry32_next(random) * 0.28) * (1.0 + bias->water * response * 0.28);
    double duration;
    if (layer->kind == GEN_KIND_RHYTHM) duration = gen_clamp(evolvedDuration, 0.12, 0.9);
    else if (layer->kind == GEN_KIND_ATMOSPHERE) duration = fmax(6.0, evolvedDuration);
    else if (layer->kind == GEN_KIND_VOICE) duration = fmax(5.0, evolvedDuration);
    else if (gen_is_signal_timbre(timbre)) duration = gen_clamp(evolvedDuration, 0.4, 3.2);
    else duration = fmax(0.24, evolvedDuration);

    double textureWeight = strcmp(timbre, "rail-click") == 0 ? state->noiseWeight * (0.72 + bias->city * response * 0.4) : 1.0;
    double peak = introFirstFire ? 0.055
        : (0.025 + profile->brightness * 0.026) * layer->volume / 52.0 * (0.72 + state->energy * 0.44)
          * (1.0 + bias->warm * response * 0.12) * (1.0 - ambientPresence * 0.12) * textureWeight;
    double panDrift = (bias->air * 0.3 + bias->water * 0.08) * response;
    double pan = gen_clamp(layer->pan / 100.0 + (gen_mulberry32_next(random) - 0.5) * (0.12 + panDrift) * state->panSpread, -1.0, 1.0);
    double cutoff = profile->cutoff * state->cutoff * (1.0 + profile->brightness * 0.6 + bias->air * response * 0.4 - bias->noise * response * 0.16);
    double freqInterval;
    if (layer->kind == GEN_KIND_RHYTHM) freqInterval = (double)(interval - 24);
    else if (layer->kind == GEN_KIND_VOICE) freqInterval = (double)(interval - 12);
    else freqInterval = (double)interval;
    double frequency = gen_semitone(profile->root, freqInterval);

    int chordIntervals[3]; int chordCount;
    if (layer->kind == GEN_KIND_ATMOSPHERE) { chordIntervals[0] = 0; chordIntervals[1] = 7; chordCount = 2; }
    else if (layer->kind == GEN_KIND_VOICE) { chordIntervals[0] = 0; chordIntervals[1] = 7; chordIntervals[2] = 12; chordCount = 3; }
    else { chordIntervals[0] = 0; chordCount = 1; }
    double chordPeak = fmax(0.001, peak / sqrt((double)chordCount) * (chordCount > 1 ? 0.78 : 1.0));
    double attack = introFirstFire ? fmin(0.14, fmax(0.025, layer->attack)) : fmax(0.025, layer->attack);
    double release = fmax(0.08, layer->release);

    *outCount = chordCount;
    for (int index = 0; index < chordCount; index++) {
        double voicePan = gen_clamp(pan + (index - (chordCount - 1) / 2.0) * 0.12, -1.0, 1.0);
        gen_create_voice(&out[index], timbre, gen_semitone(frequency, (double)chordIntervals[index]), start + index * 0.018,
                         duration, chordPeak, attack, release, voicePan, cutoff, profile,
                         random, layer->id, tick, layer->volume);
    }
    gen_memory_set(memory, layer->id, at);
    memory->lastMusical = at;
    memory->lastLayerId = layer->id;
    return 1;
}

/* 声部数组动态扩容（对照 Kotlin ArrayList<Voice> 无上限）；OOM 返回 0，调用方释放该 voice。 */
static int gen_voices_add(GenVoice** voices, int* count, int* max, GenVoice* v) {
    if (*count >= *max) {
        int newMax = (*max) * 2;
        if (newMax < *max) return 0;
        GenVoice* grown = (GenVoice*)realloc(*voices, (size_t)newMax * sizeof(GenVoice));
        if (grown == NULL) return 0;
        *voices = grown;
        *max = newMax;
    }
    (*voices)[(*count)++] = *v;
    return 1;
}

/* ---------- 渲染一个预设到 float PCM（合成路径，对照 renderPresetPcm） ---------- */

static void gen_render_preset(const char* presetId, int frames, double outputVolume, int gain,
                              float* outL, float* outR) {
    int idx = -1;
    for (int i = 0; i < GEN_ARRANGEMENT_COUNT; i++) if (strcmp(GEN_PRESET_IDS[i], presetId) == 0) { idx = i; break; }
    if (idx < 0) { memset(outL, 0, (size_t)frames * sizeof(float)); memset(outR, 0, (size_t)frames * sizeof(float)); return; }

    GenArrangement a = GEN_ARRANGEMENTS[idx];
    gen_apply_with_target(&a);

    GenMulberry32 random; random.s = gen_fnv1a(a.seed);
    GenSchedulerMemory memory; memset(&memory, 0, sizeof(memory)); memory.lastMusical = -INFINITY;
    GenSettings settings = {54, 42, 48, 1.4, 3.2, 72, 70, 1};
    GenAmbientBias bias = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    GenMasterChain master; gen_master_chain_init(&master, outputVolume, gain, 0.0);

    GenVoice* voices = (GenVoice*)calloc((size_t)GEN_MAX_VOICES, sizeof(GenVoice));
    int voiceCount = 0;
    int maxVoices = GEN_MAX_VOICES;

    int64_t frame = 0;
    int tick = 0;
    double scheduleTime = 0.0;
    double sixteenth = 60.0 / a.tempo / 4.0;

    while (frame < frames) {
        double seconds = (double)frame / GEN_SAMPLE_RATE;
        while (scheduleTime <= seconds + 512.0 / GEN_SAMPLE_RATE && scheduleTime < (double)frames / GEN_SAMPLE_RATE) {
            double at = scheduleTime;
            GenSection s = gen_section_state(&a, at);
            int hasSolo = 0;
            for (int li = 0; li < a.layerCount; li++) hasSolo |= a.layers[li].solo != 0;
            for (int li = 0; li < a.layerCount; li++) {
                GenLayer* layer = &a.layers[li];
                if (hasSolo && !layer->solo) continue;
                int introPending = layer->intro && isinf(gen_memory_last(&memory, layer->id));
                if (introPending || gen_should_schedule_at_tick(layer, layer->timbre, tick, &a.plan)) {
                    GenVoice created[3]; int createdCount = 0;
                    gen_schedule_layer(layer, layer->timbre, tick, at, &s, &bias, &random, &settings, &memory, &a.profile, created, &createdCount);
                    for (int ci = 0; ci < createdCount; ci++) {
                        if (!gen_voices_add(&voices, &voiceCount, &maxVoices, &created[ci])) gen_free_voice(&created[ci]);
                    }
                }
            }
            tick++;
            scheduleTime += sixteenth;
        }
        int block = (frames - frame) < 512 ? (int)(frames - frame) : 512;
        for (int i = 0; i < block; i++) {
            int64_t atFrame = frame + i;
            double left = 0.0, right = 0.0;
            int w = 0;
            for (int vi = 0; vi < voiceCount; vi++) {
                GenVoice* v = &voices[vi];
                if (gen_voice_finished(v, atFrame)) { gen_free_voice(v); continue; }
                double value = gen_voice_sample(v, atFrame);
                double pan = gen_voice_current_pan(v, atFrame);
                left += value * sqrt((1.0 - pan) * 0.5);
                right += value * sqrt((1.0 + pan) * 0.5);
                voices[w++] = *v;
            }
            voiceCount = w;
            double pl, pr;
            gen_master_chain_process(&master, left, right, &pl, &pr);
            outL[frame + i] = (float)pl;
            outR[frame + i] = (float)pr;
        }
        frame += block;
    }

    for (int i = 0; i < voiceCount; i++) gen_free_voice(&voices[i]);
    free(voices);
    if (gen_burst_cache != NULL) { free(gen_burst_cache); gen_burst_cache = NULL; gen_burst_cache_len = 0; }
}

/* JNI：渲染一个完整预设到交织立体声 float PCM（合成路径），返回 [left,right,...]。 */
JNIEXPORT jfloatArray JNICALL Java_com_soundist_app_NativeAudioCore_nativeRenderPreset(
        JNIEnv* env, jobject thiz, jstring presetId, jint frames, jdouble outputVolume, jint gain) {
    (void)thiz;
    const char* id = (*env)->GetStringUTFChars(env, presetId, NULL);
    if (id == NULL || frames <= 0 || frames > INT_MAX / 2) {
        if (id) (*env)->ReleaseStringUTFChars(env, presetId, id);
        return (*env)->NewFloatArray(env, 0);
    }
    int n = frames * 2;
    jfloatArray result = (*env)->NewFloatArray(env, n);
    if (result == NULL) { (*env)->ReleaseStringUTFChars(env, presetId, id); return NULL; }
    jfloat* out = (*env)->GetFloatArrayElements(env, result, NULL);
    if (out == NULL) { (*env)->ReleaseStringUTFChars(env, presetId, id); return result; }
    float* l = (float*)malloc((size_t)frames * sizeof(float));
    float* r = (float*)malloc((size_t)frames * sizeof(float));
    if (l && r) {
        gen_render_preset(id, frames, outputVolume, gain, l, r);
        for (int i = 0; i < frames; i++) { out[i * 2] = l[i]; out[i * 2 + 1] = r[i]; }
    }
    free(l); free(r);
    (*env)->ReleaseFloatArrayElements(env, result, out, 0);
    (*env)->ReleaseStringUTFChars(env, presetId, id);
    return result;
}

/* 注册一个 VSCO 采样（对照 SAMPLE_TIMBRES + decodeWav），供采样路径就近选样。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeRegisterSample(
        JNIEnv* env, jobject thiz, jstring timbre, jdouble baseFrequency, jdouble sampleRate, jshortArray pcm, jint frames, jint channels) {
    (void)thiz;
    const char* tm = (*env)->GetStringUTFChars(env, timbre, NULL);
    if (tm == NULL) return -1;
    if (pcm == NULL || frames <= 0 || channels <= 0 || channels > 2 || frames > INT_MAX / channels) {
        (*env)->ReleaseStringUTFChars(env, timbre, tm);
        return -1;
    }
    jsize len = (*env)->GetArrayLength(env, pcm);
    if (len < frames * channels) { (*env)->ReleaseStringUTFChars(env, timbre, tm); return -1; }
    jshort* src = (*env)->GetShortArrayElements(env, pcm, NULL);
    if (src == NULL) { (*env)->ReleaseStringUTFChars(env, timbre, tm); return -1; }
    gen_register_sample(tm, baseFrequency, sampleRate, src, frames, channels);
    (*env)->ReleaseShortArrayElements(env, pcm, src, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, timbre, tm);
    return 0;
}

/* 清空采样注册表（渲染前调用，避免旧采样残留）。 */
JNIEXPORT void JNICALL Java_com_soundist_app_NativeAudioCore_nativeClearSamples(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;
    gen_clear_samples();
}

/* ==========================================================================
 * 第五片：增量实时渲染引擎（对照 renderLoop，持久化状态 + 分帧渲染）。
 * 说明：渲染在调用方线程（非 ma_device 回调）进行，与旧 Kotlin 渲染器一致的「专用线程 + AudioTrack MODE_STREAM」。
 * 当前为忠实移植版（声部 extra 仍堆分配）；零分配优化（内联 extra + 预分配 burst 缓存）属后续加固。
 * ========================================================================== */

typedef struct {
    int active;
    GenArrangement* arrangement;   /* 深拷贝（layers 已复制，可安全 withTarget） */
    GenLayer* layers;
    GenScene* scenes;              /* 自定义场景（JNI 覆盖），arrangement->scenes 指向这里 */
    char* ownedSeed;               /* 自定义 seed 的堆拷贝（NULL = arrangement->seed 指向静态字面量） */
    int layersOwned;               /* 1 = layers 的 id/timbre/target 是堆拷贝需释放；0 = 指向静态字面量 */
    GenMulberry32 random;
    GenSchedulerMemory memory;
    GenSettings settings;
    GenAmbientBias bias;
    GenMasterChain master;
    GenVoice* voices;
    int voiceCount;
    int maxVoices;
    int64_t frame;
    int tick;
    double scheduleTime;
    double outputVolume;
    double currentVolume;           /* 音量 ramp 当前值（对照 renderLoop 的 currentVolume，约 30ms 逼近 outputVolume） */
    /* 热更新：层参数 prev 快照 + 当前场景亮度（对照 renderLoop 的 prevLayerVolumes/prevLayerPans/prevCurrentSceneBrightness） */
    int* prevLayerVolumes;
    double* prevLayerPans;
    double prevSceneBrightness;
} GenEngine;

static GenEngine* gen_engine_create(const char* presetId, double outputVolume, int gain) {
    int idx = -1;
    for (int i = 0; i < GEN_ARRANGEMENT_COUNT; i++) if (strcmp(GEN_PRESET_IDS[i], presetId) == 0) { idx = i; break; }
    if (idx < 0) return NULL;
    GenEngine* e = (GenEngine*)calloc(1, sizeof(GenEngine));
    if (!e) return NULL;
    e->arrangement = (GenArrangement*)malloc(sizeof(GenArrangement));
    if (!e->arrangement) { free(e); return NULL; }
    *e->arrangement = GEN_ARRANGEMENTS[idx];
    e->layers = (GenLayer*)malloc(sizeof(GenLayer) * e->arrangement->layerCount);
    if (!e->layers) { free(e->arrangement); free(e); return NULL; }
    memcpy(e->layers, e->arrangement->layers, sizeof(GenLayer) * e->arrangement->layerCount);
    e->arrangement->layers = e->layers;
    gen_apply_with_target(e->arrangement);

    e->random.s = gen_fnv1a(e->arrangement->seed);
    memset(&e->memory, 0, sizeof(e->memory));
    e->memory.lastMusical = -INFINITY;
    e->settings = (GenSettings){54, 42, 48, 1.4, 3.2, 72, 70, 1};
    e->bias = (GenAmbientBias){0, 0, 0, 0, 0, 0};
    gen_master_chain_init(&e->master, outputVolume, gain, 0.0);
    e->maxVoices = GEN_MAX_VOICES;
    e->voices = (GenVoice*)calloc((size_t)GEN_MAX_VOICES, sizeof(GenVoice));
    if (!e->voices) { free(e->layers); free(e->arrangement); free(e); return NULL; }
    e->prevLayerVolumes = (int*)malloc(sizeof(int) * e->arrangement->layerCount);
    e->prevLayerPans = (double*)malloc(sizeof(double) * e->arrangement->layerCount);
    if (!e->prevLayerVolumes || !e->prevLayerPans) {
        free(e->prevLayerVolumes); free(e->prevLayerPans);
        free(e->voices); free(e->layers); free(e->arrangement); free(e);
        return NULL;
    }
    for (int i = 0; i < e->arrangement->layerCount; i++) { e->prevLayerVolumes[i] = -1; e->prevLayerPans[i] = 0.0; }
    e->prevSceneBrightness = 0.0;
    e->active = 1;
    e->outputVolume = outputVolume;
    return e;
}

/* 热更新参数 ramp：每块（每次 render 调用）检测层音量/声像/场景亮度变化，对存量声部做 ramp。
 * 对照 Kotlin renderLoop 的 prevLayerVolumes/prevLayerPans/prevCurrentSceneBrightness 检测逻辑。 */
static void gen_engine_hot_update(GenEngine* e) {
    GenArrangement* a = e->arrangement;
    int64_t atFrame = e->frame;
    for (int li = 0; li < a->layerCount; li++) {
        GenLayer* layer = &a->layers[li];
        int prevVol = e->prevLayerVolumes[li];
        if (prevVol != -1 && prevVol != layer->volume) {
            for (int vi = 0; vi < e->voiceCount; vi++) {
                GenVoice* v = &e->voices[vi];
                if (strcmp(v->layerId, layer->id) == 0 && v->baseLayerVolume > 0)
                    gen_voice_set_gain_scale(v, (double)layer->volume / (double)v->baseLayerVolume, atFrame);
            }
        }
        if (prevVol != -1 && e->prevLayerPans[li] != layer->pan) {
            for (int vi = 0; vi < e->voiceCount; vi++) {
                GenVoice* v = &e->voices[vi];
                if (strcmp(v->layerId, layer->id) == 0)
                    gen_voice_set_pan_target(v, layer->pan, atFrame);
            }
        }
        e->prevLayerVolumes[li] = layer->volume;
        e->prevLayerPans[li] = layer->pan;
    }
    double currBrightness = gen_current_scene_brightness(a, (double)atFrame / GEN_SAMPLE_RATE);
    if (e->prevSceneBrightness > 0.0 && currBrightness > 0.0 && e->prevSceneBrightness != currBrightness) {
        double ratio = currBrightness / e->prevSceneBrightness;
        for (int vi = 0; vi < e->voiceCount; vi++) {
            GenVoice* v = &e->voices[vi];
            gen_voice_set_cutoff_target(v, v->cutoff * ratio, atFrame);
        }
    }
    e->prevSceneBrightness = currBrightness;
}

static void gen_engine_render(GenEngine* e, float* outL, float* outR, int frameCount) {
    if (e == NULL || !e->active) { memset(outL, 0, (size_t)frameCount * sizeof(float)); memset(outR, 0, (size_t)frameCount * sizeof(float)); return; }
    GenArrangement* a = e->arrangement;
    /* tempo 来自 settings（对照 play() 里 arrangement.tempo = settings.tempo），支持速度滑杆 */
    double sixteenth = 60.0 / (double)e->settings.tempo / 4.0;

    gen_engine_hot_update(e);

    /* 音量 ramp（对照 renderLoop 的 currentVolume 逼近 outputVolume，约 30ms 无 click） */
    e->currentVolume += (e->outputVolume - e->currentVolume) * (1.0 - exp(-(double)frameCount / GEN_SAMPLE_RATE / GEN_VOLUME_RAMP_TAU));
    gen_master_chain_set_volume(&e->master, e->currentVolume, e->settings.gain);

    for (int i = 0; i < frameCount; i++) {
        int64_t atFrame = e->frame;
        double seconds = (double)atFrame / GEN_SAMPLE_RATE;
        /* 调度 lookahead */
        while (e->scheduleTime <= seconds + 512.0 / GEN_SAMPLE_RATE) {
            double at = e->scheduleTime;
            GenSection s = gen_section_state(a, at);
            int hasSolo = 0;
            for (int li = 0; li < a->layerCount; li++) hasSolo |= a->layers[li].solo != 0;
            for (int li = 0; li < a->layerCount; li++) {
                GenLayer* layer = &a->layers[li];
                if (hasSolo && !layer->solo) continue;
                int introPending = layer->intro && isinf(gen_memory_last(&e->memory, layer->id));
                if (introPending || gen_should_schedule_at_tick(layer, layer->timbre, e->tick, &a->plan)) {
                    GenVoice created[3]; int createdCount = 0;
                    gen_schedule_layer(layer, layer->timbre, e->tick, at, &s, &e->bias, &e->random, &e->settings, &e->memory, &a->profile, created, &createdCount);
                    for (int ci = 0; ci < createdCount; ci++) {
                        if (!gen_voices_add(&e->voices, &e->voiceCount, &e->maxVoices, &created[ci])) gen_free_voice(&created[ci]);
                    }
                }
            }
            e->tick++;
            e->scheduleTime += sixteenth;
        }
        /* 混音 */
        double left = 0.0, right = 0.0;
        int w = 0;
        for (int vi = 0; vi < e->voiceCount; vi++) {
            GenVoice* v = &e->voices[vi];
            /* 层被删除/禁用 → 存量声部进入可控 release（对照 renderLoop 的 activeLayerIds 检测） */
            int layerActive = 0;
            for (int li = 0; li < a->layerCount; li++) {
                if (strcmp(v->layerId, a->layers[li].id) == 0) {
                    layerActive = a->layers[li].enabled && a->layers[li].kind != GEN_KIND_AMBIENT;
                    break;
                }
            }
            if (!layerActive) gen_voice_begin_release(v, atFrame);
            if (gen_voice_finished(v, atFrame)) { gen_free_voice(v); continue; }
            double value = gen_voice_sample(v, atFrame);
            double pan = gen_voice_current_pan(v, atFrame);
            left += value * sqrt((1.0 - pan) * 0.5);
            right += value * sqrt((1.0 + pan) * 0.5);
            e->voices[w++] = *v;
        }
        e->voiceCount = w;
        double pl, pr;
        gen_master_chain_process(&e->master, left, right, &pl, &pr);
        outL[i] = (float)pl;
        outR[i] = (float)pr;
        e->frame++;
    }
}

static void gen_engine_free(GenEngine* e) {
    if (e == NULL) return;
    for (int i = 0; i < e->voiceCount; i++) gen_free_voice(&e->voices[i]);
    free(e->voices);
    free(e->prevLayerVolumes);
    free(e->prevLayerPans);
    free(e->ownedSeed);
    if (e->layersOwned) gen_free_layer_strings(e->layers, e->arrangement->layerCount);
    free(e->layers);
    if (e->scenes != NULL) gen_free_scenes(e->scenes, e->arrangement->sceneCount);
    free(e->arrangement);
    free(e);
}

/* JNI：创建增量渲染引擎，返回句柄（0 = 失败）。 */
JNIEXPORT jlong JNICALL Java_com_soundist_app_NativeAudioCore_nativeCreateGenerativeEngine(JNIEnv* env, jobject thiz, jstring presetId, jdouble outputVolume, jint gain) {
    (void)thiz;
    const char* id = (*env)->GetStringUTFChars(env, presetId, NULL);
    if (id == NULL) return 0;
    GenEngine* e = gen_engine_create(id, outputVolume, gain);
    (*env)->ReleaseStringUTFChars(env, presetId, id);
    return (jlong)(uintptr_t)e;
}

/* JNI：渲染 frameCount 帧到交织立体声 float PCM（增量续播）。 */
JNIEXPORT jfloatArray JNICALL Java_com_soundist_app_NativeAudioCore_nativeRenderGenerative(JNIEnv* env, jobject thiz, jlong handle, jint frameCount) {
    (void)thiz;
    GenEngine* e = (GenEngine*)(uintptr_t)handle;
    if (frameCount <= 0 || frameCount > INT_MAX / 2) return (*env)->NewFloatArray(env, 0);
    int n = frameCount * 2;
    jfloatArray result = (*env)->NewFloatArray(env, n);
    if (result == NULL) return NULL;
    if (e == NULL) return result;
    jfloat* out = (*env)->GetFloatArrayElements(env, result, NULL);
    if (out == NULL) return result;
    float* l = (float*)malloc((size_t)frameCount * sizeof(float));
    float* r = (float*)malloc((size_t)frameCount * sizeof(float));
    if (l && r) {
        gen_engine_render(e, l, r, frameCount);
        for (int i = 0; i < frameCount; i++) { out[i * 2] = l[i]; out[i * 2 + 1] = r[i]; }
    }
    free(l); free(r);
    (*env)->ReleaseFloatArrayElements(env, result, out, 0);
    return result;
}

/* JNI：销毁引擎。 */
JNIEXPORT void JNICALL Java_com_soundist_app_NativeAudioCore_nativeDestroyGenerativeEngine(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    gen_engine_free((GenEngine*)(uintptr_t)handle);
}

/* JNI：更新引擎 settings（tempo/density/...），支持速度滑杆等自定义设置；gain 变化同步 master 音量。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetGenerativeSettings(
        JNIEnv* env, jobject thiz, jlong handle, jint tempo, jint density, jint variation,
        jdouble entryDelay, jdouble eventDuration, jint gain, jint ambientResponse, jint adaptive) {
    (void)env; (void)thiz;
    GenEngine* e = (GenEngine*)(uintptr_t)handle;
    if (e == NULL || !e->active) return -1;
    e->settings.tempo = tempo;
    e->settings.density = density;
    e->settings.variation = variation;
    e->settings.entryDelay = entryDelay;
    e->settings.eventDuration = eventDuration;
    e->settings.gain = gain;
    e->settings.ambientResponse = ambientResponse;
    e->settings.adaptive = adaptive != 0;
    /* master 音量由 gen_engine_render 每块的 ramp 统一设置，此处不再直接改 */
    return 0;
}

/* JNI：更新引擎输出音量。用户语义值仍为 0..1；Kotlin 会把经审核的预设响度
 * trim 乘在其后，因此这里允许最多 16x 的校准增益。最终仍经过 MasterChain soft clip。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetGenerativeOutputVolume(
        JNIEnv* env, jobject thiz, jlong handle, jdouble outputVolume) {
    (void)env; (void)thiz;
    GenEngine* e = (GenEngine*)(uintptr_t)handle;
    if (e == NULL || !e->active) return -1;
    if (outputVolume < 0.0) outputVolume = 0.0;
    if (outputVolume > 16.0) outputVolume = 16.0;
    e->outputVolume = outputVolume;
    return 0;
}

/* JNI：更新引擎环境偏置（对照 AmbientBias.from(activeAmbient) → bias 各分量），作用于 eventChance/scheduleLayer。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetGenerativeAmbientBias(
        JNIEnv* env, jobject thiz, jlong handle, jdouble level, jdouble water, jdouble air, jdouble warm, jdouble city, jdouble noise) {
    (void)env; (void)thiz;
    GenEngine* e = (GenEngine*)(uintptr_t)handle;
    if (e == NULL || !e->active) return -1;
    e->bias.level = level;
    e->bias.water = water;
    e->bias.air = air;
    e->bias.warm = warm;
    e->bias.city = city;
    e->bias.noise = noise;
    return 0;
}

/* JNI：渲染一个音色的试听声部（对照 previewTimbre：gen_create_voice 走采样/合成路径 + 包络 + 主链路），
 * 返回 mono float PCM（双声道试听从同一采样复制）。采样音色需先经 registerSample 注册。 */
JNIEXPORT jfloatArray JNICALL Java_com_soundist_app_NativeAudioCore_nativeRenderPreviewVoice(
        JNIEnv* env, jobject thiz, jstring timbre, jdouble frequency, jdouble durationSeconds,
        jdouble attackSeconds, jdouble releaseSeconds, jdouble pan, jdouble cutoffHz,
        jdouble brightness, jdouble room, jdouble gain, jint seed, jint outputFrames) {
    (void)thiz;
    const char* tm = (*env)->GetStringUTFChars(env, timbre, NULL);
    if (tm == NULL || outputFrames <= 0 || outputFrames > INT_MAX / 2) {
        if (tm) (*env)->ReleaseStringUTFChars(env, timbre, tm);
        return (*env)->NewFloatArray(env, 0);
    }
    jfloatArray result = (*env)->NewFloatArray(env, outputFrames);
    if (result == NULL) { (*env)->ReleaseStringUTFChars(env, timbre, tm); return NULL; }
    jfloat* out = (*env)->GetFloatArrayElements(env, result, NULL);
    if (out == NULL) { (*env)->ReleaseStringUTFChars(env, timbre, tm); return result; }

    GenProfile profile = {261.63, NULL, 0, brightness, (int)cutoffHz, room, NULL};
    GenMulberry32 random; random.s = (uint32_t)seed;
    GenVoice voice;
    gen_create_voice(&voice, tm, frequency, 0.0, durationSeconds, gain, attackSeconds, releaseSeconds, pan, cutoffHz, &profile, &random, "", 0, 52);
    GenMasterChain master;
    gen_master_chain_init(&master, 0.8, 72, 0.0);
    for (int i = 0; i < outputFrames; i++) {
        double v = gen_voice_finished(&voice, (int64_t)i) ? 0.0 : gen_voice_sample(&voice, (int64_t)i);
        double l, r;
        gen_master_chain_process(&master, v * sqrt(0.5), v * sqrt(0.5), &l, &r);
        out[i] = (jfloat)l;
    }
    gen_free_voice(&voice);

    (*env)->ReleaseFloatArrayElements(env, result, out, 0);
    (*env)->ReleaseStringUTFChars(env, timbre, tm);
    return result;
}

/* JNI：覆盖引擎的 scenes（对照 play() 的 GeneratedScene 转换）。durations 为每场景分钟数，
 * energy/density/brightness/space 为 0-100 原始值；ids 为场景 id 元数据。空数组即清空场景。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetGenerativeScenes(
        JNIEnv* env, jobject thiz, jlong handle, jobjectArray ids, jintArray durations,
        jdoubleArray energy, jdoubleArray density, jdoubleArray brightness, jdoubleArray space) {
    (void)thiz;
    GenEngine* e = (GenEngine*)(uintptr_t)handle;
    if (e == NULL || !e->active) return -1;
    if (ids == NULL || durations == NULL || energy == NULL || density == NULL || brightness == NULL || space == NULL) return -1;
    jsize n = (*env)->GetArrayLength(env, durations);
    if (n < 0 || (*env)->GetArrayLength(env, ids) != n ||
        (*env)->GetArrayLength(env, energy) != n || (*env)->GetArrayLength(env, density) != n ||
        (*env)->GetArrayLength(env, brightness) != n || (*env)->GetArrayLength(env, space) != n) return -1;

    if (e->scenes != NULL) gen_free_scenes(e->scenes, e->arrangement->sceneCount);
    e->scenes = NULL;
    e->arrangement->scenes = NULL;
    e->arrangement->sceneCount = 0;
    e->prevSceneBrightness = 0.0;
    if (n == 0) return 0;

    GenScene* scenes = (GenScene*)calloc((size_t)n, sizeof(GenScene));
    if (scenes == NULL) return -1;
    jint* dur = (*env)->GetIntArrayElements(env, durations, NULL);
    jdouble* en = (*env)->GetDoubleArrayElements(env, energy, NULL);
    jdouble* de = (*env)->GetDoubleArrayElements(env, density, NULL);
    jdouble* br = (*env)->GetDoubleArrayElements(env, brightness, NULL);
    jdouble* sp = (*env)->GetDoubleArrayElements(env, space, NULL);
    if (dur == NULL || en == NULL || de == NULL || br == NULL || sp == NULL) {
        if (dur) (*env)->ReleaseIntArrayElements(env, durations, dur, JNI_ABORT);
        if (en) (*env)->ReleaseDoubleArrayElements(env, energy, en, JNI_ABORT);
        if (de) (*env)->ReleaseDoubleArrayElements(env, density, de, JNI_ABORT);
        if (br) (*env)->ReleaseDoubleArrayElements(env, brightness, br, JNI_ABORT);
        if (sp) (*env)->ReleaseDoubleArrayElements(env, space, sp, JNI_ABORT);
        gen_free_scenes(scenes, (int)n);
        return -1;
    }
    for (jsize i = 0; i < n; i++) {
        scenes[i].durationMinutes = (int)dur[i];
        scenes[i].energy = en[i];
        scenes[i].density = de[i];
        scenes[i].brightness = br[i];
        scenes[i].space = sp[i];
    }
    (*env)->ReleaseIntArrayElements(env, durations, dur, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, energy, en, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, density, de, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, brightness, br, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, space, sp, JNI_ABORT);

    /* 拷贝 id 字符串（所有权转移给 engine，释放时用 gen_free_scenes） */
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, ids, i);
        if (s != NULL) {
            const char* c = (*env)->GetStringUTFChars(env, s, NULL);
            scenes[i].id = (c != NULL) ? gen_strdup(c) : NULL;
            if (c != NULL) (*env)->ReleaseStringUTFChars(env, s, c);
            (*env)->DeleteLocalRef(env, s);
        }
    }

    e->scenes = scenes;
    e->arrangement->scenes = scenes;
    e->arrangement->sceneCount = (int)n;
    return 0;
}

/* JNI：更新单个层的实时参数（音量/声像/启用/独奏），下一块渲染时对存量声部做 ramp（对照「试听当前编排」）。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeUpdateGenerativeLayer(
        JNIEnv* env, jobject thiz, jlong handle, jint layerIndex, jint volume, jdouble pan, jint enabled, jint solo) {
    (void)env; (void)thiz;
    GenEngine* e = (GenEngine*)(uintptr_t)handle;
    if (e == NULL || !e->active) return -1;
    if (layerIndex < 0 || layerIndex >= e->arrangement->layerCount) return -2;
    GenLayer* layer = &e->layers[layerIndex];
    layer->volume = volume;
    layer->pan = pan;
    layer->enabled = enabled != 0;
    layer->solo = solo != 0;
    return 0;
}

/* JNI：整体替换引擎编排（对照 play() 里 generatorArrangement 的 seed/sectionMinutes/arc/layers）。
 * reset=1（全重启）：清声部 + 重置 RNG/调度/prev/master（等价 stopLocked + 新 worker）。
 * reset=0（热更新，试听当前编排）：仅替换层数据，保留声部/调度/RNG/音量，层字符串有界泄漏（声部仍引用）。
 * scenes 由 nativeSetGenerativeScenes 单独设置。枚举 int：kind 0..4、relationship 0..3、register 0..3、scale 0..3、arc 0..2。 */
JNIEXPORT jint JNICALL Java_com_soundist_app_NativeAudioCore_nativeSetGenerativeArrangement(
        JNIEnv* env, jobject thiz, jlong handle, jstring seed, jint sectionMinutes, jint arc,
        jobjectArray ids, jintArray kinds, jobjectArray timbres,
        jintArray volumes, jintArray probabilities, jintArray densities,
        jdoubleArray durations, jdoubleArray attacks, jdoubleArray releases,
        jdoubleArray pans, jdoubleArray entryDelays, jintArray rhythms,
        jintArray relationships, jobjectArray targets, jintArray registers,
        jintArray scales, jintArray ambientResponses, jintArray enabledArr, jintArray soloArr,
        jintArray introArr, jboolean reset) {
    (void)thiz;
    GenEngine* e = (GenEngine*)(uintptr_t)handle;
    if (e == NULL || !e->active) return -1;
    if (seed == NULL || ids == NULL || kinds == NULL || timbres == NULL ||
        volumes == NULL || probabilities == NULL || densities == NULL ||
        durations == NULL || attacks == NULL || releases == NULL ||
        pans == NULL || entryDelays == NULL || rhythms == NULL ||
        relationships == NULL || targets == NULL || registers == NULL ||
        scales == NULL || ambientResponses == NULL || enabledArr == NULL || soloArr == NULL || introArr == NULL) return -1;

    jsize n = (*env)->GetArrayLength(env, ids);
    if (n <= 0) return -1;
    if ((*env)->GetArrayLength(env, kinds) != n || (*env)->GetArrayLength(env, timbres) != n ||
        (*env)->GetArrayLength(env, volumes) != n || (*env)->GetArrayLength(env, probabilities) != n ||
        (*env)->GetArrayLength(env, densities) != n || (*env)->GetArrayLength(env, durations) != n ||
        (*env)->GetArrayLength(env, attacks) != n || (*env)->GetArrayLength(env, releases) != n ||
        (*env)->GetArrayLength(env, pans) != n || (*env)->GetArrayLength(env, entryDelays) != n ||
        (*env)->GetArrayLength(env, rhythms) != n || (*env)->GetArrayLength(env, relationships) != n ||
        (*env)->GetArrayLength(env, targets) != n || (*env)->GetArrayLength(env, registers) != n ||
        (*env)->GetArrayLength(env, scales) != n || (*env)->GetArrayLength(env, ambientResponses) != n ||
        (*env)->GetArrayLength(env, enabledArr) != n || (*env)->GetArrayLength(env, soloArr) != n ||
        (*env)->GetArrayLength(env, introArr) != n) return -1;

    const char* seedC = (*env)->GetStringUTFChars(env, seed, NULL);
    if (seedC == NULL) return -1;

    jint* pKinds = (*env)->GetIntArrayElements(env, kinds, NULL);
    jint* pVol = (*env)->GetIntArrayElements(env, volumes, NULL);
    jint* pProb = (*env)->GetIntArrayElements(env, probabilities, NULL);
    jint* pDens = (*env)->GetIntArrayElements(env, densities, NULL);
    jdouble* pDur = (*env)->GetDoubleArrayElements(env, durations, NULL);
    jdouble* pAtk = (*env)->GetDoubleArrayElements(env, attacks, NULL);
    jdouble* pRel = (*env)->GetDoubleArrayElements(env, releases, NULL);
    jdouble* pPan = (*env)->GetDoubleArrayElements(env, pans, NULL);
    jdouble* pEntry = (*env)->GetDoubleArrayElements(env, entryDelays, NULL);
    jint* pRhy = (*env)->GetIntArrayElements(env, rhythms, NULL);
    jint* pRel2 = (*env)->GetIntArrayElements(env, relationships, NULL);
    jint* pReg = (*env)->GetIntArrayElements(env, registers, NULL);
    jint* pScale = (*env)->GetIntArrayElements(env, scales, NULL);
    jint* pAmb = (*env)->GetIntArrayElements(env, ambientResponses, NULL);
    jint* pEn = (*env)->GetIntArrayElements(env, enabledArr, NULL);
    jint* pSolo = (*env)->GetIntArrayElements(env, soloArr, NULL);
    jint* pIntro = (*env)->GetIntArrayElements(env, introArr, NULL);
    if (pKinds == NULL || pVol == NULL || pProb == NULL || pDens == NULL ||
        pDur == NULL || pAtk == NULL || pRel == NULL || pPan == NULL || pEntry == NULL ||
        pRhy == NULL || pRel2 == NULL || pReg == NULL || pScale == NULL ||
        pAmb == NULL || pEn == NULL || pSolo == NULL || pIntro == NULL) {
        if (pKinds) (*env)->ReleaseIntArrayElements(env, kinds, pKinds, JNI_ABORT);
        if (pVol) (*env)->ReleaseIntArrayElements(env, volumes, pVol, JNI_ABORT);
        if (pProb) (*env)->ReleaseIntArrayElements(env, probabilities, pProb, JNI_ABORT);
        if (pDens) (*env)->ReleaseIntArrayElements(env, densities, pDens, JNI_ABORT);
        if (pDur) (*env)->ReleaseDoubleArrayElements(env, durations, pDur, JNI_ABORT);
        if (pAtk) (*env)->ReleaseDoubleArrayElements(env, attacks, pAtk, JNI_ABORT);
        if (pRel) (*env)->ReleaseDoubleArrayElements(env, releases, pRel, JNI_ABORT);
        if (pPan) (*env)->ReleaseDoubleArrayElements(env, pans, pPan, JNI_ABORT);
        if (pEntry) (*env)->ReleaseDoubleArrayElements(env, entryDelays, pEntry, JNI_ABORT);
        if (pRhy) (*env)->ReleaseIntArrayElements(env, rhythms, pRhy, JNI_ABORT);
        if (pRel2) (*env)->ReleaseIntArrayElements(env, relationships, pRel2, JNI_ABORT);
        if (pReg) (*env)->ReleaseIntArrayElements(env, registers, pReg, JNI_ABORT);
        if (pScale) (*env)->ReleaseIntArrayElements(env, scales, pScale, JNI_ABORT);
        if (pAmb) (*env)->ReleaseIntArrayElements(env, ambientResponses, pAmb, JNI_ABORT);
        if (pEn) (*env)->ReleaseIntArrayElements(env, enabledArr, pEn, JNI_ABORT);
        if (pSolo) (*env)->ReleaseIntArrayElements(env, soloArr, pSolo, JNI_ABORT);
        if (pIntro) (*env)->ReleaseIntArrayElements(env, introArr, pIntro, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, seed, seedC);
        return -1;
    }

    /* 先全部分配，失败则整体回退，不动 engine 状态（原子提交）。 */
    GenLayer* layers = (GenLayer*)calloc((size_t)n, sizeof(GenLayer));
    char* newSeed = gen_strdup(seedC);
    int* newPrevVol = (int*)malloc(sizeof(int) * (size_t)(n > 0 ? n : 1));
    double* newPrevPan = (double*)malloc(sizeof(double) * (size_t)(n > 0 ? n : 1));
    if (layers == NULL || newSeed == NULL || newPrevVol == NULL || newPrevPan == NULL) {
        free(layers); free(newSeed); free(newPrevVol); free(newPrevPan);
        if (pKinds) (*env)->ReleaseIntArrayElements(env, kinds, pKinds, JNI_ABORT);
        if (pVol) (*env)->ReleaseIntArrayElements(env, volumes, pVol, JNI_ABORT);
        if (pProb) (*env)->ReleaseIntArrayElements(env, probabilities, pProb, JNI_ABORT);
        if (pDens) (*env)->ReleaseIntArrayElements(env, densities, pDens, JNI_ABORT);
        if (pDur) (*env)->ReleaseDoubleArrayElements(env, durations, pDur, JNI_ABORT);
        if (pAtk) (*env)->ReleaseDoubleArrayElements(env, attacks, pAtk, JNI_ABORT);
        if (pRel) (*env)->ReleaseDoubleArrayElements(env, releases, pRel, JNI_ABORT);
        if (pPan) (*env)->ReleaseDoubleArrayElements(env, pans, pPan, JNI_ABORT);
        if (pEntry) (*env)->ReleaseDoubleArrayElements(env, entryDelays, pEntry, JNI_ABORT);
        if (pRhy) (*env)->ReleaseIntArrayElements(env, rhythms, pRhy, JNI_ABORT);
        if (pRel2) (*env)->ReleaseIntArrayElements(env, relationships, pRel2, JNI_ABORT);
        if (pReg) (*env)->ReleaseIntArrayElements(env, registers, pReg, JNI_ABORT);
        if (pScale) (*env)->ReleaseIntArrayElements(env, scales, pScale, JNI_ABORT);
        if (pAmb) (*env)->ReleaseIntArrayElements(env, ambientResponses, pAmb, JNI_ABORT);
        if (pEn) (*env)->ReleaseIntArrayElements(env, enabledArr, pEn, JNI_ABORT);
        if (pSolo) (*env)->ReleaseIntArrayElements(env, soloArr, pSolo, JNI_ABORT);
        if (pIntro) (*env)->ReleaseIntArrayElements(env, introArr, pIntro, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, seed, seedC);
        return -1;
    }

    for (jsize i = 0; i < n; i++) {
        layers[i].id = gen_jstring_dup(env, ids, i);
        layers[i].timbre = gen_jstring_dup(env, timbres, i);
        layers[i].target = gen_jstring_dup(env, targets, i);
        layers[i].kind = (GenKind)pKinds[i];
        layers[i].volume = (int)pVol[i];
        layers[i].probability = (int)pProb[i];
        layers[i].density = (int)pDens[i];
        layers[i].duration = pDur[i];
        layers[i].attack = pAtk[i];
        layers[i].release = pRel[i];
        layers[i].pan = pPan[i];
        layers[i].entryDelay = pEntry[i];
        layers[i].rhythm = (int)pRhy[i];
        layers[i].relationship = (GenRelationship)pRel2[i];
        layers[i].reg = (GenRegister)pReg[i];
        layers[i].scale = (GenScale)pScale[i];
        layers[i].ambientResponse = (int)pAmb[i];
        layers[i].enabled = pEn[i] != 0;
        layers[i].solo = pSolo[i] != 0;
        layers[i].intro = pIntro[i] != 0;
        newPrevVol[i] = -1;
        newPrevPan[i] = 0.0;
    }

    (*env)->ReleaseIntArrayElements(env, kinds, pKinds, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, volumes, pVol, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, probabilities, pProb, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, densities, pDens, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, durations, pDur, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, attacks, pAtk, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, releases, pRel, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, pans, pPan, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, entryDelays, pEntry, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, rhythms, pRhy, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, relationships, pRel2, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, registers, pReg, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, scales, pScale, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, ambientResponses, pAmb, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, enabledArr, pEn, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, soloArr, pSolo, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, introArr, pIntro, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, seed, seedC);

    /* 提交：替换层数据。全重启先清声部（其 layerId/timbre 指向旧层字符串）并释放旧字符串；
     * 热更新保留声部/调度/RNG/音量，旧层字符串被声部引用故有界泄漏（每次试听约几百字节）。 */
    if (reset) {
        for (int i = 0; i < e->voiceCount; i++) gen_free_voice(&e->voices[i]);
        e->voiceCount = 0;
        if (e->layersOwned) gen_free_layer_strings(e->layers, e->arrangement->layerCount);
    }
    free(e->ownedSeed);
    free(e->layers);
    free(e->prevLayerVolumes);
    free(e->prevLayerPans);
    if (reset) {
        if (e->scenes != NULL) gen_free_scenes(e->scenes, e->arrangement->sceneCount);
        e->scenes = NULL;
        e->arrangement->scenes = NULL;
        e->arrangement->sceneCount = 0;
    }

    e->ownedSeed = newSeed;
    e->layers = layers;
    e->layersOwned = 1;
    e->prevLayerVolumes = newPrevVol;
    e->prevLayerPans = newPrevPan;
    e->arrangement->seed = newSeed;
    e->arrangement->sectionMinutes = sectionMinutes;
    e->arrangement->arc = (arc >= GEN_ARC_STEADY && arc <= GEN_ARC_JOURNEY) ? (GenArc)arc : GEN_ARC_BREATHING;
    e->arrangement->layers = layers;
    e->arrangement->layerCount = (int)n;

    if (reset) {
        /* 重置确定性状态 + master（等价新 worker 的 renderLoop 初始状态）。 */
        e->random.s = gen_fnv1a(newSeed);
        memset(&e->memory, 0, sizeof(e->memory));
        e->memory.lastMusical = -INFINITY;
        e->tick = 0;
        e->scheduleTime = 0.0;
        e->frame = 0;
        e->prevSceneBrightness = 0.0;
        e->currentVolume = 0.0;
        gen_master_chain_init(&e->master, e->outputVolume, e->settings.gain, 0.0);
    }
    return 0;
}
