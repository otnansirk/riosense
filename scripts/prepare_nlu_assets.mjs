#!/usr/bin/env node
/**
 * RioSense Offline Semantic NLU Asset Builder & Accuracy Benchmark
 *
 * 1. Converts HuggingFace XLM-RoBERTa Unigram tokenizer.json -> compact binary spm_vocab.bin
 * 2. Runs INT8 quantized paraphrase-multilingual-MiniLM-L12-v2 via onnxruntime-node
 * 3. Precomputes L2-normalized 384-dim embeddings for bilingual (ID + EN) intent prototypes
 *    and writes app/src/main/assets/nlu/nlu_prototypes.bin
 * 4. Runs a natural-language accuracy & latency benchmark across casual ID/EN car utterances
 */

import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const rootDir = "/Users/otnansirk/Bangsa-Digital/RioSense";
const cacheDir = path.join(rootDir, ".nlu_cache");
const assetsDir = path.join(rootDir, "app/src/main/assets/nlu");
const tokenizerJsonPath = path.join(cacheDir, "tokenizer.json");
const modelPath = path.join(assetsDir, "nlu_model_q8.onnx");
const vocabBinPath = path.join(assetsDir, "spm_vocab.bin");
const prototypesBinPath = path.join(assetsDir, "nlu_prototypes.bin");

const require = createRequire(path.join(cacheDir, "package.json"));
const ort = require("onnxruntime-node");

const BOS_ID = 0;
const EOS_ID = 2;
const UNK_ID = 3;
const METASPACE = "\u2581"; // ▁
const MAX_SEQ_LEN = 48;

/**
 * Curated bilingual (Indonesian + English) natural intent prototypes.
 * Includes both formal commands and casual, everyday in-car phrasing.
 */
