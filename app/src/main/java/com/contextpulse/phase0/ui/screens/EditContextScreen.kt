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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.model.AppUsageBudget
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.StickyNoteCheckItem
import com.contextpulse.phase0.model.RoomProfile
import com.contextpulse.phase0.model.computeDefaultUncoveredSummary
import com.contextpulse.phase0.model.sortModesChronologically
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.ActiveConfigSnapshotManager
import com.contextpulse.phase0.system.ProfileActivationHelper
import com.contextpulse.phase0.system.SystemControllers
import com.contextpulse.phase0.ui.components.AppSelectorDialog
import com.contextpulse.phase0.ui.components.SingleAppSelectorDialog
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

    val originalActiveRoomId = remember { roomRepo.activeRoomId.value }
    var activePreviewModeId by remember {
        mutableStateOf(ActiveConfigSnapshotManager.getActivePreviewModeId(context))
    }

    fun endActivePreviewIfRunning() {
        if (activePreviewModeId != null || ActiveConfigSnapshotManager.isPreviewActive(context)) {
            ProfileActivationHelper.restoreAfterPreview(context)
            activePreviewModeId = null
        }
    }

    val handleBackWithPreviewCleanup = {
        endActivePreviewIfRunning()
        onBack()
    }

    BackHandler { handleBackWithPreviewCleanup() }

    DisposableEffect(Unit) {
        onDispose {
            endActivePreviewIfRunning()
        }
    }

    var contextName by remember { mutableStateOf(contextItem.name) }
    var contextSubtitle by remember { mutableStateOf(contextItem.subtitle) }
    var contextIconKey by remember { mutableStateOf(contextItem.iconName.ifBlank { "meeting_room" }) }

    // Multi-mode list state, initialized with Default mode and sorted chronologically
    val modes = remember {
        mutableStateListOf<ContextTimeframeMode>().apply {
            val loaded = if (contextItem.profile.modes.isNotEmpty()) {
                contextItem.profile.modes
            } else {
                listOf(ContextTimeframeMode(name = "Default", isDefault = true))
            }
            val hasDefault = loaded.any { it.isDefault || it.name.equals("Default", ignoreCase = true) }
            val normalized = if (!hasDefault) {
                listOf(ContextTimeframeMode(name = "Default", isDefault = true)) + loaded
            } else {
                loaded.map {
                    if (it.name == "All Time Standard") it.copy(name = "Default", isDefault = true) else it
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
        Dialog(
            onDismissRequest = { showAddModeDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.border),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 560.dp)
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
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
                                    val startParts = newModeStartTime.split(":")
                                    val sHour = startParts.getOrNull(0)?.toIntOrNull() ?: 9
                                    val sMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                                    val endParts = newModeEndTime.split(":")
                                    val eHour = endParts.getOrNull(0)?.toIntOrNull() ?: 17
                                    val eMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 0

                                    val newMode = ContextTimeframeMode(
                                        id = UUID.randomUUID().toString(),
                                        name = newModeName.trim(),
                                        isDefault = false,
                                        startHour = sHour,
                                        startMinute = sMinute,
                                        endHour = eHour,
                                        endMinute = eMinute,
                                        daysOfWeek = newModeDays
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
    ) {
        // Pinned Top Header - remains stationary outside scrollable LazyColumn
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = handleBackWithPreviewCleanup,
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
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp)
                    .contextPulseLazyScrollbar(listState)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

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
                            text = "Custom modes have priority over the Default mode during their active windows. Outside scheduled windows, the Default mode applies automatically.",
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
                        Text("Custom Mode", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }

                // Modes List rendered in Chronological Order with Clean, Uncluttered Cards
                modes.forEachIndexed { modeIndex, mode ->
                    val isDefault = mode.isAllTime || mode.name.equals("Default", ignoreCase = true)
                    val isPreviewingThisMode = activePreviewModeId == mode.id

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPreviewingThisMode) theme.primary.copy(alpha = 0.06f) else theme.surface
                        ),
                        border = BorderStroke(
                            width = if (isPreviewingThisMode) 2.dp else 1.dp,
                            color = if (isPreviewingThisMode) theme.primary else if (!isDefault && !mode.isEnabled) theme.border.copy(alpha = 0.5f) else theme.border
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header: Mode title, tag badge, toggle switch, delete button
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
                                            .background(
                                                if (isPreviewingThisMode)
                                                    theme.primary
                                                else if (!isDefault && !mode.isEnabled)
                                                    theme.textSecondary.copy(alpha = 0.12f)
                                                else
                                                    theme.primary.copy(alpha = 0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewingThisMode) Icons.Default.PlayArrow else if (isDefault) Icons.Outlined.Layers else Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = if (isPreviewingThisMode) theme.onPrimary else if (!isDefault && !mode.isEnabled) theme.textSecondary else theme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = mode.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                color = if (!isDefault && !mode.isEnabled) theme.textSecondary else theme.textPrimary,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            // Dedicated Tags for Default Mode and Custom Mode
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isDefault) theme.primary.copy(alpha = 0.12f) else theme.accent.copy(alpha = 0.12f))
                                                    .border(
                                                        1.dp,
                                                        if (isDefault) theme.primary.copy(alpha = 0.35f) else theme.accent.copy(alpha = 0.35f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isDefault) "Default Mode" else "Custom Mode",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDefault) theme.primary else theme.accent
                                                )
                                            }

                                            if (isPreviewingThisMode) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(theme.primary)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White)
                                                        )
                                                        Text(
                                                            text = "Preview Active",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = theme.onPrimary
                                                        )
                                                    }
                                                }
                                            }

                                            if (!isDefault && !mode.isEnabled) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0x22EF4444))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Disabled",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFEF4444)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = if (isDefault) {
                                                "Automatic 24/7 Baseline Fallback"
                                            } else {
                                                "${mode.getDaysSummary()} • ${mode.startTime}–${mode.endTime} (Priority over Default)"
                                            },
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            color = theme.textSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Toggle switch for custom modes (to enable or disable without deleting)
                                    if (!isDefault) {
                                        Switch(
                                            checked = mode.isEnabled,
                                            onCheckedChange = { isChecked ->
                                                modes[modeIndex] = mode.copy(isEnabled = isChecked)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = theme.onPrimary,
                                                checkedTrackColor = theme.primary
                                            )
                                        )

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

                                // 1. Auto-Open App
                                val appOpenDisplay = if (mode.autoLaunchAppEnabled && mode.autoLaunchAppName.isNotBlank()) {
                                    mode.autoLaunchAppName
                                } else if (mode.autoLaunchAppEnabled && mode.autoLaunchPackage.isNotBlank()) {
                                    mode.autoLaunchPackage
                                } else {
                                    "Disabled"
                                }
                                ModeConfigBullet(
                                    icon = Icons.Default.RocketLaunch,
                                    label = "Auto-Open App",
                                    value = appOpenDisplay,
                                    isActive = mode.autoLaunchAppEnabled && mode.autoLaunchPackage.isNotBlank()
                                )

                                // 2. Do Not Disturb
                                ModeConfigBullet(
                                    icon = Icons.Default.DoNotDisturb,
                                    label = "Do Not Disturb",
                                    value = if (mode.dndEnabled) mode.dndMode else "Disabled",
                                    isActive = mode.dndEnabled
                                )

                                // 3. Eye Comfort
                                ModeConfigBullet(
                                    icon = Icons.Default.NightlightRound,
                                    label = "Eye Comfort",
                                    value = if (mode.eyeComfortEnabled) "Active (${mode.eyeComfortLevel}% warmth)" else "Disabled",
                                    isActive = mode.eyeComfortEnabled
                                )

                                // 4. Restricted Apps
                                val blockedCount = mode.outrightBlockedCount
                                val budgetCount = mode.budgetedAppsCount
                                val totalRestricted = mode.totalRestrictedCount
                                val restrictedValue = if (!mode.appRestrictionsEnabled || totalRestricted == 0) {
                                    "None"
                                } else {
                                    val parts = mutableListOf<String>()
                                    if (blockedCount > 0) parts.add("$blockedCount blocked")
                                    if (budgetCount > 0) parts.add("$budgetCount budgeted")
                                    "$totalRestricted total (${parts.joinToString(", ")})"
                                }
                                ModeConfigBullet(
                                    icon = Icons.Default.Apps,
                                    label = "Restricted Apps",
                                    value = restrictedValue,
                                    isActive = mode.appRestrictionsEnabled && totalRestricted > 0
                                )

                                // 5. Screen Brightness
                                ModeConfigBullet(
                                    icon = Icons.Default.BrightnessMedium,
                                    label = "Screen Brightness",
                                    value = if (mode.brightnessEnabled) "${mode.brightnessLevel}%" else "System default",
                                    isActive = mode.brightnessEnabled
                                )

                                // 6. Sticky Note
                                val stickySummary = if (!mode.stickyNoteEnabled) {
                                    "Disabled"
                                } else if (mode.stickyNoteChecklist.isNotEmpty()) {
                                    "${mode.stickyNoteChecklist.size} checklist item(s)"
                                } else if (mode.stickyNoteText.isNotBlank()) {
                                    "Custom note configured"
                                } else {
                                    "Enabled"
                                }
                                ModeConfigBullet(
                                    icon = Icons.Default.StickyNote2,
                                    label = "Sticky Note",
                                    value = stickySummary,
                                    isActive = mode.stickyNoteEnabled && (mode.stickyNoteText.isNotBlank() || mode.stickyNoteChecklist.isNotEmpty())
                                )
                            }

                            // Action Buttons: Configure Mode Actions and Preview
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { editingModeIdForConfig = mode.id },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = theme.primary
                                    ),
                                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Configure", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }

                                if (isPreviewingThisMode) {
                                    Button(
                                        onClick = {
                                            ProfileActivationHelper.restoreAfterPreview(context)
                                            activePreviewModeId = null
                                            Toast.makeText(context, "Ended preview for '${mode.name}'", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("End Preview", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = {
                                            // Apply mode automations live
                                            ProfileActivationHelper.previewModeAutomations(
                                                context = context,
                                                contextName = contextName.ifBlank { contextItem.name },
                                                mode = mode
                                            )
                                            activePreviewModeId = mode.id
                                            Toast.makeText(context, "Previewing '${mode.name}' live", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = theme.primary.copy(alpha = 0.12f),
                                            contentColor = theme.primary
                                        ),
                                        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.35f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Preview", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                }
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

                    // End preview before saving so settings are restored / persisted properly
                    endActivePreviewIfRunning()

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
            modifier = Modifier.weight(1f),
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
                color = if (isActive) theme.textPrimary else theme.textSecondary,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) theme.primary else theme.textSecondary.copy(alpha = 0.7f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

    val permRepo = remember { com.contextpulse.phase0.repository.PermissionsRepository.getInstance(context) }
    val permState by permRepo.state.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permRepo.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isModeEnabled by remember { mutableStateOf(mode.isEnabled) }
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

    var autoLaunchAppEnabled by remember { mutableStateOf(mode.autoLaunchAppEnabled) }
    var autoLaunchPackage by remember { mutableStateOf(mode.autoLaunchPackage) }
    var autoLaunchAppName by remember { mutableStateOf(mode.autoLaunchAppName) }

    var stickyNoteEnabled by remember { mutableStateOf(mode.stickyNoteEnabled) }
    var stickyNoteText by remember { mutableStateOf(mode.stickyNoteText) }
    var stickyNoteChecklist by remember { mutableStateOf(mode.stickyNoteChecklist) }
    var newCheckItemText by remember { mutableStateOf("") }



    var appBudgets by remember { mutableStateOf(mode.appBudgets) }
    var blockedAppGroupIds by remember { mutableStateOf(mode.blockedAppGroupIds) }
    var showRestrictedAppsDialog by remember { mutableStateOf(false) }
    var showSingleAppPicker by remember { mutableStateOf(false) }

    if (showRestrictedAppsDialog) {
        val currentEntries = remember(restrictedApps, appBudgets) {
            val res = mutableListOf<com.contextpulse.phase0.model.RestrictedAppEntry>()
            val budgetedMap = appBudgets.associateBy { it.packageName }
            restrictedApps.forEach { pkg ->
                if (!budgetedMap.containsKey(pkg)) {
                    res.add(com.contextpulse.phase0.model.RestrictedAppEntry(packageName = pkg, appName = com.contextpulse.phase0.ui.components.resolveAppDisplayName(context, pkg), maxMinutes = 0))
                }
            }
            appBudgets.forEach { b ->
                res.add(com.contextpulse.phase0.model.RestrictedAppEntry(packageName = b.packageName, appName = b.appName.ifBlank { com.contextpulse.phase0.ui.components.resolveAppDisplayName(context, b.packageName) }, maxMinutes = b.maxMinutes))
            }
            res
        }

        com.contextpulse.phase0.ui.components.RestrictedAppsManagerDialog(
            context = context,
            initialRestrictedEntries = currentEntries,
            initialBlockedGroupIds = blockedAppGroupIds,
            onDismiss = { showRestrictedAppsDialog = false },
            onConfirm = { updatedEntries, updatedGroupIds ->
                val newBlocked = updatedEntries.filter { it.isOutrightBlock }.map { it.packageName }
                val newBudgets = updatedEntries.filter { !it.isOutrightBlock }.map { entry ->
                    AppUsageBudget(
                        id = UUID.randomUUID().toString(),
                        packageName = entry.packageName,
                        appName = entry.appName,
                        maxMinutes = entry.maxMinutes
                    )
                }
                restrictedApps = newBlocked
                appBudgets = newBudgets
                blockedAppGroupIds = updatedGroupIds
                showRestrictedAppsDialog = false
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
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.9f)
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
                                text = if (isDefault) "Baseline actions when no other mode is active" else "Custom modes take priority over the Default mode",
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
                        // Section: Mode Status Toggle
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.background),
                            border = BorderStroke(1.dp, theme.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isModeEnabled) "Mode Enabled (Active)" else "Mode Disabled",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isModeEnabled) theme.primary else theme.textSecondary
                                    )
                                    Text(
                                        text = if (isModeEnabled) "Evaluated automatically during scheduled hours" else "Temporarily paused without deleting",
                                        fontSize = 11.5.sp,
                                        color = theme.textSecondary
                                    )
                                }
                                Switch(
                                    checked = isModeEnabled,
                                    onCheckedChange = { isModeEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = theme.onPrimary,
                                        checkedTrackColor = theme.primary
                                    )
                                )
                            }
                        }

                        // Section: Schedule
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.background),
                            border = BorderStroke(1.dp, theme.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "SCHEDULE & TIMING (PRIORITY WINDOW)",
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

                            // 1. Auto-Open App
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Text("Auto-Open App", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    }
                                    Switch(
                                        checked = autoLaunchAppEnabled,
                                        onCheckedChange = {
                                            autoLaunchAppEnabled = it
                                            if (it && autoLaunchPackage.isBlank()) {
                                                showSingleAppPicker = true
                                            }
                                        }
                                    )
                                }

                                if (autoLaunchAppEnabled) {
                                    if (autoLaunchPackage.isNotBlank()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(theme.surfaceVariant)
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = autoLaunchAppName.ifBlank { autoLaunchPackage },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = theme.textPrimary
                                                )
                                                Text(
                                                    text = autoLaunchPackage,
                                                    fontSize = 11.sp,
                                                    color = theme.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(onClick = { showSingleAppPicker = true }) {
                                                    Text("Change", fontSize = 12.sp, color = theme.primary, fontWeight = FontWeight.Bold)
                                                }
                                                IconButton(onClick = {
                                                    autoLaunchPackage = ""
                                                    autoLaunchAppName = ""
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { showSingleAppPicker = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Choose App to Auto-Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Divider(color = theme.border, thickness = 0.8.dp)

                            // 2. Do Not Disturb
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

                                if (dndEnabled && !permState.dndEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x1AEF4444))
                                            .border(1.dp, Color(0x44EF4444), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Notification Policy Access is required to automate DND. Without it, Do Not Disturb will remain disabled.",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFEF4444)
                                            )
                                            Button(
                                                onClick = { permRepo.openDndSettings(context) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Grant DND Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
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

                            // 3. Eye Comfort
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

                            // 4. Restricted Apps (Combines Outright Block & Daily Budget Allowance)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Apps, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Text("Restricted Apps", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    }
                                    Switch(checked = appRestrictionsEnabled, onCheckedChange = { appRestrictionsEnabled = it })
                                }

                                if (appRestrictionsEnabled && !permState.usageStatsEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x1AEF4444))
                                            .border(1.dp, Color(0x44EF4444), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "App Usage Access is required to detect and restrict foreground apps. Until granted, restricted apps will remain disabled.",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFEF4444)
                                            )
                                            Button(
                                                onClick = { permRepo.openUsageAccessSettings(context) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Grant Usage Access", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                if (appRestrictionsEnabled) {
                                    val totalRestricted = (restrictedApps + appBudgets.map { it.packageName }).distinct().size
                                    val blockedCount = restrictedApps.size
                                    val budgetCount = appBudgets.size

                                    // Apps list preview card if any restricted
                                    if (totalRestricted > 0) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // Budgeted apps list
                                            appBudgets.forEach { budget ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(theme.surfaceVariant)
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = budget.appName.ifBlank { budget.packageName },
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = theme.textPrimary
                                                        )
                                                        Text(
                                                            text = "⏳ Daily Budget: ${budget.maxMinutes} min • Dynamic Floating Timer",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFFF59E0B),
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            appBudgets = appBudgets.filterNot { it.id == budget.id }
                                                        },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }

                                            // Outright blocked apps preview summary
                                            if (blockedCount > 0) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(theme.surfaceVariant)
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("🚫", fontSize = 12.sp)
                                                        Text(
                                                            text = "$blockedCount app${if (blockedCount == 1) "" else "s"} outright blocked",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFFEF4444)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showRestrictedAppsDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (totalRestricted == 0) "Configure Restricted Apps" else "Manage Restricted Apps ($totalRestricted)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Divider(color = theme.border, thickness = 0.8.dp)

                            // 5. Screen Brightness
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

                                if (brightnessEnabled && !permState.writeSettingsEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x1AEF4444))
                                            .border(1.dp, Color(0x44EF4444), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Modify System Settings permission is needed to automate screen brightness. Until granted, brightness automation will remain disabled.",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFEF4444)
                                            )
                                            Button(
                                                onClick = { permRepo.openWriteSettings(context) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Grant Settings Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
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

                            // 6. Sticky Note
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.StickyNote2, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Text("Sticky Note", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                    }
                                    Switch(
                                        checked = stickyNoteEnabled,
                                        onCheckedChange = { stickyNoteEnabled = it }
                                    )
                                }

                                if (stickyNoteEnabled) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "Presents an upfront pop-up reminder upon entering this context. If checklist tasks are configured, you must check all boxes before dismissing the pop-up.",
                                            fontSize = 11.5.sp,
                                            color = theme.textSecondary,
                                            lineHeight = 16.sp
                                        )

                                        // Freeform Note input
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Note Content / Text (Optional)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = theme.textPrimary
                                            )

                                            OutlinedTextField(
                                                value = stickyNoteText,
                                                onValueChange = { stickyNoteText = it },
                                                placeholder = { Text("e.g., Remember to hydrate, stretch, review context notes...") },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 3,
                                                maxLines = 8,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }

                                        // Checklist section
                                        Text(
                                            text = "Checklist Tasks (Must check all to dismiss):",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = theme.textPrimary
                                        )

                                        // Add item row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = newCheckItemText,
                                                onValueChange = { newCheckItemText = it },
                                                placeholder = { Text("Add checklist task...") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val clean = newCheckItemText.trim()
                                                    if (clean.isNotBlank()) {
                                                        stickyNoteChecklist = stickyNoteChecklist + StickyNoteCheckItem(text = clean)
                                                        newCheckItemText = ""
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(theme.primary.copy(alpha = 0.16f))
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Item", tint = theme.primary)
                                            }
                                        }

                                        // Existing items
                                        if (stickyNoteChecklist.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(theme.surfaceVariant)
                                                    .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                stickyNoteChecklist.forEachIndexed { index, item ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckBoxOutlineBlank,
                                                                contentDescription = null,
                                                                tint = theme.textSecondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = item.text,
                                                                fontSize = 12.5.sp,
                                                                color = theme.textPrimary
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                stickyNoteChecklist = stickyNoteChecklist.filterIndexed { i, _ -> i != index }
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Remove Item",
                                                                tint = theme.textSecondary,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
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
                            val startParts = startTime.split(":")
                            val sHour = if (isDefault) 0 else (startParts.getOrNull(0)?.toIntOrNull() ?: 9)
                            val sMinute = if (isDefault) 0 else (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
                            val endParts = endTime.split(":")
                            val eHour = if (isDefault) 23 else (endParts.getOrNull(0)?.toIntOrNull() ?: 17)
                            val eMinute = if (isDefault) 59 else (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

                            val updated = mode.copy(
                                name = if (isDefault) "Default" else modeName.trim().ifEmpty { mode.name },
                                isEnabled = if (isDefault) true else isModeEnabled,
                                daysOfWeek = if (isDefault) setOf(1, 2, 3, 4, 5, 6, 7) else activeDays,
                                startHour = sHour,
                                startMinute = sMinute,
                                endHour = eHour,
                                endMinute = eMinute,
                                dndEnabled = dndEnabled,
                                dndMode = dndMode,
                                brightnessEnabled = brightnessEnabled,
                                brightnessPercent = brightnessLevel,
                                eyeComfortEnabled = eyeComfortEnabled,
                                eyeComfortIntensity = eyeComfortLevel,
                                appRestrictionsEnabled = appRestrictionsEnabled,
                                blockedApps = restrictedApps,
                                autoLaunchAppEnabled = autoLaunchAppEnabled,
                                autoLaunchPackage = if (autoLaunchAppEnabled) autoLaunchPackage else "",
                                autoLaunchAppName = if (autoLaunchAppEnabled) autoLaunchAppName else "",
                                stickyNoteEnabled = stickyNoteEnabled,
                                stickyNoteText = if (stickyNoteEnabled) stickyNoteText else "",
                                stickyNoteChecklist = if (stickyNoteEnabled) stickyNoteChecklist else emptyList(),
                                appBudgets = appBudgets
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
