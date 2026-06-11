package com.example.catechismapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_qa_pair")
data class FavoriteQaPairEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "question_content") val questionContent: String,
    @ColumnInfo(name = "answer_content") val answerContent: String,
    @ColumnInfo(name = "question_timestamp") val questionTimestamp: Long,
    @ColumnInfo(name = "answer_timestamp") val answerTimestamp: Long,
    @ColumnInfo(name = "paragraph_ids") val paragraphIds: String = "",
    @ColumnInfo(name = "verse_refs") val verseRefs: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
