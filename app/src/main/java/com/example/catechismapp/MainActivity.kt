package com.example.catechismapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.catechismapp.data.preferences.UserPreferences
import com.example.catechismapp.ui.chat.ChatScreen
import com.example.catechismapp.ui.search.SearchScreen
import com.example.catechismapp.ui.settings.SettingsScreen
import com.example.catechismapp.ui.splash.SeedingState
import com.example.catechismapp.ui.splash.SplashScreen
import com.example.catechismapp.ui.splash.SplashViewModel
import com.example.catechismapp.ui.theme.CatechismAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Inject early so setKeepOnScreenCondition can query the seeding state
    private val splashViewModel: SplashViewModel by viewModels()
    @Inject lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the native OS splash on-screen while seeding is still in progress.
        // Once the state leaves Checking/Seeding (success or error), the OS splash
        // is dismissed and Compose's SplashScreen composable takes over.
        installSplashScreen().setKeepOnScreenCondition {
            val state = splashViewModel.seedingState.value
            state is SeedingState.Checking || state is SeedingState.Seeding
        }
        super.onCreate(savedInstanceState)
        setContent {
            val fontScalePercent by userPreferences.fontScalePercentFlow.collectAsState(initial = 100)
            val fontScale = fontScalePercent / 100f
            CatechismAppTheme(fontScale = fontScale) {
                MainAppNavigation()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Chat : Screen("chat", "Chat", Icons.AutoMirrored.Filled.Send)
    object Search : Screen("search", "Study", Icons.Default.Search)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDarkTheme = isSystemInDarkTheme()
    val selectedIndicatorColor = if (isDarkTheme) {
        Color(0xFF3A3A3A)
    } else {
        Color(0xFFD9D9D9)
    }

    // Hide bottom navigation bar on the splash screen
    val showBottomBar = currentRoute != "splash"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(Screen.Chat, Screen.Search, Screen.Settings)
                    items.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = screen.icon, 
                                    contentDescription = screen.title 
                                ) 
                            },
                            label = { 
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = selectedIndicatorColor,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable("splash") {
                SplashScreen(
                    onSeedingComplete = {
                        navController.navigate("chat") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }
            composable("chat") {
                ChatScreen(
                    onNavigateToSettings = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("search") {
                SearchScreen()
            }
            composable("settings") {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
