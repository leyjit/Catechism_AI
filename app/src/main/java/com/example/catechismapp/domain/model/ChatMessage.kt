package com.example.catechismapp.domain.model

data class ChatMessage(
    val id: Int = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val paragraphs: List<CatechismParagraph> = emptyList(),
    val verses: List<BibleVerse> = emptyList()
) {
    val isUser: Boolean get() = role == "user"
}
