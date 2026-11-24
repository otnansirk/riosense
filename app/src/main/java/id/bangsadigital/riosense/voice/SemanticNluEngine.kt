package id.bangsadigital.riosense.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import kotlin.math.sqrt

data class SemanticIntentMatch(
    val intentId: String,
    val confidence: Float,
    val inferenceMs: Long
)

/**
 * 100% Offline On-Device Semantic AI Engine for RioSense.
 *
 * Uses Microsoft ONNX Runtime with INT8-quantized `paraphrase-multilingual-MiniLM-L12-v2`
 * to compute 384-dim sentence embeddings for Indonesian & English natural utterances and
 * match them against precomputed bilingual intent prototypes via cosine similarity.
 *
 * Designed for 4 GB RAM Android Head Units:
 * - Lazy-loaded on Dispatchers.IO (zero UI jank)
 * - Loaded from a file path so ONNX Runtime memory-maps model weights instead of duplicating them in JVM heap
 * - 2 intra-op threads (~20-50 ms per utterance, 0% CPU when idle)
 */
class SemanticNluEngine(private val context: Context) {

    private val initMutex = Mutex()
    private val inferMutex = Mutex()

    @Volatile
    private var isReady: Boolean = false

    @Volatile
    private var isClosed: Boolean = false

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: UnigramTokenizer? = null
    private var prototypeIntents: Array<String> = emptyArray()
    private var prototypeVectors: FloatArray = FloatArray(0)
    private var embeddingDim: Int = 384

    fun warmUpAsync(scope: CoroutineScope, initialDelayMs: Long = 1500L) {
        scope.launch(Dispatchers.IO) {
            if (initialDelayMs > 0L) delay(initialDelayMs)
            ensureInitialized()
        }
    }

    suspend fun ensureInitialized(): Boolean {
        if (isReady) return true
        if (isClosed) return false

        return initMutex.withLock {
            if (isReady) return@withLock true
            if (isClosed) return@withLock false

            withContext(Dispatchers.IO) {
                runCatching {
                    val startMs = SystemClock.elapsedRealtime()
                    val modelFile = ensureModelExtracted()
                    val loadedTokenizer = UnigramTokenizer.loadFromAssets(context.assets, VOCAB_ASSET_PATH)
                    loadPrototypes()

                    val env = OrtEnvironment.getEnvironment()
                    val sessionOpts = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    val session = env.createSession(modelFile.absolutePath, sessionOpts)

                    ortEnv = env
                    ortSession = session
                    tokenizer = loadedTokenizer
                    isReady = true

                    val elapsed = SystemClock.elapsedRealtime() - startMs
                    Log.i(TAG, "Offline Semantic AI ready in ${elapsed}ms (${prototypeIntents.size} prototypes)")
                    true
                }.onFailure { err ->
                    Log.e(TAG, "Failed to initialize Offline Semantic AI", err)
                }.getOrDefault(false)
            }
        }
    }

