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

class SoundManager(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val deal = pool.load(context, R.raw.playing_card, 1)
    private val chips = pool.load(context, R.raw.poker_chips, 1)
    private val fold = pool.load(context, R.raw.fold, 1)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else context.getSystemService(Vibrator::class.java)
    fun playDeal() { pool.play(deal, 1f, 1f, 1, 0, 1f) }
    fun playInitialDeal(scope: CoroutineScope, cards: Int) { repeat(cards) { i -> scope.launch { delay(i * 140L); playDeal() } } }
    fun playHit() { playDeal(); buzz(18) }
    fun playStand() { playDeal(); buzz(12) }
    fun playChips() { pool.play(chips, 1f, 1f, 1, 0, 1f); buzz(28) }
    fun playWin() { playChips() }
    fun playLose() { pool.play(fold, 1f, 1f, 1, 0, 1f) }
    private fun buzz(ms: Long) { if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator?.vibrate(ms) }
    fun release() = pool.release()
}
