#!/usr/bin/env python3
"""
Generates expressive, natural offline Neural Voice MP3 clips (Indonesian & English)
for RioSense Engine-OFF safety reminders using Gemini 2.5 Flash TTS (Aoede) + ffmpeg.

Requires GEMINI_API_KEY environment variable.
Optional CLI args: clip names to generate (e.g. voice_id_all_closed).
"""

import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from google import genai
from google.genai import types

RAW_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"
MODEL_NAME = "gemini-2.5-flash-preview-tts"
VOICE_NAME = "Aoede"

ID_STYLE = (
    "Ucapkan kalimat berikut dengan suara perempuan muda Indonesia yang hangat, ceria, "
    "ramah, sedikit centil dan manja natural seperti asisten pribadi di dalam mobil, "
    "intonasi santai seperti berbicara langsung ke pemilik mobil (jangan kaku seperti berita):\n\n"
)

EN_STYLE = (
    "Say the following in a warm, upbeat, playful, slightly cheeky young female voice, "
    "natural conversational tone like a friendly car co-pilot (not robotic or formal):\n\n"
)

CLIPS = [
    # Indonesian
    ("voice_id_all_closed", ID_STYLE,
     "Mesin sudah mati nih. Jangan lupa cek semua kaca jendelanya ya, pastikan sudah tertutup rapat!"),
    ("voice_id_door_fl", ID_STYLE,
     "Eh, tunggu dulu! Pintu kiri depan masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_fr", ID_STYLE,
     "Eh, tunggu dulu! Pintu kanan depan masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_rl", ID_STYLE,
     "Eh, tunggu dulu! Pintu kiri belakang masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_rr", ID_STYLE,
     "Eh, tunggu dulu! Pintu kanan belakang masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_trunk", ID_STYLE,
     "Eh, tunggu dulu! Pintu bagasi masih belum rapat nih. Jangan lupa tutup rapat bagasi dan semua kaca jendelanya ya!"),
    ("voice_id_doors_multi", ID_STYLE,
     "Eh, tunggu dulu! Ada beberapa pintu yang masih belum rapat nih. Cek lagi pintu dan semua kaca jendelanya sebelum keluar ya!"),

    # English
    ("voice_en_all_closed", EN_STYLE,
     "Engine's off! Don't forget to make sure all your windows are fully closed."),
    ("voice_en_door_fl", EN_STYLE,
     "Hey, wait a second! The front-left door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_fr", EN_STYLE,
     "Hey, wait a second! The front-right door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_rl", EN_STYLE,
     "Hey, wait a second! The rear-left door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_rr", EN_STYLE,
     "Hey, wait a second! The rear-right door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_trunk", EN_STYLE,
     "Hey, wait a second! The tailgate is still open. Please close it and check all your windows!"),
    ("voice_en_doors_multi", EN_STYLE,
     "Hey, wait a second! Multiple doors are still ajar. Please check your doors and all windows before leaving!"),
]


def synthesize_clip(client: genai.Client, prompt: str) -> bytes:
    for attempt in range(4):
        try:
            response = client.models.generate_content(
                model=MODEL_NAME,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_modalities=["AUDIO"],
                    speech_config=types.SpeechConfig(
                        voice_config=types.VoiceConfig(
                            prebuilt_voice_config=types.PrebuiltVoiceConfig(
                                voice_name=VOICE_NAME
                            )
                        )
                    ),
                ),
            )
            parts = response.candidates[0].content.parts
            for part in parts:
                if part.inline_data and part.inline_data.data:
                    return part.inline_data.data
            raise RuntimeError("No audio data returned by Gemini TTS")
        except Exception as exc:
            err_str = str(exc)
            if "GenerateRequestsPerDay" in err_str:
                raise
            if attempt == 3:
                raise
            wait_sec = 22 if "429" in err_str else 4 * (attempt + 1)
            print(f"  Retry {attempt + 1} in {wait_sec}s...", flush=True)
            time.sleep(wait_sec)
    raise RuntimeError("Failed to synthesize clip")


def main() -> None:
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        print("Error: GEMINI_API_KEY environment variable is required.", file=sys.stderr)
        sys.exit(1)

    only_names = set(sys.argv[1:])
    targets = [c for c in CLIPS if not only_names or c[0] in only_names]

    client = genai.Client(
        api_key=api_key,
        vertexai=False,
        http_options=types.HttpOptions(api_version="v1beta"),
    )
    RAW_DIR.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as tmpdir:
        for idx, (name, style, text) in enumerate(targets, start=1):
            print(f"[{idx}/{len(targets)}] Generating {name}.mp3 ({VOICE_NAME}) ...", flush=True)
            pcm_bytes = synthesize_clip(client, style + text)
            pcm_path = Path(tmpdir) / f"{name}.pcm"
            pcm_path.write_bytes(pcm_bytes)

            out_path = RAW_DIR / f"{name}.mp3"
            cmd = [
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "s16le", "-ar", "24000", "-ac", "1",
                "-i", str(pcm_path),
                "-af", "silenceremove=start_periods=1:start_silence=0.05:start_threshold=-45dB,loudnorm=I=-16:TP=-1.5:LRA=11",
                "-ac", "1", "-ar", "24000", "-b:a", "64k",
                str(out_path),
            ]
            subprocess.run(cmd, check=True)
            size_kb = out_path.stat().st_size / 1024.0
            print(f"  Saved {out_path.name} ({size_kb:.1f} KB)", flush=True)
            if idx % 3 == 0 and idx < len(targets):
                time.sleep(21.0)
            else:
                time.sleep(1.5)


if __name__ == "__main__":
    main()
