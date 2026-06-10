package com.example.catechismapp.ui.chat

enum class InputMode {
    Voice,
    Typing,
}

fun resolveInputMode(text: String, isFieldFocused: Boolean): InputMode = when {
    text.isNotEmpty() || isFieldFocused -> InputMode.Typing
    else -> InputMode.Voice
}
