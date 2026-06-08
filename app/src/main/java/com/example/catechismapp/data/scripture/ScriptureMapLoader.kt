package com.example.catechismapp.data.scripture

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptureMapLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Loaded once into memory at startup — the map is ~200KB, entirely acceptable
    private var scriptureMap: Map<Int, List<String>>? = null

    fun load(): Map<Int, List<String>> {
        if (scriptureMap != null) return scriptureMap!!

        val json = context.assets.open("ccc_scripture_map.json")
            .bufferedReader().use { it.readText() }

        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val raw: Map<String, List<String>> = Gson().fromJson(json, type)

        scriptureMap = raw.mapKeys { it.key.toInt() }
        return scriptureMap!!
    }

    fun getReferencesForParagraph(paragraphId: Int): List<String> {
        if (scriptureMap == null) load()
        return scriptureMap?.get(paragraphId) ?: emptyList()
    }

    fun getReferencesForParagraphs(paragraphIds: List<Int>): List<String> {
        return paragraphIds.flatMap { getReferencesForParagraph(it) }.distinct()
    }
}
