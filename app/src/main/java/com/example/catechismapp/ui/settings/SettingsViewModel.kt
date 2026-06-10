package com.example.catechismapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catechismapp.data.local.ConversationDao
import com.example.catechismapp.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val conversationDao: ConversationDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val fontScalePercent: StateFlow<Int> = userPreferences.fontScalePercentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    fun clearConversationHistory() {
        viewModelScope.launch {
            conversationDao.clearAll()
        }
    }

    fun updateFontScalePercent(percent: Int) {
        viewModelScope.launch {
            userPreferences.setFontScalePercent(percent)
        }
    }
}
