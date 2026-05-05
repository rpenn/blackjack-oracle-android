package com.blackjackoracle.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException

class TtsService(private val appContext: Context) {

    private val JSON = "application/json".toMediaType()
    private val MAX_RESPONSE_BYTES = 5_000_000L

    private val audioFocus = AudioFocusManager(appContext)

    var isSpeaking by mutableStateOf(false)
        private set

    private var player: MediaPlayer? = null
    private var pending: CompletableDeferred<Unit>? = null

    suspend fun speak(text: String) {
        stop()
        var tmp: File? = null
        try {
            val mp3 = withContext(Dispatchers.IO) { fetchAudio(text) }
            tmp = File.createTempFile("oliver", ".mp3", appContext.cacheDir).apply {
                FileOutputStream(this).use { it.write(mp3) }
            }
            val deferred = CompletableDeferred<Unit>()
            pending = deferred

            audioFocus.requestForSpeech(onFocusLost = { stop() })

            val mp = withContext(Dispatchers.IO) {
                MediaPlayer().apply {
                    setDataSource(tmp.absolutePath)
                    prepare()
                }
            }
            mp.setOnCompletionListener {
                isSpeaking = false
                audioFocus.abandon()
                pending?.complete(Unit)
                pending = null
            }
            mp.setOnErrorListener { _, _, _ ->
                isSpeaking = false
                audioFocus.abandon()
                pending?.complete(Unit)
                pending = null
                true
            }
            mp.start()
            player = mp
            isSpeaking = true
            deferred.await()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e("TtsService", "speak failed", e)
            isSpeaking = false
            audioFocus.abandon()
        } finally {
            runCatching { player?.release() }
            player = null
            tmp?.let { runCatching { it.delete() } }
        }
    }

    fun stop() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Throwable) {
        }
        player = null
        pending?.complete(Unit)
        pending = null
        isSpeaking = false
        audioFocus.abandon()
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        pending?.complete(Unit)
        pending = null
        isSpeaking = false
        audioFocus.abandon()
    }

    private suspend fun fetchAudio(text: String): ByteArray {
        val body = JSONObject().put("text", text).toString()
        val req = Request.Builder()
            .url("${HttpClient.BASE_URL}/api/tts")
            .header("Content-Type", "application/json")
            .header("Cache-Control", "no-cache")
            .post(body.toRequestBody(JSON))
            .build()

        return HttpClient.client.newCall(req).awaitResponse().use { resp ->
            if (!resp.isSuccessful) error("TTS server error ${resp.code}")
            val rspBody = resp.body ?: error("Empty TTS body")
            val cl = rspBody.contentLength()
            if (cl > MAX_RESPONSE_BYTES) error("TTS response too large ($cl)")
            val source = rspBody.source()
            source.request(MAX_RESPONSE_BYTES + 1)
            if (source.buffer.size > MAX_RESPONSE_BYTES) error("TTS response too large")
            val b64 = JSONObject(source.readUtf8()).optString("audioContent")
            if (b64.isBlank()) error("Missing audioContent")
            Base64.decode(b64, Base64.DEFAULT)
        }
    }
}
