package id.bangsadigital.riosense.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import id.bangsadigital.riosense.R
import id.bangsadigital.riosense.model.DoorState
import id.bangsadigital.riosense.model.WelcomeSoundMode
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import java.util.Calendar

/**
 * Offline Neural AI Voice reminder player for Kia Rio UB Engine-OFF safety announcements.
 * Plays pre-rendered expressive Indonesian (GadisNeural) & English (JennyNeural) MP3 clips
 * bundled in res/raw/ so voice quality is natural and consistent across all Android Head Units.
 */
class VoiceReminderEngine(private val context: Context) {

    private var activePlayer: MediaPlayer? = null

    private val speechAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun speakEngineOffReminder(doors: DoorState, language: AppLanguage) {
        val voiceResId = selectClipResId(doors, language)
        playRawClip(R.raw.voice_chime_intro, nextResId = voiceResId)
    }

    fun playListenChime() {
        playRawClip(R.raw.voice_listen_chime)
    }

    fun playAckChime() {
        playRawClip(R.raw.voice_ack_chime)
    }

    fun playWelcomeGreeting(mode: WelcomeSoundMode, language: AppLanguage) {
        when (mode) {
            WelcomeSoundMode.OFF -> Unit
            WelcomeSoundMode.CHIME -> playRawClip(R.raw.welcome_startup_chime)
            WelcomeSoundMode.VOICE -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val greetingResId = selectWelcomeClipResId(hour, language)
                playRawClip(R.raw.welcome_startup_chime, nextResId = greetingResId)
            }
        }
    }

    private fun playRawClip(@RawRes resId: Int, @RawRes nextResId: Int? = null) {
        releaseActivePlayer()
        runCatching {
            val afd = context.resources.openRawResourceFd(resId) ?: return
            val player = MediaPlayer().apply {
                setAudioAttributes(speechAudioAttributes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    if (activePlayer === mp) {
                        activePlayer = null
                        if (nextResId != null) {
                            playRawClip(nextResId, nextResId = null)
                        }
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    runCatching { mp.release() }
                    if (activePlayer === mp) activePlayer = null
                    true
                }
                prepare()
                start()
            }
            activePlayer = player
        }
    }

    private fun releaseActivePlayer() {
        activePlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        activePlayer = null
    }

    fun shutdown() {
        releaseActivePlayer()
    }

    companion object {
        @RawRes
        fun selectWelcomeClipResId(hourOfDay: Int, language: AppLanguage): Int {
            return when (language) {
                AppLanguage.ID -> when (hourOfDay) {
                    in 5..10 -> R.raw.welcome_id_morning
                    in 11..14 -> R.raw.welcome_id_afternoon
                    in 15..17 -> R.raw.welcome_id_evening
                    else -> R.raw.welcome_id_night
                }
                AppLanguage.EN -> when (hourOfDay) {
                    in 5..10 -> R.raw.welcome_en_morning
                    in 11..14 -> R.raw.welcome_en_afternoon
                    in 15..17 -> R.raw.welcome_en_evening
                    else -> R.raw.welcome_en_night
                }
            }
        }

        @RawRes
        fun selectClipResId(doors: DoorState, language: AppLanguage): Int {
            val openKeys = buildList {
                if (doors.frontLeftOpen) add("FL")
                if (doors.frontRightOpen) add("FR")
                if (doors.rearLeftOpen) add("RL")
                if (doors.rearRightOpen) add("RR")
                if (doors.trunkOpen) add("TRUNK")
            }

            return when (language) {
                AppLanguage.ID -> when {
                    openKeys.isEmpty() -> R.raw.voice_id_all_closed
                    openKeys.size > 1 -> R.raw.voice_id_doors_multi
                    else -> when (openKeys.first()) {
                        "FL" -> R.raw.voice_id_door_fl
                        "FR" -> R.raw.voice_id_door_fr
                        "RL" -> R.raw.voice_id_door_rl
                        "RR" -> R.raw.voice_id_door_rr
                        else -> R.raw.voice_id_door_trunk
                    }
                }

                AppLanguage.EN -> when {
                    openKeys.isEmpty() -> R.raw.voice_en_all_closed
                    openKeys.size > 1 -> R.raw.voice_en_doors_multi
                    else -> when (openKeys.first()) {
                        "FL" -> R.raw.voice_en_door_fl
                        "FR" -> R.raw.voice_en_door_fr
                        "RL" -> R.raw.voice_en_door_rl
                        "RR" -> R.raw.voice_en_door_rr
                        else -> R.raw.voice_en_door_trunk
                    }
                }
            }
        }
    }
}
