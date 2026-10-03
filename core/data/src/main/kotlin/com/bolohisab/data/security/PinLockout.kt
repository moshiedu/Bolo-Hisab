package com.bolohisab.data.security

/**
 * How long the lock screen refuses PINs after repeated wrong guesses. The first few
 * mistakes are free (a shopkeeper fumbling with greasy fingers), then the wait doubles
 * from 30 seconds up to 15 minutes, so trying all 10,000 four-digit PINs is impractical.
 */
object PinLockout {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MILLIS = 30_000L
    const val MAX_MILLIS = 15 * 60_000L

    /** Lockout after the [failures]-th consecutive wrong PIN; 0 means try again right away. */
    fun lockoutMillis(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_MILLIS shl doublings).coerceAtMost(MAX_MILLIS)
    }
}
