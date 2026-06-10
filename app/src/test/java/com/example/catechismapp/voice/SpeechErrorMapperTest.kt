package com.example.catechismapp.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechErrorMapperTest {

    @Test
    fun `maps no match`() {
        assertEquals(
            "No speech detected. Try again.",
            mapSpeechError(SpeechRecognizer.ERROR_NO_MATCH),
        )
    }

    @Test
    fun `maps speech timeout`() {
        assertEquals(
            "Listening timed out.",
            mapSpeechError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT),
        )
    }

    @Test
    fun `maps network errors`() {
        assertEquals(
            "Network error. Check connection.",
            mapSpeechError(SpeechRecognizer.ERROR_NETWORK),
        )
        assertEquals(
            "Network error. Check connection.",
            mapSpeechError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT),
        )
    }

    @Test
    fun `maps unknown code to generic message`() {
        assertEquals("Could not recognize speech.", mapSpeechError(-999))
    }
}
