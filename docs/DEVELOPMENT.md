# Nianri Android development

## Toolchain

This project uses JDK 17, Android SDK 36, and Gradle Wrapper 9.4.1 on Apple Silicon macOS.

Install the desktop and command-line prerequisites:

```bash
brew install --cask android-studio
brew install wget
brew install --cask temurin@17
```

This machine resolves JDK 17 to `$HOME/Library/Java/JavaVirtualMachines/jdk-17.0.19+10/Contents/Home`. Configure every shell before running Gradle or Android SDK commands:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

The required SDK packages are:

```text
platform-tools
platforms;android-36
build-tools;36.0.0
emulator
system-images;android-36;google_apis;arm64-v8a
```

Accept licenses and install them with `sdkmanager`:

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" "emulator" "system-images;android-36;google_apis;arm64-v8a"
```

Confirm the pinned toolchain:

```bash
java -version
./gradlew --version
adb version
sdkmanager --list_installed
emulator -version
```

## Build and test

Run the complete shell verification:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Emulator

Create and start an API 36 Apple Silicon emulator:

```bash
echo no | avdmanager create avd \
  --name nianri-api-36 \
  --package "system-images;android-36;google_apis;arm64-v8a" \
  --device pixel_8
emulator -avd nianri-api-36
```

Once it has booted, install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Xiaomi device debugging

On the phone, enable Developer options by tapping the OS/MIUI version seven times. In Developer options, enable USB debugging; Xiaomi devices may also require **USB debugging (Security settings)** and **Install via USB**. Connect the cable, accept the phone's RSA prompt, then run:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For Android 11 or newer wireless debugging, choose **Pair device with pairing code** on the phone and use the shown addresses and ports:

```bash
adb pair PHONE_IP:PAIRING_PORT
adb connect PHONE_IP:DEBUG_PORT
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For Xiaomi versions that expose only legacy TCP/IP debugging, connect once over USB and run:

```bash
adb tcpip 5555
adb connect PHONE_IP:5555
adb devices
```
