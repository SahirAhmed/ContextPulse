package com.contextpulse.phase0.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.model.AppGroup
import com.contextpulse.phase0.repository.AppGroupRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.ui.components.AppLockDialog
import com.contextpulse.phase0.ui.components.ContextPulseLogo
import com.contextpulse.phase0.ui.components.CreateOrEditAppGroupDialog
import com.contextpulse.phase0.ui.components.contextPulseLazyScrollbar
import com.contextpulse.phase0.ui.components.loadInstalledApps
import com.contextpulse.phase0.ui.components.resolveAppDisplayName
import com.contextpulse.phase0.ui.theme.AppThemeKey
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.contextpulse.phase0.widget.ContextPulseWidgetConfigureActivity
import com.contextpulse.phase0.widget.WidgetPreferences

enum class SettingsSubScreen {
    MAIN,
    COLOUR_PROFILE,
    WIDGET_CONFIG,
    BLOCKED_APP_GROUPS,
    SECURITY_LOCK,
    RESET_APP,
    ABOUT
}


/**
 * Top bar header for Settings sub-pages with back navigation.
 */
@Composable
fun SettingsSubPageHeader(
    category: String,
    title: String,
    onBack: () -> Unit
) {
    val theme = LocalContextPulseTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surface)
                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Settings",
                tint = theme.primary
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = category.uppercase(),
                color = theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Dedicated Sub-Page: Colour Profile & App Icon
 */
