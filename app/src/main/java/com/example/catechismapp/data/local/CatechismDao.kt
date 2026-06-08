package com.example.catechismapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.catechismapp.data.local.entity.CatechismEntity

@Dao
interface CatechismDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(paragraphs: List<CatechismEntity>)

    @Query("SELECT * FROM catechism WHERE id = :id")
    suspend fun getById(id: Int): CatechismEntity?

    @Query("SELECT * FROM catechism WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<CatechismEntity>

    // FTS search — returns paragraph IDs ranked by relevance
    // The MATCH query uses FTS4 syntax
    @Query("""
        SELECT catechism.id, catechism.text
        FROM catechism
        INNER JOIN catechism_fts ON catechism.rowid = catechism_fts.rowid
        WHERE catechism_fts MATCH :query
        LIMIT :limit
    """)
    suspend fun searchFts(query: String, limit: Int = 8): List<CatechismEntity>

    // Fallback LIKE search
    @Query("""
        SELECT * FROM catechism 
        WHERE text LIKE '%' || :query || '%' 
        LIMIT :limit
    """)
    suspend fun searchLike(query: String, limit: Int = 8): List<CatechismEntity>

    @Query("SELECT COUNT(*) FROM catechism")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM catechism_fts")
    suspend fun ftsCount(): Int
}
