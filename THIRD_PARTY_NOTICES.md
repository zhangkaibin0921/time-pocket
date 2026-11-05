# 第三方说明

应用运行时仅使用 Android 平台 API，没有打包第三方运行时库。

## Gradle Wrapper

仓库包含 Gradle 8.11.1 Wrapper：`gradlew`、`gradlew.bat` 和 `gradle/wrapper/gradle-wrapper.jar`。

- 来源：https://github.com/gradle/gradle/tree/v8.11.1
- 脚本中的原始作者版权与许可声明予以保留。
- 许可：Apache License 2.0，https://www.apache.org/licenses/LICENSE-2.0

该许可仅适用于对应组件，不意味着时光口袋自身代码采用同一许可证。项目目前尚未指定自身的开源许可证。

## 界面设计参考

界面迭代参考了 [mobile-android-design](https://github.com/wshobson/agents/tree/main/plugins/ui-design/skills/mobile-android-design) 中的触控区域、信息层级与自适应布局建议。应用使用原生 Java Views 实现，没有把该工具作为运行时依赖。
