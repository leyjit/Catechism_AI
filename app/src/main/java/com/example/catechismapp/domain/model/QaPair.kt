package com.example.catechismapp.domain.model

data class QaPair(
    val id: Int,
    val question: ChatMessage,
    val answer: ChatMessage?
)
