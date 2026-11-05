# 第三方组件与来源清单

该清单对应 Gradle 中声明的直接组件，供开发版审阅。完整分发文件应同时保留依赖包内的 LICENSE/NOTICE，并在正式发布时核对解析后的全部传递依赖。此文件不替代相应许可证文本，也不自行决定 Pointa 仓库的最终项目许可证。

| 组件 | 当前声明 | 许可/来源 |
| --- | --- | --- |
| AndroidX / Jetpack Compose / Activity / Lifecycle / CameraX | 见 `app/build.gradle.kts` | AndroidX，Apache-2.0；各构件 AAR/POM 的版权与许可为准，见 [AndroidX 源码](https://github.com/androidx/androidx) |
| Kotlin / kotlinx.serialization JSON | Kotlin 构建版本；JSON 1.9.0 | Apache-2.0，见 [JSON v1.9.0 LICENSE](https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/LICENSE.txt) |
| OkHttp | 4.12.0 | Apache-2.0，见 [4.12.0 LICENSE](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/LICENSE.txt) |
| Bouncy Castle Java provider | 1.86 | MIT-style Bouncy Castle license，见 [1.86 LICENSE](https://raw.githubusercontent.com/bcgit/bc-java/r1rv86/LICENSE.html) |
| ZXing core | 3.5.4 | Apache-2.0，见 [3.5.4 LICENSE](https://raw.githubusercontent.com/zxing/zxing/zxing-3.5.4/LICENSE) |
| JUnit、AndroidX Test、MockWebServer、okhttp-tls | 见两个模块的 test 依赖 | 仅自动化测试使用；不作为应用运行时功能 |

Pointa 的产品思路受 [lan-laser-pen](https://github.com/fuxing0910-hue/lan-laser-pen) 启发。Android 实现基于用户提供的 PRD、V5 设计和现有 Windows 协议编写；本轮没有复制 RemotePointer、gInk 或 ppInk 的源码。若后续直接复用参考项目代码，必须另行保留原版权、许可和修改说明。

上述直接组件的许可原文已放入 `app/src/main/assets/licenses`，可在设置中的“开源许可”离线阅读。2026-11-05 查阅 Bouncy Castle 官方发布页后改用 1.86，并重新执行 SRP 固定向量、JVM→Windows 与 Android→Windows 互通验证。正式发行前仍须核对全部传递依赖及其 NOTICE；固定版本和测试通过不等于独立安全认证。
