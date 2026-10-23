package io.github.roaxio.dashguardpro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import io.github.roaxio.dashguardpro.data.settings.AppMode
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.ui.components.SpeedSafetyLockoutOverlay
import io.github.roaxio.dashguardpro.ui.screens.CameraHudScreen
import io.github.roaxio.dashguardpro.ui.screens.GalleryScreen
import io.github.roaxio.dashguardpro.ui.screens.OptimizationScreen
import io.github.roaxio.dashguardpro.ui.screens.SettingsScreen
import io.github.roaxio.dashguardpro.ui.screens.VideoPlayerScreen
import io.github.roaxio.dashguardpro.ui.theme.AutomotiveDark
import io.github.roaxio.dashguardpro.ui.theme.AutomotivePitchBlack
import io.github.roaxio.dashguardpro.ui.theme.CockpitPanel
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.DashGuardProTheme
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel

enum class NavigationTab(val title: String, val icon: ImageVector) {
    CAMERA("HUD Cam", Icons.Default.Videocam),
    GALLERY("Footage", Icons.Default.Folder),
    OPTIMIZATION("Optimize", Icons.Default.Bolt),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    private val viewModel: DashcamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen alive when dashcam is actively recording
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Extend into display cutout area for true edge-to-edge full screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        enableEdgeToEdge()
        hideSystemBars()

