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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexttechtitan.aptustutor.ui.main.MainViewModel
import com.nexttechtitan.aptustutor.ui.roleselection.RoleSelectionScreen
import com.nexttechtitan.aptustutor.ui.student.AssessmentScreen
import com.nexttechtitan.aptustutor.ui.student.StudentDashboardScreen
import com.nexttechtitan.aptustutor.ui.student.StudentDashboardViewModel
import com.nexttechtitan.aptustutor.ui.tutor.SubmissionDetailsScreen
import com.nexttechtitan.aptustutor.ui.tutor.TutorDashboardScreen

// Simple enum for screen routes
enum class AptusTutorScreen {
    Splash,
    RoleSelection,
    TutorDashboard,
    StudentDashboard,
    AssessmentScreen,
    SubmissionDetailsScreen
}

@Composable
fun AptusTutorApp(mainViewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AptusTutorScreen.Splash.name
    ) {
        composable(AptusTutorScreen.Splash.name) {
            val startDestination by mainViewModel.startDestination.collectAsStateWithLifecycle()
            LaunchedEffect(startDestination) {
                if (startDestination != null) {
                    navController.navigate(startDestination!!) {
                        popUpTo(AptusTutorScreen.Splash.name) { inclusive = true }
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
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
            TutorDashboardScreen(
                onNavigateToSubmission = { submissionId ->
                    navController.navigate("${AptusTutorScreen.SubmissionDetailsScreen.name}/$submissionId")
                },
                navController = navController
            )
        }
        composable(
            route = "${AptusTutorScreen.SubmissionDetailsScreen.name}/{submissionId}",
            arguments = listOf(navArgument("submissionId") { type = NavType.StringType })
        ) {
            SubmissionDetailsScreen(onNavigateBack = { navController.popBackStack() })
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
            StudentDashboardScreen(
                viewModel = studentViewModel,
                navController = navController
            )
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
}