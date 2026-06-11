package com.example.catechismapp.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catechismapp.data.local.BibleVerseDao
import com.example.catechismapp.data.local.CatechismDao
import com.example.catechismapp.data.local.FavoriteQaPairDao
import com.example.catechismapp.data.local.entity.FavoriteQaPairEntity
import com.example.catechismapp.data.scripture.ScriptureReferenceParser
import com.example.catechismapp.domain.model.BibleVerse
import com.example.catechismapp.domain.model.CatechismParagraph
import com.example.catechismapp.domain.model.ChatMessage
import com.example.catechismapp.domain.model.QaPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteQaPairDao: FavoriteQaPairDao,
    private val catechismDao: CatechismDao,
    private val bibleVerseDao: BibleVerseDao
) : ViewModel() {

    val favorites: StateFlow<List<QaPair>> = favoriteQaPairDao.getAll()
        .map { entities -> hydrateFavorites(entities) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun deleteFavorites(pairs: List<QaPair>) = withContext(Dispatchers.IO) {
        favoriteQaPairDao.deleteByIds(pairs.map { it.id })
    }

    private suspend fun hydrateFavorites(entities: List<FavoriteQaPairEntity>): List<QaPair> {
        return entities.map { entity ->
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

            val refs = entity.verseRefs.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val verses = refs.mapNotNull { ref ->
                val parsed = ScriptureReferenceParser.parse(ref) ?: return@mapNotNull null
                val verseEntity = bibleVerseDao.getVerse(parsed.book, parsed.chapter, parsed.verse)
                    ?: return@mapNotNull null
                BibleVerse(
                    reference = ref,
                    book = verseEntity.book,
                    chapter = verseEntity.chapter,
                    verse = verseEntity.verse,
                    text = verseEntity.text
                )
            }

            QaPair(
                id = entity.id,
                question = ChatMessage(
                    id = entity.id,
                    role = "user",
                    content = entity.questionContent,
                    timestamp = entity.questionTimestamp
                ),
                answer = ChatMessage(
                    id = entity.id,
                    role = "assistant",
                    content = entity.answerContent,
                    timestamp = entity.answerTimestamp,
                    paragraphs = paragraphs,
                    verses = verses
                )
            )
        }
    }
}
