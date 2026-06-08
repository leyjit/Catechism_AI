package com.example.catechismapp.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catechismapp.data.local.AppStartupState
import com.example.catechismapp.data.local.CatechismDao
import com.example.catechismapp.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SeedingState {
    object Checking : SeedingState
    object Seeding : SeedingState
    object Completed : SeedingState
    data class Error(val message: String) : SeedingState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val catechismDao: CatechismDao,
    private val appStartupState: AppStartupState
) : ViewModel() {

    private val _seedingState = MutableStateFlow<SeedingState>(SeedingState.Checking)
    val seedingState: StateFlow<SeedingState> = _seedingState.asStateFlow()

    init {
        collectSeedingErrors()
        monitorSeeding()
    }

    /**
     * Collects errors set by [CatechismApp] when the seeding coroutine throws.
     * Because [AppStartupState.seedingError] is a [StateFlow], the current value is
     * always replayed to this collector even if the error was set before this ViewModel
     * was created — the startup race is closed.
     *
     * We only transition to Error if we are not yet Completed, so a stale null→null
     * state change does not override a successful seeding.
     */
    private fun collectSeedingErrors() {
        viewModelScope.launch {
            appStartupState.seedingError.collect { errorMessage ->
                if (errorMessage != null && _seedingState.value !is SeedingState.Completed) {
                    _seedingState.value = SeedingState.Error(errorMessage)
                }
            }
        }
    }

    /**
     * Monitors the DataStore-backed seeded flag. Once the flag is true, performs a
     * database integrity check before navigating. If the flag never becomes true
     * (because seeding threw), [collectSeedingErrors] handles that transition.
     */
    private fun monitorSeeding() {
        viewModelScope.launch {
            userPreferences.isDatabaseSeededFlow.collectLatest { seeded ->
                if (seeded) {
                    verifyDatabase()
                } else {
                    // Only update to Seeding if we haven't already errored
                    if (_seedingState.value !is SeedingState.Error) {
                        _seedingState.value = SeedingState.Seeding
                    }
                }
            }
        }
    }

    private suspend fun verifyDatabase() {
        try {
            val count = catechismDao.count()
            val ftsCount = catechismDao.ftsCount()

            if (count > 0 && ftsCount > 0) {
                _seedingState.value = SeedingState.Completed
            } else {
                _seedingState.value = SeedingState.Error(
                    "Error loading Catechism library. No data found. Please reinstall the app."
                )
            }
        } catch (e: Exception) {
            _seedingState.value = SeedingState.Error(
                "Failed to verify database: ${e.localizedMessage}"
            )
        }
    }
}
