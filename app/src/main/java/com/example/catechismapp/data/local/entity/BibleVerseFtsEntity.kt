package com.example.catechismapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = BibleVerseEntity::class)
@Entity(tableName = "bible_verse_fts")
data class BibleVerseFtsEntity(
    @ColumnInfo(name = "text") val text: String
)
