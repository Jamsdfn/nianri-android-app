# 念日 Android 开发指南

## 已验证的开发环境

项目面向 Apple Silicon macOS，使用 JDK 17、Android Gradle Plugin 9.2.1、Gradle Wrapper 9.4.1，`minSdk 26`、`compileSdk 36`、`targetSdk 36`。本机 JDK 17 路径为：

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-17.0.19+10/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

安装桌面工具和 JDK：

```bash
brew install --cask android-studio
brew install --cask temurin@17
```

基础 SDK 包：

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" "emulator" \
  "system-images;android-36;google_apis;arm64-v8a"
```

兼容矩阵使用的附加镜像是：

```text
system-images;android-26;google_apis;arm64-v8a
system-images;android-31;google_apis;arm64-v8a
platforms;android-37.1
system-images;android-37.1;google_apis_ps16k;arm64-v8a
```

`android-37.1` 是 SDK Manager 实际提供的 16 KB page-size 镜像；文档不把它写成未核实的“API 37 preview”。以 `sdkmanager --list` 和模拟器 `getprop` 的实际输出为准。

确认版本：

```bash
java -version
./gradlew --version
adb version
sdkmanager --list_installed
emulator -version
```

## 构建、测试与安装

完整自动化门禁（需要已启动一台模拟器）：

```bash
./gradlew clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

单独运行 Task 10 的端到端测试：

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.EndToEndTest
```

输出是仅供开发和设备验收使用的 debug APK，并非应用商店签名发布包：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装或覆盖安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 模拟器

创建兼容矩阵 AVD：

```bash
echo no | avdmanager create avd --force --name nianri_api26 \
  --package "system-images;android-26;google_apis;arm64-v8a" --device pixel_8
echo no | avdmanager create avd --force --name nianri_api31 \
  --package "system-images;android-31;google_apis;arm64-v8a" --device pixel_8
echo no | avdmanager create avd --force --name nianri_api36 \
  --package "system-images;android-36;google_apis;arm64-v8a" --device pixel_8
echo no | avdmanager create avd --force --name nianri_api37 \
  --package "system-images;android-37.1;google_apis_ps16k;arm64-v8a" --device pixel_8
```

逐台启动时使用独立进程；同一时间只连接目标模拟器可以避免 Gradle 把设备矩阵结果混在一起：

```bash
emulator -avd nianri_api36
adb wait-for-device
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
```

## 权限与隐私边界

应用只声明 `POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM` 和 `RECEIVE_BOOT_COMPLETED` 三项自身能力。Android 13+ 首次启用提醒时请求通知权限；Android 12+ 引导用户进入“闹钟和提醒”设置。三个提醒全部关闭时不请求权限。

应用不声明 `INTERNET`，也不声明具有更高授权语义的 `USE_EXACT_ALARM`。所有日子、展示选择和小部件配置均保存在设备本地。最终审计应检查 merged manifest：

```bash
rg "uses-permission" app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

WorkManager 依赖可能合并 `ACCESS_NETWORK_STATE` 来判断系统约束，但没有 `INTERNET` 就不能联网。

## 小米 15 Pro USB / 无线调试

在手机“开发者选项”打开 USB 调试；HyperOS 可能还要求“USB 调试（安全设置）”和“通过 USB 安装”。连接数据线、接受 RSA 指纹后：

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

无线调试选择手机上的“使用配对码配对设备”，注意配对端口与连接端口不同：

```bash
adb pair PHONE_IP:PAIRING_PORT
adb connect PHONE_IP:DEBUG_PORT
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

断线后通常只需重新运行 `adb connect PHONE_IP:DEBUG_PORT`；若手机重新生成端口，以手机当前页面为准。旧式 TCP/IP 模式需先经 USB 授权：

```bash
adb tcpip 5555
adb connect PHONE_IP:5555
```
