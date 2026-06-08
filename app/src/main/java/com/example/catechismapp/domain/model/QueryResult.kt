package com.example.catechismapp.domain.model

enum class AnswerErrorType {
    MISSING_API_KEY,
    NETWORK_ERROR,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER_ERROR,
    CONTENT_BLOCKED
}

data class QueryResult(
    val question: String,
    val paragraphs: List<CatechismParagraph>,
    val verses: List<BibleVerse>,
    val llmAnswer: String?,
    val errorType: AnswerErrorType? = null
)
