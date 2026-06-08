package com.example.catechismapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catechismapp.data.local.entity.CatechismEntity
import com.example.catechismapp.data.preferences.UserPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DatabaseSeederTest — Phase 3 acceptance test.
 *
 * Validates:
 * (a) Asset field names are correctly mapped to entity data classes
 * (b) Catechism seeder produces exactly 2865 rows
 * (c) FTS count equals 2865
 * (d) Bible verse count is > 0
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var userPreferences: UserPreferences
    private lateinit var seeder: DatabaseSeeder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        
        userPreferences = UserPreferences(context)
        seeder = DatabaseSeeder(context, database, userPreferences)
        
        // Reset the data store preference for the test
        runBlocking {
            userPreferences.setDatabaseSeeded(false)
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun catechism_json_fieldNames_matchEntityDataClass() {
        val json = context.assets.open("catechism.json")
            .bufferedReader().use { it.readText() }
        assertTrue("catechism.json must contain 'paragraph' key", json.contains("\"paragraph\""))
        assertTrue("catechism.json must contain 'text' key", json.contains("\"text\""))
    }

    @Test
    fun bible_json_fieldNames_matchEntityDataClass() {
        val json = context.assets.open("bible.json")
            .bufferedReader().use { it.readText() }
        assertTrue("bible.json must contain 'book' key", json.contains("\"book\""))
        assertTrue("bible.json must contain 'chapter' key", json.contains("\"chapter\""))
        assertTrue("bible.json must contain 'verse' key", json.contains("\"verse\""))
        assertTrue("bible.json must contain 'text' key", json.contains("\"text\""))
    }

    @Test
    fun seeder_insertsRealAssets_andValidatesCounts() = runBlocking {
        // Run the actual seeder parsing the real asset files
        seeder.seedIfNeeded()

        val cccCount = database.catechismDao().count()
        val ftsCount = database.catechismDao().ftsCount()
        val bibleCount = database.bibleVerseDao().count()

        assertEquals("Catechism table must contain exactly 2865 rows", 2865, cccCount)
        assertEquals("Catechism FTS table must contain exactly 2865 rows", 2865, ftsCount)
        assertTrue("Bible verse table must contain > 0 rows", bibleCount > 0)
        assertTrue("DatabaseSeeded flag should be true", userPreferences.isDatabaseSeeded())
    }

    @Test
    fun seeder_skipsSecondLaunch_leavesCountsStable() = runBlocking {
        // First launch
        seeder.seedIfNeeded()
        
        // Mutate a seeded row to prove it doesn't get overwritten
        database.catechismDao().insertAll(listOf(CatechismEntity(id = 1, text = "Mutated test text")))
        
        // Second launch
        seeder.seedIfNeeded()
        
        // Verify it was skipped (the mutation survived)
        val mutated = database.catechismDao().getById(1)
        assertEquals("Mutated test text", mutated?.text)
    }
}
