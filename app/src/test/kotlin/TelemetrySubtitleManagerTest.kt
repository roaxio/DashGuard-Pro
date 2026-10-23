package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.media.TelemetryPoint
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TelemetrySubtitleManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFormatTelemetryLine() {
        val point = TelemetryPoint(
            offsetSeconds = 0,
            wallClockTimeMs = 1756482600000L, // Fixed time
            speedDisplay = 65f,
            speedUnit = "km/h",
            latitude = 37.4220,
            longitude = -122.0840,
            altitudeMeters = 55.0,
            headingDegrees = 90f,
            headingCardinal = "E",
            gForce = 1.05f,
            isLocked = false
        )

        val line = TelemetrySubtitleManager.formatTelemetryLine(point)
        assertTrue(line.contains("65 KM/H"))
        assertTrue(line.contains("37.4220°N, 122.0840°W"))
        assertTrue(line.contains("ALT: 55m"))
        assertTrue(line.contains("E 90°"))
    }

    @Test
    fun testWriteAndParseSubtitleFile() {
        val srtFile = File(tempFolder.root, "test_video.srt")
        val points = listOf(
            TelemetryPoint(
                offsetSeconds = 0,
                wallClockTimeMs = 1000000L,
                speedDisplay = 40f,
                speedUnit = "km/h",
                latitude = 37.4220,
                longitude = -122.0840,
                altitudeMeters = 10.0,
                headingDegrees = 0f,
                headingCardinal = "N",
                gForce = 1.0f,
                isLocked = false
            ),
            TelemetryPoint(
                offsetSeconds = 1,
                wallClockTimeMs = 1001000L,
                speedDisplay = 45f,
                speedUnit = "km/h",
                latitude = 37.4230,
                longitude = -122.0830,
                altitudeMeters = 12.0,
                headingDegrees = 10f,
                headingCardinal = "N",
                gForce = 1.1f,
                isLocked = true
            )
        )

        val success = TelemetrySubtitleManager.writeSubtitleFile(srtFile, points)
        assertTrue(success)
        assertTrue(srtFile.exists())

        val entries = TelemetrySubtitleManager.parseSubtitleFile(srtFile)
        assertEquals(2, entries.size)
        assertEquals(1, entries[0].index)
        assertEquals(0L, entries[0].startMs)
        assertEquals(1000L, entries[0].endMs)

        val textAtZero = TelemetrySubtitleManager.getTelemetryForPosition(entries, 500)
        assertNotNull(textAtZero)
        assertTrue(textAtZero!!.contains("40 KM/H"))

        val textAtOne = TelemetrySubtitleManager.getTelemetryForPosition(entries, 1200)
        assertNotNull(textAtOne)
        assertTrue(textAtOne!!.contains("45 KM/H"))
        assertTrue(textAtOne.contains("LOCKED"))
    }
}
