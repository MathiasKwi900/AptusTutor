package com.nexttechtitan.aptustutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nexttechtitan.aptustutor.ui.AptusTutorApp
import com.nexttechtitan.aptustutor.ui.main.MainViewModel
import com.nexttechtitan.aptustutor.ui.theme.AptusTutorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The main and only Activity in the app, following a single-activity architecture.
 * It hosts the Jetpack Compose content and initializes the core app structure.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configures the native splash screen. It will remain on screen
        // until the MainViewModel determines the correct start destination.
        // This creates a seamless transition from app launch to the first screen.
        installSplashScreen().setKeepOnScreenCondition {
            mainViewModel.startDestination.value == null
        }

        enableEdgeToEdge()
        setContent {
            AptusTutorTheme {
                AptusTutorApp(mainViewModel = mainViewModel)
            }
        }
    }
}