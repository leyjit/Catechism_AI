package com.example.catechismapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.catechismapp.data.local.entity.BibleVerseEntity
import com.example.catechismapp.data.local.entity.BibleVerseFtsEntity
import com.example.catechismapp.data.local.entity.CatechismEntity
import com.example.catechismapp.data.local.entity.CatechismFtsEntity
import com.example.catechismapp.data.local.entity.ConversationEntity
import com.example.catechismapp.data.local.entity.FavoriteQaPairEntity

@Database(
    entities = [
        CatechismEntity::class,
        CatechismFtsEntity::class,
        BibleVerseEntity::class,
        BibleVerseFtsEntity::class,
        ConversationEntity::class,
        FavoriteQaPairEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catechismDao(): CatechismDao
    abstract fun bibleVerseDao(): BibleVerseDao
    abstract fun conversationDao(): ConversationDao
    abstract fun favoriteQaPairDao(): FavoriteQaPairDao

    companion object {
        const val DATABASE_NAME = "catechism_app.db"
    }
}
