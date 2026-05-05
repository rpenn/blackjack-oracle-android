package com.blackjackoracle.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.blackjackoracle.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays SFX without requesting audio focus, so the user's music keeps playing.
 */
class SoundManager(context: Context) {
    private val pool: SoundPool
    private val playingCardId: Int
    private val pokerChipsId: Int
    private val foldId: Int

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()
        playingCardId = pool.load(context, R.raw.playing_card, 1)
        pokerChipsId = pool.load(context, R.raw.poker_chips, 1)
        foldId = pool.load(context, R.raw.fold, 1)
    }

    fun playDeal() {
        pool.play(playingCardId, 0.9f, 0.9f, 1, 0, 1f)
    }

    /** Three quick deal sounds (initial 2-card-per-seat blackjack deal). */
    fun playInitialDeal(seats: Int) {
        scope.launch {
            for (i in 0 until seats * 2) {
                pool.play(playingCardId, 0.85f, 0.85f, 1, 0, 1f)
                delay(110)
            }
        }
    }

    fun playHit() {
        pool.play(playingCardId, 0.95f, 0.95f, 1, 0, 1f)
        haptic(15, 50)
    }

    fun playStand() {
        // soft tick
        pool.play(playingCardId, 0.35f, 0.35f, 1, 0, 1.5f)
        haptic(15, 40)
    }

    fun playChips() {
        pool.play(pokerChipsId, 1f, 1f, 1, 0, 1f)
        haptic(25, 80)
    }

    fun playWin() {
        pool.play(pokerChipsId, 1f, 1f, 1, 0, 1f)
        haptic(50, 200)
    }

    fun playLose() {
        pool.play(foldId, 0.85f, 0.85f, 1, 0, 1f)
    }

    private fun haptic(amplitude: Int, durationMs: Long) {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: SecurityException) { /* permission denied */ }
    }

    fun release() {
        scope.cancel()
        pool.release()
    }
}
