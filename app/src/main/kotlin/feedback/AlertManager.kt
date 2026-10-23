package io.github.roaxio.dashguardpro.feedback

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.camera.core.CameraControl
import io.github.roaxio.dashguardpro.data.settings.SentryDeterrentMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class AlertManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var sirenJob: Job? = null
    private var strobeJob: Job? = null

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var backCameraId: String? = null
    private var cameraControl: CameraControl? = null

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false
    @Volatile
    private var pendingSpeakText: Pair<String, Boolean>? = null

    init {
        try {
            backCameraId = cameraManager?.cameraIdList?.firstOrNull()
        } catch (_: Exception) {}

        try {
            android.util.Log.d("AlertManager", "Initializing TextToSpeech engine...")
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    var langResult = tts?.setLanguage(Locale.US)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        langResult = tts?.setLanguage(Locale.getDefault())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tts?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                    }
                    ttsReady = true
                    android.util.Log.d("AlertManager", "TextToSpeech initialized successfully (langResult: $langResult).")
                    pendingSpeakText?.let { (text, enabled) ->
                        pendingSpeakText = null
                        speak(text, enabled)
                    }
                } else {
                    android.util.Log.e("AlertManager", "TextToSpeech init failed with status: $status")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AlertManager", "Failed to create TextToSpeech: ${e.message}", e)
        }
    }

    fun setCameraControl(control: CameraControl?) {
        this.cameraControl = control
    }

    fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    fun vibrateImpactPattern() {
        try {
            val timings = longArrayOf(0, 200, 100, 300, 100, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }

    fun playImpactBeep() {
        scope.launch(Dispatchers.IO) {
            try {
                val resId = context.resources.getIdentifier("impact_alert", "raw", context.packageName)
                if (resId != 0) {
                    val mediaPlayer = MediaPlayer.create(context, resId)
                    mediaPlayer?.apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setVolume(1.0f, 1.0f)
                        setOnCompletionListener { mp -> mp.release() }
                        start()
                    }
                    return@launch
                }
            } catch (e: Throwable) {
                android.util.Log.w("AlertManager", "MediaPlayer failed for impact beep: ${e.message}")
            }
            playSynthesizedBeep(frequencyHz = 750.0, durationMs = 800)
        }
    }

    fun playRecordStartSound() {
        scope.launch(Dispatchers.IO) {
            try {
                val resId = context.resources.getIdentifier("record_start", "raw", context.packageName)
                if (resId != 0) {
                    val mediaPlayer = MediaPlayer.create(context, resId)
                    mediaPlayer?.apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setVolume(0.85f, 0.85f)
                        setOnCompletionListener { mp -> mp.release() }
                        start()
                    }
                    return@launch
                }
            } catch (e: Throwable) {
                android.util.Log.w("AlertManager", "MediaPlayer failed for record_start: ${e.message}")
            }
            playSynthesizedBeep(frequencyHz = 600.0, durationMs = 80)
        }
    }

    fun playRecordStopSound() {
        scope.launch(Dispatchers.IO) {
            try {
                val resId = context.resources.getIdentifier("record_stop", "raw", context.packageName)
                if (resId != 0) {
                    val mediaPlayer = MediaPlayer.create(context, resId)
                    mediaPlayer?.apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setVolume(0.85f, 0.85f)
                        setOnCompletionListener { mp -> mp.release() }
                        start()
                    }
                    return@launch
                }
            } catch (e: Throwable) {
                android.util.Log.w("AlertManager", "MediaPlayer failed for record_stop: ${e.message}")
            }
            playSynthesizedBeep(frequencyHz = 500.0, durationMs = 80)
        }
    }

    fun playLockSound() {
        scope.launch(Dispatchers.IO) {
            try {
                val resId = context.resources.getIdentifier("clip_lock", "raw", context.packageName)
                if (resId != 0) {
                    val mediaPlayer = MediaPlayer.create(context, resId)
                    mediaPlayer?.apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setVolume(1.0f, 1.0f)
                        setOnCompletionListener { mp -> mp.release() }
                        start()
                    }
                    return@launch
                }
            } catch (e: Throwable) {
                android.util.Log.w("AlertManager", "MediaPlayer failed for clip_lock: ${e.message}")
            }
            playSynthesizedBeep(frequencyHz = 880.0, durationMs = 200)
        }
    }

    fun playChime() {
        scope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("AlertManager", "playChime invoked")
                val resId = context.resources.getIdentifier("dashcam_chime", "raw", context.packageName)
                if (resId != 0) {
                    val mediaPlayer = MediaPlayer.create(context, resId)
                    mediaPlayer?.apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setVolume(1.0f, 1.0f)
                        setOnCompletionListener { mp -> mp.release() }
                        start()
                    }
                    return@launch
                }
            } catch (e: Throwable) {
                android.util.Log.w("AlertManager", "MediaPlayer failed for chime: ${e.message}")
            }
            playSynthesizedBeep(frequencyHz = 1175.0, durationMs = 200)
        }
    }

    private fun playSynthesizedBeep(frequencyHz: Double, durationMs: Long) {
        try {
            val sampleRate = 44100
            val numSamples = ((durationMs / 1000.0) * sampleRate).toInt()
            val samples = ShortArray(numSamples)
            val fadeSamples = (sampleRate * 0.015).toInt() // 15ms anti-click envelope
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val raw = Math.sin(2.0 * Math.PI * frequencyHz * t)
                val envelope = when {
                    i < fadeSamples -> i.toDouble() / fadeSamples
                    i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                    else -> 1.0
                }
                samples[i] = (raw * Short.MAX_VALUE * 0.85 * envelope).toInt().toShort()
            }
            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.size * 2,
                    AudioTrack.MODE_STATIC
                )
            }
            audioTrack.write(samples, 0, samples.size)
            audioTrack.setVolume(1.0f)
            audioTrack.play()
            Thread.sleep(durationMs + 250L)
            audioTrack.release()
            android.util.Log.d("AlertManager", "Synthesized beep played successfully (${frequencyHz}Hz, ${durationMs}ms)")
        } catch (e: Exception) {
            android.util.Log.e("AlertManager", "Synthesized beep failed: ${e.message}")
        }
    }

    fun playThermalCutoffAlert() {
        scope.launch {
            try {
                val resId = context.resources.getIdentifier("thermal_cutoff_alert", "raw", context.packageName)
                if (resId != 0) {
                    val mediaPlayer = android.media.MediaPlayer.create(context, resId)
                    mediaPlayer?.apply {
                        setOnCompletionListener { mp -> mp.release() }
                        start()
                    }
                } else {
                    val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                    toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500)
                    delay(1600)
                    toneGen.release()
                }
            } catch (e: Exception) {
                android.util.Log.w("AlertManager", "Failed to play thermal cutoff alert: ${e.message}")
            }
        }
    }

    fun playLowBatteryCutoffAlert(voiceEnabled: Boolean = true) {
        speak("Battery safety cutoff reached. Entering sleep mode.", voiceEnabled)
    }

    fun startSirenDeterrent() {
        stopSirenDeterrent()
        sirenJob = scope.launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                repeat(10) {
                    if (!isActive) return@repeat
                    toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 600)
                    delay(700)
                }
                toneGen.release()
            } catch (_: Exception) {}
        }
    }

    fun stopSirenDeterrent() {
        sirenJob?.cancel()
        sirenJob = null
    }

    fun startStrobeFlash() {
        stopStrobeFlash()
        strobeJob = scope.launch {
            try {
                var on = false
                repeat(12) {
                    if (!isActive) return@repeat
                    on = !on
                    setTorch(on)
                    delay(150)
                }
                setTorch(false)
            } catch (_: Exception) {}
        }
    }

    fun stopStrobeFlash() {
        strobeJob?.cancel()
        strobeJob = null
        try {
            setTorch(false)
        } catch (_: Exception) {}
    }

    fun setTorch(enable: Boolean) {
        cameraControl?.let {
            try {
                it.enableTorch(enable)
                return
            } catch (_: Exception) {}
        }
        val camId = backCameraId ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager?.setTorchMode(camId, enable)
            }
        } catch (_: Exception) {}
    }

    fun triggerDeterrent(mode: SentryDeterrentMode) {
        when (mode) {
            SentryDeterrentMode.NONE -> {}
            SentryDeterrentMode.TORCH_STROBE -> startStrobeFlash()
            SentryDeterrentMode.SIREN_CHIME -> startSirenDeterrent()
            SentryDeterrentMode.BOTH -> {
                startStrobeFlash()
                startSirenDeterrent()
            }
        }
    }

    fun speak(text: String, enabled: Boolean = true) {
        if (!enabled) return
        if (!ttsReady) {
            android.util.Log.w("AlertManager", "TextToSpeech not ready yet, queuing pending speech: '$text'")
            pendingSpeakText = Pair(text, enabled)
            return
        }
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dashguard_tts_${System.currentTimeMillis()}")
            } else {
                @Suppress("DEPRECATION")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
            android.util.Log.d("AlertManager", "tts.speak('$text') -> result: $result")
        } catch (e: Exception) {
            android.util.Log.e("AlertManager", "tts.speak failed: ${e.message}", e)
        }
    }

    fun speakSentryArming(seconds: Int, enabled: Boolean = true) {
        speak("Sentry arming in $seconds seconds", enabled)
    }

    fun speakSentryActive(enabled: Boolean = true) {
        speak("Sentry active", enabled)
    }

    fun speakSentryDisarmed(incidentCount: Int = 0, enabled: Boolean = true) {
        val text = if (incidentCount > 0) {
            val plural = if (incidentCount == 1) "incident" else "incidents"
            "Sentry disarmed. $incidentCount $plural recorded"
        } else {
            "Sentry disarmed"
        }
        speak(text, enabled)
    }

    fun speakThermalCoolingResumed(enabled: Boolean = true) {
        speak("Cooling complete. Dashcam recording resumed", enabled)
    }

    fun release() {
        stopSirenDeterrent()
        stopStrobeFlash()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
