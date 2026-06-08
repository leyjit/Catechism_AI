package com.example.catechismapp

import android.app.Application
import android.util.Log
import com.example.catechismapp.data.local.AppStartupState
import com.example.catechismapp.data.local.DatabaseSeeder
import com.example.catechismapp.data.scripture.ScriptureMapLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CatechismApp : Application() {

    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var scriptureMapLoader: ScriptureMapLoader
    @Inject lateinit var appStartupState: AppStartupState

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Seed database on background thread — does nothing after first launch.
        // Any exception is forwarded to AppStartupState so SplashViewModel can
        // transition to a visible error state rather than staying stuck loading.
        applicationScope.launch {
            try {
                databaseSeeder.seedIfNeeded()
                scriptureMapLoader.load()
                Log.d("CatechismApp", "Startup sequence complete")
            } catch (e: Exception) {
                Log.e("CatechismApp", "Startup error: ${e.message}", e)
                val userMessage = "Failed to load Catechism library: ${e.localizedMessage ?: "Unknown error"}. Please reinstall the app."
                appStartupState.notifySeedingError(userMessage)
            }
        }
    }
}
