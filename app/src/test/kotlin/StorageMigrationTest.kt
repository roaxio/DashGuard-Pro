package io.github.roaxio.dashguardpro

import androidx.room.Room
import io.github.roaxio.dashguardpro.data.AppDatabase
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.storage.LoopStorageManager
import io.github.roaxio.dashguardpro.storage.StorageMigrationManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var clipDao: ClipDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clipDao = database.clipDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testGetClipsInDirectory_filtersCorrectly() {
        val sourceDir = tempFolder.newFolder("SourceVideos")
        val otherDir = tempFolder.newFolder("OtherVideos")

        val clip1 = VideoClip(id = 1, filePath = File(sourceDir, "VID_1.mp4").absolutePath, fileName = "VID_1.mp4")
        val clip2 = VideoClip(id = 2, filePath = File(otherDir, "VID_2.mp4").absolutePath, fileName = "VID_2.mp4")
        val clip3 = VideoClip(id = 3, filePath = File(sourceDir, "VID_3.mp4").absolutePath, fileName = "VID_3.mp4")

        val inSource = StorageMigrationManager.getClipsInDirectory(listOf(clip1, clip2, clip3), sourceDir)
        assertEquals(2, inSource.size)
        assertTrue(inSource.contains(clip1))
        assertTrue(inSource.contains(clip3))
        assertFalse(inSource.contains(clip2))
    }

    @Test
    fun testCalculateTotalMigrationBytes_includesSrtAndFusedFiles() = runBlocking {
        val sourceDir = tempFolder.newFolder("CalcDir")
        val videoFile = File(sourceDir, "VID_LOOP_1.mp4").apply { writeBytes(ByteArray(1000)) }
        val srtFile = File(sourceDir, "VID_LOOP_1.srt").apply { writeBytes(ByteArray(200)) }
        val fusedFile = File(sourceDir, "VID_LOOP_1-telemetry-fused.mp4").apply { writeBytes(ByteArray(1500)) }

        val clip = VideoClip(
            id = 1,
            filePath = videoFile.absolutePath,
            fileName = videoFile.name,
            fileSizeBytes = 1000L
        )

        val totalBytes = StorageMigrationManager.calculateTotalMigrationBytes(listOf(clip))
        assertEquals(1000L + 200L + 1500L, totalBytes)
    }

    @Test
    fun testMigrateClips_movesFilesAndUpdatesDatabase() = runBlocking {
        val sourceDir = tempFolder.newFolder("SourceFolder")
        val targetDir = tempFolder.newFolder("TargetFolder")

        val vid1 = File(sourceDir, "VID_20260830_001.mp4").apply { writeText("Video Data 1") }
        val srt1 = File(sourceDir, "VID_20260830_001.srt").apply { writeText("Subtitle Data 1") }

        val vid2 = File(sourceDir, "VID_20260830_002.mp4").apply { writeText("Video Data 2") }

        val clip1 = VideoClip(id = 1, filePath = vid1.absolutePath, fileName = vid1.name, fileSizeBytes = vid1.length())
        val clip2 = VideoClip(id = 2, filePath = vid2.absolutePath, fileName = vid2.name, fileSizeBytes = vid2.length())

        clipDao.insertClip(clip1)
        clipDao.insertClip(clip2)

        var progressUpdates = 0
        val result = StorageMigrationManager.migrateClips(
            clipsToMigrate = listOf(clip1, clip2),
            targetDir = targetDir,
            clipDao = clipDao,
            onProgress = { progressUpdates++ }
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertTrue(progressUpdates > 0)

        // 1. Verify old files are gone from source
        assertFalse(vid1.exists())
        assertFalse(srt1.exists())
        assertFalse(vid2.exists())

        // 2. Verify files exist in destination with matching content
        val destVid1 = File(targetDir, "VID_20260830_001.mp4")
        val destSrt1 = File(targetDir, "VID_20260830_001.srt")
        val destVid2 = File(targetDir, "VID_20260830_002.mp4")

        assertTrue(destVid1.exists())
        assertEquals("Video Data 1", destVid1.readText())

        assertTrue(destSrt1.exists())
        assertEquals("Subtitle Data 1", destSrt1.readText())

        assertTrue(destVid2.exists())
        assertEquals("Video Data 2", destVid2.readText())

        // 3. Verify Room DB records updated to new target paths
        val updated1 = clipDao.getClipById(1)
        val updated2 = clipDao.getClipById(2)

        assertEquals(destVid1.absolutePath, updated1?.filePath)
        assertEquals(destVid2.absolutePath, updated2?.filePath)
    }

    @Test
    fun testGetDistinctStorageDirectories_andOutsideQuery() {
        val dirA = tempFolder.newFolder("DirA")
        val dirB = tempFolder.newFolder("DirB")
        val dirC = tempFolder.newFolder("DirC")

        val clipA = VideoClip(id = 1, filePath = File(dirA, "A.mp4").absolutePath, fileName = "A.mp4")
        val clipB1 = VideoClip(id = 2, filePath = File(dirB, "B1.mp4").absolutePath, fileName = "B1.mp4")
        val clipB2 = VideoClip(id = 3, filePath = File(dirB, "B2.mp4").absolutePath, fileName = "B2.mp4")
        val clipC = VideoClip(id = 4, filePath = File(dirC, "C.mp4").absolutePath, fileName = "C.mp4")

        val all = listOf(clipA, clipB1, clipB2, clipC)
        val distinct = StorageMigrationManager.getDistinctStorageDirectories(all)
        assertEquals(3, distinct.size)

        val outsideB = StorageMigrationManager.getClipsOutsideDirectory(all, dirB)
        assertEquals(2, outsideB.size)
        assertTrue(outsideB.contains(clipA))
        assertTrue(outsideB.contains(clipC))
        assertFalse(outsideB.contains(clipB1))
        assertFalse(outsideB.contains(clipB2))
    }

    @Test
    fun testConsolidateClipsFromMultipleDirectories() = runBlocking {
        val dir1 = tempFolder.newFolder("Dir1")
        val dir2 = tempFolder.newFolder("Dir2")
        val consolidatedTarget = tempFolder.newFolder("ConsolidatedTarget")

        val f1 = File(dir1, "VID_DIR1.mp4").apply { writeText("File 1") }
        val f2 = File(dir2, "VID_DIR2.mp4").apply { writeText("File 2") }

        val clip1 = VideoClip(id = 10, filePath = f1.absolutePath, fileName = f1.name, fileSizeBytes = f1.length())
        val clip2 = VideoClip(id = 20, filePath = f2.absolutePath, fileName = f2.name, fileSizeBytes = f2.length())

        clipDao.insertClip(clip1)
        clipDao.insertClip(clip2)

        val res = StorageMigrationManager.migrateClips(
            clipsToMigrate = listOf(clip1, clip2),
            targetDir = consolidatedTarget,
            clipDao = clipDao,
            onProgress = {}
        )

        assertTrue(res.isSuccess)
        assertEquals(2, res.getOrNull())

        assertFalse(f1.exists())
        assertFalse(f2.exists())

        val dest1 = File(consolidatedTarget, "VID_DIR1.mp4")
        val dest2 = File(consolidatedTarget, "VID_DIR2.mp4")

        assertTrue(dest1.exists())
        assertTrue(dest2.exists())

        assertEquals(dest1.absolutePath, clipDao.getClipById(10)?.filePath)
        assertEquals(dest2.absolutePath, clipDao.getClipById(20)?.filePath)
    }

    @Test
    fun testReturningToPreviousStorageLocation_skipsExistingFilesCleanly() = runBlocking {
        val originalTargetDir = tempFolder.newFolder("OriginalTargetDir")
        val currentActiveDir = tempFolder.newFolder("CurrentActiveDir")

        // 1. File that was already recorded and left in OriginalTargetDir
        val alreadyInTarget = File(originalTargetDir, "VID_OLD.mp4").apply { writeText("Old file already at target") }
        val clipOld = VideoClip(id = 100, filePath = alreadyInTarget.absolutePath, fileName = alreadyInTarget.name, fileSizeBytes = alreadyInTarget.length())

        // 2. File in current active dir that needs to move to OriginalTargetDir
        val needMove = File(currentActiveDir, "VID_NEW.mp4").apply { writeText("New file from active dir") }
        val clipNew = VideoClip(id = 101, filePath = needMove.absolutePath, fileName = needMove.name, fileSizeBytes = needMove.length())

        clipDao.insertClip(clipOld)
        clipDao.insertClip(clipNew)

        // Verify getClipsNeedingMigration only identifies clipNew
        val needingMigration = StorageMigrationManager.getClipsNeedingMigration(listOf(clipOld, clipNew), originalTargetDir)
        assertEquals(1, needingMigration.size)
        assertEquals(101L, needingMigration[0].id)

        // Verify calculateTotalMigrationBytes with targetDir ignores already-present file bytes
        val bytesNeeded = StorageMigrationManager.calculateTotalMigrationBytes(listOf(clipOld, clipNew), originalTargetDir)
        assertEquals(needMove.length(), bytesNeeded)

        // Execute migration on both clips to originalTargetDir
        val res = StorageMigrationManager.migrateClips(
            clipsToMigrate = listOf(clipOld, clipNew),
            targetDir = originalTargetDir,
            clipDao = clipDao,
            onProgress = {}
        )

        assertTrue(res.isSuccess)
        assertEquals(2, res.getOrNull())

        // Old file remains intact at originalTargetDir without corruption
        assertTrue(alreadyInTarget.exists())
        assertEquals("Old file already at target", alreadyInTarget.readText())

        // New file moved into originalTargetDir
        assertFalse(needMove.exists())
        val movedNew = File(originalTargetDir, "VID_NEW.mp4")
        assertTrue(movedNew.exists())
        assertEquals("New file from active dir", movedNew.readText())
    }

    @Test
    fun testExecuteMigration_updatesStateFlowAndCallbacks() = runBlocking {
        val sourceDir = tempFolder.newFolder("ExecSource")
        val targetDir = tempFolder.newFolder("ExecTarget")

        val file1 = File(sourceDir, "VID_EXEC.mp4").apply { writeText("Sample Content") }
        val clip = VideoClip(id = 201, filePath = file1.absolutePath, fileName = file1.name, fileSizeBytes = file1.length())
        clipDao.insertClip(clip)

        var completedSuccess = false
        var errorMsg: String? = null

        val res = StorageMigrationManager.executeMigration(
            clipsToMigrate = listOf(clip),
            targetDir = targetDir,
            clipDao = clipDao,
            onComplete = { success, err ->
                completedSuccess = success
                errorMsg = err
            }
        )

        assertTrue(res.isSuccess)
        assertTrue(completedSuccess)
        org.junit.Assert.assertNull(errorMsg)
        org.junit.Assert.assertNotNull(StorageMigrationManager.migrationState.value)
        assertEquals(1, StorageMigrationManager.migrationState.value?.currentFileIndex)

        StorageMigrationManager.dismissMigrationProgress()
        org.junit.Assert.assertNull(StorageMigrationManager.migrationState.value)
    }

    @Test
    fun testCalculateClipTotalBytes_andStorageStatsParity() = runBlocking {
        val sourceDir = tempFolder.newFolder("ParityDir")
        val unlockedVid = File(sourceDir, "VID_UNLOCKED.mp4").apply { writeBytes(ByteArray(10000)) }
        val unlockedSrt = File(sourceDir, "VID_UNLOCKED.srt").apply { writeBytes(ByteArray(500)) }

        val lockedVid = File(sourceDir, "VID_LOCKED.mp4").apply { writeBytes(ByteArray(20000)) }
        val lockedSrt = File(sourceDir, "VID_LOCKED.srt").apply { writeBytes(ByteArray(800)) }
        val lockedFused = File(sourceDir, "VID_LOCKED-telemetry-fused.mp4").apply { writeBytes(ByteArray(25000)) }

        val clip1 = VideoClip(id = 301, filePath = unlockedVid.absolutePath, fileName = unlockedVid.name, fileSizeBytes = 10000L, isLocked = false)
        val clip2 = VideoClip(id = 302, filePath = lockedVid.absolutePath, fileName = lockedVid.name, fileSizeBytes = 20000L, isLocked = true)

        clipDao.insertClip(clip1)
        clipDao.insertClip(clip2)

        val clip1Bytes = StorageMigrationManager.calculateClipTotalBytes(clip1)
        assertEquals(10000L + 500L, clip1Bytes)

        val clip2Bytes = StorageMigrationManager.calculateClipTotalBytes(clip2)
        assertEquals(20000L + 800L + 25000L, clip2Bytes)

        val totalMigrationBytes = StorageMigrationManager.calculateTotalMigrationBytes(listOf(clip1, clip2))
        assertEquals(clip1Bytes + clip2Bytes, totalMigrationBytes)

        val context = RuntimeEnvironment.getApplication()
        val settingsRepo = SettingsRepository(context)
        val storageManager = LoopStorageManager(context, clipDao, settingsRepo)
        settingsRepo.updateSettings(settingsRepo.settings.value.copy(
            storageLocationType = StorageLocationType.CUSTOM_DIRECTORY,
            customStoragePath = sourceDir.absolutePath
        ))

        val stats = storageManager.getStorageStats(10)
        assertEquals(clip2Bytes, stats.lockedVideoBytes)
        assertEquals(clip1Bytes, stats.loopBufferBytes)
        assertEquals(totalMigrationBytes, stats.appVideoBytes)
        assertEquals(stats.lockedVideoBytes + stats.loopBufferBytes, stats.appVideoBytes)
    }

    @Test
    fun testRegisteredFusedClip_notDoubleCountedOrOrphanedDuringMigration() = runBlocking {
        val sourceDir = tempFolder.newFolder("FusedSourceDir")
        val targetDir = tempFolder.newFolder("FusedTargetDir")

        val rawVid = File(sourceDir, "VID_DASH_20260923_170642.mp4").apply { writeBytes(ByteArray(10000)) }
        val rawSrt = File(sourceDir, "VID_DASH_20260923_170642.srt").apply { writeBytes(ByteArray(500)) }
        val fusedVid = File(sourceDir, "VID_DASH_20260923_170642-telemetry-fused.mp4").apply { writeBytes(ByteArray(15000)) }

        val rawClip = VideoClip(
            id = 401,
            filePath = rawVid.absolutePath,
            fileName = rawVid.name,
            fileSizeBytes = 10000L,
            isLocked = false,
            triggerReason = "Normal Loop"
        )
        val fusedClip = VideoClip(
            id = 402,
            filePath = fusedVid.absolutePath,
            fileName = fusedVid.name,
            fileSizeBytes = 15000L,
            isLocked = true,
            triggerReason = "Telemetry Fused"
        )

        clipDao.insertClip(rawClip)
        clipDao.insertClip(fusedClip)

        val bothClips = listOf(rawClip, fusedClip)

        // 1. Verify calculateTotalMigrationBytes does NOT double-count fusedVid (10000 + 500 + 15000 = 25500)
        val totalMigrationBytes = StorageMigrationManager.calculateTotalMigrationBytes(bothClips)
        assertEquals(10000L + 500L + 15000L, totalMigrationBytes)

        // 2. Verify getStorageStats attributes unlocked rawClip + srt (10500) to loopBuffer and locked fusedClip (15000) to lockedVideoBytes
        val context = RuntimeEnvironment.getApplication()
        val settingsRepo = SettingsRepository(context)
        val storageManager = LoopStorageManager(context, clipDao, settingsRepo)
        settingsRepo.updateSettings(settingsRepo.settings.value.copy(
            storageLocationType = StorageLocationType.CUSTOM_DIRECTORY,
            customStoragePath = sourceDir.absolutePath
        ))

        val stats = storageManager.getStorageStats(10)
        assertEquals(10500L, stats.loopBufferBytes)
        assertEquals(15000L, stats.lockedVideoBytes)
        assertEquals(25500L, stats.appVideoBytes)

        // 3. Migrate both clips and verify BOTH DB records are updated to targetDir (fusedClip is NOT orphaned)
        val res = StorageMigrationManager.migrateClips(
            clipsToMigrate = bothClips,
            targetDir = targetDir,
            clipDao = clipDao,
            onProgress = {}
        )
        assertTrue(res.isSuccess)
        assertEquals(2, res.getOrNull())

        val updatedRaw = clipDao.getClipById(401)
        val updatedFused = clipDao.getClipById(402)
        assertEquals(File(targetDir, rawVid.name).absolutePath, updatedRaw?.filePath)
        assertEquals(File(targetDir, fusedVid.name).absolutePath, updatedFused?.filePath)
        assertTrue(File(targetDir, rawVid.name).exists())
        assertTrue(File(targetDir, rawSrt.name).exists())
        assertTrue(File(targetDir, fusedVid.name).exists())
    }
}

