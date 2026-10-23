package io.github.roaxio.dashguardpro.ui.components.optimization

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import io.github.roaxio.dashguardpro.ui.components.common.DashboardTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roaxio.dashguardpro.system.SystemOptimizationState
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.util.IntentUtils
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Dedicated sub-menu screen hosting the 5-step Android & OEM protection workflow.
 * Guides drivers through Doze mode bypass, Kiosk mode pinning, Silent Cockpit DND automation,
 * manufacturer autostart whitelist, and windshield thermal health.
 */
@Composable
fun SystemProtectionSubMenuScreen(
    viewModel: DashcamViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val systemOptState by viewModel.systemOptState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showKioskGuideDialog by remember { mutableStateOf(false) }
    var showOemGuideDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (showKioskGuideDialog) {
            showKioskGuideDialog = false
        } else if (showOemGuideDialog) {
            showOemGuideDialog = false
        } else {
            onNavigateBack()
        }
    }

    // Dynamic, periodic sync while screen is active in foreground
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1500)
            viewModel.refreshSystemDiagnostics()
        }
    }

    // Deterministically refresh diagnostics whenever window focus changes (e.g. OS App Pinning prompt closes)
    LaunchedEffect(windowInfo.isWindowFocused) {
        viewModel.refreshSystemDiagnostics()
    }

    // Manufacturer detection for targeted guidance
    val manufacturer = remember {
        Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    val isXiaomi = remember {
        listOf("xiaomi", "redmi", "poco").any { Build.MANUFACTURER.contains(it, ignoreCase = true) }
    }
    val isSamsung = remember {
        Build.MANUFACTURER.contains("samsung", ignoreCase = true)
    }
    val isHuawei = remember {
        listOf("huawei", "honor").any { Build.MANUFACTURER.contains(it, ignoreCase = true) }
    }
    val isOppoFamily = remember {
        listOf("oppo", "realme", "oneplus").any { Build.MANUFACTURER.contains(it, ignoreCase = true) }
    }

    val batteryWhitelistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemDiagnostics()
    }

    val dndSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemDiagnostics()
    }

    val appDetailsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemDiagnostics()
    }

    val securitySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemDiagnostics()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun launchBatteryWhitelist() {
        batteryWhitelistLauncher.launch(IntentUtils.createBatteryOptimizationIntent(context))
    }

    fun launchDndSettings() {
        dndSettingsLauncher.launch(IntentUtils.createDndSettingsIntent())
    }

    fun launchDndRulesConfiguration() {
        IntentUtils.launchDndRulesConfiguration(context)
    }

    fun launchAppDetails() {
        appDetailsLauncher.launch(IntentUtils.createAppDetailsIntent(context))
    }

    fun toggleAppPinning() {
        IntentUtils.toggleAppPinning(context, systemOptState.isAppPinned) {
            showKioskGuideDialog = true
        }
        viewModel.refreshSystemDiagnostics()
    }

    if (showKioskGuideDialog) {
        KioskGuideDialog(
            onDismiss = { showKioskGuideDialog = false },
            onOpenSecuritySettings = {
                securitySettingsLauncher.launch(IntentUtils.createSecuritySettingsIntent())
            },
            onTryPinNow = {
                toggleAppPinning()
            }
        )
    }

    if (showOemGuideDialog) {
        OemGuideDialog(
            manufacturer = manufacturer,
            isXiaomi = isXiaomi,
            isSamsung = isSamsung,
            isHuawei = isHuawei,
            isOppo = isOppoFamily,
            onDismiss = { showOemGuideDialog = false },
            onOpenAppDetails = { launchAppDetails() }
        )
    }

    SystemProtectionSubMenuScreen(
        systemOptState = systemOptState,
        oemOptimizationCompleted = settings.oemOptimizationCompleted,
        autoDndWhileRecording = settings.autoDndWhileRecording,
        onNavigateBack = onNavigateBack,
        onLaunchBatteryWhitelist = { launchBatteryWhitelist() },
        onToggleAppPinning = { toggleAppPinning() },
        onShowKioskGuide = { showKioskGuideDialog = true },
        onLaunchDndSettings = { launchDndSettings() },
        onToggleAutoDnd = { enabled ->
            viewModel.updateSettings(settings.copy(autoDndWhileRecording = enabled))
        },
        onConfigureDndRules = { launchDndRulesConfiguration() },
        onLaunchAppDetails = { launchAppDetails() },
        onShowOemGuide = { showOemGuideDialog = true },
        onToggleOemOptimizationCompleted = { completed ->
            viewModel.updateSettings(settings.copy(oemOptimizationCompleted = completed))
        },
        manufacturer = manufacturer,
        modifier = modifier
    )
}

/**
 * Pure, stateless composable overload for SystemProtectionSubMenuScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemProtectionSubMenuScreen(
    systemOptState: SystemOptimizationState,
    oemOptimizationCompleted: Boolean,
    autoDndWhileRecording: Boolean,
    onNavigateBack: () -> Unit,
    onLaunchBatteryWhitelist: () -> Unit,
    onToggleAppPinning: () -> Unit,
    onShowKioskGuide: () -> Unit,
    onLaunchDndSettings: () -> Unit,
    onToggleAutoDnd: (Boolean) -> Unit,
    onConfigureDndRules: () -> Unit,
    onLaunchAppDetails: () -> Unit,
    onShowOemGuide: () -> Unit,
    onToggleOemOptimizationCompleted: (Boolean) -> Unit,
    manufacturer: String = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
    modifier: Modifier = Modifier
) {
    val subMenuListState = rememberLazyListState()

    Scaffold(
        topBar = {
            DashboardTopAppBar(
                title = "System & OEM Protection",
                subtitle = "Android & Background Hardening",
                onNavigateBack = onNavigateBack,
                navigationIconContentDescription = "Back to Optimization",
                navigationIconTestTag = "btn_back_system_protection_sub_menu"
            )
        },
        containerColor = HudBlack,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            state = subMenuListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
                .testTag("system_protection_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Battery Whitelist
            item {
                BatteryWhitelistStepCard(
                    isBatteryIgnored = systemOptState.isBatteryIgnored,
                    onLaunchBatteryWhitelist = onLaunchBatteryWhitelist
                )
            }

            // Kiosk Mode / App Pinning
            item {
                KioskAppPinningStepCard(
                    isAppPinned = systemOptState.isAppPinned,
                    onToggleAppPinning = onToggleAppPinning,
                    onShowKioskGuide = onShowKioskGuide
                )
            }

            // Silent Cockpit (DND)
            item {
                SilentCockpitDndCard(
                    isDndGranted = systemOptState.isDndGranted,
                    autoDndWhileRecording = autoDndWhileRecording,
                    onLaunchDndSettings = onLaunchDndSettings,
                    onToggleAutoDnd = onToggleAutoDnd,
                    onConfigureDndRules = onConfigureDndRules
                )
            }

            // OEM Background Protection
            item {
                OemBackgroundProtectionCard(
                    manufacturer = manufacturer,
                    oemOptimizationCompleted = oemOptimizationCompleted,
                    onLaunchAppDetails = onLaunchAppDetails,
                    onShowOemGuide = onShowOemGuide,
                    onToggleOemOptimizationCompleted = onToggleOemOptimizationCompleted
                )
            }
        }
    }
}
