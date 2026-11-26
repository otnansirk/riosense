package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.bangsadigital.riosense.launcher.AudioSpectrumTap
import id.bangsadigital.riosense.launcher.SPECTRUM_BAR_COUNT
import id.bangsadigital.riosense.launcher.WeatherCondition
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure Canvas 0-KB-font Pixel Dot-Matrix renderer inspired by Nothing OS / classic automotive HUDs.
 * Renders razor-sharp circular dots at any DPI without shipping external font binaries.
 */
@Composable
fun DotMatrixText(
    text: String,
    dotSize: Dp,
    dotGap: Dp = dotSize * 0.45f,
    charGap: Dp = dotSize * 1.4f,
    color: Color = ObsidianColors.PureSilver,
    inactiveDotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val upper = text.uppercase()
    val glyphs = remember(upper) {
        upper.map { dotMatrixGlyph(it) }
    }

    val cellStep = dotSize + dotGap
    val totalCols = glyphs.sumOf { it.cols }
    val gapCount = (glyphs.size - 1).coerceAtLeast(0)

    val totalWidth = if (totalCols == 0) {
        0.dp
    } else {
        (dotSize * totalCols) + (dotGap * (totalCols - glyphs.size).coerceAtLeast(0)) + (charGap * gapCount)
    }
    val totalHeight = (dotSize * 7) + (dotGap * 6)

    Canvas(modifier = modifier.size(width = totalWidth, height = totalHeight)) {
        val dotPx = dotSize.toPx()
        val radiusPx = dotPx * 0.5f
        val stepPx = cellStep.toPx()
        val charGapPx = charGap.toPx()
        val dotGapPx = dotGap.toPx()

        var cursorX = 0f
        glyphs.forEachIndexed { idx, glyph ->
            for (r in 0 until 7) {
                val rowMask = glyph.rows[r]
                val cy = r * stepPx + radiusPx
                for (c in 0 until glyph.cols) {
                    val bit = (rowMask shr (glyph.cols - 1 - c)) and 1
                    val cx = cursorX + c * stepPx + radiusPx
                    if (bit == 1) {
                        drawCircle(color = color, radius = radiusPx, center = Offset(cx, cy))
                    } else if (inactiveDotColor != null) {
                        drawCircle(color = inactiveDotColor, radius = radiusPx, center = Offset(cx, cy))
                    }
                }
            }
            val glyphWidthPx = glyph.cols * dotPx + (glyph.cols - 1).coerceAtLeast(0) * dotGapPx
            cursorX += glyphWidthPx
            if (idx < glyphs.lastIndex) {
                cursorX += charGapPx
            }
        }
    }
}

/**
 * Pure Canvas Dot-Matrix weather icon rendered strictly in RioSense Obsidian palette.
 */