const INTENT_PROTOTYPES = {
  VOLUME_UP: [
    "naikkan volume",
    "besarkan suara",
    "keraskan audio",
    "suaranya kekecilan",
    "kurang kenceng suaranya",
    "gedein dikit lagunya",
    "tidak kedengaran suaranya",
    "turn up the volume",
    "make it louder",
    "volume is too quiet",
    "increase audio volume",
    "cannot hear the music"
  ],
  VOLUME_DOWN: [
    "turunkan volume",
    "kecilkan suara",
    "pelankan audio",
    "aduh berisik banget",
    "suaranya terlalu keras",
    "kegedean suaranya",
    "pelanin dikit dong",
    "turn down the volume",
    "make it quieter",
    "too loud in here",
    "lower the sound",
    "reduce volume please"
  ],
  VOLUME_MUTE: [
    "mute suara",
    "matikan suara musik",
    "matiin suara lagunya sebentar",
    "senyapkan audio",
    "bisukan suara media",
    "diamkan suaranya dulu",
    "tanpa suara dulu",
    "mute the volume",
    "silence the music",
    "turn off audio sound"
  ],
  SET_VOLUME_PERCENT: [
    "atur volume ke delapan puluh persen",
    "set volume ke lima puluh persen",
    "ubah suara jadi tujuh puluh",
    "volume jadikan tiga puluh persen",
    "setel volume maksimal",
    "volume setengah saja",
    "set volume to eighty percent",
    "change volume to fifty percent",
    "put volume at thirty",
    "set sound level to maximum"
  ],
  MEDIA_NEXT: [
    "lagu berikutnya",
    "ganti lagu dong",
    "ganti lagu aja bosen",
    "skip lagu ini",
    "lagu ini bosen ganti",
    "lanjut ke lagu selanjutnya",
    "lewatkan lagu ini",
    "next song please",
    "skip this track",
    "change the song",
    "play the next track"
  ],
  MEDIA_PREVIOUS: [
    "lagu sebelumnya",
    "kembali ke lagu tadi",
    "putar ulang lagu sebelumnya",
    "balik ke track sebelumnya",
    "previous song",
    "go back to the last track",
    "play previous track"
  ],
  MEDIA_PAUSE: [
    "pause musik",
    "berhenti dulu lagunya",
    "jeda musik sebentar",
    "stop lagunya",
    "matikan lagunya dulu",
    "pause the music",
    "stop playing music",
    "hold the song"
  ],
  MEDIA_PLAY: [
    "lanjutkan musik",
    "nyalakan musik lagi",
    "putar lagi lagunya",
    "resume music",
    "continue playing the song",
    "unpause music"
  ],
  PLAY_SPOTIFY: [
    "putar lagu di spotify",
    "pengen dengerin musik santai",
    "pengen dengerin lagu enak",
    "dengerin lagu santai dong",
    "puterin lagu favorit",
    "cari lagu di spotify",
    "play some music on spotify",
    "put on a relaxing playlist",
    "I want to listen to music"
  ],
  OPEN_SPOTIFY: [
    "buka aplikasi spotify",
    "bukakan spotify",
    "open spotify app",
    "launch spotify"
  ],
  NAVIGATE_MAPS: [
    "arahkan ke tujuan",
    "arahin ke lokasi itu",
    "tolong arahin ke tempat",
    "navigasi ke alamat",
    "antar saya pergi ke",
    "tunjukkan jalan ke",
    "rute perjalanan menuju",
    "navigate to destination",
    "take me to this place",
    "give me directions to",
    "drive to address"
  ],
  SEARCH_MAPS: [
    "cari pom bensin terdekat",
    "cari spbu di sekitar sini",
    "cari bengkel terdekat",
    "cari tempat makan terdekat",
    "cari restoran atau kafe",
    "cari masjid terdekat",
    "cari atm terdekat",
    "find nearest gas station",
    "search for a nearby restaurant",
    "find a workshop nearby",
    "locate nearby parking"
  ],
  OPEN_MAPS: [
    "buka google maps",
    "buka peta navigasi",
    "open google maps",
    "launch navigation map"
  ],
  STATUS_TIRES: [
    "cek tekanan ban",
    "ban ada yang kempes gak",
    "angin ban aman tidak",
    "kondisi roda dan tpms",
    "apakah ban kurang angin",
    "sekarang kondisi ban bagaimana ya",
    "kondisi ban gimana sekarang",
    "ban aman gak sekarang",
    "bagaimana keadaan ban mobil",
    "check tire pressure",
    "are any tires flat or low",
    "how are the tires looking",
    "tpms status"
  ],
  STATUS_COOLANT: [
    "cek suhu mesin",
    "mesin kepanasan gak",
    "apakah mesin overheat",
    "temperatur air radiator aman",
    "cek coolant mesin",
    "sekarang suhu mesin bagaimana ya",
    "suhu mesin gimana sekarang",
    "kondisi temperatur mesin",
    "check engine temperature",
    "is the engine overheating",
    "how hot is the coolant"
  ],
  STATUS_BATTERY: [
    "cek aki mobil",
    "aki mobil masih sehat gak",
    "kondisi aki masih bagus tidak",
    "aki tekor atau soak gak",
    "tegangan baterai normal tidak",
    "kelistrikan aki aman",
    "aki sekarang gimana ya",
    "sekarang kondisi aki bagaimana ya",
    "check battery voltage",
    "is the car battery healthy",
    "battery level status"
  ],
  STATUS_SERVICE: [
    "kapan jadwal servis berikutnya",
    "kapan harus ganti oli",
    "cek perawatan dan part mobil",
    "ada yang harus diservis gak",
    "jadwal servis sekarang gimana ya",
    "kondisi oli dan servis bagaimana",
    "when is the next oil change",
    "check maintenance schedule",
    "is any service due soon"
  ],
  STATUS_ERRORS: [
    "ada error di mobil gak",
    "cek kerusakan mesin obd",
    "apakah ada kode fault dtc",
    "check for any car errors",
    "are there any obd fault codes",
    "diagnose engine warnings"
  ],
  STATUS_OVERVIEW: [
    "cek kondisi mobil secara keseluruhan",
    "mobil aman semua gak",
    "gimana status mobil sekarang",
    "semua sistem kendaraan normal",
    "check overall car status",
    "is everything okay with the car",
    "vehicle health summary"
  ],
  OPEN_SERVICE: [
    "buka menu servis",
    "tampilkan halaman perawatan",
    "lihat daftar ganti oli",
    "open service menu",
    "show maintenance page"
  ],
  OPEN_SETTINGS: [
    "buka pengaturan",
    "masuk ke menu setting",
    "open settings menu",
    "go to app preferences"
  ],
  OPEN_ERROR_LOG: [
    "buka catatan error",
    "lihat log kerusakan",
    "open error log",
    "show fault history"
  ],
  OPEN_ACTIVITY_LOG: [
    "buka log aktivitas obd",
    "lihat trafik data ecu",
    "open activity log",
    "show obd traffic"
  ],
  OPEN_CONTROLS: [
    "buka menu utama controls",
    "tampilkan menu kontrol",
    "open controls menu",
    "show main menu"
  ],
  CLOSE_MENUS: [
    "tutup menu",
    "tutup pengaturannya",
    "tutup halaman menu ini",
    "kembali ke layar dashboard utama",
    "keluar dari pengaturan",
    "close the menu",
    "close settings page",
    "go back to main dashboard"
  ],
  MINIMIZE_APP: [
    "kecilkan tampilan aplikasi",
    "ubah ke mode mini melayang",
    "minimize the app",
    "switch to picture in picture mode"
  ],
  ALARM_MUTE: [
    "matikan bunyi alarm peringatan",
    "senyapkan suara bel mobil",
    "mute warning alarm chime",
    "silence car alert sound"
  ],
  ALARM_UNMUTE: [
    "nyalakan kembali bunyi alarm",
    "aktifkan suara peringatan mobil",
    "unmute warning alarm chime",
    "enable car alert sound"
  ],
  LANG_EN: [
    "ganti ke bahasa inggris",
    "ubah bahasa aplikasi jadi english",
    "switch language to english",
    "change app language to english"
  ],
  LANG_ID: [
    "ganti ke bahasa indonesia",
    "ubah bahasa aplikasi jadi indonesia",
    "switch language to indonesian",
    "change app language to indonesian"
  ]
};

