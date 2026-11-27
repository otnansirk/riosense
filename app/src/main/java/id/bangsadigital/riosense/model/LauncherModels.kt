package id.bangsadigital.riosense.model

const val LAUNCHER_PAGE_WIDGETS = 0
const val LAUNCHER_PAGE_HOME = 1
const val LAUNCHER_PAGE_APPS = 2

/**
 * Display behavior for the Obsidian macOS-style quick-access Dock.
 */
enum class DockDisplayMode {
    ALWAYS_VISIBLE,
    AUTO_HIDE_SWIPE
}

/**
 * Anchored screen edge when [DockDisplayMode.ALWAYS_VISIBLE] is active.
 */
enum class DockPosition {
    BOTTOM,
    TOP,
    LEFT,
    RIGHT
}

/**
 * User-configurable App Drawer grid icon sizing for comfortable touch targets while driving.
 */
enum class AppIconSize(
    val iconDp: Int,
    val cellWidthDp: Int,
    val cellHeightDp: Int,
    val labelSp: Float
) {
    SMALL(iconDp = 40, cellWidthDp = 86, cellHeightDp = 80, labelSp = 10f),
    MEDIUM(iconDp = 52, cellWidthDp = 104, cellHeightDp = 96, labelSp = 11f),
    LARGE(iconDp = 64, cellWidthDp = 124, cellHeightDp = 114, labelSp = 12f)
}

/**
 * Procedural monochrome and soft-tinted luxury background presets for the App Drawer.
 */
enum class AppDrawerBackground {
    OBSIDIAN_PURE,
    CARBON_WEAVE,
    BRUSHED_TITANIUM,
    TOPO_CONTOUR,
    MONOLITH_GRID,
    CHAMPAGNE_GOLD,
    ESPRESSO_BRONZE,
    VELVET_MAROON,
    MIDNIGHT_SAPPHIRE,
    EMERALD_NOIR
}

/**
 * Scope for applying a selected background preset from the bottom picker (Widgets, Apps, or Both).
 */
enum class BackgroundTarget {
    WIDGETS,
    APPS,
    BOTH
}

/**
 * Persistent settings for RioSense Launcher Mode and the Global Obsidian Dock.
 */
data class LauncherSettings(
    val dockEnabled: Boolean = true,
    val dockDisplayMode: DockDisplayMode = DockDisplayMode.ALWAYS_VISIBLE,
    val dockPosition: DockPosition = DockPosition.BOTTOM,
    val pinnedPackages: List<String> = emptyList(),
    val appIconSize: AppIconSize = AppIconSize.MEDIUM,
    val widgetsBackground: AppDrawerBackground = AppDrawerBackground.OBSIDIAN_PURE,
    val appDrawerBackground: AppDrawerBackground = AppDrawerBackground.OBSIDIAN_PURE
) {
    companion object {
        const val MAX_PINNED_APPS = 16
    }
}

/**
 * Lightweight descriptor of a launchable application installed on the Android Head Unit.
 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean = false
)
