package io.github.roaxio.dashguardpro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.ui.components.common.DashboardTopAppBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roaxio.dashguardpro.data.settings.AutoDimTimeout
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.ui.components.optimization.CustomLowBatteryDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.CustomSleepEmergencyDurationDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.CustomThermalTempDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.HardwareDiagnosticsSection
import io.github.roaxio.dashguardpro.ui.components.optimization.KioskGuideDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.SystemProtectionSubMenuScreen
import io.github.roaxio.dashguardpro.ui.components.optimization.VehiclePowerAutomationSection
import io.github.roaxio.dashguardpro.ui.components.optimization.WindshieldThermalGuideCard
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.util.IntentUtils
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Optimization Screen: In-car appliance optimization and kiosk configuration.
 * Orchestrates live hardware diagnostics, system protection step cards, and vehicle automation sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizationScreen(
    viewModel: DashcamViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val systemOptState by viewModel.systemOptState.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showSystemProtectionSubMenu by rememberSaveable { mutableStateOf(false) }
    val optimizationListState = rememberLazyListState()
    var showKioskGuideDialog by remember { mutableStateOf(false) }
    var showCustomLowBatteryDialog by remember { mutableStateOf(false) }
    var showCustomWarmDimDialog by remember { mutableStateOf(false) }
    var showCustomThermalRecoveryDialog by remember { mutableStateOf(false) }
    var showCustomSleepEmergencyDialog by remember { mutableStateOf(false) }

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

    fun handleBackNavigation() {
        when {
            showSystemProtectionSubMenu -> showSystemProtectionSubMenu = false
            showKioskGuideDialog -> showKioskGuideDialog = false
            showCustomLowBatteryDialog -> showCustomLowBatteryDialog = false
            showCustomWarmDimDialog -> showCustomWarmDimDialog = false
            showCustomThermalRecoveryDialog -> showCustomThermalRecoveryDialog = false
            showCustomSleepEmergencyDialog -> showCustomSleepEmergencyDialog = false
            else -> onNavigateBack()
        }
    }

    BackHandler {
        handleBackNavigation()
    }

    // Storage capacity calculation
    val activeDir = remember(settings.storageLocationType, settings.customStoragePath) {
        StorageDirectoryManager.resolveStorageDirectory(context, settings.storageLocationType, settings.customStoragePath)
    }
    val appUsedBytes = uiState.storageStats.appVideoBytes
    val storageMetrics = remember(activeDir, systemOptState.availableStorageOptions, appUsedBytes) {
        StorageDirectoryManager.getStorageMetrics(activeDir, appUsedBytes)
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
                viewModel.refreshStorageStats()
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

    if (showCustomLowBatteryDialog) {
        CustomLowBatteryDialog(
            currentPct = settings.lowBatterySafetyLimitPct,
            onDismiss = { showCustomLowBatteryDialog = false },
            onConfirm = { pct ->
                viewModel.updateSettings(settings.copy(lowBatterySafetyLimitPct = pct))
                showCustomLowBatteryDialog = false
            }
        )
    }

    if (showCustomWarmDimDialog) {
        val maxAllowed = if (settings.maxTempThresholdC > 0f) (settings.maxTempThresholdC - 4.0f).toInt() else 60
        CustomThermalTempDialog(
            title = "Custom Warm Auto-Dim Temp",
            description = "Specify custom warm warning temperature (30°C to ${maxAllowed}°C). Screen proactively dims when temperature reaches this threshold (max allowed is 4°C below Max Cutoff).",
            currentTempC = settings.thermalAutoDimTempC,
            minTempC = 30,
            maxTempC = maxAllowed,
            onDismiss = { showCustomWarmDimDialog = false },
            onConfirm = { temp ->
                viewModel.updateSettings(settings.copy(thermalAutoDimIsAuto = false, thermalAutoDimTempC = temp))
                showCustomWarmDimDialog = false
            }
        )
    }

    if (showCustomThermalRecoveryDialog) {
        val maxAllowed = if (settings.maxTempThresholdC > 0f) (settings.maxTempThresholdC - 2.0f).toInt() else 60
        CustomThermalTempDialog(
            title = "Custom Cool-Off Recovery Target",
            description = "Specify custom cool-off recovery target temperature (30°C to ${maxAllowed}°C). Recording safely resumes once cooled down to this threshold (max allowed is 2°C below Max Cutoff).",
            currentTempC = settings.thermalRecoveryTargetTempC,
            minTempC = 30,
            maxTempC = maxAllowed,
            onDismiss = { showCustomThermalRecoveryDialog = false },
            onConfirm = { temp ->
                viewModel.updateSettings(settings.copy(thermalRecoveryIsAuto = false, thermalRecoveryTargetTempC = temp))
                showCustomThermalRecoveryDialog = false
            }
        )
    }

    if (showCustomSleepEmergencyDialog) {
        CustomSleepEmergencyDurationDialog(
            currentDurationSeconds = settings.sleepEmergencyClipDurationSeconds,
            onDismiss = { showCustomSleepEmergencyDialog = false },
            onConfirm = { sec ->
                viewModel.updateSettings(settings.copy(sleepEmergencyClipDurationSeconds = sec))
                showCustomSleepEmergencyDialog = false
            }
        )
    }

    if (showSystemProtectionSubMenu) {
        SystemProtectionSubMenuScreen(
            viewModel = viewModel,
            onNavigateBack = { showSystemProtectionSubMenu = false },
            modifier = modifier
        )
    } else {
        Scaffold(
            topBar = {
                DashboardTopAppBar(
                    title = "Dedicated Dashcam Optimization",
                    onNavigateBack = { handleBackNavigation() }
                )
            },
            containerColor = HudBlack,
            modifier = modifier
        ) { padding ->
        LazyColumn(
            state = optimizationListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
                .testTag("optimization_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Overview Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dedicated In-Car Phone Setup",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Configure this Android device as a resilient, autonomous 24/7 in-car dashcam appliance. Eliminate background OS throttling, prevent battery sleep, and automate power ignition.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Live Diagnostics Grid
            item {
                HardwareDiagnosticsSection(
                    battery = battery,
                    telemetry = telemetry,
                    settings = settings,
                    systemOptState = systemOptState,
                    storageMetrics = storageMetrics,
                    onSetAutoDim10s = {
                        viewModel.updateSettings(settings.copy(autoDimTimeout = AutoDimTimeout.SEC_10))
                    },
                    onLaunchBatteryWhitelist = {
                        launchBatteryWhitelist()
                    },
                    onToggleAppPinning = {
                        toggleAppPinning()
                    },
                    onLaunchDndSettings = {
                        launchDndSettings()
                    }
                )
            }

            // Consolidated Vehicle Power & Thermal Automation
            item {
                VehiclePowerAutomationSection(
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        viewModel.updateSettings(newSettings)
                    },
                    onShowCustomLowBattery = { showCustomLowBatteryDialog = true },
                    onShowCustomWarmDim = { showCustomWarmDimDialog = true },
                    onShowCustomThermalRecovery = { showCustomThermalRecoveryDialog = true },
                    onShowCustomSleepEmergency = { showCustomSleepEmergencyDialog = true }
                )
            }

            // Android & OEM Protection Steps Sub-Menu Entry Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = HudSurface),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Android & OEM Protection",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Device Hardening Sub-Menu",
                                    color = CyberCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Exempt DashGuard from Android Doze, lock device in Kiosk mode, automate Silent Cockpit DND, and configure OEM background restrictions.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showSystemProtectionSubMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("btn_open_system_protection_submenu")
                        ) {
                            Text(
                                text = "Configure Device Protection →",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Windshield Thermal & Hardware Health Guide Card
            item {
                WindshieldThermalGuideCard()
            }
        }
    }
}
}

