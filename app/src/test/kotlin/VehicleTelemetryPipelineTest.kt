package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.sensor.VehicleTelemetryPipeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleTelemetryPipelineTest {

    @Test
    fun testVehicleTelemetryPipeline_speedLockoutAndHysteresis() = runTest {
        val telemetryFlow = MutableStateFlow(TelemetryData(speedKmh = 0f))
        val gForceFlow = MutableStateFlow(GForceData(x = 0f, y = 0f, z = 1f, totalG = 1.0f))

        val pipeline = VehicleTelemetryPipeline(
            telemetryState = telemetryFlow,
            gForceState = gForceFlow,
            scope = backgroundScope
        )

        testScheduler.runCurrent()
        assertFalse("Initial lockout must be inactive", pipeline.isDrivingLockoutActive.value)

        // Accelerate beyond threshold (16 km/h)
        telemetryFlow.value = TelemetryData(speedKmh = 45f)
        testScheduler.runCurrent()
        assertTrue("Driving lockout must activate when traveling at 45 km/h", pipeline.isDrivingLockoutActive.value)

        // Drop speed to 0 km/h
        telemetryFlow.value = TelemetryData(speedKmh = 0f)
        testScheduler.runCurrent()

        // Hysteresis window: after 3 seconds, should still be locked out
        advanceTimeBy(3000)
        testScheduler.runCurrent()
        assertTrue("Driving lockout must remain active during 5s deadband", pipeline.isDrivingLockoutActive.value)

        // After completing 5 seconds of continuous stillness (remaining 2.5s), lockout disarms
        advanceTimeBy(2500)
        testScheduler.runCurrent()
        assertFalse("Driving lockout must disarm after 5 seconds of stillness", pipeline.isDrivingLockoutActive.value)
    }

    @Test
    fun testVehicleTelemetryPipeline_motionResetsCountdown() = runTest {
        val telemetryFlow = MutableStateFlow(TelemetryData(speedKmh = 25f))
        val gForceFlow = MutableStateFlow(GForceData(x = 0f, y = 0f, z = 1f, totalG = 1.0f))

        val pipeline = VehicleTelemetryPipeline(
            telemetryState = telemetryFlow,
            gForceState = gForceFlow,
            scope = backgroundScope
        )
        testScheduler.runCurrent()
        assertTrue("Must be active at 25 km/h", pipeline.isDrivingLockoutActive.value)

        // Speed drops to 0 km/h
        telemetryFlow.value = TelemetryData(speedKmh = 0f)
        testScheduler.runCurrent()

        // Advance 3 seconds
        advanceTimeBy(3000)
        testScheduler.runCurrent()

        // Accelerometer detects vehicle vibration (e.g. engine idle shake / rolling)
        gForceFlow.value = GForceData(x = 0.5f, y = 0.3f, z = 1.3f, totalG = 1.45f)
        advanceTimeBy(1000)
        testScheduler.runCurrent()

        // Count should be reset: advance another 3 seconds (total 7 seconds since speed drop)
        advanceTimeBy(3000)
        testScheduler.runCurrent()
        assertTrue("Vibration must reset countdown and keep lockout active", pipeline.isDrivingLockoutActive.value)

        // Restore stillness (totalG = 1.0) and wait full 5 seconds
        gForceFlow.value = GForceData(x = 0f, y = 0f, z = 1f, totalG = 1.0f)
        advanceTimeBy(5500)
        testScheduler.runCurrent()
        assertFalse("Lockout must disarm only after full 5s of clean stillness", pipeline.isDrivingLockoutActive.value)
    }

    @Test
    fun testVehicleTelemetryPipeline_isVehicleInMotion() = runTest {
        val telemetryFlow = MutableStateFlow(TelemetryData(speedKmh = 0f))
        val gForceFlow = MutableStateFlow(GForceData(x = 0f, y = 0f, z = 1f, totalG = 1.0f))

        val pipeline = VehicleTelemetryPipeline(
            telemetryState = telemetryFlow,
            gForceState = gForceFlow,
            scope = backgroundScope
        )
        testScheduler.runCurrent()

        assertFalse("Stationary vehicle must not report in motion", pipeline.isVehicleInMotion())

        // Traveling above threshold (e.g. 20 km/h)
        telemetryFlow.value = TelemetryData(speedKmh = 20f)
        testScheduler.runCurrent()
        assertTrue("Vehicle traveling at 20 km/h must report in motion", pipeline.isVehicleInMotion())

        // Speed drops to 0 km/h; hysteresis keeps it active
        telemetryFlow.value = TelemetryData(speedKmh = 0f)
        testScheduler.runCurrent()
        assertTrue("Vehicle in hysteresis window must report in motion", pipeline.isVehicleInMotion())

        // Advance beyond hysteresis
        advanceTimeBy(5500)
        testScheduler.runCurrent()
        assertFalse("Vehicle after hysteresis window and stopped must report not in motion", pipeline.isVehicleInMotion())
    }

    @Test
    fun testVehicleTelemetryPipeline_calculateTripStats() = runTest {
        val telemetryFlow = MutableStateFlow(TelemetryData(speedKmh = 0f))
        val gForceFlow = MutableStateFlow(GForceData(x = 0f, y = 0f, z = 1f, totalG = 1.0f))

        val pipeline = VehicleTelemetryPipeline(
            telemetryState = telemetryFlow,
            gForceState = gForceFlow,
            scope = backgroundScope
        )

        val initialStats = io.github.roaxio.dashguardpro.viewmodel.state.TripStats()
        // 1 second at 72 km/h -> 72 / 3600 = 0.02 km
        val step1 = pipeline.calculateTripStats(
            prevStats = initialStats,
            curSpeedKmh = 72f,
            isSegmentLocked = false
        )
        org.junit.Assert.assertEquals(1L, step1.driveSeconds)
        org.junit.Assert.assertEquals(0.02f, step1.distanceKm, 0.001f)
        org.junit.Assert.assertEquals(72f, step1.maxSpeedKmh, 0.001f)
        org.junit.Assert.assertEquals(72f, step1.avgSpeedKmh, 0.001f)
        org.junit.Assert.assertEquals(0, step1.lockedIncidentsCount)

        // 2nd second at 36 km/h -> +0.01 km = 0.03 km total over 2 seconds -> avg 54 km/h
        val step2 = pipeline.calculateTripStats(
            prevStats = step1,
            curSpeedKmh = 36f,
            isSegmentLocked = true
        )
        org.junit.Assert.assertEquals(2L, step2.driveSeconds)
        org.junit.Assert.assertEquals(0.03f, step2.distanceKm, 0.001f)
        org.junit.Assert.assertEquals(72f, step2.maxSpeedKmh, 0.001f)
        org.junit.Assert.assertEquals(54f, step2.avgSpeedKmh, 0.001f)
        org.junit.Assert.assertEquals(1, step2.lockedIncidentsCount)
    }
}
