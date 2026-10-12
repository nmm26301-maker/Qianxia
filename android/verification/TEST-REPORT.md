# 迁匣 1.0.0 验证记录

验证日期：2026-10-08 UTC。

## 构建

- 原生 Android Java 工程；Android SDK / Build Tools 35。
- 最低 Android 9（API 28），目标 API 35。
- 已生成 APK，`apksigner verify --verbose` 通过（APK Signature Scheme v3）。
- APK SHA-256：`7a6245a83e9a153a3828e31f12a7a49ac0c48e08d3d827e27239b961bfdb17ae`。
- 使用独立开发签名；不是 Google Play 发布版本。

## 已通过的核心验证

`bash test-core.sh`：29 / 29 通过，完整输出见 `core-tests.txt`。

- 空文件、单字节、普通文件、跨加密分块文件的加解密往返。
- 每次生成独立随机盐和 nonce；密码错误不留下明文。
- 密文篡改、头部篡改、截断、尾部追加均被拒绝。
- 中断加密清理不完整输出。
- ZIP 路径穿越被阻止并清理；正常 ZIP 可展开。
- 明文 HTTP 必须明确启用。
- 本机 WebDAV 测试服务：PUT/GET/DELETE 连接校验、上传读回校验及 MOVE 发布、Unicode 文件名列表、同源限制、下载 SHA-256 一致、拒绝覆盖与路径穿越、拒绝错误账号。

这些是 JVM 核心测试，WebDAV 使用测试服务，并未连接用户真实网盘。

## Android 运行验证

Android 11 软件模拟器首次启动成功；安装与界面验证脚本运行超过环境设定的时间限制，未取得完整的成功结果。不能将原生启动、备份、系统确认安装和文件选择器流程算作已通过。已准备 `tests/android-flow.py` 和测试安装包工程供继续验证。

## 尚未覆盖

- 实体手机及不同 Android 厂商的后台限制。
- 真实 WebDAV 网盘、慢速或中断网络、大体积备份、低存储空间。
- Android 15 的安装确认与前台服务超时行为。
- 不同 CPU 架构和 split APK 的跨设备兼容。
- 系统文件选择器的导入、导出、授权撤销。
- 自动备份的真实 24 小时调度。

此版本支持安装包及用户添加的文件，未实现 Root 私有数据备份恢复。
