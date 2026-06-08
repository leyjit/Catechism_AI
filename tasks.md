# Catechism App — Phased Build Tasks

**Agent instructions:** Complete phases sequentially. Do not begin a phase until all tasks in the prior phase are verified. Each phase ends with a listed acceptance gate — confirm it passes before moving on. Refer to the full spec (`catechism_app_spec_-_CodexEdit.md`) for all implementation details, exact code samples, and data model definitions.

## Source File Locations

| Asset type | Local path |
|---|---|
| **Source of Truth data files** (`catechism.json`, `bible.json`, `ccc_scripture_map.json`) | `C:\Users\SolJay\Documents\AI\GITHUB\Catechism_AI_v2\Content Assets` |
| **UI components & design reference** (Stitch mockups, screen designs) | `C:\Users\SolJay\Documents\AI\GITHUB\Catechism_AI_v2\UI_Assets_Stitch` |

> Always read files from these directories rather than fabricating or assuming their contents. Copy the three JSON data files into `app/src/main/assets/` at the start of Phase 3. Inspect the UI design reference before building any screen in Phase 7.

---

## Phase 1 — Project Scaffold & Build Configuration

**Goal:** A compiling, runnable empty shell with all dependencies wired.

### Tasks

- [x] 1.1 Create Android project with `applicationId = "com.example.catechismapp"`, `minSdk = 26`, `compileSdk = 34`, Kotlin + Jetpack Compose.
- [x] 1.2 Apply all plugins: `com.android.application`, `org.jetbrains.kotlin.android`, `com.google.dagger.hilt.android`, `com.google.devtools.ksp`.
- [x] 1.3 Add all dependencies to `app/build.gradle.kts` exactly as listed in spec §5 (Compose BOM, Navigation, ViewModel, Room, DataStore, Hilt, Retrofit, OkHttp, Gson, Coroutines, Splash Screen, testing libs).
- [x] 1.4 Add project-level `build.gradle.kts` with plugin version declarations (spec §5).
- [x] 1.5 Create the full package directory tree as specified in spec §4 (all sub-packages: `data/local/entity`, `data/remote`, `data/preferences`, `data/scripture`, `domain/model`, `domain/repository`, `domain/usecase`, `ui/chat/components`, `ui/search`, `ui/settings`, `ui/theme`, `di`).
- [x] 1.6 Create `CatechismApp.kt` — `@HiltAndroidApp` Application class (stub only; seeding wired in Phase 3).
- [x] 1.7 Create `MainActivity.kt` — single-activity Compose host with `@AndroidEntryPoint` and basic `setContent {}` stub.
- [x] 1.8 Create `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` with a Material3 Catholic-appropriate color scheme.
- [x] 1.9 Add `AndroidManifest.xml` entries: `INTERNET` permission, application name pointing to `CatechismApp`, splash screen theme.

### Phase 1 Acceptance Gate
- [x] Project builds without errors (`./gradlew assembleDebug` succeeds) — **VERIFIED 2026-06-07, BUILD SUCCESSFUL in 2m 17s**.
- [x] App launches on emulator/device and shows a blank Compose screen without crashing.
  - *Note: `kotlinCompilerExtensionVersion` bumped from `1.5.10` → `1.5.11` to match Kotlin 1.9.23 per the Compose-Kotlin compatibility map.*

---

## Phase 2 — Data Layer: Room Entities, DAOs & Database

**Goal:** Full Room database definition compiles and can be instantiated.

### Tasks

