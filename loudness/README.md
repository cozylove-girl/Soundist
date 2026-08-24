# 生成电台频道响度校准（任务七）—— 离线测量与版本化 Manifest 说明

本目录说明「生成电台」10 个官方 preset 的响度校准流程：目标分档、测量工具、如何生成
`app/src/main/assets/loudness_manifest.json`（运行时由主会话读取，本目录与生成测试只负责构建期/离线）。

---

## 1. 目标分档（专注 / 休息 / 助眠）

不统一成一个数值。按「用途 × 编排稠密度」把 10 个 preset 归为三档，目标用**集成响度范围**
表达（中心值 ± 容差；单位 Integrated LUFS，EBU R128）：

| 分档 | 中心 LUFS | 容差 | 归类 preset | 依据 |
|---|---|---|---|---|
| 助眠 sleep | -55 | ±3 | deep-sea, moon-tide, night-train | 名字/用途即睡眠场景：深海、月潮、夜行列车；编排以低音 atmosphere 为主、事件稀疏，响度需压到环境底噪级别，不打扰入睡 |
| 专注 focus | -40 | ±2 | endless-focus, warm-study | 名字/用途即专注场景：稳定电钢/脉冲/低音弦；需保持可感知的存在感又低于音乐性内容，帮助集中但不抢注意力 |
| 休息 rest | -45 | ±3 | rain-piano, morning-mist, signal-garden, bamboo-strings, custom-lab | 聆听/休息场景居中：雨窗钢琴、晨雾、信号花园等，比助眠清晰、比专注克制 |

档位依据（产品判断）：真实设备播放时系统音量 + 用户耳音量已经叠加衰减，频道内绝对 LUFS 只决定
「频道间的相对响度一致」；三档间隔 5–10 LU 足够在 UI 上感知差异，同时避免助眠档过响、专注档过弱。
最终数值需在真机多设备复核后微调（见 §4）。

## 2. 当前测量工具：自写 EBU R128 合成路径（临时停靠）

当前没有 FFmpeg/libebur128，**临时**用渲染器自带的离线测量入口：

```
NativeGeneratedAudioRenderer.measurePresetLoudness(preset, seconds): LoudnessReport
```

它内部做两件事：

1. `renderPresetPcm(preset, frames)` —— 与运行期 `renderLoop` 完全一致的调度 + 混音 + MasterChain
   （master gain → DynamicsCompressor → Limiter），但不写 AudioTrack；**强制合成路径**（`samples` 恒返回
   null，不依赖 VSCO 采样资产），所以结果确定、可离线复现。
2. `measureLoudness(pcm)` —— 自写 EBU R128（ITU-R BS.1770-4）：立体声→单声道→K 加权→400ms 块→
   绝对/相对门限→Integrated LUFS；3s 短时块→LRA；4× 线性插值过采样→True Peak dBTP；另有 RMS/峰值。

**为什么是近似、不可当作权威结论：**

- 无真实 VSCO 采样：`vsco-*` 音色在测量里全部走 `SAMPLE_FALLBACKS` 的合成替代（如
  `vsco-upright-piano`→`soft-piano`、`vsco-cello`→`warm-strings`）。真实采样样本的固有响度、
  发声时长、音符密度都和合成替代不同。
- 非实际立体声素材：测量把左右声道各按 `sqrt((1∓pan)/2)` 混为单声道再测，而真实输出是立体声
  AudioTrack；立体声相位/通道差会微调 True Peak 与 LUFS。
- 单 seed：`renderPresetPcm` 只用编排固定 seed（`"<preset>-soundist-02"`）的一次确定性渲染，
  不代表多代表性 seed 的平均响度。

因此 manifest 里每项都标了 `"measuredBy": "self-write-ebur128-synth"` + `"synthesisPath": true`，
提醒任何读到它的代码/人：这是**第一版占位校准**，不是 EBU R128 权威结论。

## 3. 生成 manifest（回归步骤）

```bash
cd apps/android-native
export JAVA_HOME="D:/DevTools/Java/temurin-17/jdk-17.0.20+8"
export GRADLE_USER_HOME="D:/DevTools/Gradle"
./gradlew :app:testDebugUnitTest --tests "com.soundist.app.LoudnessManifestGenTest" --no-build-cache --console=plain
```

生成测试 `app/src/test/java/com/soundist/app/LoudnessManifestGenTest.kt`：

- 对 10 个 preset 各调 `measurePresetLoudness(preset, 90)`（90s 合成渲染）；
- 按 §1 分档取 `trimDb = targetLufs - measuredLufs`；
- **True Peak 上限检查**：`trimDb` 作用后 True Peak ≈ `truePeakDbTp + trimDb`，若 > -1 dBTP
  则 clamp 到刚好 -1 dBTP 并记 `clamped=true`（当前版本无 preset 触发 clamp）；
