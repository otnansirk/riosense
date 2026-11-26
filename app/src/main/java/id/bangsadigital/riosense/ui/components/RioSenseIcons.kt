package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Canonical minimalist 8-tooth Obsidian gear vector icon used across TopBar, Quick-Jump,
 * App Drawer header, and Controls Sheet so settings icons stay visually identical everywhere.
 */
@Composable
fun SettingsGearVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val minDim = size.minDimension
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val stroke = 1.45.dp.toPx()
        val rRing = minDim * 0.29f
        val rToothIn = minDim * 0.32f
        val rToothOut = minDim * 0.46f

        for (i in 0 until 8) {
            val angle = i * (PI / 4.0)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            drawLine(
                color = tint,
                start = Offset(cx + cosA * rToothIn, cy + sinA * rToothIn),
                end = Offset(cx + cosA * rToothOut, cy + sinA * rToothOut),
                strokeWidth = 1.65.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = tint,
            radius = rRing,
            center = Offset(cx, cy),
            style = Stroke(width = stroke)
        )
    }
}

/**
 * Canonical minimalist winding-route vector icon for GPS trip tracking & Trip History.
 * Displays an S-curve route with start/end waypoints, optionally crossed by a crisp diagonal slash.
 */
@Composable
fun TripRouteVectorIcon(
    tint: Color,
    slashed: Boolean = false,
    slashBgColor: Color = ObsidianColors.DeepObsidian,
    slashColor: Color = ObsidianColors.MutedSilver,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()

        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.78f)
            cubicTo(
                w * 0.22f, h * 0.45f,
                w * 0.78f, h * 0.55f,
                w * 0.78f, h * 0.22f
            )
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val dotRadius = 1.7.dp.toPx()
        drawCircle(
            color = tint,
            radius = dotRadius,
            center = Offset(w * 0.22f, h * 0.78f)
        )
        drawCircle(
            color = tint,
            radius = dotRadius,
            center = Offset(w * 0.78f, h * 0.22f)
        )

        if (slashed) {
            drawLine(
                color = slashBgColor,
                start = Offset(w * 0.14f, h * 0.14f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = stroke * 2.2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = slashColor,
                start = Offset(w * 0.14f, h * 0.14f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Canonical minimalist open-end wrench vector icon for Service & Maintenance shortcuts,
 * matching the stroke weight of [SettingsGearVectorIcon] and [TripRouteVectorIcon].
 */
@Composable
fun ServiceWrenchVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val jawStroke = 1.45.dp.toPx()
        val handleStroke = 1.75.dp.toPx()

        rotate(degrees = 45f) {
            val jaw = Path().apply {
                moveTo(w * 0.35f, h * 0.13f)
                lineTo(w * 0.28f, h * 0.26f)
                lineTo(w * 0.35f, h * 0.42f)
                lineTo(w * 0.50f, h * 0.45f)
                lineTo(w * 0.65f, h * 0.42f)
                lineTo(w * 0.72f, h * 0.26f)
                lineTo(w * 0.65f, h * 0.13f)
            }
            drawPath(
                path = jaw,
                color = tint,
                style = Stroke(
                    width = jawStroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.50f, h * 0.45f),
                end = Offset(w * 0.50f, h * 0.87f),
                strokeWidth = handleStroke,
                cap = StrokeCap.Round
            )
        }
    }
}
