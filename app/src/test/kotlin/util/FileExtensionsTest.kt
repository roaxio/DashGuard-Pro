package io.github.roaxio.dashguardpro.util

import io.github.roaxio.dashguardpro.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileExtensionsTest {

    @Test
    fun testVideoClipFileExtensions() {
        val clip = VideoClip(
            filePath = "/storage/emulated/0/DCIM/DashGuard/VID_LOOP_20260928_120000.mp4",
            fileName = "VID_LOOP_20260928_120000.mp4"
        )

        assertEquals(File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_20260928_120000.mp4"), clip.videoFile)
        assertEquals(File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_20260928_120000.srt"), clip.srtSidecarFile)
        assertEquals(File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_20260928_120000-telemetry-fused.mp4"), clip.fusedVideoFile)
    }

    @Test
    fun testVideoClipCoordinateEvaluation() {
        val zeroClip = VideoClip(
            filePath = "/test.mp4",
            fileName = "test.mp4",
            latitude = 0.0,
            longitude = 0.0,
            startLatitude = 0.0,
            startLongitude = 0.0
        )
        assertFalse(zeroClip.hasValidCoordinates)
        assertNull(zeroClip.effectiveCoordinates)
        assertNull(zeroClip.effectiveStartCoordinates)
        assertNull(zeroClip.effectiveEndCoordinates)

        val primaryClip = VideoClip(
            filePath = "/test.mp4",
            fileName = "test.mp4",
            latitude = 37.4220,
            longitude = -122.0840
        )
        assertTrue(primaryClip.hasValidCoordinates)
        val coords = primaryClip.effectiveCoordinates
        assertNotNull(coords)
        assertEquals(37.4220, coords!!.first, 0.0001)
        assertEquals(-122.0840, coords.second, 0.0001)

        val rangeClip = VideoClip(
            filePath = "/test.mp4",
            fileName = "test.mp4",
            startLatitude = 37.4200,
            startLongitude = -122.0800,
            endLatitude = 37.4300,
            endLongitude = -122.0900
        )
        assertTrue(rangeClip.hasValidCoordinates)
        assertEquals(Pair(37.4200, -122.0800), rangeClip.effectiveStartCoordinates)
        assertEquals(Pair(37.4300, -122.0900), rangeClip.effectiveEndCoordinates)
    }

    @Test
    fun testFileSidecarAndFusedExtensions() {
        val rawFile = File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_1.mp4")
        assertFalse(rawFile.isFusedVideo)
        assertEquals(File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_1.srt"), rawFile.srtSidecarFile)
        assertEquals(File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_1-telemetry-fused.mp4"), rawFile.fusedVideoFile)
        assertEquals(rawFile, rawFile.rawSourceVideoFile)

        val fusedFile = File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_1-telemetry-fused.mp4")
        assertTrue(fusedFile.isFusedVideo)
        assertEquals(File("/storage/emulated/0/DCIM/DashGuard/VID_LOOP_1.srt"), fusedFile.srtSidecarFile)
        assertEquals(rawFile, fusedFile.rawSourceVideoFile)
    }

    @Test
    fun testIsDashGuardRecordingFile_distinguishesAppFromForeignFiles() {
        // Valid DashGuard files
        assertTrue(File("VID_LOOP_20261001_120000.mp4").isDashGuardRecordingFile)
        assertTrue(File("VID_DASH_20261001_120000_1.mp4").isDashGuardRecordingFile)
        assertTrue(File("VID_SENTRY_20261001_120000.srt").isDashGuardRecordingFile)
        assertTrue(File("VID_LOOP_20261001_120000-telemetry-fused.mp4").isDashGuardRecordingFile)
        assertTrue(File("VID_MANUAL_20261001_120000_2-telemetry-fused.mp4").isDashGuardRecordingFile)
        assertTrue(File("VID_INCIDENT_20261001_120000.mp4").isDashGuardRecordingFile)

        // Foreign / Non-app files
        assertFalse(File("family_vacation.mp4").isDashGuardRecordingFile)
        assertFalse(File("movie.mp4").isDashGuardRecordingFile)
        assertFalse(File("dcim_1001.mp4").isDashGuardRecordingFile)
        assertFalse(File("download.mp4").isDashGuardRecordingFile)
        assertFalse(File("download.tmp").isDashGuardRecordingFile)
        assertFalse(File("VID_YOLO_20261001_120000.mp4").isDashGuardRecordingFile)
        assertFalse(File("VID_FDSFAFASDF_20261001_120000.mp4").isDashGuardRecordingFile)
        assertFalse(File("VID_LOOP_20261001_120000.mp4.tmp").isDashGuardRecordingFile)
        assertFalse(File("VID_OTHER_123.mp4").isDashGuardRecordingFile)
        assertFalse(File("VID_LOOP_20261001_120000.jpg").isDashGuardRecordingFile)
        assertFalse(File("notes.txt").isDashGuardRecordingFile)
        assertFalse(File("").isDashGuardRecordingFile)
    }
}
