# 扫码、分享与消息协议核对

核对日期：2026-09-26。公开代码是协议参考，不是本项目已通过真实账号验证的证据。

## 来源

主要参考 [zhinjs/douyin-im](https://github.com/zhinjs/douyin-im/tree/e4ff73c43d3599a40091bd18cf794af36af466fb)，固定提交 `e4ff73c43d3599a40091bd18cf794af36af466fb`，MIT 许可。适配其视频卡片字段、安装 GUID 回退标识和分享结果判断；许可保留在 `android5/DOUYIN-IM-LICENSE.txt` 并随 APK 放入 assets。

| 本项目功能 | 参考文件（上述固定提交） | 核对结果 |
| --- | --- | --- |
| 好友视频分享 | `src/services/im/cards.ts` | 短视频为 message_type 8、aweType 800；作者名使用 content_name，同时提供 content_thumb、cover_url 与视频卡片元数据 |
| 建立单聊与发送 | `src/services/im/actions.ts`、`send.ts`、`proto/im.proto` | cmd 609 建立单聊，cmd 100 发消息；必须在用户选择好友后执行，不自动重发 |
| 安装标识 | `src/store/device-id.ts`、`src/desktop/api-connection.ts` | 采用桌面注册不可用时的 GUID 回退：安装内持久化 GUID，以 Java String.hashCode 的无符号 32 位值生成 DID，iid 为 0；不再用账号 UID 代替设备 ID，但并非已注册设备 |
| 发送结果 | `src/services/im/content.ts` | 同时判断状态、消息 ID 与审核结果；10502 表示待审核，8610 表示内容检查拒绝；check_message.status_code 可以覆盖 check_code |
| 扫码登录 | `src/desktop/api-connection.ts`、`passport-query.ts`、`src/passport/signQs.ts` | 完整实现使用桌面 aid 339757、设备注册、sign/qs、aid-sign、动态 a_bogus 和会话校验，不能将部分桌面参数拼到网页 aid 6383 上 |

另核对 [Laihiujin/Prism 的 HTTP 登录实现](https://github.com/Laihiujin/Prism/blob/9227ea1cec12ffc697e2e9b557a12d1ac416a1b4/prism_backend/app_new/platforms/douyin_http.py)：其创作者端 aid 2906 同样依赖完整签名、CSRF 和设备验证；Passport 的 `x-ms-token` 响应头会轮换 Token。本项目仅补上响应头 Token 的会话保存/后续请求传递，以及已有 Cookie 对应的 Passport CSRF 请求头，不复制其创作者端身份和签名参数。

[yidai2024/douyin-api-capture 的网页请求记录](https://github.com/yidai2024/douyin-api-capture/blob/main/README.md)中，aid 6383 的 check_qrconnect 同时在查询串和表单传 token；本项目补齐查询串。此记录是辅助核对，不能证明接口现在允许签发二维码。

## 已做与未完成

资料卡改为内容决定宽度，短内容收窄、长内容在上限内换行；保留头像外置、昵称与数据同行以及底部 48dp 安全区。头像和卡片整组靠左，宽度最多为扣除左右等距边距后可用宽度的一半，右边缘不会越过屏幕中线；另保留 720dp 上限。这些是上限，不是固定宽度，窄窗口也遵循半屏规则。

卡片采用稳定的 88dp 高度（系统字体放大时统一等比增加），标题最多两行并省略溢出文本。头像仍使用原有 CircleImageView，宽高均等于卡片高度；短标题、长标题和窄屏之间不改变卡片高度或头像尺寸。

每次切入视频、首次出画面后资料卡只显示 3 秒；循环播放和 Surface 恢复不重置。右侧互动菜单拥有临时显示权，关闭立即隐藏，切走再切回重置一次。时间与通知移到左上角。

头像底部居中显示关注标签，与菜单共享已确认状态；菜单补齐已/未关注、已/未点赞、已/未收藏。未登录、读取中、状态未知和关注待确认分别显示，避免把读取失败误报为未关注。切片或换账号后丢弃过期响应。

好友分享补齐视频卡片字段，并避免将内容拒绝、未知审核码或不匹配的发送回执显示为成功。角标按产品决定继续使用通知数，不替换成私信数，也不增加私信读取/已读操作。

扫码申请仍可能返回 4031。本次没有完整移植桌面设备注册、动态签名、Ticket Guard 和人工二次验证流程，**不能宣称扫码登录已修复**。旧凭证仍只在新账号通过验证后替换。分享通道已将误用的账号 UID 替换为公开实现的持久化安装 GUID 回退 DID；该回退不等同于服务端注册成功，仍不能宣称分享真实送达已验证。

后续接入应按同一端的完整协议建立设备与账号会话，再在获准测试的真实账号上验证取码、手机确认、凭证保存和分享送达；不要混用不同 aid、固定别人的设备标识或不断猜测路径。公开仓库里的 Node/Electron 环境与 Android 5.0 不同，需要单独适配。

本地合成用例覆盖布局伸缩、视频卡片字段、发送成功/待审核/拒绝/未知状态/回执错配，不访问真实好友。真实扫码和分享送达仍需账号实测。

## 0.1.3 登录路径调整（2026-09-26）

同机同网络重新复现：简化 HTTP `get_qrcode` 成功，首次 `check_qrconnect` 返回 4031；agent-webview 官方网页连续返回 `message=success`、`error_code=0`、`status=new`。官方轮询查询串带 `sign`、`qs`、`a_bogus` 和一组 SDK / 设备上下文字段。证据只证明两条请求链不同，尚不足以把拒绝唯一归因于某个字段，也没有证明电视设备被封禁。

生产登录入口改为可见的官方网页，由官网完成二维码、动态签名、轮询和可能的验证。原始网络响应和凭证只保留在本地忽略目录，不提交 CI。应用读取新网页候选 Cookie 后独立验证账号再保存；不将已有凭证注入新浏览器会话，也不因失败覆盖旧账号。Android API21 的平台 API 可用不等于旧 WebView 内核支持今日官网，真实电视兼容性和手机确认仍是单独的验证项。
