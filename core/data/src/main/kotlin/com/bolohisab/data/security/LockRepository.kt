package com.bolohisab.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lock state: whether a PIN guards the app, and the salted hash to check it against.
 * The PIN itself is never stored, only [PinHasher] output.
 */
@Singleton
class LockRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLED] == true }

    suspend fun setPin(pin: String) {
        val salted = PinHasher.hash(pin)
        dataStore.edit { prefs ->
            prefs[KEY_SALT] = salted.saltHex
            prefs[KEY_HASH] = salted.hashHex
            prefs[KEY_ENABLED] = true
        }
    }

    suspend fun disable() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SALT)
            prefs.remove(KEY_HASH)
            prefs[KEY_ENABLED] = false
        }
    }

    suspend fun verify(pin: String): Boolean {
        val prefs = dataStore.data.first()
        val salt = prefs[KEY_SALT]
        val hash = prefs[KEY_HASH]
        return salt != null && hash != null && PinHasher.matches(pin, salt, hash)
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("lock_enabled")
        val KEY_SALT = stringPreferencesKey("lock_pin_salt")
        val KEY_HASH = stringPreferencesKey("lock_pin_hash")
    }
}
