#!/bin/zsh
set -e

PROJECT_ROOT="/Users/otnansirk/Bangsa-Digital/RioSense"
SDK_ROOT="$PROJECT_ROOT/.sdk"
ADB="$SDK_ROOT/android-sdk/platform-tools/adb"
APK_PATH="$PROJECT_ROOT/output/RioSense-debug.apk"

export ANDROID_USER_HOME="$SDK_ROOT/android-home"
export ADB_VENDOR_KEYS="$SDK_ROOT/android-home"

# Opsi Wireless Debugging:
#   ./run-on-phone.sh pair <IP:PAIR_PORT> <KODE_6_DIGIT>  (atau langsung: ./run-on-phone.sh <IP:PAIR_PORT> <KODE_6_DIGIT>)
#   ./run-on-phone.sh <IP:CONNECT_PORT>
if [ "$1" = "pair" ] && [ -n "$2" ] && [ -n "$3" ]; then
  echo "📡 Pairing Wireless ADB ke $2..."
  "$ADB" pair "$2" "$3"
  echo "✅ Pairing selesai! Sekarang jalankan: ./run-on-phone.sh <IP:PORT_UTAMA_DI_HALAMAN_WIRELESS_DEBUGGING>"
  exit 0
elif [ -n "$1" ] && [ -n "$2" ]; then
  echo "📡 Pairing Wireless ADB ke $1..."
  "$ADB" pair "$1" "$2"
  echo "✅ Pairing selesai! Sekarang jalankan: ./run-on-phone.sh <IP:PORT_UTAMA_DI_HALAMAN_WIRELESS_DEBUGGING>"
  exit 0
elif [ -n "$1" ]; then
  echo "📡 Menyambungkan Wireless ADB ke $1..."
  "$ADB" connect "$1"
fi

echo "🔍 Mengecek koneksi HP Android..."
DEVICES_OUT=$("$ADB" devices)

if ! echo "$DEVICES_OUT" | grep -qE $'\tdevice$'; then
  echo "🔄 Merestart ADB server untuk mendeteksi ulang perangkat..."
  "$ADB" kill-server >/dev/null 2>&1 || true
  "$ADB" start-server >/dev/null 2>&1 || true
  sleep 1
  DEVICES_OUT=$("$ADB" devices)
fi

echo "$DEVICES_OUT"

if echo "$DEVICES_OUT" | grep -qE $'\tunauthorized$'; then
  echo ""
  echo "⚠️  HP terdeteksi tapi belum diizinkan (unauthorized)."
  echo "   Buka layar HP kamu lalu ketuk 'Allow / Izinkan' pada pop-up USB Debugging."
  exit 1
fi

if ! echo "$DEVICES_OUT" | grep -qE $'\tdevice$'; then
  echo ""
  echo "❌ HP belum terbaca oleh ADB Mac."
  echo "   Jika kabel USB bermasalah, gunakan Wireless Debugging (1 Wi-Fi):"
  echo "   1. Di HP -> Developer Options -> nyalakan 'Wireless debugging' -> ketuk namanya"
  echo "   2. Pairing (cukup 1x): ./run-on-phone.sh pair IP:PAIR_PORT KODE"
  echo "   3. Connect & Run     : ./run-on-phone.sh IP:CONNECT_PORT"
  exit 1
fi

echo ""
echo "📲 Menginstall RioSense ke HP Android..."
"$ADB" install -r "$APK_PATH"

echo ""
echo "🚀 Membuka aplikasi RioSense di layar HP..."
"$ADB" shell am start -n id.bangsadigital.riosense/.MainActivity

echo ""
echo "✅ Selesai! Aplikasi RioSense sudah tampil di layar HP kamu."
