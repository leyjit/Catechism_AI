package com.example.catechismapp.data.local

import android.content.Context
import android.util.Log
import com.example.catechismapp.data.local.entity.BibleVerseEntity
import com.example.catechismapp.data.local.entity.CatechismEntity
import com.example.catechismapp.data.preferences.UserPreferences
import com.google.gson.stream.JsonReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

class SeedingException(message: String) : Exception(message)

@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "Seeder"
        private const val CATECHISM_BATCH_SIZE = 200
        private const val BIBLE_BATCH_SIZE = 500
    }

    suspend fun seedIfNeeded() {
        if (userPreferences.isDatabaseSeeded()) {
            Log.d(TAG, "Database already seeded — skipping.")
            return
        }

        withContext(Dispatchers.IO) {
            seedCatechism()
            seedBible()
            userPreferences.setDatabaseSeeded(true)
            Log.d(TAG, "Seeding complete. DataStore flag set.")
        }
    }

    private suspend fun seedCatechism() {
        Log.d(TAG, "Seeding catechism...")
        val batch = mutableListOf<CatechismEntity>()
        var parsedCount = 0
        var insertedCount = 0
        
        context.assets.open("catechism.json").use { inputStream ->
            JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var paragraph = 0
                    var text = ""
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "paragraph" -> paragraph = reader.nextInt()
                            "text" -> text = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    
                    if (paragraph > 0 && text.isNotEmpty()) {
                        parsedCount++
                        batch.add(CatechismEntity(id = paragraph, text = text))
                        if (batch.size >= CATECHISM_BATCH_SIZE) {
                            database.catechismDao().insertAll(batch)
                            insertedCount += batch.size
                            batch.clear()
                        }
                    }
                }
                reader.endArray()
            }
        }
        
        if (batch.isNotEmpty()) {
            database.catechismDao().insertAll(batch)
            insertedCount += batch.size
        }

        val finalCount = database.catechismDao().count()
        val ftsCount = database.catechismDao().ftsCount()
        Log.d(TAG, "Catechism: parsed $parsedCount, inserted $insertedCount, final DB: $finalCount, FTS: $ftsCount")
        
        if (finalCount != 2865) {
            throw SeedingException("Catechism seeding failed: Expected 2865 rows, found $finalCount")
        }
        if (ftsCount != 2865) {
            throw SeedingException("Catechism FTS seeding failed: Expected 2865 rows, found $ftsCount")
        }
    }

    private suspend fun seedBible() {
        Log.d(TAG, "Seeding bible...")
        val batch = mutableListOf<BibleVerseEntity>()
        var parsedCount = 0
        var insertedCount = 0
        
        context.assets.open("bible.json").use { inputStream ->
            JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var book = ""
                    var chapter = 0
                    var verse = 0
                    var text = ""
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "book" -> book = reader.nextString()
                            "chapter" -> chapter = reader.nextInt()
                            "verse" -> verse = reader.nextInt()
                            "text" -> text = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    
                    if (book.isNotEmpty() && text.isNotEmpty()) {
                        parsedCount++
                        batch.add(BibleVerseEntity(book = book, chapter = chapter, verse = verse, text = text))
                        if (batch.size >= BIBLE_BATCH_SIZE) {
                            database.bibleVerseDao().insertAll(batch)
                            insertedCount += batch.size
                            batch.clear()
                        }
                    }
                }
                reader.endArray()
            }
        }
        
        if (batch.isNotEmpty()) {
            database.bibleVerseDao().insertAll(batch)
            insertedCount += batch.size
        }

        val finalCount = database.bibleVerseDao().count()
        Log.d(TAG, "Bible: parsed $parsedCount, inserted $insertedCount, final DB: $finalCount")
        if (finalCount == 0) {
            throw SeedingException("Bible seeding failed: DB count is 0 after insert")
        }
    }
}
