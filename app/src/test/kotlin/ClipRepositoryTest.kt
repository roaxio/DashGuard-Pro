package io.github.roaxio.dashguardpro

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.AppDatabase
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.storage.ClipRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var clipDao: ClipDao
    private lateinit var settingsRepo: SettingsRepository

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<DashGuardApplication>()
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clipDao = database.clipDao()
        settingsRepo = SettingsRepository(app)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testClipRepository_insertUpdateAndQuery() = runTest {
        val repo = ClipRepository(clipDao, settingsRepo, backgroundScope)

        val clip = VideoClip(
            id = 0,
            filePath = "/storage/test1.mp4",
            fileName = "test1.mp4",
            timestamp = 1000L,
            durationMs = 60000L,
            fileSizeBytes = 5000L,
            isLocked = false
        )

        repo.insertOrUpdateClip(clip)

        val retrieved = repo.getClipByPath("/storage/test1.mp4")
        assertNotNull(retrieved)
        assertEquals("test1.mp4", retrieved?.fileName)
        assertFalse(retrieved!!.isLocked)

        // Toggle lock
        repo.toggleClipLock(retrieved)
        val lockedClip = repo.getClipById(retrieved.id)
        assertTrue(lockedClip!!.isLocked)

        // Update
        repo.setLockStatus(retrieved.id, false)
        val unlockedClip = repo.getClipById(retrieved.id)
        assertFalse(unlockedClip!!.isLocked)
    }

    @Test
    fun testClipRepository_latestClipLocking() = runTest {
        val repo = ClipRepository(clipDao, settingsRepo, backgroundScope)

        // Empty DB
        assertFalse(repo.lockLatestClip())
        assertFalse(repo.toggleLatestClipLock())

        val clip = VideoClip(
            id = 0,
            filePath = "/storage/test_latest.mp4",
            fileName = "test_latest.mp4",
            timestamp = 2000L,
            durationMs = 60000L,
            fileSizeBytes = 5000L,
            isLocked = false
        )
        repo.insertOrUpdateClip(clip)

        assertTrue(repo.lockLatestClip())
        val latest = repo.getLatestClip()
        assertTrue(latest!!.isLocked)

        // Toggle from locked -> unlocked
        val newLockState = repo.toggleLatestClipLock()
        assertFalse(newLockState)
        assertFalse(repo.getLatestClip()!!.isLocked)
    }

    @Test
    fun testClipRepository_tripLockAndUnlock() = runTest {
        val repo = ClipRepository(clipDao, settingsRepo, backgroundScope)

        val clip1 = VideoClip(id = 1, filePath = "/storage/trip1.mp4", fileName = "trip1.mp4", timestamp = 1000L, isLocked = false)
        val clip2 = VideoClip(id = 2, filePath = "/storage/trip2.mp4", fileName = "trip2.mp4", timestamp = 2000L, isLocked = false)
        clipDao.insertClip(clip1)
        clipDao.insertClip(clip2)

        val trip = TripSession(
            id = "trip_session_1",
            startTimeMs = 1000L,
            endTimeMs = 3000L,
            clips = listOf(clip1, clip2),
            totalDurationMs = 2000L,
            totalDistanceMeters = 500f,
            avgSpeedKmh = 30f,
            maxSpeedKmh = 40f,
            lockedCount = 0,
            incidentCount = 0
        )

        val lockedCount = repo.lockTripClips(trip)
        assertEquals(2, lockedCount)
        assertTrue(repo.getClipById(1)!!.isLocked)
        assertTrue(repo.getClipById(2)!!.isLocked)

        val unlockedCount = repo.unlockTripClips(trip.copy(clips = listOf(repo.getClipById(1)!!, repo.getClipById(2)!!)), preserveIncidents = false)
        assertEquals(2, unlockedCount)
        assertFalse(repo.getClipById(1)!!.isLocked)
        assertFalse(repo.getClipById(2)!!.isLocked)
    }

    @Test
    fun testTripCustomTitles() = runTest {
        val app = ApplicationProvider.getApplicationContext<DashGuardApplication>()
        val repo = ClipRepository(clipDao, settingsRepo, backgroundScope, context = app)

        repo.saveTripCustomTitle("trip_test_1", "Scenic Highway")
        assertEquals("Scenic Highway", repo.getTripCustomTitle("trip_test_1"))
        assertEquals("Scenic Highway", repo.tripCustomTitles.value["trip_test_1"])

        repo.saveTripCustomTitle("trip_test_1", "")
        assertNull(repo.getTripCustomTitle("trip_test_1"))
        assertFalse(repo.tripCustomTitles.value.containsKey("trip_test_1"))
    }

    @Test
    fun testSyncExistingClips_discoversVideos_andPurgesZeroByteFiles() = runTest {
        val repo = ClipRepository(clipDao, settingsRepo, backgroundScope)
        val videosDir = tempFolder.newFolder("sync_videos")

        val validVideo1 = File(videosDir, "VID_DASH_20260920_120000.mp4").apply { writeBytes(ByteArray(5000)) }
        val srt1 = File(videosDir, "VID_DASH_20260920_120000.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:45,000\nSpeed: 60 km/h\n")
        }
        val validSentry = File(videosDir, "VID_SENTRY_20260920_130000.mp4").apply { writeBytes(ByteArray(8000)) }
        val zeroByteCorrupt = File(videosDir, "VID_DASH_20260920_140000.mp4").apply { writeBytes(ByteArray(0)) }

        assertTrue(zeroByteCorrupt.exists())

        repo.syncExistingClips(videosDir)

        // Corrupt 0-byte file must be purged
        assertFalse("0-byte aborted file must be deleted", zeroByteCorrupt.exists())

        // Valid videos must be inserted into Room DB
        val clips = clipDao.getAllClipsList()
        assertEquals(2, clips.size)

        val dashClip = clips.find { it.fileName == "VID_DASH_20260920_120000.mp4" }
        assertNotNull(dashClip)
        assertEquals(5000L, dashClip!!.fileSizeBytes)
        assertEquals(45000L, dashClip.durationMs)
        assertFalse(dashClip.isSentryEvent)

        val sentryClip = clips.find { it.fileName == "VID_SENTRY_20260920_130000.mp4" }
        assertNotNull(sentryClip)
        assertEquals(8000L, sentryClip!!.fileSizeBytes)
        assertTrue(sentryClip.isSentryEvent)
    }

    @Test
    fun testSyncExistingClips_strictlyIgnoresAndPreservesForeignNonAppFiles() = runTest {
        val repo = ClipRepository(clipDao, settingsRepo, backgroundScope)
        val videosDir = tempFolder.newFolder("foreign_sync_videos")

        // Foreign files (non-app relevant)
        val foreignVideo = File(videosDir, "family_vacation.mp4").apply { writeBytes(ByteArray(12000)) }
        val foreignEmpty = File(videosDir, "random_download.mp4").apply { writeBytes(ByteArray(0)) }
        val nonVideo = File(videosDir, "notes.txt").apply { writeText("Hello") }

        // Valid DashGuard recording
        val dashGuardVideo = File(videosDir, "VID_LOOP_20260920_150000.mp4").apply { writeBytes(ByteArray(6000)) }

        repo.syncExistingClips(videosDir)

        // Foreign files must NOT be deleted, even if 0 bytes!
        assertTrue("Foreign 0-byte file must NOT be deleted", foreignEmpty.exists())
        assertTrue("Foreign video must NOT be deleted", foreignVideo.exists())
        assertTrue("Non-video file must NOT be deleted", nonVideo.exists())

        // Only the valid DashGuard recording should be ingested into Room DB
        val clips = clipDao.getAllClipsList()
        assertEquals(1, clips.size)
        assertEquals("VID_LOOP_20260920_150000.mp4", clips[0].fileName)
    }

    @Test
    fun testUniqueFilePathConstraint_replacesDuplicateInserts() = runTest {
        val clip1 = VideoClip(
            id = 0,
            filePath = "/storage/test/VID_LOOP_20261001_100000.mp4",
            fileName = "VID_LOOP_20261001_100000.mp4",
            fileSizeBytes = 1000L
        )
        val clip2 = VideoClip(
            id = 0,
            filePath = "/storage/test/VID_LOOP_20261001_100000.mp4",
            fileName = "VID_LOOP_20261001_100000.mp4",
            fileSizeBytes = 2000L
        )

        clipDao.insertClip(clip1)
        clipDao.insertClip(clip2)

        val clips = clipDao.getAllClipsList()
        // Unique constraint on filePath must prevent duplicate rows
        assertEquals(1, clips.size)
        assertEquals(2000L, clips[0].fileSizeBytes)
    }

    @Test
    fun testSyncExistingClips_concurrentCalls_noDuplicatesCreated() = runTest {
        val repo = ClipRepository(clipDao, settingsRepo, backgroundScope)
        val videosDir = tempFolder.newFolder("concurrent_sync_videos")
        File(videosDir, "VID_LOOP_20261001_100000.mp4").apply { writeBytes(ByteArray(5000)) }
        File(videosDir, "VID_DASH_20261001_100100.mp4").apply { writeBytes(ByteArray(6000)) }

        // Trigger parallel syncs
        val job1 = async { repo.syncExistingClips(videosDir) }
        val job2 = async { repo.syncExistingClips(videosDir) }
        job1.await()
        job2.await()

        val clips = clipDao.getAllClipsList()
        assertEquals(2, clips.size)
    }
}
