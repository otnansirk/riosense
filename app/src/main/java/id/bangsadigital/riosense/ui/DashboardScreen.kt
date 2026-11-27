package id.bangsadigital.riosense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.R
import id.bangsadigital.riosense.VoiceDashboardNavigation
import id.bangsadigital.riosense.data.VehicleRepository
import id.bangsadigital.riosense.launcher.InstalledAppsRepository
import id.bangsadigital.riosense.launcher.RioSenseAppWidgetController
import id.bangsadigital.riosense.model.BackgroundTarget
import id.bangsadigital.riosense.model.BuiltInWidgetType
import id.bangsadigital.riosense.model.DockDisplayMode
import id.bangsadigital.riosense.model.DockPosition
import id.bangsadigital.riosense.model.InstalledAppInfo
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.LAUNCHER_PAGE_APPS
import id.bangsadigital.riosense.model.LAUNCHER_PAGE_HOME
import id.bangsadigital.riosense.model.LAUNCHER_PAGE_WIDGETS
import id.bangsadigital.riosense.model.MAX_WIDGET_PAGES
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.MaintenanceStatus
import id.bangsadigital.riosense.model.PlacedWidgetItem
import id.bangsadigital.riosense.model.TopBarBrandLogo
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.model.VoiceButtonStyle
import id.bangsadigital.riosense.model.WIDGET_GRID_ROWS
import id.bangsadigital.riosense.model.WidgetItemKind
import id.bangsadigital.riosense.model.findFirstFreeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import id.bangsadigital.riosense.ui.components.AppDrawerBackgroundCanvas
import id.bangsadigital.riosense.ui.components.AppDrawerBackgroundPickerOverlay
import id.bangsadigital.riosense.ui.components.AppSpotlightSearchOverlay
import id.bangsadigital.riosense.ui.components.ControlsMenuSheet
import id.bangsadigital.riosense.ui.components.ControlsTab
import id.bangsadigital.riosense.ui.components.CustomizableWidgetGridPage
import id.bangsadigital.riosense.ui.components.HomeShowroomStageBackdrop
import id.bangsadigital.riosense.ui.components.InAppObsidianDock
import id.bangsadigital.riosense.ui.components.KiaRioInteractiveStage
import id.bangsadigital.riosense.ui.components.LauncherAppDrawerHeader
import id.bangsadigital.riosense.ui.components.LauncherAppDrawerPage
import id.bangsadigital.riosense.ui.components.LeftSpeedAndSecurityPanel
import id.bangsadigital.riosense.ui.components.LiquidMagneticPageIndicator
import id.bangsadigital.riosense.ui.components.MinimizedFloatingPillView
import id.bangsadigital.riosense.ui.components.RightEngineTelemetryPanel
import id.bangsadigital.riosense.ui.components.RoadMotionBackdrop
import id.bangsadigital.riosense.ui.components.RoadMotionDirection
import id.bangsadigital.riosense.ui.components.SimulatorAndSettingsSheet
import id.bangsadigital.riosense.ui.components.TripRouteVectorIcon
import id.bangsadigital.riosense.ui.components.WidgetPickerOverlay
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.LocalAppLanguage
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import id.bangsadigital.riosense.voice.VoiceUiState

/**
 * Single feature flag to hide the Simulator badge and drawer once live USB OBD-II testing is complete.
 */
