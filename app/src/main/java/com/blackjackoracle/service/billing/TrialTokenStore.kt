package com.blackjackoracle.service.billing

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/// EncryptedSharedPreferences-backed store for the promo-trial JWT. The token is
/// server-verified, so plain prefs would be acceptable — encryption is just
/// defense in depth. Falls back to plain prefs if the keystore is unavailable
/// (corrupted master key on some OEM builds) so the app never crashes on launch.
///
/// NOTE: app-private storage is wiped on uninstall (unlike the iOS Keychain), so
/// a reinstalling user loses the trial and gets a fresh RevenueCat appUserID.
class TrialTokenStore(context: Context) : TrialStore {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Throwable) {
        context.getSharedPreferences("${FILE_NAME}_plain", Context.MODE_PRIVATE)
    }

    override fun getTrialToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun setTrialToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clearTrialToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val FILE_NAME = "bj_secrets"
        const val KEY_TOKEN = "trial_jwt"
    }
}
