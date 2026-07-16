package com.blackjackoracle.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import com.blackjackoracle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/// Calls the /api/blackjack/android/tts endpoint, decodes the base64 audio, writes it
/// to a temp file in the app cache, and plays via MediaPlayer.
///
/// `speak` is a `suspend` function that resumes only when playback finishes or
/// is cancelled. Temp files and MediaPlayer references are guarded by `lock`
/// and identity-checked on cleanup so that a stale `OnCompletion` from a
/// superseded player can't delete the current file out from under a newer one.
class TtsService(
    private val context: Context,
    private val client: OkHttpClient = defaultClient,
    private val baseUrl: String = BuildConfig.ADVISOR_BASE_URL,
) {
    private val lock = Any()
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var currentTempFile: File? = null
    @Volatile private var paused = false

    /**
     * Demo-capture only (debug). When set, [speak] skips the network round-trip
     * and just drives the speaking state for a realistic duration, simulating the
     * playhead so captions track — no network, no audio. Ideal for muted Play
     * Store capture. Always false in release (only the debug demo driver sets it).
     */
    var demoOffline: Boolean = false
    @Volatile private var demoSpeechStartMs = 0L
    @Volatile private var demoSpeechDurationMs = 0L

    /// True while audio is actively playing (false while paused or idle). Read by
    /// the ViewModel to decide whether a caption "pause" targets the MediaPlayer
    /// (spoken mode) or the reading clock (caption-only mode).
    val isSpeaking: Boolean
        get() = synchronized(lock) { runCatching { player?.isPlaying == true }.getOrDefault(false) }
    val isPaused: Boolean get() = paused

    /// Playhead for CaptionEngine. Guarded against the IllegalState that
    /// currentPosition/duration throw once the player has been torn down.
    /// duration is -1 until prepare() completes; the engine treats <=0 as "wait".
    fun currentPositionMs(): Long {
        val demoDur = demoSpeechDurationMs
        if (demoDur > 0) return (System.currentTimeMillis() - demoSpeechStartMs).coerceIn(0L, demoDur)
        return synchronized(lock) { runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L }
    }
    fun durationMs(): Long {
        val demoDur = demoSpeechDurationMs
        if (demoDur > 0) return demoDur
        return synchronized(lock) { runCatching { player?.duration?.toLong() }.getOrNull() ?: 0L }
    }

    fun pause() = synchronized(lock) {
        player?.takeIf { runCatching { it.isPlaying }.getOrDefault(false) }?.let { it.pause(); paused = true }
    }

    fun resume() = synchronized(lock) {
        if (paused) { paused = false; runCatching { player?.start() } }
    }

    /// `onStart` fires on the calling dispatcher the instant audio playback
    /// actually begins — after the network fetch and temp-file write — so the
    /// UI can show a "speaking" state that matches real sound, not the fetch.
    suspend fun speak(text: String, authToken: String? = null, onStart: () -> Unit = {}) {
        if (demoOffline) {
            speakOffline(text, onStart)
            return
        }
        stop()
        val audio = withContext(Dispatchers.IO) { fetchAudio(text, authToken) }
        val file = withContext(Dispatchers.IO) { writeTempFile(audio) }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            synchronized(lock) {
                player = mp
                currentTempFile = file
            }
            paused = false
            mp.start()
            onStart()
            // Drive completion off the real playback position rather than an
            // OnCompletionListener. That callback proved unreliable — it could
            // fail to fire, leaving speak() suspended past the end of the audio
            // so the "speaking" UI stayed stuck until the screen turned off.
            // Polling isPlaying returns the instant playback actually stops.
            //
            // `paused` keeps us suspended across a caption-driven pause: a paused
            // MediaPlayer reports isPlaying=false, which would otherwise let this
            // loop fall through and tear the player down mid-utterance. stop()
            // clears `paused` so a real stop still ends the loop.
            while (isPlaying(mp) || paused) {
                delay(POLL_INTERVAL_MS)
            }
        } finally {
            synchronized(lock) {
                if (player === mp) player = null
                if (currentTempFile === file) currentTempFile = null
                paused = false
            }
            try { mp.stop() } catch (_: Throwable) {}
            try { mp.release() } catch (_: Throwable) {}
            file.delete()
        }
    }

    /// Guarded because isPlaying throws IllegalStateException on a player that
    /// stop() already released out from under us (the pause/replace path).
    private fun isPlaying(mp: MediaPlayer): Boolean =
        try { mp.isPlaying } catch (_: Throwable) { false }

    /**
     * Offline stand-in for [speak]: paces the speaking UI (and a simulated
     * playhead for the caption engine) without audio or network.
     */
    private suspend fun speakOffline(text: String, onStart: () -> Unit) {
        stop()
        // Roughly track real speech pacing so the bars run a believable length.
        val ms = (text.length * 55L).coerceIn(4_000L, 9_000L)
        demoSpeechStartMs = System.currentTimeMillis()
        demoSpeechDurationMs = ms
        onStart()
        try {
            delay(ms)
        } finally {
            demoSpeechDurationMs = 0L
        }
    }

    fun stop() {
        demoSpeechDurationMs = 0L
        val active: MediaPlayer?
        val activeFile: File?
        synchronized(lock) {
            active = player
            activeFile = currentTempFile
            player = null
            currentTempFile = null
            paused = false
        }
        if (active != null) {
            try { active.stop() } catch (_: Throwable) {}
            try { active.release() } catch (_: Throwable) {}
        }
        activeFile?.delete()
    }

    fun release() = stop()

    private fun fetchAudio(text: String, authToken: String?): ByteArray {
        val body = JSONObject(mapOf("text" to text))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url("$baseUrl/api/blackjack/android/tts")
            .post(body)
        if (authToken != null) builder.header("Authorization", "Bearer $authToken")
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TTS failed: ${response.code}")
            val source = (response.body ?: throw IOException("Empty TTS response")).source()
            source.request(MAX_RESPONSE_BYTES.toLong())
            val raw = source.readUtf8(minOf(source.buffer.size, MAX_RESPONSE_BYTES.toLong()))
            val b64 = JSONObject(raw).getString("audioContent")
            return Base64.decode(b64, Base64.DEFAULT)
        }
    }

    private fun writeTempFile(bytes: ByteArray): File =
        File.createTempFile("oliver", ".mp3", context.cacheDir).apply { writeBytes(bytes) }

    companion object {
        private const val POLL_INTERVAL_MS = 120L
        private const val MAX_RESPONSE_BYTES = 5_000_000
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