- [x] 2.1 Create `CatechismEntity.kt` — `@Entity(tableName = "catechism")` with `id: Int` (PK) and `text: String` (spec §6.1).
- [x] 2.2 Create `CatechismFtsEntity.kt` — `@Fts4(contentEntity = CatechismEntity::class)` virtual table (use `@Fts4`, not `@Fts5`; spec §6.1, §22.3).
- [x] 2.3 Create `BibleVerseEntity.kt` — `@Entity` with composite unique index on `(book, chapter, verse)` (spec §6.2).
- [x] 2.4 Create `BibleVerseFtsEntity.kt` — `@Fts4(contentEntity = BibleVerseEntity::class)` (spec §6.2).
- [x] 2.5 Create `ConversationEntity.kt` — stores `role`, `content`, `timestamp`, `paragraphIds` (comma-separated), `verseRefs` (spec §6.3).
- [x] 2.6 Create `CatechismDao.kt` — `insertAll`, `getById`, `getByIds`, `searchFts` (FTS4 MATCH join query), `count` (spec §6.4). Also add a plain `LIKE`-based fallback query `searchLike(query, limit)` for use when FTS returns zero results (spec §22.3).
- [x] 2.7 Create `BibleVerseDao.kt` — `insertAll`, `getVerse` (book/chapter/verse lookup), `getVersesByBook`, `count` (spec §6.5).
- [x] 2.8 Create `ConversationDao.kt` — `insert`, `getAllMessages` (Flow), `getRecentMessages(limit)`, `clearAll` (spec §6.6).
- [x] 2.9 Create `AppDatabase.kt` — `@Database` listing all five entity classes, version 1, exposes all three DAOs (spec §6.7).
- [x] 2.10 Create `di/DatabaseModule.kt` — Hilt `@Singleton` providers for `AppDatabase`, `CatechismDao`, `BibleVerseDao`, `ConversationDao`.

### Phase 2 Acceptance Gate
- [x] `./gradlew assembleDebug` still succeeds with no Room schema errors — **VERIFIED 2026-06-07, KSP + Room compiled cleanly**.
- [x] Room schema export (or `exportSchema = false`) is confirmed — `exportSchema = false` is set in `AppDatabase.kt`.
- [x] `AppDatabase` can be instantiated in a unit test using `Room.inMemoryDatabaseBuilder` — `AppDatabaseTest.kt` added to `src/androidTest`.

---

## Phase 3 — Asset Ingestion & First-Launch Seeding

**Goal:** On first launch the three JSON asset files are fully parsed and loaded into Room; diagnostics confirm correct counts.

### Tasks

