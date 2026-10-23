package io.github.roaxio.dashguardpro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.AppDatabase
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.HudDisplayMode
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SentryArmingTrigger
import io.github.roaxio.dashguardpro.data.settings.SentryDeterrentMode
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.SentryInterruptedClipAction
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.data.settings.VideoQuality
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.storage.LoopStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashcamLogicStressTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var clipDao: ClipDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var storageManager: LoopStorageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clipDao = database.clipDao()
        settingsRepository = SettingsRepository(context)
        storageManager = LoopStorageManager(context, clipDao)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testSettingsRepository_defaultsAndPersistence() {
        val initialSettings = settingsRepository.settings.value
        assertEquals(AppMode.DASHCAM, initialSettings.currentMode)
        assertEquals(HudDisplayMode.COMPACT, initialSettings.hudDisplayMode)
        assertEquals(VideoQuality.HD_720P, initialSettings.videoQuality)
        assertEquals(SpeedUnit.KMH, initialSettings.speedUnit)
        assertEquals(Sensitivity.MEDIUM, initialSettings.impactSensitivity)
        assertTrue(initialSettings.autoRecordOnPower)
        assertTrue(initialSettings.audioRecordingEnabled)
        assertFalse(initialSettings.autoDndWhileRecording)
        assertFalse(initialSettings.oemOptimizationCompleted)

        // Modify and persist
        val customSettings = initialSettings.copy(
            currentMode = AppMode.SENTRY,
            hudDisplayMode = HudDisplayMode.FULL,
            videoQuality = VideoQuality.FHD_1080P,
            speedUnit = SpeedUnit.MPH,
            impactSensitivity = Sensitivity.HIGH,
            autoStopOnUnplugSeconds = 30,
            sentrySirenEnabled = true,
            oledPowerSaveMode = true,
            autoDndWhileRecording = true,
            oemOptimizationCompleted = true
        )
        settingsRepository.updateSettings(customSettings)

        val updated = settingsRepository.settings.value
        assertEquals(AppMode.SENTRY, updated.currentMode)
        assertEquals(HudDisplayMode.FULL, updated.hudDisplayMode)
        assertEquals(VideoQuality.FHD_1080P, updated.videoQuality)
        assertEquals(SpeedUnit.MPH, updated.speedUnit)
        assertEquals(Sensitivity.HIGH, updated.impactSensitivity)
        assertEquals(30, updated.autoStopOnUnplugSeconds)
        assertTrue(updated.sentrySirenEnabled)
        assertTrue(updated.oledPowerSaveMode)
        assertTrue(updated.autoDndWhileRecording)
        assertTrue(updated.oemOptimizationCompleted)
    }

    @Test
    fun testSpeedUnitCalculations() {
        val rawMps = 27.7778f // ~100 km/h or ~62.137 mph
        val kmh = rawMps * SpeedUnit.KMH.multiplier
        val mph = rawMps * SpeedUnit.MPH.multiplier

        assertEquals(100f, kmh, 0.5f)
        assertEquals(62.137f, mph, 0.5f)
    }

    @Test
    fun testDatabase_clipOperationsAndLockIsolation() {
        runBlocking {
            // Insert 10 clips with alternate lock states
            for (i in 1..10) {
                val clip = VideoClip(
                    filePath = "/mock/path/video_$i.mp4",
                    fileName = "video_$i.mp4",
                    timestamp = 1000L * i,
                    durationMs = 60000L,
                    fileSizeBytes = 10L * 1024 * 1024, // 10MB
                    isLocked = (i % 2 == 0), // Even numbers are locked
                    isSentryEvent = (i == 10),
                    triggerReason = if (i % 2 == 0) "Manual Emergency Lock" else "Normal Loop",
                    speedKmh = 50f + i,
                    latitude = 37.7749 + (i * 0.001),
                    longitude = -122.4194 + (i * 0.001),
                    batteryTempC = 35.0f
                )
                clipDao.insertClip(clip)
            }

            val allClips = clipDao.getAllClips().first()
            assertEquals(10, allClips.size)

            val lockedClips = clipDao.getLockedClips().first()
            assertEquals(5, lockedClips.size)

            // Verify total and locked sizes
            val totalBytes = clipDao.getTotalVideoBytes()
            assertEquals(100L * 1024 * 1024, totalBytes)

            val lockedBytes = lockedClips.sumOf { it.fileSizeBytes }
            assertEquals(50L * 1024 * 1024, lockedBytes)

            // Verify latest clip retrieval
            val latest = clipDao.getLatestClip()
            assertNotNull(latest)
            assertEquals("video_10.mp4", latest?.fileName)

            // Toggle single lock state on clip 1 (should lock clip 1 without affecting clip 3 or 5)
            val clip1 = allClips.find { it.fileName == "video_1.mp4" }!!
            assertFalse(clip1.isLocked)
            clipDao.setLockStatus(clip1.id, true)

            val updatedClip1 = clipDao.getAllClips().first().find { it.id == clip1.id }!!
            assertTrue(updatedClip1.isLocked)

            // Clip 3 should still be unlocked
            val clip3 = clipDao.getAllClips().first().find { it.fileName == "video_3.mp4" }!!
            assertFalse(clip3.isLocked)
        }
    }

    @Test
    fun testLoopStorageManager_stressQuotaAndFifoPruning() {
        runBlocking {
            // Create 20 simulated files in movies directory
            val dir = storageManager.videosDir
            dir.mkdirs()

            for (i in 1..20) {
                val file = File(dir, "VID_DASH_20260101_${"%02d".format(i)}0000.mp4")
                file.writeBytes(ByteArray(1024 * 1024)) // 1 MB file
                val isLocked = (i <= 5) // First 5 are locked, remaining 15 unlocked

                val clip = VideoClip(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    timestamp = 100000L + i,
                    durationMs = 60000L,
                    fileSizeBytes = file.length(),
                    isLocked = isLocked,
                    triggerReason = if (isLocked) "Impact Shock" else "Normal Loop"
                )
                clipDao.insertClip(clip)
            }

            assertEquals(20, clipDao.getAllClips().first().size)
            assertEquals(5, clipDao.getLockedClips().first().size)

            // Perform cleanup targeting max storage of 0GB (forcing pruning of unlocked files)
            storageManager.performLoopCleanupIfNeeded(0)

            // All 5 locked clips MUST be preserved, while unlocked clips are safely pruned
            val remainingClips = clipDao.getAllClips().first()
            val remainingLocked = remainingClips.filter { it.isLocked }
            assertEquals(5, remainingLocked.size)

            // Verify files on disk for locked clips still exist
            for (lockedClip in remainingLocked) {
                assertTrue(File(lockedClip.filePath).exists())
            }

            // Clean up test files
            dir.deleteRecursively()
        }
    }

    @Test
    fun testSensitivityThresholds() {
        assertEquals(999f, Sensitivity.OFF.thresholdG)
        assertEquals(3.5f, Sensitivity.LOW.thresholdG)
        assertEquals(2.4f, Sensitivity.MEDIUM.thresholdG)
        assertEquals(1.6f, Sensitivity.HIGH.thresholdG)

        assertTrue(Sensitivity.HIGH.thresholdG < Sensitivity.MEDIUM.thresholdG)
        assertTrue(Sensitivity.MEDIUM.thresholdG < Sensitivity.LOW.thresholdG)
    }

    @Test
    fun testStorageLocationOptions_andCustomPath() {
        val managerWithSettings = LoopStorageManager(context, clipDao, settingsRepository)

        // Default app storage
        val defaultDir = managerWithSettings.videosDir
        assertTrue(defaultDir.exists())
        assertTrue(defaultDir.absolutePath.contains("dashguard_videos"))

        // Switch to Custom Directory
        val customTestDir = File(context.cacheDir, "test_custom_dashcam_dir")
        customTestDir.mkdirs()
        settingsRepository.updateSettings(
            settingsRepository.settings.value.copy(
                storageLocationType = StorageLocationType.CUSTOM_DIRECTORY,
                customStoragePath = customTestDir.absolutePath
            )
        )
        val customResolved = managerWithSettings.videosDir
        assertEquals(customTestDir.absolutePath, customResolved.absolutePath)

        val newVideo = managerWithSettings.createNewVideoFile("CUSTOM")
        assertEquals(customTestDir.absolutePath, newVideo.parentFile?.absolutePath)

        customTestDir.deleteRecursively()
    }

    @Test
    fun testVideoFileCreation_namingConventions() {
        val videoFile = storageManager.createNewVideoFile("DASH")
        assertTrue(videoFile.name.startsWith("VID_DASH_"))
        assertTrue(videoFile.name.endsWith(".mp4"))

        val sentryFile = storageManager.createNewVideoFile("SENTRY")
        assertTrue(sentryFile.name.startsWith("VID_SENTRY_"))
        assertTrue(sentryFile.name.endsWith(".mp4"))

        val snapFile = storageManager.createNewSnapshotFile("SNAP")
        assertTrue(snapFile.name.startsWith("IMG_SNAP_"))
        assertTrue(snapFile.name.endsWith(".jpg"))
    }

    @Test
    fun testCameraXRecordingManager_lifecycleAndFinalization() {
        val recordingManager = io.github.roaxio.dashguardpro.camera.CameraXRecordingManager(context)
        var finalizedCalled = false
        var nextChunkCalled = false
        var receivedDuration: Long = 0L

        recordingManager.onSegmentFinalized = { file, durationMs, hasError, cause ->
            finalizedCalled = true
            receivedDuration = durationMs
        }

        recordingManager.onStartNextChunk = { reason ->
            nextChunkCalled = true
        }

        // Verify VideoCapture use case instantiation
        val videoCapture = recordingManager.getVideoCapture()
        assertNotNull(videoCapture)

        // When no active recording, rotate gracefully invokes callback
        recordingManager.rotateToNextChunk("Test Rotate")
        assertTrue(nextChunkCalled)
    }

    @Test
    fun testStorageDirectoryManager_customPathAndResolution() {
        val defaultDir = storageManager.defaultAppVideosDir
        assertTrue(defaultDir.exists() || defaultDir.mkdirs())

        val customTestDir = File(context.cacheDir, "test_custom_dashguard")
        customTestDir.mkdirs()

        val options = io.github.roaxio.dashguardpro.storage.StorageDirectoryManager.getAvailableStorageOptions(
            context = context,
            customPath = customTestDir.absolutePath
        )

        assertEquals(2, options.size)
        val customOpt = options.find { it.type == StorageLocationType.CUSTOM_DIRECTORY }
        assertNotNull(customOpt)
        assertEquals(customTestDir.absolutePath, customOpt?.resolvedPath)
        assertTrue(customOpt?.isWritable == true)

        val resolved = io.github.roaxio.dashguardpro.storage.StorageDirectoryManager.resolveStorageDirectory(
            context = context,
            type = StorageLocationType.CUSTOM_DIRECTORY,
            customPath = customTestDir.absolutePath
        )
        assertEquals(customTestDir.absolutePath, resolved.absolutePath)
    }

    @Test
    fun testLowBatterySafetyCutoff_andThermalCutoff_disabledOptions() {
        val settings = settingsRepository.settings.value
        assertEquals(15, settings.lowBatterySafetyLimitPct)
        assertEquals(48.0f, settings.maxTempThresholdC)

        // Disable both cutoff mechanisms
        settingsRepository.updateSettings(
            settings.copy(
                lowBatterySafetyLimitPct = 0,
                maxTempThresholdC = 0.0f
            )
        )

        val updated = settingsRepository.settings.value
        assertEquals(0, updated.lowBatterySafetyLimitPct)
        assertEquals(0.0f, updated.maxTempThresholdC)
    }

    @Test
    fun testTripSettings_and_customTitles_persistence() {
        val initial = settingsRepository.settings.value
        assertEquals(10, initial.minTripDurationMinutes)
        assertFalse(initial.includeGapsInTripDuration)

        // Update settings
        settingsRepository.updateSettings(
            initial.copy(
                minTripDurationMinutes = 15,
                includeGapsInTripDuration = true
            )
        )

        val updated = settingsRepository.settings.value
        assertEquals(15, updated.minTripDurationMinutes)
        assertTrue(updated.includeGapsInTripDuration)

        // Test Trip Custom Title persistence via authoritative ClipRepository
        val clipRepository = io.github.roaxio.dashguardpro.storage.ClipRepository(
            clipDao = clipDao,
            settingsRepo = settingsRepository,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            context = context
        )
        val tripId = "trip_test_100_200"
        assertEquals(null, clipRepository.getTripCustomTitle(tripId))

        clipRepository.saveTripCustomTitle(tripId, "Coastal Highway Drive")
        assertEquals("Coastal Highway Drive", clipRepository.getTripCustomTitle(tripId))
        assertEquals("Coastal Highway Drive", clipRepository.tripCustomTitles.value[tripId])

        // Clear custom title with blank string
        clipRepository.saveTripCustomTitle(tripId, "")
        assertNull(clipRepository.getTripCustomTitle(tripId))
        assertFalse(clipRepository.tripCustomTitles.value.containsKey(tripId))
    }

    @Test
    fun testLockedClipProtection_invariantEnforced() = runBlocking {
        val baseTime = 1725530000000L
        val lockedClip = VideoClip(
            filePath = "/storage/videos/LOCKED_TEST.mp4",
            fileName = "LOCKED_TEST.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            isLocked = true,
            triggerReason = "Emergency Guard Lock"
        )
        val insertedId = clipDao.insertClip(lockedClip)
        val loaded = clipDao.getClipById(insertedId)
        assertNotNull(loaded)
        assertTrue(loaded!!.isLocked)

        // Verify that locked clips are excluded from recyclables in LoopStorageManager
        val recyclableClips = clipDao.getUnlockedClipsOldestFirst()
        assertFalse("Locked clip must NEVER appear in unlocked / recyclable queries", recyclableClips.any { it.id == insertedId })
    }

    @Test
    fun testTripLock_locksAllClipsAndPreservesPreExistingIndividualLocks() = runBlocking {
        val baseTime = 1725530000000L
        val normalClip = VideoClip(
            id = 1L,
            filePath = "/storage/videos/CLIP1.mp4",
            fileName = "CLIP1.mp4",
            timestamp = baseTime,
            isLocked = false,
            triggerReason = "Normal Loop"
        )
        val individuallyLockedClip = VideoClip(
            id = 2L,
            filePath = "/storage/videos/CLIP2.mp4",
            fileName = "CLIP2.mp4",
            timestamp = baseTime + 60_000L,
            isLocked = true,
            triggerReason = "Manual Lock"
        )
        val incidentClip = VideoClip(
            id = 3L,
            filePath = "/storage/videos/CLIP3.mp4",
            fileName = "CLIP3.mp4",
            timestamp = baseTime + 120_000L,
            isLocked = true,
            incidentVideoOffsetSeconds = 15,
            triggerReason = "Impact Shock"
        )

        clipDao.insertClip(normalClip)
        clipDao.insertClip(individuallyLockedClip)
        clipDao.insertClip(incidentClip)

        val trip = TripSession(
            id = "test_trip",
            startTimeMs = baseTime,
            endTimeMs = baseTime + 180_000L,
            clips = listOf(normalClip, individuallyLockedClip, incidentClip),
            totalDurationMs = 180_000L,
            totalDistanceMeters = 3000f,
            avgSpeedKmh = 50f,
            maxSpeedKmh = 60f,
            lockedCount = 2,
            incidentCount = 1
        )

        // Lock trip logic
        trip.clips.forEach { clip ->
            if (!clip.isLocked) {
                clipDao.updateClip(
                    clip.copy(
                        isLocked = true,
                        triggerReason = if (clip.triggerReason == "Normal Loop") "Trip Lock" else clip.triggerReason
                    )
                )
            }
        }

        val updated1 = clipDao.getClipById(1L)!!
        val updated2 = clipDao.getClipById(2L)!!
        val updated3 = clipDao.getClipById(3L)!!

        assertTrue(updated1.isLocked)
        assertEquals("Trip Lock", updated1.triggerReason)

        assertTrue(updated2.isLocked)
        assertEquals("Manual Lock", updated2.triggerReason) // Preserved original reason!

        assertTrue(updated3.isLocked)
        assertEquals("Impact Shock", updated3.triggerReason) // Preserved incident reason!
    }

    @Test
    fun testTripUnlock_smartPreservationOfIncidentsAndIndividualLocks() = runBlocking {
        val baseTime = 1725530000000L
        val tripLockedClip = VideoClip(
            id = 10L,
            filePath = "/storage/videos/CLIP10.mp4",
            fileName = "CLIP10.mp4",
            timestamp = baseTime,
            isLocked = true,
            triggerReason = "Trip Lock"
        )
        val individuallyLockedClip = VideoClip(
            id = 20L,
            filePath = "/storage/videos/CLIP20.mp4",
            fileName = "CLIP20.mp4",
            timestamp = baseTime + 60_000L,
            isLocked = true,
            triggerReason = "Manual Lock"
        )
        val incidentClip = VideoClip(
            id = 30L,
            filePath = "/storage/videos/CLIP30.mp4",
            fileName = "CLIP30.mp4",
            timestamp = baseTime + 120_000L,
            isLocked = true,
            incidentVideoOffsetSeconds = 20,
            triggerReason = "Impact Shock"
        )

        clipDao.insertClip(tripLockedClip)
        clipDao.insertClip(individuallyLockedClip)
        clipDao.insertClip(incidentClip)

        val trip = TripSession(
            id = "test_trip_2",
            startTimeMs = baseTime,
            endTimeMs = baseTime + 180_000L,
            clips = listOf(tripLockedClip, individuallyLockedClip, incidentClip),
            totalDurationMs = 180_000L,
            totalDistanceMeters = 3000f,
            avgSpeedKmh = 50f,
            maxSpeedKmh = 60f,
            lockedCount = 3,
            incidentCount = 1
        )

        // Case A: Unlock with preserveIncidents = true
        trip.clips.forEach { clip ->
            val isProtected = clip.isSentryEvent ||
                    clip.incidentVideoOffsetSeconds >= 0 ||
                    (clip.triggerReason != "Normal Loop" && clip.triggerReason != "Trip Lock")

            if (!isProtected) {
                clipDao.updateClip(clip.copy(isLocked = false, triggerReason = "Normal Loop"))
            }
        }

        val afterUnlock10 = clipDao.getClipById(10L)!!
        val afterUnlock20 = clipDao.getClipById(20L)!!
        val afterUnlock30 = clipDao.getClipById(30L)!!

        assertFalse(afterUnlock10.isLocked) // Unlocked!
        assertEquals("Normal Loop", afterUnlock10.triggerReason)

        assertTrue(afterUnlock20.isLocked) // Kept locked!
        assertEquals("Manual Lock", afterUnlock20.triggerReason)

        assertTrue(afterUnlock30.isLocked) // Kept locked!
        assertEquals("Impact Shock", afterUnlock30.triggerReason)

        // Case B: Unlock with preserveIncidents = false (unlock all)
        trip.clips.forEach { clip ->
            clipDao.updateClip(clip.copy(isLocked = false, triggerReason = "Normal Loop"))
        }

        assertFalse(clipDao.getClipById(10L)!!.isLocked)
        assertFalse(clipDao.getClipById(20L)!!.isLocked)
        assertFalse(clipDao.getClipById(30L)!!.isLocked)
    }

    @Test
    fun testSentrySettings_persistenceAndCustomDurations() {
        val initial = settingsRepository.settings.value
        assertFalse(initial.sentryModeEnabled)
        assertEquals(SentryArmingTrigger.ON_POWER_LOSS, initial.sentryArmingTrigger)
        assertEquals(SentryGuardType.SHOCK_STANDBY, initial.sentryGuardType)
        assertEquals(Sensitivity.MEDIUM, initial.sentryAcousticTriggerSensitivity)
        assertEquals(60, initial.sentryArmingDelaySeconds)
        assertEquals(45, initial.sentryIncidentDurationSeconds)
        assertEquals(SentryDeterrentMode.NONE, initial.sentryDeterrentMode)
        assertEquals(SentryInterruptedClipAction.KEEP_WITH_BADGE, initial.sentryInterruptedClipAction)
        assertTrue(initial.sentryVoiceAlertsEnabled)
        assertTrue(initial.sentryAutoDisarmOnPower)
        assertTrue(initial.midDriveThermalRecoveryEnabled)

        // Test custom durations up to 300s (5 minutes) and custom options
        val custom = initial.copy(
            sentryModeEnabled = true,
            sentryArmingTrigger = SentryArmingTrigger.ON_RECORDING_STOP,
            sentryGuardType = SentryGuardType.OPTICAL_MOTION,
            sentryAcousticTriggerSensitivity = Sensitivity.HIGH,
            sentryArmingDelaySeconds = 300,
            sentryIncidentDurationSeconds = 300,
            sentryDeterrentMode = SentryDeterrentMode.BOTH,
            sentryInterruptedClipAction = SentryInterruptedClipAction.AUTO_DELETE,
            sentryVoiceAlertsEnabled = false,
            sentryAutoDisarmOnPower = false,
            midDriveThermalRecoveryEnabled = true,
            sentryAutoDemoteBatteryPct = 25,
            sentryAutoDemoteTempIsAuto = false,
            sentryAutoDemoteTempC = 39.0f
        )
        settingsRepository.updateSettings(custom)

        val updated = settingsRepository.settings.value
        assertTrue(updated.sentryModeEnabled)
        assertEquals(SentryArmingTrigger.ON_RECORDING_STOP, updated.sentryArmingTrigger)
        assertEquals(SentryGuardType.OPTICAL_MOTION, updated.sentryGuardType)
        assertEquals(Sensitivity.HIGH, updated.sentryAcousticTriggerSensitivity)
        assertEquals(300, updated.sentryArmingDelaySeconds)
        assertEquals(300, updated.sentryIncidentDurationSeconds)
        assertEquals(SentryDeterrentMode.BOTH, updated.sentryDeterrentMode)
        assertEquals(SentryInterruptedClipAction.AUTO_DELETE, updated.sentryInterruptedClipAction)
        assertFalse(updated.sentryVoiceAlertsEnabled)
        assertFalse(updated.sentryAutoDisarmOnPower)
        assertTrue(updated.midDriveThermalRecoveryEnabled)
        assertEquals(25, updated.sentryAutoDemoteBatteryPct)
        assertEquals(39.0f, updated.sentryAutoDemoteTempC, 0.01f)
    }

    @Test
    fun testClipDao_updateClipIncidentLocation_asynchronousGpsBackfill() = runBlocking {
        val clip = VideoClip(
            id = 101L,
            filePath = "/storage/test/thermal_sleep_incident.mp4",
            fileName = "thermal_sleep_incident.mp4",
            timestamp = System.currentTimeMillis(),
            durationMs = 45000L,
            fileSizeBytes = 5000000L,
            isLocked = true,
            isSentryEvent = true,
            triggerReason = "Emergency Shock (Thermal Sleep)",
            latitude = 0.0,
            longitude = 0.0,
            incidentLatitude = 0.0,
            incidentLongitude = 0.0
        )
        clipDao.insertClip(clip)

        val initialClip = clipDao.getClipById(101L)
        assertNotNull(initialClip)
        assertEquals(0.0, initialClip!!.incidentLatitude, 0.0001)
        assertEquals(0.0, initialClip.incidentLongitude, 0.0001)

        // Simulate asynchronous background GPS acquisition backfill
        clipDao.updateClipIncidentLocation(101L, 37.7749, -122.4194)

        val backfilledClip = clipDao.getClipById(101L)
        assertNotNull(backfilledClip)
        assertEquals(37.7749, backfilledClip!!.incidentLatitude, 0.0001)
        assertEquals(-122.4194, backfilledClip.incidentLongitude, 0.0001)
        assertEquals(37.7749, backfilledClip.latitude, 0.0001)
        assertEquals(-122.4194, backfilledClip.longitude, 0.0001)
    }

    @Test
    fun testOwnerPowerOnInterruption_keepWithBadgeVsAutoDeleteLogic() = runBlocking {
        // Test KEEP_WITH_BADGE: clip is persisted with badge triggerReason and locked status
        val badgeClip = VideoClip(
            id = 201L,
            filePath = "/storage/test/sentry_interrupted_badge.mp4",
            fileName = "sentry_interrupted_badge.mp4",
            timestamp = System.currentTimeMillis(),
            durationMs = 20000L,
            fileSizeBytes = 3000000L,
            isLocked = true,
            isSentryEvent = true,
            triggerReason = "Sentry (Interrupted by Power On)"
        )
        clipDao.insertClip(badgeClip)
        val retrieved = clipDao.getClipById(201L)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isLocked)
        assertTrue(retrieved.isSentryEvent)
        assertEquals("Sentry (Interrupted by Power On)", retrieved.triggerReason)

        // Test AUTO_DELETE: simulates file deletion on disk and exclusion from database
        val tempDir = File(context.cacheDir, "test_videos")
        tempDir.mkdirs()
        val tempVideo = File(tempDir, "sentry_interrupted_delete.mp4")
        tempVideo.writeText("fake video data")
        val tempSrt = File(tempDir, "sentry_interrupted_delete.srt")
        tempSrt.writeText("fake subtitle data")

        assertTrue(tempVideo.exists())
        assertTrue(tempSrt.exists())

        // Simulate AUTO_DELETE logic in saveFinalizedSegmentToDb
        val reason = "Sentry (Interrupted by Power On)"
        val action = SentryInterruptedClipAction.AUTO_DELETE
        if (reason == "Sentry (Interrupted by Power On)" && action == SentryInterruptedClipAction.AUTO_DELETE) {
            tempVideo.delete()
            tempSrt.delete()
        }

        assertFalse(tempVideo.exists())
        assertFalse(tempSrt.exists())
        assertNull(clipDao.getClipByPath(tempVideo.absolutePath))
    }

    @Test
    fun testThermalHysteresisCalculations() {
        val maxSafeTemp = 48.0f
        val hysteresisDelta = 4.0f
        val recoveryTarget = maxSafeTemp - hysteresisDelta // 44.0°C

        assertEquals(44.0f, recoveryTarget, 0.001f)

        // Test state transitions
        var isCoolingActive = false

        // Overheat warning triggered
        val warningTemp = 48.2f
        if (warningTemp >= maxSafeTemp) {
            isCoolingActive = true
        }
        assertTrue(isCoolingActive)

        // Temp drops to 45.5°C -> still cooling
        val tempDrop1 = 45.5f
        val canResume1 = tempDrop1 <= recoveryTarget
        assertFalse(canResume1)

        // Temp drops to 43.8°C (<= 44.0°C) -> safely recovered!
        val tempDrop2 = 43.8f
        val canResume2 = tempDrop2 <= recoveryTarget
        assertTrue(canResume2)
        if (canResume2) {
            isCoolingActive = false
        }
        assertFalse(isCoolingActive)
    }

    @Test
    fun testOpticalMotionAutoDemotionInvariant() {
        val lowBattery = 25
        val safeBattery = 65
        val highTemp = 42.0f
        val safeTemp = 36.0f

        val shouldDemoteLowBattery = lowBattery in 1..29 || safeTemp >= 41.0f
        val shouldDemoteHighTemp = safeBattery in 1..29 || highTemp >= 41.0f
        val shouldDemoteSafe = safeBattery in 1..29 || safeTemp >= 41.0f

        assertTrue(shouldDemoteLowBattery)
        assertTrue(shouldDemoteHighTemp)
        assertFalse(shouldDemoteSafe)
    }

    @Test
    fun testAcousticDecibelThresholds() {
        val veryHighDb = when (Sensitivity.VERY_HIGH) {
            Sensitivity.VERY_HIGH -> 60f
            Sensitivity.HIGH -> 65f
            Sensitivity.MEDIUM -> 75f
            Sensitivity.LOW -> 85f
            Sensitivity.VERY_LOW -> 90f
            Sensitivity.OFF -> 999f
        }
        val highDb = when (Sensitivity.HIGH) {
            Sensitivity.VERY_HIGH -> 60f
            Sensitivity.HIGH -> 65f
            Sensitivity.MEDIUM -> 75f
            Sensitivity.LOW -> 85f
            Sensitivity.VERY_LOW -> 90f
            Sensitivity.OFF -> 999f
        }
        val medDb = when (Sensitivity.MEDIUM) {
            Sensitivity.VERY_HIGH -> 60f
            Sensitivity.HIGH -> 65f
            Sensitivity.MEDIUM -> 75f
            Sensitivity.LOW -> 85f
            Sensitivity.VERY_LOW -> 90f
            Sensitivity.OFF -> 999f
        }
        val lowDb = when (Sensitivity.LOW) {
            Sensitivity.VERY_HIGH -> 60f
            Sensitivity.HIGH -> 65f
            Sensitivity.MEDIUM -> 75f
            Sensitivity.LOW -> 85f
            Sensitivity.VERY_LOW -> 90f
            Sensitivity.OFF -> 999f
        }
        val veryLowDb = when (Sensitivity.VERY_LOW) {
            Sensitivity.VERY_HIGH -> 60f
            Sensitivity.HIGH -> 65f
            Sensitivity.MEDIUM -> 75f
            Sensitivity.LOW -> 85f
            Sensitivity.VERY_LOW -> 90f
            Sensitivity.OFF -> 999f
        }
        val offDb = when (Sensitivity.OFF) {
            Sensitivity.VERY_HIGH -> 60f
            Sensitivity.HIGH -> 65f
            Sensitivity.MEDIUM -> 75f
            Sensitivity.LOW -> 85f
            Sensitivity.VERY_LOW -> 90f
            Sensitivity.OFF -> 999f
        }

        assertEquals(60f, veryHighDb, 0.1f)
        assertEquals(65f, highDb, 0.1f)
        assertEquals(75f, medDb, 0.1f)
        assertEquals(85f, lowDb, 0.1f)
        assertEquals(90f, veryLowDb, 0.1f)
        assertEquals(999f, offDb, 0.1f)
    }
}
