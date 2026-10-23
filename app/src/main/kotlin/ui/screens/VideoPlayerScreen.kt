package io.github.roaxio.dashguardpro.ui.screens

import android.app.Activity
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleManager
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.playback.VideoPlaybackState
import io.github.roaxio.dashguardpro.ui.components.playback.PlaybackControlsOverlay
import io.github.roaxio.dashguardpro.ui.components.playback.PlaybackZoomBadge
import io.github.roaxio.dashguardpro.ui.components.playback.TelemetryWatermarkHelper
import io.github.roaxio.dashguardpro.ui.components.playback.TelemetryWatermarkOverlay
import io.github.roaxio.dashguardpro.ui.components.playback.TripMetricsCard
import io.github.roaxio.dashguardpro.ui.components.playback.VideoPlaybackSurface
import io.github.roaxio.dashguardpro.ui.components.playback.PlaybackZoomState
import io.github.roaxio.dashguardpro.ui.components.playback.VideoPlayerDialogs
import io.github.roaxio.dashguardpro.ui.components.playback.rememberPlaybackZoomState
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.util.IntentUtils
import io.github.roaxio.dashguardpro.util.srtSidecarFile
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lean Screen Coordinator for video playback and incident telemetry inspection.
 * Coordinates the VideoPlaybackSurface, TelemetryWatermarkOverlay, PlaybackControlsOverlay,
 * and TripMetricsCard while owning the system UI insets, BackHandler, and ticker state machine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    clip: VideoClip,
    onBack: () -> Unit,
    onToggleLock: (VideoClip) -> Unit,
    modifier: Modifier = Modifier,
    previousClip: VideoClip? = null,
    nextClip: VideoClip? = null,
    tripPreviousClip: VideoClip? = null,
    tripNextClip: VideoClip? = null,
    adjacencyThresholdSeconds: Int = 30,
    trip: TripSession? = null,
    onSelectClip: ((VideoClip) -> Unit)? = null,
    onToggleAutoAdvance: (() -> Unit)? = null,
    playbackState: VideoPlaybackState? = null,
    onUpdatePlaybackState: ((VideoPlaybackState) -> Unit)? = null,
    speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val formattedTime = remember(clip.timestamp) { dateFormat.format(Date(clip.timestamp)) }
    val file = remember(clip.filePath) { File(clip.filePath) }
    val hasRealVideoFile = file.exists() && file.length() > 0

    val isFusedClip = remember(clip.filePath, clip.fileName) {
        clip.isTelemetryFused
    }

    val srtFile = remember(clip.filePath) {
        clip.srtSidecarFile
    }
    val subtitleEntries = remember(srtFile, isFusedClip) {
        if (!isFusedClip && srtFile.exists()) {
            TelemetrySubtitleManager.parseSubtitleFile(srtFile)
        } else {
            emptyList()
        }
    }

    var internalIsPlaying by rememberSaveable { mutableStateOf(hasRealVideoFile) }
    var internalPlaybackMs by remember(clip.id, clip.filePath) { mutableIntStateOf(0) }
    var internalIsFullScreen by rememberSaveable { mutableStateOf(false) }
    var internalIsLooping by rememberSaveable { mutableStateOf(false) }
    var internalIsContinuous by rememberSaveable { mutableStateOf(true) }
    var internalShowControls by rememberSaveable { mutableStateOf(true) }
    var showShareChoiceDialog by remember { mutableStateOf(false) }
    var showUnlockWarningDialog by remember { mutableStateOf(false) }

    val handleToggleLock: () -> Unit = {
        if (clip.isLocked) {
            showUnlockWarningDialog = true
        } else {
            onToggleLock(clip)
        }
    }

    var isScrubbing by remember(clip.id, clip.filePath) { mutableStateOf(false) }
    var scrubPositionSec by remember(clip.id, clip.filePath) { mutableFloatStateOf(0f) }
    var wasPlayingBeforeScrub by remember(clip.id, clip.filePath) { mutableStateOf(false) }
    var isSurfacePreparing by remember(clip.id, clip.filePath) { mutableStateOf(hasRealVideoFile) }
    var lastSavedPlaybackPosMs by remember(clip.id, clip.filePath) { mutableIntStateOf(0) }

    val isPlaying = playbackState?.isPlaying ?: internalIsPlaying
    val currentPlaybackMs = playbackState?.currentPositionMs ?: internalPlaybackMs
    val isFullScreen = playbackState?.isFullScreen ?: internalIsFullScreen
    val isLooping = playbackState?.isLooping ?: internalIsLooping
    val isContinuous = playbackState?.isContinuousPlayback ?: internalIsContinuous
    val showControls = playbackState?.showControls ?: internalShowControls
    val zoomState: PlaybackZoomState = rememberPlaybackZoomState()

    // Symmetrical zoom reset upon clip navigation or exiting full-screen (AGENTS.md Rule 9)
    LaunchedEffect(clip.id, clip.filePath, isFullScreen) {
        if (!isFullScreen) {
            zoomState.reset()
        }
    }

    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentPlaybackPosMs by rememberUpdatedState(currentPlaybackMs)
    val currentIsFullScreen by rememberUpdatedState(isFullScreen)
    val currentIsLooping by rememberUpdatedState(isLooping)
    val currentIsContinuous by rememberUpdatedState(isContinuous)
    val currentShowControls by rememberUpdatedState(showControls)
    val currentPlaybackState by rememberUpdatedState(playbackState)
    val currentOnUpdatePlaybackState by rememberUpdatedState(onUpdatePlaybackState)
    val currentPreviousClip by rememberUpdatedState(previousClip)
    val currentNextClip by rememberUpdatedState(nextClip)
    val currentOnSelectClip by rememberUpdatedState(onSelectClip)
    val currentTripPreviousClip by rememberUpdatedState(tripPreviousClip)
    val currentTripNextClip by rememberUpdatedState(tripNextClip)

    val isBreakBeforeNext = remember(tripNextClip, nextClip) {
        tripNextClip != null && nextClip == null
    }

    // Intercept phone system hardware back button
    BackHandler {
        if (showShareChoiceDialog) {
            showShareChoiceDialog = false
        } else if (currentIsFullScreen) {
            zoomState.reset()
            val updater = currentOnUpdatePlaybackState
            val pState = currentPlaybackState
            if (updater != null && pState != null) {
                updater(pState.copy(isFullScreen = false))
            } else {
                internalIsFullScreen = false
            }
        } else {
            onBack()
        }
    }

    val totalDurationMs = clip.durationMs.toInt().coerceAtLeast(1000)
    val totalDurationSec = (totalDurationMs / 1000).coerceAtLeast(1)
    val currentPlaybackSec = (currentPlaybackMs / 1000).coerceIn(0, totalDurationSec)

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var userInteractionCounter by rememberSaveable { mutableIntStateOf(0) }
    var hasCompletedCurrentLoop by remember(clip.id, clip.filePath) { mutableStateOf(false) }

    val effectiveDurationMs = videoViewRef?.duration?.takeIf { it > 0 } ?: totalDurationMs
    val isAtEndOfClip = hasCompletedCurrentLoop || (effectiveDurationMs > 0 && currentPlaybackMs >= effectiveDurationMs - 500)
    val shouldShowBreakCard = isBreakBeforeNext && tripNextClip != null && !isPlaying && isAtEndOfClip

    fun updatePlayback(
        playing: Boolean? = null,
        posMs: Int? = null,
        fullScreen: Boolean? = null,
        loop: Boolean? = null,
        controls: Boolean? = null,
        continuous: Boolean? = null
    ) {
        if (playing == true || (posMs != null && posMs < totalDurationMs - 1000)) {
            hasCompletedCurrentLoop = false
        }
        val targetPlaying = playing ?: currentIsPlaying
        val targetPos = posMs ?: currentPlaybackPosMs
        val targetFullScreen = fullScreen ?: currentIsFullScreen
        val targetLoop = loop ?: currentIsLooping
        val targetControls = controls ?: currentShowControls
        val targetContinuous = continuous ?: currentIsContinuous

        val updater = currentOnUpdatePlaybackState
        val pState = currentPlaybackState
        if (updater != null && pState != null) {
            updater(
                pState.copy(
                    clipFilePath = clip.filePath,
                    isPlaying = targetPlaying,
                    currentPositionMs = targetPos,
                    isFullScreen = targetFullScreen,
                    isLooping = targetLoop,
                    showControls = targetControls,
                    isContinuousPlayback = targetContinuous
                )
            )
        } else {
            if (playing != null) internalIsPlaying = playing
            if (posMs != null) internalPlaybackMs = posMs
            if (fullScreen != null) internalIsFullScreen = fullScreen
            if (loop != null) internalIsLooping = loop
            if (controls != null) internalShowControls = controls
            if (continuous != null) internalIsContinuous = continuous
        }
    }

    // Intercept back button when in full screen mode to exit full screen first
    BackHandler(enabled = isFullScreen) {
        updatePlayback(fullScreen = false, controls = true)
        userInteractionCounter++
    }

    // Immersive Mode System UI Insets Handling (Enforce App-wide Fullscreen Invariant)
    DisposableEffect(Unit) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
            videoViewRef = null
        }
    }

    // Explicit reset when navigating to a new clip: zero out position, reset saved position and loop flags, and start playing immediately
    LaunchedEffect(clip.id, clip.filePath) {
        internalPlaybackMs = 0
        lastSavedPlaybackPosMs = 0
        internalIsPlaying = true
        hasCompletedCurrentLoop = false
        val pState = currentPlaybackState
        val updater = currentOnUpdatePlaybackState
        if (pState != null && updater != null) {
            updater(
                pState.copy(
                    clipFilePath = clip.filePath,
                    currentPositionMs = if (isScrubbing) (scrubPositionSec * 1000).toInt() else 0,
                    isPlaying = hasRealVideoFile || pState.isPlaying
                )
            )
        }
    }

    // Auto-hiding idle timer: disappears after 3.5 seconds of inactivity while playing and NOT scrubbing
    LaunchedEffect(showControls, isPlaying, isScrubbing, userInteractionCounter) {
        if (showControls && isPlaying && !isScrubbing) {
            delay(3500L)
            updatePlayback(controls = false)
        }
    }

    // Playback ticker to smoothly update position
    LaunchedEffect(clip.id, clip.filePath, isPlaying, hasRealVideoFile) {
        while (isPlaying) {
            delay(250L)
            if (!isScrubbing && !isSurfacePreparing) {
                if (videoViewRef != null && hasRealVideoFile) {
                    try {
                        val v = videoViewRef
                        if (v != null && v.tag == clip.filePath) {
                            val pos = v.currentPosition
                            val dur = v.duration.takeIf { it > 0 } ?: totalDurationMs
                            val isNearEnd = (dur > 0 && pos >= dur - 500) || (totalDurationMs > 0 && pos >= totalDurationMs - 500)
                            val isStoppedAtEnd = !v.isPlaying && pos >= dur - 1500 && dur > 0

                            if (hasCompletedCurrentLoop || isNearEnd || isStoppedAtEnd) {
                                hasCompletedCurrentLoop = true
                                if (currentIsContinuous && currentNextClip != null && currentOnSelectClip != null) {
                                    v.stopPlayback()
                                    updatePlayback(playing = true, posMs = 0)
                                    currentOnSelectClip?.invoke(currentNextClip!!)
                                } else {
                                    updatePlayback(playing = false, posMs = if (dur > 0) dur else totalDurationMs, controls = true)
                                }
                            } else if (pos >= 0) {
                                if (pos > 0 || currentPlaybackPosMs == 0) {
                                    lastSavedPlaybackPosMs = pos
                                    updatePlayback(posMs = pos)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                } else if (!hasRealVideoFile) {
                    val nextMs = currentPlaybackPosMs + 250
                    if (nextMs >= totalDurationMs) {
                        hasCompletedCurrentLoop = true
                        if (currentIsContinuous && currentNextClip != null && currentOnSelectClip != null) {
                            updatePlayback(playing = true, posMs = 0)
                            currentOnSelectClip?.invoke(currentNextClip!!)
                        } else {
                            updatePlayback(playing = false, posMs = totalDurationMs, controls = true)
                        }
                    } else {
                        updatePlayback(posMs = nextMs)
                    }
                }
            }
        }
    }

    val avgSpeed = if (clip.avgSpeedKmh > 0f) clip.avgSpeedKmh else clip.speedKmh
    val distanceKm = clip.distanceMeters / 1000f

    val effectiveDisplayMs = if (isScrubbing) (scrubPositionSec * 1000).toInt() else currentPlaybackPosMs
    val activeWatermarkText = remember(effectiveDisplayMs, subtitleEntries, clip, isFusedClip, speedUnit) {
        TelemetryWatermarkHelper.computeWatermarkText(
            clip = clip,
            subtitleEntries = subtitleEntries,
            effectiveDisplayMs = effectiveDisplayMs,
            isFusedVideo = isFusedClip,
            dateFormat = dateFormat,
            speedUnit = speedUnit
        )
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = clip.fileName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = clip.triggerReason,
                                color = if (clip.isLocked) NeonAmber else CyberCyan,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = handleToggleLock) {
                            Icon(
                                imageVector = if (clip.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock",
                                tint = if (clip.isLocked) NeonAmber else Color.White
                            )
                        }
                        IconButton(onClick = {
                            if (isFusedClip) {
                                IntentUtils.shareVideoFootage(context, file)
                            } else {
                                showShareChoiceDialog = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HudBlack)
                )
            }
        },
        containerColor = HudBlack,
        modifier = modifier
    ) { padding ->
        val outerContainerModifier = if (isFullScreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .padding(padding)
        }

        Column(modifier = outerContainerModifier) {
            // Player Box Container
            Box(
                modifier = if (isFullScreen) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(0.38f)
                        .background(Color.Black)
                },
                contentAlignment = Alignment.Center
            ) {
                // 1. Video Render Surface (Native VideoView or Telemetry Simulation Player)
                VideoPlaybackSurface(
                    clip = clip,
                    file = file,
                    hasRealVideoFile = hasRealVideoFile,
                    isPlaying = isPlaying,
                    currentPlaybackMs = currentPlaybackMs,
                    lastSavedPlaybackPosMs = lastSavedPlaybackPosMs,
                    totalDurationMs = totalDurationMs,
                    totalDurationSec = totalDurationSec,
                    currentPlaybackSec = currentPlaybackSec,
                    avgSpeed = avgSpeed,
                    distanceKm = distanceKm,
                    isScrubbing = isScrubbing,
                    scrubPositionSec = scrubPositionSec,
                    isSurfacePreparing = isSurfacePreparing,
                    zoomState = zoomState,
                    isFullScreen = isFullScreen,
                    showControls = showControls,
                    speedUnit = speedUnit,
                    onVideoViewAttached = { if (it != null) videoViewRef = it },
                    onSurfacePreparingChanged = { isSurfacePreparing = it },
                    onPlaybackCompleted = {
                        hasCompletedCurrentLoop = true
                        if (currentIsContinuous && currentNextClip != null && currentOnSelectClip != null) {
                            videoViewRef?.stopPlayback()
                            updatePlayback(playing = true, posMs = 0)
                            currentOnSelectClip?.invoke(currentNextClip!!)
                        } else {
                            updatePlayback(playing = false, posMs = totalDurationMs, controls = true)
                        }
                    },
                    onToggleControls = {
                        if (!isScrubbing) {
                            val newControls = !showControls
                            updatePlayback(controls = newControls)
                            userInteractionCounter++
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                var bottomControlsHeightDp by remember { mutableStateOf(0.dp) }

                // 2. Telemetry Watermark Overlay Banner (unfused videos only)
                if (!isFusedClip) {
                    TelemetryWatermarkOverlay(
                        watermarkText = activeWatermarkText,
                        showControls = showControls,
                        isFullScreen = isFullScreen,
                        bottomControlsHeight = bottomControlsHeightDp,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 3. Floating Zoom Level Indicator & Quick Reset (Full Screen mode)
                if (isFullScreen) {
                    PlaybackZoomBadge(
                        zoomState = zoomState,
                        onReset = { zoomState.reset() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    )
                }

                // 4. Auto-Hiding Controls Overlay (Transport, Scrubber & Break Notification)
                PlaybackControlsOverlay(
                    showControls = showControls,
                    speedUnit = speedUnit,
                    isPlaying = isPlaying,
                    isFullScreen = isFullScreen,
                    isScrubbing = isScrubbing,
                    isContinuous = isContinuous,
                    isAtEndOfClip = isAtEndOfClip,
                    currentPlaybackMs = currentPlaybackMs,
                    totalDurationMs = totalDurationMs,
                    totalDurationSec = totalDurationSec,
                    currentPlaybackSec = currentPlaybackSec,
                    scrubPositionSec = scrubPositionSec,
                    clip = clip,
                    avgSpeed = avgSpeed,
                    distanceKm = distanceKm,
                    trip = trip,
                    tripPreviousClip = tripPreviousClip,
                    tripNextClip = tripNextClip,
                    isBreakBeforeNext = isBreakBeforeNext,
                    shouldShowBreakCard = shouldShowBreakCard,
                    adjacencyThresholdSeconds = adjacencyThresholdSeconds,
                    onBottomControlsHeightChanged = { bottomControlsHeightDp = it },
                    onTogglePlayPause = {
                        val newPlaying = if (isAtEndOfClip) true else !isPlaying
                        val curPos = videoViewRef?.currentPosition ?: currentPlaybackMs
                        val targetPos = if (newPlaying && (curPos >= totalDurationMs - 500 || isAtEndOfClip)) 0 else curPos
                        if (newPlaying) {
                            if (targetPos == 0) {
                                videoViewRef?.seekTo(0)
                            }
                            videoViewRef?.start()
                        } else {
                            videoViewRef?.pause()
                        }
                        hasCompletedCurrentLoop = false
                        updatePlayback(playing = newPlaying, posMs = targetPos, controls = true)
                        userInteractionCounter++
                    },
                    onReplay = {
                        hasCompletedCurrentLoop = false
                        videoViewRef?.seekTo(0)
                        videoViewRef?.start()
                        updatePlayback(playing = true, posMs = 0, controls = true)
                        userInteractionCounter++
                    },
                    onPreviousLoop = {
                        if (tripPreviousClip != null && onSelectClip != null) {
                            try {
                                videoViewRef?.stopPlayback()
                            } catch (_: Exception) {}
                            updatePlayback(posMs = 0)
                            onSelectClip(tripPreviousClip)
                            userInteractionCounter++
                        }
                    },
                    onNextLoop = {
                        if (tripNextClip != null && onSelectClip != null) {
                            if (isBreakBeforeNext && !shouldShowBreakCard) {
                                try {
                                    videoViewRef?.pause()
                                } catch (_: Exception) {}
                                hasCompletedCurrentLoop = true
                                updatePlayback(playing = false, controls = true)
                            } else {
                                try {
                                    videoViewRef?.stopPlayback()
                                } catch (_: Exception) {}
                                updatePlayback(posMs = 0)
                                onSelectClip(tripNextClip)
                            }
                            userInteractionCounter++
                        }
                    },
                    onResumeAfterBreak = {
                        if (tripNextClip != null && onSelectClip != null) {
                            try {
                                videoViewRef?.stopPlayback()
                            } catch (_: Exception) {}
                            updatePlayback(posMs = 0)
                            onSelectClip(tripNextClip)
                            userInteractionCounter++
                        }
                    },
                    onToggleAutoAdvance = {
                        if (onToggleAutoAdvance != null) {
                            onToggleAutoAdvance()
                        } else {
                            val newContinuous = !isContinuous
                            updatePlayback(continuous = newContinuous, controls = true)
                        }
                        userInteractionCounter++
                    },
                    onScrubValueChange = { newSec ->
                        if (!isScrubbing) {
                            wasPlayingBeforeScrub = isPlaying
                            if (isPlaying) {
                                try { videoViewRef?.pause() } catch (_: Exception) {}
                            }
                            isScrubbing = true
                        }
                        scrubPositionSec = newSec
                        val scrubMs = (newSec * 1000).toInt()
                        try { videoViewRef?.seekTo(scrubMs) } catch (_: Exception) {}
                        userInteractionCounter++
                    },
                    onScrubFinished = {
                        val targetMs = (scrubPositionSec * 1000).toInt().coerceIn(0, totalDurationMs)
                        try { videoViewRef?.seekTo(targetMs) } catch (_: Exception) {}
                        lastSavedPlaybackPosMs = targetMs
                        if (wasPlayingBeforeScrub && targetMs < totalDurationMs) {
                            try { videoViewRef?.start() } catch (_: Exception) {}
                            updatePlayback(playing = true, posMs = targetMs, controls = true)
                        } else {
                            try { videoViewRef?.pause() } catch (_: Exception) {}
                            updatePlayback(playing = false, posMs = targetMs, controls = true)
                        }
                        isScrubbing = false
                        wasPlayingBeforeScrub = false
                        userInteractionCounter++
                    },
                    onToggleFullScreen = {
                        val newFs = !isFullScreen
                        if (!newFs) zoomState.reset()
                        val curPos = videoViewRef?.currentPosition ?: currentPlaybackMs
                        if (curPos > 0) lastSavedPlaybackPosMs = curPos
                        updatePlayback(posMs = curPos, fullScreen = newFs, controls = true)
                        userInteractionCounter++
                    },
                    onToggleLock = {
                        handleToggleLock()
                        userInteractionCounter++
                    },
                    onShare = {
                        if (isFusedClip) {
                            IntentUtils.shareVideoFootage(context, file)
                        } else {
                            showShareChoiceDialog = true
                        }
                        userInteractionCounter++
                    },
                    onSelectClip = { segClip ->
                        try {
                            videoViewRef?.stopPlayback()
                        } catch (_: Exception) {}
                        updatePlayback(posMs = 0)
                        onSelectClip?.invoke(segClip)
                        userInteractionCounter++
                    },
                    onDismissControls = {
                        if (!isScrubbing) {
                            updatePlayback(controls = false)
                            userInteractionCounter++
                        }
                    }
                )
            }

            // 4. Non-FullScreen Telemetry Record & Actions
            if (!isFullScreen) {
                TripMetricsCard(
                    clip = clip,
                    formattedTime = formattedTime,
                    trip = trip,
                    isContinuous = isContinuous,
                    adjacencyThresholdSeconds = adjacencyThresholdSeconds,
                    onSelectClip = { segClip ->
                        try {
                            videoViewRef?.stopPlayback()
                        } catch (_: Exception) {}
                        updatePlayback(posMs = 0)
                        onSelectClip?.invoke(segClip)
                    },
                    onToggleLock = handleToggleLock,
                    speedUnit = speedUnit,
                    onShare = {
                        if (isFusedClip) {
                            IntentUtils.shareVideoFootage(context, file)
                        } else {
                            showShareChoiceDialog = true
                        }
                    },
                    modifier = Modifier.weight(0.62f)
                )
            }
        }
    }

    // 5. Presentation Dialogs
    VideoPlayerDialogs(
        clip = clip,
        showShareChoiceDialog = showShareChoiceDialog,
        showUnlockWarningDialog = showUnlockWarningDialog,
        onDismissShare = { showShareChoiceDialog = false },
        onDismissUnlockWarning = { showUnlockWarningDialog = false },
        onConfirmUnlock = {
            onToggleLock(clip)
            showUnlockWarningDialog = false
        },
        onShareSeparate = { vFile, sFile ->
            IntentUtils.shareVideoFootage(context, vFile, sFile)
        },
        onShareFused = { fusedFile ->
            IntentUtils.shareVideoFootage(context, fusedFile)
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
        }
    }
}
