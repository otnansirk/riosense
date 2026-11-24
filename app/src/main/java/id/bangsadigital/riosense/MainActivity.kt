package id.bangsadigital.riosense

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.bangsadigital.riosense.audio.VoiceReminderEngine
import id.bangsadigital.riosense.data.VehicleRepository
import id.bangsadigital.riosense.ui.RioSenseDashboardScreen
import id.bangsadigital.riosense.ui.components.ControlsTab
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.LocalAppLanguage
import id.bangsadigital.riosense.ui.theme.RioSenseObsidianTheme
import id.bangsadigital.riosense.voice.SemanticNluEngine
import id.bangsadigital.riosense.voice.VoiceActionExecutor
import id.bangsadigital.riosense.voice.VoiceCommand
import id.bangsadigital.riosense.voice.VoiceCommandController
import id.bangsadigital.riosense.voice.VoiceCommandParser
import id.bangsadigital.riosense.voice.VoiceFeedbackCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VoiceDashboardNavigation {
    data class OpenControls(val tab: ControlsTab?, val token: Long) : VoiceDashboardNavigation
    data class CloseMenus(val token: Long) : VoiceDashboardNavigation
}

class MainActivity : ComponentActivity() {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var voiceReminderEngine: VoiceReminderEngine
    private lateinit var voiceActionExecutor: VoiceActionExecutor
    private lateinit var voiceCommandController: VoiceCommandController
    private lateinit var semanticNluEngine: SemanticNluEngine

