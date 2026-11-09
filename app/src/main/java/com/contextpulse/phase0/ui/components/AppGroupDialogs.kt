package com.contextpulse.phase0.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.contextpulse.phase0.model.AppGroup
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import java.util.Locale

@Composable
fun AppGroupEditorDialog(
    initialGroup: AppGroup,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (AppGroup) -> Unit
) {
    CreateOrEditAppGroupDialog(
        groupToEdit = if (isNew) null else initialGroup,
        onDismiss = onDismiss,
        onSave = { name, packages ->
            onSave(initialGroup.copy(name = name, packageNames = packages))
        }
    )
}

@Composable
fun AppGroupSelectorDialog(
    groups: List<AppGroup>,
    initialSelectedGroupIds: List<String>,
    onDismiss: () -> Unit,
    onSaveSelection: (List<String>) -> Unit
) {
    val theme = LocalContextPulseTheme.current
    var selectedIds by remember { mutableStateOf(initialSelectedGroupIds.toSet()) }

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
                        .background(theme.tertiary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderCopy,
                        contentDescription = null,
                        tint = theme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Select Blocked App Groups",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${selectedIds.size} group(s) selected",
                        color = theme.tertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (groups.isEmpty()) {
                    Text(
                        text = "No Blocked App Groups created yet. Create groups inside Settings → Blocked App Groups.",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    groups.forEach { group ->
                        val isChecked = selectedIds.contains(group.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (isChecked) {
                                        selectedIds - group.id
                                    } else {
                                        selectedIds + group.id
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) theme.primary.copy(alpha = 0.12f) else theme.surfaceVariant
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isChecked) theme.primary else theme.border
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.name,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = "${group.packageNames.size} apps in group",
                                        color = theme.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + group.id
                                        } else {
                                            selectedIds - group.id
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSelection(selectedIds.toList()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                )
            ) {
                Text("Confirm (${selectedIds.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

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
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
                        color = theme.tertiary,
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
                                val appIconBitmap = remember(app.packageName) {
                                    try {
                                        app.icon?.toBitmap(width = 64, height = 64)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                if (appIconBitmap != null) {
                                    Image(
                                        bitmap = appIconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.name.take(1).uppercase(Locale.ROOT),
                                            color = theme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = theme.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
