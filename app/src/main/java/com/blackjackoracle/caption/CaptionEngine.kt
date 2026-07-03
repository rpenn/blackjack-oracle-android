package com.blackjackoracle.caption

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.BreakIterator
import java.util.Locale

/// Drives the on-screen transcript so it tracks Oliver's speech 1:1. Splits the
/// advice into sentences, then advances a "currently spoken" index against the
/// audio playhead (or a self-paced reading clock in caption-only mode). All
/// observable fields are Compose `mutableStateOf` so the CaptionCard recomposes.
///
/// Port of the iOS `CaptionEngine`. Key platform difference: the Android
/// `TtsService` is not a singleton, so the audio playhead is injected via
/// [setPlayhead] rather than polled from a global.
class CaptionEngine(private val scope: CoroutineScope) {
    var sentences by mutableStateOf<List<String>>(emptyList()); private set
    var text by mutableStateOf(""); private set          // full advice, for replay
    var currentIndex by mutableStateOf(0); private set
    var progress by mutableStateOf(0.0); private set       // 0..1, drives the hairline bar
    var isActive by mutableStateOf(false); private set     // begin()..dismiss; stays true after finish
    var isFinished by mutableStateOf(false); private set
    var isPaused by mutableStateOf(false); private set     // reading-pace pause (caption-only)

    /// Returns (currentPositionMs, durationMs); durationMs<=0 means "not ready".
    private var playhead: () -> Pair<Long, Long> = { 0L to 0L }

    private var boundaries: List<Double> = emptyList()
    private var tickJob: Job? = null
    private var readElapsed = 0.0
    private var readDuration = 1.0

    fun setPlayhead(p: () -> Pair<Long, Long>) { playhead = p }

    fun begin(text: String, spoken: Boolean) {
        cancel()
        this.text = text
        sentences = splitSentences(text)
        if (sentences.isEmpty()) return
        // Character-count boundaries: each sentence occupies a fraction of the
        // whole proportional to its length, so a longer sentence dwells longer.
        val counts = sentences.map { maxOf(it.length, 1).toDouble() }
        val total = counts.sum()
        var acc = 0.0
        boundaries = counts.map { acc += it; acc / total }
        isActive = true
        if (spoken) {
            startTicking(audio = true)
        } else {
            readDuration = maxOf(3.0, total / READING_CPS)
            startTicking(audio = false)
        }
    }

    /// Snap to the end without collapsing — the card stays for reference
    /// scrolling (and its replay button) until explicitly dismissed.
    fun finish() {
        if (!isActive || isFinished) return
        tickJob?.cancel(); tickJob = null
        currentIndex = (sentences.size - 1).coerceAtLeast(0)
        progress = 1.0
        isFinished = true
    }

    fun cancel() {
        tickJob?.cancel(); tickJob = null
        sentences = emptyList(); text = ""; boundaries = emptyList()
        currentIndex = 0; progress = 0.0; readElapsed = 0.0
        isActive = false; isFinished = false; isPaused = false
    }

    fun togglePause() { isPaused = !isPaused }

    private fun startTicking(audio: Boolean) {
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (audio) tickAudio() else tickReading()
            }
        }
    }

    private fun tickAudio() {
        val (pos, dur) = playhead()
        if (dur <= 0) return   // MediaPlayer not prepared yet
        apply(pos.toDouble() / dur.toDouble())
    }

    private fun tickReading() {
        if (isPaused) return
        readElapsed += TICK_MS / 1000.0
        if (readElapsed >= readDuration) finish() else apply(readElapsed / readDuration)
    }

    private fun apply(fraction: Double) {
        progress = fraction.coerceIn(0.0, 1.0)
        while (currentIndex < sentences.size - 1 && progress > boundaries[currentIndex]) currentIndex++
    }

    private fun splitSentences(t: String): List<String> {
        val it = BreakIterator.getSentenceInstance(Locale.ENGLISH)
        it.setText(t)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            t.substring(start, end).trim().takeIf { s -> s.isNotEmpty() }?.let(out::add)
            start = end; end = it.next()
        }
        return out.ifEmpty { t.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList() }
    }

    companion object {
        private const val TICK_MS = 125L        // ~8 polls/sec
        private const val READING_CPS = 14.0    // ~170 wpm, slow end of TTS
    }
}