- 把结果写成 `app/src/main/assets/loudness_manifest.json`（schemaVersion 1）。

**重要：重新生成前必须保证 `PRESET_GAIN_TRIM_DB` 全部为 0（基线）**。`measurePresetLoudness`/
`renderPresetPcm` 内部会读 `PRESET_GAIN_TRIM_DB`，一旦主会话把 manifest 的 trim 填回运行时，
再跑本生成测试会把「已应用 trim」当成基线测量，`trimDb` 与已生效 trim 形成反馈，产生错误的全 0
结果。若需要带 trim 的测量入口，请让主会话增加 `measurePresetLoudness(..., trimDb=0.0)` 显式入参。

## 4. 最终应换成 FFmpeg / libebur128（真实采样复核）

有 FFmpeg 后，对**真实采样 + 实际立体声 + 多代表性 seed** 的离线渲染 WAV 做权威测量：

```bash
# 先生成离线渲染 WAV（真实采样路径，多 seed），再：
ffmpeg -i out.wav -filter_complex ebur128=peak=true -f null -
# 或程序化：libebur128 对同一 WAV 直接算 Integrated / True Peak
```

替换步骤：

1. 给渲染器/工具加「真实采样 + 实际立体声 + 多 seed」的离线渲染出口（写入 WAV，而不是走 AudioTrack）；
2. 对每个 preset × 3–5 个代表性 seed × 120–300s 渲染，逐一过 `ebur128=peak=true`；
3. 取 Integrated LUFS 的平均值（或中位数）作为 `measuredLufs`，True Peak 取最大 seed 的
   `truePeakDbTp`；
4. 按同一 `trimDb = targetLufs - measuredLufs` + True Peak clamp 逻辑重算，`measuredBy` 改为
   `"ffmpeg-ebur128-real-samples"`、`synthesisPath` 改为 `false`，bump `schemaVersion`；
5. 重跑生成测试覆盖 `loudness_manifest.json`，并在真机（外放 + 耳机）抽查三档各一个 preset。

复核后若某档整体偏高/偏低，只调整 §1 的 `targets` 中心值再重生成，不需要动渲染器。

## 5. manifest 结构（schemaVersion 1）

```jsonc
{
  "schemaVersion": 1,                       // 结构变更递增
  "generatedBy": "…/LoudnessManifestGenTest.kt",
  "generatedAt": "2026-08-19",
  "measuredBy": "self-write-ebur128-synth", // 待换 ffmpeg-ebur128-real-samples
  "measureSeconds": 90,
  "sampleRate": 44100,
  "channelLayout": "stereo-interleaved",
  "synthesisPath": true,                    // 待换 false
  "note": "…",
  "targets": {                              // 分档定义
    "sleep": { "label": "助眠", "targetLufs": -55.0, "toleranceDb": 3.0 },
    "focus": { "label": "专注", "targetLufs": -40.0, "toleranceDb": 2.0 },
    "rest":  { "label": "休息", "targetLufs": -45.0, "toleranceDb": 3.0 }
  },
  "presets": {                              // 运行时按 preset 查 trimDb
    "deep-sea": {
      "category": "sleep",
      "targetLufs": -55.0,
      "measuredLufs": -31.3,
      "trimDb": -23.7,                      // 运行时加到 MasterChain 的频道级增益（dB）
      "truePeakDbTp": -25.8,
      "clamped": false
    },
    // …其余 9 个 preset 同构
  }
}
```

运行时读取（主会话实现）：`presets[preset].trimDb` 传给 `MasterChain(outputVolume, gain, trimDb)`。
`targets` 目前仅用于文档/校验，不参与运行时增益。

## 6. 已知风险

- **合成近似**：见 §2，`measuredLufs` 只在「全合成音色的 preset」（deep-sea / night-train /
  moon-tide / signal-garden 全部层为合成音色）上相对接近真实；含 VSCO 采样的 preset
  （rain-piano / morning-mist / endless-focus / warm-study / bamboo-strings / custom-lab）测量偏差更大，
  复核前 trim 可能明显不准，属于**可接受的第一版占位**。
- **trim 跨度大**：当前合成路径下 trim 从 -23.7 dB（deep-sea）到 +13.1 dB（signal-garden）。
  大 cut 只压低响度、无失真风险；大 boost 依赖 Limiter 保护，True Peak 已 clamp 检查（当前全部
  `clamped=false`，TP 距 -1 dBTP 仍有 ≥24 dB 余量）。真实采样复核大概率收窄这个跨度。
- **再生反馈**：见 §3，PRESET_GAIN_TRIM_DB 非全 0 时禁止重生成 manifest。
- **真机抽查**：LUFS 是节目响度，不代表设备音量/外放响度；复核阶段务必真机三档各抽查。
