# 构建与验证记录

日期：2026-11-05。这里只记录实际执行结果；尚未运行或需要真实硬件的项目保持待验证。

## Windows 配套协议增量：已执行

- `dotnet build Pointa.sln -c Release`：通过，0 警告、0 错误。
- `dotnet run --project tests/Pointa.SelfTest -c Release --no-build`：322 项断言通过。相较原验证新增默认显示器未知、非法尺寸拒绝、session.ready 真实尺寸、heartbeat 热插拔尺寸和 null 语义。
- `dotnet build tests/Pointa.InteropHost -c Release`：生成可执行测试主机。stdin 自检覆盖 ready、loopback 地址、display/state、非法尺寸拒绝、quit 与清理，已通过。
- InteropHost 使用生产 TLS/SRP/控制守卫与临时 DPAPI 存储；翻页只记录事件，不发送系统按键。它不进入 Windows 安装包。

## Android 自动化

| 检查 | 实际结果 |
| --- | --- |
| Kotlin/JVM 协议测试 | 20 个测试通过，0 跳过：9 个解析/边界/SRP向量测试、10 个 TLS/状态机/触控发送测试、1 个真实 C# 互通闭环 |
| UI 纯逻辑 | 5 个测试通过：边界、镜像侧栏、绝对映射、相对位移和音量键冲突 |
| APK 构建 | `:app:assembleDebug` 成功；Android 8.0+，compile/target API36；标准开发签名 |
| Android Lint | 0 errors，13 warnings（主要为较新依赖版本提示和 KTX 风格建议）；没有使用 baseline 隐藏错误 |
| Android 16.1 模拟器存储/Compose | 4 个测试通过：Keystore AES-GCM 往返/撤销/损坏保留、设置往返、触控松手隐藏/编辑取消、离线禁用/设置可达 |
| 模拟器→真实 Windows 服务 | 1 个设备测试通过：实际 Android BC SRP配对、Keystore 保存、凭据恢复、翻页、远端遗忘和旧凭据拒绝 |
| 真实 MainActivity 流程 | 1 个设备测试通过：扫码结果→UI状态→凭据存储→触控/翻页→中文设置→停止/恢复前台→重连→遗忘。用解码结果注入替代真实相机，未冒充相机扫码测试 |

测试环境：Gradle 8.14、Kotlin 2.2.21、AGP 8.13.2、JDK17；Android 模拟器 `Medium_Phone_API_36.1`（只读启动，1080×2400）；Windows .NET10.0.403。协议互通使用生产 HostService 和临时 DPAPI 数据，自动批准仅存在于测试主机，翻页记录事件而不向用户桌面注入输入。

报告在忽略目录 `artifacts/build.log`、`artifacts/final-build.log`、`artifacts/device-smoke.txt`、`artifacts/app-flow.txt`，原始 XML 位于各模块 `build/test-results`。预览图片在 `artifacts/previews`。关闭清理、旧隐藏覆盖新触控、旧连接命令、网络重连竞争、保存失败、双端证书 pin 等路径已在实现和回归测试中处理。

JVM 互通闭环覆盖：QR、Java/C# SRP双向证明、resume、撤销后拒绝、松手/断开隐藏、重连不重放翻页和显示器心跳更新。错误 pin、证书身份、协议顺序和不合法消息由 MockWebServer 负面测试覆盖；不把模拟器与回环网络结果外推为真实 Wi-Fi 或硬件延迟达标。

## 真机与发行前门槛：未执行

| 测项 | 环境与方法 | 当前状态 |
| --- | --- | --- |
| 相机扫码、相机拒绝后设备码 | 两类 Android 真机，真实 Windows QR | 待验证 |
| 局域网发现、AP 隔离、热点、切网 | 常见家用/会议室网络，手输 IP 回退 | 待验证 |
| 左右手、四边编辑、角落映射 | 不同尺寸、密度、横竖屏、安全区与大字号 | 待验证 |
| TalkBack/开关/拖动控件 | TalkBack 实际遍历、48dp 触达和减少动画 | 待验证 |
| 后台、锁屏、断网与切回 | 触控中触发；观察 Windows 激光隐藏与队列不补发 | 待验证 |
| 高 DPI / 多屏投影热插拔 | Windows 100%/125%/150%；不同方向屏幕 | 待验证 |
| PowerPoint/WPS/PDF/浏览器翻页 | 前台准入策略；系统鼠标位置与焦点不变 | 待验证 |
| P95 延迟、帧率与耗电 | ≥100次高帧率录像；Profiler；持续演示 | 未测量，不承诺达标 |
| Keystore/备份/清数据/升级 | Android 8 与新版本，OEM 设备迁移 | 待验证 |
| 发行签名与安全 | 正式密钥、APK权限/抓包、依赖公告、独立SRP审查 | 尚未作为正式发行版 |
