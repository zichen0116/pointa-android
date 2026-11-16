# 构建与开发

用户介绍见 [README](../README.md)。本页保留构建、自测与开发分发方法；产物版本以项目配置为准。

## 构建

安装 JDK 17、Android SDK 36 / Build Tools 35.0.0。通过 `ANDROID_HOME` 或本地 `local.properties` 指定 SDK；后者不进入 Git。仓库自带 Gradle 8.14 wrapper。

```powershell
./scripts/build.ps1
```

跨平台等效命令：

```sh
./gradlew :protocol:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`；PowerShell 脚本按版本号另复制到 `artifacts/Pointa-0.1.4-debug.apk`。发布签名密钥不应放入仓库，不用 debug 签名作为正式发行身份。

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

