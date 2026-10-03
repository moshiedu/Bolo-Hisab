package com.bolohisab.ui.settings

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

/** Where the two-step "enter, then confirm" PIN flow currently is. */
sealed interface PinSetupStep {
    data object Hidden : PinSetupStep
    data object EnterNew : PinSetupStep
    data class ConfirmNew(val first: String) : PinSetupStep
}

data class SettingsUiState(
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinSetup: PinSetupStep = PinSetupStep.Hidden,
    val mismatch: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val lockRepository: LockRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            lockRepository.enabled.collect { enabled -> _state.update { it.copy(lockEnabled = enabled) } }
        }
        viewModelScope.launch {
            lockRepository.biometricEnabled.collect { on -> _state.update { it.copy(biometricEnabled = on) } }
        }
    }

    fun onToggleLock(checked: Boolean) {
        if (checked) {
            _state.update { it.copy(pinSetup = PinSetupStep.EnterNew, mismatch = false) }
        } else {
            viewModelScope.launch { lockRepository.disable() }
        }
    }

    /** Called after a successful fingerprint scan (to turn it on) or directly (to turn it off). */
    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { lockRepository.setBiometric(enabled) }
    }

    fun onChangePin() = _state.update { it.copy(pinSetup = PinSetupStep.EnterNew, mismatch = false) }

    fun onCancelPinSetup() = _state.update { it.copy(pinSetup = PinSetupStep.Hidden) }

    fun consumeMismatch() = _state.update { it.copy(mismatch = false) }

    fun onPinEntered(pin: String) {
        when (val step = _state.value.pinSetup) {
            PinSetupStep.EnterNew ->
                _state.update { it.copy(pinSetup = PinSetupStep.ConfirmNew(pin), mismatch = false) }
            is PinSetupStep.ConfirmNew ->
                if (pin == step.first) {
                    viewModelScope.launch {
                        lockRepository.setPin(pin)
                        _state.update { it.copy(pinSetup = PinSetupStep.Hidden, mismatch = false) }
                    }
                } else {
                    _state.update { it.copy(pinSetup = PinSetupStep.EnterNew, mismatch = true) }
                }
            PinSetupStep.Hidden -> Unit
        }
    }
}