        // Lock/prevent app orientation changes when recording is active regardless of starting orientation
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.isRecording }
                    .distinctUntilChanged()
                    .collect { isRecording ->
                        if (isRecording) {
                            lockOrientationToCurrent()
                        } else {
                            unlockOrientation()
                        }
                    }
            }
        }

        val simReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                val gVal = intent?.getFloatExtra("gForce", 4.5f) ?: 4.5f
                viewModel.handleImpactDetected(gVal)
            }
        }
        val filter = android.content.IntentFilter("io.github.roaxio.dashguardpro.ACTION_SIMULATE_IMPACT")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            simReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )

        setContent {
            DashGuardProTheme {
                DashGuardApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        viewModel.refreshSystemDiagnostics()
        viewModel.onActivityResumed()
        if (viewModel.uiState.value.isRecording) {
            lockOrientationToCurrent()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockOrientation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            viewModel.refreshSystemDiagnostics()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (viewModel.uiState.value.isRecording) {
                viewModel.toggleCurrentClipLock("Hardware Volume Guard Lock")
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(150)
                    }
                } catch (_: Exception) {}
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun DashGuardApp(
    viewModel: DashcamViewModel,
    modifier: Modifier = Modifier
) {
    val navigationBackStack = rememberSaveable(
        saver = listSaver(
            save = { it.map { tab -> tab.name } },
            restore = { savedList ->
                val stack = mutableStateListOf<NavigationTab>()
                stack.addAll(savedList.mapNotNull { name ->
                    runCatching { NavigationTab.valueOf(name) }.getOrNull()
                })
                if (stack.isEmpty()) stack.add(NavigationTab.CAMERA)
                stack
            }
        )
    ) {
        mutableStateListOf(NavigationTab.CAMERA)
    }

    val saveableStateHolder = rememberSaveableStateHolder()

    val currentTab = navigationBackStack.lastOrNull() ?: NavigationTab.CAMERA

    fun navigateToTab(tab: NavigationTab) {
        if (navigationBackStack.lastOrNull() != tab) {
            navigationBackStack.add(tab)
        }
    }

    fun navigateBack() {
        if (navigationBackStack.size > 1) {
            val exitingTab = navigationBackStack.removeAt(navigationBackStack.lastIndex)
            if (exitingTab == NavigationTab.GALLERY) {
                viewModel.resetGalleryNavigationMemory()
            }
            saveableStateHolder.removeState(exitingTab.name)
        }
    }

    var selectedClipFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val allClipsList by viewModel.allClips.collectAsStateWithLifecycle()
    var transientSelectedClip by remember { mutableStateOf<VideoClip?>(null) }

    val selectedClipForPlayback = if (selectedClipFilePath != null) {
        allClipsList.find { it.filePath == selectedClipFilePath } ?: transientSelectedClip
    } else null
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tripsList by viewModel.trips.collectAsStateWithLifecycle()

    val parentTrip = remember(selectedClipForPlayback, tripsList) {
        if (selectedClipForPlayback != null) {
            viewModel.getTripForClip(selectedClipForPlayback)
        } else {
            null
        }
    }
    val (adjacentPrevClip, adjacentNextClip) = remember(selectedClipForPlayback, parentTrip, settings.loopAdjacencyThresholdSeconds) {
        if (selectedClipForPlayback != null && parentTrip != null) {
            viewModel.getAdjacentClips(selectedClipForPlayback)
        } else {
            Pair(null, null)
        }
    }
    // Tier 2: trip-scoped skip — always navigable within the same trip regardless of gap
    val (tripPrevClip, tripNextClip) = remember(selectedClipForPlayback, parentTrip) {
        if (selectedClipForPlayback != null && parentTrip != null) {
            viewModel.getTripAdjacentClips(selectedClipForPlayback, parentTrip)
        } else {
            Pair(null, null)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Lock/prevent orientation changes when recording is active regardless of starting orientation
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(uiState.isRecording) {
        activity?.let { act ->
            if (uiState.isRecording) {
                act.lockOrientationToCurrent()
            } else {
                act.unlockOrientation()
            }
        }
    }

    // Hide navigation if OLED power saver / auto-dim mode is active, video is playing, or Sentry/Thermal Cooling is active
    val isSentryOrCooling = uiState.isSentryArmed || settings.currentMode == AppMode.SENTRY || uiState.isThermalCoolingActive
    val showNav = !settings.oledPowerSaveMode && !uiState.isScreenDimmed && !isSentryOrCooling && selectedClipForPlayback == null

    // Speed Lockout Barrier: Active if vehicle is moving > 10 mph (~16 km/h) or within the 5-second hysteresis barrier
    val isDrivingLockoutActive by viewModel.isDrivingLockoutActive.collectAsStateWithLifecycle()
    val isMoving = isDrivingLockoutActive || telemetry.speedKmh > 16.0f
    val isDeepMenuLocked = isMoving && (currentTab != NavigationTab.CAMERA || selectedClipForPlayback != null)

    // Centralized In-App Back Navigation Dispatcher
    BackHandler(enabled = isDeepMenuLocked || uiState.isSpeedLockoutAlertVisible) {
        navigationBackStack.clear()
        navigationBackStack.add(NavigationTab.CAMERA)
        selectedClipFilePath = null
        transientSelectedClip = null
        viewModel.closePlayback()
        viewModel.dismissSpeedLockoutAlert()
    }

    BackHandler(enabled = selectedClipForPlayback != null) {
        selectedClipFilePath = null
        transientSelectedClip = null
        viewModel.closePlayback()
    }

    BackHandler(enabled = navigationBackStack.size > 1 && selectedClipForPlayback == null) {
        navigateBack()
    }

    if (isDeepMenuLocked || uiState.isSpeedLockoutAlertVisible) {
        SpeedSafetyLockoutOverlay(
            currentSpeedKmh = telemetry.speedKmh,
            speedUnit = settings.speedUnit,
            onReturnToHud = {
                navigationBackStack.clear()
                navigationBackStack.add(NavigationTab.CAMERA)
                selectedClipFilePath = null
                transientSelectedClip = null
                viewModel.closePlayback()
                viewModel.dismissSpeedLockoutAlert()
            }
        )
    } else if (selectedClipForPlayback != null) {
        saveableStateHolder.SaveableStateProvider("video_player") {
            VideoPlayerScreen(
                clip = selectedClipForPlayback,
                previousClip = adjacentPrevClip,
                nextClip = adjacentNextClip,
                tripPreviousClip = tripPrevClip,
                tripNextClip = tripNextClip,
                adjacencyThresholdSeconds = settings.loopAdjacencyThresholdSeconds,
                trip = parentTrip,
                onSelectClip = { targetClip ->
                    viewModel.openClipForPlayback(targetClip, forceRestart = true)
                    selectedClipFilePath = targetClip.filePath
                    transientSelectedClip = targetClip
                },
                onToggleAutoAdvance = {
                    viewModel.toggleContinuousPlayback()
                },
                playbackState = playbackState,
                speedUnit = settings.speedUnit,
                onUpdatePlaybackState = { viewModel.updatePlaybackState(it) },
                onBack = {
                    selectedClipFilePath = null
                    transientSelectedClip = null
                    viewModel.closePlayback()
                },
                onToggleLock = { clip ->
                    viewModel.toggleClipLock(clip)
                    transientSelectedClip = clip.copy(isLocked = !clip.isLocked)
                },
                modifier = modifier.fillMaxSize()
            )
        }
    } else if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(AutomotivePitchBlack)
        ) {
            if (showNav) {
                NavigationRail(
                    containerColor = CockpitPanel,
                    contentColor = Color.White,
                    modifier = Modifier
                        .fillMaxHeight()
                        .testTag("main_bottom_navigation")
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NavigationTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = {
                                    selectedClipFilePath = null
                                    transientSelectedClip = null
                                    navigateToTab(tab)
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) Color.Black else Color(0xFF94A3B8)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CyberCyan else Color(0xFF94A3B8)
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = CyberCyan,
                                    selectedIconColor = Color.Black,
                                    unselectedIconColor = Color(0xFF94A3B8)
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                saveableStateHolder.SaveableStateProvider(currentTab.name) {
                    AppScreenContent(
                        currentTab = currentTab,
                        viewModel = viewModel,
                        onNavigateToTab = ::navigateToTab,
                        onNavigateBack = ::navigateBack,
                        onPlayClip = { clip ->
                            transientSelectedClip = clip
                            selectedClipFilePath = clip.filePath
                            viewModel.openClipForPlayback(clip)
                        }
                    )
                }
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showNav) {
                    NavigationBar(
                        containerColor = CockpitPanel,
                        contentColor = Color.White,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("main_bottom_navigation")
                    ) {
                        NavigationTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    selectedClipFilePath = null
                                    transientSelectedClip = null
                                    navigateToTab(tab)
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) Color.Black else Color(0xFF94A3B8)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CyberCyan else Color(0xFF94A3B8)
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = CyberCyan,
                                    selectedIconColor = Color.Black,
                                    unselectedIconColor = Color(0xFF94A3B8)
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            containerColor = AutomotiveDark,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showNav) innerPadding.calculateBottomPadding() else 0.dp)
            ) {
                saveableStateHolder.SaveableStateProvider(currentTab.name) {
                    AppScreenContent(
                        currentTab = currentTab,
                        viewModel = viewModel,
                        onNavigateToTab = ::navigateToTab,
                        onNavigateBack = ::navigateBack,
                        onPlayClip = { clip ->
                            transientSelectedClip = clip
                            selectedClipFilePath = clip.filePath
                            viewModel.openClipForPlayback(clip)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Single, authoritative screen navigation router shared between landscape NavigationRail
 * and portrait Scaffold branches, eliminating duplicate when (currentTab) blocks.
 */
@Composable
fun AppScreenContent(
    currentTab: NavigationTab,
    viewModel: DashcamViewModel,
    onNavigateToTab: (NavigationTab) -> Unit,
    onNavigateBack: () -> Unit,
    onPlayClip: (VideoClip) -> Unit
) {
    when (currentTab) {
        NavigationTab.CAMERA -> CameraHudScreen(
            viewModel = viewModel,
            onNavigateToSettings = { onNavigateToTab(NavigationTab.SETTINGS) }
        )
        NavigationTab.GALLERY -> GalleryScreen(
            viewModel = viewModel,
            onPlayClip = onPlayClip,
            onNavigateBack = onNavigateBack
        )
        NavigationTab.OPTIMIZATION -> OptimizationScreen(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack
        )
        NavigationTab.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onNavigateToOptimization = { onNavigateToTab(NavigationTab.OPTIMIZATION) }
        )
    }
}

/**
 * Safely resolves the host Activity from a Compose or Android Context.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Freezes and locks the activity orientation to the current physical orientation
 * (Portrait, Landscape, Reverse-Portrait, or Reverse-Landscape) to prevent CameraX
 * surface teardown, stutter, or aspect-ratio distortion during active recording.
 */
fun Activity.lockOrientationToCurrent() {
    val currentOrientation = resources.configuration.orientation
    val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            display?.rotation ?: Surface.ROTATION_0
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    } else {
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }

    requestedOrientation = when (currentOrientation) {
        Configuration.ORIENTATION_LANDSCAPE -> {
            if (rotation == Surface.ROTATION_270) {
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        Configuration.ORIENTATION_PORTRAIT -> {
            if (rotation == Surface.ROTATION_180) {
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        else -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
}

/**
 * Releases the orientation lock and restores the user's natural auto-rotation policy.
 */
fun Activity.unlockOrientation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
}
