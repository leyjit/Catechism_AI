package com.example.catechismapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_DATABASE_SEEDED = booleanPreferencesKey("database_seeded")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private const val READ_TIMEOUT_MS = 2000L
    }

    /** Returns true if the database has been seeded on a previous launch. */
    suspend fun isDatabaseSeeded(): Boolean {
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            context.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .map { it[KEY_DATABASE_SEEDED] ?: false }
                .first()
        } ?: false
    }

    suspend fun setDatabaseSeeded(seeded: Boolean) {
        context.dataStore.edit { it[KEY_DATABASE_SEEDED] = seeded }
    }

    suspend fun getApiKey(): String? {
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            context.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .map { it[KEY_API_KEY] }
                .first()
        }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { it.remove(KEY_API_KEY) }
    }

    /** Flow-based API key for UI observation. */
    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[KEY_API_KEY] }

    /** Flow-based database seeded flag for UI observation. */
    val isDatabaseSeededFlow: Flow<Boolean> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[KEY_DATABASE_SEEDED] ?: false }
}
