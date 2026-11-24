package id.bangsadigital.riosense.voice

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import id.bangsadigital.riosense.data.VehicleRepository
import id.bangsadigital.riosense.ui.components.ControlsTab
import id.bangsadigital.riosense.ui.i18n.AppLanguage

/**
 * Executes parsed [VoiceCommand] intents natively on the Android Head Unit
 * (Google Maps, Spotify, Media Keys, Volume, RioSense UI & Vehicle Telemetry).
 */
class VoiceActionExecutor(
    private val context: Context,
    private val repository: VehicleRepository
) {
    fun execute(
        command: VoiceCommand,
        language: AppLanguage,
        onOpenControls: (ControlsTab?) -> Unit,
        onCloseMenus: () -> Unit
    ): VoiceFeedbackCard {
        val isId = language == AppLanguage.ID
        val telemetry = repository.telemetry.value
        val maintenance = repository.maintenance.value
        val errorLogs = repository.errorLogs.value

        return when (command) {
            is VoiceCommand.NavigateMaps -> {
                val ok = launchGoogleMaps(command.query, command.isSearchOnly)
                if (ok) {
                    VoiceFeedbackCard(
                        title = "Google Maps",
                        subtitle = if (isId) {
                            if (command.isSearchOnly) "Mencari: ${command.query}" else "Mengarahkan ke: ${command.query}"
                        } else {
                            if (command.isSearchOnly) "Searching: ${command.query}" else "Navigating to: ${command.query}"
                        }
                    )
                } else {
                    VoiceFeedbackCard(
                        title = "Google Maps",
                        subtitle = if (isId) "Aplikasi Maps tidak ditemukan." else "Maps app not found.",
                        isError = true
                    )
                }
            }

            VoiceCommand.OpenMaps -> {
                val ok = launchPackage("com.google.android.apps.maps")
                VoiceFeedbackCard(
                    title = "Google Maps",
                    subtitle = if (ok) {
                        if (isId) "Membuka Google Maps" else "Opening Google Maps"
                    } else {
                        if (isId) "Google Maps belum terpasang." else "Google Maps is not installed."
                    },
                    isError = !ok
                )
            }

            is VoiceCommand.PlaySpotify -> {
                val ok = launchSpotifySearchAndPlay(command.query, command.isPlaylist)
                VoiceFeedbackCard(
                    title = "Spotify",
                    subtitle = if (ok) {
                        if (isId) {
                            if (command.isPlaylist) "Memutar playlist: ${command.query}" else "Memutar: ${command.query}"
                        } else {
                            if (command.isPlaylist) "Playing playlist: ${command.query}" else "Playing: ${command.query}"
                        }
                    } else {
                        if (isId) "Spotify belum terpasang di Head Unit." else "Spotify is not installed on this Head Unit."
                    },
                    isError = !ok
                )
            }

            VoiceCommand.OpenSpotify -> {
                val ok = launchPackage("com.spotify.music")
                VoiceFeedbackCard(
                    title = "Spotify",
                    subtitle = if (ok) {
                        if (isId) "Membuka Spotify" else "Opening Spotify"
                    } else {
                        if (isId) "Spotify belum terpasang." else "Spotify is not installed."
                    },
                    isError = !ok
                )
            }

            is VoiceCommand.MediaControl -> {
                val keyCode = when (command.action) {
                    MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                    MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                }
                dispatchMediaKey(keyCode)
                val label = when (command.action) {
                    MediaAction.NEXT -> if (isId) "Lagu berikutnya" else "Next track"
                    MediaAction.PREVIOUS -> if (isId) "Lagu sebelumnya" else "Previous track"
                    MediaAction.PLAY -> if (isId) "Melanjutkan musik" else "Resuming playback"
                    MediaAction.PAUSE -> if (isId) "Musik dijeda" else "Playback paused"
                }
                VoiceFeedbackCard(title = "Media", subtitle = label)
            }

            is VoiceCommand.VolumeControl -> {
                adjustMediaVolume(command.action)
                val label = when (command.action) {
                    VolumeAction.UP -> if (isId) "Volume dinaikkan" else "Volume raised"
                    VolumeAction.DOWN -> if (isId) "Volume diturunkan" else "Volume lowered"
                    VolumeAction.MUTE -> if (isId) "Toggle mute media" else "Media mute toggled"
                }
                VoiceFeedbackCard(title = "Volume", subtitle = label)
            }

            is VoiceCommand.SetVolumePercent -> {
                val appliedPercent = setMediaVolumePercent(command.percent)
                VoiceFeedbackCard(
                    title = "Volume",
                    subtitle = if (isId) "Volume diatur ke $appliedPercent%" else "Volume set to $appliedPercent%"
                )
            }

            is VoiceCommand.QueryStatus -> buildStatusCard(
                topic = command.topic,
                isId = isId,
                telemetry = telemetry,
                maintenance = maintenance,
                errorCount = errorLogs.size,
                latestErrorTitle = errorLogs.firstOrNull()?.let { "${it.code} · ${it.title}" }
            )

            is VoiceCommand.OpenControls -> {
                onOpenControls(command.tab)
                val tabLabel = command.tab?.label ?: "Controls"
                VoiceFeedbackCard(
                    title = "RioSense",
                    subtitle = if (isId) "Membuka $tabLabel" else "Opening $tabLabel"
                )
            }

            VoiceCommand.CloseMenus -> {
                onCloseMenus()
                VoiceFeedbackCard(
                    title = "RioSense",
                    subtitle = if (isId) "Kembali ke Dashboard" else "Back to Dashboard"
                )
            }

            VoiceCommand.MinimizeApp -> {
                repository.setMinimized(true)
                VoiceFeedbackCard(
                    title = "RioSense",
                    subtitle = if (isId) "Mode mini aktif" else "Minimized mode active"
                )
            }

            is VoiceCommand.SetAlarmMute -> {
                if (telemetry.audioAlarmMuted != command.muted) {
                    repository.toggleAudioMute()
                }
                VoiceFeedbackCard(
                    title = if (isId) "Alarm" else "Chime",
                    subtitle = if (command.muted) {
                        if (isId) "Suara alarm disenyapkan" else "Alarm chime muted"
                    } else {
                        if (isId) "Suara alarm diaktifkan" else "Alarm chime enabled"
                    }
                )
            }

            is VoiceCommand.SetLanguage -> {
                repository.setAppLanguage(command.language)
                VoiceFeedbackCard(
                    title = if (command.language == AppLanguage.ID) "Bahasa" else "Language",
                    subtitle = if (command.language == AppLanguage.ID) "Diubah ke Bahasa Indonesia" else "Switched to English"
                )
            }

            is VoiceCommand.Unknown -> VoiceFeedbackCard(
                title = if (isId) "Perintah Tidak Dikenali" else "Command Not Recognized",
                subtitle = "\"${command.rawText}\"",
                detailLines = listOf(
                    if (isId) {
                        "Coba: \"Arahkan ke...\", \"Putar lagu... di Spotify\", \"Next song\", \"Cek kondisi mobil\""
                    } else {
                        "Try: \"Navigate to...\", \"Play ... on Spotify\", \"Next song\", \"Check car status\""
                    }
                ),
                isError = true
            )
        }
    }

    private fun buildStatusCard(
        topic: StatusTopic,
        isId: Boolean,
        telemetry: id.bangsadigital.riosense.model.VehicleTelemetry,
        maintenance: id.bangsadigital.riosense.model.MaintenanceState,
        errorCount: Int,
        latestErrorTitle: String?
    ): VoiceFeedbackCard {
        val nowMs = System.currentTimeMillis()
        val dueItems = maintenance.dueOrOverdueItems(nowMs)

        return when (topic) {
            StatusTopic.COOLANT -> VoiceFeedbackCard(
                title = if (isId) "Suhu Mesin (Coolant)" else "Engine Coolant",
                subtitle = "${telemetry.coolantTempC} °C · ${
                    if (telemetry.isCoolantOverheat) {
                        if (isId) "OVERHEAT" else "OVERHEAT"
                    } else {
                        if (isId) "Normal" else "Nominal"
                    }
                }",
                isError = telemetry.isCoolantOverheat
            )

            StatusTopic.BATTERY -> VoiceFeedbackCard(
                title = if (isId) "Tegangan Aki" else "Battery Voltage",
                subtitle = "${String.format("%.1f", telemetry.batteryVoltage)} V · ${
                    if (telemetry.isBatteryAlert) {
                        if (isId) "Perlu Dicek" else "Warning"
                    } else {
                        if (isId) "Normal" else "Nominal"
                    }
                }",
                isError = telemetry.isBatteryAlert
            )

            StatusTopic.TIRES -> {
                val t = telemetry.tpms
                VoiceFeedbackCard(
                    title = if (isId) "Status Ban (TPMS)" else "Tire Status (TPMS)",
                    subtitle = if (telemetry.alertTires.isEmpty()) {
                        if (isId) "Semua ban normal" else "All tires nominal"
                    } else {
                        if (isId) "${telemetry.alertTires.size} ban perlu perhatian" else "${telemetry.alertTires.size} tire alert(s)"
                    },
                    detailLines = listOf(
                        "FL ${String.format("%.1f", t.frontLeft.pressurePsi)} PSI (${t.frontLeft.tempC}°C)  ·  FR ${String.format("%.1f", t.frontRight.pressurePsi)} PSI (${t.frontRight.tempC}°C)",
                        "RL ${String.format("%.1f", t.rearLeft.pressurePsi)} PSI (${t.rearLeft.tempC}°C)  ·  RR ${String.format("%.1f", t.rearRight.pressurePsi)} PSI (${t.rearRight.tempC}°C)"
                    ),
                    isError = telemetry.alertTires.isNotEmpty()
                )
            }

            StatusTopic.SERVICE -> VoiceFeedbackCard(
                title = if (isId) "Jadwal Servis" else "Maintenance Status",
                subtitle = if (dueItems.isEmpty()) {
                    if (isId) "Semua part masih aman" else "All maintenance schedules OK"
                } else {
                    val top = dueItems.first()
                    "${top.name} (${top.quickSummary(nowMs, maintenance.currentOdometerKm)})"
                },
                detailLines = dueItems.drop(1).take(2).map { item ->
                    "${item.name} · ${item.quickSummary(nowMs, maintenance.currentOdometerKm)}"
                }
            )

            StatusTopic.ERRORS -> VoiceFeedbackCard(
                title = "OBD Error Log",
                subtitle = if (errorCount == 0) {
                    if (isId) "Tidak ada error terdeteksi" else "No OBD faults detected"
                } else {
                    if (isId) "$errorCount error terdeteksi" else "$errorCount fault(s) detected"
                },
                detailLines = listOfNotNull(latestErrorTitle),
                isError = errorCount > 0
            )

            StatusTopic.OVERVIEW -> {
                val doorStateText = if (telemetry.doors.anyDoorOpen) {
                    if (isId) "Ada pintu terbuka" else "Door ajar"
                } else if (telemetry.centralLocked) {
                    if (isId) "Terkunci" else "Locked"
                } else {
                    if (isId) "Belum terkunci" else "Unlocked"
                }
                val serviceHint = dueItems.firstOrNull()?.let {
                    "${it.name}: ${it.quickSummary(nowMs, maintenance.currentOdometerKm)}"
                } ?: if (isId) "Jadwal servis aman" else "Service OK"

                VoiceFeedbackCard(
                    title = if (isId) "Kondisi Kia Rio" else "Kia Rio Status",
                    subtitle = if (telemetry.hasAnyCriticalAlert) {
                        if (isId) "Ada peringatan aktif" else "Active alerts present"
                    } else {
                        if (isId) "Semua sistem normal" else "All systems nominal"
                    },
                    detailLines = listOf(
                        "Coolant ${telemetry.coolantTempC}°C  ·  ${if (isId) "Aki" else "Battery"} ${String.format("%.1f", telemetry.batteryVoltage)}V  ·  $doorStateText",
                        serviceHint
                    ),
                    isError = telemetry.hasAnyCriticalAlert
                )
            }
        }
    }

    private fun launchGoogleMaps(query: String, isSearchOnly: Boolean): Boolean {
        val encoded = Uri.encode(query)
        val uri = if (isSearchOnly) {
            Uri.parse("geo:0,0?q=$encoded")
        } else {
            Uri.parse("google.navigation:q=$encoded")
        }
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(mapsIntent)
            true
        }.getOrElse {
            // Fallback to any installed map app
            runCatching {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            }.getOrDefault(false)
        }
    }

    private fun launchSpotifySearchAndPlay(query: String, isPlaylist: Boolean): Boolean {
        val focus = if (isPlaylist) {
            MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE
        } else {
            MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
        }
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage("com.spotify.music")
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, focus)
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            // Fallback: generic media play-from-search (e.g. YouTube Music)
            runCatching {
                val generic = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, focus)
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(generic)
                true
            }.getOrDefault(false)
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun adjustMediaVolume(action: VolumeAction) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val direction = when (action) {
            VolumeAction.UP -> AudioManager.ADJUST_RAISE
            VolumeAction.DOWN -> AudioManager.ADJUST_LOWER
            VolumeAction.MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        // Step twice for noticeable in-car volume change on UP/DOWN
        val steps = if (action == VolumeAction.MUTE) 1 else 2
        repeat(steps) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun setMediaVolumePercent(percent: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return clamped
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetIndex = kotlin.math.round((clamped / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
        return clamped
    }
}
