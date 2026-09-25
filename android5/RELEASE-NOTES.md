最低系统保持 **Android 5.0 / API 21**。下载 `myDV-Android5-1.1.9-a5.1.apk`。

- 在公开 Lite 1.1.9 APK 基础上重建播放器：LibVLC 3.7.6 软件解码，避开旧系统 H.264 OMX 解码错误；首次安装默认选择 720p，画质仍可手动修改。
- 当前视频播放后预下载下一条 MP4。一次只下载一条，单文件上限 32 MiB，保留最多两条、总计不超过 64 MiB；直播、图集及超限视频跳过。完整缓存命中时从本地文件播放。预下载限速 512 KiB/s，前台缓冲时暂停。连续快速切换、缓存未完成或 CPU 解码较慢时仍可能等待。
- 独立包名 `com.dycomment.tv.android5`，可与原版并存。原版 Cookie/设置不会自动迁移，登录仍使用原 Lite 的“设置Cookie”。本次没有增加扫码登录。
- 包含 ARMv7、ARM64、x86、x86_64 原生库。软件解码会增加 CPU 使用率、APK 体积，老电视建议 720p。

发布门槛：GitHub Actions 的 API 21 x86 模拟器必须实际输出 H.264 High/Baseline 视频、播放时间前进；验证下一条提前下载、切换后无需再次请求视频、暂停、快进和倍速。附件 RESULT.txt、截图及工作流日志可核对结果。测试不等同于所有电视、所有在线视频、账号或直播都已通过。

这是兼容性预发布版，使用独立签名。完整上游 Android 源码未公开，本仓库保留公开 APK 的其他代码，仅替换播放器并做必要兼容补丁；构建脚本、新增 Java 代码及依赖校验值位于 `android5/`。上游原始签名不再保留。

LibVLC 按 LGPL-2.1 分发，未修改其二进制。绑定源代码随 release 附送；原生库及构建源码见 https://code.videolan.org/videolan/libvlcjni 和 https://code.videolan.org/videolan/vlc 。使用的官方 Maven 坐标为 `org.videolan.android:libvlc-all:3.7.6`。兼容版更新请使用本仓库 Releases，已关闭原版自动更新检测。
