package com.aeunal.stressball.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeunal.stressball.core.GameState
import com.aeunal.stressball.core.SaveCodec
import kotlinx.coroutines.flow.first

private val Context.saveDataStore: DataStore<Preferences> by preferencesDataStore(name = "stressball_save")

/**
 * Persists the [GameState] as a JSON document in Preferences DataStore.
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

    private companion object {
        val KEY_STATE = stringPreferencesKey("state_json")
    }
}
