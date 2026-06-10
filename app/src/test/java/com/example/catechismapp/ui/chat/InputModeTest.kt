package com.example.catechismapp.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class InputModeTest {

    @Test
    fun `empty unfocused field is Voice mode`() {
        assertEquals(InputMode.Voice, resolveInputMode(text = "", isFieldFocused = false))
    }

    @Test
    fun `empty focused field is Typing mode`() {
        assertEquals(InputMode.Typing, resolveInputMode(text = "", isFieldFocused = true))
    }

    @Test
    fun `non-empty unfocused field is Typing mode`() {
        assertEquals(InputMode.Typing, resolveInputMode(text = "What is grace?", isFieldFocused = false))
    }

    @Test
    fun `non-empty focused field is Typing mode`() {
        assertEquals(InputMode.Typing, resolveInputMode(text = "What is grace?", isFieldFocused = true))
    }
}