@Composable
fun ColourProfileSubScreen(
    currentTheme: AppThemeKey,
    onThemeSelected: (AppThemeKey) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsSubPageHeader(
                category = "Appearance & Branding",
                title = "Colour Profile & Icon",
                onBack = onBack
            )
        }

        item {
            Text(
                text = "Select an accent theme. The app interface, home widget, and launcher icon update seamlessly.",
                color = theme.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        items(AppThemeKey.values()) { itemTheme ->
            val isSelected = (itemTheme == currentTheme)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.surface)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) theme.primary else theme.border,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        onThemeSelected(itemTheme)
                        Toast.makeText(context, "${itemTheme.displayName} activated", Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Theme Dual Swatch Circle
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(itemTheme.surface)
                                .border(1.5.dp, itemTheme.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(36.dp)) {
                                val radius = size.minDimension / 2
                                drawArc(
                                    color = itemTheme.background,
                                    startAngle = 90f,
                                    sweepAngle = 180f,
                                    useCenter = true
                                )
                                drawArc(
                                    color = itemTheme.primary,
                                    startAngle = 270f,
                                    sweepAngle = 180f,
                                    useCenter = true
                                )
                                drawCircle(
                                    color = Color(0x33888888),
                                    radius = radius,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }

                        Column {
                            Text(
                                text = itemTheme.displayName,
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = itemTheme.subtitle,
                                color = theme.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(theme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = theme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Sub-Page: Home Screen Widget
 */
@Composable
fun WidgetConfigSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()

    val chosenProfileIds = remember { WidgetPreferences.getDefaultProfileIds(context) }
    val roomRepo = remember { RoomProfileRepository.getInstance(context) }
    val allRooms by roomRepo.rooms.collectAsState()
    val chosenRoomNames = remember(chosenProfileIds, allRooms) {
        chosenProfileIds.mapNotNull { id -> allRooms.find { it.id == id }?.name }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSubPageHeader(
                category = "Home Screen Integration",
                title = "Widget Setup",
                onBack = onBack
            )

        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Quick Profile Switching",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Switch between up to 3 chosen profiles",
                                color = theme.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    val summaryText = if (chosenRoomNames.isNotEmpty()) {
                        "Active widget profiles: " + chosenRoomNames.joinToString(" • ")
                    } else {
                        "No profiles selected for widget yet (tap below to configure)"
                    }

                    Text(
                        text = summaryText,
                        color = if (chosenRoomNames.isNotEmpty()) theme.primary else theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Place the ContextPulse widget on your phone's home screen to switch room environments with a single tap. It automatically reflects your selected color theme and active profile.",
                        color = theme.textSecondary.copy(alpha = 0.85f),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = {
                            val widgetIntent = Intent(context, ContextPulseWidgetConfigureActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(widgetIntent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configure Widget Profiles",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Sub-Page: Blocked App Groups
 */
@Composable
fun BlockedAppGroupsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()

    val appGroupRepo = remember { AppGroupRepository.getInstance(context) }
    val appGroups by appGroupRepo.appGroups.collectAsState()
    val installedApps = remember { loadInstalledApps(context) }

    var appGroupToEdit by remember { mutableStateOf<AppGroup?>(null) }
    var showAppGroupDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSubPageHeader(
                category = "Profile Automations",
                title = "Blocked App Groups",
                onBack = onBack
            )
        }

        item {
            Text(
                text = "Bundle frequently restricted apps into named groups. In your profile blocking rules, you can select whole groups at once or pick individual apps.",
                color = theme.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        if (appGroups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Category,
                            contentDescription = null,
                            tint = theme.textSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No App Groups Yet",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ Create App Group' below to bundle multiple apps together for 1-tap blocking.",
                            color = theme.textSecondary,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(appGroups, key = { it.id }) { group ->
                val appNames = remember(group.packageNames, installedApps) {
                    group.packageNames.map { resolveAppDisplayName(context, it, installedApps) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = group.name,
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${group.packageNames.size} apps",
                                        color = theme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (appNames.isEmpty()) "No apps assigned" else appNames.joinToString(", "),
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 2
                            )

                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    appGroupToEdit = group
                                    showAppGroupDialog = true
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit Group",
                                    tint = theme.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    appGroupRepo.deleteGroup(group.id)
                                    Toast.makeText(context, "Deleted group '${group.name}'", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Group",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    appGroupToEdit = null
                    showAppGroupDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create App Group",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        }
    }

    if (showAppGroupDialog) {
        CreateOrEditAppGroupDialog(
            groupToEdit = appGroupToEdit,
            onDismiss = {
                showAppGroupDialog = false
                appGroupToEdit = null
            },
            onSave = { name, packages ->
                if (appGroupToEdit == null) {
                    appGroupRepo.addGroup(name, packages)
                    Toast.makeText(context, "Created '$name' with ${packages.size} apps", Toast.LENGTH_SHORT).show()
                } else {
                    appGroupRepo.updateGroup(appGroupToEdit!!.id, name, packages)
                    Toast.makeText(context, "Updated '$name'", Toast.LENGTH_SHORT).show()
                }
                showAppGroupDialog = false
                appGroupToEdit = null
            }
        )
    }
}

/**
 * Dedicated Sub-Page: Reset Application Configurations
 */
@Composable
fun ResetAppSubScreen(
    onResetConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSubPageHeader(
                category = "System Management",
                title = "Reset Application",
                onBack = onBack
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Wipe All Configurations",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Irreversible complete reset",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "Resetting the application will completely erase all stored data and configuration parameters:",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        listOf(
                            "Wipes all custom room profiles and automation rules",
                            "Detaches and clears all configured sensors (BLE, NFC, QR)",
                            "Purges all activity history logs and proximity metrics",
                            "Deletes all custom blocked app groups",
                            "Revokes all security and system automation permissions"
                        ).forEach { itemText ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                )
                                Text(
                                    text = itemText,
                                    color = theme.textSecondary.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { showConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset App Configurations",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = theme.surface,
            titleContentColor = theme.textPrimary,
            textContentColor = theme.textSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Full Reset?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to wipe all configurations for ContextPulse? This will permanently wipe all profiles and sensors, clear logs, remove app groups, and revoke permissions.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onResetConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Wipe Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    border = BorderStroke(1.dp, theme.border),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = theme.textPrimary)
                }
            }
        )
    }
}

/**
 * Dedicated Sub-Page: About ContextPulse
 */
@Composable
fun AboutAppSubScreen(
    onTakeTour: () -> Unit = {},
    onBack: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSubPageHeader(
                category = "System Info",
                title = "About ContextPulse",
                onBack = onBack
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ContextPulseLogo(size = 56.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ContextPulse v1.0",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Native BLE Beacon proximity engine and contextual room automation. Automatically applies DND modes, brightness, eye comfort, and blocked app rules based on your physical space.",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        item {
            Button(
                onClick = onTakeTour,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Take Interactive App Tour", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "SYSTEM ARCHITECTURE & PRIVACY",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "• 100% On-Device: Telemetry, profile switching, and beacon proximity algorithms run entirely on your device with no external server transmission.\n• 7-Day Strict Retention: Historical transition logs are automatically purged after 7 days to preserve device privacy.\n• Native Foreground Service: Persistent background beacon scanning with optimized low-power consumption.",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Sub-Page: Security & App Lock
 */
@Composable
fun SecurityLockSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()
    val lockManager = remember { com.contextpulse.phase0.security.AppLockManager.getInstance(context) }

    val isLockActive by lockManager.isLockEnabled.collectAsState()

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisableVerifyPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSubPageHeader(
                category = "Privacy & Protection",
                title = "Security & App Lock",
                onBack = onBack
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(theme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Configuration Lock",
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (isLockActive) "6-Digit PIN Protection Active" else "Protection Disabled",
                                    color = if (isLockActive) theme.primary else theme.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Switch(
                            checked = isLockActive,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // Always prompt for a new 6-digit PIN each time it is enabled
                                    pinInput = ""
                                    pinConfirmInput = ""
                                    pinError = null
                                    showSetPinDialog = true
                                } else {
                                    // Require entering PIN to disable
                                    showDisableVerifyPinDialog = true
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = theme.onPrimary,
                                checkedTrackColor = theme.primary
                            )
                        )
                    }

                    Text(
                        text = "When active, your 6-digit PIN protects contexts, modes, and triggers from being changed.",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )

                    if (isLockActive) {
                        Divider(color = theme.border, thickness = 1.dp)

                        val sessionPolicy by lockManager.sessionPolicy.collectAsState()
                        val isSessionUnlocked by lockManager.isSessionUnlocked.collectAsState()

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "UNLOCK BEHAVIOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accent,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Choose how often the PIN is requested when modifying settings:",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )

                            com.contextpulse.phase0.security.LockSessionPolicy.entries.forEach { policy ->
                                val isSelected = sessionPolicy == policy
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.background),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) theme.primary else theme.border
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { lockManager.setSessionPolicy(policy) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { lockManager.setSessionPolicy(policy) },
                                            colors = RadioButtonDefaults.colors(selectedColor = theme.primary)
                                        )
                                        Column {
                                            Text(
                                                text = policy.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.textPrimary
                                            )
                                            Text(
                                                text = policy.description,
                                                fontSize = 11.5.sp,
                                                color = theme.textSecondary,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Session Status and Manual Lock Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSessionUnlocked) theme.primary.copy(alpha = 0.08f) else theme.background)
                                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isSessionUnlocked) Color(0xFF10B981) else Color(0xFFEF4444))
                                    )
                                    Text(
                                        text = if (isSessionUnlocked) "Session: Unlocked (PIN not needed)" else "Session: Locked",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary
                                    )
                                }

                                if (isSessionUnlocked) {
                                    TextButton(
                                        onClick = {
                                            lockManager.lockSessionNow()
                                            Toast.makeText(context, "Session locked. PIN required on next edit.", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lock Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDisableVerifyPinDialog) {
        AppLockDialog(
            lockManager = lockManager,
            title = "Disable Security Lock",
            subtitle = "Enter your current 6-digit PIN to turn off lock protection",
            onSuccess = {
                lockManager.disableLock()
                showDisableVerifyPinDialog = false
                Toast.makeText(context, "Lock protection disabled", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showDisableVerifyPinDialog = false
            }
        )
    }

    if (showSetPinDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSetPinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Set 6-Digit Security PIN",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    Text(
                        text = "Enter a mandatory 6-digit PIN to protect configurations:",
                        fontSize = 13.sp,
                        color = theme.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("Enter 6-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinConfirmInput = it },
                        label = { Text("Confirm 6-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinError != null) {
                        Text(
                            text = pinError ?: "",
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSetPinDialog = false }) {
                            Text("Cancel", color = theme.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (pinInput.length != 6) {
                                    pinError = "PIN must be exactly 6 digits"
                                } else if (pinInput != pinConfirmInput) {
                                    pinError = "PINs do not match"
                                } else {
                                    lockManager.setPin(pinInput)
                                    showSetPinDialog = false
                                    Toast.makeText(context, "6-digit security PIN enabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = pinInput.length == 6 && pinConfirmInput.length == 6,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) {
                            Text("Enable Lock", color = theme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

