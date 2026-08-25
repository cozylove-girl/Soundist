# 生成电台响度校准

本目录说明十个官方持续声场的离线响度测量、校准目标和清单维护方式。运行时读取 `app/src/main/assets/loudness_manifest.json`，按频道应用已经审核的增益修正。

## 校准目标

不同使用场景保留有意的响度差异，不统一到同一个数值。

| 场景 | 目标响度 | 官方持续声场 |
|---|---:|---|
| 助眠 | -55 LUFS | deep-sea、moon-tide、night-train |
| 专注 | -40 LUFS | endless-focus、warm-study |
| 休息 | -45 LUFS | rain-piano、morning-mist、signal-garden、bamboo-strings、custom-lab |

这些数值用于保持频道之间的相对一致性。设备音量、耳机灵敏度和用户设置仍决定最终听感。

## 已提交清单

`loudness_manifest.json` 使用 schemaVersion 2，记录：

- 测量方法与修订号；
- 音频引擎和采样包版本；
- 场景分类与目标 LUFS；
- 各频道的 Integrated LUFS、True Peak 和 trimDb；
- 测量所用采样路径及内容指纹字段。

清单基于真实 VSCO 2 CE 采样和立体声离线渲染生成。Integrated LUFS 与 True Peak 由项目内的 BS.1770/EBU R128 实现计算，`measuredBy` 为 `self-write-ebur128-real-samples`。

当前清单的 `contentHash` 仍为占位值，且尚未经过 FFmpeg/libebur128 独立复核。它可以用于运行时频道增益，但不能被表述为已经完成外部权威校准；完成复核后须同时更新内容指纹、测量方法和修订号。

## 重新测量

```powershell
cd apps/android-native
$env:JAVA_HOME = 'D:\DevTools\Java\temurin-17\jdk-17.0.20+8'
$env:GRADLE_USER_HOME = 'D:\DevTools\Gradle'
.\gradlew.bat :app:testDebugUnitTest --tests "com.soundist.app.LoudnessRealSampleMeasurementTest" --no-build-cache --console=plain
```

测量输出写入 `app/build/loudness/`：

- 每个持续声场的 90 秒立体声 WAV；
- `real_loudness_report.tsv`；
- 新生成的 `loudness_manifest.json`。

普通测试不会直接覆盖 `src/main/assets`。审核结果后再显式替换正式清单，避免一次测试意外改变发行配置。

## 独立复核

项目内测量器适合确定性回归。正式调整响度时，建议再用 FFmpeg `ebur128` 或 libebur128 对生成的 WAV 进行独立复核：

```bash
ffmpeg -i app/build/loudness/rain-piano.wav -filter_complex ebur128=peak=true -f null -
```

复核后更新 `measuredBy`、`measurementRevision`、测量结果和内容指纹。只有 JSON 结构发生变化时才递增 `schemaVersion`。

## 维护约束

1. 更换采样、生成规则、MasterChain 或频道配方后重新测量受影响的持续声场。
2. True Peak 修正后不得超过 -1 dBTP。
3. 响度清单只负责频道级校准，不替代用户的总音量、环境声音量和电台音量。
4. 发布前分别用外放和耳机抽查助眠、专注与休息场景。
