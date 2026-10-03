package com.bolohisab.ui.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.security.LockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockGateState(val loading: Boolean = true, val locked: Boolean = false, val wrongPin: Boolean = false)

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
    }

    override fun onStop(owner: LifecycleOwner) {
        if (lockEnabled) _state.update { it.copy(locked = true, wrongPin = false) }
    }

    fun tryUnlock(pin: String) {
        viewModelScope.launch {
            if (lockRepository.verify(pin)) {
                _state.update { it.copy(locked = false, wrongPin = false) }
            } else {
                _state.update { it.copy(wrongPin = true) }
            }
        }
    }

    fun consumeWrongPin() = _state.update { it.copy(wrongPin = false) }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}
