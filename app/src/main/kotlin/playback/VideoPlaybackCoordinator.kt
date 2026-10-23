package io.github.roaxio.dashguardpro.playback

import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleEntry
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleManager
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import io.github.roaxio.dashguardpro.util.srtSidecarFile

data class VideoPlaybackState(
    val clipFilePath: String? = null,
    val isPlaying: Boolean = true,
    val currentPositionMs: Int = 0,
    val isFullScreen: Boolean = false,
    val isLooping: Boolean = false,
    val showControls: Boolean = true,
    val isContinuousPlayback: Boolean = true
)

/**
 * Coordinates video playback state, .srt subtitle parsing and caching,
 * binary timestamp search, and trip-contiguous clip navigation.
 *
 * Adheres to AGENTS.md Rule 1 (Root-Cause Resolution) and Rule 11 (Domain Metric Authority).
 */
class VideoPlaybackCoordinator {
    private val _playbackState = MutableStateFlow(VideoPlaybackState())
    val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val subtitleCache = ConcurrentHashMap<String, List<TelemetrySubtitleEntry>>()

    fun openClipForPlayback(clip: VideoClip, continuous: Boolean? = null, forceRestart: Boolean = false) {
        val existing = _playbackState.value
        val autoAdv = continuous ?: existing.isContinuousPlayback
        if (!forceRestart && existing.clipFilePath == clip.filePath) {
            if (continuous != null && existing.isContinuousPlayback != continuous) {
                _playbackState.value = existing.copy(isContinuousPlayback = continuous)
            }
            return
        }
        _playbackState.value = VideoPlaybackState(
            clipFilePath = clip.filePath,
            isPlaying = true,
            currentPositionMs = 0,
            isFullScreen = existing.isFullScreen,
            isLooping = false,
            showControls = true,
            isContinuousPlayback = autoAdv
        )
    }

    fun closePlayback() {
        _playbackState.value = VideoPlaybackState()
    }

    fun toggleContinuousPlayback() {
        _playbackState.update { it.copy(isContinuousPlayback = !it.isContinuousPlayback) }
    }

    fun updatePlaybackState(state: VideoPlaybackState) {
        _playbackState.value = state
    }

    fun updatePlaybackState(transform: (VideoPlaybackState) -> VideoPlaybackState) {
        _playbackState.update(transform)
    }

    fun getAdjacentClips(
        clip: VideoClip,
        allClips: List<VideoClip>,
        maxAdjacencyGapSeconds: Int
    ): Pair<VideoClip?, VideoClip?> {
        return TripGroupingEngine.findAdjacentClips(
            currentClip = clip,
            allClips = allClips,
            maxAdjacencyGapSeconds = maxAdjacencyGapSeconds
        )
    }

    fun getTripAdjacentClips(clip: VideoClip, trip: TripSession): Pair<VideoClip?, VideoClip?> {
        return TripGroupingEngine.findTripAdjacentClips(clip, trip)
    }

    fun getTripForClip(clip: VideoClip, trips: List<TripSession>): TripSession? {
        return TripGroupingEngine.findTripForClip(clip, trips)
    }

    /**
     * Loads and caches subtitle entries for a clip's accompanying .srt sidecar file.
     */
    fun loadSubtitles(clipFilePath: String): List<TelemetrySubtitleEntry> {
        return subtitleCache.getOrPut(clipFilePath) {
            val srtFile = File(clipFilePath).srtSidecarFile
            if (srtFile.exists() && srtFile.length() > 0) {
                TelemetrySubtitleManager.parseSubtitleFile(srtFile)
            } else {
                emptyList()
            }
        }
    }

    /**
     * Performs a binary search over subtitle entries to find the active telemetry entry at [positionMs].
     */
    fun findSubtitleAtPosition(
        entries: List<TelemetrySubtitleEntry>,
        positionMs: Long
    ): TelemetrySubtitleEntry? {
        if (entries.isEmpty()) return null
        var low = 0
        var high = entries.size - 1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val entry = entries[mid]
            when {
                positionMs < entry.startMs -> high = mid - 1
                positionMs > entry.endMs -> low = mid + 1
                else -> return entry
            }
        }
        return null
    }

    fun clearSubtitleCache() {
        subtitleCache.clear()
    }
}