    suspend fun classify(
        rawText: String,
        minConfidence: Float = DEFAULT_CONFIDENCE_THRESHOLD
    ): SemanticIntentMatch? {
        if (rawText.isBlank() || isClosed) return null
        if (!ensureInitialized()) return null

        val env = ortEnv ?: return null
        val session = ortSession ?: return null
        val tok = tokenizer ?: return null
        if (prototypeIntents.isEmpty()) return null

        return withContext(Dispatchers.Default) {
            inferMutex.withLock {
                if (isClosed) return@withLock null
                runCatching {
                    val startMs = SystemClock.elapsedRealtime()
                    val inputIds = tok.encode(rawText)
                    val seqLen = inputIds.size
                    val shape = longArrayOf(1L, seqLen.toLong())

                    val attentionMask = LongArray(seqLen) { 1L }
                    val tokenTypeIds = LongArray(seqLen) { 0L }

                    val idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
                    val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)
                    val typeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)

                    val inputs = HashMap<String, OnnxTensor>(3).apply {
                        put("input_ids", idsTensor)
                        put("attention_mask", maskTensor)
                        if (session.inputNames.contains("token_type_ids")) {
                            put("token_type_ids", typeTensor)
                        }
                    }

                    val embedding = try {
                        session.run(inputs).use { results ->
                            val outputTensor = results[0] as OnnxTensor
                            val floatBuf = outputTensor.floatBuffer
                            val hiddenDim = embeddingDim

                            // Mean pooling over all tokens + L2 normalization
                            val pooled = FloatArray(hiddenDim)
                            for (t in 0 until seqLen) {
                                val base = t * hiddenDim
                                for (d in 0 until hiddenDim) {
                                    pooled[d] += floatBuf.get(base + d)
                                }
                            }
                            var normSq = 0.0
                            val invSeq = 1.0f / seqLen.toFloat()
                            for (d in 0 until hiddenDim) {
                                val v = pooled[d] * invSeq
                                pooled[d] = v
                                normSq += (v * v).toDouble()
                            }
                            val invNorm = (1.0 / (sqrt(normSq) + 1e-12)).toFloat()
                            for (d in 0 until hiddenDim) {
                                pooled[d] *= invNorm
                            }
                            pooled
                        }
                    } finally {
                        idsTensor.close()
                        maskTensor.close()
                        typeTensor.close()
                    }

                    // Cosine similarity against all precomputed L2-normalized prototypes
                    var bestIdx = -1
                    var bestScore = -1f
                    val dim = embeddingDim
                    for (i in prototypeIntents.indices) {
                        val offset = i * dim
                        var dot = 0f
                        for (d in 0 until dim) {
                            dot += embedding[d] * prototypeVectors[offset + d]
                        }
                        if (dot > bestScore) {
                            bestScore = dot
                            bestIdx = i
                        }
                    }

                    val elapsed = SystemClock.elapsedRealtime() - startMs
                    if (bestIdx >= 0 && bestScore >= minConfidence) {
                        SemanticIntentMatch(
                            intentId = prototypeIntents[bestIdx],
                            confidence = bestScore,
                            inferenceMs = elapsed
                        )
                    } else {
                        null
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Semantic classification failed", err)
                }.getOrNull()
            }
        }
    }

    fun close() {
        isClosed = true
        isReady = false
        runCatching { ortSession?.close() }
        ortSession = null
        tokenizer = null
    }

    private fun ensureModelExtracted(): File {
        val nluDir = File(context.filesDir, "nlu").apply { mkdirs() }
        val targetFile = File(nluDir, MODEL_FILE_NAME)

        val expectedSize = runCatching {
            context.assets.openFd(MODEL_ASSET_PATH).use { it.length }
        }.getOrDefault(-1L)

        if (targetFile.exists() && (expectedSize <= 0L || targetFile.length() == expectedSize)) {
            return targetFile
        }

        val tempFile = File(nluDir, "$MODEL_FILE_NAME.tmp")
        context.assets.open(MODEL_ASSET_PATH).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        }
        if (targetFile.exists()) targetFile.delete()
        tempFile.renameTo(targetFile)
        return targetFile
    }

    private fun loadPrototypes() {
        val bytes = context.assets.open(PROTOTYPES_ASSET_PATH).use { input ->
            BufferedInputStream(input, 65536).readBytes()
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.int
        val dim = buf.int

        val intents = Array(count) { "" }
        val vectors = FloatArray(count * dim)

        for (i in 0 until count) {
            val idLen = buf.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            buf.get(idBytes)
            intents[i] = String(idBytes, Charsets.UTF_8)
            val base = i * dim
            for (d in 0 until dim) {
                vectors[base + d] = buf.float
            }
        }

        embeddingDim = dim
        prototypeIntents = intents
        prototypeVectors = vectors
    }

    companion object {
        private const val TAG = "RioSenseNLU"
        private const val MODEL_FILE_NAME = "nlu_model_q8.onnx"
        private const val MODEL_ASSET_PATH = "nlu/nlu_model_q8.onnx"
        private const val VOCAB_ASSET_PATH = "nlu/spm_vocab.bin"
        private const val PROTOTYPES_ASSET_PATH = "nlu/nlu_prototypes.bin"
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.70f
    }
}
