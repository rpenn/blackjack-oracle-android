package com.blackjackoracle.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Owns transient ducking focus for TTS playback. SFX never request focus, so
 * the user's music keeps playing through gameplay.
 */
class AudioFocusManager(appContext: Context) {
    private val am = appContext.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var current: AudioFocusRequest? = null
    private var onLoss: (() -> Unit)? = null

    @Suppress("DEPRECATION")
    private val legacyListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= 0) onLoss?.invoke()
    }

    fun requestForSpeech(onFocusLost: () -> Unit): Boolean {
        abandon()
        onLoss = onFocusLost

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        onLoss?.invoke()
                    }
                }
                .build()
            current = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                legacyListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            current?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(legacyListener)
        }
        current = null
        onLoss = null
    }
}
