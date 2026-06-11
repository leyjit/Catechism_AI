package com.example.catechismapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.catechismapp.data.local.entity.FavoriteQaPairEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteQaPairDao {

    @Query("SELECT * FROM favorite_qa_pair ORDER BY created_at DESC")
    fun getAll(): Flow<List<FavoriteQaPairEntity>>

    @Insert
    suspend fun insertAll(pairs: List<FavoriteQaPairEntity>): List<Long>

    @Query("DELETE FROM favorite_qa_pair WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)
}
