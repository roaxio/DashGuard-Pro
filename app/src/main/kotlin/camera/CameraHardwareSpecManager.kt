package io.github.roaxio.dashguardpro.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import io.github.roaxio.dashguardpro.data.settings.VideoAspectRatio
import io.github.roaxio.dashguardpro.data.settings.VideoQuality

data class CameraHardwareProfile(
    val cameraId: String,
    val lensFacing: String,
    val sensorMegapixels: Float,
    val sensorDimensions: String,
    val supportedResolutions: List<SupportedResolutionInfo>,
    val supportedAspectRatios: List<SupportedAspectRatioInfo>
)

data class SupportedResolutionInfo(
    val quality: VideoQuality,
    val title: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.RATIO_16_9,
    val supportedFps: List<Int> = listOf(30),
    val isHardwareSupported: Boolean
)

data class SupportedAspectRatioInfo(
    val aspectRatio: VideoAspectRatio,
    val title: String,
    val description: String,
    val isHardwareSupported: Boolean
)

object CameraHardwareSpecManager {

    fun getHardwareCameraProfile(context: Context, isFrontCamera: Boolean = false): CameraHardwareProfile {
        var sensorMp = 0f
        var sensorDimStr = ""
        val supportedSizes = mutableListOf<Size>()
        var camId = if (isFrontCamera) "1" else "0"
        var fpsRanges = emptyArray<android.util.Range<Int>>()
        var streamConfigMap: android.hardware.camera2.params.StreamConfigurationMap? = null

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                val targetFacing = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == targetFacing) {
                        camId = id
                        fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
                        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        val pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                        val w = pixelArray?.width ?: activeArray?.width() ?: 0
                        val h = pixelArray?.height ?: activeArray?.height() ?: 0
                        if (w > 0 && h > 0) {
                            sensorMp = (w * h) / 1_000_000f
                            sensorDimStr = "${w}x${h}"
                        }

                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        streamConfigMap = map
                        if (map != null) {
                            val recorderSizes = map.getOutputSizes(android.media.MediaRecorder::class.java)
                            val surfaceSizes = map.getOutputSizes(android.graphics.SurfaceTexture::class.java)
                            val allSizes = (recorderSizes ?: emptyArray()) + (surfaceSizes ?: emptyArray())
                            supportedSizes.addAll(allSizes.distinctBy { "${it.width}x${it.height}" })
                        }
                        break
                    }
                }
            }
        } catch (_: Throwable) {}

        // Fallback default sizes if running in headless test/Robolectric or restricted sandbox
        if (supportedSizes.isEmpty()) {
            supportedSizes.add(Size(3840, 2160))
            supportedSizes.add(Size(1920, 1080))
            supportedSizes.add(Size(1440, 1080))
            supportedSizes.add(Size(1280, 720))
            supportedSizes.add(Size(960, 720))
            supportedSizes.add(Size(720, 480))
            supportedSizes.add(Size(640, 480))
            if (sensorMp == 0f) {
                sensorMp = 12f
                sensorDimStr = "4000x3000"
            }
        }

        val globalMaxFps = fpsRanges.map { it.upper }.filter { it <= 62 }.maxOrNull() ?: 30

        fun getSupportedFpsForSize(size: Size, quality: VideoQuality): List<Int> {
            val supportedForSize = mutableSetOf<Int>()

            val minDurationNs = try {
                streamConfigMap?.getOutputMinFrameDuration(android.media.MediaRecorder::class.java, size) ?: 0L
            } catch (_: Exception) { 0L }

            val maxFpsByDuration = if (minDurationNs > 0L) {
                (1_000_000_000.0 / minDurationNs).toInt()
            } else {
                globalMaxFps
            }

            // 1. Inspect sensor's actual AE target FPS ranges
            for (range in fpsRanges) {
                val upper = range.upper
                if (upper in 14..62) {
                    if (upper <= (maxFpsByDuration + 2)) {
                        val normalized = when {
                            upper in 58..62 -> 60
                            upper in 28..32 -> 30
                            upper in 23..25 -> 24
                            upper in 14..16 -> 15
                            else -> upper
                        }
                        supportedForSize.add(normalized)
                    }
                }
            }

            // 2. If frame duration allows 60 FPS (<= 17.2ms) and sensor has 60 FPS range or high-speed support
            if (maxFpsByDuration >= 58) {
                val has60Range = fpsRanges.any { it.upper in 58..62 }
                val has60HighSpeed = try {
                    streamConfigMap?.getHighSpeedVideoFpsRangesFor(size)?.any { it.upper in 58..62 } == true
                } catch (_: Exception) { false }

                if (has60Range || has60HighSpeed || minDurationNs in 1L..17_000_000L) {
                    supportedForSize.add(60)
                }
            }

            // 3. Check CamcorderProfile for hardware verified recording profile
            try {
                val numericCamId = camId.toIntOrNull()
                if (numericCamId != null) {
                    val profileQuality = when (quality) {
                        VideoQuality.UHD_4K -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) android.media.CamcorderProfile.QUALITY_2160P else -1
                        VideoQuality.FHD_1080P -> android.media.CamcorderProfile.QUALITY_1080P
                        VideoQuality.HD_720P -> android.media.CamcorderProfile.QUALITY_720P
                        VideoQuality.SD_480P -> android.media.CamcorderProfile.QUALITY_480P
                    }
                    if (profileQuality != -1 && android.media.CamcorderProfile.hasProfile(numericCamId, profileQuality)) {
                        val prof = android.media.CamcorderProfile.get(numericCamId, profileQuality)
                        val profRate = prof.videoFrameRate
                        if (profRate in 14..62) {
                            val normProfRate = if (profRate in 58..62) 60 else if (profRate in 28..32) 30 else profRate
                            supportedForSize.add(normProfRate)
                        }
                    }
                }
            } catch (_: Throwable) {}

            // Base standard fallback: 30 FPS
            if (supportedForSize.isEmpty()) {
                supportedForSize.add(30)
            }

            return supportedForSize.filter { it <= 60 }.sorted()
        }

        // 1. Group actual sizes by Aspect Ratio
        val sizes16_9 = supportedSizes.filter { isRatioMatching(it.width, it.height, 16.0 / 9.0) }
            .sortedByDescending { it.width * it.height }
        val sizes4_3 = supportedSizes.filter { isRatioMatching(it.width, it.height, 4.0 / 3.0) }
            .sortedByDescending { it.width * it.height }

        val resolutions = mutableListOf<SupportedResolutionInfo>()

        // Helper to match size to VideoQuality enum
        fun getQualityForSize(size: Size): VideoQuality {
            val shortEdge = minOf(size.width, size.height)
            val pixels = size.width * size.height
            return when {
                pixels >= 7_000_000 || shortEdge >= 2000 -> VideoQuality.UHD_4K
                pixels >= 1_500_000 || shortEdge >= 1000 -> VideoQuality.FHD_1080P
                pixels >= 600_000 || shortEdge >= 700 -> VideoQuality.HD_720P
                else -> VideoQuality.SD_480P
            }
        }

        // For 16:9, map highest available for each quality tier
        val qualityMap16_9 = mutableMapOf<VideoQuality, Size>()
        for (size in sizes16_9) {
            val q = getQualityForSize(size)
            if (!qualityMap16_9.containsKey(q)) {
                qualityMap16_9[q] = size
            }
        }

        qualityMap16_9.forEach { (q, size) ->
            val longEdge = maxOf(size.width, size.height)
            val shortEdge = minOf(size.width, size.height)
            val fpsList = getSupportedFpsForSize(size, q)
            val titleStr = when (q) {
                VideoQuality.UHD_4K -> "4K UHD (${longEdge}x${shortEdge}) • 16:9"
                VideoQuality.FHD_1080P -> "1080p FHD (${longEdge}x${shortEdge}) • 16:9"
                VideoQuality.HD_720P -> "720p HD (${longEdge}x${shortEdge}) • 16:9"
                VideoQuality.SD_480P -> "480p SD (${longEdge}x${shortEdge}) • 16:9"
            }
            val subStr = when (q) {
                VideoQuality.UHD_4K -> "Ultra High Detail • Hardware 4K Sensor Mode"
                VideoQuality.FHD_1080P -> "High Detail • Recommended for Dashcam"
                VideoQuality.HD_720P -> "Balanced Detail • Low Battery Heat"
                VideoQuality.SD_480P -> "Storage Saver • Max Recording Time"
            }
            resolutions.add(
                SupportedResolutionInfo(
                    quality = q,
                    title = titleStr,
                    subtitle = subStr,
                    width = longEdge,
                    height = shortEdge,
                    aspectRatio = VideoAspectRatio.RATIO_16_9,
                    supportedFps = fpsList,
                    isHardwareSupported = true
                )
            )
        }

        // For 4:3, map highest available for each quality tier
        val qualityMap4_3 = mutableMapOf<VideoQuality, Size>()
        for (size in sizes4_3) {
            val q = getQualityForSize(size)
            if (!qualityMap4_3.containsKey(q)) {
                qualityMap4_3[q] = size
            }
        }

        qualityMap4_3.forEach { (q, size) ->
            val longEdge = maxOf(size.width, size.height)
            val shortEdge = minOf(size.width, size.height)
            val fpsList = getSupportedFpsForSize(size, q)
            val titleStr = when (q) {
                VideoQuality.UHD_4K -> "4K / Full Sensor (${longEdge}x${shortEdge}) • 4:3"
                VideoQuality.FHD_1080P -> "1080p 4:3 (${longEdge}x${shortEdge}) • 4:3"
                VideoQuality.HD_720P -> "720p 4:3 (${longEdge}x${shortEdge}) • 4:3"
                VideoQuality.SD_480P -> "480p VGA (${longEdge}x${shortEdge}) • 4:3"
            }
            val subStr = when (q) {
                VideoQuality.UHD_4K -> "Ultra High Detail • Native 4:3 Full Height Sensor Mode"
                VideoQuality.FHD_1080P -> "High Detail • Full Sensor Height with Dashcam Focus"
                VideoQuality.HD_720P -> "Balanced Detail • Tall 4:3 Traffic Light Angle"
                VideoQuality.SD_480P -> "Storage Saver • Standard 4:3 VGA"
            }
            resolutions.add(
                SupportedResolutionInfo(
                    quality = q,
                    title = titleStr,
                    subtitle = subStr,
                    width = longEdge,
                    height = shortEdge,
                    aspectRatio = VideoAspectRatio.RATIO_4_3,
                    supportedFps = fpsList,
                    isHardwareSupported = true
                )
            )
        }

        val aspectRatios = mutableListOf<SupportedAspectRatioInfo>()
        if (sizes16_9.isNotEmpty()) {
            aspectRatios.add(
                SupportedAspectRatioInfo(
                    aspectRatio = VideoAspectRatio.RATIO_16_9,
                    title = "16:9 (Standard Widescreen)",
                    description = "Native 16:9 Sensor Crop • Wide Road Angle",
                    isHardwareSupported = true
                )
            )
        }
        if (sizes4_3.isNotEmpty()) {
            aspectRatios.add(
                SupportedAspectRatioInfo(
                    aspectRatio = VideoAspectRatio.RATIO_4_3,
                    title = "4:3 (Taller Field of View)",
                    description = "Full Sensor Height • Captures Traffic Lights & Sky",
                    isHardwareSupported = true
                )
            )
        }

        // If the hardware supports neither for some strange reason, supply fallbacks just in case
        if (aspectRatios.isEmpty()) {
            aspectRatios.add(
                SupportedAspectRatioInfo(VideoAspectRatio.RATIO_16_9, "16:9 (Standard Widescreen)", "Software Crop", true)
            )
        }

        return CameraHardwareProfile(
            cameraId = camId,
            lensFacing = if (isFrontCamera) "Front Camera" else "Rear Main Camera",
            sensorMegapixels = (Math.round(sensorMp * 10f) / 10f),
            sensorDimensions = sensorDimStr,
            supportedResolutions = resolutions.sortedByDescending { it.width * it.height },
            supportedAspectRatios = aspectRatios
        )
    }

    fun getResolutionsForAspectRatio(
        profile: CameraHardwareProfile,
        aspectRatio: VideoAspectRatio
    ): List<SupportedResolutionInfo> {
        val filtered = profile.supportedResolutions.filter { it.aspectRatio == aspectRatio }
        return if (filtered.isNotEmpty()) filtered else profile.supportedResolutions
    }

    fun getResolutionInfo(
        profile: CameraHardwareProfile,
        quality: VideoQuality,
        aspectRatio: VideoAspectRatio
    ): SupportedResolutionInfo? {
        return profile.supportedResolutions.find { it.quality == quality && it.aspectRatio == aspectRatio }
            ?: profile.supportedResolutions.find { it.quality == quality }
            ?: profile.supportedResolutions.firstOrNull()
    }

    fun getEquivalentResolutionForAspectRatio(
        profile: CameraHardwareProfile,
        currentQuality: VideoQuality,
        newAspectRatio: VideoAspectRatio
    ): SupportedResolutionInfo {
        val availableInNewAr = getResolutionsForAspectRatio(profile, newAspectRatio)
        // 1. Exact tier match
        val exact = availableInNewAr.find { it.quality == currentQuality }
        if (exact != null) return exact

        // 2. Closest lower tier
        val qualityOrder = listOf(
            VideoQuality.UHD_4K,
            VideoQuality.FHD_1080P,
            VideoQuality.HD_720P,
            VideoQuality.SD_480P
        )
        val currentIndex = qualityOrder.indexOf(currentQuality)
        if (currentIndex != -1) {
            for (i in (currentIndex + 1) until qualityOrder.size) {
                val lowerCandidate = availableInNewAr.find { it.quality == qualityOrder[i] }
                if (lowerCandidate != null) return lowerCandidate
            }
            // 3. Closest higher tier
            for (i in (currentIndex - 1) downTo 0) {
                val higherCandidate = availableInNewAr.find { it.quality == qualityOrder[i] }
                if (higherCandidate != null) return higherCandidate
            }
        }
        return availableInNewAr.firstOrNull() ?: profile.supportedResolutions.first()
    }

    private fun isRatioMatching(w: Int, h: Int, targetRatio: Double): Boolean {
        val longSide = maxOf(w, h).toDouble()
        val shortSide = minOf(w, h).toDouble()
        if (shortSide == 0.0) return false
        val ratio = longSide / shortSide
        return Math.abs(ratio - targetRatio) < 0.08
    }
}
