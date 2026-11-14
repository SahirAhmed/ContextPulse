package com.contextpulse.phase0.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.contextpulse.phase0.repository.AppGroupRepository
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import kotlinx.coroutines.launch
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

private var cachedInstalledApps: List<AppInfo>? = null
private val prewarmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

fun prewarmInstalledApps(context: Context) {
    if (cachedInstalledApps != null && cachedInstalledApps!!.isNotEmpty()) return
    prewarmScope.launch {
        loadInstalledApps(context.applicationContext)
    }
}

fun loadInstalledApps(context: Context, forceRefresh: Boolean = false): List<AppInfo> {
    if (!forceRefresh && cachedInstalledApps != null && cachedInstalledApps!!.isNotEmpty()) {
        return cachedInstalledApps!!
    }
    return try {
        val pm = context.packageManager
        val packageMap = linkedMapOf<String, AppInfo>()

        // 1. Query launcher intent activities - fast and only inspects apps with a launcher UI
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName) continue
            val label = ri.loadLabel(pm).toString()
            val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
            packageMap[pkg] = AppInfo(label, pkg, icon)
        }

        // 2. Also inspect installed user applications with 0 flags (no expensive GET_META_DATA)
        try {
            val installed = pm.getInstalledApplications(0)
            for (app in installed) {
                if (app.packageName == context.packageName) continue
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (!isSystem || isUpdatedSystem) {
                    if (!packageMap.containsKey(app.packageName)) {
                        val label = app.loadLabel(pm).toString()
                        val icon = try { app.loadIcon(pm) } catch (_: Exception) { null }
                        packageMap[app.packageName] = AppInfo(label, app.packageName, icon)
                    }
                }
            }
        } catch (_: Exception) {
            // Handled
        }

        val sorted = packageMap.values.sortedBy { it.name.lowercase(Locale.ROOT) }
        cachedInstalledApps = sorted
        sorted
    } catch (e: Exception) {
        cachedInstalledApps ?: emptyList()
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.85f)
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
                            text = "Select Apps & Groups to Block",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "${selectedPackages.size} Apps Selected to Block",
                            fontSize = 12.sp,
                            color = theme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // App Groups Quick-Select Carousel
                if (appGroups.isNotEmpty()) {
                    Text(
                        text = "QUICK SELECT BY GROUP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.accent,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(appGroups, key = { it.id }) { group ->
                            val allInGroupSelected = group.packageNames.isNotEmpty() &&
                                group.packageNames.all { selectedPackages.contains(it) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (allInGroupSelected) theme.primary else theme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        if (allInGroupSelected) theme.primary else theme.border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedPackages = if (allInGroupSelected) {
                                            selectedPackages - group.packageNames.toSet()
                                        } else {
                                            selectedPackages + group.packageNames.toSet()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Category,
                                        contentDescription = null,
                                        tint = if (allInGroupSelected) theme.onPrimary else theme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${group.name} (${group.packageNames.size})",
                                        fontSize = 12.sp,
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
                    placeholder = { Text("Search installed apps...", fontSize = 13.sp, color = theme.textSecondary.copy(alpha = 0.7f)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Installed Apps List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INDIVIDUAL APPS (${filteredApps.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.accent,
                        letterSpacing = 0.8.sp
                    )

                    if (filteredApps.isNotEmpty()) {
                        Text(
                            text = if (filteredApps.all { selectedPackages.contains(it.packageName) }) "Deselect All" else "Select All Filtered",
                            color = theme.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    val allFilteredSelected = filteredApps.all { selectedPackages.contains(it.packageName) }
                                    selectedPackages = if (allFilteredSelected) {
                                        selectedPackages - filteredApps.map { it.packageName }.toSet()
                                    } else {
                                        selectedPackages + filteredApps.map { it.packageName }.toSet()
                                    }
                                }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Installed Apps List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (installedApps.isEmpty()) "No installed launchable apps found on device." else "No apps match '$searchQuery'",
                                    color = theme.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackages.contains(app.packageName)
                            val appIconBitmap = remember(app.packageName) {
                                try {
                                    app.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.surface)
                                    .border(
                                        1.dp,
                                        if (isSelected) theme.primary.copy(alpha = 0.4f) else theme.border.copy(alpha = 0.5f),
                                        RoundedCornerShape(10.dp)
                                    )
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
                                    if (appIconBitmap != null) {
                                        Image(
                                            bitmap = appIconBitmap,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(theme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = app.name.take(1).uppercase(Locale.ROOT),
                                                color = theme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = theme.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 11.sp,
                                            color = theme.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = theme.primary,
                                        checkmarkColor = theme.onPrimary
                                    )
                                )
                            }
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
                        Text("Clear All", color = theme.textSecondary, fontWeight = FontWeight.SemiBold)
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

@Composable
fun SingleAppSelectorDialog(
    context: Context,
    selectedPackage: String,
    onDismiss: () -> Unit,
    onSelectApp: (packageName: String, appName: String) -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.85f)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select App to Auto-Open",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Tap an app to automatically launch when this mode activates",
                            fontSize = 11.5.sp,
                            color = theme.textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName == selectedPackage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) theme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.primary else theme.border.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectApp(app.packageName, app.name)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bmp = remember(app.packageName) {
                                try {
                                    app.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = theme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = theme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = theme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onSelectApp("", "")
                        onDismiss()
                    }) {
                        Text("No App (Clear)", color = theme.textSecondary, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = theme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Unified Restricted Apps Dialog.
 * Allows choosing apps to restrict, toggling between Outright Block and Daily Usage Allowance,
 * and configuring minute budgets directly per selected app.
 */
@Composable
fun RestrictedAppsManagerDialog(
    context: Context,
    initialRestrictedEntries: List<com.contextpulse.phase0.model.RestrictedAppEntry>,
    initialBlockedGroupIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (restrictedEntries: List<com.contextpulse.phase0.model.RestrictedAppEntry>, blockedGroupIds: List<String>) -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()

    // Map: packageName -> RestrictedAppEntry
    var entriesMap by remember {
        mutableStateOf(initialRestrictedEntries.associateBy { it.packageName }.toMutableMap())
    }
    var selectedGroupIds by remember {
        mutableStateOf(initialBlockedGroupIds.toSet())
    }

    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(0) } // 0: All Installed Apps, 1: Selected Only, 2: App Groups

    val appGroupRepo = remember { AppGroupRepository.getInstance(context) }
    val appGroups by appGroupRepo.appGroups.collectAsState()

    val installedApps by remember {
        mutableStateOf(loadInstalledApps(context))
    }

    val filteredApps = remember(searchQuery, installedApps, filterTab, entriesMap) {
        var list = installedApps
        if (filterTab == 0) {
            // "All Apps" tab shows only apps not yet selected
            list = list.filter { !entriesMap.containsKey(it.packageName) }
        } else if (filterTab == 1) {
            // "Selected" tab shows only selected apps
            list = list.filter { entriesMap.containsKey(it.packageName) }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.90f)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Restricted Apps",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        val blockedCount = entriesMap.values.count { it.isOutrightBlock }
                        val budgetCount = entriesMap.values.count { !it.isOutrightBlock }
                        Text(
                            text = "${entriesMap.size} selected ($blockedCount blocked, $budgetCount budgeted)",
                            fontSize = 11.5.sp,
                            color = theme.textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Tabs: All Apps / Selected / App Groups
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("All Apps", "Selected (${entriesMap.size})", "App Groups (${selectedGroupIds.size})")
                    tabs.forEachIndexed { index, label ->
                        val isSelected = filterTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) theme.primary else Color.Transparent)
                                .clickable { filterTab = index }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) theme.onPrimary else theme.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar (if not on app groups tab)
                if (filterTab != 2) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Body content based on tab
                if (filterTab == 2) {
                    // App Groups Selector Tab
                    if (appGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No App Groups created yet.\nCreate groups from the Apps tab to restrict batches of apps together.",
                                fontSize = 12.5.sp,
                                color = theme.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(appGroups, key = { it.id }) { group ->
                                val isSelected = selectedGroupIds.contains(group.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.surfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) theme.primary else theme.border.copy(alpha = 0.5f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            val shouldSelect = !isSelected
                                            val nextGroupIds = if (shouldSelect) selectedGroupIds + group.id else selectedGroupIds - group.id
                                            selectedGroupIds = nextGroupIds
                                            val nextMap = entriesMap.toMutableMap()
                                            if (shouldSelect) {
                                                group.packageNames.forEach { pkg ->
                                                    if (!nextMap.containsKey(pkg)) {
                                                        val appInfo = installedApps.find { it.packageName == pkg }
                                                        nextMap[pkg] = com.contextpulse.phase0.model.RestrictedAppEntry(
                                                            packageName = pkg,
                                                            appName = appInfo?.name ?: pkg,
                                                            maxMinutes = 0
                                                        )
                                                    }
                                                }
                                                filterTab = 1 // Show on selected tab
                                            } else {
                                                val otherGroupPkgs = appGroups
                                                    .filter { nextGroupIds.contains(it.id) }
                                                    .flatMap { it.packageNames }
                                                    .toSet()
                                                group.packageNames.forEach { pkg ->
                                                    if (!otherGroupPkgs.contains(pkg)) {
                                                        nextMap.remove(pkg)
                                                    }
                                                }
                                            }
                                            entriesMap = nextMap
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Category,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = group.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.textPrimary
                                            )
                                            Text(
                                                text = "${group.packageNames.size} apps • Blocked",
                                                fontSize = 11.5.sp,
                                                color = theme.textSecondary
                                            )
                                        }
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            val nextGroupIds = if (checked) selectedGroupIds + group.id else selectedGroupIds - group.id
                                            selectedGroupIds = nextGroupIds
                                            val nextMap = entriesMap.toMutableMap()
                                            if (checked) {
                                                group.packageNames.forEach { pkg ->
                                                    if (!nextMap.containsKey(pkg)) {
                                                        val appInfo = installedApps.find { it.packageName == pkg }
                                                        nextMap[pkg] = com.contextpulse.phase0.model.RestrictedAppEntry(
                                                            packageName = pkg,
                                                            appName = appInfo?.name ?: pkg,
                                                            maxMinutes = 0
                                                        )
                                                    }
                                                }
                                                filterTab = 1
                                            } else {
                                                val otherGroupPkgs = appGroups
                                                    .filter { nextGroupIds.contains(it.id) }
                                                    .flatMap { it.packageNames }
                                                    .toSet()
                                                group.packageNames.forEach { pkg ->
                                                    if (!otherGroupPkgs.contains(pkg)) {
                                                        nextMap.remove(pkg)
                                                    }
                                                }
                                            }
                                            entriesMap = nextMap
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
                } else {
                    // Apps List (All or Selected)
                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (filterTab == 1) "No apps restricted yet.\nSelect apps from 'All Apps' tab to restrict them." else "No matching apps found.",
                                fontSize = 12.5.sp,
                                color = theme.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val entry = entriesMap[app.packageName]
                                val isSelected = entry != null
                                val isBudget = entry != null && !entry.isOutrightBlock
                                val minutes = entry?.maxMinutes ?: 5

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) theme.primary.copy(alpha = 0.08f) else theme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(
                                            1.dp,
                                            if (isSelected) theme.primary.copy(alpha = 0.5f) else theme.border.copy(alpha = 0.5f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row 1: App Header + Checkbox
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val next = entriesMap.toMutableMap()
                                                if (isSelected) {
                                                    next.remove(app.packageName)
                                                } else {
                                                    // Default: Outright Block (0 min)
                                                    next[app.packageName] = com.contextpulse.phase0.model.RestrictedAppEntry(
                                                        packageName = app.packageName,
                                                        appName = app.name,
                                                        maxMinutes = 0
                                                    )
                                                }
                                                entriesMap = next
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            val bmp = remember(app.packageName) {
                                                try {
                                                    app.icon?.toBitmap(width = 64, height = 64)?.asImageBitmap()
                                                } catch (_: Exception) {
                                                    null
                                                }
                                            }
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(theme.primary.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = app.name.take(1).uppercase(Locale.ROOT),
                                                        color = theme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.name,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = theme.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (!isSelected) {
                                                        "Not restricted"
                                                    } else if (entry!!.isOutrightBlock) {
                                                        "🚫 Blocked (Immediate)"
                                                    } else {
                                                        "⏳ Budgeted: ${entry.maxMinutes} mins daily"
                                                    },
                                                    fontSize = 11.sp,
                                                    color = if (!isSelected) theme.textSecondary else if (entry!!.isOutrightBlock) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        }

                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                val next = entriesMap.toMutableMap()
                                                if (checked) {
                                                    next[app.packageName] = com.contextpulse.phase0.model.RestrictedAppEntry(
                                                        packageName = app.packageName,
                                                        appName = app.name,
                                                        maxMinutes = 0
                                                    )
                                                } else {
                                                    next.remove(app.packageName)
                                                }
                                                entriesMap = next
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = theme.primary,
                                                checkmarkColor = theme.onPrimary
                                            )
                                        )
                                    }

                                    // Row 2: If selected, inline toggle between Block vs Allowance
                                    if (isSelected && entry != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(theme.surface)
                                                .border(1.dp, theme.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Toggle 1: Block
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(if (entry.isOutrightBlock) Color(0x22EF4444) else Color.Transparent)
                                                    .border(
                                                        1.dp,
                                                        if (entry.isOutrightBlock) Color(0xFFEF4444) else Color.Transparent,
                                                        RoundedCornerShape(7.dp)
                                                    )
                                                    .clickable {
                                                        val next = entriesMap.toMutableMap()
                                                        next[app.packageName] = entry.copy(maxMinutes = 0)
                                                        entriesMap = next
                                                    }
                                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "🚫 Block",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (entry.isOutrightBlock) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (entry.isOutrightBlock) Color(0xFFEF4444) else theme.textSecondary,
                                                    maxLines = 1
                                                )
                                            }

                                            // Toggle 2: Budgeted
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(if (!entry.isOutrightBlock) Color(0x22F59E0B) else Color.Transparent)
                                                    .border(
                                                        1.dp,
                                                        if (!entry.isOutrightBlock) Color(0xFFF59E0B) else Color.Transparent,
                                                        RoundedCornerShape(7.dp)
                                                    )
                                                    .clickable {
                                                        val next = entriesMap.toMutableMap()
                                                        val defaultMins = if (entry.maxMinutes > 0) entry.maxMinutes else 5
                                                        next[app.packageName] = entry.copy(maxMinutes = defaultMins)
                                                        entriesMap = next
                                                    }
                                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "⏳ Budgeted",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (!entry.isOutrightBlock) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (!entry.isOutrightBlock) Color(0xFFF59E0B) else theme.textSecondary,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        // Row 3: If Budgeted is active, slider from 0 to 60 minutes with 5 min increments
                                        if (!entry.isOutrightBlock) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Daily Limit",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = theme.textSecondary
                                                    )
                                                    Text(
                                                        text = if (entry.maxMinutes == 0) "0 min (Immediate Block)" else "${entry.maxMinutes} mins",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (entry.maxMinutes == 0) Color(0xFFEF4444) else Color(0xFFF59E0B)
                                                    )
                                                }

                                                Slider(
                                                    value = entry.maxMinutes.toFloat(),
                                                    onValueChange = { rawVal ->
                                                        val stepMins = (Math.round(rawVal / 5f) * 5).coerceIn(0, 60)
                                                        val next = entriesMap.toMutableMap()
                                                        next[app.packageName] = entry.copy(maxMinutes = stepMins)
                                                        entriesMap = next
                                                    },
                                                    valueRange = 0f..60f,
                                                    steps = 11,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color(0xFFF59E0B),
                                                        activeTrackColor = Color(0xFFF59E0B),
                                                        inactiveTrackColor = theme.border
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        entriesMap = mutableMapOf()
                        selectedGroupIds = emptySet()
                    }) {
                        Text("Clear All", color = theme.textSecondary, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            onConfirm(entriesMap.values.toList(), selectedGroupIds.toList())
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply (${entriesMap.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

