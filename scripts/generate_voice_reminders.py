#!/usr/bin/env python3
"""
Generates compact, expressive offline Neural AI Voice MP3 clips (Indonesian & English)
for RioSense Engine-OFF safety reminders using edge-tts + ffmpeg.
"""

import asyncio
import os
import subprocess
import tempfile
from pathlib import Path

import edge_tts

RAW_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"

ID_VOICE = "id-ID-GadisNeural"
EN_VOICE = "en-US-JennyNeural"

CLIPS = [
    # Indonesian — cheerful, friendly, expressive ("Santai & Ceria")
    ("voice_id_all_closed", ID_VOICE, "+5Hz", "+4%",
     "Mesin sudah mati nih. Jangan lupa cek semua kaca jendelanya ya, pastikan sudah tertutup rapat!"),
    ("voice_id_door_fl", ID_VOICE, "+5Hz", "+4%",
     "Eh, tunggu dulu! Pintu kiri depan masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_fr", ID_VOICE, "+5Hz", "+4%",
     "Eh, tunggu dulu! Pintu kanan depan masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_rl", ID_VOICE, "+5Hz", "+4%",
     "Eh, tunggu dulu! Pintu kiri belakang masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_rr", ID_VOICE, "+5Hz", "+4%",
     "Eh, tunggu dulu! Pintu kanan belakang masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"),
    ("voice_id_door_trunk", ID_VOICE, "+5Hz", "+4%",
     "Eh, tunggu dulu! Pintu bagasi masih belum rapat nih. Jangan lupa tutup rapat bagasi dan semua kaca jendelanya ya!"),
    ("voice_id_doors_multi", ID_VOICE, "+5Hz", "+4%",
     "Eh, tunggu dulu! Ada beberapa pintu yang masih belum rapat nih. Cek lagi pintu dan semua kaca jendelanya sebelum keluar ya!"),

    # English — warm, upbeat, natural
    ("voice_en_all_closed", EN_VOICE, "+4Hz", "+4%",
     "Engine's off! Don't forget to make sure all your windows are fully closed."),
    ("voice_en_door_fl", EN_VOICE, "+4Hz", "+4%",
     "Hey, wait a second! The front-left door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_fr", EN_VOICE, "+4Hz", "+4%",
     "Hey, wait a second! The front-right door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_rl", EN_VOICE, "+4Hz", "+4%",
     "Hey, wait a second! The rear-left door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_rr", EN_VOICE, "+4Hz", "+4%",
     "Hey, wait a second! The rear-right door is still ajar. Please close it and check all your windows!"),
    ("voice_en_door_trunk", EN_VOICE, "+4Hz", "+4%",
     "Hey, wait a second! The tailgate is still open. Please close it and check all your windows!"),
    ("voice_en_doors_multi", EN_VOICE, "+4Hz", "+4%",
     "Hey, wait a second! Multiple doors are still ajar. Please check your doors and all windows before leaving!"),

    # Time-of-day Welcome Greetings (Indonesian)
    ("welcome_id_morning", ID_VOICE, "+5Hz", "+4%",
     "Selamat pagi! Kia Rio siap menemani hari ini. Hati-hati di jalan ya!"),
    ("welcome_id_afternoon", ID_VOICE, "+5Hz", "+4%",
     "Selamat siang! Kia Rio siap jalan. Tetap fokus dan hati-hati ya!"),
    ("welcome_id_evening", ID_VOICE, "+5Hz", "+4%",
     "Selamat sore! Kia Rio siap jalan. Hati-hati di perjalanan ya!"),
    ("welcome_id_night", ID_VOICE, "+5Hz", "+4%",
     "Selamat malam! Kia Rio siap jalan. Jangan lupa cek lampu dan hati-hati di jalan ya!"),

    # Time-of-day Welcome Greetings (English)
    ("welcome_en_morning", EN_VOICE, "+4Hz", "+4%",
     "Good morning! Your Kia Rio is ready for the day. Drive safe!"),
    ("welcome_en_afternoon", EN_VOICE, "+4Hz", "+4%",
     "Good afternoon! Your Kia Rio is ready to go. Stay focused and drive safe!"),
    ("welcome_en_evening", EN_VOICE, "+4Hz", "+4%",
     "Good evening! Your Kia Rio is ready. Have a smooth and safe drive!"),
    ("welcome_en_night", EN_VOICE, "+4Hz", "+4%",
     "Good evening! Your Kia Rio is ready. Don't forget your headlights and drive safe!"),
]


async def generate_all() -> None:
    import sys
    only_names = set(sys.argv[1:])
    targets = [c for c in CLIPS if not only_names or c[0] in only_names]
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmpdir:
        for name, voice, pitch, rate, text in targets:
            raw_tmp = Path(tmpdir) / f"{name}_raw.mp3"
            out_path = RAW_DIR / f"{name}.mp3"
            communicate = edge_tts.Communicate(text=text, voice=voice, pitch=pitch, rate=rate)
            await communicate.save(str(raw_tmp))

            # Normalize loudness, trim leading silence, and encode compact 48kbps 24kHz mono MP3
            cmd = [
                "ffmpeg", "-y", "-loglevel", "error",
                "-i", str(raw_tmp),
                "-af", "silenceremove=start_periods=1:start_silence=0.05:start_threshold=-45dB,loudnorm=I=-16:TP=-1.5:LRA=11",
                "-ac", "1", "-ar", "24000", "-b:a", "48k",
                str(out_path),
            ]
            subprocess.run(cmd, check=True)
            size_kb = out_path.stat().st_size / 1024.0
            print(f"Generated {out_path.name} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    asyncio.run(generate_all())
