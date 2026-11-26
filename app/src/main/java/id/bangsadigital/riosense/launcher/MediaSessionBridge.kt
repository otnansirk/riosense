package id.bangsadigital.riosense.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val packageName: String? = null,
    val isPlaying: Boolean = false,
    val albumArt: Bitmap? = null,
    val hasListenerPermission: Boolean = false,
    val hasActiveSession: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val lastPositionUpdateElapsedMs: Long = 0L
)

/**
 * Zero-polling media session bridge reading live track metadata & album art from Spotify / YouTube Music
 * when Notification Access is enabled, with universal AudioManager media-key fallback.
 */
object MediaSessionBridge {

    private val _nowPlaying = MutableStateFlow(NowPlayingState())
    val nowPlaying: StateFlow<NowPlayingState> = _nowPlaying.asStateFlow()

    private var boundController: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null

    fun hasListenerPermission(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun refresh(context: Context) {
        val appCtx = context.applicationContext
        val hasPerm = hasListenerPermission(appCtx)
        if (!hasPerm) {
            unbindController()
            _nowPlaying.value = NowPlayingState(hasListenerPermission = false)
            return
        }

        val msm = appCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(appCtx, RioSenseMediaListenerService::class.java)
        val sessions = runCatching { msm?.getActiveSessions(component) }.getOrNull().orEmpty()

        val preferred = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull {
            it.metadata != null
        } ?: sessions.firstOrNull()

        bindController(preferred, hasPerm)
    }

    internal fun updateFromSessions(controllers: List<MediaController>?, hasPerm: Boolean) {
        val list = controllers.orEmpty()
        val preferred = list.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: list.firstOrNull {
            it.metadata != null
        } ?: list.firstOrNull()
        bindController(preferred, hasPerm)
    }

    private fun bindController(controller: MediaController?, hasPerm: Boolean) {
        if (boundController?.sessionToken != controller?.sessionToken) {
            unbindController()
            boundController = controller
            if (controller != null) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        publishState(controller, hasPerm)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        publishState(controller, hasPerm)
                    }

                    override fun onSessionDestroyed() {
                        unbindController()
                        _nowPlaying.value = NowPlayingState(hasListenerPermission = hasPerm)
                    }
                }
                controllerCallback = cb
                runCatching { controller.registerCallback(cb) }
            }
        }
        publishState(controller, hasPerm)
    }

    private fun unbindController() {
        val old = boundController
        val cb = controllerCallback
        if (old != null && cb != null) {
            runCatching { old.unregisterCallback(cb) }
        }
        boundController = null
        controllerCallback = null
    }

    private fun publishState(controller: MediaController?, hasPerm: Boolean) {
        if (controller == null) {
            _nowPlaying.value = NowPlayingState(hasListenerPermission = hasPerm)
            return
        }

        val meta = controller.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val rawArt = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val scaledArt = rawArt?.let { downscaleArt(it, 160) }

        val playback = controller.playbackState
        val playing = playback?.state == PlaybackState.STATE_PLAYING
        val durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val positionMs = playback?.position?.coerceAtLeast(0L) ?: 0L
        val speed = playback?.playbackSpeed ?: if (playing) 1f else 0f
        val updatedAt = playback?.lastPositionUpdateTime?.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime()

        _nowPlaying.value = NowPlayingState(
            title = title,
            artist = artist,
            packageName = controller.packageName,
            isPlaying = playing,
            albumArt = scaledArt,
            hasListenerPermission = hasPerm,
            hasActiveSession = true,
            durationMs = durationMs,
            positionMs = positionMs,
            playbackSpeed = speed,
            lastPositionUpdateElapsedMs = updatedAt
        )
    }

    private fun downscaleArt(src: Bitmap, maxPx: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxPx && h <= maxPx) return src
        val ratio = maxPx.toFloat() / maxOf(w, h).toFloat()
        val dstW = (w * ratio).toInt().coerceAtLeast(1)
        val dstH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, dstW, dstH, true)
    }

    fun togglePlayPause(context: Context) {
        val ctrl = boundController
        if (ctrl != null) {
            val isPlaying = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) ctrl.transportControls.pause() else ctrl.transportControls.play()
        } else {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun skipNext(context: Context) {
        val ctrl = boundController
        if (ctrl != null) {
            ctrl.transportControls.skipToNext()
        } else {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun skipPrevious(context: Context) {
        val ctrl = boundController
        if (ctrl != null) {
            ctrl.transportControls.skipToPrevious()
        } else {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun openMusicApp(context: Context) {
        val activePkg = _nowPlaying.value.packageName
        if (!activePkg.isNullOrBlank() && InstalledAppsRepository.launchApp(context, activePkg)) {
            return
        }
        val candidates = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube"
        )
        for (pkg in candidates) {
            if (InstalledAppsRepository.launchApp(context, pkg)) return
        }
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }
}

/**
 * Lightweight NotificationListenerService enabling Android MediaSessionManager access for
 * live Spotify / YouTube Music metadata & artwork without background polling.
 */
class RioSenseMediaListenerService : NotificationListenerService() {

    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var msm: MediaSessionManager? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        val component = ComponentName(this, RioSenseMediaListenerService::class.java)
        msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            MediaSessionBridge.updateFromSessions(controllers, hasPerm = true)
        }
        sessionsListener = listener
        runCatching { msm?.addOnActiveSessionsChangedListener(listener, component) }
        MediaSessionBridge.refresh(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Metadata updates are handled event-driven by OnActiveSessionsChangedListener & MediaController.Callback
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Metadata updates are handled event-driven by OnActiveSessionsChangedListener & MediaController.Callback
    }

    override fun onListenerDisconnected() {
        sessionsListener?.let { listener ->
            runCatching { msm?.removeOnActiveSessionsChangedListener(listener) }
        }
        sessionsListener = null
        super.onListenerDisconnected()
    }
}
