package com.example.catechismapp.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceRecognitionService(
    private val context: Context,
    private val callbacks: Callbacks,
) : SpeechRecognizerClient {
    interface Callbacks {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onEndOfSpeech()
        fun onRmsChanged(rmsdB: Float)
        fun onResults(transcript: String)
        fun onError(code: Int, message: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() {
            ensureMainThread()
            return SpeechRecognizer.isRecognitionAvailable(context)
        }

    override fun startListening(locale: Locale) {
        ensureMainThread()
        if (!isAvailable) {
            callbacks.onError(
                SpeechRecognizer.ERROR_CLIENT,
                "Speech recognition not available on this device.",
            )
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer ->
                recognizer.setRecognitionListener(createListener())
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, VoiceInputConfig.PARTIAL_RESULTS_ENABLED)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun stopListening() {
        ensureMainThread()
        speechRecognizer?.stopListening()
    }

    override fun cancel() {
        ensureMainThread()
        speechRecognizer?.cancel()
    }

    override fun destroy() {
        ensureMainThread()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            callbacks.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            callbacks.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            callbacks.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            callbacks.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            callbacks.onError(error, mapSpeechError(error))
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            callbacks.onResults(transcript)
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun ensureMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "SpeechRecognizer must be used on the main thread"
        }
    }
}
