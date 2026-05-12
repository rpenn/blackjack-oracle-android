package com.blackjackoracle.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import com.blackjackoracle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/// Calls the same /api/tts endpoint as iOS, decodes the base64 audio, writes it
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

    suspend fun speak(text: String) {
        stop()
        val audio = withContext(Dispatchers.IO) { fetchAudio(text) }
        val file = withContext(Dispatchers.IO) { writeTempFile(audio) }
        try {
            playUntilDone(file)
        } finally {
            // Belt-and-suspenders: cleanupPlayer normally deletes the file when
            // playback ends or is cancelled; this catches the path where the
            // coroutine unwinds before playUntilDone registers its handlers.
            file.delete()
        }
    }

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

    private suspend fun playUntilDone(file: File): Unit = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                cleanupPlayer(mp, file)
                if (cont.isActive) cont.resume(Unit)
            }
            mp.setOnErrorListener { _, what, extra ->
                cleanupPlayer(mp, file)
                if (cont.isActive) cont.resumeWithException(IOException("MediaPlayer error $what/$extra"))
                true
            }
            mp.prepare()
            synchronized(lock) {
                player = mp
                currentTempFile = file
            }
            cont.invokeOnCancellation { cleanupPlayer(mp, file) }
            mp.start()
        } catch (t: Throwable) {
            cleanupPlayer(mp, file)
            throw t
        }
    }

    /// Cleanup gated on player identity AND file identity — a stale callback
    /// from a superseded MediaPlayer must not null out the new player or
    /// delete the new temp file.
    private fun cleanupPlayer(mp: MediaPlayer, file: File) {
        try { mp.stop() } catch (_: Throwable) {}
        try { mp.release() } catch (_: Throwable) {}
        synchronized(lock) {
            if (player === mp) player = null
            if (currentTempFile === file) currentTempFile = null
        }
        file.delete()
    }

    private fun fetchAudio(text: String): ByteArray {
        val body = JSONObject(mapOf("text" to text))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/tts")
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
