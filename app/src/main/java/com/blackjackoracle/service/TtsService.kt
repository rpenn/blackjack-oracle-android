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

    /// `onStart` fires on the calling dispatcher the instant audio playback
    /// actually begins — after the network fetch and temp-file write — so the
    /// UI can show a "speaking" state that matches real sound, not the fetch.
    suspend fun speak(text: String, onStart: () -> Unit = {}) {
        stop()
        val audio = withContext(Dispatchers.IO) { fetchAudio(text) }
        val file = withContext(Dispatchers.IO) { writeTempFile(audio) }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            synchronized(lock) {
                player = mp
                currentTempFile = file
            }
            mp.start()
            onStart()
            // Drive completion off the real playback position rather than an
            // OnCompletionListener. That callback proved unreliable — it could
            // fail to fire, leaving speak() suspended past the end of the audio
            // so the "speaking" UI stayed stuck until the screen turned off.
            // Polling isPlaying returns the instant playback actually stops.
            while (isPlaying(mp)) {
                delay(POLL_INTERVAL_MS)
            }
        } finally {
            synchronized(lock) {
                if (player === mp) player = null
                if (currentTempFile === file) currentTempFile = null
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

    fun stop() {
        val active: MediaPlayer?
        val activeFile: File?
        synchronized(lock) {
            active = player
            activeFile = currentTempFile
            player = null
            currentTempFile = null
        }
        if (active != null) {
            try { active.stop() } catch (_: Throwable) {}
            try { active.release() } catch (_: Throwable) {}
        }
        activeFile?.delete()
    }

    fun release() = stop()

    private fun fetchAudio(text: String): ByteArray {
        val body = JSONObject(mapOf("text" to text))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/blackjack/android/tts")
            .post(body)
            .build()
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
