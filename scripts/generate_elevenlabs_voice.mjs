#!/usr/bin/env node
/**
 * Generates uniform, expressive offline Neural Voice MP3 clips (Indonesian & English)
 * for RioSense Engine-OFF safety reminders and Engine-START time-of-day greetings
 * using ElevenLabs (eleven_multilingual_v2) + ffmpeg.
 *
 * Requires ELEVENLABS_API_KEY environment variable.
 * Optional CLI args: specific clip names to regenerate.
 */

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const RAW_DIR = path.resolve(__dirname, "..", "app", "src", "main", "res", "raw");

const MODEL_ID = "eleven_multilingual_v2";
const VOICE_ID_INDONESIAN = process.env.ELEVENLABS_VOICE_ID_ID || "ZF5uWKM2iVvSId3tBCYt";
const VOICE_ID_ENGLISH = process.env.ELEVENLABS_VOICE_ID_EN || "bxiObU1YDrf7lrFAyV99";

const CLIPS = [
  // Engine-OFF Safety Reminders (Indonesian)
  ["voice_id_all_closed", "Mesin sudah mati nih. Jangan lupa cek semua kaca jendelanya ya, pastikan sudah tertutup rapat!"],
  ["voice_id_door_fl", "Eh, tunggu dulu! Pintu kiri depan masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"],
  ["voice_id_door_fr", "Eh, tunggu dulu! Pintu kanan depan masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"],
  ["voice_id_door_rl", "Eh, tunggu dulu! Pintu kiri belakang masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"],
  ["voice_id_door_rr", "Eh, tunggu dulu! Pintu kanan belakang masih belum rapat nih. Jangan lupa tutup rapat pintu dan semua kaca jendelanya ya!"],
  ["voice_id_door_trunk", "Eh, tunggu dulu! Pintu bagasi masih belum rapat nih. Jangan lupa tutup rapat bagasi dan semua kaca jendelanya ya!"],
  ["voice_id_doors_multi", "Eh, tunggu dulu! Ada beberapa pintu yang masih belum rapat nih. Cek lagi pintu dan semua kaca jendelanya sebelum keluar ya!"],

  // Engine-OFF Safety Reminders (English)
  ["voice_en_all_closed", "Engine's off! Don't forget to make sure all your windows are fully closed."],
  ["voice_en_door_fl", "Hey, wait a second! The front-left door is still ajar. Please close it and check all your windows!"],
  ["voice_en_door_fr", "Hey, wait a second! The front-right door is still ajar. Please close it and check all your windows!"],
  ["voice_en_door_rl", "Hey, wait a second! The rear-left door is still ajar. Please close it and check all your windows!"],
  ["voice_en_door_rr", "Hey, wait a second! The rear-right door is still ajar. Please close it and check all your windows!"],
  ["voice_en_door_trunk", "Hey, wait a second! The tailgate is still open. Please close it and check all your windows!"],
  ["voice_en_doors_multi", "Hey, wait a second! Multiple doors are still ajar. Please check your doors and all windows before leaving!"],

  // Engine-START Time-of-Day Welcome Greetings (Indonesian)
  ["welcome_id_morning", "Selamat pagi! Kia Rio siap menemani hari ini. Hati-hati di jalan ya!"],
  ["welcome_id_afternoon", "Selamat siang! Kia Rio siap jalan. Tetap fokus dan hati-hati ya!"],
  ["welcome_id_evening", "Selamat sore! Kia Rio siap jalan. Hati-hati di perjalanan ya!"],
  ["welcome_id_night", "Selamat malam! Kia Rio siap jalan. Jangan lupa cek lampu dan hati-hati di jalan ya!"],

  // Engine-START Time-of-Day Welcome Greetings (English)
  ["welcome_en_morning", "Good morning! Your Kia Rio is ready for the day. Drive safe!"],
  ["welcome_en_afternoon", "Good afternoon! Your Kia Rio is ready to go. Stay focused and drive safe!"],
  ["welcome_en_evening", "Good evening! Your Kia Rio is ready. Have a smooth and safe drive!"],
  ["welcome_en_night", "Good evening! Your Kia Rio is ready. Don't forget your headlights and drive safe!"],
];

async function pickVoice(apiKey) {
  const res = await fetch("https://api.elevenlabs.io/v1/voices", {
    headers: { "xi-api-key": apiKey },
  });
  if (!res.ok) {
    throw new Error(`Failed to list voices (${res.status}): ${await res.text()}`);
  }
  const data = await res.json();
  const voices = data.voices || [];
  for (const id of PREFERRED_VOICE_IDS) {
    const match = voices.find((v) => v.voice_id === id);
    if (match) return match;
  }
  const female = voices.find((v) => (v.labels?.gender || "").toLowerCase() === "female");
  return female || voices[0];
}

async function synthesizeClip(apiKey, voiceId, text) {
  const url = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?output_format=mp3_44100_128`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "xi-api-key": apiKey,
      "Content-Type": "application/json",
      Accept: "audio/mpeg",
    },
    body: JSON.stringify({
      text,
      model_id: MODEL_ID,
      voice_settings: {
        stability: 0.44,
        similarity_boost: 0.78,
        style: 0.35,
        use_speaker_boost: true,
      },
    }),
  });
  if (!res.ok) {
    throw new Error(`TTS failed (${res.status}): ${await res.text()}`);
  }
  return Buffer.from(await res.arrayBuffer());
}

async function main() {
  const apiKey = (process.env.ELEVENLABS_API_KEY || "").trim();
  if (!apiKey) {
    console.error("Error: ELEVENLABS_API_KEY environment variable is required.");
    process.exit(1);
  }

  const onlyNames = new Set(process.argv.slice(2));
  const targets = CLIPS.filter(([name]) => onlyNames.size === 0 || onlyNames.has(name));

  console.log(`Using ElevenLabs voices -> ID: ${VOICE_ID_INDONESIAN} | EN: ${VOICE_ID_ENGLISH} · model: ${MODEL_ID}`);

  fs.mkdirSync(RAW_DIR, { recursive: true });
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "riosense-el-"));

  try {
    for (let i = 0; i < targets.length; i++) {
      const [name, text] = targets[i];
      const isIndonesian = name.includes("_id_");
      const voiceId = isIndonesian ? VOICE_ID_INDONESIAN : VOICE_ID_ENGLISH;
      console.log(`[${i + 1}/${targets.length}] Generating ${name}.mp3 (${isIndonesian ? "ID" : "EN"}: ${voiceId}) ...`);
      const rawBuf = await synthesizeClip(apiKey, voiceId, text);
      const rawTmp = path.join(tmpDir, `${name}_raw.mp3`);
      const outPath = path.join(RAW_DIR, `${name}.mp3`);
      fs.writeFileSync(rawTmp, rawBuf);

      execFileSync("ffmpeg", [
        "-y", "-loglevel", "error",
        "-i", rawTmp,
        "-af", "silenceremove=start_periods=1:start_silence=0.04:start_threshold=-45dB,loudnorm=I=-16:TP=-1.5:LRA=11",
        "-ac", "1", "-ar", "24000", "-b:a", "64k",
        outPath,
      ]);

      const sizeKb = (fs.statSync(outPath).size / 1024).toFixed(1);
      console.log(`  Saved ${name}.mp3 (${sizeKb} KB)`);
    }
    console.log(`All ${targets.length} ElevenLabs voice clips generated successfully!`);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