@Composable
fun DotMatrixWeatherIcon(
    condition: WeatherCondition,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cols = 12
        val rows = 10
        val step = minOf(size.width / cols, size.height / rows)
        val radius = step * 0.36f
        val originX = (size.width - step * cols) * 0.5f + step * 0.5f
        val originY = (size.height - step * rows) * 0.5f + step * 0.5f

        fun dot(c: Int, r: Int, tint: Color) {
            drawCircle(
                color = tint,
                radius = radius,
                center = Offset(originX + c * step, originY + r * step)
            )
        }

        val pure = ObsidianColors.PureSilver
        val muted = ObsidianColors.MutedSilver
        val sunTint = Color(0xFFE6C88A)
        val rainTint = ObsidianColors.CobaltAccent
        val stormTint = ObsidianColors.AmberCaution

        when (condition) {
            WeatherCondition.SUNNY -> {
                val sunPattern = arrayOf(
                    ".....##.....",
                    "..#......#..",
                    "....####....",
                    "...######...",
                    ".#.######.#.",
                    ".#.######.#.",
                    "...######...",
                    "....####....",
                    "..#......#..",
                    ".....##....."
                )
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if (sunPattern[r][c] == '#') {
                            val isCore = r in 2..7 && c in 3..8
                            dot(c, r, if (isCore) pure else sunTint.copy(alpha = 0.90f))
                        }
                    }
                }
            }

            WeatherCondition.PARTLY_CLOUDY -> {
                val pattern = arrayOf(
                    "...#........",
                    ".#.S.#......",
                    "..SSS.......",
                    ".SSSS####...",
                    "..SS######..",
                    "...########.",
                    "..#########.",
                    ".##########.",
                    "............",
                    "............"
                )
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        when (pattern[r][c]) {
                            'S' -> dot(c, r, sunTint)
                            '#' -> dot(c, r, pure)
                        }
                    }
                }
            }

            WeatherCondition.CLOUDY -> {
                val pattern = arrayOf(
                    "............",
                    "....####....",
                    "...######...",
                    "..########..",
                    ".##########.",
                    "############",
                    "############",
                    ".##########.",
                    "............",
                    "............"
                )
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if (pattern[r][c] == '#') {
                            dot(c, r, if (r <= 4) pure else muted)
                        }
                    }
                }
            }

            WeatherCondition.RAIN -> {
                val pattern = arrayOf(
                    "....####....",
                    "..########..",
                    ".##########.",
                    "############",
                    "############",
                    ".##########.",
                    "............",
                    "..R..R..R...",
                    ".R..R..R....",
                    "............"
                )
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        when (pattern[r][c]) {
                            '#' -> dot(c, r, pure)
                            'R' -> dot(c, r, rainTint)
                        }
                    }
                }
            }

            WeatherCondition.STORM -> {
                val pattern = arrayOf(
                    "....####....",
                    "..########..",
                    ".##########.",
                    "############",
                    ".##########.",
                    ".....BB.....",
                    "....BB......",
                    "....BBB.....",
                    ".....BB.....",
                    "....B......."
                )
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        when (pattern[r][c]) {
                            '#' -> dot(c, r, muted)
                            'B' -> dot(c, r, stormTint)
                        }
                    }
                }
            }

            WeatherCondition.FOG -> {
                val pattern = arrayOf(
                    "............",
                    "..########..",
                    "............",
                    ".##########.",
                    "............",
                    "..########..",
                    "............",
                    "...######...",
                    "............",
                    "............"
                )
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if (pattern[r][c] == '#') {
                            dot(c, r, if (r <= 3) pure.copy(alpha = 0.85f) else muted)
                        }
                    }
                }
            }
        }
    }
}

private data class DotGlyph(val cols: Int, val rows: IntArray)

private fun dotMatrixGlyph(ch: Char): DotGlyph = when (ch) {
    '0' -> glyph5(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110)
    '1' -> glyph5(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110)
    '2' -> glyph5(0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10000, 0b11111)
    '3' -> glyph5(0b01110, 0b10001, 0b00001, 0b00110, 0b00001, 0b10001, 0b01110)
    '4' -> glyph5(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010)
    '5' -> glyph5(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110)
    '6' -> glyph5(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110)
    '7' -> glyph5(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000)
    '8' -> glyph5(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110)
    '9' -> glyph5(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100)

    'A' -> glyph5(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001)
    'B' -> glyph5(0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110)
    'C' -> glyph5(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110)
    'D' -> glyph5(0b11100, 0b10010, 0b10001, 0b10001, 0b10001, 0b10010, 0b11100)
    'E' -> glyph5(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111)
    'F' -> glyph5(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000)
    'G' -> glyph5(0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01110)
    'H' -> glyph5(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001)
    'I' -> glyph3(0b111, 0b010, 0b010, 0b010, 0b010, 0b010, 0b111)
    'J' -> glyph5(0b00111, 0b00010, 0b00010, 0b00010, 0b10010, 0b10010, 0b01100)
    'K' -> glyph5(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001)
    'L' -> glyph5(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111)
    'M' -> glyph5(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001)
    'N' -> glyph5(0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001)
    'O' -> glyph5(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110)
    'P' -> glyph5(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000)
    'Q' -> glyph5(0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101)
    'R' -> glyph5(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001)
    'S' -> glyph5(0b01110, 0b10001, 0b10000, 0b01110, 0b00001, 0b10001, 0b01110)
    'T' -> glyph5(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100)
    'U' -> glyph5(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110)
    'V' -> glyph5(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100)
    'W' -> glyph5(0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001)
    'X' -> glyph5(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001)
    'Y' -> glyph5(0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100)
    'Z' -> glyph5(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111)

    ':' -> DotGlyph(1, intArrayOf(0, 0, 1, 0, 1, 0, 0))
    '.' -> DotGlyph(2, intArrayOf(0, 0, 0, 0, 0, 0b11, 0b11))
    ',' -> DotGlyph(2, intArrayOf(0, 0, 0, 0, 0b01, 0b01, 0b10))
    '°' -> glyph3(0b010, 0b101, 0b010, 0b000, 0b000, 0b000, 0b000)
    '-' -> glyph3(0b000, 0b000, 0b000, 0b111, 0b000, 0b000, 0b000)
    '+' -> glyph3(0b000, 0b010, 0b010, 0b111, 0b010, 0b010, 0b000)
    '/' -> glyph3(0b001, 0b001, 0b010, 0b010, 0b010, 0b100, 0b100)
    '%' -> glyph5(0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011)
    ' ' -> DotGlyph(2, IntArray(7) { 0 })
    else -> DotGlyph(3, IntArray(7) { 0 })
}

