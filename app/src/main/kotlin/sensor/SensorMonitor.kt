package io.github.roaxio.dashguardpro.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class GForceData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val totalG: Float = 1.0f,
    val impactDetected: Boolean = false
)

class SensorMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _gForceState = MutableStateFlow(GForceData())
    val gForceState: StateFlow<GForceData> = _gForceState.asStateFlow()

    var impactThresholdG: Float = 2.4f
    var onImpactDetected: ((Float) -> Unit)? = null

    private var lastImpactTimestamp = 0L
    private var lastEmittedG = 1.0f
    private var lastEmitTimestamp = 0L

    fun startListening() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Earth gravity is ~9.8 m/s^2 (1.0 G)
        val rawMagnitude = sqrt(x * x + y * y + z * z)
        val gValue = rawMagnitude / 9.80665f

        val now = System.currentTimeMillis()
        var impact = false
        if (gValue >= impactThresholdG && impactThresholdG < 900f && (now - lastImpactTimestamp > 5000)) {
            impact = true
            lastImpactTimestamp = now
            onImpactDetected?.invoke(gValue)
        }

        // Throttle UI StateFlow emissions: emit on impact, on significant delta (> 0.2G),
        // or periodically at 2Hz (every 500ms) to prevent 60Hz main-thread recomposition spam
        val shouldEmit = impact ||
                kotlin.math.abs(gValue - lastEmittedG) > 0.2f ||
                (now - lastEmitTimestamp >= 500L)

        if (shouldEmit) {
            lastEmittedG = gValue
            lastEmitTimestamp = now
            _gForceState.value = GForceData(
                x = x / 9.80665f,
                y = y / 9.80665f,
                z = z / 9.80665f,
                totalG = gValue,
                impactDetected = impact
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
