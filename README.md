# Android 5.0 TV 安装包

本 Fork 提供原项目发布的 **myDV Lite 1.1.9**，最低系统 Android 5.0 / API 21。APK 保留原始签名和全部字节，支持 TV 启动入口且不要求触摸屏。

**[下载 APK](https://github.com/Yuv96/myDV/releases/download/android5-lite-v1.1.9/myDV-Lite-1.1.9-android5.apk)** · [发布说明和校验值](https://github.com/Yuv96/myDV/releases/tag/android5-lite-v1.1.9) · [验证记录](https://github.com/Yuv96/myDV/actions/workflows/android5-lite.yml)

自动流程会先核对上游 SHA-256、签名和最低系统版本，再在 Android 5.0 API 21 模拟器安装、启动并发送方向键；检查通过才发布 APK 和截图。无需自行编译，下载后复制到电视安装即可。

这是原项目另一个开发者提供的 Lite 版，与最新完整版的功能不同。本次没有重新编译完整版：公开仓库不含 Android 工程，原构建依赖不可访问的 `myDV-Internal`。模拟器检查不覆盖账号登录、实时视频/直播服务或具体电视硬件，相关功能须实际联网确认。

来源：[上游 V1.1.9 发布](https://github.com/mytv-android/myDV/releases/tag/V1.1.9)，文件 `myDV.Lite_1.1.9.apk`；SHA-256：`c01f3b93a7c0b97e481f9f9706a1ff9bc91c3431000be13695b1ca9ffcad69fa`。

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
