package com.nexttechtitan.aptustutor.ui.roleselection

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RoleSelectionScreen(
    onOnboardingComplete: (String) -> Unit,
    viewModel: RoleSelectionViewModel = hiltViewModel()
) {
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    RoleSelectionContent(
        selectedRole = selectedRole,
        onRoleClicked = { role -> selectedRole = role },
        onGetStartedClicked = {
            showDetailsDialog = true
        }
    )

    if (showDetailsDialog && selectedRole != null) {
        UserDetailsDialog(
            role = selectedRole!!,
            onDismiss = { showDetailsDialog = false },
            onConfirm = { name ->
                viewModel.saveRoleAndDetails(selectedRole!!, name) {
                    showDetailsDialog = false
                    onOnboardingComplete(selectedRole!!)
                }
            }
        )
    }
}

@Composable
private fun RoleSelectionContent(
    selectedRole: String?,
    onRoleClicked: (String) -> Unit,
    onGetStartedClicked: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to AptusTutor", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Intelligent Learning, Anywhere.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(48.dp))
        Text("Please select your role to get started", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RoleCard(title = "I am a Tutor", isSelected = selectedRole == "TUTOR", onClick = { onRoleClicked("TUTOR") })
            RoleCard(title = "I am a Student", isSelected = selectedRole == "STUDENT", onClick = { onRoleClicked("STUDENT") })
        }
        Spacer(Modifier.height(48.dp))

        Button(onClick = onGetStartedClicked, enabled = selectedRole != null, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Get Started")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleCard(title: String, isSelected: Boolean, onClick: () -> Unit) {
    // This Composable is perfect as is.
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(150.dp)
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.large
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun UserDetailsDialog(
    role: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val labelText = when (role) {
        "TUTOR" -> "Your Full Name (e.g., Mr. Alex)"
        else -> "Your Full Name"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Details") },
        text = {
            Column {
                Text("Please enter your full name to be identified as a $role.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(labelText) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}