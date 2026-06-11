package com.example.catechismapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.catechismapp.data.local.AppDatabase
import com.example.catechismapp.data.local.BibleVerseDao
import com.example.catechismapp.data.local.CatechismDao
import com.example.catechismapp.data.local.ConversationDao
import com.example.catechismapp.data.local.FavoriteQaPairDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorite_qa_pair` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `question_content` TEXT NOT NULL,
                    `answer_content` TEXT NOT NULL,
                    `question_timestamp` INTEGER NOT NULL,
                    `answer_timestamp` INTEGER NOT NULL,
                    `paragraph_ids` TEXT NOT NULL,
                    `verse_refs` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideCatechismDao(database: AppDatabase): CatechismDao {
        return database.catechismDao()
    }

    @Provides
    @Singleton
    fun provideBibleVerseDao(database: AppDatabase): BibleVerseDao {
        return database.bibleVerseDao()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideFavoriteQaPairDao(database: AppDatabase): FavoriteQaPairDao {
        return database.favoriteQaPairDao()
    }
}
