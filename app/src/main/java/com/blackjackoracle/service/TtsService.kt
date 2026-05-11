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

class TtsService(private val context: Context, private val client: OkHttpClient = OkHttpClient(), private val baseUrl: String = BuildConfig.ADVISOR_BASE_URL) {
    private var player: MediaPlayer? = null
    fun stop() { player?.stop(); player?.release(); player = null }
    fun speak(text: String) {
        stop()
        val body = JSONObject(mapOf("text" to text)).toString().toRequestBody("application/json".toMediaType())
        val response = client.newCall(Request.Builder().url("$baseUrl/api/tts").post(body).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("TTS failed: ${it.code}")
            val json = JSONObject(it.body?.string()?.take(5_000_000) ?: throw IOException("Empty TTS response"))
            val bytes = Base64.decode(json.getString("audioContent"), Base64.DEFAULT)
            val file = File.createTempFile("oliver", ".mp3", context.cacheDir).apply { writeBytes(bytes); deleteOnExit() }
            player = MediaPlayer().apply { setDataSource(file.absolutePath); setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }; prepare(); start() }
        }
    }
    fun release() = stop()
}
