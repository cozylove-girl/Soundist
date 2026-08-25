# Soundist Android 音频来源与许可

最近核对：2026-08-24

本文汇总 Android 安装包中音频资产的来源、适用许可、文件哈希与必要署名。本文不替代各许可证法律文本，也不构成法律意见。

## 项目关系与许可方式

Soundist 是基于 Moodist 的深度二次开发，不是 Moodist 官方版本。它沿用了 Moodist 的环境声基础，同时新增了独立的 Android 原生应用、品牌与交互、音频引擎、持续声场、电台、专注、记录、习惯和笔记等功能。

许可处理遵循 Moodist 的公开做法：保留 Moodist 的 MIT 版权与许可文本；对继承的第三方环境声保留 Moodist 公布的“Pixabay Content License 或 CC0”汇总声明。由于上游没有逐文件许可映射，Soundist 不会无依据地把 84 个文件全部改标为 CC0。

## 随应用提供的音频

| 资产组 | 数量 | 许可依据 | 适用条件 |
|---|---:|---|---|
| 开放精选电台 | 299 | 逐曲来源页、许可证、创作者/演奏者、页面存档与 SHA-256 | 152 首为 Public Domain、CC0 或不强制署名；57 首适用 CC BY；90 首适用 CC BY-SA |
| Moodist 环境声 | 84 | 与 Moodist 上游提交逐文件 SHA-256 一致；上游 MIT 文本与第三方资产声明已归档 | 作为 Moodist 二次开发沿用上游的 Pixabay/CC0 汇总声明；不虚构逐文件许可证 |
| VSCO 2 CE 乐器采样 | 15 | CC0 1.0 全文、来源仓库、逐文件 SHA-256 | 可随 App 复制、修改和分发，无强制署名 |
| 专注/休息提示音 | 1 | 本仓库提交 `4218d2573a429c70751733b007817de6a4f881db` 引入，SHA-256 已记录 | Soundist 项目自有资产，随根目录 PolyForm Noncommercial 1.0.0 条款提供 |

## 开放精选电台

- 安装包、音频清单与许可清单均包含 299 首录音，文件哈希一致。
- 逐曲机器可读数据：`tools/license-snapshots/release-catalog-2026-08-20/release-license-manifest.json`
- 逐曲署名与来源：`RADIO_RELEASE_ATTRIBUTION.md`
- Opus 衍生文件的转码前后路径、哈希与码率：`RADIO_OPUS_TRANSCODE.tsv`
- 许可证网页快照：`tools/license-snapshots/release-catalog-2026-08-20/licenses/`
- 来源页快照：同目录的 `wikimedia/`、`opengameart/` 与 `incompetech/`。
- App 在电台详情中提供逐曲创作者、来源页和许可证链接。CC BY 与 CC BY-SA 要求的信息不得从再分发版本中删除。

安装包内的开放精选统一使用 Ogg Opus 衍生文件。转码只改变编码格式和打包哈希，不改变原录音的许可证；来源页、原始来源标识及转码前文件哈希继续保留在证据清单中。

## Moodist 环境声

来源仓库：<https://github.com/remvze/moodist>

核对上游提交：`983f7412e8cd054e76d156977c563da2028e4428`

Soundist 的 84 个文件与该提交下 `public/sounds/` 的对应文件逐字节一致。完整对应表见 `AMBIENT_AUDIO_MANIFEST.tsv`，上游声明快照见 `tools/license-snapshots/moodist-ambient-2026-08-24/`。

Moodist 的 MIT 许可证适用于其软件；其 README 明确说明部分声音另受以下许可约束：

- Pixabay Content License：<https://pixabay.com/service/license-summary/>
- CC0 1.0：<https://creativecommons.org/publicdomain/zero/1.0/>

重要边界：Moodist 没有公开 84 个文件各自属于 Pixabay 还是 CC0，也没有逐文件原始素材页面。因此 Soundist 能完整证明文件来自哪个 Moodist 版本、与上游逐字节一致，并保留上游的全部公开许可声明；但不能诚实地为每个文件单独指定 CC0 或 Pixabay。

Soundist 按 Moodist 公布的许可声明集成这些环境声，并将其用于应用内混音、定时、可视化和持续声场，不将其作为独立素材包提供。`AMBIENT_AUDIO_MANIFEST.tsv` 保留逐文件哈希及上游对应关系。

## VSCO 2 CE 采样

来源：<https://github.com/sgossner/VSCO-2-CE>

上游仓库将样本库标为 CC0 1.0。完整法律文本保存在 `app/src/main/assets/instruments/vsco/VSCO-2-CE-LICENSE.txt`；15 个实际打包样本的路径、大小和 SHA-256 见 `VSCO_SAMPLE_LICENSE_MANIFEST.tsv`。

## 项目自有提示音

`app/src/main/res/raw/focus_transition_chime.wav`

- 大小：63,548 bytes
- SHA-256：`9638f0491ba5aeb005c9fc4a042b023ee1ec5f5b70ce91ee0e3c4dba7a2f9710`
- 来源记录：本仓库提交 `4218d2573a429c70751733b007817de6a4f881db`

该文件为 Soundist 项目自有提示音，随根目录 PolyForm Noncommercial 1.0.0 条款提供，不属于 Moodist 的 MIT 授权范围。替换该文件时须同时更新来源、适用许可证和 SHA-256。

## 再分发时须保留

1. 根目录 `LICENSE` 中 Soundist 自有部分适用的 PolyForm Noncommercial 1.0.0 条款及全部 `Required Notice:`。
2. `LICENSES/Moodist-MIT.txt` 中 Moodist 继承部分适用的 MIT 版权与许可文本。
3. `RADIO_RELEASE_ATTRIBUTION.md` 及逐曲证据清单，或者在 App 内提供等价且可访问的完整署名。
4. 所有 CC BY-SA 曲目的许可证名称、作者、来源和 ShareAlike 提示。
5. VSCO 2 CE 的 CC0 文本与样本清单。
6. Moodist 环境声的上游来源、提交号、混合许可声明和 Pixabay/CC0 链接。

## 未被本文授权的内容

用户自行导入的本地文件、自定义流和封面不由 Soundist 授权。用户应自行确认其使用权；这些内容不应自动上传或并入 Soundist 官方曲库。
