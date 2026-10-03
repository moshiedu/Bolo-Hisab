package com.bolohisab.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ModelState {
    data object NotLoaded : ModelState
    data object Loading : ModelState
    data class Ready(val hotwordsSupported: Boolean) : ModelState

    /** Model files are not on the device yet; typing still works. */
    data object Missing : ModelState
    data class Failed(val message: String) : ModelState
}

sealed interface SpeechEvent {
    /** Microphone loudness, 0..1, for the level meter. */
    data class Level(val value: Float) : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
}

/** Offline speech-to-text. One listening session at a time. */
interface SpeechRecognizer {
    val state: StateFlow<ModelState>

    /** Loads the model in the background so the first tap on the mic is instant. */
    suspend fun warmUp()

    /**
     * Records from the microphone until [stop] is called, then emits [SpeechEvent.Final]
     * and completes. Cancelling the collector discards the recording.
     *
     * @param hotwords words to bias recognition towards, such as customer names.
     */
    fun listen(hotwords: List<String> = emptyList()): Flow<SpeechEvent>

    /** Ends the current recording; the flow then emits its final text. */
    fun stop()
}