    private var voiceNavState by mutableStateOf<VoiceDashboardNavigation?>(null)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val lang = vehicleRepository.appLanguage.value
        if (granted) {
            beginVoiceListening()
        } else {
            voiceCommandController.showFeedback(
                VoiceFeedbackCard(
                    title = if (lang == AppLanguage.ID) "Izin Mikrofon Ditolak" else "Microphone Permission Denied",
                    subtitle = if (lang == AppLanguage.ID) {
                        "Aktifkan izin mikrofon agar Voice Command bisa digunakan."
                    } else {
                        "Enable microphone permission to use Voice Commands."
                    },
                    isError = true
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository with persistent local storage (SharedPreferences)
        vehicleRepository = VehicleRepository(applicationContext)
        voiceReminderEngine = VoiceReminderEngine(applicationContext)
        voiceActionExecutor = VoiceActionExecutor(applicationContext, vehicleRepository)
        semanticNluEngine = SemanticNluEngine(applicationContext).also {
            // Gently warm up Offline Semantic AI on background IO thread without blocking UI startup
            it.warmUpAsync(lifecycleScope, initialDelayMs = 1500L)
        }
        voiceCommandController = VoiceCommandController(applicationContext) { transcript, usedOfflineStt ->
            handleVoiceTranscript(transcript, usedOfflineStt)
        }

        // Keep the Head Unit screen awake while the ignition/app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val telemetry by vehicleRepository.telemetry.collectAsState()
            val appLanguage by vehicleRepository.appLanguage.collectAsState()
            val engineOffTick by vehicleRepository.engineOffEventTick.collectAsState()
            val engineStartTick by vehicleRepository.engineStartEventTick.collectAsState()
            val voiceUiState by voiceCommandController.uiState.collectAsState()

            // Preload authentic Kia Rio alarm chime once in SoundPool (~13 KB mono asset, <5ms latency)
            var alarmLoaded by remember { mutableStateOf(false) }
            val alarmEngine = remember {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(attrs)
                    .build()
                pool.setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) alarmLoaded = true
                }
                val id = pool.load(this, R.raw.kia_rio_alarm, 1)
                pool to id
            }

            DisposableEffect(Unit) {
                onDispose {
                    alarmEngine.first.release()
                }
            }

            // Speak bilingual Engine-OFF safety reminder (ajar doors + closed windows check)
            LaunchedEffect(engineOffTick) {
                if (engineOffTick > 0L && telemetry.engineOffVoiceReminderEnabled && !telemetry.audioAlarmMuted) {
                    voiceReminderEngine.speakEngineOffReminder(telemetry.doors, appLanguage)
                }
            }

            // Play time-of-day Welcome Greeting (Off / Chime / Voice) when engine starts
            LaunchedEffect(engineStartTick) {
                if (engineStartTick > 0L && !telemetry.audioAlarmMuted) {
                    voiceReminderEngine.playWelcomeGreeting(telemetry.welcomeSoundMode, appLanguage)
                }
            }

            // Stream real-time OBD-II TX/RX polling frames into the Activity Log
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1500L)
                    vehicleRepository.emitRealtimePollingTick()
                }
            }

            // Play Kia Rio OEM chime periodically on Door Open / Speed Unlock / TPMS Alert / Coolant Overheat
            LaunchedEffect(
                alarmLoaded,
                telemetry.hasAnyCriticalAlert,
                telemetry.audioAlarmMuted,
                telemetry.isSpeedUnlockAlarm
            ) {
                if (alarmLoaded && telemetry.hasAnyCriticalAlert && !telemetry.audioAlarmMuted) {
                    val (pool, soundId) = alarmEngine
                    var streamId = 0
                    try {
                        while (telemetry.hasAnyCriticalAlert && !telemetry.audioAlarmMuted) {
                            streamId = pool.play(soundId, 0.90f, 0.90f, 1, 0, 1.0f)
                            delay(2400L)
                        }
                    } finally {
                        if (streamId != 0) pool.stop(streamId)
                    }
                }
            }

            CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                RioSenseObsidianTheme {
                    RioSenseDashboardScreen(
                        telemetry = telemetry,
                        repository = vehicleRepository,
                        voiceUiState = voiceUiState,
                        voiceNavigation = voiceNavState,
                        onToggleVoiceListening = { handleMicToggle() },
                        onDismissVoiceFeedback = { voiceCommandController.dismissFeedback() },
                        onEnterPipMode = { enterFloatingPipWindow() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        voiceCommandController.destroy()
        semanticNluEngine.close()
        voiceReminderEngine.shutdown()
        super.onDestroy()
    }

    private fun handleMicToggle() {
        if (voiceCommandController.uiState.value.isListening) {
            voiceCommandController.stopListening()
            return
        }
        val hasMicPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMicPerm) {
            beginVoiceListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun beginVoiceListening() {
        val state = vehicleRepository.telemetry.value
        if (!state.audioAlarmMuted) {
            voiceReminderEngine.playListenChime()
        }
        voiceCommandController.startListening(
            language = vehicleRepository.appLanguage.value
        )
    }

    private fun handleVoiceTranscript(transcript: String, usedOfflineStt: Boolean) {
        lifecycleScope.launch {
            val parsed = VoiceCommandParser.parseHybrid(transcript, semanticNluEngine)
            val command = parsed.command
            val lang = vehicleRepository.appLanguage.value
            val now = System.currentTimeMillis()

            val baseCard = withContext(Dispatchers.Main) {
                voiceActionExecutor.execute(
                    command = command,
                    language = lang,
                    onOpenControls = { tab ->
                        voiceNavState = VoiceDashboardNavigation.OpenControls(tab, now)
                    },
                    onCloseMenus = {
                        voiceNavState = VoiceDashboardNavigation.CloseMenus(now)
                    }
                )
            }

            val metaParts = buildList {
                add(if (usedOfflineStt) "Offline STT" else "Online STT")
                if (parsed.matchedByAi && command !is VoiceCommand.Unknown) {
                    val pct = (parsed.confidence * 100).toInt().coerceIn(0, 100)
                    add("Semantic AI $pct% (${parsed.inferenceMs}ms)")
                }
            }
            val finalCard = if (command !is VoiceCommand.Unknown) {
                val extraLines = buildList {
                    add("\"$transcript\"")
                    add(metaParts.joinToString(" · "))
                }
                baseCard.copy(
                    detailLines = baseCard.detailLines + extraLines
                )
            } else {
                baseCard
            }

            if (command !is VoiceCommand.Unknown && !vehicleRepository.telemetry.value.audioAlarmMuted) {
                voiceReminderEngine.playAckChime()
            }
            voiceCommandController.showFeedback(finalCard)
        }
    }

    private fun enterFloatingPipWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }
}
