package io.github.roaxio.dashguardpro.viewmodel.state

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import io.github.roaxio.dashguardpro.media.TelemetryPoint

data class ActiveSegmentData(
    val file: File,
    val startTimeMs: Long,
    var isLocked: Boolean,
    var triggerReason: String,
    var isSentry: Boolean,
    val speedSamples: MutableList<Float> = CopyOnWriteArrayList(),
    val telemetryPoints: MutableList<TelemetryPoint> = CopyOnWriteArrayList(),
    var peakSpeed: Float = 0f,
    var distanceMeters: Float = 0f,
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    var incidentLatitude: Double = 0.0,
    var incidentLongitude: Double = 0.0,
    var incidentOffsetSeconds: Int = -1,
    var incidentGForce: Float = 0f
)
