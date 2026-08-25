# Soundist 开放精选许可索引

核对日期：2026-08-24

本文汇总 Soundist Android 安装包内开放精选音乐的许可资料。每一首录音均对应本地音频文件、来源信息、许可证记录和 SHA-256；逐曲署名及完整证据由下方文件提供。

## 曲库概览

| 项目 | 数量 |
|---|---:|
| 收录录音 | 299 |
| 已匹配音频文件与 SHA-256 | 299 |
| Public Domain、CC0 或不强制署名 | 152 |
| 需要署名（CC BY） | 57 |
| 需要署名并遵守相同方式共享（CC BY-SA） | 90 |

## 许可资料

- 逐曲署名与来源：`RADIO_RELEASE_ATTRIBUTION.md`
- 机器可读的逐曲许可、创作者、来源和 SHA-256：`tools/license-snapshots/release-catalog-2026-08-20/release-license-manifest.json`
- 许可证文本存档：`tools/license-snapshots/release-catalog-2026-08-20/licenses/`
- 来源页面存档：同目录的 `wikimedia/`、`opengameart/` 与 `incompetech/`
- 安装包音频文件清单：`RADIO_AUDIO_MANIFEST.tsv`
- 转码前后文件路径、哈希与码率：`RADIO_OPUS_TRANSCODE.tsv`
- Android 全部音频来源与许可：`THIRD_PARTY_AUDIO_NOTICES.md`

## 再分发要求

1. 随应用提供 `RADIO_RELEASE_ATTRIBUTION.md`，或在应用内提供内容等价且可访问的完整署名。
2. CC BY 录音须保留作品名、创作者、来源和许可证信息。
3. CC BY-SA 录音除完整署名外，还须遵守相应版本的 ShareAlike 条款。
4. Public Domain 与 CC0 录音仍保留来源和文件哈希，以便确认录音身份。
5. 替换任何音频文件后，须更新 SHA-256 和逐曲许可记录，并重新执行 `tools/audit_packaged_audio_licenses.ps1`。
