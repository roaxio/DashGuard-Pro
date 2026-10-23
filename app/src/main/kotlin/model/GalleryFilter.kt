package io.github.roaxio.dashguardpro.model

/**
 * Presentation view mode for Gallery navigation: Trip continuous grouping vs all clips list.
 */
enum class GalleryViewMode {
    TRIP_LOOPS,
    ALL_CLIPS
}

/**
 * Filter categories for footage browsing in the Gallery screen.
 */
enum class GalleryFilter {
    ALL,
    LOCKED,
    DASHCAM,
    SENTRY,
    FUSED_TELEMETRY
}

/**
 * Authoritative domain state for Gallery browsing: view mode, category filter,
 * set of expanded trip session IDs, last played clip path, pending scroll targets,
 * and independent scroll offsets for both Trip Loops and All Clips views.
 */
data class GalleryNavState(
    val viewMode: GalleryViewMode = GalleryViewMode.TRIP_LOOPS,
    val selectedFilter: GalleryFilter = GalleryFilter.ALL,
    val expandedTripIds: Set<String> = emptySet(),
    val lastPlayedClipFilePath: String? = null,
    val pendingScrollToClip: String? = null,
    val tripScrollIndex: Int = 0,
    val tripScrollOffset: Int = 0,
    val allClipsScrollIndex: Int = 0,
    val allClipsScrollOffset: Int = 0
)