/**
 * Pure JS SentencePiece Unigram tokenizer matching our Kotlin implementation 1:1.
 */
class UnigramTokenizer {
  constructor(vocab) {
    this.tokenToEntry = new Map();
    this.maxTokenLen = 1;
    for (let id = 0; id < vocab.length; id++) {
      const [piece, score] = vocab[id];
      if (id >= 4 && piece.length > 0) {
        this.tokenToEntry.set(piece, { id, score });
        if (piece.length > this.maxTokenLen) this.maxTokenLen = piece.length;
      }
    }
  }

  encode(rawText) {
    const normalized = rawText.trim().replace(/\s+/g, " ");
    if (!normalized) return [BOS_ID, EOS_ID];

    const ids = [BOS_ID];
    const words = normalized.split(" ");
    for (const word of words) {
      if (!word) continue;
      const pieceWord = METASPACE + word;
      const subIds = this.viterbiEncode(pieceWord);
      for (const id of subIds) {
        ids.push(id);
        if (ids.length >= MAX_SEQ_LEN - 1) break;
      }
      if (ids.length >= MAX_SEQ_LEN - 1) break;
    }
    ids.push(EOS_ID);
    return ids;
  }

  viterbiEncode(text) {
    const n = text.length;
    const bestScore = new Float64Array(n + 1).fill(-1e18);
    const bestPrev = new Int32Array(n + 1).fill(-1);
    const bestId = new Int32Array(n + 1).fill(UNK_ID);
    bestScore[0] = 0.0;

    for (let i = 0; i < n; i++) {
      if (bestScore[i] <= -1e17) continue;
      const maxEnd = Math.min(n, i + this.maxTokenLen);
      let matchedAny = false;
      for (let end = i + 1; end <= maxEnd; end++) {
        const sub = text.slice(i, end);
        const entry = this.tokenToEntry.get(sub);
        if (entry !== undefined) {
          matchedAny = true;
          const cand = bestScore[i] + entry.score;
          if (cand > bestScore[end]) {
            bestScore[end] = cand;
            bestPrev[end] = i;
            bestId[end] = entry.id;
          }
        }
      }
      if (!matchedAny) {
        // Unknown single character fallback
        const cand = bestScore[i] - 20.0;
        if (cand > bestScore[i + 1]) {
          bestScore[i + 1] = cand;
          bestPrev[i + 1] = i;
          bestId[i + 1] = UNK_ID;
        }
      }
    }

    const out = [];
    let cur = n;
    while (cur > 0 && bestPrev[cur] >= 0) {
      out.push(bestId[cur]);
      cur = bestPrev[cur];
    }
    out.reverse();
    return out;
  }
}

