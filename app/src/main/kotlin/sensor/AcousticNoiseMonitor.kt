package io.github.roaxio.dashguardpro.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

class AcousticNoiseMonitor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _currentDb = MutableStateFlow(0f)
    val currentDb: StateFlow<Float> = _currentDb.asStateFlow()

    val isMonitoring: Boolean
        get() = recordingJob?.isActive == true

    var onAcousticAlert: ((Float) -> Unit)? = null
    var sensitivity: Sensitivity = Sensitivity.MEDIUM

    private var lastAlertTimestamp = 0L
    private val alertCooldownMs = 5000L

    @Synchronized
    fun startMonitoring() {
        if (recordingJob?.isActive == true || audioRecord != null) {
            return
        }

        if (sensitivity == Sensitivity.OFF) {
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, sampleRate / 5 * 2) // ~200ms buffer

            @SuppressLint("MissingPermission")
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return
            }

            record.startRecording()
            audioRecord = record

            // Give a 1.5s settling buffer upon mic startup to prevent mic click / pop from triggering a false alert
            lastAlertTimestamp = System.currentTimeMillis() - alertCooldownMs + 1500L

            recordingJob = scope.launch {
                val shortBuffer = ShortArray(bufferSize / 2)
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (readCount > 0) {
                        val db = calculateDbFromSamples(shortBuffer, readCount)
                        _currentDb.value = db

                        val thresholdDb = getThresholdForSensitivity(sensitivity)

                        val now = System.currentTimeMillis()
                        if (db >= thresholdDb && (now - lastAlertTimestamp > alertCooldownMs)) {
                            lastAlertTimestamp = now
                            onAcousticAlert?.invoke(db)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            stopAndRelease()
        }
    }

    @Synchronized
    fun stopAndRelease() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {
        } finally {
            audioRecord = null
            _currentDb.value = 0f
        }
    }

    companion object {
        fun calculateDbFromSamples(samples: ShortArray, count: Int): Float {
            if (count <= 0) return 0f
            var sum = 0.0
            for (i in 0 until count) {
                val sample = samples[i].toDouble()
                sum += sample * sample
            }
            val rms = sqrt(sum / count)
            return if (rms > 0.0) {
                (20.0 * log10(rms)).toFloat().coerceIn(0f, 100f)
            } else {
                0f
            }
        }

        fun getThresholdForSensitivity(sensitivity: Sensitivity): Float {
            return when (sensitivity) {
                Sensitivity.VERY_HIGH -> 60f
                Sensitivity.HIGH -> 65f
                Sensitivity.MEDIUM -> 75f
                Sensitivity.LOW -> 85f
                Sensitivity.VERY_LOW -> 90f
                Sensitivity.OFF -> 999f
            }
        }
    }
}
