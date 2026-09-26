# 本地抖音接口联调

本地只运行真实接口调试，**不运行编译、单元测试、回归或模拟器**。所有测试通过 GitHub Actions 执行。Python 使用 `-B`；启动 agent-webview 时设置 `PYTHONDONTWRITEBYTECODE=1`。

## 官方网页扫码

已安装 `agent-webview` 时，在仓库根目录启动独立控制器：

```sh
PYTHONDONTWRITEBYTECODE=1 agent-webview --port 8766 \
  --data-dir "$PWD/.local-debug/data" \
  --runtime-dir "$PWD/.local-debug/workers" \
  --runtime-file "$PWD/.local-debug/controller.json"
```

另一个终端执行以下真实联调命令：

```sh
python3 -B tools/douyin_debug.py start
python3 -B tools/douyin_debug.py login
python3 -B tools/douyin_debug.py status
# 在官方页面中用抖音 App 扫码，并在手机确认后：
python3 -B tools/douyin_debug.py capture
python3 -B tools/douyin_debug.py probe self
python3 -B tools/douyin_debug.py probe video --id 视频数字ID
python3 -B tools/douyin_debug.py probe friends
python3 -B tools/douyin_debug.py events
```

`capture` 使用 agent-webview 原生 Cookie API 读取 HttpOnly 会话，按域、路径和有效期过滤，通过**独立 HTTP 账号请求**确认后，才原子替换 `.local-debug/credentials.json`。支持 macOS WKWebView 的 NSDate 过期时间格式。文件权限为 0600、目录 0700；失败保留旧文件。控制器 Token、Cookie、账号 ID、原始响应均不应上传 GitHub。

终端只输出接口摘要，不打印 Cookie 值、好友姓名、账号 ID、消息正文或带签名的 URL。`events` 只显示页面探针所见的已知接口路径及 HTTP 状态，不能作为业务成功或消息送达证据。用返回的 `next_sequence` 继续 `events --after 序号`，避免重复读取旧事件。探针不保证捕获所有网络请求。

`start --proxy http://127.0.0.1:端口` 可使用已有 HTTP 代理。结束窗口使用 `close`；若窗口已手工关闭，控制器会返回 410，删除 `.local-debug/session.json` 后可重新 `start`。凭证仍保留在本地私有文件，通用 Cookie 快照无法完整恢复 HttpOnly 登录态。

## 旧独立 Passport 对照

```sh
python3 -B tools/douyin_debug.py qr
```

该命令复现 Android `QrSession` 的取码、轮询和受限跳转，在 agent-webview 显示官方返回的二维码。最多等待三分钟，未成功验证不会替换凭证。

2026-09-26 本地实测：取码返回 `success`，但独立轮询返回业务码 `4031`。**不能把网页扫码成功说成电视端独立扫码已修复**；不要把 4031 的具体原因猜成设备、账号或 IP 限制。网页需要的验证码脚本曾被本机 DNS 解析为 `0.0.0.0`，临时 hosts 放行后网页扫码成功。临时 hosts 条目在项目结束时清理。

同日网页扫码导出的凭证通过独立账号验证（包括 Android 当前 User-Agent）以及好友列表读取。评论接口仍有空白/非 JSON 响应，扫码不会自动提供所有接口的动态签名。点赞、关注需记录原始状态并在联调结束后恢复；发送视频需要明确接收人及授权，不由此工具自动发送。

用户授权后的写入联调：简化 HTTP 点赞请求后账号接口返回 8，已停止该路径并重新扫码核对原始状态。官方网页正常点赞/取消点赞均返回业务码 0，之后独立读取确认未点赞。网页发送的成功请求带动态 `a_bogus`、`msToken`、浏览器及设备字段；不把抓到的一次签名当作永久 Token。官方网页关注请求也返回业务码 8（用户未登录），账号读取仍有效，原因尚未确定；最终独立读取确认仍未关注，未继续重试。好友分享未发送，不能用好友列表成功来替代送达验证。

## GitHub 验证

`.github/workflows/douyin-debug.yml` 在 Python 3.10/3.12 上运行合成 Cookie 和模拟网络用例，覆盖作用域、过期时间、HttpOnly、凭证原子替换、失败保留旧账号和摘要脱敏。Android UI 编译与 API 21 回归由 `android5-player.yml` 执行。CI 不使用个人 Cookie。

2026-09-26 后续复现：同机同网络简化取码成功、首轮轮询 4031；agent-webview 官方页连续轮询 `message=success/error_code=0/status=new`。官方请求包含 `sign`、`qs`、`a_bogus` 与设备上下文。`qr` 子命令仅用于旧链路诊断，v0.1.3 生产登录改用官方网页，不复制捕获的动态签名。Android WebView 兼容性与手机确认仍需分别验证。
