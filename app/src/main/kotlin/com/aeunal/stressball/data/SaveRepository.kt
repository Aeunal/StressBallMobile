package com.aeunal.stressball.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeunal.stressball.core.GameState
import com.aeunal.stressball.core.Language
import com.aeunal.stressball.core.SaveCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.saveDataStore: DataStore<Preferences> by preferencesDataStore(name = "stressball_save")

/**
 * Persists the [GameState] as a JSON document in Preferences DataStore, plus
 * a few app preferences.
 *
 * A single JSON string is stored rather than one key per field so that the
 * save format stays entirely in `:core` and is covered by its tests.
 */
class SaveRepository(context: Context) {
    private val dataStore = context.applicationContext.saveDataStore

    suspend fun load(): GameState? = SaveCodec.decode(dataStore.data.first()[KEY_STATE])

    suspend fun save(state: GameState) {
        val encoded = SaveCodec.encode(state)
        dataStore.edit { it[KEY_STATE] = encoded }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_STATE) }
    }

    /** In-app language override; null means "follow the device". */
    val language: Flow<Language?> = dataStore.data.map { Language.fromCode(it[KEY_LANGUAGE]) }

    suspend fun setLanguage(language: Language?) {
        dataStore.edit {
            if (language == null) it.remove(KEY_LANGUAGE) else it[KEY_LANGUAGE] = language.code
        }
    }

    private companion object {
        val KEY_STATE = stringPreferencesKey("state_json")
        val KEY_LANGUAGE = stringPreferencesKey("language")
    }
}
