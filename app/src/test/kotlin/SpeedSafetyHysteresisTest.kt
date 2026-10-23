package io.github.roaxio.dashguardpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.sensor.LocationTracker
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeedSafetyHysteresisTest {

    private lateinit var context: Context
    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        app = context as DashGuardApplication
        viewModel = DashcamViewModel(app)
    }

    @Test
    fun testLocationTrackerSignalLossFlags() {
        val telem = TelemetryData(
            speedKmh = 60f,
            hasGpsFix = true,
            lastFixTimestamp = 1000L,
            isSignalLost = false
        )
        assertTrue(telem.hasGpsFix)
        assertFalse(telem.isSignalLost)
        assertEquals(1000L, telem.lastFixTimestamp)

        val lost = telem.copy(hasGpsFix = false, isSignalLost = true)
        assertFalse(lost.hasGpsFix)
        assertTrue(lost.isSignalLost)
    }

    @Test
    fun testInitialLockoutStateIsFalse() {
        assertFalse(viewModel.isDrivingLockoutActive.value)
    }

    @Test
    fun testSpeedExceeding16KmhEngagesLockout() = runTest {
        val tracker = app.locationTracker
        // Simulate GPS speed above threshold (e.g. 50 km/h)
        val loc = android.location.Location("gps").apply {
            speed = 50f / 3.6f // ~13.88 m/s = 50 km/h
            time = System.currentTimeMillis()
            latitude = 37.4220
            longitude = -122.0840
        }
        tracker.onLocationChanged(loc)

        // Lockout must be active
        assertTrue(viewModel.isDrivingLockoutActive.value)
        assertTrue(viewModel.isVehicleInMotion())
    }

    @Test
    fun testSpeedDroppingToZeroRetainsLockoutDuringHysteresisWindow() = runTest {
        val tracker = app.locationTracker
        // 1. Move at 60 km/h
        val locFast = android.location.Location("gps").apply {
            speed = 60f / 3.6f
            time = System.currentTimeMillis()
            latitude = 37.4220
            longitude = -122.0840
        }
        tracker.onLocationChanged(locFast)
        assertTrue(viewModel.isDrivingLockoutActive.value)

        // 2. Simulate entering tunnel (speed drops abruptly to 0 or GPS lost)
        val locStopped = android.location.Location("gps").apply {
            speed = 0f
            time = System.currentTimeMillis()
            latitude = 37.4220
            longitude = -122.0840
        }
        tracker.onLocationChanged(locStopped)

        // Invariant: Lockout MUST remain active immediately after speed drops to 0!
        assertTrue(viewModel.isDrivingLockoutActive.value)
        assertTrue(viewModel.isVehicleInMotion())
    }
}
