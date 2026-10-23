package io.github.roaxio.dashguardpro.media

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TelemetryPoint(
    val offsetSeconds: Int,
    val wallClockTimeMs: Long,
    val speedDisplay: Float,
    val speedUnit: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val headingDegrees: Float,
    val headingCardinal: String,
    val gForce: Float,
    val isLocked: Boolean
)

data class TelemetrySubtitleEntry(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object TelemetrySubtitleManager {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Formats a TelemetryPoint into a high-visibility, standard dashcam watermark text string
     */
    fun formatTelemetryLine(point: TelemetryPoint): String {
        val timeStr = timeFormat.format(Date(point.wallClockTimeMs))
        val speedStr = "%02d %s".format(point.speedDisplay.toInt(), point.speedUnit.uppercase())

        val coordStr = if (point.latitude != 0.0 || point.longitude != 0.0) {
            val latDir = if (point.latitude >= 0) "N" else "S"
            val lonDir = if (point.longitude >= 0) "E" else "W"
            "%.4f°%s, %.4f°%s".format(Math.abs(point.latitude), latDir, Math.abs(point.longitude), lonDir)
        } else {
            "NO GPS FIX"
        }

        val altStr = if (point.altitudeMeters != 0.0) " | ALT: ${point.altitudeMeters.toInt()}m" else ""
        val hdgStr = if (point.headingDegrees >= 0) " | ${point.headingCardinal} ${point.headingDegrees.toInt()}°" else ""
        val lockStr = if (point.isLocked) " | LOCKED" else ""

        return "$timeStr | $speedStr | $coordStr$altStr$hdgStr$lockStr"
    }

    /**
     * Formats millisecond duration into SRT timestamp string: HH:mm:ss,SSS
     */
    private fun formatSrtTimestamp(ms: Long): String {
        val hours = ms / (3600 * 1000)
        val minutes = (ms % (3600 * 1000)) / (60 * 1000)
        val seconds = (ms % (60 * 1000)) / 1000
        val millis = ms % 1000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    /**
     * Generates a standard .srt subtitle sidecar file for a video file
     */
    fun writeSubtitleFile(targetSrtFile: File, points: List<TelemetryPoint>): Boolean {
        if (points.isEmpty()) return false
        return try {
            if (targetSrtFile.exists()) {
                targetSrtFile.delete()
            }
            targetSrtFile.parentFile?.mkdirs()

            targetSrtFile.bufferedWriter().use { writer ->
                for (i in points.indices) {
                    val p = points[i]
                    val startMs = p.offsetSeconds.toLong() * 1000L
                    val nextStartMs = if (i + 1 < points.size) {
                        points[i + 1].offsetSeconds.toLong() * 1000L
                    } else {
                        startMs + 1000L
                    }
                    val endMs = nextStartMs.coerceAtLeast(startMs + 500L)

                    writer.write("${i + 1}\n")
                    writer.write("${formatSrtTimestamp(startMs)} --> ${formatSrtTimestamp(endMs)}\n")
                    writer.write("${formatTelemetryLine(p)}\n\n")
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parses an existing .srt subtitle file into memory for synchronized playback HUD
     */
    fun parseSubtitleFile(srtFile: File): List<TelemetrySubtitleEntry> {
        if (!srtFile.exists() || !srtFile.canRead()) return emptyList()
        val entries = mutableListOf<TelemetrySubtitleEntry>()

        try {
            val lines = srtFile.readLines()
            var i = 0
            while (i < lines.size) {
                val indexLine = lines[i].trim()
                if (indexLine.isBlank()) {
                    i++
                    continue
                }
                val index = indexLine.toIntOrNull()
                if (index == null) {
                    i++
                    continue
                }

                if (i + 1 < lines.size) {
                    val timeLine = lines[i + 1].trim()
                    val timeParts = timeLine.split("-->").map { it.trim() }
                    if (timeParts.size == 2) {
                        val startMs = parseSrtTimestamp(timeParts[0])
                        val endMs = parseSrtTimestamp(timeParts[1])

                        val textBuilder = StringBuilder()
                        var lineOffset = 2
                        while (i + lineOffset < lines.size && lines[i + lineOffset].isNotBlank()) {
                            if (textBuilder.isNotEmpty()) textBuilder.append(" ")
                            textBuilder.append(lines[i + lineOffset].trim())
                            lineOffset++
                        }

                        if (textBuilder.isNotEmpty()) {
                            entries.add(
                                TelemetrySubtitleEntry(
                                    index = index,
                                    startMs = startMs,
                                    endMs = endMs,
                                    text = textBuilder.toString()
                                )
                            )
                        }
                        i += lineOffset
                        continue
                    }
                }
                i++
            }
        } catch (_: Exception) {}

        return entries
    }

    private fun parseSrtTimestamp(str: String): Long {
        return try {
            val parts = str.split(":")
            val hours = parts[0].trim().toLong()
            val minutes = parts[1].trim().toLong()
            val secParts = parts[2].trim().replace(".", ",").split(",")
            val seconds = secParts[0].trim().toLong()
            val millis = secParts.getOrNull(1)?.trim()?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
            (hours * 3600 + minutes * 60 + seconds) * 1000L + millis
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Looks up the telemetry watermark string for a given playback position in milliseconds
     */
    fun getTelemetryForPosition(entries: List<TelemetrySubtitleEntry>, positionMs: Long): String? {
        if (entries.isEmpty()) return null
        val match = entries.find { positionMs in it.startMs..it.endMs }
        return match?.text ?: entries.lastOrNull { positionMs >= it.startMs }?.text ?: entries.firstOrNull()?.text
    }

    fun getTelemetryForPosition(entries: List<TelemetrySubtitleEntry>, positionMs: Int): String? {
        return getTelemetryForPosition(entries, positionMs.toLong())
    }
}
