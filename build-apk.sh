#!/bin/zsh
set -e

PROJECT_ROOT="/Users/otnansirk/Bangsa-Digital/RioSense"
SDK_ROOT="$PROJECT_ROOT/.sdk"

export JAVA_HOME="$SDK_ROOT/jdk/Contents/Home"
export ANDROID_HOME="$SDK_ROOT/android-sdk"
export ANDROID_SDK_ROOT="$SDK_ROOT/android-sdk"
export ANDROID_USER_HOME="$SDK_ROOT/android-home"
export GRADLE_USER_HOME="$SDK_ROOT/gradle-home"
export PATH="$JAVA_HOME/bin:$SDK_ROOT/gradle/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

echo "==================================================="
echo " RioSense Portable Builder (Apple Silicon Native)"
echo " JAVA_HOME    : $JAVA_HOME"
echo " ANDROID_HOME : $ANDROID_HOME"
echo " GRADLE_HOME  : $GRADLE_USER_HOME"
echo "==================================================="

cd "$PROJECT_ROOT"
"$SDK_ROOT/gradle/bin/gradle" assembleDebug --no-daemon -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"

mkdir -p "$PROJECT_ROOT/output"
if [ -f "$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk" ]; then
  cp "$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk" "$PROJECT_ROOT/output/RioSense-debug.apk"
  echo ""
  echo "✅ Build sukses! File APK tersedia di: $PROJECT_ROOT/output/RioSense-debug.apk"
  echo "✅ Proses compiler sudah dimatikan otomatis (--no-daemon), RAM kembali 0 MB."
fi