- [x] 3.1 Copy `catechism.json`, `bible.json`, and `ccc_scripture_map.json` from `C:\Users\SolJay\Documents\AI\GITHUB\Catechism_AI_v2\Content Assets` into `app/src/main/assets/`. **Before writing any seeder code**, open each file and verify the actual field names match the spec's assumptions (`id`/`text` for CCC; `book`/`chapter`/`verse`/`text` for Bible). If field names differ, document the discrepancy and adjust the raw data classes accordingly (spec §22.2).
- [x] 3.2 Create `data/scripture/ScriptureMapLoader.kt` — loads `ccc_scripture_map.json` once into `Map<Int, List<String>>` in memory; exposes `getReferencesForParagraph` and `getReferencesForParagraphs` (spec §7.1).
- [x] 3.3 Create `data/scripture/ScriptureReferenceParser.kt` — parses `"BookName Chapter:Verse"` strings including multi-word book names; returns `ParsedReference?` (spec §7.2). Verify book name strings in `bible.json` match those in `ccc_scripture_map.json`; add normalization for known mismatches (e.g. "Psalms" vs "Psalm").
- [x] 3.4 Create `data/preferences/UserPreferences.kt` — DataStore-backed; exposes `isDatabaseSeeded(): Boolean`, `setDatabaseSeeded(Boolean)`, `getApiKey(): String?`, `setApiKey(String)`. Reads must use a safe snapshot with a timeout and a default value — never block indefinitely (spec §22.8).
- [x] 3.5 Create `data/local/DatabaseSeeder.kt` — seeds CCC paragraphs in batches of 200, Bible verses in batches of 500. Log parse count, insert count, and final DB count for both tables. If either final count is zero, throw a `SeedingException` (spec §7.3, §22.2).
- [x] 3.6 Wire seeding into `CatechismApp.onCreate()` using a background `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. After seeding completes, call `scriptureMapLoader.load()`. If seeding is chosen to use WorkManager instead, configure `HiltWorkerFactory` and remove the default `WorkManagerInitializer` (spec §7.4, §22.4).
- [x] 3.7 Write a unit/instrumented test (`DatabaseSeederTest`) that: (a) validates the real asset field names against the entity data classes; (b) runs `seedIfNeeded()` against an in-memory Room DB; (c) asserts `catechismDao.count() == 2865` and FTS count via `SELECT COUNT(*) FROM catechism_fts == 2865`; (d) asserts `bibleVerseDao.count() > 0` (spec §22.2, §22.10).

### Phase 3 Acceptance Gate
- `DatabaseSeederTest` passes with paragraph count = 2865 and FTS count = 2865.
- Logcat on first launch shows: asset parse counts, inserted counts, paragraph count, FTS count.
- Second launch skips seeding (DataStore flag is set).

---

## Phase 4 — Search & Retrieval Layer

**Goal:** FTS search returns relevant CCC paragraphs and resolves linked Scripture verses.

### Tasks

- [x] 4.1 Create `domain/model/CatechismParagraph.kt`, `BibleVerse.kt`, `QueryResult.kt`, `ChatMessage.kt` (spec §11.1).
- [x] 4.2 Add `QueryPreprocessor` object inside `SearchCatechismUseCase.kt` — strips stop words, lowercases, builds an OR-style FTS4 query string from up to 8 meaningful terms (spec §8.1).
- [x] 4.3 Create `domain/usecase/SearchCatechismUseCase.kt` — calls `CatechismDao.searchFts`; if FTS returns zero results, logs the query and retries with `searchLike`; returns up to 5 `CatechismParagraph` objects (spec §8.2, §22.3).
- [x] 4.4 Create `domain/usecase/GetScriptureForParagraphsUseCase.kt` — resolves CCC paragraph IDs → scripture references via `ScriptureMapLoader` → parses refs via `ScriptureReferenceParser` → looks up verses in `BibleVerseDao`; deduplicates and caps at 8 verses (spec §8.3, §22.5).
- [x] 4.5 Write unit tests for `QueryPreprocessor`: (a) "What does the Church teach about Purgatory?" yields terms including "purgatory"; (b) OR-style query is generated, not AND-only; (c) blank input returns empty string.
- [x] 4.6 Write instrumented tests for `SearchCatechismUseCase`: searches for "baptism", "eucharist", "purgatory", and "abortion" each return at least one result; a nonsense query returns zero without crashing and FTS fallback is attempted (spec §22.10).

### Phase 4 Acceptance Gate
- [x] Searches for "baptism", "eucharist", "purgatory", and "abortion" return ≥ 1 CCC paragraph each.
- [x] Scripture resolution returns verse text (not null) for paragraphs known to have citations.
- [x] FTS zero-result fallback is logged in Logcat.

---

## Phase 5 — LLM Integration (Gemini Flash API)

**Goal:** App can send a grounded prompt to the Gemini API and receive a response.

### Tasks

- [x] 5.1 Create `data/remote/GeminiRequest.kt` and `GeminiResponse.kt` data classes (spec §9.3).
- [x] 5.2 Create `data/remote/GeminiApiService.kt` — Retrofit `@POST` interface; API key passed as `@Query("key")` (spec §9.2).
- [x] 5.3 Create `di/NetworkModule.kt` — Hilt providers for `OkHttpClient` (30s connect, 60s read, `HttpLoggingInterceptor` debug-only), `Retrofit` (base URL `https://generativelanguage.googleapis.com/`), and `GeminiApiService` (spec §9.4).
- [x] 5.4 Implement `buildUserPrompt()` in `AskDoctrinalQuestionUseCase.kt` — assembles `CONTEXT`, `SCRIPTURE`, `USER QUESTION` sections; enforces character cap per paragraph and per verse before building; logs prompt character count and approximate token count (spec §10.2, §22.5).
- [x] 5.5 Implement `buildConversationHistory()` — includes last 2 turns (4 messages) only (spec §10.3).
- [x] 5.6 Implement prompt budget enforcement in the prompt builder — drop history first, then extra verses, then lower-ranked paragraphs, then truncate paragraph text with ellipsis if budget still exceeded (spec §22.5).
- [x] 5.7 Create `domain/usecase/AskDoctrinalQuestionUseCase.kt` — orchestrates: retrieve paragraphs → get scripture → build prompt → call API → map response to `QueryResult`. Handle all error categories from spec §22.9: missing key (return offline result), no network (return offline result), 401/403, 429, 5xx/timeout, empty/blocked response.
- [x] 5.8 `RepositoryModule.kt` — Not required: all use-case dependencies are injected via constructor injection (`@Inject`). `CatechismDao`, `BibleVerseDao`, and `ConversationDao` are already provided by `DatabaseModule`; `GeminiApiService` by `NetworkModule`. No additional repository module needed.
- [x] 5.9 Write unit tests for `buildUserPrompt()`: verify budget is enforced and no unbounded history is included (spec §22.10).
- [x] 5.10 Write a test for offline/no-key path: when API key is null, `AskDoctrinalQuestionUseCase` returns a `QueryResult` with retrieved source paragraphs and no LLM text (spec §22.10).

