package id.bangsadigital.riosense.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceFeedbackCard(
    val title: String,
    val subtitle: String,
    val detailLines: List<String> = emptyList(),
    val isError: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class VoiceUiState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val audioLevel: Float = 0f,
    val feedbackCard: VoiceFeedbackCard? = null
)

/**
 * Smart Auto-Switch Android SpeechRecognizer controller for RioSense Voice Command.
 *
 * Automatically chooses the optimal recognition engine in the background with zero manual toggling:
 * - Online (internet validated): uses standard Android SpeechRecognizer for maximum accuracy.
 * - Offline (no internet / Airplane Mode / basement): automatically switches to Android 12+
 *   true On-Device SpeechRecognizer (`createOnDeviceSpeechRecognizer`) or `EXTRA_PREFER_OFFLINE`.
 * - Mid-session network drop: automatically retries once on-device if online recognition hits a network error.
 */
class VoiceCommandController(
    private val context: Context,
    private val onTranscriptReady: (transcript: String, usedOfflineStt: Boolean) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIsOnDevice: Boolean = false
    private var activeLanguage: AppLanguage = AppLanguage.ID
    private var currentSessionOffline: Boolean = false
    private var hasRetriedOfflineThisTurn: Boolean = false

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private val dismissRunnable = Runnable {
        _uiState.update { it.copy(feedbackCard = null) }
    }

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) || isOnDeviceAvailable()

    fun isOnDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun startListening(language: AppLanguage) {
        mainHandler.post {
            activeLanguage = language
            hasRetriedOfflineThisTurn = false
            val online = isNetworkOnline()
            startRecognitionInternal(language = language, forceOffline = !online)
        }
    }

    private fun startRecognitionInternal(language: AppLanguage, forceOffline: Boolean) {
        val isId = language == AppLanguage.ID
        val useOnDevice = forceOffline && isOnDeviceAvailable()
        currentSessionOffline = forceOffline

        if (!useOnDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            showFeedback(
                VoiceFeedbackCard(
                    title = if (isId) "Voice Tidak Tersedia" else "Voice Unavailable",
                    subtitle = if (isId) {
                        "Layanan Speech Recognition tidak ditemukan di perangkat ini."
                    } else {
                        "No Speech Recognition service is installed on this device."
                    },
                    isError = true
                )
            )
            return
        }

        cancelDismissTimer()
        ensureRecognizer(preferOnDevice = useOnDevice)

        // Always enable bilingual ID + EN auto-detection independent of UI display language.
        // Base locale uses "id-ID" (which also handles English loanwords on Android < 14),
        // while Android 14+ natively auto-switches between full "id-ID" and "en-US" in real time.
        val allowedBilingual = arrayListOf("id-ID", "en-US")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "id-ID"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    allowedBilingual
                )
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE
                )
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    allowedBilingual
                )
            }

            if (forceOffline && !useOnDevice) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        _uiState.update {
            it.copy(
                isListening = true,
                partialTranscript = "",
                audioLevel = 0f,
                feedbackCard = null
            )
        }

        runCatching {
            speechRecognizer?.startListening(intent)
        }.onFailure {
            _uiState.update { it.copy(isListening = false) }
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            _uiState.update { it.copy(isListening = false) }
        }
    }

    fun showFeedback(card: VoiceFeedbackCard, autoDismissMs: Long = 5200L) {
        mainHandler.post {
            cancelDismissTimer()
            _uiState.update {
                it.copy(
                    isListening = false,
                    partialTranscript = "",
                    feedbackCard = card
                )
            }
            if (autoDismissMs > 0L) {
                mainHandler.postDelayed(dismissRunnable, autoDismissMs)
            }
        }
    }

    fun dismissFeedback() {
        mainHandler.post {
            cancelDismissTimer()
            _uiState.update { it.copy(feedbackCard = null) }
        }
    }

    fun destroy() {
        mainHandler.post {
            cancelDismissTimer()
            releaseRecognizer()
        }
    }

    private fun isNetworkOnline(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
    }

    private fun cancelDismissTimer() {
        mainHandler.removeCallbacks(dismissRunnable)
    }

    private fun releaseRecognizer() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private fun ensureRecognizer(preferOnDevice: Boolean) {
        if (speechRecognizer != null && recognizerIsOnDevice == preferOnDevice) return
        releaseRecognizer()

        val created = if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        }
        recognizerIsOnDevice = preferOnDevice

        speechRecognizer = created.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _uiState.update { it.copy(isListening = true) }
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2.0f) / 11.0f).coerceIn(0f, 1f)
                    _uiState.update { it.copy(audioLevel = normalized) }
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _uiState.update { it.copy(isListening = false, audioLevel = 0f) }
                }

                override fun onError(error: Int) {
                    val isId = activeLanguage == AppLanguage.ID

                    // Seamless auto-fallback: if online recognition fails due to sudden network loss,
                    // automatically retry once on-device without interrupting the user.
                    val isNetworkDrop = error == SpeechRecognizer.ERROR_NETWORK ||
                        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_SERVER
                    if (isNetworkDrop && !currentSessionOffline && !hasRetriedOfflineThisTurn && isOnDeviceAvailable()) {
                        hasRetriedOfflineThisTurn = true
                        startRecognitionInternal(language = activeLanguage, forceOffline = true)
                        return
                    }

                    _uiState.update { it.copy(isListening = false, partialTranscript = "") }
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            showFeedback(
                                VoiceFeedbackCard(
                                    title = if (isId) "Perintah Suara" else "Voice Command",
                                    subtitle = if (isId) {
                                        "Suara tidak terdeteksi. Ketuk Mic untuk mencoba lagi."
                                    } else {
                                        "No speech detected. Tap Mic to try again."
                                    },
                                    isError = true
                                ),
                                autoDismissMs = 2800L
                            )
                        }
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                            showFeedback(
                                VoiceFeedbackCard(
                                    title = if (isId) "Paket Bahasa Offline Belum Terpasang" else "Offline Speech Pack Missing",
                                    subtitle = if (isId) {
                                        "Buka Controls → Pengaturan lalu ketuk 'Unduh Paket Offline (ID & EN)' saat terhubung internet."
                                    } else {
                                        "Open Controls → Settings and tap 'Download Offline Pack (ID & EN)' while online."
                                    },
                                    isError = true
                                ),
                                autoDismissMs = 6000L
                            )
                        }
                        else -> {
                            showFeedback(
                                VoiceFeedbackCard(
                                    title = if (isId) "Perintah Suara" else "Voice Command",
                                    subtitle = if (isId) {
                                        "Gagal memulai pengenalan suara (kode $error). Coba lagi."
                                    } else {
                                        "Speech recognition error (code $error). Please try again."
                                    },
                                    isError = true
                                ),
                                autoDismissMs = 3600L
                            )
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    _uiState.update { it.copy(isListening = false, partialTranscript = best) }
                    if (best.isNotEmpty()) {
                        onTranscriptReady(best, currentSessionOffline)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        _uiState.update { it.copy(partialTranscript = partial) }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }
}
