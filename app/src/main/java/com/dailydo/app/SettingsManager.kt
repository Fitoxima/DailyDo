package com.dailydo.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val DISPLAY_MODE_KEY = stringPreferencesKey("display_mode")
        val THEME_ID_KEY = intPreferencesKey("theme_id")
        val CUSTOM_CATEGORIES_KEY = stringSetPreferencesKey("custom_categories")
        val NOTIFICATION_PROMPT_SHOWN_KEY = booleanPreferencesKey("notification_prompt_shown")
        val BATTERY_PROMPT_DISMISSED_KEY = booleanPreferencesKey("battery_prompt_dismissed")
        val PRIVACY_ACCEPTED_KEY = booleanPreferencesKey("privacy_accepted")
    }

    val displayMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DISPLAY_MODE_KEY] ?: "Dark"
    }

    val themeId: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_ID_KEY] ?: 1
    }

    val customCategories: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_CATEGORIES_KEY] ?: emptySet()
    }

    val notificationPromptShown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_PROMPT_SHOWN_KEY] ?: false
    }

    val batteryPromptDismissed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BATTERY_PROMPT_DISMISSED_KEY] ?: false
    }

    val privacyAccepted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PRIVACY_ACCEPTED_KEY] ?: false
    }

    suspend fun saveDisplayMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_MODE_KEY] = mode
        }
    }

    suspend fun saveThemeId(id: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_ID_KEY] = id
        }
    }

    suspend fun saveCustomCategory(category: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_CATEGORIES_KEY] ?: emptySet()
            preferences[CUSTOM_CATEGORIES_KEY] = current + category
        }
    }

    suspend fun deleteCustomCategory(category: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_CATEGORIES_KEY] ?: emptySet()
            preferences[CUSTOM_CATEGORIES_KEY] = current - category
        }
    }

    suspend fun setNotificationPromptShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_PROMPT_SHOWN_KEY] = shown
        }
    }

    suspend fun setBatteryPromptDismissed(dismissed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BATTERY_PROMPT_DISMISSED_KEY] = dismissed
        }
    }

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PRIVACY_ACCEPTED_KEY] = accepted
        }
    }
}
