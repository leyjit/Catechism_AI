package com.example.catechismapp.data.remote

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val error: GeminiError?
)

data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String?
)

data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)
