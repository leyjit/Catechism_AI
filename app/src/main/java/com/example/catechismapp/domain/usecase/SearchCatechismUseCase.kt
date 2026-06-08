package com.example.catechismapp.domain.usecase

import android.util.Log
import com.example.catechismapp.data.local.CatechismDao
import com.example.catechismapp.data.local.entity.CatechismEntity
import com.example.catechismapp.domain.model.CatechismParagraph
import javax.inject.Inject

object QueryPreprocessor {

    // Words that are common in conversational questions but weak for CCC retrieval.
    private val STOP_WORDS = setOf(
        "what", "does", "the", "church", "teach", "about", "is", "are",
        "how", "why", "when", "where", "who", "a", "an", "and", "or",
        "in", "of", "to", "for", "on", "with", "do", "can", "i", "me",
        "my", "we", "our", "be", "have", "has", "was", "were", "will",
        "catholic", "catholics", "christian", "christians", "believe",
        "believes", "believing", "belief", "beliefs", "called", "call",
        "explain", "basis", "biblical"
    )

    private val TOPIC_EXPANSIONS = mapOf(
        "trinity" to listOf("trinity", "father", "son", "holy", "spirit"),
        "mary" to listOf("mary", "mother", "god", "virgin", "jesus", "lord"),
        "virgin" to listOf("virgin", "mary", "mother", "god", "incarnation"),
        "priest" to listOf("priest", "priests", "priesthood", "apostles", "ministry", "pastors", "father"),
        "priests" to listOf("priest", "priests", "priesthood", "apostles", "ministry", "pastors", "father")
    )

    fun meaningfulTerms(userQuestion: String): List<String> {
        val words = userQuestion
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(" ")
            .filter { it.length > 2 && it !in STOP_WORDS }
            .distinct()
            .take(8)

        return words
            .flatMap { word -> TOPIC_EXPANSIONS[word] ?: listOf(word) }
            .distinct()
            .take(8)
    }

    fun buildFtsQuery(userQuestion: String): String {
        val words = meaningfulTerms(userQuestion)

        if (words.isEmpty()) return ""

        // FTS4 OR query: returns paragraphs matching any term; use case reranks candidates.
        return words.joinToString(" OR ")
    }
}

class SearchCatechismUseCase @Inject constructor(
    private val catechismDao: CatechismDao
) {
    companion object {
        private const val TAG = "Retrieval"
        private const val CANDIDATE_MULTIPLIER = 4
        private const val MIN_CANDIDATES = 50
    }

    suspend operator fun invoke(question: String, maxResults: Int = 5): List<CatechismParagraph> {
        val ftsQuery = QueryPreprocessor.buildFtsQuery(question)
        val queryTerms = QueryPreprocessor.meaningfulTerms(question)
        Log.d(TAG, "Original query: '$question', Expanded FTS query: '$ftsQuery'")

        if (ftsQuery.isBlank()) {
            Log.d(TAG, "Query empty after preprocessing, returning empty list")
            return emptyList()
        }

        val candidateLimit = maxOf(maxResults * CANDIDATE_MULTIPLIER, MIN_CANDIDATES)
        var results = catechismDao.searchFts(ftsQuery, candidateLimit)
        Log.d(TAG, "FTS result count: ${results.size}")

        if (results.isEmpty()) {
            Log.d(TAG, "FTS returned 0 results, attempting fallback LIKE search")
            val fallbackTerm = ftsQuery.split(" OR ").firstOrNull()?.trim() ?: ""
            if (fallbackTerm.isNotEmpty()) {
                results = catechismDao.searchLike(fallbackTerm, candidateLimit)
                Log.d(TAG, "Fallback count: ${results.size}")
            }
        }

        return rankResults(results, queryTerms)
            .take(maxResults)
            .map { entity ->
                CatechismParagraph(
                    id = entity.id,
                    text = entity.text
                )
            }
    }

    private fun rankResults(
        results: List<CatechismEntity>,
        queryTerms: List<String>
    ): List<CatechismEntity> {
        return results
            .mapIndexed { index, entity ->
                val text = entity.text.lowercase()
                val score: Int = queryTerms.fold(0) { total, term ->
                    total + if (text.contains(term)) {
                        when (term) {
                            "trinity" -> 25
                            "father", "son", "holy", "spirit" -> 4
                            else -> 10
                        }
                    } else {
                        0
                    }
                }
                Triple(entity, score, index)
            }
            .sortedWith(
                compareByDescending<Triple<CatechismEntity, Int, Int>> { it.second }
                    .thenBy { it.third }
            )
            .map { it.first }
    }
}
