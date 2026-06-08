package com.example.catechismapp.domain.usecase

import com.example.catechismapp.data.local.BibleVerseDao
import com.example.catechismapp.data.scripture.ScriptureMapLoader
import com.example.catechismapp.data.scripture.ScriptureReferenceParser
import com.example.catechismapp.domain.model.BibleVerse
import javax.inject.Inject

class GetScriptureForParagraphsUseCase @Inject constructor(
    private val bibleVerseDao: BibleVerseDao,
    private val scriptureMapLoader: ScriptureMapLoader
) {
    suspend operator fun invoke(paragraphIds: List<Int>): List<BibleVerse> {
        val references = scriptureMapLoader.getReferencesForParagraphs(paragraphIds)
        
        // Spec 22.5: deduplicate and cap at 8 verses
        val cappedReferences = references.take(8)

        return cappedReferences.mapNotNull { ref ->
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
