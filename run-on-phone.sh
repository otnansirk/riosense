#!/bin/zsh
set -e

PROJECT_ROOT="/Users/otnansirk/Bangsa-Digital/RioSense"
SDK_ROOT="$PROJECT_ROOT/.sdk"
ADB="$SDK_ROOT/android-sdk/platform-tools/adb"
APK_PATH="$PROJECT_ROOT/output/RioSense-debug.apk"

export ANDROID_USER_HOME="$SDK_ROOT/android-home"
export ADB_VENDOR_KEYS="$SDK_ROOT/android-home"

echo "🔍 Mengecek koneksi HP Android di USB..."
"$ADB" devices

echo ""
echo "📲 Menginstall RioSense ke HP Android..."
"$ADB" install -r "$APK_PATH"

echo ""
echo "🚀 Membuka aplikasi RioSense di layar HP..."
"$ADB" shell am start -n id.bangsadigital.riosense/.MainActivity

echo ""
echo "✅ Selesai! Aplikasi RioSense sudah tampil di layar HP kamu."
