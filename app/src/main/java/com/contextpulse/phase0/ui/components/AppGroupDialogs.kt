package com.contextpulse.phase0.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.contextpulse.phase0.model.AppGroup
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import java.util.Locale

/**
 * Dialog for creating a new App Group or editing an existing one in Settings.
 */
@Composable
fun CreateOrEditAppGroupDialog(
    groupToEdit: AppGroup? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, packageNames: List<String>) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current

    var groupName by remember { mutableStateOf(groupToEdit?.name ?: "") }
    var selectedPackages by remember { mutableStateOf(groupToEdit?.packageNames?.toSet() ?: emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    val installedApps by remember {
        mutableStateOf(loadInstalledApps(context))
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Selected apps float to top in alphabetical order
    val displayedApps = remember(filteredApps, selectedPackages) {
        val (selected, unselected) = filteredApps.partition {
            selectedPackages.contains(it.packageName)
        }
        selected.sortedBy { it.name.lowercase(Locale.ROOT) } +
        unselected.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Category,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (groupToEdit == null) "Create App Group" else "Edit App Group",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${selectedPackages.size} apps included in group",
                        color = theme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Group Name Input
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name", color = theme.textSecondary) },
                    placeholder = { Text("e.g., Social Media, Gaming, Work", color = theme.textSecondary.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border
                    )
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps to include...", color = theme.textSecondary.copy(alpha = 0.6f)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = theme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = theme.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border
                    )
                )

                // App list
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayedApps, key = { it.packageName }) { app ->
                        val isChecked = selectedPackages.contains(app.packageName)
                        val appIcon = remember(app.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
                            } catch (e: Throwable) {
                                null
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedPackages = if (isChecked) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon,
                                        contentDescription = app.name,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.name.take(1).uppercase(Locale.ROOT),
                                            color = theme.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = app.name,
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }

                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedPackages = if (checked) {
                                        selectedPackages + app.packageName
                                    } else {
                                        selectedPackages - app.packageName
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = theme.primary,
                                    checkmarkColor = theme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isNotBlank() && selectedPackages.isNotEmpty()) {
                        onSave(groupName.trim(), selectedPackages.toList())
                    }
                },
                enabled = groupName.isNotBlank() && selectedPackages.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                )
            ) {
                Text(
                    text = if (groupToEdit == null) "Create Group (${selectedPackages.size})" else "Save Changes (${selectedPackages.size})",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}
