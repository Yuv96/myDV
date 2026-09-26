# 安装签名冲突与发布恢复

2026-09-26 排查确认：历史播放器 Actions 在缺少 `ANDROID5_KEYSTORE_BASE64` 时，自动生成 `android5/build/test-only.p12`，并用它签署可下载的“正式”构建产物。APK 签名有效只代表内容未损坏，不代表与已安装应用的证书一致。这是本次 Actions 包无法覆盖旧版的原因。

已发布的 a5.4 和 v0.1.1 的 APK 已在 GitHub runner 上下载、核对文件摘要并读取实际证书；两者的签名 SHA-256 均为：

```text
a9a53928e2d288aba0ed134d1c92bf1e8ba29886f22b23578f56cd34ef1d32e6
```

## 已排查的密钥存储位置

- 当前仓库的 Actions Secrets、Environment、Variables 及 Actions 缓存均无签名配置。
- 历史工作流只上传 `dist/` 和 `evidence/`，没有上传或缓存 `android5/build/test-only.p12`。
- Git 历史没有发现曾提交的签名私钥文件；已有发布附件没有密钥。
- 本地工作区及可读取的常见配置、数据和临时目录尚未找到原密钥。

这些证据不能排除旧的网页版 Codex、Codespaces 或其他发布任务环境中仍留有原签名文件。证书、APK、摘要和密码本身都不能替代私钥；不能通过重新生成同名密钥来恢复覆盖安装能力。

## 恢复步骤

1. 在当时签署发布包的环境中找到包含私钥的原 `.p12` / `.jks` / keystore，保留原文件备份。
2. 将其 Base64 内容配置为本仓库 Actions Secret `ANDROID5_KEYSTORE_BASE64`，密码配置为 `ANDROID5_KEYSTORE_PASSWORD`；现有构建使用别名 `android5`。不要提交密钥、在日志输出它，或在聊天发送密码。
3. GitHub runner 重新构建。新签名必须与上述指纹及两个历史发布包一致，之后执行 API 21 的 a5.4 → v0.1.1 → v0.1.2 连续覆盖安装和私有数据保留检查。
4. 主分支的手动工作流选择 `publish=true`，通过全部检查后发布 v0.1.2。没有原私钥时，发布保持阻塞，不用临时签名代替。

所有证书检查、APK 构建和安装回归都只在 GitHub 执行。本地仅编辑、提交和进行必要的接口调试。
