package com.bolohisab.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UnlockResult {
    data object Unlocked : UnlockResult

    /** Wrong PIN. [lockedUntil] is set when this mistake started a lockout. */
    data class Wrong(val lockedUntil: Long?) : UnlockResult

    /** Still inside a lockout; the PIN was not checked. */
    data class LockedOut(val until: Long) : UnlockResult
}

/**
 * App-lock state: whether a PIN guards the app, the salted hash to check it against, and the
 * wrong-guess counter behind [PinLockout]. The PIN itself is never stored, only [PinHasher]
 * output. The counter is persisted so killing the app does not reset a lockout.
 */
@Singleton
class LockRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {

    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLED] == true }

    /** End of the current lockout in epoch millis, or null when PINs are accepted. */
    val lockedUntil: Flow<Long?> = dataStore.data.map { activeLockout(it) }

    suspend fun setPin(pin: String) {
        val salted = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        dataStore.edit { prefs ->
            prefs[KEY_SALT] = salted.saltHex
            prefs[KEY_HASH] = salted.hashHex
            prefs[KEY_ENABLED] = true
            prefs.remove(KEY_FAILURES)
            prefs.remove(KEY_LOCKED_UNTIL)
        }
    }

    suspend fun disable() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SALT)
            prefs.remove(KEY_HASH)
            prefs.remove(KEY_FAILURES)
            prefs.remove(KEY_LOCKED_UNTIL)
            prefs[KEY_ENABLED] = false
        }
    }

    /** Checks [pin] while honouring the lockout, and re-hashes a legacy hash on success. */
    suspend fun verify(pin: String): UnlockResult {
        val prefs = dataStore.data.first()
        activeLockout(prefs)?.let { return UnlockResult.LockedOut(it) }
        val salt = prefs[KEY_SALT]
        val hash = prefs[KEY_HASH]
        val ok = salt != null && hash != null &&
            withContext(Dispatchers.Default) { PinHasher.matches(pin, salt, hash) }

        if (ok) {
            val upgraded = if (PinHasher.needsUpgrade(hash!!)) withContext(Dispatchers.Default) { PinHasher.hash(pin) } else null
            dataStore.edit {
                it.remove(KEY_FAILURES)
                it.remove(KEY_LOCKED_UNTIL)
                if (upgraded != null) {
                    it[KEY_SALT] = upgraded.saltHex
                    it[KEY_HASH] = upgraded.hashHex
                }
            }
            return UnlockResult.Unlocked
        }

        var until: Long? = null
        dataStore.edit {
            val failures = (it[KEY_FAILURES] ?: 0) + 1
            it[KEY_FAILURES] = failures
            val wait = PinLockout.lockoutMillis(failures)
            if (wait > 0) {
                val end = clock.millis() + wait
                it[KEY_LOCKED_UNTIL] = end
                until = end
            }
        }
        return UnlockResult.Wrong(until)
    }

    /**
     * The stored lockout end, if it is still in the future. Capped at [PinLockout.MAX_MILLIS]
     * from now, so a phone clock set backwards cannot lock the shopkeeper out for days.
     */
    private fun activeLockout(prefs: Preferences): Long? {
        val until = prefs[KEY_LOCKED_UNTIL] ?: return null
        val now = clock.millis()
        return if (until > now) minOf(until, now + PinLockout.MAX_MILLIS) else null
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("lock_enabled")
        val KEY_SALT = stringPreferencesKey("lock_pin_salt")
        val KEY_HASH = stringPreferencesKey("lock_pin_hash")
        val KEY_FAILURES = intPreferencesKey("lock_failures")
        val KEY_LOCKED_UNTIL = longPreferencesKey("lock_locked_until")
    }
}
