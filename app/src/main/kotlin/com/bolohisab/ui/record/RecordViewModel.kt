package com.bolohisab.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.LearningRepository
import com.bolohisab.data.LedgerEntry
import com.bolohisab.data.LedgerRepository
import com.bolohisab.nlu.CustomerRef
import com.bolohisab.nlu.EntryDraft
import com.bolohisab.nlu.KnownCustomer
import com.bolohisab.nlu.LedgerParser
import com.bolohisab.nlu.LedgerQuery
import com.bolohisab.nlu.ParseResult
import com.bolohisab.nlu.Period
import com.bolohisab.nlu.Poisha
import com.bolohisab.voice.MicUnavailableException
import com.bolohisab.voice.ModelState
import com.bolohisab.voice.SpeechEvent
import com.bolohisab.voice.SpeechRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MicState {
    data object Idle : MicState
    data class Listening(val partial: String, val level: Float) : MicState
    data object Processing : MicState
}

/** Answers to spoken questions, rendered by the UI with string resources. */
sealed interface Answer {
    data class Due(val name: String, val due: Poisha) : Answer
    data object UnknownCustomer : Answer
    data class Sales(val period: Period, val sales: Poisha, val credit: Poisha, val collected: Poisha) : Answer
    data class TopDebtors(val rows: List<Pair<String, Poisha>>) : Answer
}

sealed interface RecordEvent {
    data class Saved(val entryId: Long) : RecordEvent
    data class Updated(val entryId: Long) : RecordEvent
    data class EntryDeleted(val entryId: Long) : RecordEvent
    data class NotUnderstood(val transcript: String) : RecordEvent
    data object NothingHeard : RecordEvent
    data object HoldToTalk : RecordEvent
    data object ModelUnavailable : RecordEvent
    data object MicBusy : RecordEvent
    data class Failed(val message: String) : RecordEvent
}

data class RecordUiState(
    val mic: MicState = MicState.Idle,
    val review: ReviewState? = null,
    val answer: Answer? = null,
    val model: ModelState = ModelState.NotLoaded,
    val customers: List<KnownCustomer> = emptyList(),
)

