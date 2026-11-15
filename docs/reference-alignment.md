# 空中指针与网页参考对齐（0.1.2）

参考本地 `.references/lan-laser-pen` 的固定提交 [`1e643ed5cb9c93056ed9a9b2dce4ecd3765ad67b`](https://github.com/fuxing0910-hue/lan-laser-pen/tree/1e643ed5cb9c93056ed9a9b2dce4ecd3765ad67b)，主要来源为 [`example/orientation.ts`](https://github.com/fuxing0910-hue/lan-laser-pen/blob/1e643ed5cb9c93056ed9a9b2dce4ecd3765ad67b/example/orientation.ts) 和 `example/RemoteController.ts`。MIT 版权及许可保存在 `app/src/main/assets/licenses/lan-laser-pen-LICENSE.txt` 并随 APK 分发。

`AirPointerMapper.kt` 使用归一化四元数直接计算旋转矩阵，得到与网页 Euler 转换等价的手机方向基底。手机上边缘是指向轴，屏幕右边和法线构成另两个轴；按下时记录参考方向，再将当前指向投影到参考轴，用 atan2 得到水平/垂直角度。使用 80 ms 时间低通、0.1° 连续死区、25° 归一化范围，再从网页 [-1,1] 映射为 Pointa [0,1]。

没有旋转向量时，按参考实现用重力/加速度求 beta、gamma，计算相对倾斜并根据屏幕方向换轴。传感器选择顺序为游戏旋转向量、旋转向量、重力、加速度；倾斜回退在 UI 中明确提示。推荐手机屏幕朝上、上边缘指向演示屏；倾斜回退使用左右/前后倾斜，而非完整空间方向跟踪。

0.1.4 改为进入已连接的空中指针控制页自动注册，退出该模式、后台、导航和断连时注销；重新进入保留当前位置，归零校准或显示方向变化恢复中心。超过 250 ms 的旧样本丢弃，500 ms 未更新停止控制。灵敏度 60% 对应原网页 1 倍，不新增网络消息或后台传感器服务；姿态映射算法未改变。

`AirPointerMapperTest` 使用原始 TypeScript 函数直接生成的 16 组、128 个姿态/重力样本，覆盖四个屏幕方向、各旋转轴、时间滤波与倾斜回退；另测归零、重握、采样率、方向和无效数据。夹具保存在 `app/src/test/resources/air-reference.json` 与 `.csv`，生成器位于同级 Windows 仓库 `scripts/generate-reference-fixtures.mjs`。测试不代表真机陀螺仪漂移、姿势舒适度或端到端延迟已经达标。

Windows 0.1.4 同时复刻用户选定的网页贝塞尔收尖拖尾：500 ms、标准直径 12 DIP、宽度 0–8 DIP、张力 0.3、同色光点阴影。激光颜色直接来自手机主题，没有独立颜色设置。12 个真实浏览器图像与 Windows Skia 渲染的 Alpha 最大差为 1/255，详见 Windows 的 `docs/reference-alignment.md`。
