# 本次提交验证

日期：2026-11-05。源码版本：v0.6.1 / versionCode 31。

## 已完成

- `python tools/test.py`：363 项断言通过，包含使用统计 26 项、一天时间线 179 项、日期统计桶 158 项。
- Windows / JDK 17 / SDK 35 / Build Tools 35.0.0：`python tools/build.py` 完整通过，APK v2 / v3 签名验证通过。
- 生成 APK 的最低 API 为 26，目标 API 为 35；Manifest 仅包含 README 列出的三个权限，不含 `INTERNET`。
- `python tools/package_source.py` 生成源码 ZIP 与 SHA-256 校验和。检查打包内容包含 Wrapper 与构建输入，不含 Git 元数据、SDK、签名密钥、数据库或编译产物。
- 所有 Python 辅助脚本语法检查通过，Markdown 本地文档与图片链接存在。
- 应用 Java 源码、资源与 Manifest 与整理前的 v0.6.1 工作区副本内容一致（仅清理一处行尾空格）；本次主要整理文档、Git 排除规则、测试入口与打包脚本。

## 验证范围

本次未重新运行 Android instrumentation、完整 Gradle / lint、UI 操作或厂商真机长期采集测试。仓库预览截图来自此前开发模拟器，不能视为这些检查在本次全部重跑。

GitHub Actions 的执行结果请以仓库 Actions 页面为准。本地测试通过不等于远端工作流已经完成。
