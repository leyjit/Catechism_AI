package com.example.catechismapp.ui.chat

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catechismapp.data.local.AppDatabase
import com.example.catechismapp.data.local.entity.CatechismEntity
import com.example.catechismapp.data.preferences.UserPreferences
import com.example.catechismapp.data.remote.BackendHarnessApiService
import com.example.catechismapp.data.remote.GeminiApiService
import com.example.catechismapp.domain.model.AnswerErrorType
import com.example.catechismapp.domain.usecase.AskDoctrinalQuestionUseCase
import com.example.catechismapp.domain.usecase.GetScriptureForParagraphsUseCase
import com.example.catechismapp.domain.usecase.SearchCatechismUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatOfflineAndErrorTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var userPreferences: UserPreferences
    private lateinit var mockBackendService: BackendHarnessApiService
    private lateinit var mockApiService: GeminiApiService
    private lateinit var mockContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var askDoctrinalQuestionUseCase: AskDoctrinalQuestionUseCase
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Use an in-memory database so tests are fast and self-contained
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        userPreferences = UserPreferences(context)
        
        // Mock API service (should never be called)
        mockBackendService = mockk()
        mockApiService = mockk()
        AskDoctrinalQuestionUseCase.backendHarnessEnabledOverride = false

        // Mock ConnectivityManager to report network is unavailable
        mockConnectivityManager = mockk()
        every { mockConnectivityManager.activeNetwork } returns null
        every { mockConnectivityManager.getNetworkCapabilities(any()) } returns null

        // Mock Context to return our mocked ConnectivityManager
        mockContext = mockk()
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockContext.assets } returns context.assets

        // Build real search and scripture use cases against our in-memory DB
        val searchCatechism = SearchCatechismUseCase(database.catechismDao())
        val getScripture = GetScriptureForParagraphsUseCase(database.bibleVerseDao(), mockk(relaxed = true))

        askDoctrinalQuestionUseCase = AskDoctrinalQuestionUseCase(
            context = mockContext,
            searchCatechism = searchCatechism,
            getScripture = getScripture,
            backendHarnessApiService = mockBackendService,
            geminiApiService = mockApiService,
            userPreferences = userPreferences,
            conversationDao = database.conversationDao()
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun sendQuestion_withApiKeyNullAndNetworkUnavailable_persistsSourceCards_andShowsNoLlmAnswer(): Unit = runBlocking {
        // Arrange
        // Insert a test CCC paragraph into the in-memory database
        val testParagraph = CatechismEntity(id = 1030, text = "All who die in God's grace... Purgatory.")
        database.catechismDao().insertAll(listOf(testParagraph))

        // Set API key to null (missing API key)
        userPreferences.clearApiKey()

        // Instantiate ChatViewModel using Main dispatcher for UI flows
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        viewModel = ChatViewModel(
            askDoctrinalQuestion = askDoctrinalQuestionUseCase,
            conversationDao = database.conversationDao(),
            catechismDao = database.catechismDao(),
            bibleVerseDao = database.bibleVerseDao(),
            userPreferences = userPreferences
        )

        // Act - Send question
        viewModel.sendQuestion("What is Purgatory?")
        
        // Wait for coroutines to execute
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert - ViewModel state is not loading and has no error snackbar (error is persisted in message instead)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        // Assert - Conversation history contains 2 messages (user turn + assistant fallback turn)
        val messagesList = viewModel.messages.first()
        assertEquals(2, messagesList.size)

        val userMsg = messagesList[0]
        assertTrue(userMsg.isUser)
        assertEquals("What is Purgatory?", userMsg.content)

        val assistantMsg = messagesList[1]
        assertFalse(assistantMsg.isUser)
        assertTrue(
            "Assistant message should contain the fallback text",
            assistantMsg.content.contains("No API key configured")
        )
        assertTrue(
            "Assistant message should contain the sources suffix",
            assistantMsg.content.contains(AskDoctrinalQuestionUseCase.SOURCES_SUFFIX.trim())
        )

        // Assert - Local source cards are attached to the assistant message
        assertEquals(1, assistantMsg.paragraphs.size)
        assertEquals(1030, assistantMsg.paragraphs[0].id)
        assertEquals("All who die in God's grace... Purgatory.", assistantMsg.paragraphs[0].text)
    }
}