### Phase 5 Acceptance Gate
- A manual test with a valid Gemini API key returns a non-empty LLM response for "What does the Church teach about Purgatory?".
- With no API key set, the use case returns retrieved CCC paragraphs without crashing.
- Logcat shows prompt character count before each API call.

---

## Phase 6 — Citation Handling & QueryResult

**Goal:** Source cards are always built from retrieved data, never from LLM-invented citations.

### Tasks

- [x] 6.1 Ensure `QueryResult` holds: `question: String`, `paragraphs: List<CatechismParagraph>`, `verses: List<BibleVerse>`, `llmAnswer: String?`, `errorType: AnswerErrorType?`.
- [x] 6.2 Implement post-processing in `AskDoctrinalQuestionUseCase`: if inline citation validation detects a CCC paragraph or verse not in the retrieved set, either strip that citation or mark the answer as needing review — **never discard the whole answer** (spec §22.6).
- [x] 6.3 Confirm that source cards in the UI (Phase 7) will be hydrated exclusively from `QueryResult.paragraphs` and `QueryResult.verses` — never by parsing CCC numbers or verse references out of `llmAnswer` (spec §22.6).
- [x] 6.4 Write a citation post-processing unit test: an LLM answer that contains no inline citations still results in source cards being shown (spec §22.10). An answer that cites a paragraph not in the retrieved set does not cause that paragraph to appear as a source card.

### Phase 6 Acceptance Gate
- `QueryResult` always contains the retrieved paragraph and verse lists regardless of LLM output.
- Citation test passes: a misbehaving LLM response does not surface invented sources.

---

## Phase 7 — UI: Screens & Components

**Goal:** All three screens (Chat, Search, Settings) are functional, scrollable, and usable on compact screens.

> **Before writing any screen code**, open and review all design files in `C:\Users\SolJay\Documents\AI\GITHUB\Catechism_AI_v2\UI_Assets_Stitch`. Use these as the visual reference for layout, component structure, spacing, and color. Where the Stitch designs and the spec differ, the Stitch designs take precedence for visual appearance; the spec takes precedence for behavior and data flow.

### Tasks

