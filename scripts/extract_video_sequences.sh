#!/bin/zsh
set -e

FFMPEG="/opt/homebrew/bin/ffmpeg"
ROOT="/Users/otnansirk/Bangsa-Digital/RioSense"
VID_DIR="$ROOT/docs/videos"
DOCS_SEQ="$ROOT/docs/car_sequences"
ASSETS_SEQ="$ROOT/app/src/main/assets/car_sequences"

rm -rf /tmp/video_inspect2 \
       "$ROOT/docs/car_sprites" \
       "$ROOT/docs/veo_frames" \
       "$DOCS_SEQ" \
       "$ASSETS_SEQ"

mkdir -p "$DOCS_SEQ/turntable_360" \
         "$DOCS_SEQ/doors" \
         "$ASSETS_SEQ/turntable_360" \
         "$ASSETS_SEQ/doors"

FILTER_STD="scale=768:432:flags=lanczos,unsharp=3:3:0.55:3:3:0.0"
FILTER_FLIP="hflip,scale=768:432:flags=lanczos,unsharp=3:3:0.55:3:3:0.0"

echo "🔄 1. Mengekstrak 72 Frame Putaran 360° (High-Density Frame-by-Frame)..."
# Bagian A: 40 frame (00..39) dari Kia_Rio_hatchback_rotating.mp4 (t = 0.05s s/d 6.10s: Rear-Right 45° -> Right 90° -> Front 180°)
for i in {0..39}; do
  ts=$(awk "BEGIN {printf \"%.3f\", 0.05 + $i * (6.05 / 39.0)}")
  num=$(printf "%02d" $i)
  "$FFMPEG" -y -v error -ss "$ts" -i "$VID_DIR/Kia_Rio_hatchback_rotating.mp4" \
    -vf "$FILTER_STD" -frames:v 1 -q:v 3 "$DOCS_SEQ/turntable_360/turntable_${num}.jpg"
  cp "$DOCS_SEQ/turntable_360/turntable_${num}.jpg" "$ASSETS_SEQ/turntable_360/turntable_${num}.jpg"
done

# Bagian B: 32 frame (40..71) simetris kembali dari Front 180° -> Front-Left 225° -> Left 270° -> Rear-Left 315°
for j in {0..31}; do
  i=$((40 + j))
  ts=$(awk "BEGIN {printf \"%.3f\", 5.95 - $j * (5.90 / 31.0)}")
  num=$(printf "%02d" $i)
  "$FFMPEG" -y -v error -ss "$ts" -i "$VID_DIR/Kia_Rio_hatchback_rotating.mp4" \
    -vf "$FILTER_FLIP" -frames:v 1 -q:v 3 "$DOCS_SEQ/turntable_360/turntable_${num}.jpg"
  cp "$DOCS_SEQ/turntable_360/turntable_${num}.jpg" "$ASSETS_SEQ/turntable_360/turntable_${num}.jpg"
done

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

# 1. Pintu Depan Kanan (FR) & Depan Kiri (FL) — dari Kia_Rio_door_front_right_opening.mp4 (1.35s .. 3.30s)
extract_seq_window "$VID_DIR/Kia_Rio_door_front_right_opening.mp4" "fr" 1.35 3.30 "$FILTER_STD"
extract_seq_window "$VID_DIR/Kia_Rio_door_front_right_opening.mp4" "fl" 1.35 3.30 "$FILTER_FLIP"

# 2. Pintu Belakang Kanan (RR) & Belakang Kiri (RL) — dari Kia_Rio_door_rear_right_opening.mp4 (Max 0.80s .. 5.75s, buang >6s!)
extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_opening.mp4" "rr" 0.80 5.75 "$FILTER_STD"
extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_opening.mp4" "rl" 0.80 5.75 "$FILTER_FLIP"

# 3. Pintu Depan + Belakang Kanan (FR+RR) & Kiri (FL+RL) — dari Kia_Rio_door_rear_right_and_front_right_opening.mp4 (Max 0.80s .. 5.75s, buang >6s!)
extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_and_front_right_opening.mp4" "fr_rr" 0.80 5.75 "$FILTER_STD"
extract_seq_window "$VID_DIR/Kia_Rio_door_rear_right_and_front_right_opening.mp4" "fl_rl" 0.80 5.75 "$FILTER_FLIP"

# 4. Pintu Bagasi Belakang (TRUNK) — dari Kia_Rio_tailgate_opening.mp4 baru (1.00s .. 5.90s)
#    Di-flip agar sudut serong kanan belakangnya 100% sama dengan posisi kamera standar (45°)
extract_seq_window "$VID_DIR/Kia_Rio_tailgate_opening.mp4" "trunk" 1.00 5.90 "$FILTER_FLIP"

echo "✅ Seluruh 72 frame 360° + 140 frame animasi pintu & bagasi selesai diekstrak!"