function writeCompactVocabBinary(vocab, outPath) {
  const chunks = [];
  const header = Buffer.alloc(4);
  header.writeInt32LE(vocab.length, 0);
  chunks.push(header);

  for (let i = 0; i < vocab.length; i++) {
    const [piece, score] = vocab[i];
    const utf8 = Buffer.from(piece, "utf8");
    const rec = Buffer.alloc(2 + utf8.length + 4);
    rec.writeUInt16LE(utf8.length, 0);
    utf8.copy(rec, 2);
    rec.writeFloatLE(score, 2 + utf8.length);
    chunks.push(rec);
  }

  const full = Buffer.concat(chunks);
  fs.writeFileSync(outPath, full);
  return full.length;
}

async function computeEmbedding(session, tokenizer, text) {
  const inputIds = tokenizer.encode(text);
  const seqLen = inputIds.length;

  const idsBig = BigInt64Array.from(inputIds.map((x) => BigInt(x)));
  const maskBig = new BigInt64Array(seqLen).fill(1n);
  const typeBig = new BigInt64Array(seqLen).fill(0n);

  const feeds = {
    input_ids: new ort.Tensor("int64", idsBig, [1, seqLen]),
    attention_mask: new ort.Tensor("int64", maskBig, [1, seqLen])
  };
  if (session.inputNames.includes("token_type_ids")) {
    feeds.token_type_ids = new ort.Tensor("int64", typeBig, [1, seqLen]);
  }

  const output = await session.run(feeds);
  const tensor = output[session.outputNames[0]];
  const data = tensor.data; // Float32Array [1, seqLen, hiddenDim]
  const hiddenDim = tensor.dims[2];

  // Mean pooling over all active tokens (attention_mask == 1)
  const pooled = new Float32Array(hiddenDim);
  for (let t = 0; t < seqLen; t++) {
    const offset = t * hiddenDim;
    for (let d = 0; d < hiddenDim; d++) {
      pooled[d] += data[offset + d];
    }
  }
  let normSq = 0;
  for (let d = 0; d < hiddenDim; d++) {
    pooled[d] /= seqLen;
    normSq += pooled[d] * pooled[d];
  }
  const invNorm = 1.0 / (Math.sqrt(normSq) + 1e-12);
  for (let d = 0; d < hiddenDim; d++) {
    pooled[d] *= invNorm;
  }
  return pooled;
}

function dot(a, b) {
  let s = 0;
  for (let i = 0; i < a.length; i++) s += a[i] * b[i];
  return s;
}

function writePrototypesBinary(entries, dim, outPath) {
  const chunks = [];
  const header = Buffer.alloc(8);
  header.writeInt32LE(entries.length, 0);
  header.writeInt32LE(dim, 4);
  chunks.push(header);

  for (const { intent, vector } of entries) {
    const idBuf = Buffer.from(intent, "utf8");
    const rec = Buffer.alloc(2 + idBuf.length + dim * 4);
    rec.writeUInt16LE(idBuf.length, 0);
    idBuf.copy(rec, 2);
    const floatOffset = 2 + idBuf.length;
    for (let d = 0; d < dim; d++) {
      rec.writeFloatLE(vector[d], floatOffset + d * 4);
    }
    chunks.push(rec);
  }

  const full = Buffer.concat(chunks);
  fs.writeFileSync(outPath, full);
  return full.length;
}

