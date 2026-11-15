# Android 0.1.3：连接、扫码与弹层

## 本轮修复

- 设备码进入独立视图，不再追加在设备列表后面。IP 四段带固定圆点，数字键盘；三位自动下一段，较短段用键盘“下一项”。支持粘贴完整 IPv4。六位码输完自动发起一次配对，失败后保留显式重试。
- 发现结果只用于定位和确认 serverId。电脑刷新设备码后，SRP 使用当前 TLS 握手中的 pairingId；不再被旧 UDP pairingId 误判过期。QR 仍严格匹配其绑定会话，证书校验和 PC 批准不变。
- 发现失败、连接错误、翻页失败等使用短 Toast，不占页面空间；同一次操作的相同错误不反复弹出。无法到达、超时、拒绝连接、设备码错误和等待电脑确认超时分开提示。
- 按 2026-11-15 的最终要求，连接设备使用可下拉关闭的 bottom sheet，自身不做整页滚动，不带返回或设置跳转；多台最近电脑通过选择菜单切换。设置改为全屏页面，左上返回始终可见，仅内容滚动，避免嵌套滚动抖动。设备码同样使用独立全屏输入页。主页仍不滚动。
- 扫码使用全屏 CameraX 预览，保留关闭、手电筒与一行提示，扫描线由上而下移动。ZXing 定位点经 CameraX CoordinateTransform 映射到预览，识别后绘框、短振动、提示音，约 550 ms 后连接。静音模式不强制播音，减少动画关闭扫描线。
- 仅有效 Pointa QR 触发成功反馈。其他码提示后继续扫描；不保存相机帧，退出扫码释放相机、执行器和音频资源。

坐标变换依据 [CameraX 输出变换](https://developer.android.com/media/camera/camerax/transform-output) 与 [CoordinateTransform](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform)。预览与分析使用同一个 ViewPort，保留旋转与裁切关系。

- 默认进入全屏相对坐标；单手改为绝对坐标。旧偏好在首次升级时迁移这两项默认值，保留左右手区域、主题及设备信任，之后继续保存用户选择。
- 按后续反馈彻底删除自动连接/重试及其设置开关；启动、切前台和网络变化均不会自动连接。点击最近电脑时只发现/连接一次，失败后等待手动操作。六位码填完自动提交仍保留。清理连接、输入与设置页的重复说明小字。

## 真实网络排查（2026-11-14）

使用用户授权的 Xiaomi 23078RKD5C / Android 13，通过 USB 调试读取连接阶段；不清空 App 数据、不修改系统防火墙或路由器配置。

1. 原设备码流程在真机复现：先发现电脑、再刷新电脑设备码，输入新的六位码，旧代码在 HELLO 阶段返回 pairing_expired。加入原始发现会话过期后的真实 C#/Java SRP 回归，修复时仍验证 serverId 和证书绑定。
2. 初次接 USB 时同时启用了 USB 网络共享。该连接明确记为 USB，不能作为 Wi-Fi 验证；用户关闭共享后继续测试。
3. 家庭 Wi-Fi 曾建立真实连接，随后复测发生 EHOSTUNREACH；两端可访问网关、连接同一接入点，但互相 ping 失败，手机邻居解析显示 FAILED。手机重新连接 Wi-Fi 后仍未恢复。**家庭网络互访异常尚未解决，不能宣称软件更新已修复这项网络条件。**
4. 手机开启移动数据/个人热点、电脑加入热点，USB 网络共享关闭：自动发现新地址并建立真实加密连接，电脑端 TCP 对端为热点网关。热点路径已验证。随后两次真实相机扫码均经过 PAIRING → WAITING_APPROVAL → CONNECTED，用户在电脑允许后完成连接。振动/提示音由应用触发；不将日志当成主观听觉/触感验收。

开发构建仅向本机 logcat 输出连接阶段、错误类别和目标局域网地址，用于排障；不输出配对码、QR token 或长期凭据，release 不记录这些诊断。

## 图标来源

使用 Codex 内置 imagegen 生成新的透明前景，保存为 `app/src/main/res/drawable-nodpi/pointa_logo.png`，背景 `#101729`。自适应图标使用 6 dp 内边距，并提供 monochrome 层，适配圆形/圆角启动器。未使用外部图标素材或 API CLI。

生成提示词：

> Use case: logo-brand. Create a production Android adaptive launcher icon foreground for Pointa, a phone-based presentation remote with an independent laser pointer. A single elegant, distinctive P-shaped mark drawn as a smooth coral-red laser ribbon, ending in a small precise luminous round point at the upper right. Modern, restrained, geometric, premium app identity; soft controlled tonal shading gives a little depth, but crisp readable silhouette at 24 pixels. No typography or wordmark, no phone, no slide screen, no extra rings, no decorative particles. The mark must be one cohesive symbol. Transparent background, no square or rounded-square tile, no drop shadow outside the symbol. Center the entire mark in the central 60% of a square canvas, with generous equal transparent padding, so Android can crop the background to any adaptive mask without clipping the mark. Intended background in the app will be very deep navy #101729. Primary coral #FF6475, subtle highlight #FF9A9F. Output one polished square icon foreground, not a grid of options.
