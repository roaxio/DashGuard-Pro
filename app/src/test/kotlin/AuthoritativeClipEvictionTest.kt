package io.github.roaxio.dashguardpro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.AppDatabase
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.storage.LoopStorageManager
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.storage.StorageMigrationManager
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
class AuthoritativeClipEvictionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var clipDao: ClipDao
    private lateinit var storageManager: LoopStorageManager
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clipDao = database.clipDao()
        storageManager = LoopStorageManager(context, clipDao)
        testDir = File(context.filesDir, "test_eviction_storage").apply { mkdirs() }
    }

    @After
    fun teardown() {
        testDir.deleteRecursively()
        database.close()
    }

    @Test
    fun testDeleteClipAndSidecars_unlockedClip_deletesAllSidecarsAndDbRecord() = runBlocking {
        val videoFile = File(testDir, "VID_TEST_UNLOCKED.mp4").apply { writeBytes(ByteArray(2048)) }
        val srtFile = File(testDir, "VID_TEST_UNLOCKED.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\nSpeed: 50 km/h\n") }
        val fusedFile = File(testDir, "VID_TEST_UNLOCKED-telemetry-fused.mp4").apply { writeBytes(ByteArray(4096)) }

        val clip = VideoClip(
            id = 101L,
            filePath = videoFile.absolutePath,
            fileName = videoFile.name,
            timestamp = System.currentTimeMillis(),
            durationMs = 60_000L,
            fileSizeBytes = videoFile.length(),
            isLocked = false,
            triggerReason = "Normal Loop"
        )
        clipDao.insertClip(clip)

        // Verify pre-conditions
        assertNotNull(clipDao.getClipById(101L))
        assertTrue(videoFile.exists())
        assertTrue(srtFile.exists())
        assertTrue(fusedFile.exists())

        // Execute canonical sidecar deletion
        val deleted = storageManager.deleteClipAndSidecars(clip, forceDeleteLocked = false)
        assertTrue("Unlocked clip must be successfully deleted", deleted)

        // Verify post-conditions: all physical files and DB record purged
        assertFalse("Primary video file must be deleted", videoFile.exists())
        assertFalse("Companion subtitle sidecar must be deleted", srtFile.exists())
        assertFalse("Fused telemetry sidecar must be deleted", fusedFile.exists())
        assertNull("Database record must be purged", clipDao.getClipById(101L))
    }

    @Test
    fun testDeleteClipAndSidecars_lockedClip_protectedUnlessForced() = runBlocking {
        val videoFile = File(testDir, "VID_TEST_LOCKED.mp4").apply { writeBytes(ByteArray(2048)) }
        val srtFile = File(testDir, "VID_TEST_LOCKED.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\nSpeed: 65 km/h\n") }
        val fusedFile = File(testDir, "VID_TEST_LOCKED-telemetry-fused.mp4").apply { writeBytes(ByteArray(4096)) }

        val clip = VideoClip(
            id = 202L,
            filePath = videoFile.absolutePath,
            fileName = videoFile.name,
            timestamp = System.currentTimeMillis(),
            durationMs = 60_000L,
            fileSizeBytes = videoFile.length(),
            isLocked = true,
            triggerReason = "Impact Shock (3.5 G)"
        )
        clipDao.insertClip(clip)

        // Attempt deletion without force flag
        val normalDeleteResult = storageManager.deleteClipAndSidecars(clip, forceDeleteLocked = false)
        assertFalse("Locked clip must be protected from unforced deletion", normalDeleteResult)

        // Assert files and DB record still intact
        assertTrue(videoFile.exists())
        assertTrue(srtFile.exists())
        assertTrue(fusedFile.exists())
        assertNotNull(clipDao.getClipById(202L))

        // Force delete (e.g. from user trip purge action)
        val forceDeleteResult = storageManager.deleteClipAndSidecars(clip, forceDeleteLocked = true)
        assertTrue("Locked clip must be deleted when forceDeleteLocked is true", forceDeleteResult)

        // Assert files and DB record purged
        assertFalse(videoFile.exists())
        assertFalse(srtFile.exists())
        assertFalse(fusedFile.exists())
        assertNull(clipDao.getClipById(202L))
    }

    @Test
    fun testStorageDirectoryManager_authoritativeCapacityQueries() {
        val availableBytes = StorageDirectoryManager.getAvailableBytes(testDir)
        val totalBytes = StorageDirectoryManager.getTotalBytes(testDir)

        assertTrue("Available bytes should be non-negative", availableBytes >= 0L)
        assertTrue("Total bytes should be non-negative", totalBytes >= 0L)

        val metrics = StorageDirectoryManager.getStorageMetrics(testDir, appUsedBytes = 100L * 1024L * 1024L)
        assertEquals(100L * 1024L * 1024L, metrics.appUsedBytes)
        assertEquals(metrics.physicalFreeBytes + metrics.appUsedBytes, metrics.totalPoolBytes)

        // Migration check delegates to StorageDirectoryManager
        val canAccommodate = StorageMigrationManager.canAccommodateMigration(testDir, requiredBytes = 50L * 1024L * 1024L)
        assertTrue("Migration accommodation check must succeed in test sandbox", canAccommodate)
    }

    @Test
    fun testPerformLoopCleanup_strictlyScopesEvictionToActiveDirectory_preservesHistoricalClips(): Unit = runBlocking {
        val historicalDir = File(context.filesDir, "historical_storage").apply { mkdirs() }
        val activeDir = storageManager.videosDir.apply { mkdirs() }

        // 1. Create an older unlocked clip residing in historical directory
        val historicalVideo = File(historicalDir, "VID_HISTORICAL_OLD.mp4").apply { writeBytes(ByteArray(2048)) }
        val historicalSrt = File(historicalDir, "VID_HISTORICAL_OLD.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\nOld clip") }
        val historicalClip = VideoClip(
            id = 501L,
            filePath = historicalVideo.absolutePath,
            fileName = historicalVideo.name,
            timestamp = 100_000L, // Older timestamp
            durationMs = 60_000L,
            fileSizeBytes = historicalVideo.length(),
            isLocked = false,
            triggerReason = "Normal Loop"
        )
        clipDao.insertClip(historicalClip)

        // 2. Create a newer unlocked clip residing in active directory with sparse size > 1GB to trigger quota cleanup
        val activeVideo = File(activeDir, "VID_ACTIVE_NEW.mp4")
        java.io.RandomAccessFile(activeVideo, "rw").use {
            it.setLength(1024L * 1024L * 1024L + 1024L * 1024L) // 1025 MB sparse file
        }
        val activeSrt = File(activeDir, "VID_ACTIVE_NEW.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\nActive clip") }
        val activeClip = VideoClip(
            id = 502L,
            filePath = activeVideo.absolutePath,
            fileName = activeVideo.name,
            timestamp = 200_000L, // Newer timestamp
            durationMs = 60_000L,
            fileSizeBytes = activeVideo.length(),
            isLocked = false,
            triggerReason = "Normal Loop"
        )
        clipDao.insertClip(activeClip)

        // Verify initial state: both clips exist in DB and filesystem
        assertNotNull(clipDao.getClipById(501L))
        assertNotNull(clipDao.getClipById(502L))
        assertTrue(historicalVideo.exists())
        assertTrue(activeVideo.exists())

        // 3. Trigger loop cleanup with 1GB quota (active directory is 1025 MB, exceeding quota)
        val cleanupResult = storageManager.performLoopCleanupIfNeeded(1)
        assertTrue("Cleanup should execute successfully", cleanupResult)

        // 4. Verify invariant:
        // Even though historicalClip is older (timestamp 100_000 vs 200_000), it resides in historicalDir
        // and MUST BE PRESERVED (both DB record and physical file)
        assertNotNull("Historical clip in old directory must NOT be deleted from DB", clipDao.getClipById(501L))
        assertTrue("Historical video file must remain intact on disk", historicalVideo.exists())
        assertTrue("Historical srt file must remain intact on disk", historicalSrt.exists())

        // Active clip in active directory SHOULD be evicted to satisfy the active storage quota
        assertNull("Active clip in active directory should be evicted by loop cleanup", clipDao.getClipById(502L))
        assertFalse("Active video file should be removed from disk", activeVideo.exists())
        assertFalse("Active srt file should be removed from disk", activeSrt.exists())

        // Teardown
        historicalDir.deleteRecursively()
        activeDir.deleteRecursively()
        Unit
    }
}
