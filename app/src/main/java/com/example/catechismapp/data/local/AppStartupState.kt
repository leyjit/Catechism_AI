package com.example.catechismapp.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that carries startup-sequence errors from [CatechismApp] to [SplashViewModel].
 *
 * Because the seeding coroutine is launched in [Application.onCreate] — before any Compose
 * ViewModel is created — exceptions cannot be delivered via ViewModel state directly.
 * Instead, [CatechismApp] sets an error message here, and [SplashViewModel] collects it
 * so the user sees a real error screen instead of an infinite loading indicator.
 *
 * Using [MutableStateFlow] (not SharedFlow) guarantees that any new collector — regardless
 * of when it subscribes — always receives the current value. A SharedFlow with replay=0
 * would silently drop errors emitted before SplashViewModel subscribes.
 */
@Singleton
class AppStartupState @Inject constructor() {

    private val _seedingError = MutableStateFlow<String?>(null)

    /**
     * Non-null when the seeding coroutine in [CatechismApp] terminated with an exception.
     * Always replays the latest value to new collectors (StateFlow guarantee).
     */
    val seedingError: StateFlow<String?> = _seedingError.asStateFlow()

    /** Called by [CatechismApp] when seeding fails. Safe to call from any thread. */
    fun notifySeedingError(message: String) {
        _seedingError.value = message
    }
}
