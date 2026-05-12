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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SoundManager(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val dealSound: Int = pool.load(context, R.raw.playing_card, 1)
    private val chipsSound: Int = pool.load(context, R.raw.poker_chips, 1)
    private val foldSound: Int = pool.load(context, R.raw.fold, 1)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun playDeal() {
        pool.play(dealSound, 1f, 1f, 1, 0, 1f)
    }

    fun playInitialDeal(scope: CoroutineScope, cards: Int) {
        repeat(cards) { i ->
            scope.launch {
                delay(i * DEAL_STAGGER_MS)
                playDeal()
            }
        }
    }

    fun playHit() {
        playDeal(); buzz(18)
    }

    fun playStand() {
        playDeal(); buzz(12)
    }

    fun playChips() {
        pool.play(chipsSound, 1f, 1f, 1, 0, 1f)
        buzz(28)
    }

    fun playWin() {
        playChips()
    }

    fun playLose() {
        pool.play(foldSound, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    private fun buzz(ms: Long) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    companion object {
        private const val MAX_STREAMS = 6
        private const val DEAL_STAGGER_MS = 140L
    }
}
