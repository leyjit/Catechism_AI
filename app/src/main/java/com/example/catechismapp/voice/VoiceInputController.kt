package com.example.catechismapp.voice

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoiceInputController(
    private val scope: CoroutineScope,
    private val createService: (VoiceRecognitionService.Callbacks) -> SpeechRecognizerClient,
    private val onTranscript: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var service: SpeechRecognizerClient = createService(createServiceCallbacks())

    private var _state = mutableStateOf<VoiceInputState>(VoiceInputState.Idle)
    val state: State<VoiceInputState> = _state

    private var captureTimeoutJob: Job? = null
    private var finalizationTimeoutJob: Job? = null

    val isRecognitionAvailable: Boolean
        get() = service.isAvailable

    fun onMicClick() {
        when (_state.value) {
            is VoiceInputState.Listening -> stopListening()
            is VoiceInputState.Processing -> Unit
            is VoiceInputState.Idle -> startListening()
        }
    }

    fun startListening() {
        if (_state.value !is VoiceInputState.Idle) return
        if (!service.isAvailable) {
            onError("Speech recognition not available on this device.")
            return
        }

        captureTimeoutJob?.cancel()
        captureTimeoutJob = null
        finalizationTimeoutJob?.cancel()
        finalizationTimeoutJob = null

        _state.value = VoiceInputState.Listening
        service.startListening()
        captureTimeoutJob = scope.launch {
            delay(VoiceInputConfig.CAPTURE_MAX_MS)
            if (_state.value is VoiceInputState.Listening) {
                stopListening()
            }
        }
    }

    fun stopListening() {
        if (_state.value !is VoiceInputState.Listening) return
        captureTimeoutJob?.cancel()
        captureTimeoutJob = null
        _state.value = VoiceInputState.Processing
        service.stopListening()
        startFinalizationTimeout()
    }

    fun cancel() {
        captureTimeoutJob?.cancel()
        captureTimeoutJob = null
        finalizationTimeoutJob?.cancel()
        finalizationTimeoutJob = null
        service.cancel()
        _state.value = VoiceInputState.Idle
    }

    fun destroy() {
        captureTimeoutJob?.cancel()
        captureTimeoutJob = null
        finalizationTimeoutJob?.cancel()
        finalizationTimeoutJob = null
        service.cancel()
        service.destroy()
        _state.value = VoiceInputState.Idle
    }

    private fun startFinalizationTimeout() {
        finalizationTimeoutJob?.cancel()
        finalizationTimeoutJob = scope.launch {
            delay(VoiceInputConfig.FINALIZATION_MAX_MS)
            if (_state.value is VoiceInputState.Processing) {
                service.cancel()
                _state.value = VoiceInputState.Idle
                onError("No speech detected")
            }
            finalizationTimeoutJob = null
        }
    }

    private fun completeRecognition(onSuccess: () -> Unit) {
        captureTimeoutJob?.cancel()
        captureTimeoutJob = null
        finalizationTimeoutJob?.cancel()
        finalizationTimeoutJob = null
        onSuccess()
        _state.value = VoiceInputState.Idle
    }

    private fun createServiceCallbacks(): VoiceRecognitionService.Callbacks =
        object : VoiceRecognitionService.Callbacks {
            override fun onReadyForSpeech() = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onEndOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onResults(transcript: String) {
                completeRecognition { onTranscript(transcript) }
            }

            override fun onError(code: Int, message: String) {
                completeRecognition { onError(message) }
            }
        }
}

@Composable
fun rememberVoiceInputController(
    onTranscript: (String) -> Unit,
    onError: (String) -> Unit,
): VoiceInputController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val onTranscriptState by rememberUpdatedState(onTranscript)
    val onErrorState by rememberUpdatedState(onError)

    val controller = remember(context) {
        VoiceInputController(
            scope = scope,
            createService = { callbacks ->
                VoiceRecognitionService(context, callbacks)
            },
            onTranscript = { onTranscriptState(it) },
            onError = { onErrorState(it) },
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }

    return controller
}
