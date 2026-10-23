package io.github.roaxio.dashguardpro

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.BatteryMonitor
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.sentry.SentryController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryMonitorPowerEdgeCaseTest {

    private lateinit var context: Context
    private lateinit var batteryMonitor: BatteryMonitor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        batteryMonitor = BatteryMonitor(context)
    }

    private fun sendBroadcastAndIdle(intent: Intent) {
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun testBatteryInfo_isPluggedComputation() {
        val batAc = BatteryInfo(powerSource = PowerSource.AC)
        assertTrue(batAc.isPlugged)

        val batUsb = BatteryInfo(powerSource = PowerSource.USB)
        assertTrue(batUsb.isPlugged)

        val batWireless = BatteryInfo(powerSource = PowerSource.WIRELESS)
        assertTrue(batWireless.isPlugged)

        val batBattery = BatteryInfo(powerSource = PowerSource.BATTERY)
        assertFalse(batBattery.isPlugged)
    }

    @Test
    fun testBatteryMonitor_maxBatteryNotCharging_doesNotTriggerPowerDisconnected() {
        var powerConnectedCount = 0
        var powerDisconnectedCount = 0

        batteryMonitor.onPowerConnected = { powerConnectedCount++ }
        batteryMonitor.onPowerDisconnected = { powerDisconnectedCount++ }
        batteryMonitor.startListening()

        // 1. Initial State: Plugged into AC charger, actively charging at 95%
        val chargingIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 95)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 280)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 4200)
        }
        sendBroadcastAndIdle(chargingIntent)

        assertEquals(1, powerConnectedCount)
        assertEquals(0, powerDisconnectedCount)
        assertTrue(batteryMonitor.batteryState.value.isPlugged)
        assertTrue(batteryMonitor.batteryState.value.isCharging)
        assertEquals(PowerSource.AC, batteryMonitor.batteryState.value.powerSource)

        // 2. Battery reaches 100% (or OEM battery protect limit):
        // Android OS sets status to BATTERY_STATUS_NOT_CHARGING (4) while EXTRA_PLUGGED remains AC
        val maxChargeIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 100)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_NOT_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 290)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 4350)
        }
        sendBroadcastAndIdle(maxChargeIntent)

        // CRITICAL INVARIANT: The device is STILL PLUGGED IN!
        // It must NOT trigger onPowerDisconnected, nor trigger onPowerConnected again.
        assertEquals(1, powerConnectedCount)
        assertEquals(0, powerDisconnectedCount)
        assertTrue(batteryMonitor.batteryState.value.isPlugged)
        assertFalse(batteryMonitor.batteryState.value.isCharging)
        assertEquals(PowerSource.AC, batteryMonitor.batteryState.value.powerSource)

        // 3. Status changes to BATTERY_STATUS_FULL with EXTRA_PLUGGED = AC
        val fullIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 100)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 285)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 4350)
        }
        sendBroadcastAndIdle(fullIntent)

        assertEquals(1, powerConnectedCount)
        assertEquals(0, powerDisconnectedCount)
        assertTrue(batteryMonitor.batteryState.value.isPlugged)
        assertTrue(batteryMonitor.batteryState.value.isCharging)

        // 4. Physical cord unplug: EXTRA_PLUGGED = 0, status = BATTERY_STATUS_DISCHARGING
        val unplugIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 100)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 280)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 4200)
        }
        sendBroadcastAndIdle(unplugIntent)

        // NOW onPowerDisconnected must fire!
        assertEquals(1, powerConnectedCount)
        assertEquals(1, powerDisconnectedCount)
        assertFalse(batteryMonitor.batteryState.value.isPlugged)
        assertFalse(batteryMonitor.batteryState.value.isCharging)
        assertEquals(PowerSource.BATTERY, batteryMonitor.batteryState.value.powerSource)

        batteryMonitor.stopListening()
    }

    @Test
    fun testDirectActionPowerConnectedAndDisconnected() {
        var powerConnectedCount = 0
        var powerDisconnectedCount = 0

        batteryMonitor.onPowerConnected = { powerConnectedCount++ }
        batteryMonitor.onPowerDisconnected = { powerDisconnectedCount++ }
        batteryMonitor.startListening()

        // Send ACTION_POWER_CONNECTED
        sendBroadcastAndIdle(Intent(Intent.ACTION_POWER_CONNECTED))
        assertEquals(1, powerConnectedCount)
        assertEquals(0, powerDisconnectedCount)
        assertTrue(batteryMonitor.batteryState.value.isPlugged)

        // Duplicate ACTION_POWER_CONNECTED should be debounced
        sendBroadcastAndIdle(Intent(Intent.ACTION_POWER_CONNECTED))
        assertEquals(1, powerConnectedCount)

        // Send ACTION_POWER_DISCONNECTED
        sendBroadcastAndIdle(Intent(Intent.ACTION_POWER_DISCONNECTED))
        assertEquals(1, powerConnectedCount)
        assertEquals(1, powerDisconnectedCount)
        assertFalse(batteryMonitor.batteryState.value.isPlugged)
        assertEquals(PowerSource.BATTERY, batteryMonitor.batteryState.value.powerSource)

        batteryMonitor.stopListening()
    }

    @Test
    fun testSentryController_shouldPromptDashcamOnDisarm_usesPluggedState() {
        val app = context as DashGuardApplication
        val sentryController = SentryController(app.alertManager, app.acousticNoiseMonitor)

        // If manual disarm while plugged into power (even if not charging because battery is 100%)
        assertTrue(sentryController.shouldPromptDashcamOnDisarm(isManual = true, isPlugged = true))

        // If manual disarm while on battery
        assertFalse(sentryController.shouldPromptDashcamOnDisarm(isManual = true, isPlugged = false))

        // If automatic disarm while plugged
        assertFalse(sentryController.shouldPromptDashcamOnDisarm(isManual = false, isPlugged = true))
    }
}
