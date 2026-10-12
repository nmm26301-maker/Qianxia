# 迁匣 Qianxia · 完整备份

备份日期：2026-10-12。备份版本：**1.0.0**，包名 `com.qianxia.backup`，最低 Android 9。

## 已备份内容

- 原始 APK、完整源码 ZIP（包含构建依赖）、使用说明及原验证报告。
- 可浏览的 Java / XML 源码、构建脚本、核心测试和开源许可证，位于 `android/`。
- 设计素材包和界面预览图。在线 Figma 文件尚未完成；素材包不代表在线设计已补齐。
- **原签名密钥及原密码说明**，位于 `signing/Qianxia-original-signing.enc.json`，以 AES-256-GCM 加密保存。
- 精确文件大小、SHA-256 及签名证书指纹，位于 `BACKUP-MANIFEST.json`。

此前未保存的 1.1 临时工作文件已被环境清理，本次可恢复备份不包含 1.1 代码或 APK。原验证报告保持原样；不将此前 1.1 的运行检查计入本备份。

## 还原安装包、完整源码和设计素材

下载仓库 ZIP 并解压，在仓库目录运行：

```bash
python3 restore_backup.py --output restored
```

脚本会校验每个分块与每个原文件，输出原始 APK、源码 ZIP、设计 ZIP、预览图，并展开完整源码至 `restored/source/qianxia/`。备份二进制文件采用 Base64 分块保存，分块不能直接安装，必须先运行还原脚本。

## 还原原签名与密码

单独保存交付的 `Qianxia-original-signing-recovery.json`。此文件不在 GitHub 中。

```bash
python3 -m pip install cryptography
python3 restore_backup.py --recovery-key /path/to/Qianxia-original-signing-recovery.json --output restored
```

原密钥输出至 `restored/original-signing/signing/development.jks`。原签名密码、密钥密码与别名说明在 `restored/original-signing/README.txt`。

如需构建可覆盖安装的更新，将该原密钥复制到 `restored/source/qianxia/signing/development.jks`，保持原别名和密码，再使用源码中的 `build.sh`。不要用新生成的密钥替换原密钥。

签名证书 SHA-256：`ade80b56e5dcc15839e6841e5bf3fefaad60fe9c3399d0ee4e1f03d6598b05ef`。

## 本次备份核对

已核对原 JKS 中的证书与原 APK 的签名证书一致；原 APK 的 SHA-256 与先前记录一致。发布前还原测试核对全部原文件、原密钥和签名说明的字节一致性。

应用支持安装包与用户主动添加文件的备份恢复；尚不支持其他应用的私有聊天数据。具体功能与旧版本验证范围见源码内 README 和 `android/verification/TEST-REPORT.md`。
