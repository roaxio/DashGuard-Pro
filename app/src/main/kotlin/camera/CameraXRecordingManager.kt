package io.github.roaxio.dashguardpro.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.core.CameraInfo
import androidx.core.content.ContextCompat
import io.github.roaxio.dashguardpro.data.settings.VideoAspectRatio
import io.github.roaxio.dashguardpro.data.settings.VideoQuality
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Manages CameraX VideoCapture and handles strict lifecycle management for rolling video chunks.
 *
 * Guarantees stream closure and prevents 0-byte output files:
 * 1. Calls stop() on ActiveRecording during chunk rotation.
 * 2. Waits strictly for VideoRecordEvent.Finalize before initializing the next chunk's output file.
 * 3. Inspects event.hasError() on finalization and logs event.cause directly to Logcat to expose
 *    codec crashes, SecurityExceptions, or container errors.
 */
class CameraXRecordingManager(private val context: Context) {

    enum class CameraBindingState {
        UNBOUND,
        BOUND,
        FAILED
    }

    companion object {
        private const val TAG = "DashGuardRecorder"
    }

    private var videoCaptureUseCase: VideoCapture<Recorder>? = null
    var previewUseCase: androidx.camera.core.Preview? = null
    var imageAnalysisUseCase: androidx.camera.core.ImageAnalysis? = null
    private var activeRecording: Recording? = null
    private var isRecordingStarted: Boolean = false
    private var isStopPending: Boolean = false
    private var pendingStopIsRotate: Boolean = false

    // Tracking rotation state
    var isRotating: Boolean = false
        private set
    var isStopping: Boolean = false
        private set
    var lastTransitionDurationMs: Long = 0L
        private set
    private var chunkRotationStartTimeMs: Long = 0L
    private var pendingRotateReason: String? = null
    private var currentActiveFile: File? = null
    private var currentStartTimeMs: Long = 0L
    private val _cameraBindingState = MutableStateFlow(CameraBindingState.UNBOUND)
    val cameraBindingState: StateFlow<CameraBindingState> = _cameraBindingState.asStateFlow()

    // Callbacks for ViewModel integration
    var onSegmentFinalized: ((file: File?, durationMs: Long, hasError: Boolean, cause: Throwable?) -> Unit)? = null
    var onStartNextChunk: ((reason: String) -> Unit)? = null
    var onRecordEvent: ((VideoRecordEvent) -> Unit)? = null

    fun markCameraBindingPending() {
        _cameraBindingState.value = CameraBindingState.UNBOUND
    }

    fun markCameraBound(cameraInfo: CameraInfo? = null) {
        _cameraBindingState.value = CameraBindingState.BOUND
    }

    fun markCameraBindingFailed() {
        _cameraBindingState.value = CameraBindingState.FAILED
    }

    fun markCameraUnbound() {
        _cameraBindingState.value = CameraBindingState.UNBOUND
    }

