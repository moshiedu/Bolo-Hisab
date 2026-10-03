package com.bolohisab.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.voice.ModelState
import com.bolohisab.voice.SpeechEvent
import com.bolohisab.voice.SpeechRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Drives voice for any number of [VoiceOutlinedTextField]s on one screen. Composables share a
 * single instance (default `hiltViewModel()` scoping), so [listeningKey] tracks which field
 * actually owns the current recording. Tapping a different field's mic while one is already
 * listening hands off cleanly — the current recording is stopped (its partial text is kept)
 * and the new field starts immediately after, rather than silently ignoring the second tap.
 *
 * Also stops recording the moment the app leaves the foreground (screen lock, home button, app
 * switch) — a tap-to-toggle mic has no gesture to release the way the home screen's hold-to-talk
 * button does, so without this it would keep recording (and transcribing whatever it overhears)
 * for as long as the app sits backgrounded. Mirrors [com.bolohisab.ui.lock.LockGateViewModel]'s
 * existing `ProcessLifecycleOwner` pattern.
 */
@HiltViewModel
class VoiceFieldViewModel @Inject constructor(
    private val speech: SpeechRecognizer,
) : ViewModel(), DefaultLifecycleObserver {
    val modelState: StateFlow<ModelState> = speech.state

    private val _listeningKey = MutableStateFlow<Any?>(null)
    val listeningKey: StateFlow<Any?> = _listeningKey.asStateFlow()

    /** Live mic loudness (0..1) of the currently listening field, for the "live" pulse. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var job: Job? = null

    /** Serialises stop-then-start handoffs so a rapid switch between fields can't race. */
    private val switchMutex = Mutex()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (_listeningKey.value != null) speech.stop()
    }

    fun start(key: Any, onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        viewModelScope.launch {
            switchMutex.withLock {
                if (_listeningKey.value == key) return@withLock
                if (_listeningKey.value != null) {
                    speech.stop()
                    job?.join()
                }
                _listeningKey.value = key
                job = launch {
                    try {
                        speech.listen().collect { event ->
                            when (event) {
                                is SpeechEvent.Partial -> onPartial(event.text)
                                is SpeechEvent.Final -> onFinal(event.text)
                                is SpeechEvent.Level -> _level.value = event.value
                            }
                        }
                    } finally {
                        _listeningKey.value = null
                        _level.value = 0f
                    }
                }
            }
        }
    }

    fun stop() = speech.stop()

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        speech.stop()
    }
}

/**
 * [OutlinedTextField] with an inline mic icon: tap to voice-type into it, tap again to stop.
 * Falls back to a plain keyboard field when the mic permission is denied or the voice model
 * isn't ready — nothing here is required to use the field.
 */
@Composable
fun VoiceOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    colors: TextFieldColors? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val vm: VoiceFieldViewModel = hiltViewModel()
    val fieldKey = remember { Any() }
    val listeningKey by vm.listeningKey.collectAsStateWithLifecycle()
    val listening = listeningKey == fieldKey
    val modelState by vm.modelState.collectAsStateWithLifecycle()
    val micAvailable = modelState is ModelState.Ready

    val context = LocalContext.current
    fun hasMicPermission() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startListening() {
        vm.start(
            key = fieldKey,
            onPartial = onValueChange,
            onFinal = onValueChange,
        )
    }

    val permission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) startListening()
    }

    // A steady ambient pulse while listening, boosted by the mic's real loudness so the field
    // visibly "breathes" with the shopkeeper's voice rather than just showing a static tint.
    val level by vm.level.collectAsStateWithLifecycle()
    val ambientPulse = rememberInfiniteTransition(label = "voiceFieldAmbientPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "voiceFieldAmbientPulseAlpha",
    ).value
    val levelBoost by animateFloatAsState(if (listening) level else 0f, tween(90), label = "voiceFieldLevelBoost")
    val pulseAlpha = if (listening) (ambientPulse * 0.7f + levelBoost * 0.3f).coerceIn(0f, 1f) else 1f
    val micScale by animateFloatAsState(if (listening) 1f + levelBoost * 0.25f else 1f, tween(90), label = "voiceFieldMicScale")

    val pulsingColors = if (listening) {
        val liveColor = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
        OutlinedTextFieldDefaults.colors(focusedBorderColor = liveColor, unfocusedBorderColor = liveColor)
    } else {
        colors ?: OutlinedTextFieldDefaults.colors()
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = if (micAvailable) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingContent?.invoke()
                    IconButton(
                        onClick = {
                            when {
                                listening -> vm.stop()
                                hasMicPermission() -> startListening()
                                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = stringResource(
                                if (listening) {
                                    R.string.voice_field_mic_listening_content_description
                                } else {
                                    R.string.voice_field_mic_content_description
                                },
                            ),
                            tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.scale(micScale),
                        )
                    }
                }
            }
        } else if (trailingContent != null) {
            { Row(verticalAlignment = Alignment.CenterVertically) { trailingContent() } }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText,
        colors = pulsingColors,
        shape = shape,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
