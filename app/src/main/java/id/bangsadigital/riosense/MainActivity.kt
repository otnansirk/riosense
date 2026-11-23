package id.bangsadigital.riosense

import android.app.PictureInPictureParams
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import id.bangsadigital.riosense.audio.VoiceReminderEngine
import id.bangsadigital.riosense.data.VehicleRepository
import id.bangsadigital.riosense.ui.RioSenseDashboardScreen
import id.bangsadigital.riosense.ui.i18n.LocalAppLanguage
import id.bangsadigital.riosense.ui.theme.RioSenseObsidianTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var vehicleRepository: VehicleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository with persistent local storage (SharedPreferences)
        vehicleRepository = VehicleRepository(applicationContext)

        // Keep the Head Unit screen awake while the ignition/app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val telemetry by vehicleRepository.telemetry.collectAsState()
            val appLanguage by vehicleRepository.appLanguage.collectAsState()
            val engineOffTick by vehicleRepository.engineOffEventTick.collectAsState()

            val voiceReminderEngine = remember { VoiceReminderEngine(applicationContext) }

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
                    voiceReminderEngine.shutdown()
                }
            }

            // Speak bilingual Engine-OFF safety reminder (ajar doors + closed windows check)
            LaunchedEffect(engineOffTick) {
                if (engineOffTick > 0L && telemetry.engineOffVoiceReminderEnabled && !telemetry.audioAlarmMuted) {
                    voiceReminderEngine.speakEngineOffReminder(telemetry.doors, appLanguage)
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
                        onEnterPipMode = { enterFloatingPipWindow() }
                    )
                }
            }
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
