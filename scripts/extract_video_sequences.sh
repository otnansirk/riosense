#!/bin/zsh
set -e

FFMPEG="/opt/homebrew/bin/ffmpeg"
ROOT="/Users/otnansirk/Bangsa-Digital/RioSense"
VID_DIR="$ROOT/docs/videos"
DOCS_SEQ="$ROOT/docs/car_sequences"
ASSETS_SEQ="$ROOT/app/src/main/assets/car_sequences"

echo "🧹 Menghapus seluruh asset turntable_360 sebelumnya..."
rm -rf "$DOCS_SEQ/turntable_360" "$ASSETS_SEQ/turntable_360"
mkdir -p "$DOCS_SEQ/turntable_360" \
         "$DOCS_SEQ/doors" \
         "$ASSETS_SEQ/turntable_360" \
         "$ASSETS_SEQ/doors"

FILTER_TURNTABLE="scale=640:360:flags=lanczos,unsharp=3:3:0.55:3:3:0.0"
FILTER_STD="scale=768:432:flags=lanczos,unsharp=3:3:0.55:3:3:0.0"
FILTER_FLIP="hflip,scale=768:432:flags=lanczos,unsharp=3:3:0.55:3:3:0.0"

echo "🔄 1. Mengekstrak 180 Frame Transparent PNG (2°/Frame: 000..179, exact 360° loop frame 4..418)..."
TMP_RAW=$(mktemp -d)
"$FFMPEG" -y -v error -i "$VID_DIR/Kia_Rio_hatchback_rotating.mp4" \
  -vf "trim=start_frame=4:end_frame=419,setpts=PTS-STARTPTS,fps=180/17.2917,$FILTER_TURNTABLE" \
  -frames:v 180 -start_number 0 "$TMP_RAW/raw_%03d.png"

/Users/otnansirk/Bangsa-Digital/RioSense/.sdk/jdk/Contents/Home/bin/java \
  "$ROOT/scripts/TurntableAlphaProcessor.java" \
  "$TMP_RAW" "$DOCS_SEQ/turntable_360" "$ASSETS_SEQ/turntable_360" 180
rm -rf "$TMP_RAW"
echo "   ✅ 180 Frame Transparent PNG (turntable_000.png .. turntable_179.png) selesai!"

# Ekstrak sekuens pintu & bagasi jika belum ada
if [ ! -f "$DOCS_SEQ/doors/trunk_19.jpg" ]; then
  echo "🚪 2. Mengekstrak 7 Sekuens Animasi Pintu & Bagasi (Masing-Masing 20 Frame Halus)..."
  extract_seq_window() {
    local vid="$1"
    local prefix="$2"
    local t_start="$3"
    local t_end="$4"
    local vf="$5"
    for s in {0..19}; do
      local ts=$(awk "BEGIN {printf \"%.3f\", $t_start + $s * (($t_end - $t_start) / 19.0)}")
      local num=$(printf "%02d" $s)
      "$FFMPEG" -y -v error -ss "$ts" -i "$vid" \
        -vf "$vf" -frames:v 1 -q:v 3 "$DOCS_SEQ/doors/${prefix}_${num}.jpg"
      cp "$DOCS_SEQ/doors/${prefix}_${num}.jpg" "$ASSETS_SEQ/doors/${prefix}_${num}.jpg"
    done
    echo "   ✅ Sekuens $prefix (20 frame: ${t_start}s -> ${t_end}s) selesai"
  }

  extract_seq_window "$VID_DIR/Kia_Rio_door_front_right_opening.mp4" "fr" 1.35 3.30 "$FILTER_STD"
  extract_seq_window "$VID_DIR/Kia_Rio_door_front_right_opening.mp4" "fl" 1.35 3.30 "$FILTER_FLIP"
  extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_opening.mp4" "rr" 0.80 5.75 "$FILTER_STD"
  extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_opening.mp4" "rl" 0.80 5.75 "$FILTER_FLIP"
  extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_and_front_right_opening.mp4" "fr_rr" 0.80 5.75 "$FILTER_STD"
  extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_and_front_right_opening.mp4" "fl_rl" 0.80 5.75 "$FILTER_FLIP"
  extract_seq_window "$VID_DIR/Kia_Rio_tailgate_opening.mp4" "trunk" 1.00 5.90 "$FILTER_FLIP"
fi

echo "✅ Seluruh 180 frame transparent PNG (2°/frame, seamless 360°) + sekuens pintu siap!"
