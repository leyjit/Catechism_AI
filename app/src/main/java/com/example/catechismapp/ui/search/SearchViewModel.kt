package com.example.catechismapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catechismapp.domain.model.CatechismParagraph
import com.example.catechismapp.domain.usecase.SearchCatechismUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCatechism: SearchCatechismUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CatechismParagraph>>(emptyList())
    val searchResults: StateFlow<List<CatechismParagraph>> = _searchResults.asStateFlow()

    init {
        // Implement reactive search with debounce and distinct check to prevent excessive querying
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    private suspend fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            _error.value = null
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        _error.value = null

        try {
            // Retrieve up to 30 paragraphs for a rich direct study experience
            val results = searchCatechism(trimmed, maxResults = 30)
            _searchResults.value = results
            if (results.isEmpty()) {
                _error.value = "No paragraphs found. Try different keywords."
            }
        } catch (e: Exception) {
            _error.value = "Search error: ${e.localizedMessage}"
            _searchResults.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }
}
