# 开发指南

## 环境与构建

JDK 17（设置 `JAVA_HOME`）、Python 3.10+、Android SDK 35、Build Tools 35.0.0。应用最低 API 26，标识为 `cn.timepocket.app`。Gradle Wrapper 8.11.1，AGP 8.9.1。

应用使用原生 Java Views、Canvas、SQLiteOpenHelper、UsageStatsManager 与 JobScheduler，没有第三方运行时依赖。

Windows 在项目根目录执行：

```powershell
python tools/bootstrap.py
python tools/build.py
# 构建成功后，生成源码 ZIP 与校验和。
python tools/package_source.py
```

`bootstrap.py` 从官方 Android 仓库下载 SDK、核对发布摘要，存放到 `.tools/android-sdk`，同时写入本机 `local.properties`。如果已有 SDK，可设置 `ANDROID_HOME` 后直接构建，其中需包含 Android 35 和 Build Tools 35.0.0。

`build.py` 运行三组 JVM 测试，执行 aapt2、javac、D8、zipalign、调试签名和验签。输出在 `dist/`，中间产物在 `app/build/manual/`。脚本依赖 Windows `.exe` 工具，其他平台使用 Gradle。

源码打包采用允许列表，排除 Git 元数据、工具、缓存、构建产物、签名及本机配置。源码 ZIP 和 APK 不提交到 Git。

## Android Studio 与 Gradle

打开项目根目录，选择 JDK 17 与 SDK 35。命令行也可使用：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lint
```

macOS / Linux 使用 `./gradlew`。初次同步需下载 Gradle、AGP 和构建依赖，缓存准备完成前不能保证离线构建。输出为 `app/build/outputs/apk/debug/app-debug.apk`。

本次源码提交验证 Windows 直接 SDK 构建路径，未把完整 Gradle / lint 构建标记为已验证。Gradle 不会自动运行根目录 `tests/` 中的测试，需另外执行下面命令。

## JVM 回归测试

```sh
python tools/test.py
```

只需 JDK 17 与 Python，不需要 SDK 或设备。编译使用临时目录并自动清理，失败返回非零退出码。

| 测试 | 关注点 |
| --- | --- |
| `UsageEngineTest` | 前后台事件、结束边界、去重与迟到事件 |
| `DayTimelineTest` | 时间线并集、短片段、合并、空白与睡眠 |
| `UsageBucketsTest` | 日期划分、区间与图表统计桶 |

GitHub Actions 在推送与 PR 时运行这些 JVM 测试，不代表完成 Android 构建或设备测试。

## Android 数据库测试

先连接一台测试设备或启动专用模拟器。如果连接了多台设备，在每条 adb 命令中加 `-s <serial>` 指定目标。

```powershell
python tools/build.py
python tools/build_test.py
& .tools/android-sdk/platform-tools/adb.exe install -r dist/time-pocket-0.6.8-debug.apk
& .tools/android-sdk/platform-tools/adb.exe install -r app/build/manual/instrumentation/tests.apk
& .tools/android-sdk/platform-tools/adb.exe shell am instrument -w cn.timepocket.app.tests/cn.timepocket.app.PocketInstrumentation
```

使用外部 SDK 时替换 adb 路径。测试使用独立的 `qa-accounting.db`，覆盖查询、迁移、足迹来源、图片与备份恢复。检查输出中的 `PASS` / `FAIL`，不能只看 adb 退出码。

## UI 与真机检查

`emulator_setup.py` / `start_emulator.py` 创建并启动 Windows PocketQA 模拟器，会下载镜像并占用磁盘。`ui_review_qa.py 0.6.8` / `capture_ui.py` 硬编码目标 `emulator-5554`，操作 UI、创建测试手记并输出截图，只用于专用测试模拟器。

`device_qa.py`、`live_qa.py`、`v02_qa.py`、`verify_live_result.py` 是早期辅助脚本，含旧版本 APK 路径或 UI 假设，保留供复现历史问题，不是当前通用验收入口。`setup_gradle.py` 用于重新获取 Wrapper，正常克隆后无需运行。

手动验收见 [设备清单](DEVICE_CHECKLIST.md)。统计准确性与后台调度需要厂商真机持续观察，模拟器结果不代表所有机型表现。

## 代码导航

源码包：`app/src/main/java/cn/timepocket/app/`。

| 文件 | 职责 |
| --- | --- |
| `MainActivity.java` | 四个 Tab、图表筛选、详情导航、权限与备份入口 |
| `UsageEngine.java` / `UsageBuckets.java` | 事件归并、日期统计桶 |
| `Collector.java` / `CollectJob.java` | 采集回读、应用清单核对、周期任务 |
| `PocketApp.java` / `PackageReceiver.java` | 初始化和安装卸载监听 |
| `PocketDb.java` | SQLite v4、查询、手记与图片事务 |
| `DayTimeline.java` / `HomeApps.java` | 时间线片段处理、桌面识别 |
| `ActivityDetail.java` / `ActivityEditor.java` | 手记详情、编辑、草稿与保存 |
| `EntryPhotos.java` / `PhotoImportActivity.java` / `PhotoPreview.java` | 图片导入、压缩与预览 |
| `DateRangeDialog.java` / `TimeRangeDialog.java` | 日期与起止时间选择 |
| `LibraryDialog.java` | 应用与手记查询 |
| `UiTokens.java` / `UiControls.java` / `PocketDialogs.java` / `ArtViews.java` | 配色、控件、弹窗与 Canvas 绘制 |
| `Backup.java` | JSON v4 导出、旧格式兼容与事务恢复 |

## 签名与版本

直接构建首次在 `.tools/debug.keystore` 生成本机调试密钥，保留同一密钥才能让后续 APK 覆盖已有安装。Gradle 使用自己的 debug 签名，两条路径不保证签名相同。签名不同无法直接覆盖安装，切换前先导出备份。

release 签名尚未配置，仓库与源码包均不提供签名密钥。

发布新版本需同步 `app/build.gradle`、`tools/build.py` 与应用内版本文案，更新 CHANGELOG；源码打包脚本从 `app/build.gradle` 读取版本。数据库变动需保留迁移路径，备份格式变动需明确兼容及替换行为。
