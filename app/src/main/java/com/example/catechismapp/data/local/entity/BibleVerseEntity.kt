package com.example.catechismapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bible_verse",
    indices = [Index(value = ["book", "chapter", "verse"], unique = true)]
)
data class BibleVerseEntity(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "book") val book: String,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "verse") val verse: Int,
    @ColumnInfo(name = "text") val text: String
)
