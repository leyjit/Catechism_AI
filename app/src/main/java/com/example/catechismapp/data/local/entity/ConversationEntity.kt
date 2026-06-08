package com.example.catechismapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_message")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "role") val role: String,        // "user" or "assistant"
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "paragraph_ids") val paragraphIds: String = "", // comma-separated
    @ColumnInfo(name = "verse_refs") val verseRefs: String = ""        // comma-separated
)
