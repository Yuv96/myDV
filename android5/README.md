# 开发与构建

应用：抖音抬头版 0.1.2。最低 API 21，目标 API 30，包名 `com.dycomment.tv.android5`，版本号 1007。保留既有签名才能覆盖此前的 Android5 社区版。

## 环境

本地仅允许源码编辑、接口联调和 Git 提交，禁止本地编译和测试。以下命令仅在 GitHub Actions runner 执行，可用 `gh run list`、`gh run view` 查看结果。

GitHub runner 需要 JDK 17、Python 3.10+、Android SDK Platform 35 与 Build Tools 35.0.0。回归构建另外需要 FFmpeg；模拟器需要 API 21 x86 系统镜像。

```sh
# 以下仅供 GitHub runner；正式构建还需注入原签名 Secrets
export ANDROID_HOME=/path/to/android-sdk
python3 android5/build.py
python3 android5/verify_release.py
```

正式包输出到 `dist/Douyin-TV-0.1.2.apk`，不包含测试 Activity 或测试视频。`BUILD_WORK=/tmp/douyin-build` 可指定临时构建目录；依赖下载后校验固定 SHA-256。

```sh
SELF_TEST=1 python3 android5/build.py
bash android5/smoke.sh
```

测试包输出到 `dist-test/`。测试只用生成的视频和本地数据，不需要个人 Cookie，不发送真实点赞、关注或私信。`SELF_TEST=1` 会开放测试 Activity，不能将该 APK 当作发行版。

## 签名

正式包必须使用原维护者密钥，GitHub Actions 通过 `ANDROID5_KEYSTORE_BASE64` 和 `ANDROID5_KEYSTORE_PASSWORD` 注入。密钥别名为 `android5`。缺少密钥、密码、文件或证书不一致时，正式构建直接失败，不生成可供发布的临时签名包。只有 `SELF_TEST=1` 且没有配置正式密钥时才允许使用一次性回归签名，这种测试包不会作为发布附件上传。

CI 下载 SHA-256 固定的 a5.4 和 v0.1.1 发布包，读取真实证书并比对固定指纹；新正式包必须与两者一致。API 21 模拟器另执行 a5.4 → v0.1.1 → v0.1.2 的 `adb install -r` 升级链，并确认应用私有数据标记保留。`SIGNING_CERTIFICATE.txt` 随正式包提供公开证书指纹。

仅主分支可以手动触发工作流的 `publish=true`。只有编译、完整回归、签名比对及真实升级安装全部通过，发布任务才会再次验证下载的产物并创建 v0.1.2。

私钥、Cookie、Token、用户接口响应不得进入仓库、构建日志或发行附件。Actions 只需要公开源码和测试数据。

## 代码风格

Java 使用 google-java-format 1.22.0 的 `--aosp` 格式（四空格缩进）。共享主题、账号状态、图片加载和旧 ABI 桥接分别放在独立类中；避免在界面回调中直接做网络或磁盘操作。

## 模块边界

`PlayerView` 管理 LibVLC 和画面生命周期；`PlaybackCoordinator` 管理选择序号、异步结果过期及超时；`NextVideoCache` 只缓存容量范围内的完整下一条视频。

`InteractionController` 负责遥控器路由和菜单分组，`ModernMenuHelper`、`UiTheme`、`CommentsPanel` 提供统一组件。`CredentialStore` 管理兼容存储，`QrSession` 只访问抖音 HTTPS 域名，`CredentialHealth` 从实际失败频率产生可恢复提示。`SocialApi` 与 `LegacyRequests` 管理新旧接口边界。

`cleanup.py` 对固定上游 APK 替换指定方法、移除共享账号和遗留实现，并删除不再可达的嵌套类。签名不匹配即停止构建。保留的空 ABI 桥接方法用于原二进制调用，不能未经调用关系检查直接删除。

## 验证范围

CI 首先编译测试包并在 API 21 运行本地 H.264、缓存、连续切换、Surface 返回和遥控器测试，再编译不带测试页面的正式包，检查包名、版本、最低 API、测试代码排除及签名。测试与发行使用同一提交、同一业务源文件；两者因测试代码是否包含，DEX 不会逐字节相同。

扫码申请在当前开发网络返回 4031，因此二维码轮询后的真实登录、扫码后的评论动态签名尚未验证。网络失败提示和域名限制有本地用例，但不等于真实账号登录成功。

完整改造历史保留在 `docs/history/`，其中旧安装方式与旧版本限制不代表当前入口。
