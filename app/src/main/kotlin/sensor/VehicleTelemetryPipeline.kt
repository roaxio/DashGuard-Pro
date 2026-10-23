package io.github.roaxio.dashguardpro.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pipeline coordinating GPS telemetry and IMU sensor fusion into higher-level
 * driving safety assertions and hysteresis lockout state (AGENTS.md Rule 22.1 & Rule 1).
 */
class VehicleTelemetryPipeline(
    private val telemetryState: StateFlow<TelemetryData>,
    private val gForceState: StateFlow<GForceData>,
    private val scope: CoroutineScope
) {
    companion object {
        const val SPEED_LOCKOUT_THRESHOLD_KMH = 16.0f
        const val ACCELEROMETER_STILLNESS_THRESHOLD_G = 0.35f
        const val HYSTERESIS_COUNTDOWN_SECONDS = 5
    }

    private val _isDrivingLockoutActive = MutableStateFlow(false)
    val isDrivingLockoutActive: StateFlow<Boolean> = _isDrivingLockoutActive.asStateFlow()

    private var speedLockoutHysteresisJob: Job? = null

    init {
        startTelemetryObservation()
    }

    private fun startTelemetryObservation() {
        scope.launch {
            telemetryState.collect { telem ->
                evaluateTelemetry(telem.speedKmh)
            }
        }
    }

    fun evaluateTelemetry(speedKmh: Float) {
        if (speedKmh > SPEED_LOCKOUT_THRESHOLD_KMH) {
            speedLockoutHysteresisJob?.cancel()
            speedLockoutHysteresisJob = null
            _isDrivingLockoutActive.value = true
        } else if (_isDrivingLockoutActive.value) {
            // Start 5-second hysteresis countdown if not already running
            if (speedLockoutHysteresisJob?.isActive != true) {
                speedLockoutHysteresisJob = scope.launch {
                    var elapsedSeconds = 0
                    while (elapsedSeconds < HYSTERESIS_COUNTDOWN_SECONDS) {
                        delay(1000)
                        val currentSpeed = telemetryState.value.speedKmh
                        val currentG = gForceState.value.totalG
                        val isAccelerometerMoving = abs(currentG - 1.0f) > ACCELEROMETER_STILLNESS_THRESHOLD_G
                        if (currentSpeed > SPEED_LOCKOUT_THRESHOLD_KMH || isAccelerometerMoving) {
                            // Reset countdown if vehicle is in motion or shaking
                            elapsedSeconds = 0
                        } else {
                            elapsedSeconds++
                        }
                    }
                    _isDrivingLockoutActive.value = false
                    speedLockoutHysteresisJob = null
                }
            }
        }
    }

    /**
     * Determines whether the vehicle is physically in motion or within the active hysteresis barrier.
     */
    fun isVehicleInMotion(): Boolean {
        return _isDrivingLockoutActive.value || telemetryState.value.speedKmh > SPEED_LOCKOUT_THRESHOLD_KMH
    }

    /**
     * Computes incremented trip statistics for an elapsed 1-second interval.
     */
    fun calculateTripStats(
        prevStats: io.github.roaxio.dashguardpro.viewmodel.state.TripStats,
        curSpeedKmh: Float,
        isSegmentLocked: Boolean
    ): io.github.roaxio.dashguardpro.viewmodel.state.TripStats {
        val newDriveSeconds = prevStats.driveSeconds + 1
        val distanceIncrementKm = (curSpeedKmh / 3600f) // km traveled in 1 second
        val newDistKm = prevStats.distanceKm + distanceIncrementKm
        val newMaxSpeed = maxOf(prevStats.maxSpeedKmh, curSpeedKmh)
        val newAvgSpeed = if (newDriveSeconds > 0) (newDistKm / (newDriveSeconds / 3600f)) else 0f

        return prevStats.copy(
            driveSeconds = newDriveSeconds,
            distanceKm = newDistKm,
            maxSpeedKmh = newMaxSpeed,
            avgSpeedKmh = newAvgSpeed,
            lockedIncidentsCount = if (isSegmentLocked) maxOf(prevStats.lockedIncidentsCount, 1) else prevStats.lockedIncidentsCount
        )
    }
}
