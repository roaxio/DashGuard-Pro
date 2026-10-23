package io.github.roaxio.dashguardpro.util

import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFormatBytesCompact() {
        val mbBytes = 500L * 1024L * 1024L
        assertEquals("500.0 MB", FormatUtils.formatBytesCompact(mbBytes))

        val gbBytes = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals("1.50 GB", FormatUtils.formatBytesCompact(gbBytes))
    }

    @Test
    fun testFormatBytesWithUnit() {
        val kbBytes = 512L * 1024L
        assertEquals("512 KB", FormatUtils.formatBytesWithUnit(kbBytes))

        val mbBytes = 250L * 1024L * 1024L
        assertEquals("250.0 MB", FormatUtils.formatBytesWithUnit(mbBytes))

        val gbBytes = 2L * 1024L * 1024L * 1024L
        assertEquals("2.00 GB", FormatUtils.formatBytesWithUnit(gbBytes))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("45s", FormatUtils.formatDuration(45L))
        assertEquals("1m 15s", FormatUtils.formatDuration(75L))
        assertEquals("2m 0s", FormatUtils.formatDuration(120L))
    }

    @Test
    fun testFormatDurationDetailed() {
        assertEquals("45s", FormatUtils.formatDurationDetailed(45L))
        assertEquals("75s (1m 15s)", FormatUtils.formatDurationDetailed(75L))
    }

    @Test
    fun testFormatTripDuration() {
        assertEquals("0m 45s", FormatUtils.formatTripDuration(45_000L))
        assertEquals("1m 15s", FormatUtils.formatTripDuration(75_000L))
        assertEquals("1h 1m 15s", FormatUtils.formatTripDuration(3_675_000L))
    }

    @Test
    fun testFormatDistance() {
        assertEquals("450 m", FormatUtils.formatDistance(450f))
        assertEquals("1.50 km (1500 m)", FormatUtils.formatDistance(1500f))
    }

    @Test
    fun testFormatTripDistance() {
        assertNull(FormatUtils.formatTripDistance(0f))
        assertEquals("500 m", FormatUtils.formatTripDistance(500f))
        assertEquals("2.50 km", FormatUtils.formatTripDistance(2500f))
    }

    @Test
    fun testFormatSpeed() {
        assertEquals("65 km/h", FormatUtils.formatSpeed(65.4f))
        assertEquals("40 mph", FormatUtils.formatSpeed(40.8f, "mph"))
        assertEquals("100 km/h", FormatUtils.formatSpeed(100f, SpeedUnit.KMH))
        assertEquals("62 mph", FormatUtils.formatSpeed(100f, SpeedUnit.MPH))
        assertEquals("62 MPH", FormatUtils.formatSpeedUppercase(100f, SpeedUnit.MPH))
        assertEquals("62.1 mph", FormatUtils.formatSpeedDetailed(100f, SpeedUnit.MPH))
        assertEquals("310 ft", FormatUtils.formatDistanceCompact(94.5f, SpeedUnit.MPH, spaceBeforeMeters = true))
        assertEquals("1.00 mi", FormatUtils.formatTripDistance(1609.344f, SpeedUnit.MPH))
    }

    @Test
    fun testFormatCoordinates() {
        assertEquals("37.4220, -122.0840", FormatUtils.formatCoordinates(37.42202, -122.08399))
    }

    @Test
    fun testFormatStorageCapacityAndFileSizeMb() {
        val gb50 = 50L * 1024L * 1024L * 1024L
        val mb512 = 512L * 1024L * 1024L
        assertEquals("50.0 GB free", FormatUtils.formatStorageCapacityFree(gb50))
        assertEquals("512 MB free", FormatUtils.formatStorageCapacityFree(mb512))
        assertEquals("50.0 GB total", FormatUtils.formatStorageCapacityTotal(gb50))
        assertEquals("512.00 MB", FormatUtils.formatFileSizeMb(mb512))
        assertEquals("450 meters", FormatUtils.formatDistanceMetersOrKm(450f))
        assertEquals("2.50 km", FormatUtils.formatDistanceMetersOrKm(2500f))
    }
}
