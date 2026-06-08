package com.example.catechismapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryPreprocessorTest {

    @Test
    fun testBuildFtsQuery_stripsStopWords_andBuildsOrQuery() {
        // "What does the Church teach about Purgatory?"
        // stop words: what, does, the, church, teach, about
        // remaining: purgatory
        val query = "What does the Church teach about Purgatory?"
        val result = QueryPreprocessor.buildFtsQuery(query)
        assertEquals("purgatory", result)
    }

    @Test
    fun testBuildFtsQuery_multipleTerms_buildsOrQuery() {
        val query = "baptism eucharist sacrament"
        val result = QueryPreprocessor.buildFtsQuery(query)
        assertEquals("baptism OR eucharist OR sacrament", result)
    }

    @Test
    fun testBuildFtsQuery_blankInput_returnsEmptyString() {
        assertEquals("", QueryPreprocessor.buildFtsQuery(""))
        assertEquals("", QueryPreprocessor.buildFtsQuery("   "))
        assertEquals("", QueryPreprocessor.buildFtsQuery("what is the")) // only stop words
    }

    @Test
    fun testBuildFtsQuery_conversationalTrinityQuestion_focusesOnDoctrine() {
        val query = "Why do Catholics believe in the Trinity?"
        val result = QueryPreprocessor.buildFtsQuery(query)
        assertEquals("trinity OR father OR son OR holy OR spirit", result)
    }

    @Test
    fun testBuildFtsQuery_maryMotherOfGodQuestion_expandsToCatechismTerms() {
        val query = "why is mother mary called the virgin mother of god?"
        val result = QueryPreprocessor.buildFtsQuery(query)
        assertEquals("mother OR mary OR god OR virgin OR jesus OR lord OR incarnation", result)
    }

    @Test
    fun testBuildFtsQuery_priestsCalledFatherQuestion_expandsToPriesthoodTerms() {
        val query = "why are priests called father?"
        val result = QueryPreprocessor.buildFtsQuery(query)
        assertEquals("priest OR priests OR priesthood OR apostles OR ministry OR pastors OR father", result)
    }
}
