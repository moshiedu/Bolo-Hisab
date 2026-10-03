package com.bolohisab.ui.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.security.LockRepository
import com.bolohisab.data.security.UnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockGateState(
    val loading: Boolean = true,
    val locked: Boolean = false,
    val wrongPin: Boolean = false,
    /** Epoch millis until which the keypad is disabled after too many wrong PINs. */
    val lockedUntil: Long? = null,
    val checking: Boolean = false,
)

/**
 * Gates the whole app behind a PIN when app lock is on. Re-locks whenever the app leaves
 * the foreground, not just on cold start — a shop phone gets set down and picked up again.
 */
@HiltViewModel
class LockGateViewModel @Inject constructor(
    private val lockRepository: LockRepository,
) : ViewModel(), DefaultLifecycleObserver {

    private val _state = MutableStateFlow(LockGateState())
    val state: StateFlow<LockGateState> = _state.asStateFlow()

    private var lockEnabled = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        viewModelScope.launch {
            lockRepository.enabled.collect { enabled ->
                lockEnabled = enabled
                _state.update { it.copy(loading = false, locked = enabled) }
            }
        }
        viewModelScope.launch {
            lockRepository.normalizeLockout()
            lockRepository.lockedUntil.collect { until -> _state.update { it.copy(lockedUntil = until) } }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (lockEnabled) _state.update { it.copy(locked = true, wrongPin = false) }
    }

    fun tryUnlock(pin: String) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            try {
                when (val result = lockRepository.verify(pin)) {
                    UnlockResult.Unlocked -> _state.update { it.copy(locked = false, wrongPin = false, lockedUntil = null) }
                    is UnlockResult.Wrong -> _state.update { it.copy(wrongPin = true, lockedUntil = result.lockedUntil) }
                    is UnlockResult.LockedOut -> _state.update { it.copy(lockedUntil = result.until) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // A storage error must not leave the keypad dead (finally re-enables it). Not shown
                // as "wrong PIN": the PIN may well have been right.
                android.util.Log.w("LockGate", "PIN check failed", e)
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    fun consumeWrongPin() = _state.update { it.copy(wrongPin = false) }

    /** Called by the screen once its countdown reaches zero. */
    fun onLockoutEnded() = _state.update { it.copy(lockedUntil = null) }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}
