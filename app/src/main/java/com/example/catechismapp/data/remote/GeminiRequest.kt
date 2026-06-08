package com.example.catechismapp.data.remote

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class GeminiContent(
    val role: String,  // "user" or "model"
    val parts: List<GeminiPart>
)

data class GeminiPart(val text: String)

data class GenerationConfig(
    val temperature: Float = 0.2f,   // Low temperature = faithful, grounded answers
    val maxOutputTokens: Int = 1024,
    val topP: Float = 0.8f,
    val responseMimeType: String? = null
)
