package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageDirectoryManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testGetDynamicQuotaOptions() {
        val options0 = StorageDirectoryManager.getDynamicQuotaOptions(0)
        assertTrue(options0.isEmpty())

        val optionsNeg = StorageDirectoryManager.getDynamicQuotaOptions(-1)
        assertTrue(optionsNeg.isEmpty())

        val options10 = StorageDirectoryManager.getDynamicQuotaOptions(10)
        assertTrue(options10.contains(10))
        assertTrue(options10.contains(5))
        assertFalse(options10.any { it > 10 })

        val options64 = StorageDirectoryManager.getDynamicQuotaOptions(64)
        assertTrue(options64.contains(64))
        assertTrue(options64.contains(30))
        assertFalse(options64.any { it > 64 })
    }

    @Test
    fun testCreateAndListSubdirectories() {
        val root = tempFolder.root
        val res = StorageDirectoryManager.createSubdirectory(root, "DashcamVideos")
        assertTrue(res.isSuccess)
        val created = res.getOrNull()
        assertTrue(created != null && created.exists())

        val subdirs = StorageDirectoryManager.listSubdirectories(root)
        assertEquals(1, subdirs.size)
        assertEquals("DashcamVideos", subdirs[0].name)
    }

    @Test
    fun testResolveStorageDirectory_customPath() {
        val root = tempFolder.root
        val customFolder = File(root, "MyCustomDashFolder")
        
        // Non-existent before resolve
        assertFalse(customFolder.exists())

        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val resolved = StorageDirectoryManager.resolveStorageDirectory(
            context,
            io.github.roaxio.dashguardpro.data.settings.StorageLocationType.CUSTOM_DIRECTORY,
            customFolder.absolutePath
        )

        assertEquals(customFolder.absolutePath, resolved.absolutePath)
        assertTrue(resolved.exists())
    }

    @Test
    fun testResolveStorageDirectory_defaultFallbackWhenCustomNull() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val resolved = StorageDirectoryManager.resolveStorageDirectory(
            context,
            io.github.roaxio.dashguardpro.data.settings.StorageLocationType.CUSTOM_DIRECTORY,
            null
        )

        val defaultDir = StorageDirectoryManager.getDefaultAppStorageDirectory(context)
        assertEquals(defaultDir.absolutePath, resolved.absolutePath)
    }

    @Test
    fun testMaxAssignableStorageGb_withAppUsedBytesInvariance() {
        val root = tempFolder.root
        // Verify that passing appUsedBytes increases total reclaimable pool
        val baseMaxGb = StorageDirectoryManager.getMaxAssignableStorageGb(root, 0L)
        val with2GbUsed = StorageDirectoryManager.getMaxAssignableStorageGb(root, 2L * 1024L * 1024L * 1024L)
        assertTrue(with2GbUsed >= baseMaxGb)
    }

    @Test
    fun testGetStorageMetrics_exactRawValues() {
        val root = tempFolder.root
        val appUsed = 1_500_000_000L
        val metrics = StorageDirectoryManager.getStorageMetrics(root, appUsed)
        assertTrue(metrics.physicalFreeBytes >= 0L)
        assertEquals(appUsed, metrics.appUsedBytes)
        assertEquals(metrics.physicalFreeBytes + appUsed, metrics.totalPoolBytes)
        val expectedBuffer = StorageDirectoryManager.getOsSafetyBufferBytes(root)
        assertEquals(expectedBuffer, metrics.osBufferBytes)
        assertEquals(
            (metrics.totalPoolBytes - expectedBuffer).coerceAtLeast(0L),
            metrics.usableSafeBytes
        )
    }

    @Test
    fun testDynamicOsSafetyBuffer_clampingLogic() {
        val minBuffer = StorageDirectoryManager.MIN_OS_SAFETY_BUFFER_BYTES // 500 MB
        val maxBuffer = StorageDirectoryManager.MAX_OS_SAFETY_BUFFER_BYTES // 5 GB

        // 1. Zero or negative volume -> clamps to 500 MB
        assertEquals(minBuffer, StorageDirectoryManager.calculateOsSafetyBufferBytes(0L))
        assertEquals(minBuffer, StorageDirectoryManager.calculateOsSafetyBufferBytes(-100L))

        // 2. Small volume: 16 GB -> 2% is 320 MB -> clamps up to 500 MB minimum
        val sixteenGb = 16L * 1024L * 1024L * 1024L
        assertEquals(minBuffer, StorageDirectoryManager.calculateOsSafetyBufferBytes(sixteenGb))

        // 3. Medium volume: 100 GB -> 2% is 2 GB -> dynamic scaling
        val hundredGb = 100L * 1024L * 1024L * 1024L
        val expectedTwoGb = (hundredGb.toDouble() * 0.02).toLong()
        assertEquals(expectedTwoGb, StorageDirectoryManager.calculateOsSafetyBufferBytes(hundredGb))

        // 4. Large volume: 512 GB -> 2% is 10.24 GB -> clamps down to 5 GB maximum
        val fiveTwelveGb = 512L * 1024L * 1024L * 1024L
        assertEquals(maxBuffer, StorageDirectoryManager.calculateOsSafetyBufferBytes(fiveTwelveGb))
    }
}
