package id.bangsadigital.riosense

import android.app.PictureInPictureParams
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import id.bangsadigital.riosense.data.VehicleRepository
import id.bangsadigital.riosense.ui.RioSenseDashboardScreen
import id.bangsadigital.riosense.ui.theme.RioSenseObsidianTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vehicleRepository = VehicleRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the Head Unit screen awake while the ignition/app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val telemetry by vehicleRepository.telemetry.collectAsState()

            // Automotive alarm chime beep on Door Open / Speed Unlock / TPMS Alert / Coolant Overheat
            LaunchedEffect(
                telemetry.hasAnyCriticalAlert,
                telemetry.audioAlarmMuted,
                telemetry.isSpeedUnlockAlarm
            ) {
                if (telemetry.hasAnyCriticalAlert && !telemetry.audioAlarmMuted) {
                    val toneGen = runCatching {
                        ToneGenerator(AudioManager.STREAM_ALARM, 80)
                    }.getOrNull()

                    try {
                        while (telemetry.hasAnyCriticalAlert && !telemetry.audioAlarmMuted) {
                            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                            delay(2400L)
                        }
                    } finally {
                        toneGen?.release()
                    }
                }
            }

            RioSenseObsidianTheme {
                RioSenseDashboardScreen(
                    telemetry = telemetry,
                    repository = vehicleRepository,
                    onEnterPipMode = { enterFloatingPipWindow() }
                )
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
