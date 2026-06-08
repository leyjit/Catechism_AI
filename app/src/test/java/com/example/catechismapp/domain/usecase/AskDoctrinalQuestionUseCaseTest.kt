package com.example.catechismapp.domain.usecase

import com.example.catechismapp.domain.model.BibleVerse
import com.example.catechismapp.domain.model.CatechismParagraph
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for AskDoctrinalQuestionUseCase prompt building logic.
 * Tests the PRODUCTION buildUserPrompt() and buildConversationHistory() methods directly.
 * Budget enforcement and conversation history handling per spec §22.10.
 */
class AskDoctrinalQuestionUseCaseTest {

    // Use a real instance of the use case's prompt methods
    // Other dependencies are mocked since only prompt-building is under test
    private lateinit var useCase: AskDoctrinalQuestionUseCase

    @Before
    fun setup() {
        useCase = AskDoctrinalQuestionUseCase(
            context = mock(),
            searchCatechism = mock(),
            getScripture = mock(),
            backendHarnessApiService = mock(),
            geminiApiService = mock(),
            userPreferences = mock(),
            conversationDao = mock()
        )
    }

    // --- buildUserPrompt tests ---

    @Test
    fun parsePlannerTerms_extractsRetrievalTerms_fromJsonOnlyResponse() {
        val raw = """
```json
{
  "doctrinal_topic": "Trinity",
  "normalized_question": "What does the Catechism teach about the Trinity?",
  "retrieval_terms": ["Trinity", "Father", "Son", "Holy Spirit", "one God", "three persons"],
  "scripture_priority": true
}
```
        """.trimIndent()

        val terms = useCase.parsePlannerTerms(raw)

        assertEquals(
            listOf("Trinity", "Father", "Son", "Holy Spirit", "one God", "three persons"),
            terms
        )
    }

    @Test
    fun parseSelectedParagraphIds_keepsOnlyAllowedIds_inModelOrder() {
        val raw = """
```json
{"selected_paragraph_ids":[495,9999,963,495],"reason":"directly relevant and scripture-supported"}
```
        """.trimIndent()

        val ids = useCase.parseSelectedParagraphIds(
            rawText = raw,
            allowedIds = setOf(495, 963, 2677)
        )

        assertEquals(listOf(495, 963), ids)
    }

    @Test
    fun buildUserPrompt_withParagraphsAndVerses_containsAllSections() {
        val paragraphs = listOf(
            CatechismParagraph(id = 1, text = "The faith of the Church.")
        )
        val verses = listOf(
            BibleVerse(reference = "John 3:16", book = "John", chapter = 3, verse = 16, text = "For God so loved the world.")
        )

        val prompt = useCase.buildUserPrompt("What is faith?", paragraphs, verses)

        assertTrue("Should contain CONTEXT section", prompt.contains("CONTEXT"))
        assertTrue("Should contain CCC §1", prompt.contains("CCC §1"))
        assertTrue("Should contain SCRIPTURE section", prompt.contains("SCRIPTURE"))
        assertTrue("Should contain John 3:16", prompt.contains("John 3:16"))
        assertTrue("Should contain USER QUESTION", prompt.contains("USER QUESTION"))
        assertTrue("Should contain the question", prompt.contains("What is faith?"))
    }

    @Test
    fun buildUserPrompt_withNoVerses_omitsScriptureSection() {
        val paragraphs = listOf(CatechismParagraph(id = 1, text = "Some text."))
        val prompt = useCase.buildUserPrompt("A question", paragraphs, emptyList())

        assertFalse("Should NOT contain SCRIPTURE section when no verses", prompt.contains("SCRIPTURE"))
    }

    @Test
    fun buildUserPrompt_enforcesParagraphCharCap() {
        val longText = "A".repeat(2000)
        val paragraphs = listOf(CatechismParagraph(id = 1, text = longText))

        val prompt = useCase.buildUserPrompt("question", paragraphs, emptyList())

        // With 800 char cap, the original 2000-char block must be truncated
        assertFalse("Paragraph text should be capped", prompt.contains("A".repeat(900)))
        assertTrue("Capped paragraph should end with ellipsis", prompt.contains("…"))
    }

    @Test
    fun buildUserPrompt_dropsVersesWhenOverBudget() {
        val bigText = "B".repeat(800)
        val paragraphs = (1..5).map { CatechismParagraph(id = it, text = bigText) }
        val bigVerseText = "V".repeat(300)
        val verses = (1..8).map {
            BibleVerse(reference = "Ref $it", book = "Book", chapter = 1, verse = it, text = bigVerseText)
        }

        val prompt = useCase.buildUserPrompt("question", paragraphs, verses)

        // Total should fit in budget after reduction
        assertTrue("Prompt should be within 12k char budget", prompt.length <= 12_000)
    }

    @Test
    fun buildUserPrompt_reducedBudget_dropsParagraphsAccordingly() {
        // Simulate history taking up 8000 chars of the 12000 budget -> effective budget = 4000
        val bigText = "C".repeat(800)
        val paragraphs = (1..5).map { CatechismParagraph(id = it, text = bigText) }

        val prompt = useCase.buildUserPrompt("question", paragraphs, emptyList(), budgetChars = 4_000)

        assertTrue("Reduced-budget prompt should fit", prompt.length <= 4_000)
    }

