# 签名密钥

`clearrdp-release.p12` 是无遮远程 ClearRDP 的固定签名密钥。所有已发布的版本（分屏远程 1.0、1.1，无遮远程 1.2、1.3 及以后）都用这把密钥签名，本地编译和 GitHub Actions 编译也都用它，所以新版本总能直接覆盖安装旧版本。

| 项目 | 值 |
| --- | --- |
| 文件 | `keystore/clearrdp-release.p12`（PKCS12） |
| 密钥库密码 | `android` |
| 别名 | `androiddebugkey` |
| 密钥密码 | `android` |
| 证书 SHA-256 | `33:BF:16:75:2F:41:D8:3B:4A:CA:8F:BA:68:FA:A9:D5:BC:A1:29:F5:2D:C4:21:08:80:E2:C7:42:B0:2B:08:30` |
| 有效期 | 2026-10-25 至 2056-10-17 |

`app/build.gradle.kts` 直接引用这个文件。GitHub Actions 编译完成后会核对 APK 的证书指纹，和上表不一致就不发布。

**不要替换或重新生成这把密钥。** 换了密钥，手机会拒绝把新版本当作更新安装，用户只能先卸载（会丢失已保存的电脑）。

本地备份：`D:\RdpSplit\keystore\`。
