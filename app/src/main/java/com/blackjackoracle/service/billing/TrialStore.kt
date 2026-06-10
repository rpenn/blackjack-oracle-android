package com.blackjackoracle.service.billing

/// Persistence seam for the promo-trial JWT. Abstracted so EntitlementStore can
/// be unit-tested with an in-memory fake instead of EncryptedSharedPreferences.
interface TrialStore {
    fun getTrialToken(): String?
    fun setTrialToken(token: String)
    fun clearTrialToken()
}
