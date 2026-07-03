package com.blackjackoracle.service.billing

import com.blackjackoracle.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/// Single source of truth for premium access. `isPremium` = a real entitlement
/// (RevenueCat) OR an active promo trial — unless a DEBUG override is set.
///
/// The JWT is never signature-verified here; the server does that. We only read
/// `exp` locally to know whether a trial is still live.
class EntitlementStore(
    private val trialStore: TrialStore,
    /// Testable clock seam (epoch seconds). Passed in so unit tests control time
    /// before `init` evaluates a persisted trial.
    internal var now: () -> Long = { System.currentTimeMillis() / 1000L },
) {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /// Set only by PurchaseManager from CustomerInfo.
    var entitled: Boolean = false
        set(value) {
            field = value
            recompute()
        }

    /// Play product id of the active paid subscription, if any. Set by
    /// PurchaseManager; used to deep-link "Manage Subscription" to the exact plan.
    var activeProductId: String? = null

    private var trialExpiryEpochSeconds: Long? = null
    private var trialToken: String? = null

    private val trialActive: Boolean
        get() = (trialExpiryEpochSeconds ?: 0L) > now()

    /// The bearer token to send during a live trial; null once expired.
    val activeTrialToken: String?
        get() = if (trialActive) trialToken else null

    /// In-memory only. Persisting a `false` override would mask a real purchase
    /// across launches (the iOS bug we're avoiding), so it is never stored.
    private var _debugOverride: Boolean? = null
    var debugOverride: Boolean?
        get() = _debugOverride
        set(value) {
            if (!BuildConfig.DEBUG) return
            _debugOverride = value
            recompute()
        }

    init {
        trialStore.getTrialToken()?.let { token ->
            val exp = decodeJwtExp(token)
            if (exp != null && exp > now()) {
                trialExpiryEpochSeconds = exp
                trialToken = token
            } else {
                trialStore.clearTrialToken()
            }
        }
        recompute()
    }

    /// Validate + persist a trial JWT. Returns false (and changes nothing) if the
    /// token is malformed or already expired.
    fun applyTrial(token: String): Boolean {
        val exp = decodeJwtExp(token) ?: return false
        if (exp <= now()) return false
        trialExpiryEpochSeconds = exp
        trialToken = token
        trialStore.setTrialToken(token)
        recompute()
        return true
    }

    /// Drop a trial whose `exp` has passed (call on resume/launch).
    fun pruneExpiredTrial() {
        if (trialExpiryEpochSeconds != null && !trialActive) {
            trialExpiryEpochSeconds = null
            trialToken = null
            trialStore.clearTrialToken()
            recompute()
        }
    }

    private fun recompute() {
        val real = entitled || trialActive
        _isPremium.value = if (BuildConfig.DEBUG) (_debugOverride ?: real) else real
    }

    private fun decodeJwtExp(token: String): Long? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = String(Base64Url.decode(parts[1]), Charsets.UTF_8)
            val obj = JSONObject(payload)
            if (obj.has("exp")) obj.getLong("exp") else null
        } catch (_: Throwable) {
            null
        }
    }
}

/// Minimal base64url decoder. Pure Kotlin so it runs in JVM unit tests and on
/// all API levels (android.util.Base64 is stubbed in unit tests; java.util.Base64
/// needs API 26 but minSdk is 24).
internal object Base64Url {
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun decode(input: String): ByteArray {
        val out = ArrayList<Byte>(input.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in input) {
            if (c == '=' || c.isWhitespace()) continue
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "Invalid base64url char: $c" }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
