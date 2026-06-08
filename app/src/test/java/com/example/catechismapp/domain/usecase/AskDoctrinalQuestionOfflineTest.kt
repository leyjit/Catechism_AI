package com.example.catechismapp.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.catechismapp.data.remote.GeminiCandidate
import com.example.catechismapp.data.remote.GeminiContent
import com.example.catechismapp.data.remote.GeminiPart
import com.example.catechismapp.data.remote.GeminiResponse
import com.example.catechismapp.data.remote.BackendHarnessApiService
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import com.example.catechismapp.data.local.ConversationDao
import com.example.catechismapp.data.preferences.UserPreferences
import com.example.catechismapp.data.remote.GeminiApiService
import com.example.catechismapp.domain.model.AnswerErrorType
import com.example.catechismapp.domain.model.BibleVerse
import com.example.catechismapp.domain.model.CatechismParagraph
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for offline and no-key paths in AskDoctrinalQuestionUseCase.
 * Uses MockK which correctly handles Kotlin suspend functions per spec §22.10.
 */
class AskDoctrinalQuestionOfflineTest {

    private lateinit var mockSearch: SearchCatechismUseCase
    private lateinit var mockScripture: GetScriptureForParagraphsUseCase
    private lateinit var mockBackend: BackendHarnessApiService
    private lateinit var mockApi: GeminiApiService
    private lateinit var mockPreferences: UserPreferences
    private lateinit var mockConversationDao: ConversationDao
    private lateinit var useCase: AskDoctrinalQuestionUseCase

    private val fakeParagraphs = listOf(
        CatechismParagraph(id = 1030, text = "All who die in God's grace..."),
        CatechismParagraph(id = 1031, text = "The Church gives the name Purgatory...")
    )
    private val fakeVerses = listOf(
        BibleVerse(
            reference = "1 Cor 3:15",
            book = "1 Corinthians",
            chapter = 3,
            verse = 15,
            text = "he himself will be saved, but only as through fire."
        )
    )

    @Before
    fun setup() {
        mockSearch = mockk()
        mockScripture = mockk()
        mockBackend = mockk()
        mockApi = mockk()
        mockPreferences = mockk()
        mockConversationDao = mockk()

        coEvery { mockConversationDao.insert(any()) } returns 1L
        AskDoctrinalQuestionUseCase.backendHarnessEnabledOverride = false

        useCase = AskDoctrinalQuestionUseCase(
            context = mockk(relaxed = true),
            searchCatechism = mockSearch,
            getScripture = mockScripture,
            backendHarnessApiService = mockBackend,
            geminiApiService = mockApi,
            userPreferences = mockPreferences,
            conversationDao = mockConversationDao
        )
    }

    @Test
    fun noApiKey_returnsOfflineResult_withRetrievedParagraphs_andNeverCallsApi(): Unit = runBlocking {
        // Arrange
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns null

        // Act
        val result = useCase("What does the Church teach about Purgatory?")

        // Assert
        assertNull("llmAnswer should be null when no API key", result.llmAnswer)
        assertEquals("Error type should be MISSING_API_KEY", AnswerErrorType.MISSING_API_KEY, result.errorType)
        assertEquals("Retrieved paragraphs should be present", 2, result.paragraphs.size)
        assertEquals("Retrieved verses should be present", 1, result.verses.size)
        assertFalse("Question should be preserved", result.question.isBlank())

        // Verify the API was never contacted
        coVerify(exactly = 0) { mockApi.generateContent(any(), any()) }
    }

    @Test
    fun blankApiKey_returnsOfflineResult_andNeverCallsApi(): Unit = runBlocking {
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns emptyList()
        coEvery { mockPreferences.getApiKey() } returns "   "  // blank string

        val result = useCase("What is grace?")

        assertEquals(AnswerErrorType.MISSING_API_KEY, result.errorType)
        assertNull(result.llmAnswer)
        coVerify(exactly = 0) { mockApi.generateContent(any(), any()) }
    }

