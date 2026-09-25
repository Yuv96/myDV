# Android 5.0 播放与交互兼容补丁

上游公开仓库不包含 Android 工程。本目录以 SHA-256 固定的官方 Lite 1.1.9 APK 为输入，通过 Apktool + javac + D8 重建。a5.1 替换 `PlayerView` 并添加 `NextVideoCache`；a5.2 保留这两者，增加纯文字菜单、遥控器互动面板、关注直播及未读通知提示。a5.3 修复慢网络下的播放器释放阻塞、快速往返的旧请求覆盖及底栏提前切换，详见 [切换修复说明](SWITCHING-NOTES.md)。没有降低声明版本来掩盖新 API，最低系统保持 API 21。

a5.2 的功能范围、验证方法和未完成事项见 [INTERACTION-NOTES.md](INTERACTION-NOTES.md)。好友分享暂未接通，未读数暂不包含私信。点赞、关注、收藏写操作仍需实际使用验证，开发验证没有代替用户执行这些账号操作。

原 `PlayerView.openVideo()` 每次释放并创建系统 `android.media.MediaPlayer`；`MainActivity.playAt()` 在切换时调用它。原 `loadMoreFeed()` 仅预取视频列表，`VideoProxyServer` 只转发当前视频的网络字节，没有下一条内容缓存。

现在 `PlayerView` 使用 LibVLC 3.7.6 自带的软件解码器，不调用旧系统 OMX H.264。预缓存于视频输出后延迟至少 1.5 秒启动（前台缓冲后至少 5 秒），复用原列表中的下一条 URL；缺少 URL 时提前调用原详情接口。只缓存完整 MP4，单条最多 32 MiB、两条共 64 MiB、30 分钟有效期；后台下载上限 512 KiB/s，正文下载最多 20 秒。命中后直接传本地文件给解码器。切换或前台缓冲时取消旧预取，不同时解码两个视频。

限制：完整下载前不复用部分缓存；直播、图集、未知长度或超过容量的视频不预缓存；快速连续跳过、网络较慢、旧 CPU 软件解码较慢时仍可能停顿。Cookie 登录方式没有改变。新包名为 `com.dycomment.tv.android5`，与原版并存，需重新填写 Cookie。

## 构建

安装 JDK 17、Android SDK platform 35 / build-tools 35.0.0、FFmpeg，设置 `ANDROID_HOME`，执行 `python3 android5/build.py`。编译用 SDK 35 不代表最低要求提高：manifest 和 D8 的最低版本均为 21，目标版本保持 30。

依赖下载全部校验 SHA-256。LibVLC 原生库包含 ARMv7、ARM64、x86、x86_64。源码中的生成测试视频使用 FFmpeg `testsrc2`，不包含用户视频。

CI 临时签名 APK 仅用于测试；正式 Release 使用单独备份的长期签名重新签署 CI 产物。不要将签名私钥提交到仓库。自建签名可通过 `ANDROID5_KEYSTORE`、`ANDROID5_KEYSTORE_PASSWORD` 配置，别名为 `android5`。

## 验证

`.github/workflows/android5-player.yml` 在 API 21 x86 模拟器运行 `PlaybackSelfTestActivity`：播放 H.264 High 和 Baseline 生成视频，要求视频输出和播放进度；在播放第一条时提前下载第二条，切换后检查 HTTP 请求计数没有增加；再验证暂停、seek、倍速。之后启动实际主界面并尝试在线推荐视频。自测结果和在线结果分开记录；合成视频通过不等于所有在线视频或电视机型都通过。

手动运行自测：`adb shell am start -n com.dycomment.tv.android5/com.dycomment.tv.PlaybackSelfTestActivity`。仅使用内置生成视频和本机回环 HTTP 服务，不读取账号信息。

## 第三方来源

- 原 APK：https://github.com/mytv-android/myDV/releases/tag/V1.1.9
- 本补丁完整构建源：https://github.com/Yuv96/myDV/tree/main/android5
- 官方 LibVLC Maven 坐标：`org.videolan.android:libvlc-all:3.7.6`，其 AAR 声明 minSdk 17；绑定源码 JAR 随 Release 提供。
- LibVLC JNI / 原生库构建源码：https://code.videolan.org/videolan/libvlcjni
- VLC 原生引擎源码：https://code.videolan.org/videolan/vlc
- LibVLC LGPL-2.1 许可正文见 `LIBVLC-LICENSE.txt`，同时嵌入 APK assets。LibVLC 二进制未修改，按官方 AAR 分发；可以在构建脚本中更换或自行重编译该 AAR。

本目录新增补丁源码沿用本仓库 GPL-3.0 许可。原版其他功能仍来自公开 APK，上游未提供的完整 Android 源码不在本目录中。
