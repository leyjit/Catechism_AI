package com.example.catechismapp.ui.chat

import com.example.catechismapp.data.local.BibleVerseDao
import com.example.catechismapp.data.local.CatechismDao
import com.example.catechismapp.data.local.ConversationDao
import com.example.catechismapp.data.local.entity.ConversationEntity
import com.example.catechismapp.data.preferences.UserPreferences
import com.example.catechismapp.domain.model.AnswerErrorType
import com.example.catechismapp.domain.model.QueryResult
import com.example.catechismapp.domain.usecase.AskDoctrinalQuestionUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val askUseCase = mockk<AskDoctrinalQuestionUseCase>()
    private val conversationDao = mockk<ConversationDao>()
    private val catechismDao = mockk<CatechismDao>()
    private val bibleVerseDao = mockk<BibleVerseDao>()
    private val userPreferences = mockk<UserPreferences>()

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Stub standard reactive properties
        every { conversationDao.getAllMessages() } returns flowOf(emptyList())
        every { userPreferences.apiKeyFlow } returns flowOf("fake-api-key")

        viewModel = ChatViewModel(
            askDoctrinalQuestion = askUseCase,
            conversationDao = conversationDao,
            catechismDao = catechismDao,
            bibleVerseDao = bibleVerseDao,
            userPreferences = userPreferences
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdleAndNotLoading() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun messages_ordersLatestConversationFirst_butKeepsQuestionBeforeAnswer() = runTest(testDispatcher) {
        val orderedMessages = listOf(
            ConversationEntity(id = 1, role = "user", content = "Old question", timestamp = 1L),
            ConversationEntity(id = 2, role = "assistant", content = "Old answer", timestamp = 2L),
            ConversationEntity(id = 3, role = "user", content = "New question", timestamp = 3L),
            ConversationEntity(id = 4, role = "assistant", content = "New answer", timestamp = 4L)
        )
        every { conversationDao.getAllMessages() } returns flowOf(orderedMessages)

        viewModel = ChatViewModel(
            askDoctrinalQuestion = askUseCase,
            conversationDao = conversationDao,
            catechismDao = catechismDao,
            bibleVerseDao = bibleVerseDao,
            userPreferences = userPreferences
        )

        val messages = viewModel.messages.first { it.isNotEmpty() }
        assertEquals(
            listOf("New question", "New answer", "Old question", "Old answer"),
            messages.map { it.content }
        )
    }

    @Test
    fun sendQuestion_triggersLoadingState_andSucceeds() = runTest(testDispatcher) {
        // Arrange
        val question = "What is Baptism?"
        coEvery { askUseCase(question) } returns QueryResult(
            question = question,
            paragraphs = emptyList(),
            verses = emptyList(),
            llmAnswer = "Baptism is...",
            errorType = null
        )

        // Act
        viewModel.sendQuestion(question)
        
        // Assert: should be in loading state initially
        assertTrue(viewModel.uiState.value.isLoading)

        // Advance coroutines
        advanceUntilIdle()

        // Assert: loading complete, no error
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
        coVerify(exactly = 1) { askUseCase(question) }
    }

    @Test
    fun sendQuestion_withRateLimited_showsNoSnackbar_errorIsInChatMessage() = runTest(testDispatcher) {
        // RATE_LIMITED is now persisted to the DB as a chat message with source cards.
        // ChatViewModel should NOT show a redundant snackbar for it.
        val question = "What is Purgatory?"
        coEvery { askUseCase(question) } returns QueryResult(
            question = question,
            paragraphs = emptyList(),
            verses = emptyList(),
            llmAnswer = null,
            errorType = AnswerErrorType.RATE_LIMITED
        )

        // Act
        viewModel.sendQuestion(question)
        advanceUntilIdle()

        // Assert: not loading, no snackbar error — error is already in the persisted chat message
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun sendQuestion_withUnauthorized_showsSettingsSnackbar() = runTest(testDispatcher) {
        // UNAUTHORIZED is the one error that still shows a snackbar because it is actionable:
        // the user must go to Settings to update their API key.
        val question = "What is grace?"
        coEvery { askUseCase(question) } returns QueryResult(
            question = question,
            paragraphs = emptyList(),
            verses = emptyList(),
            llmAnswer = null,
            errorType = AnswerErrorType.UNAUTHORIZED
        )

        // Act
        viewModel.sendQuestion(question)
        advanceUntilIdle()

        // Assert: snackbar contains actionable Settings message
        assertFalse(viewModel.uiState.value.isLoading)
        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(
            "Expected 'Settings' in error message, got: $error",
            error!!.contains("Settings")
        )
    }

    @Test
    fun clearConversation_delegatesToDao() = runTest(testDispatcher) {
        // Arrange
        coEvery { conversationDao.clearAll() } just Runs

        // Act
        viewModel.clearConversation()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { conversationDao.clearAll() }
    }
}