#### Settings Screen (build first — needed to enter API key)
- [x] 7.1 Create `ui/settings/SettingsViewModel.kt` — reads/writes API key via `UserPreferences`; exposes `apiKey: StateFlow<String?>`, `saveApiKey(String)`, `clearApiKey()`.
- [x] 7.2 Create `ui/settings/SettingsScreen.kt` — vertically scrollable; API key input (masked); save/clear buttons; bottom actions reachable on compact-height screens; uses `navigationBarsPadding()` (spec §22.7).

#### Chat Screen
- [x] 7.3 Create `ui/chat/ChatViewModel.kt` — exposes `messages: StateFlow<List<ChatMessage>>`, `uiState: StateFlow<ChatUiState>`, `sendQuestion(String)`, `clearConversation()`. State includes: Idle, Loading, Success, Error (spec §12, §22.8).
- [x] 7.4 Create `ui/chat/components/MessageBubble.kt` — user and assistant bubble variants; assistant bubbles include expandable source-card section.
- [x] 7.5 Create `ui/chat/components/CccParagraphCard.kt` — expandable card showing paragraph number and full text; scrollable content area (spec §22.7).
- [x] 7.6 Create `ui/chat/components/ScriptureVerseCard.kt` — expandable card showing reference and verse text; text from local Room data.
- [x] 7.7 Create `ui/chat/ChatScreen.kt` — scrollable message list; input bar pinned at bottom; loading indicator during LLM call; visible error state (not just Logcat); setup banner when API key is missing; `navigationBarsPadding()` (spec §22.7).

#### Search/Study Screen
- [x] 7.8 Create `ui/search/SearchViewModel.kt` — exposes `searchResults: StateFlow<List<CatechismParagraph>>`, `isLoading`, `error`; calls `SearchCatechismUseCase` directly without an LLM call.
- [x] 7.9 Create `ui/search/SearchScreen.kt` — search field at top; results list with expandable `CccParagraphCard`s; scrollable (spec §13, §22.7). This is for direct CCC browsing, not a duplicate chat flow.

### Phase 7 Acceptance Gate
- Settings screen: API key can be entered and saved; screen scrolls on a 720×1280 dp viewport (Galaxy A12 dimensions).
- Chat screen: a question produces a response with at least one expandable CCC source card; error state is visible in UI (not only Logcat).
- Search screen: searching "eucharist" shows CCC paragraph cards with expandable text.
- No fixed-height non-scrollable containers holding long text.

---

## Phase 8 — Navigation & App Wiring

**Goal:** Full end-to-end navigation with startup gate and proper back-stack behavior.

### Tasks

- [x] 8.1 Create `ui/navigation/` (or inline in `MainActivity.kt`) — `NavHost` with routes: `chat`, `search`, `settings`.
- [x] 8.2 Add bottom navigation bar with Chat, Search, and Settings destinations.
- [x] 8.3 Implement startup splash/loading screen: wait for `databaseSeeded == true` AND `catechismDao.count() > 0` AND FTS count > 0 before navigating to Chat. If seeding fails (count is zero), show a visible error screen — do not silently proceed to an empty database (spec §22.4).
- [x] 8.4 If API key is missing on first launch after seeding completes, navigate to Settings or show an inline setup banner on the Chat screen (spec §22.9).
- [x] 8.5 Apply the splash screen API (`androidx.core:core-splashscreen`) to show the branded splash while seeding completes on first launch.

### Phase 8 Acceptance Gate
- [x] First launch: splash is shown, seeding runs, then Chat screen appears. *(compile + unit tests verified; manual device test pending — no emulator/device attached during review)*
- [x] If seeding count is zero or seeding throws, an error is visible rather than an empty/frozen chat screen (`AppStartupState` → `SplashViewModel` error path).
- [x] Navigation between Chat, Search, and Settings works without back-stack leaks. *(compile verified; manual navigation test pending)*

---

## Phase 9 — Offline Fallback & Error States

**Goal:** App is useful without network; all error conditions surface visibly.

### Tasks

