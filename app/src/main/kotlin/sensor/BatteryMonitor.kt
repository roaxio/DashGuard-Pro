package io.github.roaxio.dashguardpro.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PowerSource {
    AC,
    USB,
    WIRELESS,
    BATTERY
}

enum class TemperatureStatus(val label: String) {
    COOL("Cool / Safe"),
    NORMAL("Normal"),
    WARM("Warm"),
    HOT("High Temp (Hot)"),
    CRITICAL("Critical Overheating")
}

data class BatteryInfo(
    val levelPct: Int = 100,
    val isCharging: Boolean = false,
    val powerSource: PowerSource = PowerSource.BATTERY,
    val temperatureC: Float = 25.0f,
    val voltageMv: Int = 4000,
    val isOverheating: Boolean = false
) {
    val isPlugged: Boolean
        get() = powerSource != PowerSource.BATTERY

    val temperatureStatus: TemperatureStatus
        get() = when {
            temperatureC >= 48f || isOverheating -> TemperatureStatus.CRITICAL
            temperatureC >= 45f -> TemperatureStatus.HOT
            temperatureC >= 41f -> TemperatureStatus.WARM
            temperatureC < 38f -> TemperatureStatus.COOL
            else -> TemperatureStatus.NORMAL
        }
}

class BatteryMonitor(private val context: Context) {
    private val _batteryState = MutableStateFlow(BatteryInfo())
    val batteryState: StateFlow<BatteryInfo> = _batteryState.asStateFlow()

    var onPowerConnected: (() -> Unit)? = null
    var onPowerDisconnected: (() -> Unit)? = null
    var onThermalWarning: ((Float) -> Unit)? = null
    var onWarmTrigger: ((Float) -> Unit)? = null
    var onLowBatteryCutoff: ((Int) -> Unit)? = null

    var maxSafeTempC: Float = 48.0f
        set(value) {
            field = value
            warmThresholdC = if (value > 0f) (value - 7.0f).coerceAtLeast(38.0f) else 41.0f
        }
    var warmThresholdC: Float = 41.0f
    var lowBatterySafetyLimitPct: Int = 15

    private var wasPlugged = false
    private var wasWarmTriggered = false
    private var lastObservedBatteryPct: Int? = null

    fun resetBatteryLevelBaseline(levelPct: Int? = null) {
        lastObservedBatteryPct = levelPct ?: _batteryState.value.levelPct
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                android.util.Log.d("SentryFix", "BatteryMonitor: ACTION_POWER_CONNECTED received, wasPlugged=$wasPlugged")
                if (!wasPlugged) {
                    wasPlugged = true
                    val currentSource = _batteryState.value.powerSource
                    val effectiveSource = if (currentSource == PowerSource.BATTERY) PowerSource.AC else currentSource
                    _batteryState.value = _batteryState.value.copy(
                        isCharging = true,
                        powerSource = effectiveSource
                    )
                    onPowerConnected?.invoke()
                }
                return
            }
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                if (wasPlugged) {
                    wasPlugged = false
                    _batteryState.value = _batteryState.value.copy(
                        isCharging = false,
                        powerSource = PowerSource.BATTERY
                    )
                    onPowerDisconnected?.invoke()
                }
                return
            }
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val levelPct = if (level >= 0 && scale > 0) (level * 100) / scale else 50

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val powerSource = when (chargePlug) {
                    BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
                    BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
                    else -> if (chargePlug > 0) PowerSource.AC else PowerSource.BATTERY
                }
                val isPlugged = powerSource != PowerSource.BATTERY
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        (status == BatteryManager.BATTERY_STATUS_FULL && isPlugged)

                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
                val tempC = rawTemp / 10.0f
                val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)

                val overheating = if (maxSafeTempC > 0f) tempC >= maxSafeTempC else false
                val powerConnected = isPlugged && !wasPlugged
                val powerDisconnected = !isPlugged && wasPlugged
                wasPlugged = isPlugged

                _batteryState.value = BatteryInfo(
                    levelPct = levelPct,
                    isCharging = isCharging,
                    powerSource = powerSource,
                    temperatureC = tempC,
                    voltageMv = voltage,
                    isOverheating = overheating
                )

                if (overheating) {
                    onThermalWarning?.invoke(tempC)
                }

                // Trigger-based proactive thermal defense (zero polling, OS broadcast event)
                if (tempC >= warmThresholdC) {
                    if (!wasWarmTriggered) {
                        wasWarmTriggered = true
                        onWarmTrigger?.invoke(tempC)
                    }
                } else if (tempC < warmThresholdC - 2.0f) {
                    wasWarmTriggered = false
                }

                val prevLevel = lastObservedBatteryPct
                lastObservedBatteryPct = levelPct
                val isBelowLimit = lowBatterySafetyLimitPct > 0 && levelPct <= lowBatterySafetyLimitPct
                val isDecreasing = prevLevel == null || levelPct < prevLevel
                val lowBattery = isBelowLimit && isDecreasing
                if (lowBattery) {
                    onLowBatteryCutoff?.invoke(levelPct)
                }

                if (powerConnected) {
                    onPowerConnected?.invoke()
                } else if (powerDisconnected) {
                    onPowerDisconnected?.invoke()
                }
            }
        }
    }

    fun startListening() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
    }

    fun stopListening() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    fun updateForTesting(
        levelPct: Int,
        tempC: Float,
        isCharging: Boolean,
        powerSource: PowerSource = if (isCharging) PowerSource.AC else PowerSource.BATTERY,
        voltageMv: Int = 4000
    ) {
        val isPlugged = powerSource != PowerSource.BATTERY
        val overheating = if (maxSafeTempC > 0f) tempC >= maxSafeTempC else false
        val powerConnected = isPlugged && !wasPlugged
        val powerDisconnected = !isPlugged && wasPlugged
        wasPlugged = isPlugged

        _batteryState.value = BatteryInfo(
            levelPct = levelPct,
            isCharging = isCharging,
            powerSource = powerSource,
            temperatureC = tempC,
            voltageMv = voltageMv,
            isOverheating = overheating
        )

        if (overheating) {
            onThermalWarning?.invoke(tempC)
        }

        if (tempC >= warmThresholdC) {
            if (!wasWarmTriggered) {
                wasWarmTriggered = true
                onWarmTrigger?.invoke(tempC)
            }
        } else if (tempC < warmThresholdC - 2.0f) {
            wasWarmTriggered = false
        }

        val prevLevel = lastObservedBatteryPct
        lastObservedBatteryPct = levelPct
        val isBelowLimit = lowBatterySafetyLimitPct > 0 && levelPct <= lowBatterySafetyLimitPct
        val isDecreasing = prevLevel == null || levelPct < prevLevel
        val lowBattery = isBelowLimit && isDecreasing
        if (lowBattery) {
            onLowBatteryCutoff?.invoke(levelPct)
        }

        if (powerConnected) {
            onPowerConnected?.invoke()
        } else if (powerDisconnected) {
            onPowerDisconnected?.invoke()
        }
    }
}
