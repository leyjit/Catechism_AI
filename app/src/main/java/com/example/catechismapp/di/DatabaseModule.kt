package com.example.catechismapp.di

import android.content.Context
import androidx.room.Room
import com.example.catechismapp.data.local.AppDatabase
import com.example.catechismapp.data.local.BibleVerseDao
import com.example.catechismapp.data.local.CatechismDao
import com.example.catechismapp.data.local.ConversationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
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
}