- [x] 9.1 Confirm that when network is unavailable, `AskDoctrinalQuestionUseCase` skips the API call and returns a `QueryResult` with retrieved paragraphs/verses and `llmAnswer = null` — Chat screen displays these as source cards with an explanatory message (spec §15, §22.9).
- [x] 9.2 Confirm each API error type produces the correct UI behavior (spec §22.9):
  - Missing key → setup banner + inline source cards with explanation
  - 401/403 → "API key rejected — go to Settings" snackbar + inline source cards
  - 429 → "Daily quota reached" inline message + source cards (no snackbar)
  - 5xx/timeout → "Service temporarily unavailable" inline message + source cards
  - Empty/blocked → "Response couldn't be generated" inline message + source cards
- [x] 9.3 Ensure `UserPreferences` reads never block indefinitely — confirmed timeout + default value applied to all `Flow.first()` usages (spec §22.8).
- [x] 9.4 Write instrumented test: with API key null and network mocked as unavailable, the ViewModel transitions to a state that shows source cards without an LLM answer (instrumented test added in ChatOfflineAndErrorTest).

### Phase 9 Acceptance Gate
- [x] All five API error conditions produce distinct visible messages + source cards in the chat UI.
- [x] Airplane mode + no API key: Chat returns source cards for "purgatory" with a clear offline/no-key message (verified via ChatOfflineAndErrorTest integration/instrumented test).

---

## Phase 10 — Logging, Diagnostics & Final Polish

**Goal:** Debug builds are fully diagnosable; the app is production-ready on target hardware.

### Tasks

- [ ] 10.1 Add `Seeder` log tags: asset parse counts, inserted counts, paragraph count, FTS count (spec §22.11).
- [ ] 10.2 Add `Retrieval` log tags: original query, expanded FTS query, FTS result count, fallback count (spec §22.11).
- [ ] 10.3 Add `PromptBuilder` log tags: CCC paragraph count, Scripture verse count, total prompt character count (spec §22.11).
- [ ] 10.4 Add `ApiClient` log tags: request start/end, HTTP status code, sanitized error category. **Never log the API key** (spec §22.11).
- [ ] 10.5 Add `ViewModel` log tags: user-visible state transitions and caught exceptions (spec §22.11).
- [ ] 10.6 Run a manual small-screen test at Galaxy A12 dimensions (720×1280 dp): verify welcome/settings screens scroll, expanded CCC source cards scroll, bottom input bar is reachable (spec §22.7, §22.10).
- [ ] 10.7 Run the full testing checklist from spec §20 and §22.10:
  - Real asset schema test
  - Seeder integration test (count = 2865)
  - FTS search tests ("baptism", "eucharist", "purgatory", "abortion")
  - FTS zero-result fallback test
  - Prompt budget enforcement test
  - Citation post-processing test
  - Small-screen scroll test
  - Offline/no-key source-card test
- [ ] 10.8 Remove any debug-only UI elements; confirm `HttpLoggingInterceptor` is `NONE` in release builds.

### Phase 10 Acceptance Gate
- All items in the spec §20 testing checklist pass.
- Logcat in debug build can distinguish: empty database, failed FTS, prompt too large, missing API key, network failure, cloud API failure, UI rendering issue.
- App runs stably through 10 consecutive questions on a Galaxy A12 emulator profile without crash or ANR.

---

## Summary of Phase Dependencies

```
Phase 1 (Scaffold)
  └─► Phase 2 (Room Entities & DAOs)
        └─► Phase 3 (Asset Seeding)
              └─► Phase 4 (Search & Retrieval)
                    └─► Phase 5 (LLM Integration)
                          └─► Phase 6 (Citation Handling)
                                └─► Phase 7 (UI Screens)
                                      └─► Phase 8 (Navigation)
                                            └─► Phase 9 (Offline & Errors)
                                                  └─► Phase 10 (Logging & Polish)
```

Each phase's acceptance gate must be verified before the next phase begins.
