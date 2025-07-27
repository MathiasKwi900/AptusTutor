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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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