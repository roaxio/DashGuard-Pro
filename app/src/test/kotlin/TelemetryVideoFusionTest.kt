package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.media.TelemetryVideoFusionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TelemetryVideoFusionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testGetFusedVideoFile_namingConvention() {
        val originalVideo = File("/storage/emulated/0/Movies/dashguard_videos/VID_DASH_20260829_175000.mp4")
        val fusedFile = TelemetryVideoFusionEngine.getFusedVideoFile(originalVideo)

        assertEquals("VID_DASH_20260829_175000-telemetry-fused.mp4", fusedFile.name)
        assertEquals("/storage/emulated/0/Movies/dashguard_videos", fusedFile.parentFile?.path?.replace('\\', '/'))
    }

    @Test
    fun testIsFusedVideoAvailable_detection() {
        val root = tempFolder.root
        val originalVideo = File(root, "VID_DASH_20260829_120000.mp4")
        originalVideo.writeText("sample video content")

        assertFalse(TelemetryVideoFusionEngine.isFusedVideoAvailable(originalVideo))

        val fusedFile = File(root, "VID_DASH_20260829_120000-telemetry-fused.mp4")
        fusedFile.writeBytes(ByteArray(2048)) // Valid non-empty file

        assertTrue(TelemetryVideoFusionEngine.isFusedVideoAvailable(originalVideo))
    }

    @Test
    fun testFusedTelemetryCategoryFiltering() {
        val clipNormal = VideoClip(
            id = 1,
            filePath = "/storage/emulated/0/VID_DASH_1.mp4",
            fileName = "VID_DASH_1.mp4",
            timestamp = 1000L,
            durationMs = 60000L,
            fileSizeBytes = 1000000L,
            isLocked = false
        )

        val clipFused = VideoClip(
            id = 2,
            filePath = "/storage/emulated/0/VID_DASH_1-telemetry-fused.mp4",
            fileName = "VID_DASH_1-telemetry-fused.mp4",
            timestamp = 1000L,
            durationMs = 60000L,
            fileSizeBytes = 1000000L,
            isLocked = false
        )

        val allClips = listOf(clipNormal, clipFused)
        val fusedClips = allClips.filter {
            it.fileName.contains("-telemetry-fused")
        }

        assertEquals(1, fusedClips.size)
        assertEquals("VID_DASH_1-telemetry-fused.mp4", fusedClips[0].fileName)
        assertFalse(fusedClips.any { it.fileName == "VID_DASH_1.mp4" })

        val dashClips = allClips.filter {
            !it.isSentryEvent && !it.fileName.contains("-telemetry-fused")
        }
        assertEquals(1, dashClips.size)
        assertEquals("VID_DASH_1.mp4", dashClips[0].fileName)
    }
}
