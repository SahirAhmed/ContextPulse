package com.contextpulse.phase0.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.contextpulse.phase0.repository.AppGroupRepository
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import java.util.Locale

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null
)

val DND_MODES = listOf(
    Triple("Priority Only", "Priority", "Allows priority callers & alarms"),
    Triple("Total Silence", "Total Silence", "Blocks all sounds & alerts"),
    Triple("Alarms Only", "Alarms Only", "Only alarms can ring")
)

fun loadInstalledApps(context: Context): List<AppInfo> {
    return try {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        installed
            .filter { app ->
                // Filter out essential system packages, show launchable or user apps
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val hasLaunchIntent = pm.getLaunchIntentForPackage(app.packageName) != null
                (!isSystem || isUpdatedSystem || hasLaunchIntent) && app.packageName != context.packageName
            }
            .map { app ->
                AppInfo(
                    name = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    icon = try { app.loadIcon(pm) } catch (e: Exception) { null }
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    } catch (e: Exception) {
        emptyList()
    }
}

fun resolveAppDisplayName(context: Context, packageName: String, installedApps: List<AppInfo>? = null): String {
    if (installedApps != null) {
        val found = installedApps.find { it.packageName == packageName }
        if (found != null) return found.name
    }
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (e: Exception) {
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}


@Composable
fun AppSelectorDialog(
    context: Context,
    initialSelected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()

    var selectedPackages by remember { mutableStateOf(initialSelected.toSet()) }
    var searchQuery by remember { mutableStateOf("") }

    val appGroupRepo = remember { AppGroupRepository.getInstance(context) }
    val appGroups by appGroupRepo.appGroups.collectAsState()

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.border),
            shadowElevation = 20.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Block Apps & Groups",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "${selectedPackages.size} Apps Selected To Block",
                            fontSize = 12.5.sp,
                            color = theme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // App Groups Quick-Select Chips (User Request: can select groups instead of individual apps)
                if (appGroups.isNotEmpty()) {
                    Text(
                        text = "App Groups",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        appGroups.forEach { group ->
                            val allInGroupSelected = group.packageNames.isNotEmpty() && group.packageNames.all { selectedPackages.contains(it) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (allInGroupSelected) theme.primary else theme.background)
                                    .border(1.dp, if (allInGroupSelected) theme.primary else theme.border, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedPackages = if (allInGroupSelected) {
                                            selectedPackages - group.packageNames.toSet()
                                        } else {
                                            selectedPackages + group.packageNames.toSet()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Category,
                                        contentDescription = null,
                                        tint = if (allInGroupSelected) theme.onPrimary else theme.textSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${group.name} (${group.packageNames.size})",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (allInGroupSelected) theme.onPrimary else theme.textPrimary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Installed Apps List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) theme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    selectedPackages = if (isSelected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.toBitmap(width = 64, height = 64).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.primary.copy(alpha = 0.15f))
                                    )
                                }

                                Column {
                                    Text(
                                        text = app.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = theme.textPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        color = theme.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }

                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedPackages = if (checked) {
                                        selectedPackages + app.packageName
                                    } else {
                                        selectedPackages - app.packageName
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = theme.primary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selectedPackages = emptySet() }) {
                        Text("Clear All", color = theme.textSecondary)
                    }

                    Button(
                        onClick = {
                            onConfirm(selectedPackages.toList())
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply (${selectedPackages.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