/**
 * Drives voice and typed entry: record → parse → review → save.
 * Nothing reaches the ledger without passing the confirm card.
 */
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val learning: LearningRepository,
    private val speech: SpeechRecognizer,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordUiState())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private val events = Channel<RecordEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val customers = repository.customers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val itemNames = repository.itemNames.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val productNames = repository.products.map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val corrections = learning.corrections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var listenJob: Job? = null
    private var pressedAt = 0L

    init {
        viewModelScope.launch { speech.state.collect { m -> _state.update { it.copy(model = m) } } }
        viewModelScope.launch { customers.collect { c -> _state.update { it.copy(customers = c) } } }
        viewModelScope.launch { speech.warmUp() }
    }

    // ----------------------------------------------------------------- voice

    fun onMicDown() {
        if (listenJob?.isActive == true) return
        val model = _state.value.model
        if (model is ModelState.Missing || model is ModelState.Failed) {
            viewModelScope.launch { events.send(RecordEvent.ModelUnavailable) }
            return
        }
        pressedAt = System.currentTimeMillis()
        _state.update { it.copy(mic = MicState.Listening("", 0f), answer = null) }
        listenJob = viewModelScope.launch {
            try {
                speech.listen(hotwords = customers.value.map { it.name }).collect { event ->
                    when (event) {
                        is SpeechEvent.Level -> _state.update { s ->
                            (s.mic as? MicState.Listening)?.let { s.copy(mic = it.copy(level = event.value)) } ?: s
                        }
                        is SpeechEvent.Partial -> _state.update { s ->
                            (s.mic as? MicState.Listening)?.let { s.copy(mic = it.copy(partial = event.text)) } ?: s
                        }
                        is SpeechEvent.Final -> {
                            _state.update { it.copy(mic = MicState.Processing) }
                            if (event.text.isBlank()) events.send(RecordEvent.NothingHeard) else handleText(event.text)
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                events.send(if (t is MicUnavailableException) RecordEvent.MicBusy else RecordEvent.Failed(t.message ?: t::class.java.simpleName))
            } finally {
                _state.update { it.copy(mic = MicState.Idle) }
            }
        }
    }

    fun onMicUp() {
        if (listenJob?.isActive != true) return
        if (System.currentTimeMillis() - pressedAt < MIN_HOLD_MS) {
            listenJob?.cancel()
            viewModelScope.launch { events.send(RecordEvent.HoldToTalk) }
        } else {
            speech.stop()
        }
    }

    // ----------------------------------------------------------------- typed

    fun onTyped(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { handleText(text.trim()) }
    }

    fun onManualEntry() = _state.update { it.copy(review = ReviewState.blank(), answer = null) }

    /** Reopens a saved entry for correction, loading its edit history first. */
    fun onEditEntry(entry: LedgerEntry) {
        viewModelScope.launch {
            val history = repository.historyOf(entry.id)
            _state.update { it.copy(review = ReviewState.forEdit(entry, history), answer = null) }
        }
    }

    // ---------------------------------------------------------------- review

    fun onReviewChange(review: ReviewState) = _state.update { it.copy(review = review) }

    fun onReviewDismiss() = _state.update { it.copy(review = null) }

    fun onAnswerDismiss() = _state.update { it.copy(answer = null) }

    fun onSave() {
        val review = _state.value.review ?: return
        if (review.problems.isNotEmpty()) return
        val editingId = review.editingEntryId
        viewModelScope.launch {
            try {
                if (editingId != null) {
                    val customerId = repository.update(editingId, review.toDraft())
                    attachPickedPhone(customerId, review.pickedPhone)
                    _state.update { it.copy(review = null) }
                    events.send(RecordEvent.Updated(editingId))
                } else {
                    val draft = review.toDraft()
                    val saved = repository.save(draft)
                    attachPickedPhone(saved.customerId, review.pickedPhone)
                    _state.update { it.copy(review = null) }
                    events.send(RecordEvent.Saved(saved.entryId))
                    learnFrom(review.parsed, draft)
                }
            } catch (t: Throwable) {
                events.send(RecordEvent.Failed(t.message ?: t::class.java.simpleName))
            }
        }
    }

    /** Teaches the typing help and the parser from a confirmed entry. Never fails the save. */
    private suspend fun learnFrom(parsed: EntryDraft?, saved: EntryDraft) {
        try {
            learning.learnFromEntry(parsed, saved, customers.value, productNames.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("RecordViewModel", "Learning from a saved entry failed", e)
        }
    }

    private suspend fun attachPickedPhone(customerId: Long?, phone: String?) {
        if (customerId != null && phone != null) repository.setCustomerPhone(customerId, phone)
    }

    /** Deletes the entry currently open in the confirm card (soft-delete, same as [onUndo]). */
    fun onDeleteEditingEntry() {
        val entryId = _state.value.review?.editingEntryId ?: return
        viewModelScope.launch {
            repository.undo(entryId)
            _state.update { it.copy(review = null) }
            events.send(RecordEvent.EntryDeleted(entryId))
        }
    }

    fun onUndo(entryId: Long) {
        viewModelScope.launch { repository.undo(entryId) }
    }

    /** Brings back an entry removed via [onDeleteEditingEntry]. */
    fun onRestoreDeleted(entryId: Long) {
        viewModelScope.launch { repository.restore(entryId) }
    }

    // ------------------------------------------------------------------ core

    private suspend fun handleText(text: String) {
        val parser = LedgerParser(customers.value, itemNames.value, corrections.value)
        when (val result = parser.parse(text)) {
            is ParseResult.Entry -> _state.update { it.copy(review = ReviewState.from(result.draft)) }
            is ParseResult.Query -> _state.update { it.copy(answer = answer(result.query)) }
            is ParseResult.Unrecognized -> events.send(RecordEvent.NotUnderstood(text))
        }
    }

    private suspend fun answer(query: LedgerQuery): Answer = when (query) {
        is LedgerQuery.CustomerDue -> when (val c = query.customer) {
            is CustomerRef.Existing -> Answer.Due(c.name, repository.dueOf(c.id))
            else -> Answer.UnknownCustomer
        }
        is LedgerQuery.Sales -> repository.summaryNow(query.period).let {
            Answer.Sales(query.period, it.sales, it.creditGiven, it.collected)
        }
        LedgerQuery.TopDebtors -> Answer.TopDebtors(
            repository.topDebtors(5).map { it.customer.name to it.due },
        )
    }

    private companion object {
        const val MIN_HOLD_MS = 350L
    }
}
