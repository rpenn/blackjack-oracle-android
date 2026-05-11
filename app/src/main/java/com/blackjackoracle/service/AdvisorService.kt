package com.blackjackoracle.service

import com.blackjackoracle.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AdvisorService(private val client: OkHttpClient = OkHttpClient(), private val baseUrl: String = BuildConfig.ADVISOR_BASE_URL) {
    fun advice(prompt: String): String {
        val body = JSONObject(mapOf("game" to "blackjack", "prompt" to prompt)).toString().toRequestBody("application/json".toMediaType())
        val response = client.newCall(Request.Builder().url("$baseUrl/api/advisor").post(body).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("Advisor failed: ${it.code}")
            val text = it.body?.string()?.take(1_000_000) ?: throw IOException("Empty advisor response")
            return JSONObject(text).getString("text")
        }
    }
}
