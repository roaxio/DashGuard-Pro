package io.github.roaxio.dashguardpro.viewmodel

import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.model.GalleryFilter
import io.github.roaxio.dashguardpro.model.GalleryViewMode
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import java.io.File

/**
 * DashcamIntent — Sealed hierarchy representing all unidirectional user intents and lifecycle actions
 * dispatched from presentation layers to the Orchestration Tier (AGENTS.md Rule 8: Framework State Invariance).
 */
sealed interface DashcamIntent {
    // 1. Recording Session Controls
    data object ToggleRecording : DashcamIntent
    data object ToggleCurrentClipLock : DashcamIntent
    data class LockCurrentClip(val reason: String = "Manual UI") : DashcamIntent

    // 2. Cockpit Operating Modes & Quick Toggles
    data class SetMode(val mode: AppMode) : DashcamIntent
    data object ToggleOledMode : DashcamIntent
    data class SetOledMode(val enabled: Boolean) : DashcamIntent
    data object ToggleNightExposure : DashcamIntent
    data class SetScreenBrightness(val level: Float) : DashcamIntent
    data object ToggleTorch : DashcamIntent
    data object ToggleAudio : DashcamIntent
    data object ToggleCameraLens : DashcamIntent
    data object CycleHudDisplayMode : DashcamIntent

    // 3. Sentry Mode Controls
    data class ArmSentryMode(val delaySeconds: Int? = null) : DashcamIntent
    data class DisarmSentryMode(val isManual: Boolean = true) : DashcamIntent
    data object CancelSentryArming : DashcamIntent
    data object ConfirmStartDashcamAfterDisarm : DashcamIntent
    data object DismissStartDashcamPrompt : DashcamIntent

    // 4. Power & Thermal Safety Overrides
    data object CancelThermalCooling : DashcamIntent
    data object CancelUnplugShutdown : DashcamIntent

    // 5. Ephemeral UI & Driving Safety
    data object CaptureSnapshot : DashcamIntent
    data object ClearIncidentMessage : DashcamIntent
    data object DismissSpeedLockout : DashcamIntent

    // 6. Settings & Storage Migration
    data class UpdateSettings(val newSettings: DashGuardSettings) : DashcamIntent
    data class MigrateFootageToNewDirectory(val targetDir: File) : DashcamIntent
    data object DismissMigrationProgress : DashcamIntent
    data object ResetStorageMigrationState : DashcamIntent
    data object RefreshStorageStats : DashcamIntent
    data object RefreshSystemDiagnostics : DashcamIntent

    // 7. Gallery, Clip & Trip Management
    data class SetGalleryViewMode(val mode: GalleryViewMode) : DashcamIntent
    data class SetGalleryFilter(val filter: GalleryFilter) : DashcamIntent
    data class ToggleTripExpanded(val tripId: String) : DashcamIntent
    data class SetTripExpanded(val tripId: String, val expanded: Boolean) : DashcamIntent
    data object ResetGalleryNavigationMemory : DashcamIntent
    data class SaveTripCustomTitle(val tripId: String, val title: String) : DashcamIntent
    data class DeleteClip(val clip: VideoClip) : DashcamIntent
    data class DeleteTrip(val trip: TripSession, val deleteLockedClips: Boolean = false) : DashcamIntent
    data class ToggleClipLock(val clip: VideoClip) : DashcamIntent
    data class LockTripClips(val trip: TripSession) : DashcamIntent
    data class UnlockTripClips(val trip: TripSession, val preserveIncidents: Boolean = true) : DashcamIntent

    // 8. Video Playback & Telemetry Subtitles
    data class OpenClipForPlayback(val clip: VideoClip) : DashcamIntent
    data object ClosePlayback : DashcamIntent
    data object ToggleContinuousPlayback : DashcamIntent
    data class UpdatePlaybackState(val currentPositionMs: Int, val isPlaying: Boolean = true) : DashcamIntent
}
