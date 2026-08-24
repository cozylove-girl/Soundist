# Soundist Android 音频许可与发行说明

最近核对：2026-08-24

本文是 Android 安装包中音频资产的统一入口。它记录来源、许可义务、文件哈希和证据边界，不替代各许可证法律文本，也不构成法律意见。

## 项目关系与许可方式

Soundist 是基于 Moodist 的深度二次开发，不是 Moodist 官方版本。它沿用了 Moodist 的环境声基础，同时新增了独立的 Android 原生应用、品牌与交互、音频引擎、持续声场、电台、专注、记录、习惯和笔记等功能。

许可处理遵循 Moodist 的公开做法：保留 Moodist 的 MIT 版权与许可文本；对继承的第三方环境声保留 Moodist 公布的“Pixabay Content License 或 CC0”汇总声明。由于上游没有逐文件许可映射，Soundist 不会无依据地把 84 个文件全部改标为 CC0。

## 实际打包范围

| 资产组 | 数量 | 当前许可证据 | 发行判断 |
|---|---:|---|---|
| 开放精选电台 | 299 | 逐曲来源页、许可证、作者/演奏者字段、官方页面快照、打包 SHA-256 | 299 首均被审计脚本归类为可再分发；其中 57 首要求署名，90 首还带 ShareAlike 条件 |
| Moodist 环境声 | 84 | 与 Moodist 上游提交逐文件 SHA-256 一致；上游 MIT 文本与第三方资产声明已归档 | 作为 Moodist 二次开发沿用上游的 Pixabay/CC0 汇总声明；不虚构逐文件许可证 |
| VSCO 2 CE 乐器采样 | 15 | CC0 1.0 全文、来源仓库、逐文件 SHA-256 | 可随 App 复制、修改和分发，无强制署名 |
| 专注/休息提示音 | 1 | 本仓库提交 `4218d2573a429c70751733b007817de6a4f881db` 引入，SHA-256 已记录 | Soundist 项目资产，随本仓库按根目录 MIT License 分发 |

## 开放精选电台

- 当前打包数量、技术清单和许可清单均为 299 首，文件哈希全部一致。
- 发行级逐曲数据：`tools/license-snapshots/release-catalog-2026-08-20/release-license-manifest.json`
- 面向人的署名清单：`RADIO_RELEASE_ATTRIBUTION.md`
- 许可证网页快照：`tools/license-snapshots/release-catalog-2026-08-20/licenses/`
- 来源页快照：同目录的 `wikimedia/`、`opengameart/` 与 `incompetech/`。
- App 已在电台详情中保存并展示逐曲作者、来源页和许可证链接。CC BY 与 CC BY-SA 信息不得从发行版中删除。

`RADIO_LICENSE_MANIFEST.md` 现为当前许可入口摘要，不再保留容易与现状冲突的早期 80 首表格。

## Moodist 环境声

来源仓库：<https://github.com/remvze/moodist>

核对上游提交：`983f7412e8cd054e76d156977c563da2028e4428`

Soundist 的 84 个文件与该提交下 `public/sounds/` 的对应文件逐字节一致。完整对应表见 `AMBIENT_AUDIO_MANIFEST.tsv`，上游声明快照见 `tools/license-snapshots/moodist-ambient-2026-08-24/`。

Moodist 的 MIT 许可证适用于其软件；其 README 明确说明部分声音另受以下许可约束：

- Pixabay Content License：<https://pixabay.com/service/license-summary/>
- CC0 1.0：<https://creativecommons.org/publicdomain/zero/1.0/>

重要边界：Moodist 没有公开 84 个文件各自属于 Pixabay 还是 CC0，也没有逐文件原始素材页面。因此 Soundist 能完整证明文件来自哪个 Moodist 版本、与上游逐字节一致，并保留上游的全部公开许可声明；但不能诚实地为每个文件单独指定 CC0 或 Pixabay。

Soundist 当前按 Moodist 已公开采用的方式集成和发行这些环境声，并将它们作为混音、定时、可视化和持续声场产品的一部分，而不是另行发布为独立素材包。若未来上游补充逐文件来源表，可直接把它并入 `AMBIENT_AUDIO_MANIFEST.tsv`，不需要修改 App 功能代码。

## VSCO 2 CE 采样

来源：<https://github.com/sgossner/VSCO-2-CE>

上游仓库将样本库标为 CC0 1.0。完整法律文本保存在 `app/src/main/assets/instruments/vsco/VSCO-2-CE-LICENSE.txt`；15 个实际打包样本的路径、大小和 SHA-256 见 `VSCO_SAMPLE_LICENSE_MANIFEST.tsv`。

## 项目自有提示音

`app/src/main/res/raw/focus_transition_chime.wav`

- 大小：63,548 bytes
- SHA-256：`9638f0491ba5aeb005c9fc4a042b023ee1ec5f5b70ce91ee0e3c4dba7a2f9710`
- 首次提交：`4218d2573a429c70751733b007817de6a4f881db`

仓库中没有它的外部下载记录或第三方许可页，因此将其登记为 Soundist 项目自有提示音，并随本仓库按根目录 MIT License 分发。若以后替换为第三方提示音，必须在替换时新增来源、许可证与 SHA-256，不能沿用本条记录。

## 发行时必须保留

1. 根目录 `LICENSE` 中 Moodist 派生软件的 MIT 版权与许可文本。
2. `RADIO_RELEASE_ATTRIBUTION.md` 及逐曲证据清单，或者在 App 内提供等价且可访问的完整署名。
3. 所有 CC BY-SA 曲目的许可证名称、作者、来源和 ShareAlike 提示。
4. VSCO 2 CE 的 CC0 文本与样本清单。
5. Moodist 环境声的上游来源、提交号、混合许可声明和 Pixabay/CC0 链接。

## 未被本文授权的内容

用户自行导入的本地文件、自定义流和封面不由 Soundist 授权。用户应自行确认其使用权；这些内容不应自动上传或并入 Soundist 官方曲库。
