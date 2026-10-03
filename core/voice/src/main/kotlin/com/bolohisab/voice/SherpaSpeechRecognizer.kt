package com.bolohisab.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Offline Bangla speech recognition with sherpa-onnx and a streaming Zipformer.
 *
 * All recognizer calls run on one dedicated thread: the native recognizer is not
 * thread-safe, and keeping it off the main thread keeps the UI smooth.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class SherpaSpeechRecognizer(
    private val context: Context,
    private val locator: AsrModelLocator = AsrModelLocator(context),
) : SpeechRecognizer {

    private val asrThread = newSingleThreadContext("bolohisab-asr")
    private val _state = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    private var recognizer: OnlineRecognizer? = null
    private var hotwordsSupported = false
    private val stopRequested = AtomicBoolean(false)

    override suspend fun warmUp() = withContext(asrThread) { ensureLoaded(); Unit }

    private fun ensureLoaded(): OnlineRecognizer? {
        recognizer?.let { return it }
        val files = locator.locate() ?: run { _state.value = ModelState.Missing; return null }
        _state.value = ModelState.Loading
        return try {
            // Hotwords need the BPE vocab read from a real file path, so only for models on disk.
            hotwordsSupported = !files.fromAssets && files.bpeVocab != null
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = files.encoder,
                        decoder = files.decoder,
                        joiner = files.joiner,
                    ),
                    tokens = files.tokens,
                    numThreads = THREADS,
                    modelType = "zipformer2",
                    modelingUnit = if (hotwordsSupported) "bpe" else "",
                    bpeVocab = if (hotwordsSupported) files.bpeVocab.orEmpty() else "",
                ),
                // Hold-to-talk decides when speech ends, so endpointing only guards very long input.
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 30f, 0f),
                    rule2 = EndpointRule(true, 30f, 0f),
                    rule3 = EndpointRule(false, 0f, 60f),
                ),
                enableEndpoint = false,
                decodingMethod = if (hotwordsSupported) "modified_beam_search" else "greedy_search",
                maxActivePaths = 4,
                hotwordsScore = 2.0f,
            )
            val assets = if (files.fromAssets) context.assets else null
            OnlineRecognizer(assetManager = assets, config = config).also {
                recognizer = it
                _state.value = ModelState.Ready(hotwordsSupported)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load ASR model", t)
            _state.value = ModelState.Failed(t.message ?: t::class.java.simpleName)
            null
        }
    }

    override fun stop() = stopRequested.set(true)

    @SuppressLint("MissingPermission") // Checked below; the UI requests it before calling listen().
    override fun listen(hotwords: List<String>): Flow<SpeechEvent> = flow {
        check(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        ) { "RECORD_AUDIO permission not granted" }
        val rec = ensureLoaded() ?: error("Speech model is not available")
        stopRequested.set(false)

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CHUNK * 4),
        )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            throw MicUnavailableException()
        }
        val stream: OnlineStream = rec.createStream(
            if (hotwordsSupported) hotwords.filter { it.isNotBlank() }.joinToString("\n") else "",
        )
        val pcm = ShortArray(CHUNK)
        val samples = FloatArray(CHUNK)
        var lastPartial = ""
        try {
            audio.startRecording()
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw MicUnavailableException()
            var emptyReads = 0
            while (currentCoroutineContext().isActive && !stopRequested.get()) {
                val n = audio.read(pcm, 0, CHUNK)
                // A negative count is an error (device lost, mic taken by a call) and will not recover;
                // a run of empty reads means the same. Either way fail instead of spinning the CPU.
                if (n < 0) throw MicUnavailableException()
                if (n == 0) {
                    if (++emptyReads > MAX_EMPTY_READS) throw MicUnavailableException()
                    continue
                }
                emptyReads = 0
                var sumSquares = 0.0
                for (i in 0 until n) {
                    val s = pcm[i] / 32768f
                    samples[i] = s
                    sumSquares += s * s
                }
                emit(SpeechEvent.Level((sqrt(sumSquares / n) * 6).toFloat().coerceIn(0f, 1f)))
                stream.acceptWaveform(if (n == CHUNK) samples else samples.copyOf(n), SAMPLE_RATE)
                while (rec.isReady(stream)) rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                if (text != lastPartial) { lastPartial = text; emit(SpeechEvent.Partial(text)) }
            }
            audio.stop()
            if (!currentCoroutineContext().isActive) return@flow

            // Flush: a little trailing silence lets the streaming model finish the last word.
            stream.acceptWaveform(FloatArray(SAMPLE_RATE * 3 / 10), SAMPLE_RATE)
            stream.inputFinished()
            while (rec.isReady(stream)) rec.decode(stream)
            emit(SpeechEvent.Final(rec.getResult(stream).text.trim()))
        } finally {
            runCatching { audio.release() }
            stream.release()
        }
    }.flowOn(asrThread)

    private companion object {
        const val TAG = "SherpaSpeech"
        const val SAMPLE_RATE = 16_000
        const val CHUNK = 1_600 // 100 ms
        const val THREADS = 2
        const val MAX_EMPTY_READS = 20
    }
}
