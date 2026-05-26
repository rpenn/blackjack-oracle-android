package com.blackjackoracle.service.billing

import com.blackjackoracle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface RedeemResult {
    data class Success(val token: String, val trialDays: Int) : RedeemResult
    data class Failure(val message: String) : RedeemResult
}

/// Redeems a promo code against the shared backend (game-parameterized; the
/// `blackjack/android` route already works). Returns a trial JWT on success.
class PromoCodeService(
    private val client: OkHttpClient = defaultClient,
    private val baseUrl: String = BuildConfig.ADVISOR_BASE_URL,
) {
    suspend fun redeem(code: String, appUserId: String?): RedeemResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("code", code)
            if (appUserId != null) put("appUserId", appUserId)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/blackjack/android/redeem-promo")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (response.isSuccessful && json != null && json.has("token")) {
                    RedeemResult.Success(
                        token = json.getString("token"),
                        trialDays = json.optInt("trialDays", 0),
                    )
                } else {
                    RedeemResult.Failure(messageFor(json?.optString("reason").orEmpty()))
                }
            }
        } catch (_: Throwable) {
            RedeemResult.Failure("Network error. Please try again.")
        }
    }

    private fun messageFor(reason: String): String = when (reason) {
        "invalid_code" -> "That code isn't valid."
        "code_expired" -> "That code has expired."
        "code_exhausted" -> "That code has been fully redeemed."
        "code_inactive" -> "That code isn't active yet."
        "bad_request" -> "Please enter a valid code."
        else -> "Couldn't redeem that code."
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