    suspend fun awaitCameraBinding(timeoutMillis: Long = 3000L): Boolean {
        if (_cameraBindingState.value == CameraBindingState.BOUND) return true
        return try {
            withTimeout(timeoutMillis) {
                when (cameraBindingState.first { it != CameraBindingState.UNBOUND }) {
                    CameraBindingState.BOUND -> true
                    CameraBindingState.FAILED -> false
                    CameraBindingState.UNBOUND -> false
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timed out waiting for camera binding; state: ${_cameraBindingState.value}")
            _cameraBindingState.value == CameraBindingState.BOUND || videoCaptureUseCase != null
        }
    }

    private var boundQuality: VideoQuality? = null
    private var boundAspectRatio: VideoAspectRatio? = null
    private var boundFps: Int? = null

    val isRecordingActive: Boolean
        get() = activeRecording != null

    fun resetVideoCapture() {
        if (!isRecordingActive) {
            videoCaptureUseCase = null
            boundQuality = null
            boundAspectRatio = null
            boundFps = null
        }
    }

    fun getVideoCapture(
        cameraInfo: androidx.camera.core.CameraInfo? = null,
        targetQuality: VideoQuality? = null,
        targetAspectRatio: VideoAspectRatio? = null,
        targetFps: Int? = null
    ): VideoCapture<Recorder>? {
        val existing = videoCaptureUseCase
        if (existing != null) {
            // If caller didn't specify new parameters, return the current bound instance
            if (targetQuality == null && targetAspectRatio == null && targetFps == null) {
                return existing
            }
            // If parameters match what is already bound, return the current instance
            val qMatches = targetQuality == null || targetQuality == boundQuality
            val arMatches = targetAspectRatio == null || targetAspectRatio == boundAspectRatio
            val fpsMatches = targetFps == null || targetFps == boundFps
            if (qMatches && arMatches && fpsMatches) {
                return existing
            }
            // If recording is actively in progress, do not destroy active pipeline mid-record
            if (isRecordingActive) {
                return existing
            }
        }

        val quality = when (targetQuality ?: boundQuality) {
            VideoQuality.SD_480P -> Quality.SD
            VideoQuality.HD_720P -> Quality.HD
            VideoQuality.FHD_1080P -> Quality.FHD
            VideoQuality.UHD_4K -> Quality.UHD
            else -> Quality.HD
        }

        val qualitySelector = QualitySelector.from(
            quality,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST)
        )

        val recorderBuilder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .setExecutor(ContextCompat.getMainExecutor(context))
            
        (targetAspectRatio ?: boundAspectRatio)?.let {
            recorderBuilder.setAspectRatio(it.ratioValue)
        }
        
        val recorder = recorderBuilder.build()
        val capture = VideoCapture.withOutput(recorder)
        
        // Dynamically align target rotation with current display orientation to avoid inverted 180° video metadata
        capture.targetRotation = getDisplayRotation()
        
        videoCaptureUseCase = capture
        if (targetQuality != null) boundQuality = targetQuality
        if (targetAspectRatio != null) boundAspectRatio = targetAspectRatio
        if (targetFps != null) boundFps = targetFps
        return capture
    }

    fun getDisplayRotation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
            } catch (_: Exception) {
                windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
            }
        } else {
            windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
        }
    }

    fun updateTargetRotation(rotation: Int? = null) {
        // Prevent camera sensor/recording pipeline from rotating mid-record
        if (isRecordingActive) {
            return
        }
        val effectiveRotation = rotation ?: getDisplayRotation()
        try {
            videoCaptureUseCase?.targetRotation = effectiveRotation
            previewUseCase?.targetRotation = effectiveRotation
        } catch (e: Exception) {
            Log.w(TAG, "Could not update targetRotation: ${e.message}")
        }
    }

    fun startRecording(outputFile: File, enableAudio: Boolean): Recording? {
        val capture = getVideoCapture() ?: return null
        
        // Refresh rotation prior to recording start so video metadata precisely matches current orientation
        try {
            capture.targetRotation = getDisplayRotation()
        } catch (_: Exception) {}

        currentActiveFile = outputFile
        currentStartTimeMs = System.currentTimeMillis()
        isRecordingStarted = false
        isStopPending = false
        pendingStopIsRotate = false

        // Clean up any stale active recording before starting a new one
        if (activeRecording != null) {
            try {
                if (isRecordingStarted) {
                    activeRecording?.stop()
                } else {
                    activeRecording?.close()
                }
            } catch (_: Exception) {}
            activeRecording = null
            isStopping = false
        }

        return try {
            val outputOptions = FileOutputOptions.Builder(outputFile).build()
            var pending: PendingRecording = capture.output.prepareRecording(context, outputOptions)

            if (enableAudio) {
                val hasAudioPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasAudioPerm) {
                    try {
                        pending = pending.withAudioEnabled()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Audio recording permission check failed: ${e.message}")
                    }
                }
            }

            val recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                handleRecordEvent(event, outputFile)
            }

            activeRecording = recording
            Log.i(TAG, "Started recording chunk: ${outputFile.name}")
            recording
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CameraX recording: ${e.message}", e)
            activeRecording = null
            isStopping = false
            isRecordingStarted = false
            isStopPending = false
            null
        }
    }

    /**
     * Rotates to a new rolling video chunk.
     *
     * Strict Lifecycle Handling:
     * 1. Calls stop() on ActiveRecording once it has cleanly started.
     * 2. Waits for VideoRecordEvent.Finalize before creating/initializing the next output file.
     */
    fun rotateToNextChunk(reason: String = "Normal Loop") {
        if (isRotating || isStopping) return
        chunkRotationStartTimeMs = System.currentTimeMillis()
        val current = activeRecording
        if (current != null) {
            isRotating = true
            isStopping = true
            pendingRotateReason = reason
            Log.d(TAG, "Requesting stop() on ActiveRecording to rotate chunk (reason: $reason)...")

            if (!isRecordingStarted) {
                // A Recording returned by PendingRecording.start() can be stopped before
                // CameraX emits Start. Waiting for Start is unsafe on slower/device-specific
                // camera pipelines: some interruptions can leave that event pending forever.
                // close() is the documented terminal operation and still produces Finalize.
                Log.d(TAG, "Recording not started yet; closing immediately to finalize rotation")
                isStopPending = false
                pendingStopIsRotate = false
                try {
                    current.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing pre-start recording during rotation: ${e.message}", e)
                    activeRecording = null
                    isRotating = false
                    isStopping = false
                    isRecordingStarted = false
                    pendingRotateReason = null
                    onStartNextChunk?.invoke(reason)
                }
                return
            }

            try {
                current.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error calling stop() on active recording: ${e.message}", e)
                activeRecording = null
                isRotating = false
                isStopping = false
                isRecordingStarted = false
                isStopPending = false
                onStartNextChunk?.invoke(reason)
            }
        } else {
            // If no active CameraX recording (e.g. running in testing/mock or unsupported device), trigger direct transition
            onStartNextChunk?.invoke(reason)
        }
    }

    /**
     * Explicitly stops the current active recording session.
     */
    fun stopRecording() {
        isRotating = false
        pendingRotateReason = null
        val current = activeRecording
        if (current != null) {
            Log.d(TAG, "Requesting stop() on ActiveRecording to end recording session (started=$isRecordingStarted)...")
            isStopping = true
            if (!isRecordingStarted) {
                // Do not defer teardown to VideoRecordEvent.Start. On physical devices the
                // camera may be interrupted during startup and never emit Start. Closing the
                // Recording here lets CameraX deliver its terminal Finalize event immediately.
                Log.d(TAG, "Recording not started yet; closing immediately to finalize")
                isStopPending = false
                pendingStopIsRotate = false
                try {
                    current.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing pre-start recording: ${e.message}", e)
                    activeRecording = null
                    isStopping = false
                    isRecordingStarted = false
                }
                return
            }
            try {
                current.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping active recording: ${e.message}", e)
                activeRecording = null
                isStopping = false
                isRecordingStarted = false
                isStopPending = false
            }
        } else {
            isStopping = false
            isRecordingStarted = false
            isStopPending = false
        }
    }

    /**
     * Event listener for CameraX VideoRecordEvent.
     */
    private fun handleRecordEvent(event: VideoRecordEvent, recordedFile: File) {
        onRecordEvent?.invoke(event)

        when (event) {
            is VideoRecordEvent.Start -> {
                isRecordingStarted = true
                Log.i(TAG, "VideoRecordEvent.Start: Active stream began for ${recordedFile.name}")

                if (isStopPending) {
                    isStopPending = false
                    val rec = activeRecording
                    if (rec != null) {
                        Log.d(TAG, "Executing deferred stop() after receiving VideoRecordEvent.Start...")
                        try {
                            rec.stop()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in deferred stop(): ${e.message}", e)
                            activeRecording = null
                            isStopping = false
                            isRecordingStarted = false
                            if (pendingStopIsRotate) {
                                isRotating = false
                                val reason = pendingRotateReason ?: "Normal Loop"
                                pendingRotateReason = null
                                onStartNextChunk?.invoke(reason)
                            }
                        }
                    }
                }
            }

            is VideoRecordEvent.Finalize -> {
                isRecordingStarted = false
                isStopPending = false
                val finalizedFile = recordedFile
                val durationMs = if (event.recordingStats.recordedDurationNanos > 0) {
                    event.recordingStats.recordedDurationNanos / 1_000_000L
                } else {
                    (System.currentTimeMillis() - currentStartTimeMs).coerceAtLeast(1000L)
                }

                if (event.hasError()) {
                    Log.e(
                        TAG,
                        "VideoRecordEvent.Finalize encountered error (code: ${event.error}): ${event.cause?.message}",
                        event.cause
                    )
                } else {
                    Log.i(
                        TAG,
                        "VideoRecordEvent.Finalize successful: file=${finalizedFile.name}, size=${finalizedFile.length()}, duration=${durationMs}ms"
                    )
                }

                if (currentActiveFile == recordedFile) {
                    activeRecording = null
                    currentActiveFile = null
                    isStopping = false
                }

                // Notify callback that segment is completely written and finalized
                onSegmentFinalized?.invoke(finalizedFile, durationMs, event.hasError(), event.cause)

                // Stream Closure Guarantee: Only initialize next output file after Finalize event is received
                if (isRotating && currentActiveFile == null) {
                    val transitionDurationMs = if (chunkRotationStartTimeMs > 0) System.currentTimeMillis() - chunkRotationStartTimeMs else 0L
                    lastTransitionDurationMs = transitionDurationMs
                    chunkRotationStartTimeMs = 0L
                    if (transitionDurationMs > 300L) {
                        Log.w(TAG, "Chunk transition latency alert: container rotation took ${transitionDurationMs}ms (> 300ms threshold)")
                    } else {
                        Log.i(TAG, "Chunk transition latency: ${transitionDurationMs}ms")
                    }
                    isRotating = false
                    val reason = pendingRotateReason ?: "Normal Loop"
                    pendingRotateReason = null
                    Log.d(TAG, "Stream closure complete. Initializing next output file chunk (reason: $reason)...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onStartNextChunk?.invoke(reason)
                    }, 200L)
                }
            }

            is VideoRecordEvent.Status -> {
                // Periodic status updates
            }

            is VideoRecordEvent.Pause -> {
                Log.d(TAG, "VideoRecordEvent.Pause")
            }

            is VideoRecordEvent.Resume -> {
                Log.d(TAG, "VideoRecordEvent.Resume")
            }
        }
    }
}
