package io.github.roaxio.dashguardpro.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roaxio.dashguardpro.model.GalleryFilter
import io.github.roaxio.dashguardpro.model.GalleryViewMode
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.util.IntentUtils
import io.github.roaxio.dashguardpro.util.videoFile
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import io.github.roaxio.dashguardpro.ui.components.gallery.StorageBar
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.ui.components.gallery.ShareChoiceDialog
import io.github.roaxio.dashguardpro.ui.components.gallery.ClipItemRow
import io.github.roaxio.dashguardpro.ui.components.gallery.GalleryFilterBar
import io.github.roaxio.dashguardpro.ui.components.gallery.TripRenameDialog
import io.github.roaxio.dashguardpro.ui.components.gallery.TripSessionCard
import io.github.roaxio.dashguardpro.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: DashcamViewModel,
    onPlayClip: (VideoClip) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allClips by viewModel.allClips.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val galleryNavState by viewModel.galleryNavState.collectAsStateWithLifecycle()

    val viewMode = galleryNavState.viewMode
    val selectedFilter = galleryNavState.selectedFilter
    val expandedTripIds = galleryNavState.expandedTripIds

    val tripListState = rememberLazyListState(
        initialFirstVisibleItemIndex = galleryNavState.tripScrollIndex,
        initialFirstVisibleItemScrollOffset = galleryNavState.tripScrollOffset
    )
    val allClipsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = galleryNavState.allClipsScrollIndex,
        initialFirstVisibleItemScrollOffset = galleryNavState.allClipsScrollOffset
    )

    LaunchedEffect(tripListState) {
        snapshotFlow { Pair(tripListState.firstVisibleItemIndex, tripListState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                viewModel.updateGalleryScrollPosition(GalleryViewMode.TRIP_LOOPS, idx, off)
            }
    }

    LaunchedEffect(allClipsListState) {
        snapshotFlow { Pair(allClipsListState.firstVisibleItemIndex, allClipsListState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                viewModel.updateGalleryScrollPosition(GalleryViewMode.ALL_CLIPS, idx, off)
            }
    }

    var clipToDelete by remember { mutableStateOf<VideoClip?>(null) }
    var clipToShare by remember { mutableStateOf<VideoClip?>(null) }
    var clipToUnlock by remember { mutableStateOf<VideoClip?>(null) }
    var tripToRename by remember { mutableStateOf<TripSession?>(null) }
    var tripToLock by remember { mutableStateOf<TripSession?>(null) }
    var tripToUnlock by remember { mutableStateOf<TripSession?>(null) }
    var tripToDelete by remember { mutableStateOf<TripSession?>(null) }

    // Intercept phone system hardware back button
    androidx.activity.compose.BackHandler {
        if (tripToDelete != null) {
            tripToDelete = null
        } else if (tripToLock != null) {
            tripToLock = null
        } else if (tripToUnlock != null) {
            tripToUnlock = null
        } else if (tripToRename != null) {
            tripToRename = null
        } else if (clipToUnlock != null) {
            clipToUnlock = null
        } else if (clipToShare != null) {
            clipToShare = null
        } else if (clipToDelete != null) {
            clipToDelete = null
        } else if (viewMode == GalleryViewMode.ALL_CLIPS && selectedFilter != GalleryFilter.ALL) {
            viewModel.setGalleryFilter(GalleryFilter.ALL)
        } else if (viewMode == GalleryViewMode.ALL_CLIPS) {
            viewModel.setGalleryViewMode(GalleryViewMode.TRIP_LOOPS)
        } else {
            viewModel.resetGalleryNavigationMemory()
            onNavigateBack()
        }
    }

    // Refresh storage stats whenever Gallery is accessed
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
    }

    val filteredClips = remember(allClips, selectedFilter) {
        when (selectedFilter) {
            GalleryFilter.ALL -> allClips
            GalleryFilter.LOCKED -> allClips.filter { it.isLocked }
            GalleryFilter.DASHCAM -> allClips.filter { !it.isSentryEvent && it.isOriginalRawClip }
            GalleryFilter.SENTRY -> allClips.filter { it.isSentryEvent && it.isOriginalRawClip }
            GalleryFilter.FUSED_TELEMETRY -> allClips.filter { it.isTelemetryFused }
        }
    }

    // Consume pendingScrollToClip on return from playback while preserving exact LazyListState scroll offset
    LaunchedEffect(galleryNavState.pendingScrollToClip) {
        if (galleryNavState.pendingScrollToClip != null) {
            viewModel.consumePendingScrollToClip()
        }
    }

    val lockedCount = remember(allClips) { allClips.count { it.isLocked } }
    val dashCount = remember(allClips) { allClips.count { !it.isSentryEvent && it.isOriginalRawClip } }
    val sentryCount = remember(allClips) { allClips.count { it.isSentryEvent && it.isOriginalRawClip } }
    val fusedCount = remember(allClips) { allClips.count { it.isTelemetryFused } }

    Scaffold(
        topBar = {
            DashboardTopAppBar(
                title = "Recorded Footage",
                onNavigateBack = {
                    if (clipToShare != null) {
                        clipToShare = null
                    } else if (clipToDelete != null) {
                        clipToDelete = null
                    } else if (viewMode == GalleryViewMode.ALL_CLIPS && selectedFilter != GalleryFilter.ALL) {
                        viewModel.setGalleryFilter(GalleryFilter.ALL)
                    } else if (viewMode == GalleryViewMode.ALL_CLIPS) {
                        viewModel.setGalleryViewMode(GalleryViewMode.TRIP_LOOPS)
                    } else {
                        viewModel.resetGalleryNavigationMemory()
                        onNavigateBack()
                    }
                }
            )
        },
        containerColor = HudBlack,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            state = if (viewMode == GalleryViewMode.TRIP_LOOPS) tripListState else allClipsListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // Header 1: Storage Health & Loop buffer bar with percentage full
            item(key = "storage_header") {
                StorageBar(
                    stats = uiState.storageStats,
                    onRefresh = { viewModel.refreshStorageStats() }
                )
            }

            // Header 2: Segmented View Mode Selector
            item(key = "view_mode_selector") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF131B2A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isTripMode = viewMode == GalleryViewMode.TRIP_LOOPS
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTripMode) CyberCyan else Color.Transparent)
                            .clickable { viewModel.setGalleryViewMode(GalleryViewMode.TRIP_LOOPS) }
                            .testTag("gallery_tab_trip_loops"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = if (isTripMode) Color.Black else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Trip Loops (${trips.size})",
                                color = if (isTripMode) Color.Black else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    val isAllMode = viewMode == GalleryViewMode.ALL_CLIPS
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAllMode) CyberCyan else Color.Transparent)
                            .clickable { viewModel.setGalleryViewMode(GalleryViewMode.ALL_CLIPS) }
                            .testTag("gallery_tab_all_clips"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isAllMode) Color.Black else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "All Clips (${allClips.size})",
                                color = if (isAllMode) Color.Black else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (viewMode == GalleryViewMode.TRIP_LOOPS) {
                if (trips.isEmpty()) {
                    item(key = "trips_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                                .testTag("gallery_trips_empty_state"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Trip Sessions Yet",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Continuous dashcam loops recorded during the same ride are automatically grouped here into complete trip sessions with unified timelines.",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                if (allClips.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.setGalleryViewMode(GalleryViewMode.ALL_CLIPS) },
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                                    ) {
                                        Text("View All Clips (${allClips.size})", color = CyberCyan)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(trips, key = { it.id }) { trip ->
                        TripSessionCard(
                            trip = trip,
                            tripIndex = trips.indexOf(trip),
                            totalTrips = trips.size,
                            isExpanded = expandedTripIds.contains(trip.id),
                            onToggleExpand = { viewModel.toggleTripExpanded(trip.id) },
                            focusedClipFilePath = galleryNavState.lastPlayedClipFilePath,
                            onPlayFullTrip = {
                                val firstClip = trip.clips.firstOrNull()
                                if (firstClip != null) {
                                    viewModel.openClipForPlayback(firstClip, continuous = true)
                                    onPlayClip(firstClip)
                                }
                            },
                            onPlayClip = { clip ->
                                viewModel.openClipForPlayback(clip, continuous = true)
                                onPlayClip(clip)
                            },
                            onToggleLock = { clip ->
                                if (clip.isLocked) {
                                    clipToUnlock = clip
                                } else {
                                    viewModel.toggleClipLock(clip)
                                }
                            },
                            onRenameTrip = {
                                tripToRename = trip
                            },
                            onShare = { clip ->
                                val isFused = clip.isTelemetryFused
                                if (isFused) {
                                    IntentUtils.shareVideoFootage(context, clip.videoFile)
                                } else {
                                    clipToShare = clip
                                }
                            },
                            onDelete = { clip ->
                                if (clip.isLocked) {
                                    Toast.makeText(context, "Locked footage is protected from deletion. Unlock the clip first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipToDelete = clip
                                }
                            },
                            onLockTrip = { selectedTrip ->
                                tripToLock = selectedTrip
                            },
                            onUnlockTrip = { selectedTrip ->
                                tripToUnlock = selectedTrip
                            },
                            speedUnit = settings.speedUnit,
                            onDeleteTrip = { selectedTrip ->
                                tripToDelete = selectedTrip
                            }
                        )
                    }
                }
            } else {
                // ALL_CLIPS Mode: Filter Chips
                item(key = "filter_chips") {
                    GalleryFilterBar(
                        selectedFilter = selectedFilter,
                        onSelectFilter = { viewModel.setGalleryFilter(it) },
                        allCount = allClips.size,
                        lockedCount = lockedCount,
                        dashCount = dashCount,
                        sentryCount = sentryCount,
                        fusedCount = fusedCount
                    )
                }

                // Clips List or Empty state
                if (filteredClips.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No footage in this category",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Footage recorded in Dashcam or Sentry mode appears here automatically.",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filteredClips, key = { it.id }) { clip ->
                        ClipItemRow(
                            clip = clip,
                            isFocused = (clip.filePath == galleryNavState.lastPlayedClipFilePath),
                            speedUnit = settings.speedUnit,
                            onPlay = {
                                viewModel.openClipForPlayback(clip)
                                onPlayClip(clip)
                            },
                            onToggleLock = {
                                if (clip.isLocked) {
                                    clipToUnlock = clip
                                } else {
                                    viewModel.toggleClipLock(clip)
                                }
                            },
                            onShare = {
                                val isFused = clip.isTelemetryFused
                                if (isFused) {
                                    IntentUtils.shareVideoFootage(context, clip.videoFile)
                                } else {
                                    clipToShare = clip
                                }
                            },
                            onDelete = {
                                if (clip.isLocked) {
                                    Toast.makeText(context, "Locked footage is protected from deletion. Unlock the clip first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipToDelete = clip
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (clipToDelete != null) {
        val clip = clipToDelete!!
        AlertDialog(
            onDismissRequest = { clipToDelete = null },
            title = { Text("Delete Clip?", color = Color.White) },
            text = {
                Text(
                    text = if (clip.isLocked)
                        "This clip is locked and protected. It cannot be deleted while protected. Please unlock it first."
                    else
                        "Permanently delete this recorded segment from storage?",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!clip.isLocked) {
                            viewModel.deleteClip(clip)
                        }
                        clipToDelete = null
                    },
                    enabled = !clip.isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToDelete = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = HudSurface
        )
    }

    // Unlock Confirmation Dialog
    if (clipToUnlock != null) {
        val clip = clipToUnlock!!
        AlertDialog(
            onDismissRequest = { clipToUnlock = null },
            title = { Text("Unlock Protected Clip?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This clip is locked to prevent automatic loop cycling and accidental deletion. Unlocking it will allow DashGuard to overwrite or delete it when storage is full.\n\nAre you sure you want to unlock it?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleClipLock(clip)
                        clipToUnlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black)
                ) {
                    Text("Unlock Clip", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToUnlock = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = HudSurface
        )
    }

    // Trip Renaming Dialog
    if (tripToRename != null) {
        val trip = tripToRename!!
        TripRenameDialog(
            trip = trip,
            onSaveTitle = { title ->
                viewModel.saveTripCustomTitle(trip.id, title)
                tripToRename = null
            },
            onResetTitle = {
                viewModel.saveTripCustomTitle(trip.id, "")
                tripToRename = null
            },
            onDismiss = { tripToRename = null }
        )
    }

    // Share Choice Dialog
    if (clipToShare != null) {
        val clip = clipToShare!!
        ShareChoiceDialog(
            clip = clip,
            onDismiss = { clipToShare = null },
            onShareSeparate = { vFile, sFile ->
                IntentUtils.shareVideoFootage(context, vFile, sFile)
            },
            onShareFused = { fusedFile ->
                IntentUtils.shareVideoFootage(context, fusedFile)
            }
        )
    }

    // Lock Entire Trip Dialog
    if (tripToLock != null) {
        val trip = tripToLock!!
        val totalBytes = trip.clips.sumOf { it.fileSizeBytes }
        val sizeFormatted = FormatUtils.formatBytesCompact(totalBytes)
        AlertDialog(
            onDismissRequest = { tripToLock = null },
            title = {
                Text("Lock Entire Trip?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Locking this trip will protect all ${trip.loopCount} video loops (totaling $sizeFormatted).\n\n⚠️ Warning: Locked files are excluded from rolling loop cleanup and will permanently occupy storage until manually unlocked.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.lockTripClips(trip)
                        tripToLock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black)
                ) {
                    Text("Lock Trip ($sizeFormatted)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToLock = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = HudSurface
        )
    }

    // Unlock Trip Dialog
    if (tripToUnlock != null) {
        val trip = tripToUnlock!!
        val totalBytes = trip.clips.sumOf { it.fileSizeBytes }
        val sizeFormatted = FormatUtils.formatBytesCompact(totalBytes)
        val protectedClips = trip.clips.filter {
            TripGroupingEngine.isIncidentClip(it)
        }

        if (protectedClips.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { tripToUnlock = null },
                title = {
                    Text("Unlock Trip Options", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "This trip contains ${protectedClips.size} loop(s) with recorded incidents (${protectedClips.take(2).joinToString { it.fileName }}${if (protectedClips.size > 2) "..." else ""}).\n\nUnlocking loops removes protection and allows DashGuard to delete them when storage is full.\n\nDo you want to keep these ${protectedClips.size} incident loop(s) locked and unlock only the rest of the trip?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.unlockTripClips(trip, preserveIncidents = true)
                                tripToUnlock = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black)
                        ) {
                            Text("Keep Incidents (${protectedClips.size}) & Unlock Rest", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.unlockTripClips(trip, preserveIncidents = false)
                                tripToUnlock = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed)
                        ) {
                            Text("Unlock Everything (${trip.loopCount} Loops)")
                        }
                        TextButton(
                            onClick = { tripToUnlock = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                    }
                },
                dismissButton = null,
                containerColor = HudSurface
            )
        } else {
            AlertDialog(
                onDismissRequest = { tripToUnlock = null },
                title = {
                    Text("Unlock Entire Trip?", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "Unlocking this trip will remove protection from all ${trip.loopCount} video loops ($sizeFormatted).\n\nUnlocking allows DashGuard to automatically overwrite or delete these loops when storage is full.\n\nAre you sure you want to unlock this trip?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.unlockTripClips(trip, preserveIncidents = false)
                            tripToUnlock = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black)
                    ) {
                        Text("Unlock Trip", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { tripToUnlock = null }) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                },
                containerColor = HudSurface
            )
        }
    }

    // Smart Delete Trip Dialog
    if (tripToDelete != null) {
        val trip = tripToDelete!!
        val totalBytes = trip.clips.sumOf { it.fileSizeBytes }
        val sizeFormatted = FormatUtils.formatBytesCompact(totalBytes)
        val lockedClips = trip.clips.filter { it.isLocked }

        if (lockedClips.isNotEmpty()) {
            val lockedCount = lockedClips.size
            val unlockedCount = trip.loopCount - lockedCount
            AlertDialog(
                onDismissRequest = { tripToDelete = null },
                title = {
                    Text("Delete Trip Options", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "This trip contains ${trip.loopCount} loops ($sizeFormatted), including $lockedCount locked/incident loop(s).\n\nLocked loops are protected against deletion.\n\nChoose how you want to delete this trip:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (unlockedCount > 0) {
                            Button(
                                onClick = {
                                    viewModel.deleteTrip(trip, deleteLockedClips = false)
                                    tripToDelete = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                            ) {
                                Text("Delete Unlocked Loops Only ($unlockedCount)", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteTrip(trip, deleteLockedClips = true)
                                tripToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed)
                        ) {
                            Text("Delete Everything (${trip.loopCount} Loops)")
                        }
                        TextButton(
                            onClick = { tripToDelete = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                    }
                },
                dismissButton = null,
                containerColor = HudSurface
            )
        } else {
            AlertDialog(
                onDismissRequest = { tripToDelete = null },
                title = {
                    Text("Delete Entire Trip?", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete this trip and all ${trip.loopCount} video loops ($sizeFormatted)?\n\nThis action cannot be undone.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTrip(trip, deleteLockedClips = true)
                            tripToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White)
                    ) {
                        Text("Delete Trip", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { tripToDelete = null }) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                },
                containerColor = HudSurface
            )
        }
    }
}

