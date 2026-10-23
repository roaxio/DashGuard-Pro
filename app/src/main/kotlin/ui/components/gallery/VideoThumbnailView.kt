package io.github.roaxio.dashguardpro.ui.components.gallery

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe memory cache for video thumbnails.
 * Caches up to 80 scaled bitmaps (~6MB total memory footprint) to provide instant gallery scrolling.
 */
object VideoThumbnailCache {
    private val cache = LruCache<String, Bitmap>(80)
    private val aspectRatioCache = LruCache<String, Float>(80)

    fun get(key: String): Bitmap? = synchronized(cache) {
        cache.get(key)
    }

    fun getAspectRatio(key: String): Float? = synchronized(aspectRatioCache) {
        aspectRatioCache.get(key)
    }

    fun put(key: String, bitmap: Bitmap, aspectRatio: Float) = synchronized(cache) {
        cache.put(key, bitmap)
        aspectRatioCache.put(key, aspectRatio)
    }

    suspend fun loadThumbnail(filePath: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return@withContext null

        get(filePath)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            // Extract frame at 1s or first available keyframe
            val rawBitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            if (rawBitmap != null) {
                val bw = rawBitmap.width
                val bh = rawBitmap.height
                val trueAr = if (bh > 0) bw.toFloat() / bh.toFloat() else (16f / 9f)

                // Scale to crisp preview resolution (target 240px width)
                val targetWidth = 240
                val targetHeight = if (bw > 0) {
                    (targetWidth.toFloat() * bh / bw).toInt().coerceAtLeast(1)
                } else {
                    135
                }
                val scaled = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                if (scaled !== rawBitmap) {
                    rawBitmap.recycle()
                }
                put(filePath, scaled, trueAr)
                scaled
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}

/**
 * Renders a lightweight, cached thumbnail image of the first frame of a local video file.
 */
@Composable
fun VideoThumbnailView(
    filePath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Video thumbnail",
    contentScale: ContentScale = ContentScale.Crop,
    onAspectRatioLoaded: ((Float) -> Unit)? = null
) {
    var thumbnailBitmap by remember(filePath) {
        mutableStateOf(VideoThumbnailCache.get(filePath))
    }

    LaunchedEffect(filePath) {
        if (thumbnailBitmap == null) {
            thumbnailBitmap = VideoThumbnailCache.loadThumbnail(filePath)
        }
        val ar = VideoThumbnailCache.getAspectRatio(filePath)
        if (ar != null && onAspectRatioLoaded != null) {
            onAspectRatioLoaded(ar)
        }
    }

    val currentBitmap = thumbnailBitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}
