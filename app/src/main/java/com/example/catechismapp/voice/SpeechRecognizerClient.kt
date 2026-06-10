package com.example.catechismapp.voice

import java.util.Locale

interface SpeechRecognizerClient {
    val isAvailable: Boolean
    fun startListening(locale: Locale = Locale.getDefault())
    fun stopListening()
    fun cancel()
    fun destroy()
}
