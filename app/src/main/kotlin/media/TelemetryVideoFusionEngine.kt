@file:OptIn(androidx.media3.common.util.UnstableApi::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.roaxio.dashguardpro.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import io.github.roaxio.dashguardpro.util.srtSidecarFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object TelemetryVideoFusionEngine {

    private const val TAG = "TelemetryFusionEngine"

    fun getFusedVideoFile(sourceVideoFile: File): File {
        val parent = sourceVideoFile.parentFile ?: File(".")
        val nameWithoutExt = sourceVideoFile.nameWithoutExtension
        val ext = sourceVideoFile.extension.ifBlank { "mp4" }
        return File(parent, "$nameWithoutExt-telemetry-fused.$ext")
    }

    fun isFusedVideoAvailable(sourceVideoFile: File): Boolean {
        val fusedFile = getFusedVideoFile(sourceVideoFile)
        return fusedFile.exists() && fusedFile.length() > 1024L
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun fuseTelemetry(
        context: Context,
        sourceVideo: File,
        sourceSrt: File? = null,
        onProgress: (Float) -> Unit = {}
    ): File? {
        if (!sourceVideo.exists() || sourceVideo.length() == 0L) {
            Log.e(TAG, "Source video does not exist: ${sourceVideo.absolutePath}")
            return null
        }

        val targetFusedFile = getFusedVideoFile(sourceVideo)
        if (isFusedVideoAvailable(sourceVideo)) {
            onProgress(1.0f)
            return targetFusedFile
        }

        val meta = withContext(Dispatchers.IO) {
            val effectiveSrtFile = sourceSrt ?: sourceVideo.srtSidecarFile
            val subs = if (effectiveSrtFile.exists()) {
                TelemetrySubtitleManager.parseSubtitleFile(effectiveSrtFile)
            } else {
                emptyList()
            }

            val retriever = MediaMetadataRetriever()
            var w = 1280
            var h = 720
            var rotation = 0
            var bitrate = 0
            try {
                retriever.setDataSource(sourceVideo.absolutePath)
                w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    val temp = w
                    w = h
                    h = temp
                }
            } catch (e: Exception) {
                Log.e(TAG, "Retriever failed", e)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            VideoSourceMetadata(subs, if (w > 0) w else 1280, if (h > 0) h else 720, bitrate)
        }

        val tempOutputFile = File(targetFusedFile.parentFile ?: sourceVideo.parentFile, "tmp_fused_${System.currentTimeMillis()}.mp4")
        if (tempOutputFile.exists()) {
            try { tempOutputFile.delete() } catch (_: Exception) {}
        }

        val success = transcodeWithMedia3(
            context = context,
            sourceVideo = sourceVideo,
            outputFile = tempOutputFile,
            subtitles = meta.subtitles,
            width = meta.width,
            height = meta.height,
            bitrate = meta.bitrate,
            onProgress = onProgress
        )

        return withContext(Dispatchers.IO) {
            if (success && tempOutputFile.exists() && tempOutputFile.length() > 1024L) {
                try {
                    if (targetFusedFile.exists()) targetFusedFile.delete()
                    val renamed = tempOutputFile.renameTo(targetFusedFile)
                    if (!renamed || !targetFusedFile.exists()) {
                        tempOutputFile.copyTo(targetFusedFile, overwrite = true)
                        tempOutputFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finalizing fused video file", e)
                }

                if (targetFusedFile.exists() && targetFusedFile.length() > 1024L) {
                    onProgress(1.0f)
                    targetFusedFile
                } else {
                    null
                }
            } else {
                try { if (tempOutputFile.exists()) tempOutputFile.delete() } catch (_: Exception) {}
                null
            }
        }
    }

    private data class VideoSourceMetadata(
        val subtitles: List<TelemetrySubtitleEntry>,
        val width: Int,
        val height: Int,
        val bitrate: Int
    )

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun transcodeWithMedia3(
        context: Context,
        sourceVideo: File,
        outputFile: File,
        subtitles: List<TelemetrySubtitleEntry>,
        width: Int,
        height: Int,
        bitrate: Int,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            try {
                val isLandscape = width >= height
                val baseTextSize = if (isLandscape) (height * 0.035f).coerceIn(16f, 42f) else (width * 0.035f).coerceIn(16f, 36f)

                val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = baseTextSize
                    typeface = Typeface.MONOSPACE
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(3f, 0f, 1f, Color.BLACK)
                }
                val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(64, 0, 0, 0) // 25% Opacity / 75% Transparent
                    style = Paint.Style.FILL
                }
                val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(35, 0, 240, 255) // Subtle transparent cyan outline (14% opacity)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }

                @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
                val bitmapOverlay = object : BitmapOverlay() {
                    private var lastText: String? = null
                    private val cachedBitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    private val canvas = Canvas(cachedBitmap)

                    override fun getBitmap(presentationTimeUs: Long): Bitmap {
                        val posMs = presentationTimeUs / 1000L
                        val text = TelemetrySubtitleManager.getTelemetryForPosition(subtitles, posMs)
                            ?: formatFallbackTelemetry(posMs)

                        if (text == lastText) {
                            return cachedBitmap
                        }
                        lastText = text

                        cachedBitmap.eraseColor(Color.TRANSPARENT)

                        val maxTextWidth = width * 0.90f
                        paintText.textSize = baseTextSize
                        var textWidth = paintText.measureText(text)

                        if (textWidth > maxTextWidth) {
                            val scaledSize = baseTextSize * (maxTextWidth / textWidth)
                            if (scaledSize >= 15f) {
                                paintText.textSize = scaledSize
                                textWidth = paintText.measureText(text)
                                drawSingleLineWatermark(canvas, text, width, height, textWidth, paintText, paintBg, paintBorder)
                            } else {
                                drawTwoLineWatermark(canvas, text, width, height, baseTextSize, maxTextWidth, paintText, paintBg, paintBorder)
                            }
                        } else {
                            drawSingleLineWatermark(canvas, text, width, height, textWidth, paintText, paintBg, paintBorder)
                        }

                        return cachedBitmap
                    }
                }

                val overlayEffect = OverlayEffect(listOf(bitmapOverlay))
                val effects = Effects(
                    listOf(),
                    listOf<Effect>(overlayEffect)
                )

                val mediaItem = MediaItem.fromUri(Uri.fromFile(sourceVideo))
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(effects)
                    .build()

                var progressJob: Job? = null

                val transformerBuilder = Transformer.Builder(context)
                if (bitrate > 0) {
                    try {
                        val encoderSettings = androidx.media3.transformer.VideoEncoderSettings.Builder()
                            .setBitrate(bitrate)
                            .build()
                        val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(encoderSettings)
                            .setEnableFallback(true)
                            .build()
                        transformerBuilder.setEncoderFactory(encoderFactory)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set custom bitrate for transformer: ${e.message}")
                    }
                }

                val transformer = transformerBuilder
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                            Log.d(TAG, "Media3 export completed successfully")
                            progressJob?.cancel()
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                            Log.e(TAG, "Media3 export failed: ${exportException.errorCodeName} (${exportException.errorCode})", exportException)
                            progressJob?.cancel()
                            if (continuation.isActive) continuation.resume(false)
                        }
                    })
                    .build()

                transformer.start(editedMediaItem, outputFile.absolutePath)

                progressJob = launch(Dispatchers.Main) {
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    while (continuation.isActive) {
                        try {
                            val state = transformer.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val currentPct = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                                onProgress(currentPct)
                            }
                        } catch (_: Exception) {}
                        delay(100)
                    }
                }

                continuation.invokeOnCancellation {
                    progressJob?.cancel()
                    try {
                        transformer.cancel()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error cancelling transformer", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Media3 transformer", e)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun drawSingleLineWatermark(
        canvas: Canvas,
        text: String,
        width: Int,
        height: Int,
        textWidth: Float,
        paintText: Paint,
        paintBg: Paint,
        paintBorder: Paint
    ) {
        val padX = width * 0.03f
        val padY = paintText.textSize * 0.5f
        val boxWidth = (textWidth + padX * 2).coerceIn(width * 0.4f, width * 0.94f)
        val boxHeight = paintText.textSize + padY * 2
        val boxLeft = (width - boxWidth) / 2f
        val boxBottom = height - (height * 0.035f)
        val boxTop = boxBottom - boxHeight
        val boxRect = RectF(boxLeft, boxTop, boxLeft + boxWidth, boxBottom)
        val cornerRadius = 8f

        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paintBg)
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paintBorder)

        val textY = boxTop + (boxHeight / 2f) - ((paintText.descent() + paintText.ascent()) / 2f)
        canvas.drawText(text, width / 2f, textY, paintText)
    }

    private fun drawTwoLineWatermark(
        canvas: Canvas,
        telemetryText: String,
        width: Int,
        height: Int,
        baseTextSize: Float,
        maxTextWidth: Float,
        paintText: Paint,
        paintBg: Paint,
        paintBorder: Paint
    ) {
        val parts = telemetryText.split(" | ")
        val mid = (parts.size + 1) / 2
        val line1 = parts.take(mid).joinToString(" | ")
        val line2 = parts.drop(mid).joinToString(" | ")

        val line1Width = paintText.measureText(line1)
        val line2Width = paintText.measureText(line2)
        val maxLineWidth = maxOf(line1Width, line2Width)

        if (maxLineWidth > maxTextWidth) {
            paintText.textSize = (baseTextSize * (maxTextWidth / maxLineWidth)).coerceAtLeast(13f)
        }

        val actualLine1W = paintText.measureText(line1)
        val actualLine2W = paintText.measureText(line2)
        val maxActualW = maxOf(actualLine1W, actualLine2W)

        val padX = width * 0.03f
        val padY = paintText.textSize * 0.5f
        val lineHeight = paintText.textSize * 1.3f
        val boxWidth = (maxActualW + padX * 2).coerceIn(width * 0.4f, width * 0.95f)
        val boxHeight = (lineHeight * 2) + padY * 1.5f
        val boxLeft = (width - boxWidth) / 2f
        val boxBottom = height - (height * 0.035f)
        val boxTop = boxBottom - boxHeight
        val boxRect = RectF(boxLeft, boxTop, boxLeft + boxWidth, boxBottom)
        val cornerRadius = 8f

        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paintBg)
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paintBorder)

        val textY1 = boxTop + padY + paintText.textSize * 0.85f
        val textY2 = textY1 + lineHeight
        canvas.drawText(line1, width / 2f, textY1, paintText)
        canvas.drawText(line2, width / 2f, textY2, paintText)
    }

    private fun formatFallbackTelemetry(posMs: Long): String {
        val sec = posMs / 1000L
        val mm = sec / 60
        val ss = sec % 60
        return String.format("DashGuard Telemetry | T+%02d:%02d | GPS LIVE", mm, ss)
    }
}
