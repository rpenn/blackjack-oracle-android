package com.blackjackoracle.service

import com.blackjackoracle.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AdvisorService(
    private val client: OkHttpClient = defaultClient,
    private val baseUrl: String = BuildConfig.ADVISOR_BASE_URL,
) {
    fun advice(prompt: String): String {
        val body = JSONObject(mapOf("game" to "blackjack", "prompt" to prompt))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/advisor")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Advisor failed: ${response.code}")
            val raw = response.body?.string()?.take(MAX_RESPONSE_BYTES)
                ?: throw IOException("Empty advisor response")
            return JSONObject(raw).getString("text")
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 1_000_000
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
