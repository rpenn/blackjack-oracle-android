package com.blackjackoracle.service.billing

import com.blackjackoracle.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import android.util.Base64

class EntitlementStore(private val trialTokenStore: TrialTokenStore) {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    var entitled: Boolean = false
        set(value) {
            field = value
            recompute()
        }

    var trialActive: Boolean = false
        private set

    var activeTrialToken: String? = null
        private set
        
    var debugOverride: Boolean? = null
        set(value) {
            field = value
            recompute()
        }

    init {
        restoreTrial()
    }

    private fun recompute() {
        if (BuildConfig.DEBUG && debugOverride != null) {
            _isPremium.value = debugOverride!!
        } else {
            _isPremium.value = entitled || trialActive
        }
    }

    private fun restoreTrial() {
        val token = trialTokenStore.trialToken
        if (token != null) {
            applyTrial(token)
        } else {
            trialActive = false
            activeTrialToken = null
            recompute()
        }
    }

    fun applyTrial(token: String) {
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
                val json = JSONObject(payload)
                val exp = json.optLong("exp", 0)
                val now = System.currentTimeMillis() / 1000
                if (exp > now) {
                    trialTokenStore.trialToken = token
                    trialActive = true
                    activeTrialToken = token
                    recompute()
                    return
                }
            }
        } catch (e: Exception) {
            // malformed
        }
        
        pruneExpiredTrial()
    }

    fun pruneExpiredTrial() {
        trialTokenStore.trialToken = null
        trialActive = false
        activeTrialToken = null
        recompute()
    }
}
