# Android Release 签名

从 v0.1.5 起，GitHub Release 分发不可调试的 release APK，使用固定的 Pointa 正式签名；Actions 中的 debug APK 仅用于开发，不能覆盖安装正式版。

维护者签名材料保存在发布电脑的 `%LOCALAPPDATA%/PointaSigning/`，不进入 Git、CI 或 Release 附件。`pointa-release.p12` 是私钥库，alias 为 `pointa`；`password.dpapi` 是当前 Windows 用户保护的密码。公开证书随 Release 提供，供核对身份。

**请备份私钥库，并在迁移电脑或重装系统前安全导出并离线保存密码。只复制 DPAPI 文件到其他账号不能解密。私钥丢失后无法为已安装用户签发同身份更新。** 不要把私钥、密码或备份上传到 Issue。

构建命令：

```powershell
./gradlew.bat :app:assembleRelease :app:lintRelease :app:testReleaseUnitTest
```

使用 Android SDK 的 `apksigner` 为 `app/build/outputs/apk/release/app-release-unsigned.apk` 签名。密码通过临时进程环境变量传入，使用 `--ks-pass env:POINTA_SIGN_PASSWORD --key-pass env:POINTA_SIGN_PASSWORD`，不用明文命令参数。签名后执行 `apksigner verify --verbose --print-certs`，检查 Manifest 不含 `android:debuggable=true`，生成 SHA-256 后上传正式 APK 与校验文件。后续发布必须继续使用同一密钥，递增 versionCode。

从旧 debug 包切换时不能直接覆盖安装，须由用户主动卸载后安装、重新配对。发布流程不应擅自清除用户手机数据。

参考：[Android 官方应用签名说明](https://developer.android.com/studio/publish/app-signing)。
