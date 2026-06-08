package com.example.catechismapp.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catechismapp.data.local.AppDatabase
import com.example.catechismapp.data.local.DatabaseSeeder
import com.example.catechismapp.data.local.entity.CatechismEntity
import com.example.catechismapp.data.preferences.UserPreferences
import com.example.catechismapp.data.scripture.ScriptureMapLoader
import com.example.catechismapp.data.scripture.ScriptureReferenceParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchCatechismUseCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var searchUseCase: SearchCatechismUseCase
    private lateinit var scriptureUseCase: GetScriptureForParagraphsUseCase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()

        val preferences = UserPreferences(context)
        val seeder = DatabaseSeeder(context, database, preferences)

        // Seed data for tests, but ensure the DataStore flag is cleared first!
        runBlocking {
            preferences.setDatabaseSeeded(false)
            seeder.seedIfNeeded()
        }

        searchUseCase = SearchCatechismUseCase(database.catechismDao())
        
        val mapLoader = ScriptureMapLoader(context)
        scriptureUseCase = GetScriptureForParagraphsUseCase(database.bibleVerseDao(), mapLoader)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun search_baptism_returnsResults() = runBlocking {
        val results = searchUseCase("baptism")
        assertTrue("Search for 'baptism' should return >= 1 result", results.isNotEmpty())
    }

    @Test
    fun search_eucharist_returnsResults() = runBlocking {
        val results = searchUseCase("eucharist")
        assertTrue("Search for 'eucharist' should return >= 1 result", results.isNotEmpty())
    }

    @Test
    fun search_purgatory_returnsResults() = runBlocking {
        val results = searchUseCase("what does the church teach about purgatory?")
        assertTrue("Search for 'purgatory' should return >= 1 result", results.isNotEmpty())
    }

    @Test
    fun search_abortion_returnsResults() = runBlocking {
        val results = searchUseCase("abortion")
        assertTrue("Search for 'abortion' should return >= 1 result", results.isNotEmpty())
    }

    @Test
    fun search_nonsense_returnsZero_doesNotCrash() = runBlocking {
        val results = searchUseCase("xyzzzasdfqwer")
        assertTrue("Search for nonsense should return 0 results without crashing", results.isEmpty())
    }

    @Test
    fun search_partialWord_triggersLikeFallback_andReturnsResult() = runBlocking {
        // Insert a paragraph with a unique compound word
        database.catechismDao().insertAll(listOf(CatechismEntity(9999, "supercalifragilistic")))
        
        // FTS search for a partial token "supercalifrag" will fail because it's not a full token
        // and we aren't appending the '*' wildcard in QueryPreprocessor.
        // It should fallback to searchLike('%supercalifrag%') and succeed.
        val results = searchUseCase("supercalifrag")
        
        assertTrue("Fallback LIKE search should find the partial word", results.any { it.id == 9999 })
    }

    @Test
    fun getScripture_forParagraphWithCitations_returnsHydratedVerses() = runBlocking {
        // Paragraph 2 has known scripture citations in ccc_scripture_map.json (e.g. Matthew 28:19)
        val verses = scriptureUseCase(listOf(2))
        
        assertTrue("Should resolve and hydrate at least one Bible verse", verses.isNotEmpty())
        assertTrue("Verse text should be hydrated", verses.first().text.isNotEmpty())
    }
}
