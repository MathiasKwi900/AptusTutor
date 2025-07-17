package com.nexttechtitan.aptustutor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexttechtitan.aptustutor.ui.main.MainViewModel
import com.nexttechtitan.aptustutor.ui.roleselection.RoleSelectionScreen
import com.nexttechtitan.aptustutor.ui.student.AssessmentScreen
import com.nexttechtitan.aptustutor.ui.student.StudentDashboardScreen
import com.nexttechtitan.aptustutor.ui.student.StudentDashboardViewModel
import com.nexttechtitan.aptustutor.ui.tutor.TutorDashboardScreen

// Simple enum for screen routes
enum class AptusTutorScreen {
    RoleSelection,
    TutorDashboard,
    StudentDashboard,
    AssessmentScreen
}

@Composable
fun AptusTutorApp(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val startDestination by mainViewModel.startDestination.collectAsStateWithLifecycle()

    if (startDestination != null) {
        NavHost(
            navController = navController,
            startDestination = startDestination!!
        ) {
            composable(AptusTutorScreen.RoleSelection.name) {
                RoleSelectionScreen(
                    onOnboardingComplete = { role ->
                        val destination = when (role) {
                            "TUTOR" -> AptusTutorScreen.TutorDashboard.name
                            else -> AptusTutorScreen.StudentDashboard.name
                        }
                        navController.navigate(destination) {
                            popUpTo(AptusTutorScreen.RoleSelection.name) { inclusive = true }
                        }
                    }
                )
            }
            composable(AptusTutorScreen.TutorDashboard.name) {
                TutorDashboardScreen()
            }
            composable(AptusTutorScreen.StudentDashboard.name) {
                val studentViewModel: StudentDashboardViewModel = hiltViewModel()
                val uiState by studentViewModel.uiState.collectAsStateWithLifecycle()

                // This effect will navigate to the assessment screen when an assessment starts
                LaunchedEffect(uiState.activeAssessment) {
                    if (uiState.activeAssessment != null) {
                        navController.navigate(AptusTutorScreen.AssessmentScreen.name)
                    }
                }
                StudentDashboardScreen(viewModel = studentViewModel)
            }
            composable(AptusTutorScreen.AssessmentScreen.name) {
                val studentViewModel: StudentDashboardViewModel = hiltViewModel()
                AssessmentScreen(
                    viewModel = studentViewModel,
                    onNavigateBack = {
                        // Navigate back to the dashboard after submission
                        navController.popBackStack(AptusTutorScreen.StudentDashboard.name, inclusive = false)
                    }
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}