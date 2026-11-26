package com.capji.signalpro.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.capji.signalpro.core.SonarMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Generates the sonar "ping": higher and faster as the Wi-Fi signal improves. */
class SonarPlayer(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Beeps until cancelled. [quality] is read before every beep. */
    suspend fun run(quality: () -> Int?, sound: () -> Boolean, haptics: () -> Boolean) {
        while (true) {
            val current = quality()
            if (current == null) {
                delay(500)
                continue
            }
            if (haptics()) vibrate()
            if (sound()) beep(SonarMapping.toneHz(current))
            delay(SonarMapping.intervalMs(current))
        }
    }

    private fun vibrate() {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            device.vibrate(VibrationEffect.createOneShot(22, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private suspend fun beep(frequency: Int) = withContext(Dispatchers.Default) {
        val samples = SAMPLE_RATE * DURATION_MS / 1000
        val fade = samples / 8
        val pcm = ShortArray(samples) { index ->
            // Short fade in/out avoids clicks.
            val envelope = min(1.0, min(index, samples - index) / fade.toDouble())
            (sin(2 * PI * frequency * index / SAMPLE_RATE) * envelope * Short.MAX_VALUE * 0.6)
                .toInt().toShort()
        }
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(DURATION_MS + 20L)
        } catch (_: IllegalStateException) {
            // Audio output unavailable.
        } catch (_: UnsupportedOperationException) {
            // Audio output unavailable.
        } finally {
            track?.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val DURATION_MS = 70
    }
}
