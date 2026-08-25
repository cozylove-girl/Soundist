<div align="center">
  <img src="branding/soundist-mark-transparent.png" width="104" alt="Soundist fox mark" />
  <h2>Soundist 声境</h2>
  <p>让声音成为一处可以停留的地方。</p>
  <p><em>A place to stay, made of sound.</em></p>
  <p>
    <a href="https://github.com/cozylove-girl/Soundist/releases/latest"><strong>下载 Android 版</strong></a>
    &nbsp;&nbsp;·&nbsp;&nbsp;
    <a href="https://ifdian.net/a/luna4work"><strong>在爱发电支持</strong></a>
  </p>
  <p><sub>前往最新 Release，下载 APK 后即可安装</sub></p>
</div>

---

有些时刻，我们并不需要更多信息。

只需要雨落在窗上。风从树梢经过。远处的列车，缓慢驶向夜色。

或者一段不过分打扰的音乐，让思绪有地方安静下来。

Soundist 收集这些细微的声音，也收留那些想暂时离开喧闹的人。

<p align="center">
  <strong>不是为了填满每一分钟。<br />只是让你在需要时，有一处声音可以停留。</strong>
</p>

## 声音

雨、风、河流、火焰，还有城市深夜仍未停下的呼吸。轻轻叠在一起，调到刚好，留下一处自己的声场。

有些旋律没有固定终点。它们缓慢生长，持续变化，也在开放音乐中等待一次安静的相遇。

## 时间

把一段时间交给自己。正计时，倒计时，或一个番茄钟。计划不必催促，习惯也可以一点一点形成。

夜深以后，设好睡眠时间。声音会慢慢变轻，在你入睡之前安静离开。

## 留下

留住刚才想到的事。一句话，一张图片，一段录音，一笔尚未写完的字。可以很完整，也可以只保存此刻。

然后在某一天回头看看：哪场雨陪你读完一本书，哪段音乐经过一次专注，哪些平凡的日子正在成为习惯。

无需登录，也不必始终联网。声音、笔记与记录，首先属于你的设备，也属于你自己。

### 声音会留下痕迹

首页的深海星云会回应此刻正在播放的声音。星点明灭，微光漂流，像远处仍在呼吸的海。

一段专注会记得陪伴它的雨声。一篇笔记也可以留住当时的电台。声音不再停留在播放器里，而是在时间、睡眠与记忆之间，留下安静的线索。

## 开始

Soundist 当前优先支持 Android。

无需配置开发环境，可以直接前往 [最新 Release](https://github.com/cozylove-girl/Soundist/releases/latest) 下载 APK。首次安装时，Android 可能会询问是否允许安装来自浏览器或文件管理器的应用。

<details>
<summary>从源码构建</summary>

需要 JDK 17、Android SDK 36、Android NDK 27 与 CMake 3.22.1。

```powershell
git clone --depth 1 https://github.com/cozylove-girl/Soundist.git
cd Soundist
.\gradlew.bat :app:assembleDebug
```

</details>

## 从 Moodist 出发

Soundist 最初受到 [Moodist](https://github.com/remvze/moodist) 的启发，并在它的环境声体验上继续生长。如今，它更关注声音如何陪伴一段时间，又如何成为个人记录的一部分。

感谢 Moodist，以及所有开放音乐、演奏、采样与工具的创作者。第三方内容的来源和许可均单独保留。

## 一起完善声境

欢迎提交 Issue、建议与 Pull Request。真实的使用感受尤其珍贵：哪一种声音让你愿意久留，哪个动作打断了沉浸，哪些地方还不够自然。

视觉也欢迎一起完善。你可以提交应用图标、启动画面、动效分镜或界面改进的[设计提案](https://github.com/cozylove-girl/Soundist/issues/new?template=design_proposal.yml)，并直接在 Issue 中附上图片、原型或 Figma 链接。请说明设计思路、素材来源、使用许可和生成式 AI 的参与情况；提案是否采用及如何调整，由项目维护者最终决定。

提交代码、图片或音频前，请确认你拥有相应的分享权利。

参与之前请阅读 [贡献指南](CONTRIBUTING.md)。安全问题请通过 [安全报告流程](SECURITY.md) 私下提交；关于本地数据、权限与联网行为，参见 [隐私说明](PRIVACY.md)。

## 支持 Soundist

如果 Soundist 曾陪你度过一段安静的时间，也可以在 [爱发电](https://ifdian.net/a/luna4work) 支持它继续生长。

你的支持会用于声音整理、设备适配与后续维护。无论是否赞助，Soundist 的现有功能都不会因此受到限制。

## 许可

Soundist 源码公开，并欢迎非商业使用与共同改进，但不允许未经授权的商业复制。由于采用非商业许可，Soundist 属于 source-available 软件，而不是 OSI 定义下的开源软件。

- Soundist 自有代码采用 [PolyForm Noncommercial 1.0.0](LICENSE)，中文参考见 [LICENSE.zh-CN.md](LICENSE.zh-CN.md)。
- Moodist 衍生部分继续遵守 [Moodist MIT License](LICENSES/Moodist-MIT.txt)。
- 音频、采样和第三方代码遵守各自的许可与署名要求。
- Soundist 名称、Logo、狐狸形象、图标与启动插画不随代码授权，详见 [品牌素材声明](BRAND_ASSETS_LICENSE.md)。
- 应用图标与启动插画的生成式人工智能辅助来源见 [品牌素材来源记录](BRAND_ASSET_PROVENANCE.md)。

完整来源与致谢见 [NOTICE.md](NOTICE.md) 和 [THIRD_PARTY_AUDIO_NOTICES.md](THIRD_PARTY_AUDIO_NOTICES.md)。商业使用请事先取得仓库所有者的书面许可。
