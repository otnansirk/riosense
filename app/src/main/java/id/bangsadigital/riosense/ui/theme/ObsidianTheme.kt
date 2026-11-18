package id.bangsadigital.riosense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palet Monochrome Luxury: Murni kombinasi Black, Obsidian, Charcoal, dan Dark Grey.
 * Hanya mobil 3D di tengah yang mengikuti warna cat pilihan user.
 */
object ObsidianColors {
    val DeepObsidian = Color(0xFF050507)          // Pure Matte Black background
    val CarbonSurface = Color(0xFF0B0C0E)         // Deep Carbon Black
    val SmokedGlass = Color(0xE6111317)           // Dark Grey Card Surface
    val SmokedGlassElevated = Color(0xF2171A20)   // Elevated Dark Charcoal
    val DarkGreyButton = Color(0xFF1E2229)        // Button Dark Grey
    val DarkGreyActive = Color(0xFF2C313B)        // Active Control Dark Grey
    val MetallicBorder = Color(0xFF262A33)        // Subtle Gunmetal Border
    val HighlightBorder = Color(0xFF4A5160)       // Crisp Brushed Steel Border

    val PureSilver = Color(0xFFECEFF4)            // Primary Platinum White/Silver text
    val ChampagneAccent = Color(0xFFB8C0CC)       // Brushed Silver/Grey accent (menggantikan gold/cyan)
    val MutedSilver = Color(0xFF858D9D)           // Secondary Cool Grey text
    val DimSilver = Color(0xFF4C5260)             // Dimmed Charcoal Grey text

    // Indikator status tetap dalam spektrum Monochrome Silver/Grey & Deep Matte Alert
    val EmeraldSafe = Color(0xFF9AA3B2)           // Brushed Steel Grey untuk status normal
    val IceCyan = Color(0xFFD8DEE9)               // Crisp Platinum untuk elemen aktif
    val AmberWarning = Color(0xFFD0D6E0)          // High-contrast Silver untuk highlight
    val CrimsonAlert = Color(0xFFE53935)          // Subtle Automotive Warning Indicator
    val AlertSurface = Color(0xFF1F1416)          // Dark Charcoal-Red tint sangat gelap
}

private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianColors.IceCyan,
    secondary = ObsidianColors.ChampagneAccent,
    tertiary = ObsidianColors.EmeraldSafe,
    background = ObsidianColors.DeepObsidian,
    surface = ObsidianColors.CarbonSurface,
    onPrimary = Color.Black,
    onBackground = ObsidianColors.PureSilver,
    onSurface = ObsidianColors.PureSilver,
    error = ObsidianColors.CrimsonAlert
)

@Composable
fun RioSenseObsidianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        content = content
    )
}
