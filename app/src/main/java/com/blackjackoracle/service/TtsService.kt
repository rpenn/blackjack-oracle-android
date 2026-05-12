package com.blackjackoracle.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import com.blackjackoracle.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/// Calls the same /api/tts endpoint as iOS, decodes the base64 audio, writes it
/// to a temp file in the app cache, and plays via MediaPlayer. The temp file is
/// always cleaned up — both on normal completion and on error.
class TtsService(
    private val context: Context,
    private val client: OkHttpClient = defaultClient,
    private val baseUrl: String = BuildConfig.ADVISOR_BASE_URL,
) {
    private var player: MediaPlayer? = null
    private var currentTempFile: File? = null

    fun speak(text: String) {
        stop()
        val audio = fetchAudio(text)
        val file = writeTempFile(audio)
        currentTempFile = file
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { mp -> cleanup(mp) }
                setOnErrorListener { mp, _, _ -> cleanup(mp); true }
                prepare()
                start()
            }
        } catch (t: Throwable) {
            file.delete()
            currentTempFile = null
            throw t
        }
    }

    fun stop() {
        val active = player
        player = null
        try { active?.stop() } catch (_: Throwable) {}
        active?.release()
        currentTempFile?.delete()
        currentTempFile = null
    }

    fun release() = stop()

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
            val raw = response.body?.string()?.take(MAX_RESPONSE_BYTES)
                ?: throw IOException("Empty TTS response")
            val b64 = JSONObject(raw).getString("audioContent")
            return Base64.decode(b64, Base64.DEFAULT)
        }
    }

    private fun writeTempFile(bytes: ByteArray): File =
        File.createTempFile("oliver", ".mp3", context.cacheDir).apply {
            writeBytes(bytes)
            deleteOnExit()
        }

    private fun cleanup(mp: MediaPlayer) {
        try { mp.release() } catch (_: Throwable) {}
        if (player === mp) player = null
        currentTempFile?.delete()
        currentTempFile = null
    }

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
