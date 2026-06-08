package com.example.catechismapp.data.scripture

/**
 * Parsed representation of a scripture reference string like "1 Corinthians 9:22"
 */
data class ParsedReference(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val original: String
)

object ScriptureReferenceParser {

    /**
     * Books with numeric prefixes or multi-word names need special handling.
     * Must be checked before falling back to the single-word-book parsing path.
     *
     * Verified against actual book names in bible.json and ccc_scripture_map.json:
     * - No "Psalm" vs "Psalms" mismatch — both files use "Psalms" ✅
     * - No "Song of Solomon" — both files use "Song of Songs" ✅
     * - No "Revelation" vs "Revelations" — both use "Revelation" ✅
     * - "Wisdom" (single-word) is NOT in this set — handled by fallback path ✅
     * - "Sirach" (single-word) is NOT in this set ✅
     */
    private val MULTI_WORD_BOOKS = setOf(
        "1 Samuel", "2 Samuel",
        "1 Kings", "2 Kings",
        "1 Chronicles", "2 Chronicles",
        "1 Maccabees", "2 Maccabees",
        "1 Corinthians", "2 Corinthians",
        "1 Thessalonians", "2 Thessalonians",
        "1 Timothy", "2 Timothy",
        "1 Peter", "2 Peter",
        "1 John", "2 John", "3 John",
        "Song of Songs",
        "Song of Solomon" // alias — not in actual data but kept for safety
    )

    fun parse(reference: String): ParsedReference? {
        // Try multi-word book names first (longest match wins)
        for (book in MULTI_WORD_BOOKS.sortedByDescending { it.length }) {
            if (reference.startsWith(book)) {
                val rest = reference.removePrefix(book).trim()
                val parts = rest.split(":")
                if (parts.size == 2) {
                    return ParsedReference(
                        book = book,
                        chapter = parts[0].trim().toIntOrNull() ?: return null,
                        verse = parts[1].trim().toIntOrNull() ?: return null,
                        original = reference
                    )
                }
            }
        }

        // Single-word book name (e.g. "Genesis 1:1", "Psalms 23:1")
        val spaceIdx = reference.lastIndexOf(' ')
        if (spaceIdx < 0) return null
        val book = reference.substring(0, spaceIdx).trim()
        val chapterVerse = reference.substring(spaceIdx + 1).trim()
        val parts = chapterVerse.split(":")
        if (parts.size != 2) return null

        return ParsedReference(
            book = book,
            chapter = parts[0].toIntOrNull() ?: return null,
            verse = parts[1].toIntOrNull() ?: return null,
            original = reference
        )
    }
}
