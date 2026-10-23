package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.data.settings.VideoAspectRatio
import io.github.roaxio.dashguardpro.data.settings.VideoQuality
import io.github.roaxio.dashguardpro.camera.CameraHardwareSpecManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraHardwareSpecTest {

    @Test
    fun testGetHardwareCameraProfile_rearCamera() {
        val context = RuntimeEnvironment.getApplication()
        val profile = CameraHardwareSpecManager.getHardwareCameraProfile(context, isFrontCamera = false)

        assertNotNull(profile)
        assertTrue(profile.sensorMegapixels > 0f)
        assertTrue(profile.supportedResolutions.isNotEmpty())
        assertTrue(profile.supportedAspectRatios.isNotEmpty())

        // Verify standard resolutions exist
        assertTrue(profile.supportedResolutions.any { it.quality == VideoQuality.FHD_1080P })
        assertTrue(profile.supportedResolutions.any { it.quality == VideoQuality.HD_720P })

        // Verify aspect ratios exist
        assertTrue(profile.supportedAspectRatios.any { it.aspectRatio == VideoAspectRatio.RATIO_16_9 })
        assertTrue(profile.supportedAspectRatios.any { it.aspectRatio == VideoAspectRatio.RATIO_4_3 })
    }

    @Test
    fun testGetHardwareCameraProfile_frontCamera() {
        val context = RuntimeEnvironment.getApplication()
        val profile = CameraHardwareSpecManager.getHardwareCameraProfile(context, isFrontCamera = true)

        assertNotNull(profile)
        assertTrue(profile.lensFacing.contains("Front"))
        assertTrue(profile.supportedResolutions.isNotEmpty())
    }

    @Test
    fun testResolutionsFilteredByAspectRatio() {
        val context = RuntimeEnvironment.getApplication()
        val profile = CameraHardwareSpecManager.getHardwareCameraProfile(context, isFrontCamera = false)

        // 16:9 Resolutions
        val res16_9 = CameraHardwareSpecManager.getResolutionsForAspectRatio(profile, VideoAspectRatio.RATIO_16_9)
        assertTrue(res16_9.isNotEmpty())
        assertTrue(res16_9.all { it.aspectRatio == VideoAspectRatio.RATIO_16_9 })
        val fhd16_9 = res16_9.find { it.quality == VideoQuality.FHD_1080P }
        assertNotNull(fhd16_9)
        assertEquals(1920, fhd16_9?.width)
        assertEquals(1080, fhd16_9?.height)

        // 4:3 Resolutions
        val res4_3 = CameraHardwareSpecManager.getResolutionsForAspectRatio(profile, VideoAspectRatio.RATIO_4_3)
        assertTrue(res4_3.isNotEmpty())
        assertTrue(res4_3.all { it.aspectRatio == VideoAspectRatio.RATIO_4_3 })
        val fhd4_3 = res4_3.find { it.quality == VideoQuality.FHD_1080P }
        assertNotNull(fhd4_3)
        assertEquals(1440, fhd4_3?.width)
        assertEquals(1080, fhd4_3?.height)

        // Resolution lookup helper
        val info16_9 = CameraHardwareSpecManager.getResolutionInfo(profile, VideoQuality.HD_720P, VideoAspectRatio.RATIO_16_9)
        assertEquals(1280, info16_9?.width)
        assertEquals(720, info16_9?.height)

        val info4_3 = CameraHardwareSpecManager.getResolutionInfo(profile, VideoQuality.HD_720P, VideoAspectRatio.RATIO_4_3)
        assertEquals(960, info4_3?.width)
        assertEquals(720, info4_3?.height)
    }
}
