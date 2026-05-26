package com.blackjackoracle.service.billing

import com.blackjackoracle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PromoCodeService {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.ADVISOR_BASE_URL

    suspend fun redeem(code: String, appUserId: String?): RedemptionResult = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("code", code)
                if (appUserId != null) put("appUserId", appUserId)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/blackjack/android/redeem-promo")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val resJson = JSONObject(responseBody)
                val token = resJson.getString("token")
                RedemptionResult.Success(token)
            } else {
                val reason = try {
                    JSONObject(responseBody).getString("reason")
                } catch (e: Exception) {
                    "unknown_error"
                }
                
                val userMessage = when (reason) {
                    "invalid_code" -> "This promo code does not exist."
                    "code_expired" -> "This promo code has expired."
                    "code_exhausted" -> "This promo code has reached its maximum redemptions."
                    "code_inactive" -> "This promo code is not currently active."
                    "bad_request" -> "Invalid request."
                    else -> "An error occurred ($reason)."
                }
                RedemptionResult.Error(userMessage)
            }
        } catch (e: Exception) {
            RedemptionResult.Error("Network error: ${e.message}")
        }
    }
}

sealed class RedemptionResult {
    data class Success(val token: String) : RedemptionResult()
    data class Error(val message: String) : RedemptionResult()
}
