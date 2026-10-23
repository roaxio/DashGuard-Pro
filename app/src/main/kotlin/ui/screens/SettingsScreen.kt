package io.github.roaxio.dashguardpro.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.ui.components.common.DashboardTopAppBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.storage.StorageMigrationManager
import io.github.roaxio.dashguardpro.ui.components.settings.DirectoryViewfinderDialog
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel

import io.github.roaxio.dashguardpro.ui.components.settings.PendingStorageChange
import io.github.roaxio.dashguardpro.ui.components.settings.SectionHeader
import io.github.roaxio.dashguardpro.ui.components.settings.SettingsCard
import io.github.roaxio.dashguardpro.ui.components.settings.CustomSentryArmingDelayDialog
import io.github.roaxio.dashguardpro.ui.components.settings.CustomSentryDemoteTempDialog
import io.github.roaxio.dashguardpro.ui.components.settings.CustomSentryIncidentDurationDialog
import io.github.roaxio.dashguardpro.ui.components.settings.CustomTripGapDialog
import io.github.roaxio.dashguardpro.ui.components.settings.InsufficientStorageMigrationDialog
import io.github.roaxio.dashguardpro.ui.components.settings.MigrationProgressDialog
import io.github.roaxio.dashguardpro.ui.components.settings.StorageLocationSubMenuDialog
import io.github.roaxio.dashguardpro.ui.components.settings.StorageMigrationPromptDialog
import io.github.roaxio.dashguardpro.ui.components.settings.StorageQuotaInfoDialog
import io.github.roaxio.dashguardpro.ui.components.settings.SmartPlaybackSubMenuScreen
import io.github.roaxio.dashguardpro.ui.components.settings.SentrySubMenuScreen
import io.github.roaxio.dashguardpro.ui.components.settings.StorageSettingsSection
import io.github.roaxio.dashguardpro.ui.components.settings.DashcamSettingsSection
import io.github.roaxio.dashguardpro.ui.components.settings.SensorsSettingsSection
import io.github.roaxio.dashguardpro.ui.components.settings.RecordingMediaSettingsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashcamViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToOptimization: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val systemOptState by viewModel.systemOptState.collectAsStateWithLifecycle()
    val cameraProfile by viewModel.cameraProfile.collectAsStateWithLifecycle()

    val migrationProgress by viewModel.migrationState.collectAsStateWithLifecycle()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var showStorageSubMenuDialog by remember { mutableStateOf(false) }
    var showViewfinderDialog by remember { mutableStateOf(false) }
    var showQuotaInfoDialog by remember { mutableStateOf(false) }
    var showStopRecordingToChangeStorageDialog by remember { mutableStateOf(false) }
    var pendingStorageChange by remember { mutableStateOf<PendingStorageChange?>(null) }
    var showMigrationPromptDialog by remember { mutableStateOf(false) }
    var showInsufficientSpaceDialog by remember { mutableStateOf(false) }
    var pendingActiveMigrationClips by remember { mutableStateOf<List<VideoClip>>(emptyList()) }
    var pendingActiveMigrationBytes by remember { mutableStateOf(0L) }
    var pendingOtherMigrationClips by remember { mutableStateOf<List<VideoClip>>(emptyList()) }
    var pendingOtherMigrationBytes by remember { mutableStateOf(0L) }
    var pendingInsufficientRequiredBytes by remember { mutableStateOf(0L) }
    var showCustomTripGapDialog by remember { mutableStateOf(false) }
    var showCustomSentryArmingDialog by remember { mutableStateOf(false) }
    var showCustomSentryDurationDialog by remember { mutableStateOf(false) }
    var showCustomSentryDemoteTempDialog by remember { mutableStateOf(false) }
    var showSmartPlaybackSubMenu by rememberSaveable { mutableStateOf(false) }
    var showSentrySubMenu by rememberSaveable { mutableStateOf(false) }
    val settingsListState = rememberLazyListState()

    // Intercept phone system hardware back button
    androidx.activity.compose.BackHandler {
        if (showCustomSentryArmingDialog) {
            showCustomSentryArmingDialog = false
        } else if (showCustomSentryDurationDialog) {
            showCustomSentryDurationDialog = false
        } else if (showCustomSentryDemoteTempDialog) {
            showCustomSentryDemoteTempDialog = false
        } else if (showCustomTripGapDialog) {
            showCustomTripGapDialog = false
        } else if (showSmartPlaybackSubMenu) {
            showSmartPlaybackSubMenu = false
        } else if (showSentrySubMenu) {
            showSentrySubMenu = false
        } else if (migrationProgress != null && !migrationProgress!!.isCompleted) {
            // Active file migration in progress
        } else if (migrationProgress != null) {
            viewModel.dismissMigrationProgress()
        } else if (showInsufficientSpaceDialog) {
            showInsufficientSpaceDialog = false
            pendingStorageChange = null
        } else if (showMigrationPromptDialog) {
            showMigrationPromptDialog = false
            pendingStorageChange = null
        } else if (showStopRecordingToChangeStorageDialog) {
            showStopRecordingToChangeStorageDialog = false
        } else if (showViewfinderDialog) {
            showViewfinderDialog = false
        } else if (showStorageSubMenuDialog) {
            showStorageSubMenuDialog = false
        } else if (showQuotaInfoDialog) {
            showQuotaInfoDialog = false
        } else {
            onNavigateBack()
        }
    }

    // Strict single ON_RESUME event listener (no continuous polling loops)
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

    val activeDir = remember(settings.storageLocationType, settings.customStoragePath) {
        StorageDirectoryManager.resolveStorageDirectory(context, settings.storageLocationType, settings.customStoragePath)
    }
    val appUsedBytes = uiState.storageStats.appVideoBytes
    val storageMetrics = remember(activeDir, systemOptState.availableStorageOptions, appUsedBytes) {
        StorageDirectoryManager.getStorageMetrics(activeDir, appUsedBytes)
    }
    val maxDirectoryGb = storageMetrics.maxAssignableGb
    val dynamicQuotaOptions = remember(maxDirectoryGb) {
        StorageDirectoryManager.getDynamicQuotaOptions(maxDirectoryGb)
    }
    val activeOption = systemOptState.availableStorageOptions.find { it.type == settings.storageLocationType }

    fun handleStorageLocationChange(newType: StorageLocationType, newPath: String? = null, newUri: String? = null) {
        val targetDir = StorageDirectoryManager.resolveStorageDirectory(context, newType, newPath)
        val currentCanonical = try { activeDir.canonicalPath } catch (_: Exception) { activeDir.absolutePath }
        val targetCanonical = try { targetDir.canonicalPath } catch (_: Exception) { targetDir.absolutePath }

        if (currentCanonical == targetCanonical) {
            viewModel.updateStorageLocation(newType, newPath, newUri)
            return
        }

        val change = PendingStorageChange(newType, newPath, newUri, targetDir)
        pendingStorageChange = change

        coroutineScope.launch {
            viewModel.syncClipsFromStorageDirect()
            val activeClips = StorageMigrationManager.getClipsNeedingMigration(viewModel.getClipsInActiveStorage(), targetDir)
            val otherClips = StorageMigrationManager.getClipsNeedingMigration(viewModel.getClipsOutsideActiveStorage(), targetDir)

            if (activeClips.isNotEmpty() || otherClips.isNotEmpty()) {
                pendingActiveMigrationClips = activeClips
                pendingOtherMigrationClips = otherClips
                pendingActiveMigrationBytes = StorageMigrationManager.calculateTotalMigrationBytes(activeClips, targetDir)
                pendingOtherMigrationBytes = StorageMigrationManager.calculateTotalMigrationBytes(otherClips, targetDir)
                showMigrationPromptDialog = true
            } else {
                viewModel.updateStorageLocation(newType, newPath, newUri)
                Toast.makeText(context, "Storage location updated", Toast.LENGTH_SHORT).show()
                pendingStorageChange = null
            }
        }
    }

    // Storage Permission Request Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        viewModel.refreshSystemDiagnostics()
        viewModel.refreshStorageStats()
        if (allGranted) {
            Toast.makeText(context, "Storage permissions granted", Toast.LENGTH_SHORT).show()
        }
    }

    // All Files Access Settings Launcher for Android 11+ (API 30+)
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemDiagnostics()
        viewModel.refreshStorageStats()
    }

    // SAF Document Tree folder picker launcher via ACTION_OPEN_DOCUMENT_TREE
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            val realPath = StorageDirectoryManager.getPathFromTreeUri(context, uri)
            handleStorageLocationChange(
                newType = StorageLocationType.CUSTOM_DIRECTORY,
                newPath = realPath,
                newUri = uri.toString()
            )
        }
    }

    if (showSmartPlaybackSubMenu) {
        SmartPlaybackSubMenuScreen(
            settings = settings,
            viewModel = viewModel,
            onNavigateBack = { showSmartPlaybackSubMenu = false },
            onShowCustomTripGapDialog = { showCustomTripGapDialog = true },
            modifier = modifier
        )
    } else if (showSentrySubMenu) {
        SentrySubMenuScreen(
            settings = settings,
            viewModel = viewModel,
            onNavigateBack = { showSentrySubMenu = false },
            onShowCustomArmingDialog = { showCustomSentryArmingDialog = true },
            onShowCustomDurationDialog = { showCustomSentryDurationDialog = true },
            onShowCustomDemoteTempDialog = { showCustomSentryDemoteTempDialog = true },
            modifier = modifier
        )
    } else {
        Scaffold(
            topBar = {
                DashboardTopAppBar(
                    title = "DashGuard Settings",
                    onNavigateBack = {
                        if (showCustomSentryArmingDialog) {
                            showCustomSentryArmingDialog = false
                        } else if (showCustomSentryDurationDialog) {
                            showCustomSentryDurationDialog = false
                        } else if (showCustomSentryDemoteTempDialog) {
                            showCustomSentryDemoteTempDialog = false
                        } else if (showCustomTripGapDialog) {
                            showCustomTripGapDialog = false
                        } else if (showViewfinderDialog) {
                            showViewfinderDialog = false
                        } else if (showStorageSubMenuDialog) {
                            showStorageSubMenuDialog = false
                        } else if (showQuotaInfoDialog) {
                            showQuotaInfoDialog = false
                        } else {
                            onNavigateBack()
                        }
                    }
                )
            },
            containerColor = HudBlack,
            modifier = modifier
        ) { padding ->
        LazyColumn(
            state = settingsListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
                .testTag("settings_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // STORAGE & DIRECTORY MANAGEMENT SECTION
            item {
                val displayedActivePath = remember(settings.storageLocationType, settings.customStoragePath) {
                    viewModel.getActiveVideosDirectoryPath()
                }
                StorageSettingsSection(
                    displayedActivePath = displayedActivePath,
                    hasStoragePermission = systemOptState.hasStoragePermission,
                    activeOption = activeOption,
                    settings = settings,
                    maxDirectoryGb = maxDirectoryGb,
                    storageMetrics = storageMetrics,
                    appUsedBytes = appUsedBytes,
                    onRequestStoragePermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                allFilesAccessLauncher.launch(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                allFilesAccessLauncher.launch(intent)
                            }
                        } else {
                            permissionLauncher.launch(
                                StorageDirectoryManager.getRequiredStoragePermissions().toTypedArray()
                            )
                        }
                    },
                    onChangeStorageLocation = {
                        if (uiState.isRecording) {
                            showStopRecordingToChangeStorageDialog = true
                        } else {
                            showStorageSubMenuDialog = true
                        }
                    },
                    onShowQuotaInfo = { showQuotaInfoDialog = true },
                    onMaxStorageGbChanged = { newGb ->
                        viewModel.updateSettings(settings.copy(maxStorageGb = newGb))
                    }
                )
            }

            // DASHCAM LOOP RECORDING SECTION
            item {
                DashcamSettingsSection(
                    settings = settings,
                    cameraProfile = cameraProfile,
                    onSettingsChanged = { updated ->
                        viewModel.updateSettings(updated)
                    }
                )
            }

            // SMART SENSORS & TRIGGERS
            item {
                SensorsSettingsSection(
                    settings = settings,
                    onSettingsChanged = { updated ->
                        viewModel.updateSettings(updated)
                    }
                )
            }

            // SMART PLAYBACK & TRIP CONTINUITY SUB-MENU
            item {
                SectionHeader("SMART PLAYBACK & TRIP CONTINUITY")
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSmartPlaybackSubMenu = true }
                            .padding(12.dp)
                            .testTag("settings_smart_playback_sub_menu_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trip Continuity & Playback Settings",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val currentGapDisplay = if (listOf(1, 2, 3, 5, 10).contains(settings.tripGroupingGapMinutes)) {
                                "${settings.tripGroupingGapMinutes}m Gap"
                            } else {
                                "${settings.tripGroupingGapMinutes}m (Custom)"
                            }
                            val autoAdvDisplay = if (settings.autoAdvancePlayback) "Auto-Advance On" else "Auto-Advance Off"
                            Text(
                                text = "$currentGapDisplay • ${settings.loopAdjacencyThresholdSeconds}s Adjacency • $autoAdvDisplay",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Trip Continuity & Playback Settings",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // VEHICLE POWER & HARDWARE AUTOMATION NOTICE
            item {
                SectionHeader("VEHICLE POWER & HARDWARE AUTOMATION")
                Card(
                    onClick = onNavigateToOptimization,
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = HudSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_go_to_optimize")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Consolidated into Optimize Tab",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ignition Auto-Start, Auto-Stop, Auto-Dim, Low Battery & Thermal Cutoffs, and Do Not Disturb are now centralized in the Optimize tab. Tap to open.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Optimize Tab",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // SENTRY MODE (PARK & PROTECT) SUB-MENU
            item {
                SectionHeader("SENTRY MODE (PARK & PROTECT)")
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSentrySubMenu = true }
                            .padding(12.dp)
                            .testTag("settings_sentry_sub_menu_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sentry Mode (Park & Protect)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val statusText = if (settings.sentryModeEnabled) {
                                val triggerText = settings.sentryArmingTrigger.label
                                val durationText = "${settings.sentryIncidentDurationSeconds}s"
                                "Enabled • $triggerText • $durationText Clips"
                            } else {
                                "Disabled • Tap to configure parked triggers & surveillance"
                            }
                            Text(
                                text = statusText,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Sentry Mode Sub-Menu",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // MEDIA & WATERMARK
            item {
                RecordingMediaSettingsSection(
                    settings = settings,
                    onSettingsChanged = { updated ->
                        viewModel.updateSettings(updated)
                    }
                )
            }
        }
    }
    }

    // ACTIVE RECORDING STORAGE CHANGE GUARD DIALOG
    if (showStopRecordingToChangeStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStopRecordingToChangeStorageDialog = false },
            containerColor = HudSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SafetyAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recording Active", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "A dashcam recording is currently in progress. Changing the storage directory requires stopping the active recording first to safely finalize and protect the current video file.\n\nWould you like to stop recording and proceed to change the storage location?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stopRecording()
                        showStopRecordingToChangeStorageDialog = false
                        showStorageSubMenuDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Stop Recording & Change", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showStopRecordingToChangeStorageDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // STORAGE LOCATIONS SUB-MENU DIALOG
    if (showStorageSubMenuDialog) {
        StorageLocationSubMenuDialog(
            availableOptions = systemOptState.availableStorageOptions,
            selectedType = settings.storageLocationType,
            onDismiss = { showStorageSubMenuDialog = false },
            onSelectLocation = { opt ->
                showStorageSubMenuDialog = false
                if (opt.type == StorageLocationType.CUSTOM_DIRECTORY) {
                    showViewfinderDialog = true
                } else {
                    handleStorageLocationChange(opt.type)
                }
            },
            onOpenViewfinder = {
                showStorageSubMenuDialog = false
                showViewfinderDialog = true
            },
            onLaunchSafPicker = {
                showStorageSubMenuDialog = false
                folderPickerLauncher.launch(null)
            }
        )
    }

    // STORAGE QUOTA INFO DIALOG ('?' help)
    if (showQuotaInfoDialog) {
        StorageQuotaInfoDialog(
            metrics = storageMetrics,
            onDismiss = { showQuotaInfoDialog = false }
        )
    }

    // In-App Directory Viewfinder & Folder Explorer Dialog
    if (showViewfinderDialog) {
        DirectoryViewfinderDialog(
            initialPath = settings.customStoragePath,
            onDismiss = { showViewfinderDialog = false },
            onConfirmFolder = { chosenPath ->
                showViewfinderDialog = false
                handleStorageLocationChange(
                    newType = StorageLocationType.CUSTOM_DIRECTORY,
                    newPath = chosenPath,
                    newUri = ""
                )
            },
            onLaunchSystemPicker = {
                showViewfinderDialog = false
                folderPickerLauncher.launch(null)
            }
        )
    }

    // STORAGE MIGRATION PROMPT DIALOG (Move vs Keep in place)
    if (showMigrationPromptDialog && pendingStorageChange != null) {
        val change = pendingStorageChange!!
        StorageMigrationPromptDialog(
            sourcePath = activeDir.absolutePath,
            targetPath = change.targetDir.absolutePath,
            activeClipCount = pendingActiveMigrationClips.size,
            activeSizeBytes = pendingActiveMigrationBytes,
            otherClipCount = pendingOtherMigrationClips.size,
            otherSizeBytes = pendingOtherMigrationBytes,
            onMoveActiveFootage = {
                val canFit = StorageMigrationManager.canAccommodateMigration(change.targetDir, pendingActiveMigrationBytes)
                if (canFit) {
                    showMigrationPromptDialog = false
                    viewModel.migrateFootageToNewDirectory(change.targetDir, pendingActiveMigrationClips) { success, _ ->
                        if (success) {
                            viewModel.updateStorageLocation(change.locationType, change.customPath, change.customUri)
                        }
                    }
                } else {
                    pendingInsufficientRequiredBytes = pendingActiveMigrationBytes
                    showMigrationPromptDialog = false
                    showInsufficientSpaceDialog = true
                }
            },
            onConsolidateAllFootage = {
                val allClips = pendingActiveMigrationClips + pendingOtherMigrationClips
                val totalBytes = pendingActiveMigrationBytes + pendingOtherMigrationBytes
                val canFit = StorageMigrationManager.canAccommodateMigration(change.targetDir, totalBytes)
                if (canFit) {
                    showMigrationPromptDialog = false
                    viewModel.migrateFootageToNewDirectory(change.targetDir, allClips) { success, _ ->
                        if (success) {
                            viewModel.updateStorageLocation(change.locationType, change.customPath, change.customUri)
                        }
                    }
                } else {
                    pendingInsufficientRequiredBytes = totalBytes
                    showMigrationPromptDialog = false
                    showInsufficientSpaceDialog = true
                }
            },
            onKeepInPlace = {
                showMigrationPromptDialog = false
                viewModel.updateStorageLocation(change.locationType, change.customPath, change.customUri)
                Toast.makeText(context, "Storage location updated (Footage preserved in previous locations)", Toast.LENGTH_LONG).show()
                pendingStorageChange = null
            },
            onDismiss = {
                showMigrationPromptDialog = false
                pendingStorageChange = null
            }
        )
    }

    // INSUFFICIENT STORAGE MIGRATION DIALOG
    if (showInsufficientSpaceDialog && pendingStorageChange != null) {
        val change = pendingStorageChange!!
        val targetStat = try { StatFs(change.targetDir.path) } catch (_: Exception) { null }
        val targetAvail = targetStat?.availableBytes ?: 0L
        InsufficientStorageMigrationDialog(
            requiredBytes = pendingInsufficientRequiredBytes,
            availableBytes = targetAvail,
            bufferBytes = StorageDirectoryManager.getOsSafetyBufferBytes(change.targetDir),
            onKeepInPlaceAndSwitch = {
                showInsufficientSpaceDialog = false
                viewModel.updateStorageLocation(change.locationType, change.customPath, change.customUri)
                Toast.makeText(context, "Storage location updated (Footage preserved in previous locations)", Toast.LENGTH_LONG).show()
                pendingStorageChange = null
            },
            onDismiss = {
                showInsufficientSpaceDialog = false
                pendingStorageChange = null
            }
        )
    }

    // MIGRATION PROGRESS DIALOG
    if (migrationProgress != null) {
        MigrationProgressDialog(
            progress = migrationProgress!!,
            onDismiss = {
                viewModel.dismissMigrationProgress()
                pendingStorageChange = null
            }
        )
    }

    // CUSTOM TRIP INACTIVITY GAP DIALOG
    if (showCustomTripGapDialog) {
        CustomTripGapDialog(
            currentMinutes = settings.tripGroupingGapMinutes,
            adjacencyThresholdSeconds = settings.loopAdjacencyThresholdSeconds,
            onDismiss = { showCustomTripGapDialog = false },
            onConfirm = { customMin ->
                viewModel.updateSettings(settings.copy(tripGroupingGapMinutes = customMin))
                showCustomTripGapDialog = false
            }
        )
    }

    // CUSTOM SENTRY ARMING DELAY DIALOG
    if (showCustomSentryArmingDialog) {
        CustomSentryArmingDelayDialog(
            currentSeconds = settings.sentryArmingDelaySeconds,
            onDismiss = { showCustomSentryArmingDialog = false },
            onConfirm = { customSec ->
                viewModel.updateSettings(settings.copy(sentryArmingDelaySeconds = customSec))
                showCustomSentryArmingDialog = false
            }
        )
    }

    // CUSTOM SENTRY INCIDENT DURATION DIALOG
    if (showCustomSentryDurationDialog) {
        CustomSentryIncidentDurationDialog(
            currentSeconds = settings.sentryIncidentDurationSeconds,
            onDismiss = { showCustomSentryDurationDialog = false },
            onConfirm = { customSec ->
                viewModel.updateSettings(settings.copy(sentryIncidentDurationSeconds = customSec))
                showCustomSentryDurationDialog = false
            }
        )
    }

    // CUSTOM SENTRY DEMOTION TEMPERATURE LIMIT DIALOG
    if (showCustomSentryDemoteTempDialog) {
        val maxAllowed = if (settings.maxTempThresholdC > 0f) (settings.maxTempThresholdC - 4.0f).toInt() else 50
        CustomSentryDemoteTempDialog(
            currentTempC = settings.sentryAutoDemoteTempC,
            maxAllowedC = maxAllowed,
            onDismiss = { showCustomSentryDemoteTempDialog = false },
            onConfirm = { customTemp ->
                viewModel.updateSettings(settings.copy(sentryAutoDemoteTempIsAuto = false, sentryAutoDemoteTempC = customTemp))
                showCustomSentryDemoteTempDialog = false
            }
        )
    }
}
