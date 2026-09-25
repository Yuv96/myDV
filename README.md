# Android 5.0 TV 修复版

最低系统保持 **Android 5.0 / API 21**。新版基于 Lite 1.1.9，使用 LibVLC 软件解码修复旧系统 H.264 播放路径，并增加下一条 MP4 预缓存。

**[下载修复版 APK](https://github.com/Yuv96/myDV/releases/download/android5-player-v1/myDV-Android5-1.1.9-a5.1.apk)** · [发布说明](https://github.com/Yuv96/myDV/releases/tag/android5-player-v1) · [构建与播放测试](https://github.com/Yuv96/myDV/actions/workflows/android5-player.yml)

已在 API 21 x86 模拟器验证 H.264 High/Baseline 视频输出、播放进度、下一条提前下载和切换后的缓存命中，也观察到在线推荐视频正常出画面。电视实机、个人账号、直播等仍需实际验证。建议老电视先使用默认 720p。

原版只有视频列表预取，没有下一条视频内容缓存。新版在当前视频播放后预下载下一条完整 MP4，单条上限 32 MiB，总缓存上限 64 MiB。缓存未完成、视频过大、连续快速切换或 CPU 较慢时仍会等待；不承诺所有情况无停顿。

新包名 `com.dycomment.tv.android5`、名称 **myDV Android5**，可与原版并存。原版 Cookie/设置不会自动迁移；登录仍为“返回键 → 设置Cookie”，本次没有新增扫码登录。

公开上游不含 Android 工程，因此这是基于官方 APK 的可复现兼容补丁，不是完整源码重编译。新增 Java 源码、固定依赖与构建过程见 [android5/](android5/)。原始 Lite APK 的安装启动验证发布保留在 [旧版记录](https://github.com/Yuv96/myDV/releases/tag/android5-lite-v1.1.9)。

---

<div align="center">
    <h1>my Debug Video<sup>TV</sup></h1>


![GitHub Repo stars](https://img.shields.io/github/stars/mytv-android/myDV)
![GitHub all releases](https://img.shields.io/github/downloads/mytv-android/myDV/total)
[![Android Sdk Require](https://img.shields.io/badge/Android-6.0%2B-informational?logo=android)](https://apilevels.com/#:~:text=Jetpack%20Compose%20requires%20a%20minSdk%20of%2023%20or%20higher)
[![GitHub](https://img.shields.io/github/license/mytv-android/myDV)](https://github.com/mytv-android/myDV)

</div>
<div  align="center">
    <img src="./img/play_store_feature_graphic.png" width="100%"/>
    <p>卡是正常的，保持深呼吸</p>
</div>

## 使用教程

### 下载

通过右侧release进行下载

### 安装

可以使用我们的姊妹项目[mytv-android](https://github.com/mytv-android/mytv-android)来安装（但你需要先安装它）

### Cookie填写

- 如需登录，需要在设置中填写Cookie，这需要在网页端中获取。你需要登录网页端（https://www.douyin.com ），并使用Chrome插件（例如https://chromewebstore.google.com/detail/cookie-editor/hlkenndednhfkekhgcdicdfddnkalmdm ），并从插件复制登录后的所有Cookie（就举例的插件而言，点击插件，点击右下角的Export，选择以“Header String”格式导出，图文并茂教程可以参考这里的第一步和第二步https://support-orig.hubstudio.cn/7794/e1fd ）.
- 不要在控制台使用``document.cookie``来获取，因为一些敏感Cookie不能被此方式获取到.
- 如果以上信息未能帮助到你，你还可以参考https://github.com/mytv-android/myDV/issues/25

### 操作方式

~~按到哪儿算哪儿吧~~

- 上下左右键移动焦点
- 返回键打开导航栏
- 在视频页使用左键/点击屏幕左部分显示视频推荐面板，右键/点击屏幕右部分显示进度条，继续点击左键或右键快进/快退视频
- 使用确认键/点击中间部分屏幕以暂停/播放视频
- 支持手势向左向右拖拽以快退/快进
- 连续按两次退出以退出应用

### 卸载

请在安装后24小时内卸载，因为这个项目不是用来在TV上刷视频的，只是告诉你AI写的代码有多厉害。


## 信息获取

可以来这个群里玩，虽然它是隔壁项目的群（懒得建新的了）

<div align="center">
    <img src="./img/QRCode.png" width="48%"/>
</div>

## 星标历史



[![Stargazers over time](https://starchart.cc/mytv-android/myDV.svg?variant=adaptive)](https://starchart.cc/mytv-android/myDV)


## 著作权、许可证声明和致谢

- AI写的代码，也不知道它都抄了谁的代码，在此一并感谢了！
