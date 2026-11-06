# 映点 Pointa · Android

原生 Kotlin / Compose 手机演示遥控端，配合 [Pointa Windows](https://github.com/zichen0116/pointa-windows) 使用。按照产品 PRD 和 V5 原型实现；激光与电脑鼠标独立，无云账号、无公网中转、无演示画面采集。

当前是 **0.1.1 开发验证版**，Android 8.0+（API 26），目标 API 36。提供可安装的 debug APK；正式签名与应用商店发布尚未配置。

## 使用

1. 安装 `artifacts/Pointa-0.1.1-debug.apk`，在 Windows 打开新版 Pointa。
2. 手机、电脑连到互通的局域网。Windows 0.1.1 自动选择 Wi-Fi／有线网卡，不再因“公用”分类禁用连接；配对窗直接显示可复制的 IP。
3. 扫描 Windows QR，或选择发现的电脑/手输 IP 并输入六位设备码；在 Windows 明确允许配对。
4. 控制主页固定一屏，连接和设置使用弹层。单手模式按住移动、松开隐藏；全屏按显示器比例映射；纯翻页保留两个大按钮。
5. 浏览器/PDF 翻页前，从 Windows 托盘菜单明确允许当前演示窗口。失败会在手机显示原因，不虚构页码。

支持左右手布局及四边编辑、主题/明暗/双语、激光颜色/大小/拖尾/平滑、灵敏度、音量键映射、布局锁定、触觉、常亮、最近电脑与前台重连。相机权限只在扫码时请求；拒绝后仍能使用设备码。离线遗忘设备只清除手机记录，仍应在 Windows 撤销原授权。

**空中指针：** 选择“空中指针”，按住激活区后轻转手机；松手立即隐藏。“归零校准”将起点恢复到屏幕中心。支持平拿、竖拿和显示方向换轴；灵敏度在设置中调整。优先使用游戏旋转向量，缺失时使用旋转向量；没有相应传感器的手机明确提示使用触控。离开控制页、进入后台、断连或传感器失去更新都会停止输出。不同真机的漂移与手感仍需实测。

扫码使用 CameraX + ZXing 离线解码。QR 绑定实际 TLS 证书 SHA-256；设备码使用证书绑定的 SRP-6a，批准后才保存独立凭据。长期凭据使用 Android Keystore AES-GCM 加密，并排除系统备份。首次配对、IP 发现与错误处理的安全边界见 [隐私与安全](docs/privacy-security.md)。

## 构建

安装 JDK 17、Android SDK 36 / Build Tools 35.0.0。通过 `ANDROID_HOME` 或本地 `local.properties` 指定 SDK；后者不进入 Git。仓库自带 Gradle 8.14 wrapper。

```powershell
./scripts/build.ps1
```

跨平台等效命令：

```sh
./gradlew :protocol:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`；PowerShell 脚本按版本号另复制到 `artifacts/Pointa-0.1.1-debug.apk`。发布签名密钥不应放入仓库，不用 debug 签名作为正式发行身份。

## 测试

`protocol` 是无 Android 依赖的 Kotlin/JVM 模块，测试二维码严格解析、TLS pin、SRP 固定向量、状态机、时钟、隐藏、翻页限流、凭据与协议负面路径。几何/音量映射测试在 `app/src/test`。设备测试验证 Keystore、损坏记录保留和 Compose 实际交互。

同级 Windows 仓库先构建测试专用主机（不是真实桌面服务，不注入键盘）：

```powershell
dotnet build ../pointa-windows/tests/Pointa.InteropHost -c Release
$env:POINTA_INTEROP_HOST = (Resolve-Path '../pointa-windows/tests/Pointa.InteropHost/bin/Release/net10.0/Pointa.InteropHost.dll').Path
$env:POINTA_DOTNET = (Get-Command dotnet).Source
./gradlew :protocol:test --rerun-tasks
```

未指定 `POINTA_INTEROP_HOST` 时，JVM Windows 互通用例明确跳过，不冒充通过。Android 模拟器到真实 Windows TLS/SRP/存储服务的测试：

```powershell
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./scripts/device-smoke.ps1 -Serial emulator-5554 -InteropHost $env:POINTA_INTEROP_HOST
./scripts/device-smoke.ps1 -Serial emulator-5554 -InteropHost $env:POINTA_INTEROP_HOST -AppFlow
```

参数必须指向要测试的模拟器；该脚本安装应用和测试 APK，仅启动绑定回环地址、自动批准、临时凭据的测试主机。普通设备测试可运行 `./gradlew connectedDebugAndroidTest`，跨 Windows 用例没有测试参数时明确跳过。

## 文档与边界

- [架构](docs/architecture.md)、[范围与验收](docs/scope.md)、[本次验证](docs/validation.md)、[开源组件](docs/open-source-notices.md)。应用设置内可离线查看许可原文。
- [Windows v1 协议](../pointa-windows/docs/protocol.md) 是双端契约；新 Windows 构建增加可选 `display` 信息，旧构建仍可连接，但手机没有真实显示器比例时按触控区域映射。
- 空中指针已实现；真实扫码、传感器机型差异、网络切换、投影、P95 延迟/耗电、TalkBack 和独立安全审查仍需发布前验证。
- 项目最终开源许可证由仓库所有者决定；第三方许可已保留，不代表给本项目擅自指定许可。