private fun glyph5(r0: Int, r1: Int, r2: Int, r3: Int, r4: Int, r5: Int, r6: Int): DotGlyph =
    DotGlyph(5, intArrayOf(r0, r1, r2, r3, r4, r5, r6))

private fun glyph3(r0: Int, r1: Int, r2: Int, r3: Int, r4: Int, r5: Int, r6: Int): DotGlyph =
    DotGlyph(3, intArrayOf(r0, r1, r2, r3, r4, r5, r6))

/**
 * Pure Canvas 24-band Dot-Matrix audio spectrum visualizer synchronized with [AudioSpectrumTap]
 * FFT output when available, falling back smoothly to a beat-reactive organic wave when
 * microphone/audio-tap access is unavailable or DSP bypasses Android's Visualizer effect.
 */
@Composable
fun DotMatrixAudioSpectrum(
    isPlaying: Boolean,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val tap = remember(context) { AudioSpectrumTap(context) }

    DisposableEffect(tap, isVisible, isResumed, isPlaying) {
        if (isVisible && isResumed && isPlaying) {
            tap.start()
        } else {
            tap.stop()
        }
        onDispose {
            tap.stop()
        }
    }

    val targetBuf = remember { FloatArray(SPECTRUM_BAR_COUNT) }
    val smoothed = remember { FloatArray(SPECTRUM_BAR_COUNT) }
    val peaks = remember { FloatArray(SPECTRUM_BAR_COUNT) }
    val peakHoldSec = remember { FloatArray(SPECTRUM_BAR_COUNT) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isVisible, isResumed, isPlaying) {
        if (!(isVisible && isResumed)) return@LaunchedEffect

        var prevFrameMs = -1L
        var elapsedSec = 0f

        while (isPlaying || hasResidualEnergy(smoothed, peaks)) {
            withFrameMillis { frameMs ->
                val dtMs = if (prevFrameMs < 0L) 16L else (frameMs - prevFrameMs).coerceIn(1L, 64L)
                prevFrameMs = frameMs
                val dt = dtMs / 1000f
                elapsedSec += dt

                if (!isPlaying) {
                    targetBuf.fill(0f)
                } else if (tap.hasRecentLiveSignal()) {
                    tap.copyTargetsInto(targetBuf)
                } else {
                    synthesizeBeatSpectrum(elapsedSec, targetBuf)
                }

                val attackAlpha = 1f - exp(-22f * dt)
                val decayAlpha = 1f - exp(-7.5f * dt)

                for (i in 0 until SPECTRUM_BAR_COUNT) {
                    val target = targetBuf[i]
                    val cur = smoothed[i]
                    val alpha = if (target > cur) attackAlpha else decayAlpha
                    val next = (cur + (target - cur) * alpha).coerceIn(0f, 1f)
                    smoothed[i] = if (next < 0.002f && target == 0f) 0f else next

                    if (smoothed[i] >= peaks[i]) {
                        peaks[i] = smoothed[i]
                        peakHoldSec[i] = 0.22f
                    } else if (peakHoldSec[i] > 0f) {
                        peakHoldSec[i] = (peakHoldSec[i] - dt).coerceAtLeast(0f)
                    } else {
                        val decayedPeak = (peaks[i] - 0.90f * dt).coerceAtLeast(smoothed[i])
                        peaks[i] = if (decayedPeak < 0.002f && smoothed[i] == 0f) 0f else decayedPeak
                    }
                }
                frameTick++
            }
        }
    }

    Canvas(modifier = modifier) {
        // Read frameTick so Compose invalidates only the draw phase on each animation frame.
        if (frameTick < 0) return@Canvas

        val cols = SPECTRUM_BAR_COUNT
        val rows = 6
        val colGap = 2.5.dp.toPx()
        val rowGap = 2.dp.toPx()
        val dotW = ((size.width - colGap * (cols - 1)) / cols).coerceAtLeast(1f)
        val dotH = ((size.height - rowGap * (rows - 1)) / rows).coerceAtLeast(1f)
        val corner = CornerRadius(minOf(dotW, dotH) * 0.45f)
        val unlitColor = Color(0xFF1A1E28)
        val peakColor = ObsidianColors.PureSilver.copy(alpha = 0.92f)

        for (c in 0 until cols) {
            val x = c * (dotW + colGap)
            val scaled = smoothed[c] * rows

            for (r in 0 until rows) {
                // Row 0 is bottom-most, row (rows - 1) is top-most.
                val y = size.height - dotH - r * (dotH + rowGap)
                val dotOffset = Offset(x, y)
                val dotSize = Size(dotW, dotH)

                drawRoundRect(
                    color = unlitColor,
                    topLeft = dotOffset,
                    size = dotSize,
                    cornerRadius = corner
                )

                val fill = (scaled - r).coerceIn(0f, 1f)
                if (fill > 0.02f) {
                    val bandTint = when {
                        r <= 1 -> ObsidianColors.CobaltAccent.copy(alpha = 0.85f)
                        r <= 3 -> ObsidianColors.PureSilver.copy(alpha = 0.92f)
                        else -> ObsidianColors.ChampagneAccent
                    }
                    drawRoundRect(
                        color = bandTint.copy(alpha = bandTint.alpha * fill),
                        topLeft = dotOffset,
                        size = dotSize,
                        cornerRadius = corner
                    )
                }
            }

            if (peaks[c] > 0.14f) {
                val peakRow = (peaks[c] * rows).toInt().coerceIn(0, rows - 1)
                val topLitRow = scaled.toInt()
                if (peakRow > topLitRow) {
                    val py = size.height - dotH - peakRow * (dotH + rowGap)
                    drawRoundRect(
                        color = peakColor,
                        topLeft = Offset(x, py),
                        size = Size(dotW, dotH),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

private fun hasResidualEnergy(smoothed: FloatArray, peaks: FloatArray): Boolean {
    for (i in smoothed.indices) {
        if (smoothed[i] > 0.008f || peaks[i] > 0.008f) return true
    }
    return false
}

private fun synthesizeBeatSpectrum(tSec: Float, out: FloatArray) {
    val twoPi = (2.0 * PI).toFloat()
    // 118 BPM primary beat pulse + sub-beat groove
    val beatFreq = 118f / 60f
    val beatPhase = (tSec * beatFreq) % 1f
    val kickEnv = (1f - beatPhase).coerceIn(0f, 1f).pow(2.4f)
    val snareEnv = ((1f - ((beatPhase + 0.5f) % 1f)).coerceIn(0f, 1f)).pow(3.2f)

    for (i in 0 until out.size) {
        val normPos = i.toFloat() / (out.size - 1).coerceAtLeast(1)
        val waveA = 0.5f + 0.5f * sin(twoPi * (tSec * (1.8f + i * 0.13f) + i * 0.27f))
        val waveB = 0.5f + 0.5f * sin(twoPi * (tSec * (3.1f + (out.size - i) * 0.09f) - i * 0.19f))
        val shimmer = abs(sin(twoPi * (tSec * 5.2f + i * 0.41f)))

        val bandEnergy = when {
            i <= 5 -> {
                val bassCurve = 1f - (i / 6f) * 0.28f
                (0.24f + 0.62f * kickEnv + 0.14f * waveA) * bassCurve
            }
            i <= 15 -> {
                val centerBias = 1f - abs(normPos - 0.46f) * 0.45f
                (0.18f + 0.44f * waveA + 0.26f * waveB + 0.22f * snareEnv) * centerBias
            }
            else -> {
                val trebleRoll = 1f - ((i - 16) / 8f) * 0.35f
                (0.10f + 0.34f * shimmer + 0.32f * snareEnv + 0.16f * waveB) * trebleRoll
            }
        }
        out[i] = bandEnergy.coerceIn(0.04f, 0.96f)
    }
}
