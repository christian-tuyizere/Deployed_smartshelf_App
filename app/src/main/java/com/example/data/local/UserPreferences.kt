package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smartshelf_prefs")

data class UserPrefs(
    val language: String = "rw",
    val darkMode: Boolean = true,
    val stockAlertsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val pinHash: String = "",
    val lastAlertSignature: String = ""
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val STOCK_ALERTS = booleanPreferencesKey("stock_alerts_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val LAST_ALERT_SIG = stringPreferencesKey("last_alert_signature")
    }

    val prefsFlow: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            language = p[Keys.LANGUAGE] ?: "rw",
            darkMode = p[Keys.DARK_MODE] ?: true,
            stockAlertsEnabled = p[Keys.STOCK_ALERTS] ?: true,
            onboardingCompleted = p[Keys.ONBOARDING_DONE] ?: false,
            pinHash = p[Keys.PIN_HASH] ?: "",
            lastAlertSignature = p[Keys.LAST_ALERT_SIG] ?: ""
        )
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = dark }
    }

    suspend fun setStockAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STOCK_ALERTS] = enabled }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setPinHash(hash: String) {
        context.dataStore.edit { it[Keys.PIN_HASH] = hash }
    }

    suspend fun setLastAlertSignature(sig: String) {
        context.dataStore.edit { it[Keys.LAST_ALERT_SIG] = sig }
    }

    suspend fun resetAllPrefs() {
        context.dataStore.edit { it.clear() }
    }
}
