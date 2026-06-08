package com.example.catechismapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.catechismapp.data.local.entity.BibleVerseEntity

@Dao
interface BibleVerseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(verses: List<BibleVerseEntity>)

    // Look up a specific verse by book/chapter/verse
    @Query("""
        SELECT * FROM bible_verse
        WHERE book = :book AND chapter = :chapter AND verse = :verse
        LIMIT 1
    """)
    suspend fun getVerse(book: String, chapter: Int, verse: Int): BibleVerseEntity?

    // Batch lookup — used after resolving ccc_scripture_map references
    @Query("""
        SELECT * FROM bible_verse
        WHERE book IN (:books)
    """)
    suspend fun getVersesByBook(books: List<String>): List<BibleVerseEntity>

    @Query("SELECT COUNT(*) FROM bible_verse")
    suspend fun count(): Int
}
