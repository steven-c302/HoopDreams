package com.partyos.tv.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("partyos_settings")

data class Settings(val pin: String, val advertisedOverride: String, val perfHud: Boolean, val rounds: Int)

class SettingsRepo(private val context: Context) {
    private val pinKey = stringPreferencesKey("pin")
    private val overrideKey = stringPreferencesKey("advertised_override")
    private val hudKey = booleanPreferencesKey("perf_hud")
    private val roundsKey = intPreferencesKey("rounds")

    val settings: Flow<Settings> = context.store.data.map {
        Settings(it[pinKey] ?: "", it[overrideKey] ?: "", it[hudKey] ?: false, it[roundsKey] ?: 5)
    }

    /** Returns the host PIN, creating a random 4-digit one on first launch. */
    suspend fun ensurePin(): String {
        settings.first().pin.takeIf { it.isNotEmpty() }?.let { return it }
        val pin = (1000..9999).random().toString()
        context.store.edit { it[pinKey] = pin }
        return pin
    }

    suspend fun setPin(pin: String) = context.store.edit { it[pinKey] = pin }
    suspend fun setOverride(value: String) = context.store.edit { it[overrideKey] = value.trim() }
    suspend fun setPerfHud(on: Boolean) = context.store.edit { it[hudKey] = on }
    suspend fun setRounds(n: Int) = context.store.edit { it[roundsKey] = n.coerceIn(3, 8) }
}
