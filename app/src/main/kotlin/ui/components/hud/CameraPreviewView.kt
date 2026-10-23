package io.github.roaxio.dashguardpro.ui.components.hud

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.roaxio.dashguardpro.camera.MotionDetectionAnalyzer
import io.github.roaxio.dashguardpro.DashGuardApplication
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.VideoAspectRatio
import io.github.roaxio.dashguardpro.data.settings.VideoQuality
import java.util.concurrent.Executors

private const val TAG = "CameraPreviewView"

@Composable
fun CameraPreviewView(
    isFrontCamera: Boolean,
    isSentryMode: Boolean,
    sentryGuardType: SentryGuardType = SentryGuardType.SHOCK_STANDBY,
    isRecording: Boolean,
    isDimmed: Boolean = false,
    isThermalCooling: Boolean = false,
    targetQuality: VideoQuality = VideoQuality.HD_720P,
    targetAspectRatio: VideoAspectRatio = VideoAspectRatio.RATIO_16_9,
    targetFps: Int = 30,
    motionSensitivityThreshold: Float,
    motionPixelDeltaThreshold: Int = 20,
    motionMinClusterNeighbors: Int = 2,
    motionPersistenceFrames: Int = 2,
    onMotionDetected: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as? DashGuardApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val isOpticalMotion = isSentryMode && sentryGuardType == SentryGuardType.OPTICAL_MOTION

    val motionAnalyzer = remember {
        MotionDetectionAnalyzer { score, luma ->
            Log.d(TAG, "Optical motion detected by analyzer: score=$score, luma=$luma")
            onMotionDetected(score, luma)
        }
    }
    motionAnalyzer.isEnabled = isOpticalMotion && !isRecording
    motionAnalyzer.motionSensitivityThreshold = motionSensitivityThreshold
    motionAnalyzer.pixelDeltaThreshold = motionPixelDeltaThreshold
    motionAnalyzer.minNeighborsForCluster = motionMinClusterNeighbors
    motionAnalyzer.persistenceFrames = motionPersistenceFrames

    // Re-bind only when camera configuration or lifecycle changes
    var isBound by remember { mutableStateOf(false) }
    var boundCameraIsFront by remember { mutableStateOf<Boolean?>(null) }
    var boundQuality by remember { mutableStateOf<VideoQuality?>(null) }
    var boundAspectRatio by remember { mutableStateOf<VideoAspectRatio?>(null) }
    var boundFps by remember { mutableStateOf<Int?>(null) }
    var boundWithImageAnalysis by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isDimmed, previewViewRef) {
        val preview = app?.recordingManager?.previewUseCase
        val pView = previewViewRef
        if (isDimmed) {
            Log.d(TAG, "Screen dimmed: detaching camera preview surface provider to conserve GPU/thermal resources")
            try {
                preview?.setSurfaceProvider(null)
                pView?.visibility = android.view.View.INVISIBLE
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching preview surface: ${e.message}")
            }
        } else {
            Log.d(TAG, "Screen active: restoring camera preview surface provider")
            try {
                pView?.visibility = android.view.View.VISIBLE
                pView?.surfaceProvider?.let { provider ->
                    preview?.setSurfaceProvider(provider)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error restoring preview surface: ${e.message}")
            }
        }
    }

    LaunchedEffect(isFrontCamera, isSentryMode, sentryGuardType, isThermalCooling, isRecording, targetQuality, targetAspectRatio, targetFps, previewViewRef, lifecycleOwner) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        val isRecordingActive = isRecording || app?.recordingManager?.isRecordingActive == true
        val isOpticalMotionTarget = isSentryMode && sentryGuardType == SentryGuardType.OPTICAL_MOTION
        val isCoolingStandby = isThermalCooling && !isRecordingActive
        val shouldBindCamera = (!isSentryMode && !isCoolingStandby) || isRecordingActive || isOpticalMotionTarget
        val needsImageAnalysis = isOpticalMotionTarget && !isRecordingActive

        // If recording is actively writing to disk, guard against mid-recording camera pipeline mutation.
        // Lens, quality, aspect ratio, and FPS changes are held until recording stops cleanly.
        if (isRecordingActive && isBound) {
            Log.d(TAG, "Recording in-flight: guarding active camera pipeline against mid-stream rebind")
            if (!isDimmed) {
                app?.recordingManager?.previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
            }
            return@LaunchedEffect
        }

        // Early return only if the bound state matches target shouldBindCamera and all parameters match (including image analysis)
        if (isBound == shouldBindCamera) {
            if (!shouldBindCamera) {
                // Already cleanly unbound for standby / cooling
                return@LaunchedEffect
            }
            if (boundCameraIsFront == isFrontCamera &&
                boundQuality == targetQuality &&
                boundAspectRatio == targetAspectRatio &&
                boundFps == targetFps &&
                boundWithImageAnalysis == needsImageAnalysis
            ) {
                app?.recordingManager?.markCameraBound()
                return@LaunchedEffect
            }
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderRef = cameraProvider

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                if (!cameraProvider.hasCamera(cameraSelector)) {
                    Log.w(TAG, "Requested camera ($cameraSelector) not available on device")
                    return@addListener
                }

                val cameraInfo = try {
                    cameraProvider.getCameraInfo(cameraSelector)
                } catch (_: Exception) {
                    null
                }

                val previewBuilder = Preview.Builder()
                    .setTargetAspectRatio(targetAspectRatio.ratioValue)

                if (cameraInfo != null && targetFps > 0) {
                    try {
                        val supportedRanges = cameraInfo.supportedFrameRateRanges
                        val matchedRange = supportedRanges.filter { Math.abs(it.upper - targetFps) <= 2 }
                            .minByOrNull { Math.abs(it.lower - (targetFps / 2)) }
                            ?: supportedRanges.firstOrNull { Math.abs(it.upper - targetFps) <= 2 }
                        if (matchedRange != null) {
                            previewBuilder.setTargetFrameRate(matchedRange)
                            Log.d(TAG, "Configured Preview target frame rate: $matchedRange")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set Preview target frame rate: ${e.message}")
                    }
                }

                val preview = previewBuilder.build().also {
                    if (!isDimmed) {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    } else {
                        it.setSurfaceProvider(null)
                    }
                }

                val currentSettings = app?.settingsRepository?.settings?.value
                val videoCapture = try {
                    app?.recordingManager?.getVideoCapture(
                        cameraInfo = cameraInfo,
                        targetQuality = currentSettings?.videoQuality,
                        targetAspectRatio = targetAspectRatio,
                        targetFps = targetFps
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "VideoCapture initialization warning: ${e.message}")
                    null
                }

                val isOpticalMotion = isSentryMode && sentryGuardType == SentryGuardType.OPTICAL_MOTION
                val isCoolingStandby = isThermalCooling && !isRecordingActive
                val shouldBindCamera = (!isSentryMode && !isCoolingStandby) || isRecordingActive || isOpticalMotion
                val needsImageAnalysis = isOpticalMotion && !isRecordingActive

                if (!shouldBindCamera) {
                    cameraProvider.unbindAll()
                    isBound = false
                    app?.recordingManager?.markCameraUnbound()
                    boundCameraIsFront = null
                    boundQuality = null
                    boundAspectRatio = null
                    boundFps = null
                    boundWithImageAnalysis = null
                    Log.d(TAG, "Standby / Thermal Sleep: Camera pipeline completely unbound to save battery/thermal limits.")
                    return@addListener
                }

                val imageAnalysis: ImageAnalysis? = if (needsImageAnalysis) {
                    motionAnalyzer.analysisIntervalMs = 250L // 4 FPS in Sentry standby
                    motionAnalyzer.resetTemporalHistory()
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, motionAnalyzer) }
                } else null

                // Never tear down or rebind camera while recording is actively writing to storage
                if (app?.recordingManager?.isRecordingActive == true) {
                    Log.d(TAG, "Recording is active; keeping camera pipeline intact")
                    if (!isDimmed) {
                        app.recordingManager.previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    isBound = true
                    app?.recordingManager?.markCameraBound(cameraInfo)
                    boundCameraIsFront = isFrontCamera
                    boundQuality = targetQuality
                    boundAspectRatio = targetAspectRatio
                    boundFps = targetFps
                    return@addListener
                }

                if (isBound && boundCameraIsFront == isFrontCamera && boundQuality == targetQuality && boundAspectRatio == targetAspectRatio && boundFps == targetFps && boundWithImageAnalysis == (imageAnalysis != null)) {
                    Log.d(TAG, "Camera already bound to required lens; updating preview surface")
                    if (!isDimmed) {
                        app?.recordingManager?.previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    app?.recordingManager?.markCameraBound(cameraInfo)
                    return@addListener
                }

                // Tier 1 starts from an unbound camera. Publish the pending state before
                // binding so the recorder can wait for this asynchronous lifecycle operation
                // instead of guessing with a delay.
                app?.recordingManager?.markCameraBindingPending()

                cameraProvider.unbindAll()

                val useCasesToBind = mutableListOf<androidx.camera.core.UseCase>()
                useCasesToBind.add(preview)
                if (videoCapture != null) useCasesToBind.add(videoCapture)
                if (imageAnalysis != null) useCasesToBind.add(imageAnalysis)

                app?.recordingManager?.previewUseCase = preview
                app?.recordingManager?.imageAnalysisUseCase = imageAnalysis

                var activeCamera: androidx.camera.core.Camera? = null

                try {
                    activeCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        *useCasesToBind.toTypedArray()
                    )
                    activeCamera?.cameraControl?.let {
                        app?.alertManager?.setCameraControl(it)
                    }
                    isBound = true
                    app?.recordingManager?.markCameraBound(cameraInfo)
                    boundCameraIsFront = isFrontCamera
                    boundQuality = targetQuality
                    boundAspectRatio = targetAspectRatio
                    boundFps = targetFps
                    boundWithImageAnalysis = (imageAnalysis != null)
                    Log.d(TAG, "Successfully bound camera pipeline (AR: ${targetAspectRatio.title}, FPS: $targetFps, ImageAnalysis: ${imageAnalysis != null})")
                } catch (e: Exception) {
                    Log.w(TAG, "Multi-stream bind failed (${e.message}), attempting fallback...")
                    try {
                        cameraProvider.unbindAll()
                        // For Sentry Tier 2 standby, prioritize Preview + ImageAnalysis
                        val fallbackUseCases = if (imageAnalysis != null) {
                            arrayOf<androidx.camera.core.UseCase>(preview, imageAnalysis)
                        } else if (videoCapture != null) {
                            arrayOf<androidx.camera.core.UseCase>(preview, videoCapture)
                        } else {
                            arrayOf<androidx.camera.core.UseCase>(preview)
                        }
                        activeCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            *fallbackUseCases
                        )
                        isBound = true
                        app?.recordingManager?.markCameraBound(cameraInfo)
                        boundCameraIsFront = isFrontCamera
                        boundQuality = targetQuality
                        boundAspectRatio = targetAspectRatio
                        boundFps = targetFps
                        boundWithImageAnalysis = (imageAnalysis != null)
                        Log.d(TAG, "Successfully bound fallback camera pipeline (ImageAnalysis: ${imageAnalysis != null})")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Fallback bind failed: ${e2.message}", e2)
                        try {
                            cameraProvider.unbindAll()
                            val minimalUseCases = if (videoCapture != null) {
                                arrayOf<androidx.camera.core.UseCase>(videoCapture)
                            } else {
                                arrayOf<androidx.camera.core.UseCase>(preview)
                            }
                            activeCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                *minimalUseCases
                            )
                            isBound = true
                            app?.recordingManager?.markCameraBound(cameraInfo)
                            boundCameraIsFront = isFrontCamera
                            boundQuality = targetQuality
                            boundAspectRatio = targetAspectRatio
                            boundFps = targetFps
                            boundWithImageAnalysis = false
                        } catch (e3: Exception) {
                            Log.e(TAG, "Minimal capture bind failed: ${e3.message}", e3)
                            app?.recordingManager?.markCameraBindingFailed()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get ProcessCameraProvider: ${e.message}", e)
                app?.recordingManager?.markCameraBindingFailed()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewViewRef = this
                }
            },
            onRelease = { _ ->
                // Do not unbind if recording is actively running
                if (app?.recordingManager?.isRecordingActive != true) {
                    try {
                        cameraProviderRef?.unbindAll()
                        isBound = false
                        app?.recordingManager?.markCameraUnbound()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error unbinding on release: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(lifecycleOwner, isRecording) {
        val orientationEventListener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                if (isRecording || app?.recordingManager?.isRecordingActive == true) return
                val rotation = when (orientation) {
                    in 45..134 -> android.view.Surface.ROTATION_270
                    in 135..224 -> android.view.Surface.ROTATION_180
                    in 225..314 -> android.view.Surface.ROTATION_90
                    else -> android.view.Surface.ROTATION_0
                }
                app?.recordingManager?.updateTargetRotation(rotation)
            }
        }
        // Actively prevent hardware sensor rotation updates while recording:
        // Do NOT enable the hardware orientation listener if recording is active!
        if (!isRecording && app?.recordingManager?.isRecordingActive != true) {
            if (orientationEventListener.canDetectOrientation()) {
                orientationEventListener.enable()
            }
        }

        onDispose {
            try {
                orientationEventListener.disable()
            } catch (_: Exception) {}
        }
    }

    // Do not key camera teardown to isRecording. The old effect disposed when recording
    // changed from false to true, while CameraX was still binding Tier 1's pipeline, and
    // could unbind the very pipeline that the new recording was waiting for.
    DisposableEffect(lifecycleOwner) {
        onDispose {
            if (app?.recordingManager?.isRecordingActive != true) {
                try {
                    cameraProviderRef?.unbindAll()
                    isBound = false
                    app?.recordingManager?.markCameraUnbound()
                } catch (e: Exception) {
                    Log.w(TAG, "Error unbinding camera on lifecycle dispose: ${e.message}")
                }
            }
        }
    }
}
