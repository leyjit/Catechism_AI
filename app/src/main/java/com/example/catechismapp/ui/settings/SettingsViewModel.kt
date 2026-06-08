package com.example.catechismapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catechismapp.data.local.ConversationDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val conversationDao: ConversationDao
) : ViewModel() {

    fun clearConversationHistory() {
        viewModelScope.launch {
            conversationDao.clearAll()
        }
    }
}
