package com.carmind.voicejournal.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val whisperModelPathKey = stringPreferencesKey("whisper_model_path")
    private val whisperModelNameKey = stringPreferencesKey("whisper_model_name")
    private val whisperUpdateKey = stringPreferencesKey("whisper_updated_at")
    private val llmModelPathKey = stringPreferencesKey("llm_model_path")
    private val llmModelNameKey = stringPreferencesKey("llm_model_name")

    val whisperModelPath: Flow<String?> = context.dataStore.data.map { it[whisperModelPathKey] }
    val whisperModelName: Flow<String?> = context.dataStore.data.map { it[whisperModelNameKey] }
    val whisperUpdateTimestamp: Flow<String?> = context.dataStore.data.map { it[whisperUpdateKey] }
    val llmModelPath: Flow<String?> = context.dataStore.data.map { it[llmModelPathKey] }
    val llmModelName: Flow<String?> = context.dataStore.data.map { it[llmModelNameKey] }

    suspend fun saveWhisperModelPath(path: String, name: String? = null) {
        context.dataStore.edit { 
            it[whisperModelPathKey] = path
            it[whisperUpdateKey] = System.currentTimeMillis().toString()
            if (name != null) it[whisperModelNameKey] = name
            else if (path.isEmpty()) it[whisperModelNameKey] = ""
        }
    }

    suspend fun saveLlmModelPath(path: String, name: String? = null) {
        context.dataStore.edit { 
            it[llmModelPathKey] = path
            if (name != null) it[llmModelNameKey] = name
            else if (path.isEmpty()) it[llmModelNameKey] = ""
        }
    }
}
