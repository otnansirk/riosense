package id.bangsadigital.riosense.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

const val SPECTRUM_BAR_COUNT = 24

/**
 * Low-overhead FFT tap on the global Android audio output mix (session 0) using [Visualizer].
 * Falls back gracefully without throwing when `RECORD_AUDIO` permission is not granted or when
 * the head unit's audio DSP bypasses Android's Visualizer effect.
 */
class AudioSpectrumTap(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val rawBands = FloatArray(SPECTRUM_BAR_COUNT)

    @Volatile
    private var lastLiveFftMs: Long = 0L

    private var visualizer: Visualizer? = null

    fun start() {
        if (visualizer != null) return

        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        runCatching {
            val vis = Visualizer(0).apply {
                enabled = false
                val range = Visualizer.getCaptureSizeRange()
                val targetSize = 256.coerceIn(range[0], range[1])
                captureSize = targetSize
                runCatching { scalingMode = Visualizer.SCALING_MODE_NORMALIZED }
                val rate = (Visualizer.getMaxCaptureRate() / 2).coerceAtMost(20000)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            processFftFrame(fft)
                        }
                    },
                    rate,
                    false,
                    true
                )
                enabled = true
            }
            visualizer = vis
        }.onFailure {
            stop()
        }
    }

    fun stop() {
        val vis = visualizer
        visualizer = null
        if (vis != null) {
            runCatching { vis.setDataCaptureListener(null, 0, false, false) }
            runCatching { vis.enabled = false }
            runCatching { vis.release() }
        }
        lastLiveFftMs = 0L
        synchronized(lock) {
            rawBands.fill(0f)
        }
    }

    fun hasRecentLiveSignal(windowMs: Long = 1200L): Boolean {
        val last = lastLiveFftMs
        return last > 0L && (SystemClock.elapsedRealtime() - last) <= windowMs
    }

    fun copyTargetsInto(dst: FloatArray) {
        val count = minOf(rawBands.size, dst.size)
        synchronized(lock) {
            for (i in 0 until count) {
                dst[i] = rawBands[i]
            }
        }
    }

    private fun processFftFrame(fft: ByteArray?) {
        if (fft == null || fft.size < 8) return

        val binCount = fft.size / 2
        val maxBin = binCount - 1
        if (maxBin < 1) return

        var hasEnergy = false
        synchronized(lock) {
            for (i in 0 until SPECTRUM_BAR_COUNT) {
                val startBin = maxBin.toDouble()
                    .pow(i.toDouble() / SPECTRUM_BAR_COUNT)
                    .roundToInt()
                    .coerceIn(1, maxBin)
                val endBin = maxBin.toDouble()
                    .pow((i + 1).toDouble() / SPECTRUM_BAR_COUNT)
                    .roundToInt()
                    .coerceIn(startBin, maxBin)

                var peakMag = 0f
                for (k in startBin..endBin) {
                    val re = fft[2 * k].toFloat()
                    val im = fft[2 * k + 1].toFloat()
                    val mag = hypot(re, im)
                    if (mag > peakMag) peakMag = mag
                }

                val db = if (peakMag > 0.5f) (20.0 * log10(peakMag.toDouble())).toFloat() else 0f
                val tilt = 0.85f + 0.45f * (i.toFloat() / (SPECTRUM_BAR_COUNT - 1))
                val norm = ((db - 4f) / 34f * tilt).coerceIn(0f, 1f)
                rawBands[i] = norm
                if (norm > 0.02f) hasEnergy = true
            }
        }
        if (hasEnergy) {
            lastLiveFftMs = SystemClock.elapsedRealtime()
        }
    }
}
