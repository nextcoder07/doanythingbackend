package com.hakdaar.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hakdaar_settings")

/**
 * Stores non-sensitive user settings ONLY:
 * - Selected language (hi-IN or en-IN)
 * - TTS on/off
 * - Speech rate
 * Absolutely NO PII, form values, task history or audio is ever written here.
 */
class SettingsDataStore(private val context: Context) {
    companion object {
        val KEY_LANGUAGE = stringPreferencesKey("preferred_language")
        val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("privacy_disclosure_accepted")
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "hi-IN"
    }

    val ttsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_ENABLED] ?: true
    }

    val speechRateFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPEECH_RATE] ?: 1.0f
    }

    val disclosureAcceptedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISCLOSURE_ACCEPTED] ?: false
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = languageCode
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TTS_ENABLED] = enabled
        }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEECH_RATE] = rate
        }
    }

    suspend fun setDisclosureAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISCLOSURE_ACCEPTED] = accepted
        }
    }
}
