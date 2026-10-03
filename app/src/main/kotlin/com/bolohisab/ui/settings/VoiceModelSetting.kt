package com.bolohisab.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bolohisab.R
import com.bolohisab.ui.format.Bn
import com.bolohisab.voice.AsrModelInstaller
import com.bolohisab.voice.ModelState
import com.bolohisab.voice.SpeechRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class VoiceModelUiState(
    val available: Boolean = false,
    val installed: Boolean = false,
    val busy: Boolean = false,
    val sizeMb: Long = 0,
    val problem: VoiceModelProblem? = null,
)

sealed interface VoiceModelProblem {
    data class NoSpace(val neededMb: Long, val freeMb: Long) : VoiceModelProblem
    data class Failed(val message: String) : VoiceModelProblem

    /** The model loaded but cannot use hotwords on this phone; the copy was removed again. */
    data object Unsupported : VoiceModelProblem
}

/** Opt-in copy of the speech model to storage, which lets recognition favour the shop's own words. */
@HiltViewModel
class VoiceModelViewModel @Inject constructor(
    private val installer: AsrModelInstaller,
    private val speech: SpeechRecognizer,
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceModelUiState())
    val state: StateFlow<VoiceModelUiState> = _state.asStateFlow()
    val model: StateFlow<ModelState> = speech.state

    init {
        viewModelScope.launch {
            val (bytes, installed, usable) = withContext(Dispatchers.IO) {
                Triple(installer.bundledBytes(), installer.isInstalled(), installer.canUseHotwords())
            }
            // A copy that can never turn hotwords on (e.g. from an earlier failed try) only wastes space.
            if (installed && !usable) {
                runCatching { installer.remove(); speech.reload() }
            }
            _state.update {
                it.copy(available = usable && bytes > 0, installed = installed && usable, sizeMb = bytes / MB)
            }
        }
    }

    fun setEnabled(on: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            var problem: VoiceModelProblem? = try {
                if (on) {
                    when (val r = installer.install()) {
                        AsrModelInstaller.Result.Installed -> null
                        is AsrModelInstaller.Result.NotEnoughSpace -> VoiceModelProblem.NoSpace(r.neededBytes / MB, r.freeBytes / MB)
                        AsrModelInstaller.Result.NoBundledModel -> VoiceModelProblem.Failed("no bundled model")
                        is AsrModelInstaller.Result.Failed -> VoiceModelProblem.Failed(r.message)
                    }
                } else {
                    installer.remove()
                    null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                VoiceModelProblem.Failed(e.message ?: e::class.java.simpleName)
            }
            runCatching { speech.reload() }
            // Copied, but this model/phone can't use hotwords: the 90 MB copy would only waste space.
            val loaded = speech.state.value
            if (on && problem == null && loaded is ModelState.Ready && !loaded.hotwordsSupported) {
                runCatching { installer.remove(); speech.reload() }
                problem = VoiceModelProblem.Unsupported
            }
            _state.update { it.copy(busy = false, installed = installer.isInstalled(), problem = problem) }
        }
    }

    private companion object {
        const val MB = 1024L * 1024
    }
}

@Composable
fun VoiceModelSetting(viewModel: VoiceModelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    if (!state.available) return

    val active = state.installed && (model as? ModelState.Ready)?.hotwordsSupported == true
    ListItem(
        leadingContent = { Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.voice_boost_toggle)) },
        supportingContent = {
            Column {
                Text(
                    when {
                        state.busy -> stringResource(R.string.voice_boost_working)
                        active -> stringResource(R.string.voice_boost_on)
                        else -> stringResource(R.string.voice_boost_hint, Bn.digits(state.sizeMb.toString()))
                    },
                )
                when (val p = state.problem) {
                    is VoiceModelProblem.NoSpace -> Text(
                        stringResource(R.string.voice_boost_no_space, Bn.digits(p.neededMb.toString()), Bn.digits(p.freeMb.toString())),
                        color = MaterialTheme.colorScheme.error,
                    )
                    is VoiceModelProblem.Failed -> Text(stringResource(R.string.voice_boost_failed, p.message), color = MaterialTheme.colorScheme.error)
                    VoiceModelProblem.Unsupported -> Text(stringResource(R.string.voice_boost_unsupported), color = MaterialTheme.colorScheme.error)
                    null -> Unit
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        trailingContent = {
            Switch(checked = state.installed, onCheckedChange = viewModel::setEnabled, enabled = !state.busy)
        },
    )
}
