package id.bangsadigital.riosense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Monochrome Luxury Palette: Pure blend of Black, Obsidian, Charcoal, and Dark Grey.
 * Only the center 3D vehicle stage adapts to the user's selected paint finish.
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
    val ChampagneAccent = Color(0xFFB8C0CC)       // Brushed Silver/Grey accent
    val MutedSilver = Color(0xFF858D9D)           // Secondary Cool Grey text
    val DimSilver = Color(0xFF4C5260)             // Dimmed Charcoal Grey text

    // Status indicators stay within Monochrome Silver/Grey & Deep Matte Alert tones
    val EmeraldSafe = Color(0xFF9AA3B2)           // Brushed Steel Grey for nominal states
    val IceCyan = Color(0xFFD8DEE9)               // Crisp Platinum for active items
    val AmberWarning = Color(0xFFD0D6E0)          // High-contrast Silver highlight
    val CrimsonAlert = Color(0xFFE53935)          // Subtle Automotive Warning Indicator
    val AlertSurface = Color(0xFF1F1416)          // Ultra-dark Charcoal-Red alert surface
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
