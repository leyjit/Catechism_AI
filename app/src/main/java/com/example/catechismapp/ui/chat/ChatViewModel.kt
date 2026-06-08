package com.example.catechismapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catechismapp.BuildConfig
import com.example.catechismapp.data.local.BibleVerseDao
import com.example.catechismapp.data.local.CatechismDao
import com.example.catechismapp.data.local.ConversationDao
import com.example.catechismapp.data.local.entity.ConversationEntity
import com.example.catechismapp.data.preferences.UserPreferences
import com.example.catechismapp.data.scripture.ScriptureReferenceParser
import com.example.catechismapp.domain.model.AnswerErrorType
import com.example.catechismapp.domain.model.BibleVerse
import com.example.catechismapp.domain.model.CatechismParagraph
import com.example.catechismapp.domain.model.ChatMessage
import com.example.catechismapp.domain.usecase.AskDoctrinalQuestionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val askDoctrinalQuestion: AskDoctrinalQuestionUseCase,
    private val conversationDao: ConversationDao,
    private val catechismDao: CatechismDao,
    private val bibleVerseDao: BibleVerseDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Expose reactive status of the API key configuration
    val isApiKeyMissing: StateFlow<Boolean> = if (BuildConfig.USE_BACKEND_HARNESS) {
        MutableStateFlow(false)
    } else {
        userPreferences.apiKeyFlow
            .map { it.isNullOrBlank() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    }

    // Load and reactively hydrate database messages to domain ChatMessage objects
    val messages: StateFlow<List<ChatMessage>> = conversationDao.getAllMessages()
        .map { entities -> hydrateMessages(entities) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendQuestion(question: String) {
        if (question.trim().isBlank()) return
        if (_uiState.value.isLoading) return

        _uiState.value = ChatUiState(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val result = askDoctrinalQuestion(question)
                
                // Only UNAUTHORIZED gets a snackbar — the user must act (go to Settings).
                // All other error types (rate-limited, server error, content blocked, network)
                // are now persisted as assistant chat messages with local source cards, so
                // a redundant snackbar would be confusing and is suppressed here.
                val errorMsg = when (result.errorType) {
                    AnswerErrorType.UNAUTHORIZED ->
                        "Your API key was not accepted. Please check or update it in Settings."
                    AnswerErrorType.RATE_LIMITED,
                    AnswerErrorType.SERVER_ERROR,
                    AnswerErrorType.CONTENT_BLOCKED,
                    AnswerErrorType.NETWORK_ERROR,
                    AnswerErrorType.MISSING_API_KEY -> null // error already in chat message
                    null -> null
                }

                // Only show the "no results" fallback when the API succeeded (errorType == null)
                // but returned nothing — all other error paths have already written to the DB.
                val finalError = if (errorMsg == null
                        && result.errorType == null
                        && result.llmAnswer == null
                        && result.paragraphs.isEmpty()) {
                    "No CCC paragraphs matched your question. Try rephrasing."
                } else {
                    errorMsg
                }

                _uiState.value = ChatUiState(isLoading = false, error = finalError)
            } catch (e: Exception) {
                _uiState.value = ChatUiState(isLoading = false, error = "An unexpected error occurred: ${e.localizedMessage}")
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            conversationDao.clearAll()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun hydrateMessages(entities: List<ConversationEntity>): List<ChatMessage> {
        return entities.map { entity ->
            if (entity.role == "user") {
                ChatMessage(
                    id = entity.id,
                    role = entity.role,
                    content = entity.content,
                    timestamp = entity.timestamp
                )
            } else {
                // Hydrate CCC Paragraphs
                val paraIds = entity.paragraphIds.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                val paragraphEntities = if (paraIds.isNotEmpty()) {
                    catechismDao.getByIds(paraIds)
                } else {
                    emptyList()
                }
                val paragraphs = paragraphEntities.map {
                    CatechismParagraph(id = it.id, text = it.text)
                }

                // Hydrate Bible Verses
                val refs = entity.verseRefs.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val verses = refs.mapNotNull { ref ->
                    val parsed = ScriptureReferenceParser.parse(ref) ?: return@mapNotNull null
                    val verseEntity = bibleVerseDao.getVerse(parsed.book, parsed.chapter, parsed.verse) ?: return@mapNotNull null
                    BibleVerse(
                        reference = ref,
                        book = verseEntity.book,
                        chapter = verseEntity.chapter,
                        verse = verseEntity.verse,
                        text = verseEntity.text
                    )
                }

                ChatMessage(
                    id = entity.id,
                    role = entity.role,
                    content = entity.content,
                    timestamp = entity.timestamp,
                    paragraphs = paragraphs,
                    verses = verses
                )
            }
        }
    }
}
