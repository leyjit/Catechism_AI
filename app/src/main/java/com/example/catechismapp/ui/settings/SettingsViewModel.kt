package com.example.catechismapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catechismapp.data.local.ConversationDao
import com.example.catechismapp.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val conversationDao: ConversationDao
) : ViewModel() {

    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    init {
        loadApiKey()
    }

    private fun loadApiKey() {
        viewModelScope.launch {
            _apiKey.value = userPreferences.getApiKey()
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            userPreferences.setApiKey(key.trim())
            _apiKey.value = key.trim()
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            userPreferences.setApiKey("")
            _apiKey.value = null
        }
    }

    fun clearConversationHistory() {
        viewModelScope.launch {
            conversationDao.clearAll()
        }
    }
}
