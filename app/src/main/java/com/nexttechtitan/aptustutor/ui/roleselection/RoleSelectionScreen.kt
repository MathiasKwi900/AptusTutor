package com.nexttechtitan.aptustutor.ui.roleselection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RoleSelectionScreen(
    onOnboardingComplete: (String) -> Unit,
    viewModel: RoleSelectionViewModel = hiltViewModel()
) {
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(0.5f))
            Header()
            Spacer(Modifier.weight(1f))
            RoleCards(
                selectedRole = selectedRole,
                onRoleClicked = { role -> selectedRole = role }
            )
            Spacer(Modifier.weight(1f))
            GetStartedButton(
                isVisible = selectedRole != null,
                onClick = { showDetailsDialog = true }
            )
            Spacer(Modifier.weight(0.2f))
        }
    }

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
private fun Header() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Rounded.Api,
            contentDescription = "AptusTutor Logo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Welcome to AptusTutor",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Intelligent Learning, Anywhere.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoleCards(selectedRole: String?, onRoleClicked: (String) -> Unit) {
    Text(
        "Please select your role to get started",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 24.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        RoleCard(
            title = "I am a Tutor",
            icon = Icons.Rounded.School,
            isSelected = selectedRole == "TUTOR",
            onClick = { onRoleClicked("TUTOR") }
        )
        RoleCard(
            title = "I am a Student",
            icon = Icons.Rounded.Face,
            isSelected = selectedRole == "STUDENT",
            onClick = { onRoleClicked("STUDENT") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val targetContainerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val targetContentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val targetElevation = if (isSelected) 8.dp else 2.dp

    val animatedContainerColor by animateColorAsState(targetValue = targetContainerColor, animationSpec = tween(300), label = "containerColor")
    val animatedContentColor by animateColorAsState(targetValue = targetContentColor, animationSpec = tween(300), label = "contentColor")

    Card(
        onClick = onClick,
        modifier = Modifier.size(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainerColor,
            contentColor = animatedContentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = targetElevation)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GetStartedButton(isVisible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it / 2 })
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(52.dp)
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium)
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
    val isTutor = role == "TUTOR"
    val labelText = if (isTutor) "Full Name (e.g., Mr. Alex)" else "Full Name"
    val icon = if (isTutor) Icons.Rounded.School else Icons.Rounded.Face

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Let's Get You Set Up") },
        text = {
            Column {
                Text(
                    "Please enter your name. This will be visible to others in your class.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(labelText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotBlank()
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