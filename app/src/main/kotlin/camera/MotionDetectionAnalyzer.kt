package io.github.roaxio.dashguardpro.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

class MotionDetectionAnalyzer(
    private val onMotionDetected: (motionScore: Float, avgLuminance: Float) -> Unit
) : ImageAnalysis.Analyzer {

    var motionSensitivityThreshold: Float = 15f // Percentage of clustered motion required
    var pixelDeltaThreshold: Int = 20           // Luma difference required per block
    var minNeighborsForCluster: Int = 2         // Minimum adjacent changing blocks to form a cluster
    var persistenceFrames: Int = 2              // Number of consecutive positive frames required to trigger
    var isEnabled: Boolean = true
        set(value) {
            if (!field && value) {
                resetTemporalHistory()
            }
            field = value
        }
    var analysisIntervalMs: Long = 220L
    var warmupFrames: Int = 3
    private var warmupFramesRemaining: Int = 3

    private var previousGridLuma: ByteArray? = null
    private var previousAvgLuma: Float = 0f
    private var lastAnalysisTimestamp = 0L
    private var consecutiveMotionCount = 0

    val gridWidth = 32
    val gridHeight = 24

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isEnabled || motionSensitivityThreshold >= 900f) {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTimestamp < analysisIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalysisTimestamp = currentTime

        val planes = imageProxy.planes
        if (planes.isEmpty()) {
            imageProxy.close()
            return
        }

        val buffer: ByteBuffer = planes[0].buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = planes[0].rowStride
        val pixelStride = planes[0].pixelStride

        val currentGrid = ByteArray(gridWidth * gridHeight)
        var totalLuma = 0L

        val stepX = width / gridWidth
        val stepY = height / gridHeight

        // Stage 1: Spatial Block Pooling (4x4 subsampling within each grid block)
        for (gy in 0 until gridHeight) {
            val startY = gy * stepY
            for (gx in 0 until gridWidth) {
                val startX = gx * stepX
                var blockLumaSum = 0
                var sampleCount = 0

                for (sy in 0 until 4) {
                    val py = startY + (sy * stepY / 4)
                    if (py >= height) continue
                    val rowOffset = py * rowStride
                    for (sx in 0 until 4) {
                        val px = startX + (sx * stepX / 4)
                        if (px >= width) continue
                        val index = rowOffset + px * pixelStride
                        if (index < buffer.remaining()) {
                            blockLumaSum += (buffer.get(index).toInt() and 0xFF)
                            sampleCount++
                        }
                    }
                }

                val avgBlockLuma = if (sampleCount > 0) (blockLumaSum / sampleCount) else 0
                currentGrid[gy * gridWidth + gx] = avgBlockLuma.toByte()
                totalLuma += avgBlockLuma
            }
        }

        val avgLuma = (totalLuma.toFloat() / (gridWidth * gridHeight))
        if (warmupFramesRemaining > 0) {
            warmupFramesRemaining--
            previousGridLuma = currentGrid.clone()
            previousAvgLuma = avgLuma
            consecutiveMotionCount = 0
            imageProxy.close()
            return
        }

        processGridInternal(currentGrid, avgLuma)

        imageProxy.close()
    }

    /**
     * Core computer vision motion processing engine.
     * Evaluates spatial clustering, global illumination compensation, and temporal persistence.
     * Exposed for direct verification and unit testing without requiring camera hardware.
     */
    fun processGridInternal(currentGrid: ByteArray, avgLuma: Float): Float {
        var motionPct = 0f
        val prevGrid = previousGridLuma

        if (prevGrid != null && prevGrid.size == currentGrid.size) {
            val totalBlocks = gridWidth * gridHeight

            // Stage 2: Global Illumination Shift Compensation
            // Cancels out camera auto-exposure hunting, passing clouds, and headlight sweeps
            val globalDelta = avgLuma - previousAvgLuma

            // Stage 3: Dynamic Noise Floor
            // In low-light / night conditions (< 50 DN), sensor noise gain is elevated
            val adaptiveDeltaThreshold = if (avgLuma < 50f) {
                pixelDeltaThreshold + 6
            } else {
                pixelDeltaThreshold
            }

            // Stage 4: Difference Detection with Global Delta Normalization
            val rawChanged = BooleanArray(totalBlocks)
            for (i in 0 until totalBlocks) {
                val curr = currentGrid[i].toInt() and 0xFF
                val prev = prevGrid[i].toInt() and 0xFF
                val normalizedDiff = abs((curr - globalDelta) - prev)
                if (normalizedDiff >= adaptiveDeltaThreshold) {
                    rawChanged[i] = true
                }
            }

            // Stage 5: 8-Neighbor Spatial Cluster Filter
            // Eliminates isolated sensor shot noise, rain streaks, and small wind tremors
            var clusteredCount = 0
            for (gy in 0 until gridHeight) {
                for (gx in 0 until gridWidth) {
                    val idx = gy * gridWidth + gx
                    if (!rawChanged[idx]) continue

                    var neighbors = 0
                    for (dy in -1..1) {
                        val ny = gy + dy
                        if (ny !in 0 until gridHeight) continue
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = gx + dx
                            if (nx !in 0 until gridWidth) continue
                            if (rawChanged[ny * gridWidth + nx]) {
                                neighbors++
                            }
                        }
                    }

                    if (neighbors >= minNeighborsForCluster) {
                        clusteredCount++
                    }
                }
            }

            motionPct = (clusteredCount.toFloat() / totalBlocks.toFloat()) * 100f

            // Stage 6: Temporal Multi-Frame Persistence Filter
            if (motionPct >= motionSensitivityThreshold) {
                consecutiveMotionCount++
                if (consecutiveMotionCount >= persistenceFrames) {
                    onMotionDetected(motionPct, avgLuma)
                }
            } else {
                consecutiveMotionCount = 0
            }
        }

        previousGridLuma = currentGrid.clone()
        previousAvgLuma = avgLuma
        return motionPct
    }

    fun resetTemporalHistory() {
        previousGridLuma = null
        previousAvgLuma = 0f
        consecutiveMotionCount = 0
        warmupFramesRemaining = warmupFrames
    }
}
