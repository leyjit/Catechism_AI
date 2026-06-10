package com.example.catechismapp.voice

import android.speech.SpeechRecognizer

fun mapSpeechError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error. Check connection."
    SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy — try again."
    else -> "Could not recognize speech."
}
