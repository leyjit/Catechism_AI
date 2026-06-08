package com.example.catechismapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BackendHarnessApiService {
    @POST("ask")
    suspend fun ask(@Body request: BackendAskRequest): Response<BackendAskResponse>
}

data class BackendAskRequest(
    val question: String
)

data class BackendAskResponse(
    val answer: String?,
    val ccc_sources: List<BackendCatechismSource> = emptyList(),
    val scripture_sources: List<BackendScriptureSource> = emptyList(),
    val model: String? = null,
    val error: String? = null
)

data class BackendCatechismSource(
    val id: Int,
    val citation: String? = null,
    val text: String
)

data class BackendScriptureSource(
    val reference: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)
