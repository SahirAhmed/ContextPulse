package com.contextpulse.phase0.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.RoomProfile
import com.contextpulse.phase0.model.computeDefaultUncoveredSummary
import com.contextpulse.phase0.model.sortModesChronologically
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.SystemControllers
import com.contextpulse.phase0.ui.components.AppSelectorDialog
import com.contextpulse.phase0.ui.components.DND_MODES
import com.contextpulse.phase0.ui.components.contextPulseLazyScrollbar
import com.contextpulse.phase0.ui.components.loadInstalledApps
import com.contextpulse.phase0.ui.components.resolveAppDisplayName
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import java.util.UUID

@Composable
fun EditContextScreen(
    contextItem: ContextItem,
    roomRepo: RoomProfileRepository,
    onSave: (newName: String, newSubtitle: String, newIconKey: String, updatedProfile: RoomProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    BackHandler { onBack() }

    var contextName by remember { mutableStateOf(contextItem.name) }
    var contextSubtitle by remember { mutableStateOf(contextItem.subtitle) }
    var contextIconKey by remember { mutableStateOf(contextItem.iconName.ifBlank { "meeting_room" }) }

    // Multi-mode list state, initialized with Default mode and sorted chronologically
    val modes = remember {
        mutableStateListOf<ContextTimeframeMode>().apply {
            val loaded = if (contextItem.profile.modes.isNotEmpty()) {
                contextItem.profile.modes
            } else {
                listOf(ContextTimeframeMode(name = "Default", isAllTime = true))
            }
            val hasDefault = loaded.any { it.isAllTime || it.name.equals("Default", ignoreCase = true) }
            val normalized = if (!hasDefault) {
                listOf(ContextTimeframeMode(name = "Default", isAllTime = true)) + loaded
            } else {
                loaded.map {
                    if (it.name == "All Time Standard") it.copy(name = "Default", isAllTime = true) else it
                }
            }
            addAll(sortModesChronologically(normalized))
        }
    }

    var editingModeIdForConfig by remember { mutableStateOf<String?>(null) }
    var showAddModeDialog by remember { mutableStateOf(false) }
    var newModeName by remember { mutableStateOf("") }
    var newModeDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var newModeStartTime by remember { mutableStateOf("09:00") }
    var newModeEndTime by remember { mutableStateOf("17:00") }

    val daysOfWeek = listOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )

    val isDuplicateName = remember(contextName) {
        contextName.isNotBlank() && roomRepo.isDuplicateName(contextName, excludeId = contextItem.id)
    }

    // Focused Mode Configuration Dialog
    if (editingModeIdForConfig != null) {
        val targetModeIndex = modes.indexOfFirst { it.id == editingModeIdForConfig }
        if (targetModeIndex != -1) {
            val targetMode = modes[targetModeIndex]
            val isDefault = targetMode.isAllTime || targetMode.name.equals("Default", ignoreCase = true)
            ConfigureModeDialog(
                mode = targetMode,
                isDefault = isDefault,
                daysOfWeek = daysOfWeek,
                onSave = { updatedMode ->
                    modes[targetModeIndex] = updatedMode
                    val reordered = sortModesChronologically(modes.toList())
                    modes.clear()
                    modes.addAll(reordered)
                    editingModeIdForConfig = null
                },
                onDismiss = { editingModeIdForConfig = null }
            )
        }
    }

    // Add Mode Dialog with Day of Week and Timeframe selection
    if (showAddModeDialog) {
        Dialog(onDismissRequest = { showAddModeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.border),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "New Mode",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    Text(
                        text = "A space can adapt based on time and day. The Default mode automatically adjusts to cover remaining hours.",
                        fontSize = 12.5.sp,
                        color = theme.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = newModeName,
                        onValueChange = { newModeName = it },
                        label = { Text("Mode Name") },
                        placeholder = { Text("e.g. Work Focus, Night Sleep") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Day of Week Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Active Days",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.textPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            daysOfWeek.forEach { (dayNum, dayLabel) ->
                                val isSelected = newModeDays.contains(dayNum)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) theme.primary else theme.background)
                                        .border(1.dp, if (isSelected) theme.primary else theme.border, CircleShape)
                                        .clickable {
                                            newModeDays = if (isSelected) {
                                                if (newModeDays.size > 1) newModeDays - dayNum else newModeDays
                                            } else {
                                                newModeDays + dayNum
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) theme.onPrimary else theme.textSecondary
                                    )
                                }
                            }
                        }

                        // Preset shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Weekdays",
                                fontSize = 11.sp,
                                color = theme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { newModeDays = setOf(1, 2, 3, 4, 5) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(
                                text = "Weekends",
                                fontSize = 11.sp,
                                color = theme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { newModeDays = setOf(6, 7) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(
                                text = "All 7 Days",
                                fontSize = 11.sp,
                                color = theme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { newModeDays = setOf(1, 2, 3, 4, 5, 6, 7) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Start & End Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newModeStartTime,
                            onValueChange = { newModeStartTime = it },
                            label = { Text("From") },
                            placeholder = { Text("09:00") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = newModeEndTime,
                            onValueChange = { newModeEndTime = it },
                            label = { Text("To") },
                            placeholder = { Text("17:00") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddModeDialog = false }) {
                            Text("Cancel", color = theme.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newModeName.isNotBlank()) {
                                    val newMode = ContextTimeframeMode(
                                        id = UUID.randomUUID().toString(),
                                        name = newModeName.trim(),
                                        isAllTime = false,
                                        startTime = newModeStartTime.trim().ifEmpty { "09:00" },
                                        endTime = newModeEndTime.trim().ifEmpty { "17:00" },
                                        activeDays = newModeDays.sorted()
                                    )
                                    val reordered = sortModesChronologically(modes + newMode)
                                    modes.clear()
                                    modes.addAll(reordered)
                                    showAddModeDialog = false
                                    newModeName = ""
                                    editingModeIdForConfig = newMode.id
                                }
                            },
                            enabled = newModeName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            Text("Create Mode", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.primary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "CONTEXT PROFILE SETTINGS",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Edit ${contextItem.name}",
                        color = theme.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Section 1: Basic Context Information
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Context Information",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    OutlinedTextField(
                        value = contextName,
                        onValueChange = { contextName = it },
                        label = { Text("Context Name") },
                        singleLine = true,
                        isError = isDuplicateName,
                        supportingText = {
                            if (isDuplicateName) {
                                Text("A context with this name already exists", color = Color(0xFFE53935))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = contextSubtitle,
                        onValueChange = { contextSubtitle = it },
                        label = { Text("Subtitle / Location description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Context Logo / Icon Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Context Logo / Icon",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getContextIcon(contextIconKey),
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Icon selection grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CONTEXT_ICONS.forEach { (key, vector) ->
                                val isSelected = contextIconKey == key
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) theme.primary else theme.background)
                                        .border(
                                            1.dp,
                                            if (isSelected) theme.primary else theme.border,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { contextIconKey = key },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = key,
                                        tint = if (isSelected) theme.onPrimary else theme.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Modes (Adaptive Time and Day Contexts)
        item {
            val customModes = modes.filter { !it.isAllTime && !it.name.equals("Default", ignoreCase = true) }
            val defaultCoverageText = computeDefaultUncoveredSummary(customModes)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MODES & SCHEDULE",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                        Text(
                            text = "Modes",
                            color = theme.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "A space adapts based on time and day. All modes cover all 24 hours all 7 days.",
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Button(
                        onClick = {
                            newModeName = ""
                            newModeDays = setOf(1, 2, 3, 4, 5)
                            newModeStartTime = "09:00"
                            newModeEndTime = "17:00"
                            showAddModeDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Mode", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }

                // Modes List rendered in Chronological Order with Clean, Uncluttered Cards
                modes.forEachIndexed { modeIndex, mode ->
                    val isDefault = mode.isAllTime || mode.name.equals("Default", ignoreCase = true)

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        border = BorderStroke(1.dp, theme.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header: Mode title, tag badge, delete button (for custom)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(theme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isDefault) Icons.Outlined.Layers else Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = mode.name,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.textPrimary
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isDefault) theme.primary.copy(alpha = 0.12f) else theme.background)
                                                    .border(1.dp, if (isDefault) theme.primary.copy(alpha = 0.35f) else theme.border, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isDefault) "Default Mode (24/7)" else "${mode.getDaysSummary()} • ${mode.startTime}–${mode.endTime}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDefault) theme.primary else theme.textSecondary
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (isDefault) "Automatic 24/7 Baseline" else "Active ${mode.getDaysSummary()} from ${mode.startTime} to ${mode.endTime}",
                                            fontSize = 11.5.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                }

                                if (!isDefault) {
                                    IconButton(
                                        onClick = { modes.removeAt(modeIndex) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Delete Mode",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Detailed Schedule Breakdown for Default Mode
                            if (isDefault) {
                                val breakdown = com.contextpulse.phase0.model.computeDefaultDetailedScheduleBreakdown(customModes)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.primary.copy(alpha = 0.08f))
                                        .border(1.dp, theme.primary.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = theme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Active Schedule (24/7 Baseline Fallback):",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.primary
                                            )
                                        }

                                        breakdown.forEach { scheduleLine ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(theme.primary)
                                                )
                                                Text(
                                                    text = scheduleLine,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = theme.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Bullet Points: Configurations in this mode
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.background)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Active Configurations:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textSecondary,
                                    letterSpacing = 0.5.sp
                                )

                                // Bullet 1: DND
                                ModeConfigBullet(
                                    icon = Icons.Default.DoNotDisturb,
                                    label = "Do Not Disturb",
                                    value = if (mode.dndEnabled) mode.dndMode else "Disabled",
                                    isActive = mode.dndEnabled
                                )

                                // Bullet 2: Screen Brightness
                                ModeConfigBullet(
                                    icon = Icons.Default.BrightnessMedium,
                                    label = "Screen Brightness",
                                    value = if (mode.brightnessEnabled) "${mode.brightnessLevel}%" else "System default",
                                    isActive = mode.brightnessEnabled
                                )

                                // Bullet 3: Eye Comfort / Night Light
                                ModeConfigBullet(
                                    icon = Icons.Default.NightlightRound,
                                    label = "Eye Comfort",
                                    value = if (mode.eyeComfortEnabled) "Active (${mode.eyeComfortLevel}% warmth)" else "Disabled",
                                    isActive = mode.eyeComfortEnabled
                                )

                                // Bullet 4: Blocked Apps
                                val appCount = mode.restrictedApps.size
                                ModeConfigBullet(
                                    icon = Icons.Default.Apps,
                                    label = "Blocked Apps",
                                    value = if (mode.appRestrictionsEnabled && appCount > 0) "$appCount restricted" else "No apps restricted",
                                    isActive = mode.appRestrictionsEnabled && appCount > 0
                                )
                            }

                            // Button to configure mode actions
                            OutlinedButton(
                                onClick = { editingModeIdForConfig = mode.id },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = theme.primary
                                ),
                                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Configure Mode Actions", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Save Changes Action
        item {
            Button(
                onClick = {
                    if (contextName.isBlank()) {
                        Toast.makeText(context, "Please enter a context name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isDuplicateName) {
                        Toast.makeText(context, "A context with this name already exists", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Save modes strictly in chronological order
                    val orderedModes = sortModesChronologically(modes.toList())
                    val updatedProfile = contextItem.profile.copy(modes = orderedModes)
                    onSave(contextName.trim(), contextSubtitle.trim(), contextIconKey, updatedProfile)
                    Toast.makeText(context, "Context profile saved successfully", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                ),
                enabled = contextName.isNotBlank() && !isDuplicateName
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Context Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun ModeConfigBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isActive: Boolean
) {
    val theme = LocalContextPulseTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) theme.primary else theme.textSecondary.copy(alpha = 0.4f))
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) theme.primary else theme.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) theme.textPrimary else theme.textSecondary
            )
        }

        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) theme.primary else theme.textSecondary.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ConfigureModeDialog(
    mode: ContextTimeframeMode,
    isDefault: Boolean,
    daysOfWeek: List<Pair<Int, String>>,
    onSave: (ContextTimeframeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current

    var modeName by remember { mutableStateOf(mode.name) }
    var activeDays by remember { mutableStateOf(mode.activeDays.toSet()) }
    var startTime by remember { mutableStateOf(mode.startTime) }
    var endTime by remember { mutableStateOf(mode.endTime) }

    var dndEnabled by remember { mutableStateOf(mode.dndEnabled) }
    var dndMode by remember { mutableStateOf(mode.dndMode) }

    var brightnessEnabled by remember { mutableStateOf(mode.brightnessEnabled) }
    var brightnessLevel by remember { mutableStateOf(mode.brightnessLevel) }

    var eyeComfortEnabled by remember { mutableStateOf(mode.eyeComfortEnabled) }
    var eyeComfortLevel by remember { mutableStateOf(mode.eyeComfortLevel) }

    var appRestrictionsEnabled by remember { mutableStateOf(mode.appRestrictionsEnabled) }
    var restrictedApps by remember { mutableStateOf(mode.restrictedApps) }

    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AppSelectorDialog(
            context = context,
            initialSelected = restrictedApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { updated ->
                restrictedApps = updated
                showAppPicker = false
            }
        )
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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDefault) Icons.Outlined.Layers else Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isDefault) "Configure Default Mode" else "Configure $modeName",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = if (isDefault) "Baseline actions when no other mode is active" else "Schedule and actions for this mode",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isDefault) {
                        // Section: Schedule
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.background),
                            border = BorderStroke(1.dp, theme.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "SCHEDULE & TIMING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.accent,
                                    letterSpacing = 1.sp
                                )

                                OutlinedTextField(
                                    value = modeName,
                                    onValueChange = { modeName = it },
                                    label = { Text("Mode Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Active Days
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Active Days", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        daysOfWeek.forEach { (dNum, dLabel) ->
                                            val isSel = activeDays.contains(dNum)
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSel) theme.primary else theme.surface)
                                                    .border(1.dp, if (isSel) theme.primary else theme.border, CircleShape)
                                                    .clickable {
                                                        activeDays = if (isSel) {
                                                            if (activeDays.size > 1) activeDays - dNum else activeDays
                                                        } else {
                                                            activeDays + dNum
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dLabel,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) theme.onPrimary else theme.textSecondary
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Weekdays",
                                            fontSize = 11.sp,
                                            color = theme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { activeDays = setOf(1, 2, 3, 4, 5) }
                                        )
                                        Text(
                                            text = "Weekends",
                                            fontSize = 11.sp,
                                            color = theme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { activeDays = setOf(6, 7) }
                                        )
                                        Text(
                                            text = "Everyday",
                                            fontSize = 11.sp,
                                            color = theme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { activeDays = setOf(1, 2, 3, 4, 5, 6, 7) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = startTime,
                                        onValueChange = { startTime = it },
                                        label = { Text("From (09:00)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = endTime,
                                        onValueChange = { endTime = it },
                                        label = { Text("To (17:00)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Section: Automations
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.background),
                        border = BorderStroke(1.dp, theme.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "AUTOMATION ACTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accent,
                                letterSpacing = 1.sp
                            )

                            // Action 1: DND
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.DoNotDisturb, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Text("Do Not Disturb", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    }
                                    Switch(checked = dndEnabled, onCheckedChange = { dndEnabled = it })
                                }

                                if (dndEnabled) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (dMode in DND_MODES) {
                                            val isSel = dndMode == dMode.first
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) theme.primary else theme.surface)
                                                    .border(1.dp, if (isSel) theme.primary else theme.border, RoundedCornerShape(8.dp))
                                                    .clickable { dndMode = dMode.first }
                                                    .padding(vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dMode.second,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) theme.onPrimary else theme.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(color = theme.border, thickness = 0.8.dp)

                            // Action 2: Screen Brightness
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Text("Screen Brightness", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    }
                                    Switch(checked = brightnessEnabled, onCheckedChange = { brightnessEnabled = it })
                                }

                                if (brightnessEnabled) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Target Brightness", fontSize = 12.sp, color = theme.textSecondary)
                                        Text("$brightnessLevel%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primary)
                                    }
                                    Slider(
                                        value = brightnessLevel.toFloat(),
                                        onValueChange = { brightnessLevel = it.toInt() },
                                        valueRange = 1f..100f
                                    )
                                }
                            }

                            Divider(color = theme.border, thickness = 0.8.dp)

                            // Action 3: Eye Comfort
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.NightlightRound, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Text("Eye Comfort / Night Light", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    }
                                    Switch(checked = eyeComfortEnabled, onCheckedChange = { eyeComfortEnabled = it })
                                }

                                if (eyeComfortEnabled) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Warmth Tint Intensity", fontSize = 12.sp, color = theme.textSecondary)
                                        Text("$eyeComfortLevel%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primary)
                                    }
                                    Slider(
                                        value = eyeComfortLevel.toFloat(),
                                        onValueChange = { eyeComfortLevel = it.toInt() },
                                        valueRange = 1f..100f
                                    )
                                }
                            }

                            Divider(color = theme.border, thickness = 0.8.dp)

                            // Action 4: Blocked Apps
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Apps, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Text("Blocked Apps", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    }
                                    Switch(checked = appRestrictionsEnabled, onCheckedChange = { appRestrictionsEnabled = it })
                                }

                                if (appRestrictionsEnabled) {
                                    val count = restrictedApps.size
                                    OutlinedButton(
                                        onClick = { showAppPicker = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (count == 0) "Select Apps & Groups to Block" else "Manage Blocked Apps ($count)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = theme.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = mode.copy(
                                name = if (isDefault) "Default" else modeName.trim().ifEmpty { mode.name },
                                activeDays = if (isDefault) listOf(1, 2, 3, 4, 5, 6, 7) else activeDays.sorted(),
                                startTime = if (isDefault) "00:00" else startTime.trim().ifEmpty { "09:00" },
                                endTime = if (isDefault) "23:59" else endTime.trim().ifEmpty { "17:00" },
                                dndEnabled = dndEnabled,
                                dndMode = dndMode,
                                brightnessEnabled = brightnessEnabled,
                                brightnessLevel = brightnessLevel,
                                eyeComfortEnabled = eyeComfortEnabled,
                                eyeComfortLevel = eyeComfortLevel,
                                appRestrictionsEnabled = appRestrictionsEnabled,
                                restrictedApps = restrictedApps
                            )
                            onSave(updated)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        )
                    ) {
                        Text("Save Mode", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
