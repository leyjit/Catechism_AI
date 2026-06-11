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

    @Insert
    suspend fun insertAll(messages: List<ConversationEntity>)

    @Query("SELECT * FROM conversation_message ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation_message ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationEntity>

    @Query("DELETE FROM conversation_message")
    suspend fun clearAll()

    @Query("SELECT * FROM conversation_message WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<Int>): List<ConversationEntity>

    @Query("DELETE FROM conversation_message WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)
}