    @Test
    fun buildUserPrompt_doesNotEmbedHistoryInline() {
        // buildUserPrompt does NOT embed conversation history inline — that's handled separately
        // by buildConversationHistory. This verifies the prompt has no cross-contamination.
        val paragraphs = listOf(CatechismParagraph(id = 1, text = "Text."))
        val prompt = useCase.buildUserPrompt("New question", paragraphs, emptyList())

        // The prompt should contain no "role" markers or chat-history syntax
        assertFalse("Prompt must not contain 'user:' chat markers", prompt.contains("user:"))
        assertFalse("Prompt must not contain 'model:' chat markers", prompt.contains("model:"))
    }

    // --- buildConversationHistory tests ---

    @Test
    fun buildConversationHistory_capsAtMaxTurns() {
        // 10 messages = 5 turns; should only take last 2 turns = 4 messages
        val messages = (1..10).map { i ->
            com.example.catechismapp.data.local.entity.ConversationEntity(
                role = if (i % 2 == 1) "user" else "assistant",
                content = "Message $i"
            )
        }

        val result = useCase.buildConversationHistory(messages, maxTurns = 2)

        assertEquals("Should take last 2 turns (4 messages)", 4, result.size)
        assertTrue("Last entry content should be message 10", result.last().parts.first().text.contains("Message 10"))
    }

    @Test
    fun buildConversationHistory_mapsRolesToGeminiFormat() {
        val messages = listOf(
            com.example.catechismapp.data.local.entity.ConversationEntity(role = "user", content = "User turn"),
            com.example.catechismapp.data.local.entity.ConversationEntity(role = "assistant", content = "Assistant turn")
        )

        val result = useCase.buildConversationHistory(messages)

        assertEquals("user", result[0].role)
        assertEquals("model", result[1].role)  // assistant -> model for Gemini API
    }

    @Test
    fun buildConversationHistory_emptyHistory_returnsEmptyList() {
        val result = useCase.buildConversationHistory(emptyList())
        assertTrue(result.isEmpty())
    }

    // --- postProcessAnswer tests ---

    @Test
    fun postProcessAnswer_preservesValidCitations() {
        val paragraphs = listOf(
            CatechismParagraph(id = 1213, text = "Baptism is the basis of the whole Christian life.")
        )
        val verses = listOf(
            BibleVerse(reference = "Matthew 28:19", book = "Matthew", chapter = 28, verse = 19, text = "Go therefore and make disciples of all nations.")
        )

        val rawAnswer = "The Church teaches about Baptism in (CCC §1213) as shown in (Matthew 28:19)."
        val processed = useCase.postProcessAnswer(rawAnswer, paragraphs, verses)

        assertEquals("The Church teaches about Baptism in (CCC §1213) as shown in (Matthew 28:19).", processed)
    }

    @Test
    fun postProcessAnswer_stripsInvalidCccCitations() {
        val paragraphs = listOf(
            CatechismParagraph(id = 1213, text = "Baptism is the basis...")
        )
        val verses = emptyList<BibleVerse>()

        // Check standard § symbol, Â§ representation symbol, and unicode \u00A7 character
        val rawAnswer1 = "Baptism is defined in (CCC §1213) and also referenced in (CCC §9999)."
        val rawAnswer2 = "Baptism is defined in (CCC §1213) and also referenced in (CCC Â§9999)."
        val rawAnswer3 = "Baptism is defined in (CCC §1213) and also referenced in (CCC \u00A79999)."

        assertEquals("Baptism is defined in (CCC §1213) and also referenced in.", useCase.postProcessAnswer(rawAnswer1, paragraphs, verses))
        assertEquals("Baptism is defined in (CCC §1213) and also referenced in.", useCase.postProcessAnswer(rawAnswer2, paragraphs, verses))
        assertEquals("Baptism is defined in (CCC §1213) and also referenced in.", useCase.postProcessAnswer(rawAnswer3, paragraphs, verses))
    }

    @Test
    fun postProcessAnswer_stripsInvalidScriptureCitations() {
        val paragraphs = emptyList<CatechismParagraph>()
        val verses = listOf(
            BibleVerse(reference = "Matthew 28:19", book = "Matthew", chapter = 28, verse = 19, text = "Go therefore...")
        )

        val rawAnswer = "See (Matthew 28:19) and also (John 3:16)."
        val processed = useCase.postProcessAnswer(rawAnswer, paragraphs, verses)

        // (John 3:16) should be stripped, leaving "See (Matthew 28:19) and also."
        assertEquals("See (Matthew 28:19) and also.", processed)
    }

    @Test
    fun postProcessAnswer_handlesOptionalSpacesAndBrackets() {
        val paragraphs = listOf(CatechismParagraph(id = 100, text = "Test"))
        val verses = listOf(BibleVerse(reference = "Romans 8:28", book = "Romans", chapter = 8, verse = 28, text = "All things work together"))

        // CCC with brackets, spaces, scripture range, etc.
        val rawAnswer = "Valid [CCC 100] and invalid CCC 999. Valid Romans 8:28-30 and invalid (John 3:16)."
        val processed = useCase.postProcessAnswer(rawAnswer, paragraphs, verses)

        assertEquals("Valid [CCC 100] and invalid. Valid Romans 8:28-30 and invalid.", processed)
    }

    @Test
    fun postProcessAnswer_noCitations_returnsCleanAnswer() {
        val rawAnswer = "This is a simple answer without any citations."
        val processed = useCase.postProcessAnswer(rawAnswer, emptyList(), emptyList())
        assertEquals("This is a simple answer without any citations.", processed)
    }
}
