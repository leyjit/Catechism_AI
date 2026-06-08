package com.example.catechismapp.domain.model

data class BibleVerse(
    val reference: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)
