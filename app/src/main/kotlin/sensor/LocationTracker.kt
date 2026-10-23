package io.github.roaxio.dashguardpro.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TelemetryData(
    val speedKmh: Float = 0f,
    val speedDisplay: Float = 0f,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val speedUnitLabel: String = "km/h",
    val speedSource: String = "GPS",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "N",
    val hasGpsFix: Boolean = false,
    val accuracyMeters: Float = 0f,
    val totalDistanceMeters: Float = 0f,
    val lastFixTimestamp: Long = 0L,
    val isSignalLost: Boolean = false
)

class LocationTracker(private val context: Context) : LocationListener, SensorEventListener {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Compass and Orientation Sensor
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _telemetryState = MutableStateFlow(TelemetryData())
    val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()

    var speedUnit: SpeedUnit = SpeedUnit.KMH
        set(value) {
            field = value
            val cur = _telemetryState.value
            val updatedDisplay = when (value) {
                SpeedUnit.KMH -> cur.speedKmh
                SpeedUnit.MPH -> cur.speedKmh * 0.621371f
            }
            _telemetryState.value = cur.copy(
                speedDisplay = updatedDisplay,
                speedUnit = value,
                speedUnitLabel = value.label
            )
        }

    var isTracking: Boolean = false
        private set

    private var lastLocation: Location? = null
    private var lastLocationTimestamp = 0L
    private var accumulatedDistanceMeters = 0f

    // Rotation matrices for phone compass heading
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private fun getCardinalDirection(deg: Float): String {
        val normalized = (deg % 360 + 360) % 360
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "N"
            normalized >= 22.5 && normalized < 67.5 -> "NE"
            normalized >= 67.5 && normalized < 112.5 -> "E"
            normalized >= 112.5 && normalized < 157.5 -> "SE"
            normalized >= 157.5 && normalized < 202.5 -> "S"
            normalized >= 202.5 && normalized < 247.5 -> "SW"
            normalized >= 247.5 && normalized < 292.5 -> "W"
            normalized >= 292.5 && normalized < 337.5 -> "NW"
            else -> "N"
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        isTracking = true
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}

        startGpsTracking()

        // Register Hardware Compass with SENSOR_DELAY_NORMAL (~5Hz) to conserve CPU/battery
        try {
            sensorManager?.unregisterListener(this)
            rotationVectorSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startGpsTracking() {
        try {
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

            if (isGpsEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1000ms (1Hz standard rate allows GPS modem low-power sleep)
                    1.0f,
                    this,
                    Looper.getMainLooper()
                )
            }
            if (isNetworkEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    this,
                    Looper.getMainLooper()
                )
            }

            // Get last known location if available
            val lastLoc = try {
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) { null }

            lastLoc?.let { onLocationChanged(it) }
        } catch (_: SecurityException) {
            // Permissions not yet granted
        }
    }

    fun stopTracking() {
        isTracking = false
        try {
            locationManager?.removeUpdates(this)
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        val now = System.currentTimeMillis()

        // Distance delta calculation between GPS points
        if (lastLocation != null) {
            val deltaDist = lastLocation!!.distanceTo(location)
            // Sanity check: filter out GPS teleportation anomalies (> 200m per 0.5s = > 1440 km/h)
            if (deltaDist in 0.5f..150f) {
                accumulatedDistanceMeters += deltaDist
            }
        }
        lastLocation = location
        lastLocationTimestamp = now

        // Calculate GPS Velocity
        val speedMps = when {
            location.hasSpeed() && location.speed >= 0 -> location.speed
            lastLocation != null && (now - lastLocationTimestamp) > 0 -> {
                val dt = (now - lastLocationTimestamp) / 1000f
                (location.distanceTo(lastLocation!!) / dt).coerceIn(0f, 60f)
            }
            else -> 0f
        }

        val speedKmh = speedMps * 3.6f
        val speedDisplay = when (speedUnit) {
            SpeedUnit.KMH -> speedKmh
            SpeedUnit.MPH -> speedMps * 2.23694f
        }

        // Prefer GPS bearing if moving, else fallback to current magnetometer heading
        val bearing = if (location.hasBearing() && speedKmh > 3f) {
            location.bearing
        } else {
            _telemetryState.value.headingDegrees
        }
        val cardinal = getCardinalDirection(bearing)

        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = speedKmh,
            speedDisplay = speedDisplay,
            speedUnit = speedUnit,
            speedUnitLabel = speedUnit.label,
            speedSource = "GPS",
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = location.altitude,
            headingDegrees = bearing,
            headingCardinal = cardinal,
            hasGpsFix = true,
            accuracyMeters = location.accuracy,
            totalDistanceMeters = accumulatedDistanceMeters,
            lastFixTimestamp = now,
            isSignalLost = false
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // HARDWARE COMPASS ORIENTATION (Magnetometer / Rotation Vector)
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val currentSpeed = _telemetryState.value.speedKmh
            // When moving with GPS fix, GPS bearing is authoritative; skip matrix math
            if (currentSpeed > 3f && _telemetryState.value.hasGpsFix) return

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // Azimuth is orientationAngles[0] in radians (-PI to +PI)
            var azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (azimuthDegrees < 0) azimuthDegrees += 360f

            if (currentSpeed <= 3f || !_telemetryState.value.hasGpsFix) {
                _telemetryState.value = _telemetryState.value.copy(
                    headingDegrees = azimuthDegrees,
                    headingCardinal = getCardinalDirection(azimuthDegrees)
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        _telemetryState.value = _telemetryState.value.copy(hasGpsFix = false, isSignalLost = true)
    }

    fun markSignalLost() {
        _telemetryState.value = _telemetryState.value.copy(hasGpsFix = false, isSignalLost = true)
    }
}
