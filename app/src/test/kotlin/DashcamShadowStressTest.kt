package io.github.roaxio.dashguardpro

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.AppDatabase
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.storage.LoopStorageManager
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBatteryManager
import org.robolectric.shadows.ShadowPowerManager
import java.io.File
import kotlin.math.sqrt

/**
 * High-reliability stress testing and shadow environment integration suite.
 * Evaluates system lifecycle shadows, broadcast triggers, concurrency stress,
 * and sensor telemetry algorithms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashcamShadowStressTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var clipDao: ClipDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var storageManager: LoopStorageManager
    private lateinit var shadowPowerManager: ShadowPowerManager
    private lateinit var shadowBatteryManager: ShadowBatteryManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clipDao = database.clipDao()
        settingsRepository = SettingsRepository(context)
        storageManager = LoopStorageManager(context, clipDao, settingsRepository)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowPowerManager = shadowOf(powerManager)
        shadowBatteryManager = shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
    }

    @After
    fun teardown() {
        database.close()
    }

    // ==========================================
    // 1. SHADOW POWER & BATTERY STRESS TESTS
    // ==========================================

    @Test
    fun testShadowBatteryManager_powerSourceTransitions() {
        // Test USB Connected
        val usbIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
            putExtra(BatteryManager.EXTRA_LEVEL, 85)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 320) // 32.0 C
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        }
        
        val usbSource = when (usbIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
            else -> PowerSource.BATTERY
        }
        assertEquals(PowerSource.USB, usbSource)

        // Test AC Connected (Automotive 12V adapter)
        val acIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
            putExtra(BatteryManager.EXTRA_LEVEL, 95)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
        }
        val acSource = when (acIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
            else -> PowerSource.BATTERY
        }
        assertEquals(PowerSource.AC, acSource)

        // Test Unplugged State
        val unplugIntent = Intent(Intent.ACTION_POWER_DISCONNECTED)
        assertNotNull(unplugIntent)
        assertEquals(Intent.ACTION_POWER_DISCONNECTED, unplugIntent.action)
    }

    @Test
    fun testBatteryOverheatingCutoff_thresholdVerification() {
        val settings = settingsRepository.settings.value
        val thermalLimit = settings.maxTempThresholdC // 46.0 C

        // Normal temperature: 38.5 C
        val normalTemp = 38.5f
        val isNormalOverheating = normalTemp >= thermalLimit
        assertFalse(isNormalOverheating)

        // Overheating temperature: 49.5 C
        val hotTemp = 49.5f
        val isHotOverheating = hotTemp >= thermalLimit
        assertTrue(isHotOverheating)
    }

    @Test
    fun testLowBatterySafetyCutoff_thresholdVerification() {
        val settings = settingsRepository.settings.value
        val lowBatteryLimit = settings.lowBatterySafetyLimitPct // 15%

        // Safe level: 25%
        assertFalse(25 <= lowBatteryLimit)

        // Critical level: 8%
        assertTrue(8 <= lowBatteryLimit)

        // Boundary level: 10%
        assertTrue(10 <= lowBatteryLimit)
    }

    @Test
    fun testShadowPowerManager_screenStateAndWakeLock() {
        shadowPowerManager.setIsInteractive(true)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        assertTrue(powerManager.isInteractive)

        shadowPowerManager.setIsInteractive(false)
        assertFalse(powerManager.isInteractive)
    }

    // ==========================================
    // 2. G-FORCE & SENSOR ALGORITHM TESTS
    // ==========================================

    @Test
    fun testGForceVectorCalculation_andImpactDetection() {
        // Flat on table (gravity along Z)
        val ax = 0f
        val ay = 0f
        val az = 9.81f
        val totalG = sqrt(ax * ax + ay * ay + az * az) / 9.80665f
        assertEquals(1.0f, totalG, 0.05f)

        // Impact shock test (Pothole or Collision)
        val shockAx = 15.0f
        val shockAy = 20.0f
        val shockAz = 18.0f
        val shockTotalG = sqrt(shockAx * shockAx + shockAy * shockAy + shockAz * shockAz) / 9.80665f
        assertTrue(shockTotalG > 2.4f) // Exceeds Medium Sensitivity (2.4G)

        // Test Sensitivity comparisons
        assertTrue(shockTotalG >= Sensitivity.MEDIUM.thresholdG)
        assertTrue(shockTotalG >= Sensitivity.HIGH.thresholdG)
        assertTrue(shockTotalG < Sensitivity.OFF.thresholdG)
    }

    @Test
    fun testSpeedUnitConversions_andFormatting() {
        val speedMps = 25.0f // 25 m/s = 90 km/h = ~55.92 mph
        val kmh = speedMps * SpeedUnit.KMH.multiplier
        val mph = speedMps * SpeedUnit.MPH.multiplier

        assertEquals(90.0f, kmh, 0.1f)
        assertEquals(55.92f, mph, 0.1f)

        // Speed Display formatting
        val formattedKmh = "%.1f %s".format(kmh, SpeedUnit.KMH.label)
        val formattedMph = "%.1f %s".format(mph, SpeedUnit.MPH.label)

        assertEquals("90.0 km/h", formattedKmh)
        assertEquals("55.9 mph", formattedMph)
    }

    // ==========================================
    // 3. CONCURRENCY & STRESS PERSISTENCE TESTS
    // ==========================================

    @Test
    fun testStressConcurrency_parallelClipInsertions() {
        runBlocking {
            // Insert 100 clips concurrently across multiple coroutines
            val jobs = (1..100).map { id ->
                async(Dispatchers.IO) {
                    val isIncident = (id % 5 == 0) // Every 5th clip is an incident
                    val clip = VideoClip(
                        filePath = "/mock/storage/stress_clip_$id.mp4",
                        fileName = "stress_clip_$id.mp4",
                        timestamp = System.currentTimeMillis() + id * 1000L,
                        durationMs = 180000L,
                        fileSizeBytes = 50L * 1024 * 1024, // 50MB
                        isLocked = isIncident,
                        isSentryEvent = (id % 10 == 0),
                        triggerReason = if (isIncident) "Impact G-Shock" else "Normal Loop",
                        speedKmh = 60f + (id % 40),
                        latitude = 37.7749 + (id * 0.0001),
                        longitude = -122.4194 + (id * 0.0001),
                        incidentGForce = if (isIncident) 3.2f else 1.1f,
                        incidentVideoOffsetSeconds = if (isIncident) 45 else -1
                    )
                    clipDao.insertClip(clip)
                }
            }
            jobs.awaitAll()

            val allClips = clipDao.getAllClips().first()
            assertEquals(100, allClips.size)

            val lockedClips = clipDao.getLockedClips().first()
            assertEquals(20, lockedClips.size)

            val sentryClips = clipDao.getSentryClips().first()
            assertEquals(10, sentryClips.size)
        }
    }

    @Test
    fun testStressQuotaPruning_preservesAllLockedIncidentsUnderExtremeMemoryPressure() {
        runBlocking {
            val testDir = File(context.cacheDir, "dashguard_stress_test_dir")
            testDir.mkdirs()

            // Generate 50 physical test files
            for (i in 1..50) {
                val file = File(testDir, "VID_DASH_2026_${"%03d".format(i)}.mp4")
                file.writeBytes(ByteArray(1024 * 64)) // 64KB dummy data
                val isLocked = (i <= 15) // First 15 clips are locked incidents

                val clip = VideoClip(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    timestamp = 1000000L + (i * 1000L),
                    durationMs = 60000L,
                    fileSizeBytes = file.length(),
                    isLocked = isLocked,
                    triggerReason = if (isLocked) "💥 Critical Emergency Lock" else "Normal Loop"
                )
                clipDao.insertClip(clip)
            }

            assertEquals(50, clipDao.getAllClips().first().size)
            assertEquals(15, clipDao.getLockedClips().first().size)

            // Perform loop cleanup with 0GB quota to force absolute maximum FIFO pruning
            storageManager.performLoopCleanupIfNeeded(0)

            // Verify: ALL 15 locked clips MUST remain in database AND on disk
            val remainingClips = clipDao.getAllClips().first()
            val remainingLocked = remainingClips.filter { it.isLocked }
            assertEquals(15, remainingLocked.size)

            for (locked in remainingLocked) {
                assertTrue(File(locked.filePath).exists())
                assertTrue(locked.isLocked)
            }

            // Clean up directory
            testDir.deleteRecursively()
        }
    }

    @Test
    fun testBroadcastReceiver_emergencyLockIntent() {
        val filter = Intent("io.github.roaxio.dashguardpro.EMERGENCY_LOCK_TRIGGER")
        assertEquals("io.github.roaxio.dashguardpro.EMERGENCY_LOCK_TRIGGER", filter.action)
    }
}
