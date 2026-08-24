# Soundist 电台许可清单

最近核对：2026-08-24

本文是当前电台许可资料的索引，不再重复保存已经过时的 80 首早期核验表。

## 当前状态

| 项目 | 结果 |
|---|---:|
| Android 实际打包曲目 | 299 |
| 已匹配本地音频文件 | 299 |
| SHA-256 校验通过 | 299 |
| 审计脚本判定可再分发 | 299 |
| 需要署名 | 57 |
| 需要署名并遵守 ShareAlike | 90 |
| 阻止发行或仍待核验 | 0 |

## 权威资料

- 面向用户与发行者的逐曲署名：`RADIO_RELEASE_ATTRIBUTION.md`
- 机器可读逐曲许可、来源、作者和 SHA-256：`tools/license-snapshots/release-catalog-2026-08-20/release-license-manifest.json`
- 许可证文本快照：`tools/license-snapshots/release-catalog-2026-08-20/licenses/`
- 来源页快照：同目录的 `wikimedia/`、`opengameart/` 与 `incompetech/`
- 实际打包文件清单：`RADIO_AUDIO_MANIFEST.tsv`
- 全部 Android 音频许可入口：`THIRD_PARTY_AUDIO_NOTICES.md`

## 发行规则

1. 发行版必须保留 `RADIO_RELEASE_ATTRIBUTION.md`，或在 App 内提供内容等价、可访问的署名页。
2. CC BY 曲目必须保留作者、作品名、来源和许可证。
3. CC BY-SA 曲目除署名外，还必须保留对应 ShareAlike 许可证说明。
4. Public Domain 与 CC0 曲目仍保留来源记录，便于复核文件身份。
5. 曲目文件发生替换后必须重新计算 SHA-256，并重新运行 `tools/audit_packaged_audio_licenses.ps1`。

早期 80 首核验数据曾用于曲库建设过程，但已经被当前 299 首完整证据清单取代，不再作为发行判断依据。