private const val SHOW_SIMULATOR_TOOLS = true

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RioSenseDashboardScreen(
    telemetry: VehicleTelemetry,
    repository: VehicleRepository,
    voiceUiState: VoiceUiState,
    voiceNavigation: VoiceDashboardNavigation?,
    appWidgetController: RioSenseAppWidgetController,
    requestedLauncherPage: Pair<Int, Long>? = null,
    hasOverlayPermission: Boolean = false,
    onRequestOverlayPermission: () -> Unit = {},
    onOpenHomeLauncherSettings: () -> Unit = {},
    onToggleVoiceListening: () -> Unit,
    onDismissVoiceFeedback: () -> Unit,
    onEnterPipMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = appStrings()
    var showSimulatorDrawer by remember { mutableStateOf(false) }
    var showControlsDrawer by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var backgroundPickerTarget by remember { mutableStateOf(BackgroundTarget.APPS) }
    var showSpotlightSearch by remember { mutableStateOf(false) }
    var controlsInitialTab by remember { mutableStateOf<ControlsTab?>(null) }
    var isCarDragging by remember { mutableStateOf(false) }
    var homeRoadDirection by remember { mutableStateOf<RoadMotionDirection?>(null) }

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var pinFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            InstalledAppsRepository.loadInstalledApps(context)
        }
    }

    LaunchedEffect(pinFeedback) {
        if (pinFeedback != null) {
            delay(2600L)
            pinFeedback = null
        }
    }

    val errorLogs by repository.errorLogs.collectAsState()
    val activityLogs by repository.activityLogs.collectAsState()
    val isActivityPaused by repository.activityLogPaused.collectAsState()
    val maintenance by repository.maintenance.collectAsState()
    val tripHistory by repository.tripHistory.collectAsState()
    val activeTrip by repository.activeTrip.collectAsState()
    val launcherSettings by repository.launcherSettings.collectAsState()
    val widgetWorkspace by repository.widgetWorkspace.collectAsState()
    val dueServiceItems = remember(maintenance) { maintenance.dueOrOverdueItems() }
    var isWidgetEditMode by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var pendingTargetWidgetPage by remember { mutableStateOf<Int?>(null) }

    if (telemetry.isMinimized) {
        MinimizedFloatingPillView(
            telemetry = telemetry,
            onExpandDashboard = { repository.setMinimized(false) },
            onTriggerSimulatedDoorAlarm = {
                repository.applyNormalPreset()
                repository.setMinimized(true)
                repository.toggleDoor("RR")
            },
            onEnterNativePip = onEnterPipMode
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianColors.DeepObsidian)
    ) {
        val availGridW = (maxWidth - 48.dp).coerceAtLeast(280.dp)
        val availGridH = (maxHeight - 134.dp).coerceAtLeast(160.dp)
        val appGridCols = (availGridW / launcherSettings.appIconSize.cellWidthDp.dp).toInt().coerceAtLeast(3)
        val appGridRows = (availGridH / launcherSettings.appIconSize.cellHeightDp.dp).toInt().coerceAtLeast(2)
        val appsPerPage = (appGridCols * appGridRows).coerceAtLeast(6)

        val appChunks = remember(installedApps, appsPerPage) {
            installedApps.chunked(appsPerPage).ifEmpty { listOf(emptyList()) }
        }
        val widgetGridCols = if (maxWidth >= 840.dp) 8 else 6
        val widgetGridRows = WIDGET_GRID_ROWS

        LaunchedEffect(widgetGridCols) {
            repository.ensureWidgetWorkspaceInitialized(widgetGridCols)
        }

        val widgetPageCount = widgetWorkspace.pageCount.coerceIn(1, MAX_WIDGET_PAGES)
        val homePageIndex = widgetPageCount
        val appsStartPageIndex = widgetPageCount + 1
        val totalPages = widgetPageCount + 1 + appChunks.size

        val pagerState = rememberPagerState(
            initialPage = homePageIndex,
            pageCount = { totalPages }
        )

        var prevWidgetPageCount by remember { mutableIntStateOf(widgetPageCount) }
        LaunchedEffect(widgetPageCount) {
            val delta = widgetPageCount - prevWidgetPageCount
            val requestedWidgetPage = pendingTargetWidgetPage
            pendingTargetWidgetPage = null
            if (requestedWidgetPage != null) {
                val targetPagerIndex = (homePageIndex - 1 - requestedWidgetPage)
                    .coerceIn(0, (homePageIndex - 1).coerceAtLeast(0))
                pagerState.animateScrollToPage(targetPagerIndex)
            } else if (delta != 0) {
                val shifted = (pagerState.currentPage + delta).coerceIn(0, totalPages - 1)
                pagerState.scrollToPage(shifted)
            }
            prevWidgetPageCount = widgetPageCount
        }

        LaunchedEffect(totalPages) {
            if (pagerState.currentPage >= totalPages) {
                pagerState.scrollToPage((totalPages - 1).coerceAtLeast(0))
            }
        }

        LaunchedEffect(requestedLauncherPage, totalPages, homePageIndex, appsStartPageIndex) {
            val rawTarget = requestedLauncherPage?.first ?: return@LaunchedEffect
            val target = when (rawTarget) {
                LAUNCHER_PAGE_WIDGETS -> (homePageIndex - 1).coerceAtLeast(0)
                LAUNCHER_PAGE_HOME -> homePageIndex
                LAUNCHER_PAGE_APPS -> appsStartPageIndex
                else -> rawTarget.coerceIn(0, totalPages - 1)
            }
            isWidgetEditMode = false
            showWidgetPicker = false
            showSpotlightSearch = false
            showBackgroundPicker = false
            showSimulatorDrawer = false
            showControlsDrawer = false
            pagerState.animateScrollToPage(target.coerceIn(0, totalPages - 1))
        }

        LaunchedEffect(voiceNavigation) {
            when (voiceNavigation) {
                is VoiceDashboardNavigation.OpenControls -> {
                    showWidgetPicker = false
                    showSpotlightSearch = false
                    showSimulatorDrawer = false
                    controlsInitialTab = voiceNavigation.tab
                    showControlsDrawer = true
                }
                is VoiceDashboardNavigation.CloseMenus -> {
                    showWidgetPicker = false
                    showSpotlightSearch = false
                    showSimulatorDrawer = false
                    showControlsDrawer = false
                }
                null -> Unit
            }
        }

        BackHandler(
            enabled = showWidgetPicker || isWidgetEditMode || showSpotlightSearch || showBackgroundPicker || showSimulatorDrawer || showControlsDrawer || pagerState.currentPage != homePageIndex
        ) {
            when {
                showWidgetPicker -> showWidgetPicker = false
                isWidgetEditMode -> isWidgetEditMode = false
                showSpotlightSearch -> showSpotlightSearch = false
                showBackgroundPicker -> showBackgroundPicker = false
                showSimulatorDrawer -> showSimulatorDrawer = false
                showControlsDrawer -> showControlsDrawer = false
                pagerState.currentPage != homePageIndex -> {
                    scope.launch { pagerState.animateScrollToPage(homePageIndex) }
                }
            }
        }

        val absolutePage = pagerState.currentPage + pagerState.currentPageOffsetFraction
        val drawerReveal = (absolutePage - homePageIndex).coerceIn(0f, 1f)
        val widgetsReveal = (homePageIndex - absolutePage).coerceIn(0f, 1f)
        val homeReveal = (1f - maxOf(drawerReveal, widgetsReveal)).coerceIn(0f, 1f)
        val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        // Home hero stage backdrop: Deep Obsidian with Auto-Sync studio spotlight & floor horizon
        if (homeReveal > 0.001f) {
            HomeShowroomStageBackdrop(
                widgetsPreset = launcherSettings.widgetsBackground,
                appsPreset = launcherSettings.appDrawerBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = homeReveal }
            )
        }

        // Left Widgets workspace customizable Canvas background (Widgets 1..M)
        if (widgetsReveal > 0.001f) {
            AppDrawerBackgroundCanvas(
                preset = launcherSettings.widgetsBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -(1f - widgetsReveal) * screenWidthPx
                        alpha = widgetsReveal
                    }
            )
        }

        // Right App Drawer customizable Canvas background (Apps 1..N)
        if (drawerReveal > 0.001f) {
            AppDrawerBackgroundCanvas(
                preset = launcherSettings.appDrawerBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (1f - drawerReveal) * screenWidthPx
                        alpha = drawerReveal
                    }
            )
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCarDragging && !isWidgetEditMode && !showWidgetPicker && !showControlsDrawer && !showSimulatorDrawer && !showBackgroundPicker && !showSpotlightSearch,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when {
                page < homePageIndex -> {
                    val widgetPageIdx = (homePageIndex - 1 - page).coerceIn(0, widgetPageCount - 1)
                    val pageItems = remember(widgetWorkspace.items, widgetPageIdx) {
                        widgetWorkspace.items.filter { it.pageIndex == widgetPageIdx }
                    }
                    CustomizableWidgetGridPage(
                        pageIndex = widgetPageIdx,
                        totalWidgetPages = widgetPageCount,
                        items = pageItems,
                        gridCols = widgetGridCols,
                        gridRows = widgetGridRows,
                        telemetry = telemetry,
                        maintenance = maintenance,
                        isPageVisible = pagerState.currentPage == page,
                        isEditMode = isWidgetEditMode,
                        appWidgetController = appWidgetController,
                        onToggleEditMode = { isWidgetEditMode = it },
                        onOpenWidgetPicker = { showWidgetPicker = true },
                        onCommitPageLayout = { updatedItems ->
                            repository.replaceWidgetPageItems(widgetPageIdx, updatedItems)
                        },
                        onRemoveItem = { id ->
                            val hadMultiplePages = widgetPageCount > 1
                            val remainingOnThisPage = pageItems.count { it.id != id }
                            val removed = repository.removeWidgetItem(id)
                            if (removed != null && removed.appWidgetId > 0) {
                                appWidgetController.deleteWidgetId(removed.appWidgetId)
                            }
                            if (hadMultiplePages && remainingOnThisPage == 0) {
                                pendingTargetWidgetPage = widgetPageIdx.coerceAtMost((widgetPageCount - 2).coerceAtLeast(0))
                            }
                        },
                        onOpenControlsTab = { tab ->
                            showSimulatorDrawer = false
                            controlsInitialTab = tab
                            showControlsDrawer = true
                        },
                        onBackToHome = {
                            isWidgetEditMode = false
                            scope.launch { pagerState.animateScrollToPage(homePageIndex) }
                        }
                    )
                }

                page == homePageIndex -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RoadMotionBackdrop(
                            direction = homeRoadDirection,
                            speedKmh = telemetry.speedKmh,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 1. BORDERLESS OEM TOP BAR
                                TopObsidianBar(
                                    telemetry = telemetry,
                                    showSimulatorEntry = SHOW_SIMULATOR_TOOLS && telemetry.simulatorMode,
                                    hasServiceReminder = dueServiceItems.isNotEmpty(),
                                    isVoiceListening = voiceUiState.isListening,
                                    activeTripKm = if (telemetry.tripRecordingEnabled) activeTrip?.distanceKm else null,
                                    onSelectPaint = { repository.setPaintColor(it) },
                                    onToggleVoiceListening = onToggleVoiceListening,
                                    onToggleTripRecording = {
                                        repository.setTripRecordingEnabled(!telemetry.tripRecordingEnabled)
                                    },
                                    onToggleMute = { repository.toggleAudioMute() },
                                    onOpenSimulator = {
                                        showControlsDrawer = false
                                        showSimulatorDrawer = !showSimulatorDrawer
                                    },
                                    onOpenControls = {
                                        showSimulatorDrawer = false
                                        controlsInitialTab = null
                                        showControlsDrawer = !showControlsDrawer
                                    },
                                    onMinimize = { repository.setMinimized(true) }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // 2. SEAMLESS 3-COLUMN STAGE (Zero borders on Speed, Car Stage, and Engine Telemetry)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LeftSpeedAndSecurityPanel(
                                        telemetry = telemetry,
                                        onToggleLock = { repository.toggleCentralLock() },
                                        modifier = Modifier.weight(0.26f)
                                    )

                                    KiaRioInteractiveStage(
                                        telemetry = telemetry,
                                        onToggleDoor = { doorCode -> repository.toggleDoor(doorCode) },
                                        onDragStateChanged = { isCarDragging = it },
                                        onRoadMotionDirectionChange = { homeRoadDirection = it },
                                        modifier = Modifier
                                            .weight(0.48f)
                                            .fillMaxHeight()
                                    )

                                    RightEngineTelemetryPanel(
                                        telemetry = telemetry,
                                        modifier = Modifier.weight(0.26f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // 3. BORDERLESS MINIMALIST STATUS FOOTER (Live Alerts > Service Reminders > Nominal)
                                BottomAlertBanner(
                                    telemetry = telemetry,
                                    maintenance = maintenance,
                                    onAcknowledgeOrFix = { repository.applyNormalPreset() },
                                    onOpenServiceReminders = {
                                        showSimulatorDrawer = false
                                        controlsInitialTab = ControlsTab.SERVICE
                                        showControlsDrawer = true
                                    }
                                )
                            }

                            // 3b. FLOATING OBSIDIAN VOICE COMMAND OVERLAY
                            if (voiceUiState.isListening || voiceUiState.feedbackCard != null) {
                                val bottomOffset = if (voiceUiState.isListening) 86.dp else 46.dp
                                VoiceCommandFloatingOverlay(
                                    voiceUiState = voiceUiState,
                                    onStopListening = onToggleVoiceListening,
                                    onDismissCard = onDismissVoiceFeedback,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = bottomOffset)
                                )
                            }

                            // 3c. OPTIONAL ASSISTIVETOUCH-STYLE FLOATING GLASS MIC BUTTON
                            if (telemetry.voiceButtonStyle == VoiceButtonStyle.FLOATING) {
                                FloatingGlassMicButton(
                                    isListening = voiceUiState.isListening,
                                    onClick = onToggleVoiceListening
                                )
                            }
                        }
                    }
                }

                else -> {
                    val appPageIdx = (page - appsStartPageIndex).coerceIn(0, appChunks.lastIndex)
                    LauncherAppDrawerPage(
                        pageApps = appChunks[appPageIdx],
                        columns = appGridCols,
                        rows = appGridRows,
                        iconSize = launcherSettings.appIconSize,
                        pinnedPackages = launcherSettings.pinnedPackages,
                        onTogglePinApp = { app ->
                            val pinnedNow = repository.togglePinnedDockApp(app.packageName)
                            pinFeedback = if (pinnedNow) {
                                s.pinnedToDockFeedback(app.label)
                            } else {
                                s.unpinnedFromDockFeedback(app.label)
                            }
                        },
                        onOpenBackgroundPicker = {
                            backgroundPickerTarget = BackgroundTarget.APPS
                            showBackgroundPicker = true
                        }
                    )
                }
            }
        }

        // Stationary App Drawer header (slides cleanly from Home -> Apps, remains locked across Apps 1..N)
        if (absolutePage > homePageIndex + 0.001f) {
            val currentAppPageIdx = (pagerState.currentPage - appsStartPageIndex).coerceIn(0, appChunks.lastIndex)
            LauncherAppDrawerHeader(
                totalAppCount = installedApps.size,
                appPageIndex = currentAppPageIdx,
                totalAppPages = appChunks.size,
                onOpenSearch = { showSpotlightSearch = true },
                onOpenSettings = {
                    showSimulatorDrawer = false
                    controlsInitialTab = ControlsTab.SETTINGS
                    showControlsDrawer = true
                },
                onBackToHome = {
                    scope.launch { pagerState.animateScrollToPage(homePageIndex) }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = (1f - drawerReveal) * screenWidthPx
                        alpha = drawerReveal
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            )
        }

        // Dynamic bottom page indicator (Widgets · Home · Apps 1..N)
        val alwaysVisibleBottomDock = !hasOverlayPermission &&
            launcherSettings.dockEnabled &&
            launcherSettings.dockDisplayMode == DockDisplayMode.ALWAYS_VISIBLE &&
            launcherSettings.dockPosition == DockPosition.BOTTOM
        val indicatorBottomPadding = if (alwaysVisibleBottomDock) 64.dp else 6.dp
        LiquidMagneticPageIndicator(
            totalPages = totalPages,
            currentPagePosition = absolutePage,
            onSelectPage = { pageIndex ->
                scope.launch { pagerState.animateScrollToPage(pageIndex) }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = indicatorBottomPadding)
        )

        // In-app Obsidian Dock fallback when global overlay permission is not granted yet
        if (launcherSettings.dockEnabled && !hasOverlayPermission && !showBackgroundPicker) {
            InAppObsidianDock(
                settings = launcherSettings,
                onGoHome = {
                    scope.launch { pagerState.animateScrollToPage(homePageIndex) }
                },
                onOpenApps = {
                    scope.launch { pagerState.animateScrollToPage(appsStartPageIndex) }
                },
                onLaunchApp = { pkg -> InstalledAppsRepository.launchApp(context, pkg) }
            )
        }

        // Modal picker to add RioSense built-in or third-party Android app widgets
        val isIdLang = LocalAppLanguage.current == AppLanguage.ID
        val activeWidgetPageIdx = if (pagerState.currentPage < homePageIndex) {
            (homePageIndex - 1 - pagerState.currentPage).coerceIn(0, widgetPageCount - 1)
        } else {
            0
        }
        fun placeNewWidget(
            colSpan: Int,
            rowSpan: Int,
            buildItem: (targetPage: Int, col: Int, row: Int, w: Int, h: Int) -> PlacedWidgetItem
        ): Boolean {
            val safeW = colSpan.coerceIn(1, widgetGridCols)
            val safeH = rowSpan.coerceIn(1, widgetGridRows)
            val slotOnCurrent = findFirstFreeSlot(
                items = widgetWorkspace.items,
                pageIndex = activeWidgetPageIdx,
                colSpan = safeW,
                rowSpan = safeH,
                gridCols = widgetGridCols,
                gridRows = widgetGridRows
            )
            if (slotOnCurrent != null) {
                repository.addWidgetItem(
                    buildItem(activeWidgetPageIdx, slotOnCurrent.first, slotOnCurrent.second, safeW, safeH)
                )
                return true
            }
            for (p in 0 until widgetPageCount) {
                if (p == activeWidgetPageIdx) continue
                val freeOnOther = findFirstFreeSlot(
                    items = widgetWorkspace.items,
                    pageIndex = p,
                    colSpan = safeW,
                    rowSpan = safeH,
                    gridCols = widgetGridCols,
                    gridRows = widgetGridRows
                )
                if (freeOnOther != null) {
                    repository.addWidgetItem(
                        buildItem(p, freeOnOther.first, freeOnOther.second, safeW, safeH)
                    )
                    val targetPagerIndex = (homePageIndex - 1 - p).coerceIn(0, (homePageIndex - 1).coerceAtLeast(0))
                    scope.launch { pagerState.animateScrollToPage(targetPagerIndex) }
                    return true
                }
            }
            if (widgetPageCount < MAX_WIDGET_PAGES) {
                val newPage = repository.addWidgetPage()
                repository.addWidgetItem(buildItem(newPage, 0, 0, safeW, safeH))
                pendingTargetWidgetPage = newPage
                return true
            }
            pinFeedback = if (isIdLang) {
                "Grid widget penuh — hapus widget atau geser ukuran dulu"
            } else {
                "Widget grid is full — remove or resize a widget first"
            }
            return false
        }

        WidgetPickerOverlay(
            visible = showWidgetPicker,
            gridCols = widgetGridCols,
            appWidgetController = appWidgetController,
            onAddBuiltInWidget = { type ->
                val defaultW = if (type == BuiltInWidgetType.WEATHER || type == BuiltInWidgetType.VEHICLE) {
                    (widgetGridCols - 2).coerceAtLeast(2)
                } else {
                    type.defaultColSpan
                }
                val added = placeNewWidget(defaultW, type.defaultRowSpan) { pageIdx, col, row, w, h ->
                    PlacedWidgetItem(
                        id = "builtin_${type.name.lowercase()}_${System.currentTimeMillis()}",
                        pageIndex = pageIdx,
                        kind = WidgetItemKind.BUILT_IN,
                        builtInType = type,
                        col = col,
                        row = row,
                        colSpan = w,
                        rowSpan = h
                    )
                }
                showWidgetPicker = false
                if (added) isWidgetEditMode = true
            },
            onSelectAppWidget = { avail ->
                showWidgetPicker = false
                appWidgetController.requestAddAppWidget(
                    providerInfo = avail.providerInfo,
                    defaultSpan = avail.defaultColSpan to avail.defaultRowSpan
                ) { widgetId, info, span ->
                    val added = placeNewWidget(span.first, span.second) { pageIdx, col, row, w, h ->
                        PlacedWidgetItem(
                            id = "appwidget_${widgetId}_${System.currentTimeMillis()}",
                            pageIndex = pageIdx,
                            kind = WidgetItemKind.APP_WIDGET,
                            appWidgetId = widgetId,
                            providerComponent = info.provider.flattenToShortString(),
                            col = col,
                            row = row,
                            colSpan = w,
                            rowSpan = h
                        )
                    }
                    if (!added) {
                        appWidgetController.deleteWidgetId(widgetId)
                    } else {
                        isWidgetEditMode = true
                    }
                }
            },
            onDismiss = { showWidgetPicker = false }
        )

        // Slide-up live Background Preset Picker for Widgets, Apps, or Both
        AppDrawerBackgroundPickerOverlay(
            visible = showBackgroundPicker,
            selectedTarget = backgroundPickerTarget,
            widgetsBackground = launcherSettings.widgetsBackground,
            appDrawerBackground = launcherSettings.appDrawerBackground,
            onSelectTarget = { target ->
                backgroundPickerTarget = target
                when (target) {
                    BackgroundTarget.WIDGETS -> {
                        if (pagerState.currentPage >= homePageIndex) {
                            scope.launch { pagerState.animateScrollToPage((homePageIndex - 1).coerceAtLeast(0)) }
                        }
                    }
                    BackgroundTarget.APPS -> {
                        if (pagerState.currentPage < appsStartPageIndex) {
                            scope.launch { pagerState.animateScrollToPage(appsStartPageIndex) }
                        }
                    }
                    BackgroundTarget.BOTH -> {
                        if (pagerState.currentPage == homePageIndex) {
                            scope.launch { pagerState.animateScrollToPage(appsStartPageIndex) }
                        }
                    }
                }
            },
            onSelectBackground = { target, bg ->
                when (target) {
                    BackgroundTarget.WIDGETS -> repository.setWidgetsBackground(bg)
                    BackgroundTarget.APPS -> repository.setAppDrawerBackground(bg)
                    BackgroundTarget.BOTH -> repository.setSharedBackground(bg)
                }
            },
            onDismiss = { showBackgroundPicker = false }
        )

        // macOS Spotlight-style centered search overlay
        AppSpotlightSearchOverlay(
            visible = showSpotlightSearch,
            allApps = installedApps,
            pinnedPackages = launcherSettings.pinnedPackages,
            onLaunchApp = { app ->
                showSpotlightSearch = false
                InstalledAppsRepository.launchApp(context, app.packageName)
            },
            onTogglePinApp = { app ->
                val pinnedNow = repository.togglePinnedDockApp(app.packageName)
                pinFeedback = if (pinnedNow) {
                    s.pinnedToDockFeedback(app.label)
                } else {
                    s.unpinnedFromDockFeedback(app.label)
                }
            },
            onDismiss = { showSpotlightSearch = false }
        )

        // Consistent single-line Obsidian floating notification card matching VoiceCommandFloatingOverlay
        pinFeedback?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (alwaysVisibleBottomDock) 74.dp else 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xEB11141B))
                    .border(0.8.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(14.dp))
                    .clickable { pinFeedback = null }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message,
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4. SIMULATOR SHEET (Opened from top-left "Simulator" badge; easily hidden via SHOW_SIMULATOR_TOOLS)
        if (SHOW_SIMULATOR_TOOLS && showSimulatorDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { showSimulatorDrawer = false },
                contentAlignment = Alignment.CenterStart
            ) {
                SimulatorAndSettingsSheet(
                    telemetry = telemetry,
                    onSpeedRpmChange = { sp, rpm -> repository.setSpeedAndRpm(sp, rpm) },
                    onCoolantChange = { temp -> repository.setCoolantTemp(temp) },
                    onToggleDoor = { code -> repository.toggleDoor(code) },
                    onToggleLock = { repository.toggleCentralLock() },
                    onToggleLight = { code -> repository.toggleLight(code) },
                    onToggleTpmsSignal = { available -> repository.setTpmsSignalAvailable(available) },
                    onTirePressureChange = { code, psi -> repository.setTirePressure(code, psi) },
                    onTireTempChange = { code, temp -> repository.setTireTemp(code, temp) },
                    onPaintColorSelect = { color -> repository.setPaintColor(color) },
                    onBatteryVoltageChange = { volts -> repository.setBatteryVoltage(volts) },
                    onApplyNormalPreset = { repository.applyNormalPreset() },
                    onApplyAlertPreset = { repository.applyAlertShowcasePreset() },
                    onSimulateEngineStart = { repository.simulateEngineStart() },
                    onSimulateEngineOff = { repository.simulateEngineOff() },
                    onInjectDtc = { code -> repository.injectSimulatedDtc(code) },
                    onClose = { showSimulatorDrawer = false },
                    modifier = Modifier.clickable(enabled = false) {}
                )
            }
        }

        // 5. CONTROLS MENU SHEET (OBD-II Diagnostics: Error Log, Activity Log, Service & Settings)
        if (showControlsDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { showControlsDrawer = false },
                contentAlignment = Alignment.CenterEnd
            ) {
                ControlsMenuSheet(
                    telemetry = telemetry,
                    maintenance = maintenance,
                    currentLanguage = LocalAppLanguage.current,
                    onLanguageChange = { lang -> repository.setAppLanguage(lang) },
                    onWelcomeSoundModeChange = { mode ->
                        repository.setWelcomeSoundMode(mode)
                    },
                    onEngineOffVoiceReminderChange = { enabled ->
                        repository.setEngineOffVoiceReminderEnabled(enabled)
                    },
                    onVoiceButtonStyleChange = { style ->
                        repository.setVoiceButtonStyle(style)
                    },
                    onTripRecordingEnabledChange = { enabled ->
                        repository.setTripRecordingEnabled(enabled)
                    },
                    onTopBarBrandLogoChange = { logo ->
                        repository.setTopBarBrandLogo(logo)
                    },
                    onRoadEffectEnabledChange = { enabled ->
                        repository.setRoadEffectEnabled(enabled)
                    },
                    launcherSettings = launcherSettings,
                    hasOverlayPermission = hasOverlayPermission,
                    onDockEnabledChange = { enabled -> repository.setDockEnabled(enabled) },
                    onDockDisplayModeChange = { mode -> repository.setDockDisplayMode(mode) },
                    onDockPositionChange = { pos -> repository.setDockPosition(pos) },
                    onAppIconSizeChange = { size -> repository.setAppIconSize(size) },
                    onOpenBackgroundPicker = {
                        showControlsDrawer = false
                        showSimulatorDrawer = false
                        when {
                            pagerState.currentPage < homePageIndex -> {
                                backgroundPickerTarget = BackgroundTarget.WIDGETS
                            }
                            pagerState.currentPage >= appsStartPageIndex -> {
                                backgroundPickerTarget = BackgroundTarget.APPS
                            }
                            else -> {
                                backgroundPickerTarget = BackgroundTarget.APPS
                                scope.launch { pagerState.animateScrollToPage(appsStartPageIndex) }
                            }
                        }
                        showBackgroundPicker = true
                    },
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onOpenHomeLauncherSettings = onOpenHomeLauncherSettings,
                    initialTab = controlsInitialTab,
                    tripHistory = tripHistory,
                    activeTrip = activeTrip,
                    onDeleteTrip = { tripId -> repository.deleteTrip(tripId) },
                    onLoadSampleTrips = { repository.loadSampleTrips() },
                    onClearTripHistory = { repository.clearTripHistory() },
                    errorLogs = errorLogs,
                    activityLogs = activityLogs,
                    isActivityPaused = isActivityPaused,
                    onToggleActivityPause = { repository.toggleActivityLogPaused() },
                    onClearErrorLogs = { repository.clearErrorLogs() },
                    onClearActivityLogs = { repository.clearActivityLogs() },
                    onUpdateOdometer = { km -> repository.updateOdometerKm(km) },
                    onRemindDaysChange = { days -> repository.setMaintenanceRemindDays(days) },
                    onRemindKmChange = { km -> repository.setMaintenanceRemindKm(km) },
                    onLogServiceRecord = { id, dateMs, odoKm, costIdr, note ->
                        repository.logMaintenanceServiceRecord(id, dateMs, odoKm, costIdr, note)
                    },
                    onDeleteHistoryRecord = { id, recId ->
                        repository.deleteMaintenanceHistoryRecord(id, recId)
                    },
                    onUpdateServiceSchedule = { id, months, km ->
                        repository.updateMaintenanceItemSchedule(id, months, km)
                    },
                    onAddCustomServiceItem = { name, months, km, dateMs, odoKm, costIdr, note ->
                        repository.addCustomMaintenanceItem(name, months, km, dateMs, odoKm, costIdr, note)
                    },
                    onDeleteCustomServiceItem = { id -> repository.deleteCustomMaintenanceItem(id) },
                    onResetMaintenanceToStandard = { repository.resetMaintenanceToStandard() },
                    onSpeedLockThresholdChange = { kmh -> repository.setSpeedLockThreshold(kmh) },
                    onTpmsLowPsiChange = { psi -> repository.setTpmsLowThreshold(psi) },
                    onTpmsHighPsiChange = { psi -> repository.setTpmsHighThreshold(psi) },
                    onTpmsHighTempChange = { tempC -> repository.setTpmsHighTempThreshold(tempC) },
                    onCoolantOverheatChange = { tempC -> repository.setCoolantOverheatThreshold(tempC) },
                    onBatteryLowChange = { volts -> repository.setBatteryLowThreshold(volts) },
                    onBatteryHighChange = { volts -> repository.setBatteryHighThreshold(volts) },
                    onResetSettingsToStandard = { repository.resetAlertSettingsToStandard() },
                    onClose = { showControlsDrawer = false },
                    modifier = Modifier.clickable(enabled = false) {}
                )
            }
        }
    }
}

