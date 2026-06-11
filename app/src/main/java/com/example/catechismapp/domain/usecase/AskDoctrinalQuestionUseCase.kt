package com.example.catechismapp.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.catechismapp.BuildConfig
import com.example.catechismapp.data.local.ConversationDao
import com.example.catechismapp.data.local.entity.ConversationEntity
import com.example.catechismapp.data.preferences.UserPreferences
import com.example.catechismapp.data.remote.BackendAskRequest
import com.example.catechismapp.data.remote.BackendHarnessApiService
import com.example.catechismapp.data.remote.GeminiApiService
import com.example.catechismapp.data.remote.GeminiContent
import com.example.catechismapp.data.remote.GeminiPart
import com.example.catechismapp.data.remote.GeminiRequest
import com.example.catechismapp.data.remote.GenerationConfig
import com.example.catechismapp.domain.model.AnswerErrorType
import com.example.catechismapp.domain.model.BibleVerse
import com.example.catechismapp.domain.model.CatechismParagraph
import com.example.catechismapp.domain.model.QueryResult
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AskDoctrinalQuestionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchCatechism: SearchCatechismUseCase,
    private val getScripture: GetScriptureForParagraphsUseCase,
    private val backendHarnessApiService: BackendHarnessApiService,
    private val geminiApiService: GeminiApiService,
    private val userPreferences: UserPreferences,
    private val conversationDao: ConversationDao
) {
    companion object {
        private const val TAG = "AskDoctrinalQuestion"

        // Prompt budget constants
        private const val MAX_PARAGRAPHS = 5
        private const val MAX_SOURCE_PARAGRAPHS = 20
        private const val MAX_VERSES = 8
        private const val MAX_PARAGRAPH_CHARS = 800
        private const val MAX_VERSE_CHARS = 300
        private const val MAX_PROMPT_CHARS = 12_000
        private const val MAX_HISTORY_TURNS = 2
        private const val MAX_PLANNER_TERMS = 8
        private const val MAX_SELECTOR_PARAGRAPHS = 8
        private const val MAX_SELECTOR_TEXT_CHARS = 500

        /**
         * Suffix appended to all error/fallback assistant messages when local sources are
         * available. [MessageBubble] detects this phrase to auto-expand the sources panel,
         * so users see CCC cards immediately without tapping "Show Sources".
         */
        const val SOURCES_SUFFIX = "\n\nHere are the CCC paragraphs most relevant to your question:"

        // Friendly error messages — pastoral tone, actionable where possible.
        private const val MSG_NO_KEY =
            "No API key configured. Add your free Gemini API key in Settings to get AI-powered answers."
        private const val MSG_NO_NETWORK =
            "No internet connection. Your question and local CCC results have been saved."
        private const val MSG_UNAUTHORIZED =
            "Your API key was not accepted by Gemini (invalid or expired). Please check or update it in Settings."
        private const val MSG_RATE_LIMITED =
            "Daily quota reached — Gemini's free tier resets every 24 hours. Try again later."
        private const val MSG_SERVER_ERROR =
            "The AI service is temporarily unavailable. Please try again in a moment."
        private const val MSG_CONTENT_BLOCKED =
            "A response could not be generated for this question. This may be a content filter or an empty reply from Gemini."
        private const val MSG_NETWORK_INTERRUPTED =
            "The connection was interrupted before a response arrived. Please check your network and try again."
        private const val MSG_BACKEND_UNAVAILABLE =
            "The backend harness is not reachable. Start the local backend and make sure USB port forwarding is active, then try again."
        private const val MSG_BACKEND_TIMEOUT =
            "The hosted service is taking too long to answer. This can happen while the service starts up or the cloud model is busy. Please try again in a moment."

        var backendHarnessEnabledOverride: Boolean? = null

        val SYSTEM_PROMPT = """
You are a Catholic catechetical assistant. Your sole purpose is to help users understand Catholic doctrine as taught by the Catechism of the Catholic Church (CCC).

STRICT RULES:
1. Answer ONLY based on the CCC paragraphs provided in the CONTEXT section below. Do not draw on any other source or prior knowledge.
2. If the provided CCC paragraphs do not address the user's question, say: "The CCC paragraphs retrieved for this question do not directly address this topic. Try rephrasing your question, or look up the CCC index directly."
3. ALWAYS cite specific CCC paragraph numbers using the format (CCC §123) inline in your answer.
4. When Scripture is provided in the SCRIPTURE section, you may reference specific verses to support the CCC teaching. Format as (Book Chapter:Verse).
5. Keep your answer clear, faithful, and appropriately concise. Avoid speculation, personal opinions, or theological positions not found in the provided paragraphs.
6. If the user asks something outside Catholic doctrine (e.g., political opinions, personal advice, non-doctrinal matters), politely redirect: "This app is designed specifically for questions about Catholic teaching as found in the Catechism."
7. Write in a warm, pastoral tone suitable for both new Catholics and those seeking to deepen their faith.
        """.trimIndent()

        private val QUERY_PLANNER_PROMPT = """
You convert conversational Catholic doctrine questions into search terms for a local Catechism and Bible retrieval system.

Return ONLY compact JSON with this shape:
{"doctrinal_topic":"...","normalized_question":"...","retrieval_terms":["term 1","term 2"],"scripture_priority":true}

Rules:
- Remove conversational filler such as "why do Catholics believe", "can you explain", "what is the biblical basis for".
- Keep the doctrinal topic and closely related theological terms.
- Prefer terms found in the Catechism and Scripture.
- Do not answer the question.
- Do not cite sources.
- Use 3 to 8 retrieval terms.
        """.trimIndent()

        private val SOURCE_SELECTOR_PROMPT = """
You select Catechism sources for a Catholic doctrine answer.

Return ONLY compact JSON with this shape:
{"selected_paragraph_ids":[123,456],"reason":"short reason"}

Rules:
- Select only paragraph IDs from the supplied candidate list.
- Select paragraphs that directly answer the user's doctrinal question.
- Prefer paragraphs with Scripture support when doctrinal relevance is comparable.
- Do not select a paragraph merely because it has Scripture if it does not answer the question.
- Prefer 3 to 5 paragraphs; use up to 8 only when needed.
- Do not answer the question.
- Do not cite or invent sources.
        """.trimIndent()
    }

    suspend operator fun invoke(question: String): QueryResult {
        if (backendHarnessEnabledOverride ?: BuildConfig.USE_BACKEND_HARNESS) {
            return askBackendHarness(question)
        }

        val apiKey = userPreferences.getApiKey()?.takeIf { it.isNotBlank() }
        val networkAvailable = apiKey != null && isNetworkAvailable()
        val retrievalQuestion = if (apiKey != null && networkAvailable) {
            buildRetrievalQuestionWithPlanner(apiKey, question)
        } else {
            question
        }

        // Step 1: Retrieve CCC paragraphs.
        // Keep the prompt compact, but harvest Scripture from a wider source window.
        // Some doctrinal sections, such as the Trinity, have dense CCC paragraphs first
        // and richer Scripture mappings a little later in the same retrieval cluster.
        val candidateParagraphs = searchCatechism(retrievalQuestion, maxResults = MAX_SOURCE_PARAGRAPHS)
        val sourceParagraphs = if (apiKey != null && networkAvailable && candidateParagraphs.isNotEmpty()) {
            selectSourceParagraphsWithScriptureBent(
                apiKey = apiKey,
                originalQuestion = question,
                candidates = candidateParagraphs
            )
        } else {
            candidateParagraphs
        }
        val paragraphs = sourceParagraphs.take(MAX_PARAGRAPHS)

        // Step 2: Resolve scripture for the broader source window, capped downstream.
        val verses = if (sourceParagraphs.isNotEmpty()) {
            getScripture(sourceParagraphs.map { it.id })
        } else {
            emptyList()
        }

        // Step 3: Check API key
        if (apiKey == null) {
            Log.w(TAG, "No API key set — returning offline result")
            persistErrorTurns(question, paragraphs, verses, MSG_NO_KEY)
            return QueryResult(
                question = question,
                paragraphs = paragraphs,
                verses = verses,
                llmAnswer = null,
                errorType = AnswerErrorType.MISSING_API_KEY
            )
        }

        // Step 4: Check network connectivity
        if (!networkAvailable) {
            Log.w(TAG, "No network available — returning offline result")
            persistErrorTurns(question, paragraphs, verses, MSG_NO_NETWORK)
            return QueryResult(
                question = question,
                paragraphs = paragraphs,
                verses = verses,
                llmAnswer = null,
                errorType = AnswerErrorType.NETWORK_ERROR
            )
        }

        // Step 5: Fetch conversation history (last 2 turns = 4 messages)
        val history = conversationDao.getRecentMessages(MAX_HISTORY_TURNS * 2)
            .reversed()

        // Estimate history char overhead to keep total under budget
        val historyChars = history.sumOf { it.content.length }

        // Step 6: Build prompt. If history already pushes us near budget, drop it first.
        val effectiveBudget = MAX_PROMPT_CHARS - historyChars
        val (userPrompt, includedHistory) = if (effectiveBudget < MAX_PROMPT_CHARS / 2) {
            // History too large — drop it and build with full budget
            Log.d(TAG, "History overhead ${historyChars}ch exceeds half budget — dropping history")
            Pair(buildUserPrompt(question, paragraphs, verses, budgetChars = MAX_PROMPT_CHARS), emptyList<ConversationEntity>())
        } else {
            Pair(buildUserPrompt(question, paragraphs, verses, budgetChars = effectiveBudget), history)
        }

        // Step 7: Assemble the full request contents (history only included if budget allowed)
        val historyContents = buildConversationHistory(includedHistory)
        val currentUserContent = GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(userPrompt))
        )
        val allContents = historyContents + currentUserContent

        val request = GeminiRequest(
            contents = allContents,
            systemInstruction = GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(SYSTEM_PROMPT))
            )
        )

        // Step 8: Call API and handle errors. All non-success paths persist the user question
        // and a friendly assistant message with local source cards so the chat is never empty.
        return try {
            val response = geminiApiService.generateContent(apiKey, request)

            when {
                response.code() == 401 || response.code() == 403 -> {
                    Log.e(TAG, "API auth error: ${response.code()}")
                    persistErrorTurns(question, paragraphs, verses, MSG_UNAUTHORIZED)
                    QueryResult(question, paragraphs, verses, null, AnswerErrorType.UNAUTHORIZED)
                }
                response.code() == 429 -> {
                    Log.w(TAG, "API rate limited (429)")
                    persistErrorTurns(question, paragraphs, verses, MSG_RATE_LIMITED)
                    QueryResult(question, paragraphs, verses, null, AnswerErrorType.RATE_LIMITED)
                }
                response.code() >= 500 -> {
                    Log.e(TAG, "API server error: ${response.code()}")
                    persistErrorTurns(question, paragraphs, verses, MSG_SERVER_ERROR)
                    QueryResult(question, paragraphs, verses, null, AnswerErrorType.SERVER_ERROR)
                }
                !response.isSuccessful -> {
                    Log.e(TAG, "API unexpected error: ${response.code()}")
                    persistErrorTurns(question, paragraphs, verses, MSG_SERVER_ERROR)
                    QueryResult(question, paragraphs, verses, null, AnswerErrorType.SERVER_ERROR)
                }
                else -> {
                    val body = response.body()
                    val llmText = body?.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
                        ?.takeIf { it.isNotBlank() }

                    if (llmText == null) {
                        Log.w(TAG, "Empty or blocked response from Gemini")
                        persistErrorTurns(question, paragraphs, verses, MSG_CONTENT_BLOCKED)
                        QueryResult(question, paragraphs, verses, null, AnswerErrorType.CONTENT_BLOCKED)
                    } else {
                        val processedText = postProcessAnswer(llmText, paragraphs, verses)
                        // Persist both turns to conversation history
                        conversationDao.insert(
                            ConversationEntity(
                                role = "user",
                                content = question,
                                paragraphIds = paragraphs.joinToString(",") { it.id.toString() }
                            )
                        )
                        conversationDao.insert(
                            ConversationEntity(
                                role = "assistant",
                                content = processedText,
                                paragraphIds = paragraphs.joinToString(",") { it.id.toString() },
                                verseRefs = verses.joinToString(",") { it.reference }
                            )
                        )
                        QueryResult(question, paragraphs, verses, processedText, null)
                    }
                }
            }
        } catch (e: Exception) {
            val (errorMsg, errorType) = if (e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException) {
                Log.e(TAG, "Timeout exception: ${e.message}", e)
                Pair(MSG_SERVER_ERROR, AnswerErrorType.SERVER_ERROR)
            } else {
                Log.e(TAG, "Network exception: ${e.message}", e)
                Pair(MSG_NETWORK_INTERRUPTED, AnswerErrorType.NETWORK_ERROR)
            }
            persistErrorTurns(question, paragraphs, verses, errorMsg)
            QueryResult(question, paragraphs, verses, null, errorType)
        }
    }

    private suspend fun askBackendHarness(question: String): QueryResult {
        return try {
            val response = backendHarnessApiService.ask(BackendAskRequest(question))
            val body = response.body()
            val answer = body?.answer?.takeIf { it.isNotBlank() }

            if (response.isSuccessful && answer != null) {
                val paragraphs = body.ccc_sources.map { source ->
                    CatechismParagraph(id = source.id, text = source.text)
                }
                val verses = body.scripture_sources.map { source ->
                    BibleVerse(
                        reference = source.reference,
                        book = source.book,
                        chapter = source.chapter,
                        verse = source.verse,
                        text = source.text
                    )
                }

                conversationDao.insert(
                    ConversationEntity(
                        role = "user",
                        content = question,
                        paragraphIds = paragraphs.joinToString(",") { it.id.toString() }
                    )
                )
                conversationDao.insert(
                    ConversationEntity(
                        role = "assistant",
                        content = answer,
                        paragraphIds = paragraphs.joinToString(",") { it.id.toString() },
                        verseRefs = verses.joinToString(",") { it.reference }
                    )
                )

                QueryResult(
                    question = question,
                    paragraphs = paragraphs,
                    verses = verses,
                    llmAnswer = answer,
                    errorType = null
                )
            } else {
                val backendError = body?.error?.takeIf { it.isNotBlank() }
                    ?: response.errorBody()
                        ?.string()
                        ?.let { errorJson ->
                            try {
                                Gson().fromJson(errorJson, com.example.catechismapp.data.remote.BackendAskResponse::class.java)
                                    ?.error
                                    ?.takeIf { it.isNotBlank() }
                            } catch (e: Exception) {
                                null
                            }
                        }

                val message = backendError ?: MSG_BACKEND_UNAVAILABLE
                persistErrorTurns(question, emptyList(), emptyList(), message)
                QueryResult(
                    question = question,
                    paragraphs = emptyList(),
                    verses = emptyList(),
                    llmAnswer = null,
                    errorType = AnswerErrorType.SERVER_ERROR
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend harness request failed: ${e.message}", e)
            val message = if (e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException) {
                MSG_BACKEND_TIMEOUT
            } else {
                MSG_BACKEND_UNAVAILABLE
            }
            persistErrorTurns(question, emptyList(), emptyList(), message)
            QueryResult(
                question = question,
                paragraphs = emptyList(),
                verses = emptyList(),
                llmAnswer = null,
                errorType = AnswerErrorType.NETWORK_ERROR
            )
        }
    }

    private suspend fun buildRetrievalQuestionWithPlanner(
        apiKey: String,
        question: String
    ): String {
        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(question))
                    )
                ),
                systemInstruction = GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(QUERY_PLANNER_PROMPT))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.0f,
                    maxOutputTokens = 256,
                    topP = 0.2f,
                    responseMimeType = "application/json"
                )
            )

            val response = geminiApiService.generateContent(apiKey, request)
            val plannerText = response.body()
                ?.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text

            val terms = parsePlannerTerms(plannerText)
            if (response.isSuccessful && terms.isNotEmpty()) {
                val retrievalQuestion = terms.joinToString(" ")
                Log.d(TAG, "Query planner terms: $retrievalQuestion")
                retrievalQuestion
            } else {
                Log.w(TAG, "Query planner unavailable (${response.code()}); using local query")
                question
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query planner failed; using local query: ${e.message}")
            question
        }
    }

    fun parsePlannerTerms(rawText: String?): List<String> {
        if (rawText.isNullOrBlank()) return emptyList()

        val json = rawText
            .replace("```json", "")
            .replace("```", "")
            .trim()
            .let { text ->
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start >= 0 && end > start) text.substring(start, end + 1) else text
            }

        return try {
            val plan = Gson().fromJson(json, QueryPlannerResult::class.java)
            plan.retrievalTerms
                .orEmpty()
                .asSequence()
                .map { it.trim() }
                .filter { it.length > 2 }
                .distinctBy { it.lowercase() }
                .take(MAX_PLANNER_TERMS)
                .toList()
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse query planner JSON: ${e.message}")
            emptyList()
        }
    }

    private suspend fun selectSourceParagraphsWithScriptureBent(
        apiKey: String,
        originalQuestion: String,
        candidates: List<CatechismParagraph>
    ): List<CatechismParagraph> {
        val scriptureCounts = candidates.associate { paragraph ->
            paragraph.id to getScripture(listOf(paragraph.id)).size
        }

        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(
                                buildSourceSelectionPrompt(
                                    question = originalQuestion,
                                    candidates = candidates,
                                    scriptureCounts = scriptureCounts
                                )
                            )
                        )
                    )
                ),
                systemInstruction = GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(SOURCE_SELECTOR_PROMPT))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.0f,
                    maxOutputTokens = 384,
                    topP = 0.2f,
                    responseMimeType = "application/json"
                )
            )

            val response = geminiApiService.generateContent(apiKey, request)
            val selectorText = response.body()
                ?.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text

            val selectedIds = parseSelectedParagraphIds(
                rawText = selectorText,
                allowedIds = candidates.map { it.id }.toSet()
            )

            if (response.isSuccessful && selectedIds.isNotEmpty()) {
                val byId = candidates.associateBy { it.id }
                val selected = selectedIds.mapNotNull { byId[it] }
                val selectedIdSet = selected.map { it.id }.toSet()
                val remainder = candidates
                    .filterNot { it.id in selectedIdSet }
                    .sortedWith(
                        compareByDescending<CatechismParagraph> { scriptureCounts[it.id] ?: 0 }
                            .thenBy { paragraph -> candidates.indexOfFirst { it.id == paragraph.id } }
                    )

                Log.d(TAG, "Source selector chose CCC IDs: ${selected.joinToString { it.id.toString() }}")
                (selected + remainder).take(MAX_SOURCE_PARAGRAPHS)
            } else {
                Log.w(TAG, "Source selector unavailable (${response.code()}); using retrieval order")
                candidates
            }
        } catch (e: Exception) {
            Log.w(TAG, "Source selector failed; using retrieval order: ${e.message}")
            candidates
        }
    }

    private fun buildSourceSelectionPrompt(
        question: String,
        candidates: List<CatechismParagraph>,
        scriptureCounts: Map<Int, Int>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("USER QUESTION:")
        sb.appendLine(question)
        sb.appendLine()
        sb.appendLine("CANDIDATE CCC PARAGRAPHS:")
        candidates.forEach { paragraph ->
            val scriptureCount = scriptureCounts[paragraph.id] ?: 0
            sb.appendLine("CCC §${paragraph.id} | scripture_refs=$scriptureCount")
            sb.appendLine(paragraph.text.take(MAX_SELECTOR_TEXT_CHARS))
            sb.appendLine()
        }
        return sb.toString()
    }

    fun parseSelectedParagraphIds(rawText: String?, allowedIds: Set<Int>): List<Int> {
        if (rawText.isNullOrBlank()) return emptyList()

        val json = rawText
            .replace("```json", "")
            .replace("```", "")
            .trim()
            .let { text ->
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start >= 0 && end > start) text.substring(start, end + 1) else text
            }

        return try {
            val selection = Gson().fromJson(json, SourceSelectorResult::class.java)
            selection.selectedParagraphIds
                .orEmpty()
                .filter { it in allowedIds }
                .distinct()
                .take(MAX_SELECTOR_PARAGRAPHS)
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse source selector JSON: ${e.message}")
            emptyList()
        }
    }

    /**
     * Persists both the user turn and a friendly assistant error message (with local source
     * card references) to the conversation database.
     *
     * When [paragraphs] or [verses] are non-empty, [SOURCES_SUFFIX] is appended to
     * [errorPrefix] so that [MessageBubble] can auto-expand the sources panel using its
     * phrase-based detection — no schema changes required.
     */
    private suspend fun persistErrorTurns(
        question: String,
        paragraphs: List<CatechismParagraph>,
        verses: List<BibleVerse>,
        errorPrefix: String
    ) {
        val assistantContent = if (paragraphs.isNotEmpty() || verses.isNotEmpty()) {
            "$errorPrefix$SOURCES_SUFFIX"
        } else {
            errorPrefix
        }
        conversationDao.insert(
            ConversationEntity(
                role = "user",
                content = question,
                paragraphIds = paragraphs.joinToString(",") { it.id.toString() }
            )
        )
        conversationDao.insert(
            ConversationEntity(
                role = "assistant",
                content = assistantContent,
                paragraphIds = paragraphs.joinToString(",") { it.id.toString() },
                verseRefs = verses.joinToString(",") { it.reference }
            )
        )
    }

    /**
     * Post-processes the LLM's answer to strip out citations of CCC paragraphs or
     * Scripture verses that were not returned by the local retrieval pipeline.
     */
    fun postProcessAnswer(
        llmAnswer: String,
        retrievedParagraphs: List<CatechismParagraph>,
        retrievedVerses: List<BibleVerse>
    ): String {
        // Regex for CCC citations: matching (CCC §123), CCC §123, [CCC 123], CCC 123.
        // We match section symbol (§), unicode section symbol (\u00A7), and its UTF-8 encoding artifact (Â§).
        val cccRegex = Regex("""(?i)[(\[]?\s*CCC\s*(?:§|Â§|\u00A7|paragraph)?\s*(\d+)\s*[)\]]?""")
        val withCleanedCcc = cccRegex.replace(llmAnswer) { matchResult ->
            val paraId = matchResult.groupValues[1].toIntOrNull()
            if (paraId != null && retrievedParagraphs.any { it.id == paraId }) {
                matchResult.value
            } else {
                ""
            }
        }

        // Regex for Scripture citations: matching (John 3:16), John 3:16, (1 Corinthians 9:22)
        // with optional range like John 3:16-20. We explicitly list valid book names to avoid
        // matching preceding words (like "Valid Romans 8:28-30" matching "Valid Romans" as a book).
        val booksPattern = """(?:1\s+Corinthians|2\s+Corinthians|1\s+Thessalonians|2\s+Thessalonians|1\s+Timothy|2\s+Timothy|1\s+Peter|2\s+Peter|1\s+John|2\s+John|3\s+John|1\s+Samuel|2\s+Samuel|1\s+Kings|2\s+Kings|1\s+Chronicles|2\s+Chronicles|1\s+Maccabees|2\s+Maccabees|Song\s+of\s+Songs|Song\s+of\s+Solomon|Wisdom\s+of\s+Solomon|Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|Ezra|Nehemiah|Tobit|Judith|Esther|Job|Psalms|Psalm|Proverbs|Ecclesiastes|Wisdom|Sirach|Isaiah|Jeremiah|Lamentations|Baruch|Ezekiel|Daniel|Hosea|Joel|Amos|Obadiah|Jonah|Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|Matthew|Mark|Luke|John|Acts|Romans|Galatians|Ephesians|Philippians|Colossians|Titus|Philemon|Hebrews|James|Jude|Revelation)"""
        val scriptureRegex = Regex("""(?i)[(\[]?\s*\b($booksPattern)\s+(\d+)\s*:\s*(\d+)(?:\s*-\s*\d+)?\s*[)\]]?""")
        val withCleanedScripture = scriptureRegex.replace(withCleanedCcc) { matchResult ->
            val book = matchResult.groupValues[1]
            val chapter = matchResult.groupValues[2].toIntOrNull()
            val verse = matchResult.groupValues[3].toIntOrNull()
            if (chapter != null && verse != null && retrievedVerses.any {
                    it.book.equals(book, ignoreCase = true) && it.chapter == chapter && it.verse == verse
                }) {
                matchResult.value
            } else {
                ""
            }
        }

        // Clean up punctuation spacing: collapse whitespace, and delete spaces before punctuation
        return withCleanedScripture
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s+([.,;:!?])"""), "$1")
            .trim()
    }

    /**
     * Builds the grounded user turn prompt with budget enforcement.
     * Budget reduction order per spec §22.5:
     *   1. Drop history (handled at call site — history is optional)
     *   2. Drop extra verses
     *   3. Drop lower-ranked paragraphs
     *   4. Truncate paragraph text with ellipsis
     */
    fun buildUserPrompt(
        question: String,
        paragraphs: List<CatechismParagraph>,
        verses: List<BibleVerse>,
        budgetChars: Int = MAX_PROMPT_CHARS
    ): String {
        var activeParagraphs = paragraphs.take(MAX_PARAGRAPHS)
        var activeVerses = verses.take(MAX_VERSES)

        // Apply per-item character caps
        fun capParagraph(p: CatechismParagraph): CatechismParagraph {
            return if (p.text.length > MAX_PARAGRAPH_CHARS)
                p.copy(text = p.text.take(MAX_PARAGRAPH_CHARS) + "…")
            else p
        }

        fun capVerse(v: BibleVerse): BibleVerse {
            return if (v.text.length > MAX_VERSE_CHARS)
                v.copy(text = v.text.take(MAX_VERSE_CHARS) + "…")
            else v
        }

        fun buildPromptString(
            paras: List<CatechismParagraph>,
            vrs: List<BibleVerse>
        ): String {
            val sb = StringBuilder()
            sb.appendLine("CONTEXT - CCC Paragraphs retrieved for this question:")
            sb.appendLine()
            paras.forEach { para ->
                val capped = capParagraph(para)
                sb.appendLine("CCC §${capped.id}:")
                sb.appendLine(capped.text)
                sb.appendLine()
            }
            if (vrs.isNotEmpty()) {
                sb.appendLine("SCRIPTURE - Verses cited by the above CCC paragraphs:")
                sb.appendLine()
                vrs.forEach { verse ->
                    val capped = capVerse(verse)
                    sb.appendLine("${capped.reference}: ${capped.text}")
                }
                sb.appendLine()
            }
            sb.appendLine("USER QUESTION:")
            sb.appendLine(question)
            return sb.toString()
        }

        // Budget enforcement loop
        var prompt = buildPromptString(activeParagraphs, activeVerses)

        // Step 1: Drop extra verses if over budget
        while (prompt.length > budgetChars && activeVerses.isNotEmpty()) {
            activeVerses = activeVerses.dropLast(1)
            prompt = buildPromptString(activeParagraphs, activeVerses)
        }

        // Step 2: Drop lower-ranked paragraphs if still over budget
        while (prompt.length > budgetChars && activeParagraphs.size > 1) {
            activeParagraphs = activeParagraphs.dropLast(1)
            prompt = buildPromptString(activeParagraphs, activeVerses)
        }

        // Step 3: Truncate the single remaining paragraph if still over budget
        if (prompt.length > budgetChars && activeParagraphs.isNotEmpty()) {
            val spaceForParagraph = budgetChars - buildPromptString(emptyList(), emptyList()).length
            activeParagraphs = listOf(
                activeParagraphs.first().copy(
                    text = activeParagraphs.first().text.take(maxOf(spaceForParagraph, 100)) + "…"
                )
            )
            prompt = buildPromptString(activeParagraphs, emptyList())
        }

        val approxTokens = prompt.length / 4
        Log.d(TAG, "Prompt built: ${prompt.length} chars, ~$approxTokens tokens")

        return prompt
    }

    /**
     * Builds the last N turns of conversation history as GeminiContent items.
     * Each "turn" is one user + one assistant message.
     */
    fun buildConversationHistory(
        recentMessages: List<ConversationEntity>,
        maxTurns: Int = MAX_HISTORY_TURNS
    ): List<GeminiContent> {
        val turns = recentMessages.takeLast(maxTurns * 2)
        return turns.map { msg ->
            GeminiContent(
                role = if (msg.role == "user") "user" else "model",
                parts = listOf(GeminiPart(msg.content))
            )
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

private data class QueryPlannerResult(
    @SerializedName("doctrinal_topic") val doctrinalTopic: String? = null,
    @SerializedName("normalized_question") val normalizedQuestion: String? = null,
    @SerializedName("retrieval_terms") val retrievalTerms: List<String>? = null,
    @SerializedName("scripture_priority") val scripturePriority: Boolean? = null
)

private data class SourceSelectorResult(
    @SerializedName("selected_paragraph_ids") val selectedParagraphIds: List<Int>? = null,
    @SerializedName("reason") val reason: String? = null
)
