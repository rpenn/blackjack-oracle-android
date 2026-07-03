package com.blackjackoracle.data

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.captionDataStore: DataStore<Preferences> by preferencesDataStore("caption_prefs")

/// Two persisted booleans driving the captions feature, plus a one-time seed of
/// the "show captions" default from the OS closed-captioning switch. Android
/// analog of the iOS `CaptionPrefs` (`UserDefaults` + `UIAccessibility
/// .isClosedCaptioningEnabled`). DataStore is a process-wide singleton keyed by
/// name, so the in-context CC chip and the Settings switches read/write the same
/// store and stay in sync no matter which touched it.
class CaptionPreferences(private val context: Context) {
    private object Keys {
        val SHOW = booleanPreferencesKey("showOliverCaptions")
        val ONLY = booleanPreferencesKey("oliverCaptionOnly")
        val SEEDED = booleanPreferencesKey("captionsSeeded")
    }

    /// Until the one-time seed writes, fall back per-read to the OS switch so
    /// captions are effectively on-by-default for users who enabled system
    /// captions — matching iOS's register-defaults behavior.
    val showCaptions: Flow<Boolean> =
        context.captionDataStore.data.map { it[Keys.SHOW] ?: systemCaptionsOn() }
    val captionOnly: Flow<Boolean> =
        context.captionDataStore.data.map { it[Keys.ONLY] ?: false }

    suspend fun setShowCaptions(v: Boolean) {
        context.captionDataStore.edit { it[Keys.SHOW] = v }
    }

    suspend fun setCaptionOnly(v: Boolean) {
        context.captionDataStore.edit { it[Keys.ONLY] = v }
    }

    /// One-time seed of the default from the OS closed-captioning switch. Call
    /// from BlackjackApp.onCreate().
    suspend fun seedDefaultsIfNeeded() {
        context.captionDataStore.edit { prefs ->
            if (prefs[Keys.SEEDED] != true) {
                if (prefs[Keys.SHOW] == null) prefs[Keys.SHOW] = systemCaptionsOn()
                prefs[Keys.SEEDED] = true
            }
        }
    }

    private fun systemCaptionsOn(): Boolean =
        (context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager)?.isEnabled ?: false
}
