package com.blackjackoracle.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore("onboarding_prefs")

/// Persists whether first-launch onboarding has run. Android analog of the iOS
/// `@AppStorage("hasCompletedOnboarding")` flag: AppRoot routes the SETUP phase
/// to the tutorial welcome screen until this flips true, and Settings' "Replay
/// Tutorial" clears it. DataStore (matching CaptionPreferences) rather than
/// SharedPreferences so all persisted app prefs live in one mechanism.
class OnboardingPreferences(private val context: Context) {
    private object Keys {
        val COMPLETED = booleanPreferencesKey("hasCompletedOnboarding")
    }

    val hasCompletedOnboarding: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[Keys.COMPLETED] ?: false }

    suspend fun setCompleted(value: Boolean) {
        context.onboardingDataStore.edit { it[Keys.COMPLETED] = value }
    }
}
