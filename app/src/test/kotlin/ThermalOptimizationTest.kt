package io.github.roaxio.dashguardpro

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.AutoDimTimeout
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.feedback.AlertManager
import io.github.roaxio.dashguardpro.sensor.BatteryMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThermalOptimizationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSettingsRepository_defaultAutoDimAndThermalThresholdValues() {
        val repo = SettingsRepository(context)
        val settings = repo.settings.value

        // Default autoDimTimeout should be SEC_15 (15 seconds) for out-of-the-box thermal defense
        assertEquals(AutoDimTimeout.SEC_15, settings.autoDimTimeout)

        // Default maxTempThresholdC should be 48.0°C (calibrated for vehicle windshield operation)
        assertEquals(48.0f, settings.maxTempThresholdC, 0.01f)
    }

    @Test
    fun testBatteryMonitor_eventDrivenWarmTriggerAndCutoffWithHysteresis() {
        val monitor = BatteryMonitor(context)
        var warmTriggerCount = 0
        var cutoffTriggerCount = 0

        monitor.onWarmTrigger = { _ -> warmTriggerCount++ }
        monitor.onThermalWarning = { _ -> cutoffTriggerCount++ }
        monitor.startListening()

        // 1. Send battery changed broadcast below warm threshold (35°C)
        val intent35 = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 350) // 35.0°C
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        context.sendBroadcast(intent35)
        ShadowLooper.idleMainLooper()

        assertEquals(0, warmTriggerCount)
        assertEquals(0, cutoffTriggerCount)
        assertEquals(35.0f, monitor.batteryState.value.temperatureC, 0.1f)

        // 2. Send broadcast crossing warm threshold (41.5°C) -> triggers onWarmTrigger
        val intent41 = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 415) // 41.5°C
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        context.sendBroadcast(intent41)
        ShadowLooper.idleMainLooper()

        assertEquals(1, warmTriggerCount)
        assertEquals(0, cutoffTriggerCount)

        // 3. Second broadcast at warm temp (42.0°C) -> should NOT re-trigger due to latch
        val intent42 = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 420) // 42.0°C
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        context.sendBroadcast(intent42)
        ShadowLooper.idleMainLooper()

        assertEquals(1, warmTriggerCount) // still 1

        // 4. Send broadcast crossing cutoff limit (48.5°C) -> triggers onThermalWarning
        val intent48 = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 485) // 48.5°C
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        context.sendBroadcast(intent48)
        ShadowLooper.idleMainLooper()

        assertEquals(1, warmTriggerCount)
        assertEquals(1, cutoffTriggerCount)
        assertTrue(monitor.batteryState.value.isOverheating)

        // 5. Cool down below hysteresis reset (< 39.0°C)
        val intent38 = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 380) // 38.0°C
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        context.sendBroadcast(intent38)
        ShadowLooper.idleMainLooper()
        assertFalse(monitor.batteryState.value.isOverheating)

        // 6. Warm up again -> rearmed latch triggers onWarmTrigger
        context.sendBroadcast(intent41)
        ShadowLooper.idleMainLooper()
        assertEquals(2, warmTriggerCount)

        monitor.stopListening()
    }

    @Test
    fun testAlertManager_playThermalCutoffAlert_executesWithoutCrashing() {
        val alertManager = AlertManager(context)
        // Should execute cleanly (playing raw resource or fallback ToneGenerator)
        alertManager.playThermalCutoffAlert()
    }
}
