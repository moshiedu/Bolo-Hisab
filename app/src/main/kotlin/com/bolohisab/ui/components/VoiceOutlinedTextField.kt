package com.bolohisab.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bolohisab.R
import com.bolohisab.nlu.typing.PhoneticSuggester
import com.bolohisab.nlu.typing.Suggestion
import com.bolohisab.nlu.typing.SuggestionKind
import com.bolohisab.nlu.typing.Suggestions
import com.bolohisab.nlu.typing.TypingContext
import com.bolohisab.voice.MicUnavailableException
import com.bolohisab.voice.ModelState
import com.bolohisab.voice.SpeechEvent
import com.bolohisab.voice.SpeechRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val micBusyEvents = Channel<Unit>(Channel.CONFLATED)

    /** Fires when the mic could not be opened, so the field can tell the shopkeeper why. */
    val micBusy = micBusyEvents.receiveAsFlow()

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
                    } catch (e: MicUnavailableException) {
                        micBusyEvents.trySend(Unit)
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
 *
 * Also gives Avro-style typing help: Banglish ("chal") shows Bangla candidates ("চাল") from the
 * Bolo Hisab vocabulary and this shop's own names in a strip under the field; a space or comma
 * accepts the highlighted one. [typing] says what the field holds so names rank right; null
 * turns the help off for this field.
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
    typing: TypingContext? = TypingContext.TEXT,
) {
    val vm: VoiceFieldViewModel = hiltViewModel()
    val fieldKey = remember { Any() }
    val listeningKey by vm.listeningKey.collectAsStateWithLifecycle()
    val listening = listeningKey == fieldKey
    val modelState by vm.modelState.collectAsStateWithLifecycle()
    val micAvailable = modelState is ModelState.Ready

    val assist: TypingAssistViewModel = hiltViewModel()
    val suggester by assist.suggester.collectAsStateWithLifecycle()
    val assistOn = typing != null && suggester != null

    // The caller owns the text; the cursor and IME composition live here. When the text changes
    // from outside (voice, a chip, a reset), the cursor goes to its end.
    var internal by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val fieldValue = if (internal.text == value) internal else TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }

    fun set(next: TextFieldValue) {
        internal = next
        if (next.text != value) onValueChange(next.text)
    }

    /** A space/comma right after a Banglish word swaps in the highlighted Bangla candidate. */
    fun onEdit(next: TextFieldValue) {
        val s = suggester
        val ctx = typing
        val old = fieldValue
        val at = old.selection.end
        val typedOne = s != null && ctx != null && old.selection.collapsed && next.selection.collapsed &&
            next.text.length == old.text.length + 1 && next.selection.end == at + 1 &&
            next.text.regionMatches(0, old.text, 0, at) && next.text.endsWith(old.text.substring(at))
        if (typedOne && next.text[at] in COMMIT_CHARS) {
            val found = s!!.suggest(old.text, at, ctx!!)
            val commit = found?.commitOnSpace
            if (found != null && commit != null && commit.text != found.typed) {
                val (text, cursor) = PhoneticSuggester.apply(old.text, found, commit, trailing = next.text[at].toString())
                set(TextFieldValue(text, TextRange(cursor)))
                return
            }
        }
        set(next)
    }

    val suggestions = remember(fieldValue.text, fieldValue.selection, suggester, typing, focused, listening) {
        val s = suggester
        if (s == null || typing == null || !focused || listening || !fieldValue.selection.collapsed) null
        else s.suggest(fieldValue.text, fieldValue.selection.end, typing)
    }

    val context = LocalContext.current
    val micBusyMessage = stringResource(R.string.mic_busy)
    LaunchedEffect(vm) {
        vm.micBusy.collect { Toast.makeText(context, micBusyMessage, Toast.LENGTH_LONG).show() }
    }
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

    // While focused, the strip's row is reserved even when empty, so the field doesn't jump
    // up and down between words.
    val showStrip = assistOn && focused && !listening
    val support: (@Composable () -> Unit)? = if (showStrip) {
        {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SuggestionStrip(suggestions) { picked ->
                    val s = suggestions ?: return@SuggestionStrip
                    val (text, cursor) = PhoneticSuggester.apply(fieldValue.text, s, picked)
                    set(TextFieldValue(text, TextRange(cursor)))
                }
                supportingText?.invoke()
            }
        }
    } else {
        supportingText
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = ::onEdit,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
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
        supportingText = support,
        colors = pulsingColors,
        shape = shape,
        // The phone keyboard's own English autocorrect would turn "chal" into "chalk" on space,
        // before our Bangla commit sees it.
        keyboardOptions = if (assistOn) keyboardOptions.copy(autoCorrectEnabled = false) else keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

/** Characters that end a word and accept the highlighted suggestion, as in Avro. */
private const val COMMIT_CHARS = " ,।?"

/**
 * One row of Avro-style candidates. The first, highlighted, is what a space accepts; the Latin
 * as typed comes last so English names can stay English. Keeps its height when empty.
 */
@Composable
private fun SuggestionStrip(suggestions: Suggestions?, onPick: (Suggestion) -> Unit) {
    val commit = suggestions?.commitOnSpace
    Row(
        Modifier.fillMaxWidth().height(34.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions?.items?.forEach { item ->
            val primary = item == commit
            val original = item.kind == SuggestionKind.ORIGINAL
            Surface(
                onClick = { onPick(item) },
                shape = RoundedCornerShape(10.dp),
                color = when {
                    primary -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = when {
                    primary -> MaterialTheme.colorScheme.onPrimaryContainer
                    original -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.height(30.dp),
            ) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        item.text,
                        style = if (original) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