    @Test
    fun noApiKey_withNoParagraphsFound_returnsEmptySourcesGracefully(): Unit = runBlocking {
        coEvery { mockSearch(any(), any()) } returns emptyList()
        coEvery { mockPreferences.getApiKey() } returns null

        val result = useCase("xyzzy nonsense question")

        assertEquals(AnswerErrorType.MISSING_API_KEY, result.errorType)
        assertNull(result.llmAnswer)
        assertTrue("Paragraphs should be empty for no-match query", result.paragraphs.isEmpty())
    }

    @Test
    fun noApiKey_apiIsNeverContacted(): Unit = runBlocking {
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns emptyList()
        coEvery { mockPreferences.getApiKey() } returns null

        useCase("A question about baptism")

        // On offline path, generateContent must NEVER be called
        coVerify(exactly = 0) { mockApi.generateContent(any(), any()) }
    }

    @Test
    fun scriptureLookup_usesBroaderSourceWindow_thanPromptParagraphLimit(): Unit = runBlocking {
        val trinityParagraphs = listOf(
            CatechismParagraph(id = 232, text = "Baptized in the name of the Father and of the Son and of the Holy Spirit."),
            CatechismParagraph(id = 233, text = "There is only one God, the almighty Father, his only Son and the Holy Spirit."),
            CatechismParagraph(id = 234, text = "The mystery of the Most Holy Trinity is central."),
            CatechismParagraph(id = 235, text = "The mystery of the Blessed Trinity was revealed."),
            CatechismParagraph(id = 236, text = "Theology refers to God's inmost life within the Blessed Trinity."),
            CatechismParagraph(id = 267, text = "The divine persons show forth what is proper to them in the Trinity.")
        )
        coEvery { mockSearch(any(), any()) } returns trinityParagraphs
        coEvery { mockScripture(match { it.contains(267) }) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns null

        val result = useCase("What is the Trinity?")

        assertEquals(
            "Prompt/source paragraph list should stay capped to the top five CCC paragraphs",
            5,
            result.paragraphs.size
        )
        assertEquals("Scripture should include verses from the broader retrieval window", 1, result.verses.size)
        coVerify(exactly = 1) {
            mockScripture(match { ids -> ids.contains(267) && ids.size == trinityParagraphs.size })
        }
    }

    @Test
    fun invoke_withSuccessfulButMisbehavingResponse_filtersInvalidCitationsAndRetainsOriginalSources(): Unit = runBlocking {
        // Arrange: Mock connectivity
        val mockContext = mockk<Context>()
        val mockCm = mockk<ConnectivityManager>()
        val mockNet = mockk<Network>()
        val mockCaps = mockk<NetworkCapabilities>()
        coEvery { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockCm
        coEvery { mockCm.activeNetwork } returns mockNet
        coEvery { mockCm.getNetworkCapabilities(mockNet) } returns mockCaps
        coEvery { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        val useCaseWithMockContext = AskDoctrinalQuestionUseCase(
            context = mockContext,
            searchCatechism = mockSearch,
            getScripture = mockScripture,
            backendHarnessApiService = mockBackend,
            geminiApiService = mockApi,
            userPreferences = mockPreferences,
            conversationDao = mockConversationDao
        )

        // Arrange: Mock dependencies
        val testParagraphs = listOf(
            CatechismParagraph(id = 1030, text = "Purgatory paragraph text.")
        )
        val testVerses = listOf(
            BibleVerse(reference = "1 Cor 3:15", book = "1 Corinthians", chapter = 3, verse = 15, text = "Only as through fire.")
        )
        coEvery { mockSearch(any(), any()) } returns testParagraphs
        coEvery { mockScripture(any()) } returns testVerses
        coEvery { mockPreferences.getApiKey() } returns "valid-api-key"
        coEvery { mockConversationDao.getRecentMessages(any()) } returns emptyList()
        coEvery { mockConversationDao.insert(any()) } returns 1L

        // Mock a response containing both a valid citation (CCC §1030) and an invalid citation (CCC §9999 / John 3:16)
        val mockLlmAnswer = "Purgatory is described in (CCC §1030) and shown in (1 Cor 3:15). But (CCC §9999) and (John 3:16) are wrong."
        val mockResponse = Response.success(
            GeminiResponse(
                candidates = listOf(
                    GeminiCandidate(
                        content = GeminiContent(
                            role = "model",
                            parts = listOf(GeminiPart(mockLlmAnswer))
                        ),
                        finishReason = null
                    )
                ),
                error = null
            )
        )
        coEvery { mockApi.generateContent("valid-api-key", any()) } returns mockResponse

        // Act
        val result = useCaseWithMockContext("What is Purgatory?")

        // Assert
        assertNotNull(result.llmAnswer)
        // Check that valid citations are kept, invalid stripped, and spacing formatted:
        val expectedProcessedAnswer = "Purgatory is described in (CCC §1030) and shown in (1 Cor 3:15). But and are wrong."
        assertEquals(expectedProcessedAnswer, result.llmAnswer)

        // Verify we still retain the original retrieved sources in the QueryResult
        assertEquals(1, result.paragraphs.size)
        assertEquals(1030, result.paragraphs[0].id)
        assertEquals(1, result.verses.size)
        assertEquals("1 Cor 3:15", result.verses[0].reference)
        assertNull(result.errorType)

        // Verify the conversation history was populated with the processed answer, not the raw LLM answer
        coVerify(exactly = 1) {
            mockConversationDao.insert(
                match { it.role == "assistant" && it.content == expectedProcessedAnswer }
            )
        }
    }

    // ── Phase 9: API error persistence tests ──────────────────────────────────────────────────

    /**
     * Helper that creates an [AskDoctrinalQuestionUseCase] with a mocked network-available
     * context, reusing the top-level mocks for the other dependencies.
     */
    private fun useCaseWithNetwork(): AskDoctrinalQuestionUseCase {
        val mockContext = mockk<Context>()
        val mockCm = mockk<ConnectivityManager>()
        val mockNet = mockk<Network>()
        val mockCaps = mockk<NetworkCapabilities>()
        coEvery { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockCm
        coEvery { mockCm.activeNetwork } returns mockNet
        coEvery { mockCm.getNetworkCapabilities(mockNet) } returns mockCaps
        coEvery { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        return AskDoctrinalQuestionUseCase(
            context = mockContext,
            searchCatechism = mockSearch,
            getScripture = mockScripture,
            backendHarnessApiService = mockBackend,
            geminiApiService = mockApi,
            userPreferences = mockPreferences,
            conversationDao = mockConversationDao
        )
    }

    @Test
    fun rateLimited_persistsBothTurnsWithSourceCards_andReturnsSources(): Unit = runBlocking {
        // Arrange
        val useCase = useCaseWithNetwork()
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns "valid-key"
        coEvery { mockConversationDao.getRecentMessages(any()) } returns emptyList()
        coEvery { mockApi.generateContent(any(), any()) } returns
            Response.error(429, "".toResponseBody(null))

        // Act
        val result = useCase("What is Purgatory?")

        // Assert: errorType is returned for routing decisions in ChatViewModel
        assertEquals(AnswerErrorType.RATE_LIMITED, result.errorType)
        assertNull(result.llmAnswer)
        // Local sources are still returned in the QueryResult
        assertEquals(2, result.paragraphs.size)
        assertEquals(1, result.verses.size)

        // Both conversation turns are persisted (user + assistant)
        coVerify(exactly = 1) {
            mockConversationDao.insert(match { it.role == "user" })
        }
        // Assistant message must contain SOURCES_SUFFIX so MessageBubble auto-expands sources
        coVerify(exactly = 1) {
            mockConversationDao.insert(
                match { entity ->
                    entity.role == "assistant" &&
                    entity.content.contains("Here are the CCC paragraphs most relevant to your question:") &&
                    entity.paragraphIds.isNotBlank()
                }
            )
        }
    }

    @Test
    fun unauthorized_persistsBothTurns_andReturnsUnauthorizedErrorType(): Unit = runBlocking {
        // Arrange
        val useCase = useCaseWithNetwork()
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns "bad-key"
        coEvery { mockConversationDao.getRecentMessages(any()) } returns emptyList()
        coEvery { mockApi.generateContent(any(), any()) } returns
            Response.error(401, "".toResponseBody(null))

        // Act
        val result = useCase("What is grace?")

        // Assert: UNAUTHORIZED is returned so ChatViewModel can show "Go to Settings" snackbar
        assertEquals(AnswerErrorType.UNAUTHORIZED, result.errorType)
        assertNull(result.llmAnswer)

        // Both turns persisted — user sees their question and source cards immediately
        coVerify(exactly = 1) {
            mockConversationDao.insert(match { it.role == "user" })
        }
        coVerify(exactly = 1) {
            mockConversationDao.insert(
                match { entity ->
                    entity.role == "assistant" &&
                    entity.content.contains("Here are the CCC paragraphs most relevant to your question:") &&
                    entity.paragraphIds.isNotBlank()
                }
            )
        }
    }

    @Test
    fun contentBlocked_persistsBothTurns_andSourcesAreAttached(): Unit = runBlocking {
        // Arrange
        val useCase = useCaseWithNetwork()
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns "valid-key"
        coEvery { mockConversationDao.getRecentMessages(any()) } returns emptyList()
        // Simulate a successful HTTP 200 with empty candidates (content blocked)
        coEvery { mockApi.generateContent(any(), any()) } returns Response.success(
            com.example.catechismapp.data.remote.GeminiResponse(candidates = emptyList(), error = null)
        )

        // Act
        val result = useCase("A question that gets blocked")

        // Assert
        assertEquals(AnswerErrorType.CONTENT_BLOCKED, result.errorType)
        assertNull(result.llmAnswer)

        coVerify(exactly = 1) {
            mockConversationDao.insert(
                match { entity ->
                    entity.role == "assistant" &&
                    entity.content.contains("Here are the CCC paragraphs most relevant to your question:")
                }
            )
        }
    }

    @Test
    fun serverError5xx_persistsBothTurns_andReturnsServerErrorType(): Unit = runBlocking {
        // Arrange
        val useCase = useCaseWithNetwork()
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns "valid-key"
        coEvery { mockConversationDao.getRecentMessages(any()) } returns emptyList()
        coEvery { mockApi.generateContent(any(), any()) } returns
            Response.error(500, "".toResponseBody(null))

        // Act
        val result = useCase("What is grace?")

        // Assert
        assertEquals(AnswerErrorType.SERVER_ERROR, result.errorType)
        assertNull(result.llmAnswer)

        coVerify(exactly = 1) {
            mockConversationDao.insert(
                match { entity ->
                    entity.role == "assistant" &&
                    entity.content.contains("The AI service is temporarily unavailable")
                }
            )
        }
    }

    @Test
    fun timeout_persistsBothTurns_andReturnsServerErrorType(): Unit = runBlocking {
        // Arrange
        val useCase = useCaseWithNetwork()
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns "valid-key"
        coEvery { mockConversationDao.getRecentMessages(any()) } returns emptyList()
        coEvery { mockApi.generateContent(any(), any()) } throws java.net.SocketTimeoutException("Read timeout")

        // Act
        val result = useCase("What is grace?")

        // Assert
        assertEquals(AnswerErrorType.SERVER_ERROR, result.errorType)
        assertNull(result.llmAnswer)

        coVerify(exactly = 1) {
            mockConversationDao.insert(
                match { entity ->
                    entity.role == "assistant" &&
                    entity.content.contains("The AI service is temporarily unavailable")
                }
            )
        }
    }

    @Test
    fun networkInterrupted_persistsBothTurns_andReturnsNetworkErrorType(): Unit = runBlocking {
        // Arrange
        val useCase = useCaseWithNetwork()
        coEvery { mockSearch(any(), any()) } returns fakeParagraphs
        coEvery { mockScripture(any()) } returns fakeVerses
        coEvery { mockPreferences.getApiKey() } returns "valid-key"
        coEvery { mockConversationDao.getRecentMessages(any()) } returns emptyList()
        coEvery { mockApi.generateContent(any(), any()) } throws java.io.IOException("Connection reset")

        // Act
        val result = useCase("What is grace?")

        // Assert
        assertEquals(AnswerErrorType.NETWORK_ERROR, result.errorType)
        assertNull(result.llmAnswer)

        coVerify(exactly = 1) {
            mockConversationDao.insert(
                match { entity ->
                    entity.role == "assistant" &&
                    entity.content.contains("The connection was interrupted before a response arrived")
                }
            )
        }
    }
}
