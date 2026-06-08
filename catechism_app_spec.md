# Catholic Catechetical Assistant — Android App
## Complete Implementation Specification for a Coding Agent

**Version:** 1.0  
**Target Device:** Samsung Galaxy A12 and above (Android 8.0+ / API 26+)  
**Architecture:** Thin-client RAG — local FTS retrieval + cloud LLM generation  
**Primary Language:** Kotlin  
**UI Framework:** Jetpack Compose

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture Summary](#2-architecture-summary)
3. [Source Data Files](#3-source-data-files)
4. [Project Structure](#4-project-structure)
5. [Dependencies (build.gradle)](#5-dependencies)
6. [Data Layer — Room Database](#6-data-layer--room-database)
7. [Asset Ingestion — First-Launch Seeding](#7-asset-ingestion--first-launch-seeding)
8. [Search Layer — FTS5 Query Logic](#8-search-layer--fts5-query-logic)
9. [LLM Integration — Gemini Flash API](#9-llm-integration--gemini-flash-api)
10. [Prompt Engineering](#10-prompt-engineering)
11. [Repository & Use Cases](#11-repository--use-cases)
12. [ViewModel](#12-viewmodel)
13. [UI — Jetpack Compose Screens](#13-ui--jetpack-compose-screens)
14. [Navigation](#14-navigation)
15. [Offline Fallback](#15-offline-fallback)
16. [Settings & API Key Management](#16-settings--api-key-management)
17. [Conversation History](#17-conversation-history)
18. [Error Handling](#18-error-handling)
19. [Performance Constraints](#19-performance-constraints)
20. [Testing Checklist](#20-testing-checklist)
21. [Known Assumptions & Open Questions](#21-known-assumptions--open-questions)

---

## 1. Project Overview

This is a Catholic catechetical and apologetics assistant for Android. It answers natural-language questions about Catholic doctrine by grounding every response in the actual text of the Catechism of the Catholic Church (CCC) and the Scripture passages the CCC cites. It is not a general-purpose chatbot.

**Core user flow:**
1. User types a doctrinal question (e.g., "What does the Church teach about Purgatory?")
2. App performs full-text search on the local CCC database → retrieves top 5–8 relevant paragraphs
3. App looks up Scripture references for those paragraphs via `ccc_scripture_map.json`
4. App fetches the actual verse texts from the local Bible database
5. App sends the question + CCC paragraphs + Scripture texts to the cloud LLM
6. LLM composes a grounded, cited answer referencing CCC paragraph numbers
7. Answer is displayed with expandable source cards for each CCC paragraph and each Scripture verse

**What this app is NOT:**
- Not a general AI chatbot
- Not a Bible reader app
- Not a prayer app
- Responses must never go beyond what the provided CCC paragraphs support

---

## 2. Architecture Summary

```
┌─────────────────────────────────────────────────────┐
│                   Android App                        │
│                                                      │
│  ┌──────────────┐     ┌────────────────────────┐    │
│  │  Compose UI  │────▶│      ViewModel         │    │
│  └──────────────┘     └──────────┬─────────────┘    │
│                                  │                   │
│                      ┌───────────▼──────────────┐   │
│                      │      Repository          │   │
│                      └──┬──────────────────┬────┘   │
│                         │                  │        │
│            ┌────────────▼───┐    ┌─────────▼──────┐ │
│            │  Local DB      │    │  LLM API Client│ │
│            │  (Room + FTS5) │    │  (Gemini Flash)│ │
│            └────────────────┘    └────────────────┘ │
│                    ▲                                 │
│            ┌───────┴──────────┐                     │
│            │  Assets (JSON)   │                     │
│            │  catechism.json  │                     │
│            │  bible.json      │                     │
│            │  ccc_scripture_  │                     │
│            │  map.json        │                     │
│            └──────────────────┘                     │
└─────────────────────────────────────────────────────┘
```

**Key design principle:** The device only does fast SQLite text search and a single HTTPS call. All AI reasoning happens in the cloud. The JSON files are the source of truth — the LLM is only a formatter/reasoner over content the device retrieves.

---

## 3. Source Data Files

Place all three files in `app/src/main/assets/`.

### 3.1 `catechism.json`

A JSON array of objects, one per CCC paragraph:

```json
[
  {
    "id": 1,
    "text": "God, infinitely perfect and blessed in himself, in a plan of sheer goodness freely created man to make him share in his own blessed life..."
  },
  {
    "id": 2,
    "text": "Therefore, the Father revealed himself fully by sending his own Son..."
  }
]
```

- `id`: Integer paragraph number (1–2865)
- `text`: Full paragraph text as a plain string

> **Note:** If the actual `catechism.json` in the project uses different field names (e.g., `paragraph_number` instead of `id`, or `content` instead of `text`), adjust the data model and parser accordingly. Verify against the actual file before seeding.

### 3.2 `bible.json`

A JSON array of objects, one per verse:

```json
[
  {
    "book": "Genesis",
    "chapter": 1,
    "verse": 1,
    "text": "In the beginning God created the heavens and the earth."
  }
]
```

- `book`: Book name as a string (must match the book names used in `ccc_scripture_map.json`, e.g., "Matthew", "Genesis", "1 Corinthians")
- `chapter`: Integer chapter number
- `verse`: Integer verse number
- `text`: Verse text as a plain string

> **Note:** Verify the actual field names and book-name format in the project's `bible.json` against the scripture reference strings in `ccc_scripture_map.json` (e.g., "1 Corinthians 9:22", "Psalms 105:3"). The lookup logic depends on exact string matching of book names. If there are mismatches (e.g., "Psalm" vs "Psalms"), add a normalization map in the parser.

### 3.3 `ccc_scripture_map.json`

A JSON object (dictionary) mapping CCC paragraph numbers to lists of scripture references:

```json
{
  "1": [],
  "2": ["Matthew 28:19", "Matthew 28:20"],
  "3": ["Acts 2:42"],
  "29": ["Genesis 3:8", "Genesis 3:9", "Matthew 13:22"],
  "57": ["Genesis 9:16", "Romans 1:18", "Romans 1:19", "Acts 17:26"]
}
```

- Keys: Paragraph numbers as strings ("1" through "2865")
- Values: Arrays of scripture reference strings in the format `"BookName Chapter:Verse"`
- Empty arrays `[]` are valid (many paragraphs have no scripture citations)
- Total entries: 2,865 keys, 5,216 total scripture references

This file is small enough to be loaded fully into memory as a `Map<Int, List<String>>` at app startup.

---

## 4. Project Structure

```
app/
├── src/main/
│   ├── assets/
│   │   ├── catechism.json
│   │   ├── bible.json
│   │   └── ccc_scripture_map.json
│   ├── java/com/example/catechismapp/
│   │   ├── CatechismApp.kt               # Application class, Hilt entry point
│   │   ├── MainActivity.kt               # Single-activity host
│   │   │
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt        # Room database definition
│   │   │   │   ├── CatechismDao.kt       # DAO for CCC paragraphs
│   │   │   │   ├── BibleVerseDao.kt      # DAO for Bible verses
│   │   │   │   ├── ConversationDao.kt    # DAO for conversation history
│   │   │   │   ├── entity/
│   │   │   │   │   ├── CatechismEntity.kt
│   │   │   │   │   ├── CatechismFtsEntity.kt
│   │   │   │   │   ├── BibleVerseEntity.kt
│   │   │   │   │   ├── BibleVerseFtsEntity.kt
│   │   │   │   │   └── ConversationEntity.kt
│   │   │   │   └── DatabaseSeeder.kt     # First-launch JSON → SQLite seeder
│   │   │   │
│   │   │   ├── remote/
│   │   │   │   ├── GeminiApiService.kt   # Retrofit interface
│   │   │   │   ├── GeminiRequest.kt      # Request data classes
│   │   │   │   └── GeminiResponse.kt     # Response data classes
│   │   │   │
│   │   │   ├── preferences/
│   │   │   │   └── UserPreferences.kt    # DataStore for API key + settings
│   │   │   │
│   │   │   └── scripture/
│   │   │       └── ScriptureMapLoader.kt # Loads ccc_scripture_map.json into memory
│   │   │
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── CatechismParagraph.kt
│   │   │   │   ├── BibleVerse.kt
│   │   │   │   ├── ChatMessage.kt
│   │   │   │   └── QueryResult.kt        # Holds paragraphs + verses for a query
│   │   │   ├── repository/
│   │   │   │   └── CatechismRepository.kt
│   │   │   └── usecase/
│   │   │       ├── SearchCatechismUseCase.kt
│   │   │       ├── GetScriptureForParagraphsUseCase.kt
│   │   │       └── AskDoctrinalQuestionUseCase.kt
│   │   │
│   │   ├── ui/
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreen.kt
│   │   │   │   ├── ChatViewModel.kt
│   │   │   │   └── components/
│   │   │   │       ├── MessageBubble.kt
│   │   │   │       ├── CccParagraphCard.kt
│   │   │   │       └── ScriptureVerseCard.kt
│   │   │   ├── search/
│   │   │   │   ├── SearchScreen.kt       # Browse/search CCC directly
│   │   │   │   └── SearchViewModel.kt
│   │   │   ├── settings/
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── SettingsViewModel.kt
│   │   │   └── theme/
│   │   │       ├── Theme.kt
│   │   │       ├── Color.kt
│   │   │       └── Type.kt
│   │   │
│   │   └── di/
│   │       ├── DatabaseModule.kt         # Hilt: provides Room DB, DAOs
│   │       ├── NetworkModule.kt          # Hilt: provides Retrofit, OkHttp
│   │       └── RepositoryModule.kt       # Hilt: provides Repository
│   │
│   └── res/
│       └── ...standard Android resources
│
├── build.gradle.kts (app-level)
└── build.gradle.kts (project-level)
```

---

## 5. Dependencies

### `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.catechismapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Room (with FTS5 support — included in room-runtime)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (for API key and settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Hilt (dependency injection)
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Retrofit + OkHttp (for LLM API calls)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson (JSON parsing for asset seeding)
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

### `project-level build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" apply false
}
```

---

## 6. Data Layer — Room Database

### 6.1 CatechismEntity

```kotlin
// data/local/entity/CatechismEntity.kt
@Entity(tableName = "catechism")
data class CatechismEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "text") val text: String
)

// FTS virtual table — mirrors catechism for full-text search
@Fts4(contentEntity = CatechismEntity::class)
@Entity(tableName = "catechism_fts")
data class CatechismFtsEntity(
    @ColumnInfo(name = "text") val text: String
)
```

> Use `@Fts4` rather than `@Fts5` — Room's FTS5 annotation support has been inconsistent across versions. FTS4 is fully supported and performs well enough for 2,865 rows.

### 6.2 BibleVerseEntity

```kotlin
// data/local/entity/BibleVerseEntity.kt
@Entity(
    tableName = "bible_verse",
    indices = [Index(value = ["book", "chapter", "verse"], unique = true)]
)
data class BibleVerseEntity(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "book") val book: String,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "verse") val verse: Int,
    @ColumnInfo(name = "text") val text: String
)

@Fts4(contentEntity = BibleVerseEntity::class)
@Entity(tableName = "bible_verse_fts")
data class BibleVerseFtsEntity(
    @ColumnInfo(name = "text") val text: String
)
```

### 6.3 ConversationEntity

```kotlin
// data/local/entity/ConversationEntity.kt
@Entity(tableName = "conversation_message")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "role") val role: String,        // "user" or "assistant"
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "paragraph_ids") val paragraphIds: String = "", // comma-separated
    @ColumnInfo(name = "verse_refs") val verseRefs: String = ""        // comma-separated
)
```

### 6.4 CatechismDao

```kotlin
// data/local/CatechismDao.kt
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

    @Query("SELECT COUNT(*) FROM catechism")
    suspend fun count(): Int
}
```

### 6.5 BibleVerseDao

```kotlin
// data/local/BibleVerseDao.kt
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
```

### 6.6 ConversationDao

```kotlin
// data/local/ConversationDao.kt
@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(message: ConversationEntity): Long

    @Query("SELECT * FROM conversation_message ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation_message ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationEntity>

    @Query("DELETE FROM conversation_message")
    suspend fun clearAll()
}
```

### 6.7 AppDatabase

```kotlin
// data/local/AppDatabase.kt
@Database(
    entities = [
        CatechismEntity::class,
        CatechismFtsEntity::class,
        BibleVerseEntity::class,
        BibleVerseFtsEntity::class,
        ConversationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catechismDao(): CatechismDao
    abstract fun bibleVerseDao(): BibleVerseDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        const val DATABASE_NAME = "catechism_app.db"
    }
}
```

---

## 7. Asset Ingestion — First-Launch Seeding

This is the most critical startup component. On first launch, the app reads the three JSON asset files and populates the Room database. This should happen once and be gated by a flag in DataStore.

### 7.1 ScriptureMapLoader

```kotlin
// data/scripture/ScriptureMapLoader.kt
class ScriptureMapLoader @Inject constructor(
    private val context: Context
) {
    // Loaded once into memory at startup — the map is ~200KB, entirely acceptable
    private var scriptureMap: Map<Int, List<String>>? = null

    fun load(): Map<Int, List<String>> {
        if (scriptureMap != null) return scriptureMap!!

        val json = context.assets.open("ccc_scripture_map.json")
            .bufferedReader().use { it.readText() }

        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val raw: Map<String, List<String>> = Gson().fromJson(json, type)

        scriptureMap = raw.mapKeys { it.key.toInt() }
        return scriptureMap!!
    }

    fun getReferencesForParagraph(paragraphId: Int): List<String> {
        return scriptureMap?.get(paragraphId) ?: emptyList()
    }

    fun getReferencesForParagraphs(paragraphIds: List<Int>): List<String> {
        return paragraphIds.flatMap { getReferencesForParagraph(it) }.distinct()
    }
}
```

### 7.2 ScriptureReferenceParser

The scripture references in `ccc_scripture_map.json` follow the format `"BookName Chapter:Verse"` (e.g., `"1 Corinthians 9:22"`, `"Psalms 105:3"`). This parser splits them into components for database lookups.

```kotlin
// data/scripture/ScriptureReferenceParser.kt
data class ParsedReference(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val original: String
)

object ScriptureReferenceParser {

    // Books with numeric prefixes need special handling
    private val MULTI_WORD_BOOKS = setOf(
        "1 Samuel", "2 Samuel", "1 Kings", "2 Kings",
        "1 Chronicles", "2 Chronicles",
        "1 Maccabees", "2 Maccabees",
        "1 Corinthians", "2 Corinthians",
        "1 Thessalonians", "2 Thessalonians",
        "1 Timothy", "2 Timothy",
        "1 Peter", "2 Peter",
        "1 John", "2 John", "3 John",
        "Song of Songs", "Song of Solomon",
        "Wisdom of Solomon"
    )

    fun parse(reference: String): ParsedReference? {
        // Try multi-word book names first (longest match)
        for (book in MULTI_WORD_BOOKS.sortedByDescending { it.length }) {
            if (reference.startsWith(book)) {
                val rest = reference.removePrefix(book).trim()
                val parts = rest.split(":")
                if (parts.size == 2) {
                    return ParsedReference(
                        book = book,
                        chapter = parts[0].trim().toIntOrNull() ?: return null,
                        verse = parts[1].trim().toIntOrNull() ?: return null,
                        original = reference
                    )
                }
            }
        }

        // Single-word book name
        val spaceIdx = reference.lastIndexOf(' ')
        if (spaceIdx < 0) return null
        val book = reference.substring(0, spaceIdx).trim()
        val chapterVerse = reference.substring(spaceIdx + 1).trim()
        val parts = chapterVerse.split(":")
        if (parts.size != 2) return null

        return ParsedReference(
            book = book,
            chapter = parts[0].toIntOrNull() ?: return null,
            verse = parts[1].toIntOrNull() ?: return null,
            original = reference
        )
    }
}
```

> **Important:** After first seeding, run a validation pass to confirm that book names in `bible.json` match the book names in `ccc_scripture_map.json`. Common mismatches to watch for: "Psalms" vs "Psalm", "Song of Songs" vs "Song of Solomon", "Revelation" vs "Revelations". Add entries to `MULTI_WORD_BOOKS` and normalization logic as needed.

### 7.3 DatabaseSeeder

```kotlin
// data/local/DatabaseSeeder.kt
class DatabaseSeeder @Inject constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val CATECHISM_BATCH_SIZE = 200
        private const val BIBLE_BATCH_SIZE = 500
    }

    suspend fun seedIfNeeded() {
        if (userPreferences.isDatabaseSeeded()) return

        withContext(Dispatchers.IO) {
            seedCatechism()
            seedBible()
            userPreferences.setDatabaseSeeded(true)
        }
    }

    private suspend fun seedCatechism() {
        val json = context.assets.open("catechism.json")
            .bufferedReader().use { it.readText() }

        // Adjust field names here if actual catechism.json uses different keys
        data class RawParagraph(val id: Int, val text: String)
        val type = object : TypeToken<List<RawParagraph>>() {}.type
        val raw: List<RawParagraph> = Gson().fromJson(json, type)

        val entities = raw.map { CatechismEntity(id = it.id, text = it.text) }

        entities.chunked(CATECHISM_BATCH_SIZE).forEach { batch ->
            database.catechismDao().insertAll(batch)
        }
    }

    private suspend fun seedBible() {
        val json = context.assets.open("bible.json")
            .bufferedReader().use { it.readText() }

        // Adjust field names here if actual bible.json uses different keys
        data class RawVerse(
            val book: String,
            val chapter: Int,
            val verse: Int,
            val text: String
        )
        val type = object : TypeToken<List<RawVerse>>() {}.type
        val raw: List<RawVerse> = Gson().fromJson(json, type)

        val entities = raw.map {
            BibleVerseEntity(
                book = it.book,
                chapter = it.chapter,
                verse = it.verse,
                text = it.text
            )
        }

        entities.chunked(BIBLE_BATCH_SIZE).forEach { batch ->
            database.bibleVerseDao().insertAll(batch)
        }
    }
}
```

### 7.4 Startup Sequence in Application Class

```kotlin
// CatechismApp.kt
@HiltAndroidApp
class CatechismApp : Application() {

    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var scriptureMapLoader: ScriptureMapLoader

    override fun onCreate() {
        super.onCreate()

        // Seed database on background thread — does nothing after first launch
        applicationScope.launch {
            databaseSeeder.seedIfNeeded()
            scriptureMapLoader.load()  // Load scripture map into memory
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
```

---

## 8. Search Layer — FTS5 Query Logic

FTS4 full-text search is the engine that replaces local embeddings. For a doctrinal question, this works very well because: (a) users naturally use the same vocabulary as the CCC, (b) the CCC is highly consistent in its terminology, and (c) we only need the top 5–8 paragraphs to give the LLM sufficient context.

### 8.1 Query Preprocessing

Before running FTS, preprocess the user's question to extract meaningful search terms:

```kotlin
// domain/usecase/SearchCatechismUseCase.kt
object QueryPreprocessor {

    // Words that are common but meaningless for CCC search
    private val STOP_WORDS = setOf(
        "what", "does", "the", "church", "teach", "about", "is", "are",
        "how", "why", "when", "where", "who", "a", "an", "and", "or",
        "in", "of", "to", "for", "on", "with", "do", "can", "i", "me",
        "my", "we", "our", "be", "have", "has", "was", "were", "will"
    )

    fun buildFtsQuery(userQuestion: String): String {
        val words = userQuestion
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(" ")
            .filter { it.length > 2 && it !in STOP_WORDS }
            .distinct()
            .take(8)  // FTS4 performs well with 3–8 terms

        // FTS4 OR query: returns paragraphs matching any term, ranked by match count
        return words.joinToString(" OR ")
    }
}
```

### 8.2 SearchCatechismUseCase

```kotlin
// domain/usecase/SearchCatechismUseCase.kt
class SearchCatechismUseCase @Inject constructor(
    private val catechismDao: CatechismDao
) {
    suspend operator fun invoke(question: String, maxResults: Int = 8): List<CatechismParagraph> {
        val ftsQuery = QueryPreprocessor.buildFtsQuery(question)

        if (ftsQuery.isBlank()) return emptyList()

        return catechismDao.searchFts(ftsQuery, maxResults)
            .map { entity ->
                CatechismParagraph(
                    id = entity.id,
                    text = entity.text
                )
            }
    }
}
```

### 8.3 GetScriptureForParagraphsUseCase

```kotlin
// domain/usecase/GetScriptureForParagraphsUseCase.kt
class GetScriptureForParagraphsUseCase @Inject constructor(
    private val bibleVerseDao: BibleVerseDao,
    private val scriptureMapLoader: ScriptureMapLoader
) {
    suspend operator fun invoke(paragraphIds: List<Int>): List<BibleVerse> {
        val references = scriptureMapLoader.getReferencesForParagraphs(paragraphIds)

        return references.mapNotNull { ref ->
            val parsed = ScriptureReferenceParser.parse(ref) ?: return@mapNotNull null
            val entity = bibleVerseDao.getVerse(
                book = parsed.book,
                chapter = parsed.chapter,
                verse = parsed.verse
            ) ?: return@mapNotNull null

            BibleVerse(
                reference = ref,
                book = entity.book,
                chapter = entity.chapter,
                verse = entity.verse,
                text = entity.text
            )
        }
    }
}
```

---

## 9. LLM Integration — Gemini Flash API

### 9.1 API Choice & Configuration

**Primary:** Google Gemini 2.0 Flash (free tier, generous rate limits, fast)  
**Fallback:** Groq (Llama 3.1 — also free, very fast)  
**API key storage:** User-entered in Settings screen, persisted in DataStore (encrypted at rest via Android Keystore if targeting API 23+)

Gemini Flash endpoint:  
`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent`

### 9.2 Retrofit Service

```kotlin
// data/remote/GeminiApiService.kt
interface GeminiApiService {

    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
```

### 9.3 Request/Response Models

```kotlin
// data/remote/GeminiRequest.kt
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class GeminiContent(
    val role: String,  // "user" or "model"
    val parts: List<GeminiPart>
)

data class GeminiPart(val text: String)

data class GenerationConfig(
    val temperature: Float = 0.2f,   // Low temperature = faithful, grounded answers
    val maxOutputTokens: Int = 1024,
    val topP: Float = 0.8f
)

// data/remote/GeminiResponse.kt
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val error: GeminiError?
)

data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String?
)

data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)
```

### 9.4 NetworkModule (Hilt)

```kotlin
// di/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // LLM responses can take 5–15s
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG)
                    HttpLoggingInterceptor.Level.BODY
                else
                    HttpLoggingInterceptor.Level.NONE
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGeminiApiService(retrofit: Retrofit): GeminiApiService {
        return retrofit.create(GeminiApiService::class.java)
    }
}
```

---

## 10. Prompt Engineering

The system prompt and user prompt are the most important elements of this app. They determine whether the LLM stays grounded in the CCC or drifts into generic AI responses.

### 10.1 System Prompt (fixed, never changes)

```
You are a Catholic catechetical assistant. Your sole purpose is to help users understand Catholic doctrine as taught by the Catechism of the Catholic Church (CCC).

STRICT RULES:
1. Answer ONLY based on the CCC paragraphs provided in the CONTEXT section below. Do not draw on any other source or prior knowledge.
2. If the provided CCC paragraphs do not address the user's question, say: "The CCC paragraphs retrieved for this question do not directly address this topic. Try rephrasing your question, or look up the CCC index directly."
3. ALWAYS cite specific CCC paragraph numbers using the format (CCC §123) inline in your answer.
4. When Scripture is provided in the SCRIPTURE section, you may reference specific verses to support the CCC teaching. Format as (Book Chapter:Verse).
5. Keep your answer clear, faithful, and appropriately concise. Avoid speculation, personal opinions, or theological positions not found in the provided paragraphs.
6. If the user asks something outside Catholic doctrine (e.g., political opinions, personal advice, non-doctrinal matters), politely redirect: "This app is designed specifically for questions about Catholic teaching as found in the Catechism."
7. Write in a warm, pastoral tone suitable for both new Catholics and those seeking to deepen their faith.
```

### 10.2 User Turn Construction

```kotlin
// domain/usecase/AskDoctrinalQuestionUseCase.kt — prompt builder

fun buildUserPrompt(
    question: String,
    paragraphs: List<CatechismParagraph>,
    verses: List<BibleVerse>
): String {
    val sb = StringBuilder()

    sb.appendLine("CONTEXT — CCC Paragraphs retrieved for this question:")
    sb.appendLine()
    paragraphs.forEach { para ->
        sb.appendLine("CCC §${para.id}:")
        sb.appendLine(para.text)
        sb.appendLine()
    }

    if (verses.isNotEmpty()) {
        sb.appendLine("SCRIPTURE — Verses cited by the above CCC paragraphs:")
        sb.appendLine()
        verses.forEach { verse ->
            sb.appendLine("${verse.reference}: ${verse.text}")
        }
        sb.appendLine()
    }

    sb.appendLine("USER QUESTION:")
    sb.appendLine(question)

    return sb.toString()
}
```

### 10.3 Multi-Turn Context

For follow-up questions, include the last 2 turns of conversation as context. This is sufficient to handle clarifying questions without inflating the prompt size.

```kotlin
fun buildConversationHistory(
    recentMessages: List<ConversationEntity>,
    maxTurns: Int = 2
): List<GeminiContent> {
    // Each "turn" is one user + one assistant message = 2 entities
    val turns = recentMessages.takeLast(maxTurns * 2)

    return turns.map { msg ->
        GeminiContent(
            role = if (msg.role == "user") "user" else "model",
            parts = listOf(GeminiPart(msg.content))
        )
    }
}
```

---

## 11. Repository & Use Cases

### 11.1 Domain Models

```kotlin
// domain/model/CatechismParagraph.kt
data class CatechismParagraph(
    val id: Int,
    val text: String
)

// domain/model/BibleVerse.kt
data class BibleVerse(
    val reference: String,   // e.g., "Romans 3:23"
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

// domain/model/QueryResult.kt
data class QueryResult(
    val paragraphs: List<CatechismParagraph>,
    val verses: List<BibleVerse>
)

// domain/model/ChatMessage.kt
data class ChatMessage(
    val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceParagraphs: List<CatechismParagraph> = emptyList(),
    val sourceVerses: List<BibleVerse> = emptyList()
)

enum class MessageRole { USER, ASSISTANT, ERROR }
```

### 11.2 CatechismRepository

```kotlin
// domain/repository/CatechismRepository.kt
class CatechismRepository @Inject constructor(
    private val catechismDao: CatechismDao,
    private val bibleVerseDao: BibleVerseDao,
    private val conversationDao: ConversationDao,
    private val geminiApiService: GeminiApiService,
    private val userPreferences: UserPreferences,
    private val searchCatechismUseCase: SearchCatechismUseCase,
    private val getScriptureUseCase: GetScriptureForParagraphsUseCase
) {
    val conversationHistory: Flow<List<ConversationEntity>> =
        conversationDao.getAllMessages()

    suspend fun askQuestion(question: String): Result<ChatMessage> {
        return try {
            // 1. Save user message
            conversationDao.insert(
                ConversationEntity(role = "user", content = question)
            )

            // 2. Retrieve relevant CCC paragraphs via FTS
            val paragraphs = searchCatechismUseCase(question)

            // 3. Get scripture references for retrieved paragraphs
            val verses = getScriptureUseCase(paragraphs.map { it.id })

            // 4. Build prompt
            val userPrompt = buildUserPrompt(question, paragraphs, verses)

            // 5. Get recent history for multi-turn context
            val recentHistory = conversationDao.getRecentMessages(4)
                .dropLast(1)  // exclude the message we just inserted
                .let { buildConversationHistory(it) }

            // 6. Call LLM
            val apiKey = userPreferences.getApiKey()
                ?: return Result.failure(Exception("No API key configured. Please add your Gemini API key in Settings."))

            val systemInstruction = GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(SYSTEM_PROMPT))
            )

            val allContents = recentHistory + listOf(
                GeminiContent(role = "user", parts = listOf(GeminiPart(userPrompt)))
            )

            val request = GeminiRequest(
                contents = allContents,
                systemInstruction = systemInstruction,
                generationConfig = GenerationConfig(temperature = 0.2f)
            )

            val response = geminiApiService.generateContent(apiKey, request)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                return Result.failure(Exception("API error ${response.code()}: $errorBody"))
            }

            val responseBody = response.body()
            if (responseBody?.error != null) {
                return Result.failure(Exception(responseBody.error.message))
            }

            val answerText = responseBody?.candidates
                ?.firstOrNull()
                ?.content?.parts
                ?.firstOrNull()?.text
                ?: return Result.failure(Exception("Empty response from AI"))

            // 7. Save assistant message with source references
            conversationDao.insert(
                ConversationEntity(
                    role = "assistant",
                    content = answerText,
                    paragraphIds = paragraphs.map { it.id }.joinToString(","),
                    verseRefs = verses.map { it.reference }.joinToString(",")
                )
            )

            // 8. Return domain model
            Result.success(
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = answerText,
                    sourceParagraphs = paragraphs,
                    sourceVerses = verses
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearConversation() {
        conversationDao.clearAll()
    }
}
```

---

## 12. ViewModel

```kotlin
// ui/chat/ChatViewModel.kt
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: CatechismRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = repository.conversationHistory
        .map { entities -> entities.toDomainModels() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(question: String) {
        if (question.isBlank()) return
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = repository.askQuestion(question)

            _uiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(isLoading = false, error = null) },
                    onFailure = { e -> state.copy(isLoading = false, error = e.message) }
                )
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
```

---

## 13. UI — Jetpack Compose Screens

### 13.1 ChatScreen

The main screen. Layout:
- Top bar: App title ("Catholic Catechist"), clear conversation button
- Message list: scrollable, user bubbles right-aligned, assistant bubbles left-aligned
- Each assistant message has an expandable "Sources" section showing CCC paragraph cards and Scripture verse cards
- Bottom: text input + send button, disabled while loading
- Loading indicator: centered circular progress when `isLoading = true`

```kotlin
// ui/chat/ChatScreen.kt
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catholic Catechist") },
                actions = {
                    IconButton(onClick = { viewModel.clearConversation() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear conversation")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                isEnabled = !uiState.isLoading
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.dismissError() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
```

### 13.2 MessageBubble

```kotlin
// ui/chat/components/MessageBubble.kt
@Composable
fun MessageBubble(message: ChatMessage) {
    var sourcesExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.role == MessageRole.USER)
            Alignment.End else Alignment.Start
    ) {
        // Message bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.role == MessageRole.USER) 16.dp else 4.dp,
                bottomEnd = if (message.role == MessageRole.USER) 4.dp else 16.dp
            ),
            color = if (message.role == MessageRole.USER)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.role == MessageRole.USER)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Sources section (assistant messages only)
        if (message.role == MessageRole.ASSISTANT &&
            (message.sourceParagraphs.isNotEmpty() || message.sourceVerses.isNotEmpty())) {

            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { sourcesExpanded = !sourcesExpanded }
            ) {
                Text(
                    if (sourcesExpanded) "Hide Sources ▲" else "Show Sources ▼",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            AnimatedVisibility(visible = sourcesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.sourceParagraphs.isNotEmpty()) {
                        Text(
                            "CCC Paragraphs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        message.sourceParagraphs.forEach { para ->
                            CccParagraphCard(paragraph = para)
                        }
                    }
                    if (message.sourceVerses.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Scripture",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        message.sourceVerses.forEach { verse ->
                            ScriptureVerseCard(verse = verse)
                        }
                    }
                }
            }
        }
    }
}
```

### 13.3 CccParagraphCard

```kotlin
// ui/chat/components/CccParagraphCard.kt
@Composable
fun CccParagraphCard(paragraph: CatechismParagraph) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "CCC §${paragraph.id}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = paragraph.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                // Preview: first 120 characters
                Text(
                    text = paragraph.text.take(120) + if (paragraph.text.length > 120) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

### 13.4 ScriptureVerseCard

```kotlin
// ui/chat/components/ScriptureVerseCard.kt
@Composable
fun ScriptureVerseCard(verse: BibleVerse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = verse.reference,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = verse.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
```

### 13.5 SettingsScreen

```kotlin
// ui/settings/SettingsScreen.kt
// This screen allows the user to enter and save their Gemini API key.
// Fields:
//   - API Key text field (obscured, with show/hide toggle)
//   - "Save" button
//   - Link text: "Get a free Gemini API key at aistudio.google.com"
//   - Model selector (optional): Gemini Flash (default) / Groq
//   - "Clear conversation history" button
//   - App version display
//
// The API key is saved to DataStore via SettingsViewModel.
// Show a success Snackbar on save.
```

---

## 14. Navigation

```kotlin
// MainActivity.kt / NavGraph
sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object Splash : Screen("splash")  // First-launch seeding progress
}

// NavHost setup:
// Start destination: Splash if !isDatabaseSeeded, else Chat
// Splash → Chat (automatic after seeding completes)
// Chat → Settings (settings icon in top bar)
// Settings → Chat (back button)
```

### Splash/Seeding Screen

Show this screen on first launch while the database is being seeded. Display a progress indicator and the text "Preparing your Catechism library… (first launch only)". After seeding completes, navigate to Chat automatically.

---

## 15. Offline Fallback

When there is no network connection or the LLM API call fails, the app should still be useful:

1. Run the FTS search as normal
2. Retrieve the CCC paragraphs and Scripture verses
3. Instead of displaying an LLM-generated answer, display the raw paragraphs directly with the message: *"No internet connection. Here are the CCC paragraphs most relevant to your question:"*
4. Show the full CccParagraphCard list and ScriptureVerseCard list

This makes the app a functioning CCC reference tool even completely offline.

Check connectivity before the API call:

```kotlin
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
```

---

## 16. Settings & API Key Management

```kotlin
// data/preferences/UserPreferences.kt
class UserPreferences @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.createDataStore(name = "user_prefs")

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val DATABASE_SEEDED = booleanPreferencesKey("database_seeded")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
    }

    suspend fun getApiKey(): String? {
        return dataStore.data.first()[GEMINI_API_KEY]?.takeIf { it.isNotBlank() }
    }

    suspend fun setApiKey(key: String) {
        dataStore.edit { it[GEMINI_API_KEY] = key }
    }

    suspend fun isDatabaseSeeded(): Boolean {
        return dataStore.data.first()[DATABASE_SEEDED] ?: false
    }

    suspend fun setDatabaseSeeded(seeded: Boolean) {
        dataStore.edit { it[DATABASE_SEEDED] = seeded }
    }
}
```

**API Key setup flow for user:**
1. On first launch (after seeding), if no API key is present, show a dismissible banner: *"To get AI-powered answers, add your free Gemini API key in Settings. Tap here to set it up."*
2. Tapping the banner navigates to Settings.
3. Until an API key is set, the app still works in offline fallback mode (raw paragraphs, no LLM summary).

---

## 17. Conversation History

- Conversations persist across sessions in the `conversation_message` table
- The chat screen loads and displays all previous messages on startup
- The "clear conversation" button deletes all rows from the table
- Conversation is not synced to any cloud service — it stays local

---

## 18. Error Handling

| Scenario | Handling |
|----------|----------|
| No API key set | Show banner prompting settings; fallback to offline mode |
| No internet | Skip API call; show offline fallback with raw paragraphs |
| API 429 (rate limit) | Show error: "Rate limit reached. Please wait a moment and try again." |
| API 401 (invalid key) | Show error: "Invalid API key. Please check your key in Settings." |
| API 500 | Show error: "The AI service is temporarily unavailable. Try again shortly." |
| FTS returns 0 results | Show: "No CCC paragraphs matched your question. Try rephrasing using different keywords." |
| Database not seeded | Show loading spinner; do not allow questions until seeding completes |
| JSON parse error during seeding | Log error; show: "Error loading Catechism data. Please reinstall the app." |

---

## 19. Performance Constraints

Targeting Galaxy A12 (4GB RAM, Exynos 850, Android 11):

- **Database seeding:** Run entirely on `Dispatchers.IO`. Expected time: 5–15 seconds for both files combined (Catechism: 2,865 rows; Bible: ~31,000 verses). Show progress UI.
- **FTS search:** Expected < 50ms per query on this hardware.
- **Scripture map lookup:** In-memory, < 1ms.
- **LLM API call:** Network-bound, expected 3–10 seconds depending on response length. Show loading indicator.
- **Memory:** Do not hold all CatechismEntity or BibleVerseEntity objects in memory simultaneously. Load only what FTS returns (max 8 paragraphs per query) and the associated verses.
- **JSON seeding:** Use Gson streaming or chunked inserts (see `CATECHISM_BATCH_SIZE`/`BIBLE_BATCH_SIZE` in DatabaseSeeder) to avoid OOM during first-launch parsing of large JSON files.
- **No ML libraries:** Do not add TensorFlow Lite, ONNX Runtime, or any embedding model. The entire value of this architecture is that zero ML runs on-device.

---

## 20. Testing Checklist

### Unit Tests
- [ ] `ScriptureReferenceParser.parse()` correctly handles all book name formats including "1 Corinthians", "Song of Songs", "Psalms"
- [ ] `QueryPreprocessor.buildFtsQuery()` strips stop words and produces valid FTS4 syntax
- [ ] `DatabaseSeeder` correctly parses `catechism.json` and `bible.json` field names
- [ ] `ScriptureMapLoader.getReferencesForParagraphs()` returns deduplicated references

### Integration Tests (Room in-memory database)
- [ ] Seeding inserts correct count: 2,865 CCC paragraphs
- [ ] FTS search for "baptism" returns relevant paragraphs (e.g., §1213, §1214)
- [ ] FTS search for "purgatory" returns relevant paragraphs (e.g., §1030, §1031)
- [ ] Scripture lookup for paragraph 2 returns Matthew 28:19 and Matthew 28:20
- [ ] Scripture lookup for paragraph 57 returns 13 references

### UI / Manual Tests on Galaxy A12
- [ ] First launch: seeding completes without ANR or OOM
- [ ] Asking "What is the Eucharist?" returns a grounded response citing CCC paragraphs
- [ ] Asking "What does the Church teach about abortion?" returns a grounded response
- [ ] Asking "What is the weather today?" is redirected politely
- [ ] Offline mode: asking a question shows raw CCC paragraphs without crash
- [ ] No API key: banner shown, app still functions in offline mode
- [ ] Invalid API key: clear error message shown
- [ ] Clear conversation: message list empties, history table cleared

---

## 21. Known Assumptions & Open Questions

1. **`catechism.json` field names:** This spec assumes `id` (int) and `text` (string). If your file uses different keys (e.g., `paragraph_number`, `content`, `body`), update `DatabaseSeeder.seedCatechism()` and `RawParagraph` accordingly before running.

2. **`bible.json` field names:** This spec assumes `book`, `chapter`, `verse`, `text`. Verify these match your actual file. Also confirm that book names in `bible.json` exactly match the book names used in `ccc_scripture_map.json` (e.g., both use "Psalms" not one using "Psalm").

3. **Bible translation:** The spec does not specify a translation. Use whichever translation is included in your `bible.json`. Note that the Deuterocanonical books (Tobit, Judith, 1–2 Maccabees, Wisdom, Sirach, Baruch) are cited by the CCC — confirm your `bible.json` includes them.

4. **Gemini API free tier limits:** As of mid-2025, Gemini 2.0 Flash offers 15 requests per minute and 1,500 requests per day on the free tier. This is sufficient for personal/devotional use. If rate limits become an issue, implement a simple request queue with retry-after logic.

5. **Groq fallback:** If you want to implement Groq as a fallback LLM, the API is compatible with OpenAI's format (`/openai/v1/chat/completions`). The prompt structure translates directly. Add a second Retrofit service and toggle in Settings.

6. **CCC paragraph structure:** Some paragraphs contain in-text citations formatted as superscript numbers linking to footnotes. These may or may not be present in your JSON. If they appear as raw numbers in the text, they are harmless but can be stripped with a regex if desired.

7. **Re-seeding on update:** If the source JSON files are updated in a future app release, increment `AppDatabase.version` and add a migration (or `fallbackToDestructiveMigration()` for early versions) to re-trigger seeding.

---

*End of specification. This document is intended to be given in full to a coding agent (Claude Code, Cursor, Copilot Workspace, etc.) as the primary implementation guide.*
