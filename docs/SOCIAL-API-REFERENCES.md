# 扫码、分享与消息协议核对

核对日期：2026-09-26。公开代码是协议参考，不是本项目已通过真实账号验证的证据。

## 来源

主要参考 [zhinjs/douyin-im](https://github.com/zhinjs/douyin-im/tree/e4ff73c43d3599a40091bd18cf794af36af466fb)，固定提交 `e4ff73c43d3599a40091bd18cf794af36af466fb`，MIT 许可。适配其视频卡片字段和分享结果判断；许可保留在 `android5/DOUYIN-IM-LICENSE.txt` 并随 APK 放入 assets。

| 本项目功能 | 参考文件（上述固定提交） | 核对结果 |
| --- | --- | --- |
| 好友视频分享 | `src/services/im/cards.ts` | 短视频为 message_type 8、aweType 800；作者名使用 content_name，同时提供 content_thumb、cover_url 与视频卡片元数据 |
| 建立单聊与发送 | `src/services/im/actions.ts`、`send.ts`、`proto/im.proto` | cmd 609 建立单聊，cmd 100 发消息；必须在用户选择好友后执行，不自动重发 |
| 发送结果 | `src/services/im/content.ts` | 同时判断状态、消息 ID 与审核结果；10502 表示待审核，8610 表示内容检查拒绝；check_message.status_code 可以覆盖 check_code |
| 扫码登录 | `src/desktop/api-connection.ts`、`passport-query.ts`、`src/passport/signQs.ts` | 完整实现使用桌面 aid 339757、设备注册、sign/qs、aid-sign、动态 a_bogus 和会话校验，不能将部分桌面参数拼到网页 aid 6383 上 |

另核对 [Laihiujin/Prism 的 HTTP 登录实现](https://github.com/Laihiujin/Prism/blob/9227ea1cec12ffc697e2e9b557a12d1ac416a1b4/prism_backend/app_new/platforms/douyin_http.py)：其创作者端 aid 2906 同样依赖完整签名、CSRF 和设备验证；Passport 的 `x-ms-token` 响应头会轮换 Token。本项目仅补上响应头 Token 的会话保存/后续请求传递，以及已有 Cookie 对应的 Passport CSRF 请求头，不复制其创作者端身份和签名参数。

[yidai2024/douyin-api-capture 的网页请求记录](https://github.com/yidai2024/douyin-api-capture/blob/main/README.md)中，aid 6383 的 check_qrconnect 同时在查询串和表单传 token；本项目补齐查询串。此记录是辅助核对，不能证明接口现在允许签发二维码。

## 已做与未完成

资料卡改为内容决定宽度，短内容收窄、长内容在上限内换行，窄屏受可用空间约束；保留头像外置、昵称与数据同行以及底部 48dp 安全区。电视宽窗口最多占可用宽度的 72%，并以 720dp 为上限；这只是上限，不是固定宽度。

卡片采用稳定的 88dp 高度（系统字体放大时统一等比增加），标题最多两行并省略溢出文本。头像仍使用原有 CircleImageView，宽高均等于卡片高度；短标题、长标题和窄屏之间不改变卡片高度或头像尺寸。

每次切入视频、首次出画面后资料卡只显示 3 秒；循环播放和 Surface 恢复不重置。右侧互动菜单拥有临时显示权，关闭立即隐藏，切走再切回重置一次。时间与通知移到左上角。

好友分享补齐视频卡片字段，并避免将内容拒绝、未知审核码或不匹配的发送回执显示为成功。角标按产品决定继续使用通知数，不替换成私信数，也不增加私信读取/已读操作。

扫码申请仍可能返回 4031。本次没有完整移植桌面设备注册、动态签名、Ticket Guard 和人工二次验证流程，**不能宣称扫码登录已修复**。旧凭证仍只在新账号通过验证后替换。现有分享通道还使用账号 uid 作为设备字段，而参考桌面实现使用注册设备 ID；在适配完整设备会话前，不能宣称分享真实送达已验证。

后续接入应按同一端的完整协议建立设备与账号会话，再在获准测试的真实账号上验证取码、手机确认、凭证保存和分享送达；不要混用不同 aid、固定别人的设备标识或不断猜测路径。公开仓库里的 Node/Electron 环境与 Android 5.0 不同，需要单独适配。

本地合成用例覆盖布局伸缩、视频卡片字段、发送成功/待审核/拒绝/未知状态/回执错配，不访问真实好友。真实扫码和分享送达仍需账号实测。