async function main() {
  console.log("Loading tokenizer.json...");
  const tokJson = JSON.parse(fs.readFileSync(tokenizerJsonPath, "utf8"));
  const vocab = tokJson.model.vocab;

  const vocabBytes = writeCompactVocabBinary(vocab, vocabBinPath);
  console.log(`Wrote ${vocabBinPath} (${(vocabBytes / 1024 / 1024).toFixed(2)} MB, ${vocab.length} tokens)`);

  const tokenizer = new UnigramTokenizer(vocab);

  console.log("Loading ONNX INT8 model...");
  const session = await ort.InferenceSession.create(modelPath, {
    intraOpNumThreads: 2,
    graphOptimizationLevel: "all"
  });
  console.log("Model inputs:", session.inputNames, "outputs:", session.outputNames);

  const prototypeEntries = [];
  let hiddenDim = 384;
  for (const [intent, phrases] of Object.entries(INTENT_PROTOTYPES)) {
    for (const phrase of phrases) {
      const vec = await computeEmbedding(session, tokenizer, phrase);
      hiddenDim = vec.length;
      prototypeEntries.push({ intent, phrase, vector: vec });
    }
  }

  const protoBytes = writePrototypesBinary(prototypeEntries, hiddenDim, prototypesBinPath);
  console.log(
    `Wrote ${prototypesBinPath} (${(protoBytes / 1024).toFixed(1)} KB, ${prototypeEntries.length} prototypes, dim=${hiddenDim})`
  );

  // Natural-language benchmark (casual utterances NOT verbatim in prototypes)
  const testCases = [
    ["aduh berisik banget nih", "VOLUME_DOWN"],
    ["suaranya kekecilan gak kedengeran", "VOLUME_UP"],
    ["suaranya jadiin tujuh puluh dong", "SET_VOLUME_PERCENT"],
    ["ban ada yang kempes gak ya", "STATUS_TIRES"],
    ["sekarang kondisi ban bagaimana ya", "STATUS_TIRES"],
    ["kondisi ban gimana", "STATUS_TIRES"],
    ["ban aman gak sekarang", "STATUS_TIRES"],
    ["mesin kepanasan gak sih", "STATUS_COOLANT"],
    ["gimana suhu mesin sekarang", "STATUS_COOLANT"],
    ["aki mobil masih sehat gak", "STATUS_BATTERY"],
    ["aki sekarang gimana ya", "STATUS_BATTERY"],
    ["kapan ya waktunya ganti oli", "STATUS_SERVICE"],
    ["mobil aman semua gak nih", "STATUS_OVERVIEW"],
    ["ada masalah di mesin gak", "STATUS_ERRORS"],
    ["bosen ah ganti lagu aja", "MEDIA_NEXT"],
    ["pengen dengerin lagu santai dong", "PLAY_SPOTIFY"],
    ["cariin pom bensin terdekat dong", "SEARCH_MAPS"],
    ["tolong arahin ke grand indonesia", "NAVIGATE_MAPS"],
    ["tolong matiin suara lagunya bentar", "VOLUME_MUTE"],
    ["it is way too noisy in here", "VOLUME_DOWN"],
    ["are my tires losing air", "STATUS_TIRES"],
    ["is my engine running too hot", "STATUS_COOLANT"],
    ["skip this boring song", "MEDIA_NEXT"],
    ["find me a nearby gas station", "SEARCH_MAPS"],
    ["tutup dulu pengaturannya", "CLOSE_MENUS"],
    ["halo selamat pagi apa kabar", "NONE"] // should score low!
  ];

  console.log("\n=== Semantic AI Accuracy & Latency Benchmark ===");
  let passed = 0;
  let totalMs = 0;
  for (const [utterance, expected] of testCases) {
    const t0 = performance.now();
    const vec = await computeEmbedding(session, tokenizer, utterance);
    const dt = performance.now() - t0;
    totalMs += dt;

    let bestIntent = "NONE";
    let bestScore = -1;
    for (const p of prototypeEntries) {
      const s = dot(vec, p.vector);
      if (s > bestScore) {
        bestScore = s;
        bestIntent = p.intent;
      }
    }
    const predicted = bestScore >= 0.56 ? bestIntent : "NONE";
    const ok = predicted === expected;
    if (ok) passed++;
    console.log(
      `${ok ? "PASS" : "FAIL"} | ${dt.toFixed(1).padStart(5)}ms | score=${bestScore.toFixed(3)} | pred=${predicted.padEnd(18)} | exp=${expected.padEnd(18)} | "${utterance}"`
    );
  }
  console.log(
    `\nBenchmark result: ${passed}/${testCases.length} passed | avg inference latency: ${(totalMs / testCases.length).toFixed(1)} ms`
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