@Composable
private fun TopObsidianBar(
    telemetry: VehicleTelemetry,
    activeTripKm: Float?,
    showSimulatorEntry: Boolean,
    hasServiceReminder: Boolean,
    isVoiceListening: Boolean,
    onSelectPaint: (KiaRioPaintColor) -> Unit,
    onToggleVoiceListening: () -> Unit,
    onToggleTripRecording: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenControls: () -> Unit,
    onMinimize: () -> Unit
) {
    val s = appStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand & Clickable Simulator Entry (or OBD Status when Simulator is hidden/off)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (telemetry.topBarBrandLogo) {
                TopBarBrandLogo.RIOSENSE -> Text(
                    text = "RioSense",
                    color = ObsidianColors.PureSilver,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )

                TopBarBrandLogo.KIA_CLASSIC -> Image(
                    painter = painterResource(id = R.drawable.ic_kia_classic),
                    contentDescription = "Kia",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(21.dp)
                )

                TopBarBrandLogo.KIA_MODERN -> Image(
                    painter = painterResource(id = R.drawable.ic_kia_modern),
                    contentDescription = "Kia",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(14.dp)
                )
            }

            if (showSimulatorEntry) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF15181F))
                        .clickable { onOpenSimulator() }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(ObsidianColors.CobaltAccent)
                    )
                    Text(
                        text = s.simulator,
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.obdConnected) Color(0xFF38A169) else Color(0xFF646C7E))
                    )
                    Text(
                        text = if (telemetry.obdConnected) s.obdConnected else s.obdStandby,
                        color = ObsidianColors.DimSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // Paint Swatches (Clean circles without noisy labels)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KiaRioPaintColor.entries.forEach { paint ->
                val isSelected = telemetry.paintColor == paint
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 16.dp else 12.dp)
                        .clip(CircleShape)
                        .background(paint.primaryHex)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.5.dp, Color.White, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelectPaint(paint) }
                )
            }
        }

        // Borderless Top-Right OEM Icon Bar (Mic, GPS Route, Chime Speaker, Controls Sliders, Minimize PiP)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Voice Command Microphone Icon (shown in Top Bar by default; hidden when Floating style is active)
            if (telemetry.voiceButtonStyle == VoiceButtonStyle.TOP_BAR) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isVoiceListening) {
                                Modifier
                                    .background(ObsidianColors.CobaltSurface)
                                    .border(0.9.dp, ObsidianColors.CobaltAccent.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onToggleVoiceListening() }
                        .padding(horizontal = 5.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MicrophoneVectorIcon(
                        tint = if (isVoiceListening) ObsidianColors.CobaltAccent else ObsidianColors.MutedSilver,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Borderless Trip Tracking Shortcut (winding route icon + live km when ON; slashed icon when OFF)
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleTripRecording() }
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TripRouteVectorIcon(
                    tint = if (telemetry.tripRecordingEnabled) ObsidianColors.EmeraldNominal else ObsidianColors.DimSilver,
                    slashed = !telemetry.tripRecordingEnabled,
                    modifier = Modifier.size(14.dp)
                )
                if (telemetry.tripRecordingEnabled) {
                    Text(
                        text = String.format("%.1f km", activeTripKm ?: 0f),
                        color = if ((activeTripKm ?: 0f) > 0f) ObsidianColors.PureSilver else ObsidianColors.MutedSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 1A. Borderless Chime / Muted Speaker Icon (Speaker + waves when ON; diagonal slash when Muted)
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleMute() }
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                SpeakerChimeVectorIcon(
                    muted = telemetry.audioAlarmMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            // 2A. Borderless Controls Sliders Icon (+ small Amber dot at top-right if service is due)
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOpenControls() }
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                ControlsSlidersVectorIcon(
                    tint = ObsidianColors.PureSilver,
                    modifier = Modifier.size(15.dp)
                )
                if (hasServiceReminder) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(ObsidianColors.AmberCaution)
                    )
                }
            }

            // 3A. Borderless Minimize / Floating Pill PiP Icon
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onMinimize() }
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                MinimizePipVectorIcon(
                    tint = ObsidianColors.MutedSilver,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomAlertBanner(
    telemetry: VehicleTelemetry,
    maintenance: MaintenanceState,
    onAcknowledgeOrFix: () -> Unit,
    onOpenServiceReminders: () -> Unit
) {
    val s = appStrings()
    val alerts = telemetry.activeAlerts
    val hasAlert = alerts.isNotEmpty()
    val nowMs = System.currentTimeMillis()
    val dueServices = maintenance.dueOrOverdueItems(nowMs)
    val hasServiceReminder = !hasAlert && dueServices.isNotEmpty()
    val hasOverdueService = dueServices.any {
        it.evaluateStatus(
            nowMs,
            maintenance.currentOdometerKm,
            maintenance.remindDaysBefore,
            maintenance.remindKmBefore
        ) == MaintenanceStatus.OVERDUE
    }

    val bg = when {
        hasAlert -> ObsidianColors.AlertSurface
        hasServiceReminder -> Color(0xFF17140E)
        else -> Color(0xFF0B0C0F)
    }

    val dotColor = when {
        hasAlert -> ObsidianColors.CrimsonAlert
        hasServiceReminder && hasOverdueService -> ObsidianColors.CrimsonAlert
        hasServiceReminder -> ObsidianColors.AmberCaution
        else -> Color(0xFF4C5260)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(
                if (hasServiceReminder) Modifier.clickable { onOpenServiceReminders() }
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    hasAlert -> alerts.joinToString("  ·  ")
                    hasServiceReminder -> {
                        val summary = dueServices.take(2).joinToString("  ·  ") { item ->
                            "${item.name} (${item.quickSummary(nowMs, maintenance.currentOdometerKm)})"
                        }
                        val extra = if (dueServices.size > 2) s.moreSuffix(dueServices.size - 2) else ""
                        "${s.serviceReminderPrefix}: $summary$extra"
                    }
                    else -> "Kia Rio UB 2015  ·  ${telemetry.paintColor.displayName}  ·  ${s.allSystemsNominal}"
                },
                color = if (hasAlert || hasServiceReminder) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                fontSize = 11.sp,
                fontWeight = if (hasAlert || hasServiceReminder) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (hasAlert) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF281A1D))
                    .clickable { onAcknowledgeOrFix() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = s.dismiss,
                    color = ObsidianColors.PureSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (hasServiceReminder) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2214))
                    .clickable { onOpenServiceReminders() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = s.serviceBtn,
                    color = ObsidianColors.AmberCaution,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun VoiceCommandFloatingOverlay(
    voiceUiState: VoiceUiState,
    onStopListening: () -> Unit,
    onDismissCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (voiceUiState.isListening) {
        ObsidianFrequencyVisualizer(
            audioLevel = voiceUiState.audioLevel,
            onStopListening = onStopListening,
            modifier = modifier
        )
        return
    }

    val card = voiceUiState.feedbackCard ?: return

    Column(
        modifier = modifier
            .fillMaxWidth(0.52f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xEB11141B))
            .border(0.8.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(14.dp))
            .clickable { onDismissCard() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = card.title,
            color = if (card.isError) ObsidianColors.CrimsonAlert else ObsidianColors.EmeraldNominal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = card.subtitle,
            color = if (card.isError) ObsidianColors.MutedSilver else ObsidianColors.PureSilver,
            fontSize = 11.sp
        )
        card.detailLines.forEach { line ->
            Text(
                text = line,
                color = ObsidianColors.DimSilver,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Borderless luxury automotive HUD audio frequency visualizer.
 * Reacts smoothly in real time to microphone RMS level with multi-harmonic motion,
 * zero text clutter, and zero heavy box borders.
 */
@Composable
private fun ObsidianFrequencyVisualizer(
    audioLevel: Float,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val smoothLevel by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "voiceFreqLevel"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "voiceFreqWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voiceFreqPhase"
    )

    Box(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onStopListening() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(154.dp)
                .height(42.dp)
        ) {
            val barCount = 15
            val centerIdx = (barCount - 1) / 2f
            val barWidth = 3.2.dp.toPx()
            val totalBarsWidth = barCount * barWidth
            val gap = (size.width - totalBarsWidth) / (barCount - 1)
            val centerY = size.height * 0.5f

            // Soft borderless ambient cobalt glow beneath the wave
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ObsidianColors.CobaltAccent.copy(alpha = 0.16f + smoothLevel * 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, centerY),
                    radius = size.width * 0.48f
                ),
                radius = size.width * 0.48f,
                center = Offset(size.width * 0.5f, centerY)
            )

            for (i in 0 until barCount) {
                val distFromCenter = abs(i - centerIdx) / centerIdx // 0.0 at center -> 1.0 at edges
                val bellEnvelope = (1f - distFromCenter * distFromCenter * 0.78f).coerceIn(0.22f, 1f)

                // Multi-harmonic wave for organic, non-robotic cadence
                val h1 = sin(phase * 1.0f + i * 0.62f)
                val h2 = sin(phase * 2.0f - i * 0.95f) * 0.45f
                val waveNorm = ((h1 + h2) * 0.35f + 0.5f).coerceIn(0.12f, 1f)

                val minH = size.height * 0.14f
                val idleAmp = size.height * 0.34f * bellEnvelope * waveNorm
                val voiceBoost = size.height * 0.62f * bellEnvelope * smoothLevel * (0.45f + 0.55f * waveNorm)
                val barHeight = (minH + idleAmp + voiceBoost).coerceIn(minH, size.height * 0.94f)

                val x = i * (barWidth + gap) + barWidth * 0.5f
                val topY = centerY - barHeight * 0.5f
                val bottomY = centerY + barHeight * 0.5f

                val barColor = if (distFromCenter < 0.38f) {
                    ObsidianColors.CobaltAccent.copy(alpha = 0.85f + smoothLevel * 0.15f)
                } else if (distFromCenter < 0.72f) {
                    ObsidianColors.PureSilver.copy(alpha = 0.72f + smoothLevel * 0.22f)
                } else {
                    ObsidianColors.MutedSilver.copy(alpha = 0.42f + smoothLevel * 0.30f)
                }

                drawLine(
                    color = barColor,
                    start = Offset(x, topY),
                    end = Offset(x, bottomY),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * AssistiveTouch-inspired draggable frosted-glass rounded square microphone button.
 * Snaps smoothly to the nearest horizontal screen edge on release so it never obscures
 * the central 360-degree Kia Rio viewport.
 */
@Composable
private fun FloatingGlassMicButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val buttonSizeDp = 52.dp
        val edgeMarginDp = 12.dp
        val buttonSizePx = with(density) { buttonSizeDp.toPx() }
        val edgeMarginPx = with(density) { edgeMarginDp.toPx() }
        val maxXPx = (with(density) { maxWidth.toPx() } - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val maxYPx = (with(density) { maxHeight.toPx() } - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)

        var isDragging by remember { mutableStateOf(false) }
        var targetX by remember { mutableFloatStateOf(-1f) }
        var targetY by remember { mutableFloatStateOf(-1f) }

        // Default position: right edge, slightly below vertical center (ergonomic thumb reach)
        val resolvedX = if (targetX < 0f) maxXPx else targetX.coerceIn(edgeMarginPx, maxXPx)
        val resolvedY = if (targetY < 0f) (maxYPx * 0.58f).coerceIn(edgeMarginPx, maxYPx) else targetY.coerceIn(edgeMarginPx, maxYPx)

        val animatedX by animateFloatAsState(
            targetValue = resolvedX,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "floatingMicSnapX"
        )

        val displayX = if (isDragging) resolvedX else animatedX
        val shape = RoundedCornerShape(15.dp)

        val glassBgBrush = if (isListening) {
            Brush.verticalGradient(
                colors = listOf(ObsidianColors.CobaltGlassTop, ObsidianColors.CobaltGlassBottom)
            )
        } else if (isDragging) {
            Brush.verticalGradient(
                colors = listOf(Color(0xD42C3648), Color(0xC2181F2B))
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xB8252D3D), Color(0xA6141923))
            )
        }

        val glassRimBrush = if (isListening) {
            Brush.verticalGradient(
                colors = listOf(
                    ObsidianColors.CobaltAccent.copy(alpha = 0.88f),
                    ObsidianColors.CobaltAccent.copy(alpha = 0.45f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.14f))
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(displayX.roundToInt(), resolvedY.roundToInt()) }
                .size(buttonSizeDp)
                .clip(shape)
                .background(glassBgBrush)
                .border(width = 1.dp, brush = glassRimBrush, shape = shape)
                .pointerInput(maxXPx, maxYPx) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            if (targetX < 0f) targetX = maxXPx
                            if (targetY < 0f) targetY = (maxYPx * 0.58f).coerceIn(edgeMarginPx, maxYPx)
                        },
                        onDragEnd = {
                            isDragging = false
                            // Snap smoothly to nearest horizontal edge like iPhone AssistiveTouch
                            val midX = (edgeMarginPx + maxXPx) * 0.5f
                            targetX = if (targetX < midX) edgeMarginPx else maxXPx
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            targetX = (targetX + dragAmount.x).coerceIn(edgeMarginPx, maxXPx)
                            targetY = (targetY + dragAmount.y).coerceIn(edgeMarginPx, maxYPx)
                        }
                    )
                }
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Subtle frosted inner glass square (AssistiveTouch depth cue)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isListening) 0.18f else 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(
                        width = 0.7.dp,
                        color = if (isListening) {
                            ObsidianColors.CobaltAccent.copy(alpha = 0.55f)
                        } else {
                            Color.White.copy(alpha = 0.18f)
                        },
                        shape = RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                MicrophoneVectorIcon(
                    tint = if (isListening) {
                        ObsidianColors.CobaltAccent
                    } else {
                        ObsidianColors.PureSilver.copy(alpha = 0.94f)
                    },
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

/**
 * Pixel-centered vector microphone icon drawn on Canvas.
 */
@Composable
private fun MicrophoneVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()

        // Capsule head
        val capW = w * 0.38f
        val capH = h * 0.50f
        val capLeft = (w - capW) * 0.5f
        drawRoundRect(
            color = tint,
            topLeft = Offset(capLeft, h * 0.08f),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(capW * 0.5f, capW * 0.5f),
            style = Stroke(width = stroke)
        )

        // U-shaped receiver arc
        val arcW = w * 0.68f
        val arcH = h * 0.44f
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset((w - arcW) * 0.5f, h * 0.28f),
            size = Size(arcW, arcH),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Stand stem + base
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.90f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.90f),
            end = Offset(w * 0.70f, h * 0.90f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 1A: Minimalist Speaker / Chime vector icon.
 * Draws a speaker cone + curved sound wave when unmuted, or a dimmed speaker
 * crossed by a diagonal slash when muted.
 */
@Composable
private fun SpeakerChimeVectorIcon(
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.35.dp.toPx()
        val baseColor = if (muted) ObsidianColors.DimSilver else ObsidianColors.MutedSilver

        // Speaker cone polygon
        val conePath = Path().apply {
            moveTo(w * 0.12f, h * 0.37f)
            lineTo(w * 0.30f, h * 0.37f)
            lineTo(w * 0.54f, h * 0.17f)
            lineTo(w * 0.54f, h * 0.83f)
            lineTo(w * 0.30f, h * 0.63f)
            lineTo(w * 0.12f, h * 0.63f)
            close()
        }
        drawPath(
            path = conePath,
            color = baseColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        if (!muted) {
            // Sound wave arcs on the right
            drawArc(
                color = baseColor,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.44f, h * 0.28f),
                size = Size(w * 0.30f, h * 0.44f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = baseColor,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(w * 0.48f, h * 0.16f),
                size = Size(w * 0.44f, h * 0.68f),
                style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round)
            )
        } else {
            // Crisp diagonal slash when muted
            drawLine(
                color = ObsidianColors.DeepObsidian,
                start = Offset(w * 0.14f, h * 0.14f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = stroke * 2.2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ObsidianColors.MutedSilver,
                start = Offset(w * 0.14f, h * 0.14f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 2A: Minimalist 2-track horizontal sliders vector icon representing Controls.
 */
@Composable
private fun ControlsSlidersVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.35.dp.toPx()
        val knobR = 2.0.dp.toPx()

        // Top slider track + knob at ~66%
        val topY = h * 0.30f
        val topKnobX = w * 0.66f
        drawLine(tint, Offset(w * 0.10f, topY), Offset(w * 0.90f, topY), stroke, StrokeCap.Round)
        drawCircle(ObsidianColors.DeepObsidian, radius = knobR + 0.8.dp.toPx(), center = Offset(topKnobX, topY))
        drawCircle(tint, radius = knobR, center = Offset(topKnobX, topY), style = Stroke(width = stroke))

        // Bottom slider track + knob at ~34%
        val botY = h * 0.70f
        val botKnobX = w * 0.34f
        drawLine(tint, Offset(w * 0.10f, botY), Offset(w * 0.90f, botY), stroke, StrokeCap.Round)
        drawCircle(ObsidianColors.DeepObsidian, radius = knobR + 0.8.dp.toPx(), center = Offset(botKnobX, botY))
        drawCircle(tint, radius = knobR, center = Offset(botKnobX, botY), style = Stroke(width = stroke))
    }
}

/**
 * 3A: Minimalist Picture-in-Picture / Floating Pill vector icon representing Minimize.
 * Draws an outer screen frame, a compact pill/box in the bottom-right corner, and a diagonal arrow pointing into it.
 */
@Composable
private fun MinimizePipVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()

        // Outer display frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.14f),
            size = Size(w * 0.84f, h * 0.72f),
            cornerRadius = CornerRadius(2.4.dp.toPx(), 2.4.dp.toPx()),
            style = Stroke(width = stroke)
        )

        // Inner floating pill in bottom-right corner
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.50f, h * 0.52f),
            size = Size(w * 0.32f, h * 0.24f),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )

        // Small diagonal arrow pointing from top-left toward the floating pill
        val aStart = Offset(w * 0.24f, h * 0.30f)
        val aEnd = Offset(w * 0.44f, h * 0.48f)
        drawLine(tint, aStart, aEnd, strokeWidth = stroke * 0.95f, cap = StrokeCap.Round)
        drawLine(tint, Offset(aEnd.x - w * 0.13f, aEnd.y), aEnd, strokeWidth = stroke * 0.95f, cap = StrokeCap.Round)
        drawLine(tint, Offset(aEnd.x, aEnd.y - h * 0.13f), aEnd, strokeWidth = stroke * 0.95f, cap = StrokeCap.Round)
    }
}
