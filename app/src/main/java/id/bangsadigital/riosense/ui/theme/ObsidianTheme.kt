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

    // Locked Global Semantic Palette (Low-Contrast Premium Automotive Tones)
    val EmeraldSafe = Color(0xFF9AA3B2)           // Brushed Steel Grey for subtle nominal states
    val EmeraldNominal = Color(0xFF68D391)        // Refined Soft Emerald (Connected, Live, Installed, Success)
    val CobaltAccent = Color(0xFF63B3ED)          // Refined Soft Cobalt-Blue (Listening, Simulator, Download, Custom)
    val CobaltSurface = Color(0xFF152536)         // Subtle Matte Cobalt active surface
    val CobaltGlassTop = Color(0xDE193B5A)        // Frosted Cobalt Glass (top) for active floating mic
    val CobaltGlassBottom = Color(0xCC0E2236)     // Frosted Cobalt Glass (bottom) for active floating mic
    val IceCyan = Color(0xFFD8DEE9)               // Crisp Platinum for active items
    val AmberWarning = Color(0xFFD0D6E0)          // High-contrast Silver highlight
    val AmberCaution = Color(0xFFE6A23C)          // Refined Warm Amber (Warnings, Service Due Soon)
    val CrimsonAlert = Color(0xFFE53935)          // Locked Signature Automotive Crimson (Alerts, Listening, Delete)
    val AlertSurface = Color(0xFF1F1416)          // Ultra-dark Charcoal-Red alert surface
    val CrimsonSurface = Color(0xFF261317)        // Subtle Matte Crimson pill/active surface
    val CrimsonGlassTop = Color(0xDE3B161B)       // Frosted Crimson Glass (top) for active floating mic
    val CrimsonGlassBottom = Color(0xCC210D10)    // Frosted Crimson Glass (bottom) for active floating mic

    // Locked Subtle Container & Border Tones
    val ObsidianPanel = Color(0xFF0E1016)         // Low-contrast Settings group container
    val ObsidianSubPanel = Color(0xFF12151C)      // Nested sub-group container
    val ObsidianBorder = Color(0xFF1E232E)        // Low-contrast outer border
    val ObsidianSubBorder = Color(0xFF212733)     // Low-contrast inner sub-group border
    val ObsidianHairline = Color(0xFF1A1E26)      // Subtle 1dp horizontal hairline divider
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
