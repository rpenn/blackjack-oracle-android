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
    fun advice(prompt: String, authToken: String? = null): String {
        val body = JSONObject(mapOf("prompt" to prompt))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url("$baseUrl/api/blackjack/android/advisor")
            .post(body)
        if (authToken != null) builder.header("Authorization", "Bearer $authToken")
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Advisor failed: ${response.code}")
            // Cap the read at the okio source level — `.string()` buffers the
            // entire body before truncation, which is the bug we're avoiding.
            val source = (response.body ?: throw IOException("Empty advisor response")).source()
            source.request(MAX_RESPONSE_BYTES.toLong())
            val raw = source.readUtf8(minOf(source.buffer.size, MAX_RESPONSE_BYTES.toLong()))
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
