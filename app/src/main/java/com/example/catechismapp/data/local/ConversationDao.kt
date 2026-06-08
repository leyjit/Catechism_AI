package com.example.catechismapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.catechismapp.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(message: ConversationEntity): Long

    @Query("SELECT * FROM conversation_message ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation_message ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationEntity>

    @Query("DELETE FROM conversation_message")
    suspend fun clearAll()
}
