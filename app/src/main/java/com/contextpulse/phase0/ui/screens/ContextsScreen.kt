package com.contextpulse.phase0.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.TriggerItem
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.repository.ContextTemplate
import com.contextpulse.phase0.repository.ContextTemplateRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.security.AppLockManager
import com.contextpulse.phase0.ui.components.AppLockDialog
import com.contextpulse.phase0.ui.components.contextPulseLazyScrollbar
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

val CONTEXT_ICONS = listOf(
    Pair("meeting_room", Icons.Default.MeetingRoom),
    Pair("business", Icons.Default.Business),
    Pair("laptop", Icons.Default.Laptop),
    Pair("school", Icons.Default.School),
    Pair("bed", Icons.Default.Bed),
    Pair("weekend", Icons.Default.Weekend),
    Pair("fitness", Icons.Default.FitnessCenter),
    Pair("coffee", Icons.Default.Coffee),
    Pair("books", Icons.Default.MenuBook)
)

fun getContextIcon(iconKey: String): ImageVector {
    if (iconKey == "fitness_center") return Icons.Default.FitnessCenter
    return CONTEXT_ICONS.find { it.first == iconKey }?.second ?: Icons.Default.MeetingRoom
}

@Composable
fun ContextsScreen(
    contexts: List<ContextItem>,
    activeContextId: String?,
    triggers: List<TriggerItem>,
    roomRepo: RoomProfileRepository,
    beaconRepo: BeaconRepository,
    lockManager: AppLockManager,
    onSelectContext: (ContextItem) -> Unit,
    onAddContext: (name: String, subtitle: String, iconName: String) -> Unit,
    onDeleteContext: (contextId: String) -> Unit,
    onEditContext: (ContextItem) -> Unit,
    onAttachTrigger: (triggerId: String, contextId: String, contextName: String) -> Unit,
    onDetachTrigger: (triggerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val isLockActive by lockManager.isLockEnabled.collectAsState()
    var showLockDialog by remember { mutableStateOf(false) }
    var pendingLockedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newContextName by remember { mutableStateOf("") }
    var selectedIconKey by remember { mutableStateOf("meeting_room") }
    var selectedTemplate by remember { mutableStateOf<ContextTemplate?>(null) }
    var templateTab by remember { mutableStateOf(0) } // 0: Templates, 1: Blank

    var contextToSaveAsTemplate by remember { mutableStateOf<ContextItem?>(null) }
    var templateSaveName by remember { mutableStateOf("") }
    var templateSaveDesc by remember { mutableStateOf("") }

    var contextPendingDelete by remember { mutableStateOf<ContextItem?>(null) }
    var attachingContext by remember { mutableStateOf<ContextItem?>(null) }

    fun runWithLockProtection(action: () -> Unit) {
        if (lockManager.isLockedForAction()) {
            pendingLockedAction = action
            showLockDialog = true
        } else {
            action()
        }
    }

    if (showLockDialog) {
        AppLockDialog(
            lockManager = lockManager,
            title = "Security Verification",
            subtitle = "Enter PIN to modify contexts",
            onSuccess = {
                pendingLockedAction?.invoke()
                pendingLockedAction = null
                showLockDialog = false
            },
            onDismiss = {
                pendingLockedAction = null
                showLockDialog = false
            }
        )
    }

    // Save as Template Dialog
    if (contextToSaveAsTemplate != null) {
        val targetContext = contextToSaveAsTemplate!!
        Dialog(
            onDismissRequest = { contextToSaveAsTemplate = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = theme.primary)
                        }
                        Column {
                            Text(
                                text = "Save as Template",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Reuse this space layout for new contexts",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = templateSaveName,
                        onValueChange = { templateSaveName = it },
                        label = { Text("Template Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = templateSaveDesc,
                        onValueChange = { templateSaveDesc = it },
                        label = { Text("Description") },
                        placeholder = { Text("e.g. Focused workspace with DND & app limits") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { contextToSaveAsTemplate = null }) {
                            Text("Cancel", color = theme.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                ContextTemplateRepository.saveCustomTemplate(
                                    context = context,
                                    name = templateSaveName.trim().ifBlank { targetContext.name },
                                    description = templateSaveDesc.trim().ifBlank { "Custom space template" },
                                    iconKey = targetContext.iconName,
                                    profile = targetContext.profile
                                )
                                Toast.makeText(
                                    context,
                                    "Saved '${templateSaveName.ifBlank { targetContext.name }}' as reusable template!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                contextToSaveAsTemplate = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) {
                            Text("Save Template", fontWeight = FontWeight.Bold, color = theme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    // Add Context Dialog
    if (showAddDialog) {
        val allTemplates = remember(showAddDialog) { ContextTemplateRepository.getAllTemplates(context) }
        val isDuplicate = remember(newContextName) {
            newContextName.isNotBlank() && (
                roomRepo.isDuplicateName(newContextName) ||
                triggers.any { it.name.trim().equals(newContextName.trim(), ignoreCase = true) }
            )
        }

        Dialog(
            onDismissRequest = { showAddDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "New Context Space",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    // Tab selector: Template vs Blank
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.surfaceVariant)
                            .padding(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (templateTab == 0) theme.primary else Color.Transparent)
                                .clickable {
                                    templateTab = 0
                                    if (selectedTemplate == null) {
                                        selectedTemplate = allTemplates.firstOrNull()
                                    }
                                    selectedTemplate?.let {
                                        newContextName = it.name
                                        selectedIconKey = it.iconKey
                                    }
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "From Template",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (templateTab == 0) theme.onPrimary else theme.textSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (templateTab == 1) theme.primary else Color.Transparent)
                                .clickable {
                                    templateTab = 1
                                    selectedTemplate = null
                                    newContextName = ""
                                    selectedIconKey = "meeting_room"
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Blank Context",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (templateTab == 1) theme.onPrimary else theme.textSecondary
                            )
                        }
                    }

                    if (templateTab == 0) {
                        Text(
                            text = "Choose a starting point with pre-configured modes and settings:",
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        // Template selection row
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                        ) {
                            items(allTemplates) { t ->
                                val isSel = selectedTemplate?.id == t.id
                                Box(
                                    modifier = Modifier
                                        .width(135.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) theme.primary.copy(alpha = 0.12f) else theme.background)
                                        .border(
                                            width = if (isSel) 1.8.dp else 1.dp,
                                            color = if (isSel) theme.primary else theme.border,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectedTemplate = t
                                            newContextName = t.name
                                            selectedIconKey = t.iconKey
                                        }
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = getContextIcon(t.iconKey),
                                                contentDescription = null,
                                                tint = if (isSel) theme.primary else theme.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            if (!t.isSystemTemplate) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(theme.primary.copy(alpha = 0.15f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text("CUSTOM", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = theme.primary)
                                                }
                                            }
                                        }
                                        Text(
                                            text = t.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) theme.primary else theme.textPrimary
                                        )
                                        Text(
                                            text = t.description,
                                            fontSize = 10.5.sp,
                                            color = theme.textSecondary,
                                            maxLines = 2,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newContextName,
                        onValueChange = { newContextName = it },
                        label = { Text("Context Name") },
                        placeholder = { Text("e.g. Work, Gym, Home Office") },
                        singleLine = true,
                        isError = isDuplicate,
                        supportingText = {
                            if (isDuplicate) {
                                Text("A context or trigger with this name already exists", color = Color(0xFFE53935))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Icon selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CONTEXT_ICONS.take(7).forEach { (key, icon) ->
                            val isSel = selectedIconKey == key
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSel) theme.primary else theme.background)
                                    .border(1.dp, if (isSel) theme.primary else theme.border, CircleShape)
                                    .clickable { selectedIconKey = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSel) theme.onPrimary else theme.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel", color = theme.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newContextName.isNotBlank() && !isDuplicate) {
                                    val name = newContextName.trim()
                                    showAddDialog = false
                                    if (templateTab == 0 && selectedTemplate != null) {
                                        roomRepo.createRoomWithProfile(
                                            name = name,
                                            subtitle = "",
                                            iconName = selectedIconKey,
                                            profile = selectedTemplate!!.profile
                                        )
                                        Toast.makeText(context, "Created '$name' from ${selectedTemplate!!.name} template", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onAddContext(name, "", selectedIconKey)
                                        Toast.makeText(context, "Created '$name' space", Toast.LENGTH_SHORT).show()
                                    }
                                    newContextName = ""
                                }
                            },
                            enabled = newContextName.isNotBlank() && !isDuplicate,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            Text("Create Context", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (contextPendingDelete != null) {
        val c = contextPendingDelete!!
        AlertDialog(
            onDismissRequest = { contextPendingDelete = null },
            title = { Text("Delete Context?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${c.name}' and all its modes? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteContext(c.id)
                        contextPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { contextPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Attach Trigger Dialog
    if (attachingContext != null) {
        val targetContext = attachingContext!!
        val assignedToTarget = triggers.filter {
            (it.assignedRoomId.isNotBlank() && it.assignedRoomId == targetContext.id) ||
                it.assignedRoomName.equals(targetContext.name, ignoreCase = true)
        }
        val lockedType = assignedToTarget.firstOrNull { it.type != TriggerType.UNCONFIGURED }?.type
        val availableTriggers = triggers.filter { it.assignedRoomId.isBlank() && it.assignedRoomName.isBlank() }

        Dialog(
            onDismissRequest = { attachingContext = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Assign Trigger to ${targetContext.name}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    if (lockedType != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.1f))
                                .border(1.dp, theme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Locked to ${lockedType.getDisplayName()} triggers. Unpair existing triggers to switch trigger types.",
                                fontSize = 12.sp,
                                color = theme.primary,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Select the first trigger type for this context (BLE Beacon, QR Code, or NFC Tag). Additional triggers will match this type.",
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    if (availableTriggers.isEmpty()) {
                        val hasAssignedTriggers = triggers.any { it.assignedRoomId.isNotBlank() || !it.assignedRoomName.isNullOrBlank() }
                        Text(
                            text = if (hasAssignedTriggers) {
                                "All existing triggers are currently assigned to other contexts. To attach a trigger to ${targetContext.name}, detach it from its current context first."
                            } else {
                                "No triggers found. Add a trigger first from the Triggers tab."
                            },
                            fontSize = 13.sp,
                            color = theme.textSecondary,
                            lineHeight = 18.sp
                        )
                    } else {
                        val sortedTriggers = availableTriggers.sortedByDescending { trig ->
                            lockedType == null || trig.type == TriggerType.UNCONFIGURED || trig.type == lockedType
                        }
                        sortedTriggers.forEach { trig ->
                            val isCompatible = lockedType == null || trig.type == TriggerType.UNCONFIGURED || trig.type == lockedType
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (isCompatible) 1f else 0.45f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.background)
                                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                    .then(
                                        if (isCompatible) {
                                            Modifier.clickable {
                                                onAttachTrigger(trig.id, targetContext.id, targetContext.name)
                                                attachingContext = null
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = when (trig.type) {
                                            TriggerType.BLE_BEACON -> Icons.Outlined.Bluetooth
                                            TriggerType.NFC_TAG -> Icons.Default.Nfc
                                            TriggerType.QR_CODE -> Icons.Default.QrCode2
                                            else -> Icons.Default.Sensors
                                        },
                                        contentDescription = null,
                                        tint = if (isCompatible) theme.primary else theme.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = trig.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )
                                        Text(
                                            text = if (isCompatible) {
                                                trig.type.getDisplayName()
                                            } else {
                                                "${trig.type.getDisplayName()} • Incompatible (${lockedType?.getDisplayName()} only)"
                                            },
                                            fontSize = 11.5.sp,
                                            color = if (isCompatible) theme.textSecondary else Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { attachingContext = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close", color = theme.textSecondary)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWide = maxWidth >= 640.dp

        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 1080.dp)
                .fillMaxSize()
                .contextPulseLazyScrollbar(listState)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Text(
                text = "YOUR CONTEXTS & SPACES",
                color = theme.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contexts",
                    color = theme.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Button(
                    onClick = {
                        runWithLockProtection {
                            val allTemplates = ContextTemplateRepository.getAllTemplates(context)
                            selectedTemplate = allTemplates.firstOrNull()
                            newContextName = selectedTemplate?.name ?: ""
                            selectedIconKey = selectedTemplate?.iconKey ?: "meeting_room"
                            templateTab = 0
                            showAddDialog = true
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Context",
                        tint = theme.onPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Context",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Each context defines how your device responds to physical spaces through adaptive modes.",
                color = theme.textSecondary,
                fontSize = 13.5.sp
            )
        }

        if (contexts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(20.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = "No Contexts Configured",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Tap 'Add Context' above to set up your first smart room or focus context.",
                            fontSize = 13.sp,
                            color = theme.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (isWide) {
            items(contexts.chunked(2), key = { row -> row.first().id }) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowItems.forEach { contextItem ->
                        Box(modifier = Modifier.weight(1f)) {
                            val itemIndex = contexts.indexOf(contextItem)
                            val assignedTriggers = triggers.filter {
                                (it.assignedRoomId.isNotBlank() && it.assignedRoomId == contextItem.id) ||
                                    it.assignedRoomName.equals(contextItem.name, ignoreCase = true)
                            }
                            ContextSpaceCard(
                                contextItem = contextItem,
                                isActive = contextItem.id == activeContextId,
                                itemIndex = itemIndex,
                                totalCount = contexts.size,
                                assignedTriggers = assignedTriggers,
                                onMoveUp = {
                                    runWithLockProtection {
                                        if (itemIndex > 0) roomRepo.moveRoom(itemIndex, itemIndex - 1)
                                    }
                                },
                                onMoveDown = {
                                    runWithLockProtection {
                                        if (itemIndex < contexts.size - 1) roomRepo.moveRoom(itemIndex, itemIndex + 1)
                                    }
                                },
                                onSaveAsTemplate = {
                                    templateSaveName = contextItem.name
                                    templateSaveDesc = "${contextItem.profile.modes.size} modes configured"
                                    contextToSaveAsTemplate = contextItem
                                },
                                onDelete = {
                                    runWithLockProtection { contextPendingDelete = contextItem }
                                },
                                onPairTrigger = {
                                    runWithLockProtection { attachingContext = contextItem }
                                },
                                onDetachTrigger = onDetachTrigger,
                                onSelectContext = { onSelectContext(contextItem) },
                                onEditContext = {
                                    runWithLockProtection { onEditContext(contextItem) }
                                }
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(contexts, key = { it.id }) { contextItem ->
                val itemIndex = contexts.indexOf(contextItem)
                val assignedTriggers = triggers.filter {
                    (it.assignedRoomId.isNotBlank() && it.assignedRoomId == contextItem.id) ||
                        it.assignedRoomName.equals(contextItem.name, ignoreCase = true)
                }
                ContextSpaceCard(
                    contextItem = contextItem,
                    isActive = contextItem.id == activeContextId,
                    itemIndex = itemIndex,
                    totalCount = contexts.size,
                    assignedTriggers = assignedTriggers,
                    onMoveUp = {
                        runWithLockProtection {
                            if (itemIndex > 0) roomRepo.moveRoom(itemIndex, itemIndex - 1)
                        }
                    },
                    onMoveDown = {
                        runWithLockProtection {
                            if (itemIndex < contexts.size - 1) roomRepo.moveRoom(itemIndex, itemIndex + 1)
                        }
                    },
                    onSaveAsTemplate = {
                        templateSaveName = contextItem.name
                        templateSaveDesc = "${contextItem.profile.modes.size} modes configured"
                        contextToSaveAsTemplate = contextItem
                    },
                    onDelete = {
                        runWithLockProtection { contextPendingDelete = contextItem }
                    },
                    onPairTrigger = {
                        runWithLockProtection { attachingContext = contextItem }
                    },
                    onDetachTrigger = onDetachTrigger,
                    onSelectContext = { onSelectContext(contextItem) },
                    onEditContext = {
                        runWithLockProtection { onEditContext(contextItem) }
                    }
                )
            }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextSpaceCard(
    contextItem: ContextItem,
    isActive: Boolean,
    itemIndex: Int,
    totalCount: Int,
    assignedTriggers: List<TriggerItem>,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    onDelete: () -> Unit,
    onPairTrigger: () -> Unit,
    onDetachTrigger: (String) -> Unit,
    onSelectContext: () -> Unit,
    onEditContext: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val modesCount = contextItem.profile.modes.size
    val activeMode = contextItem.profile.resolveActiveMode()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) theme.primary.copy(alpha = 0.08f) else theme.surface
        ),
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) theme.primary else theme.border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getContextIcon(contextItem.iconName),
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = contextItem.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.primary)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = theme.onPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = itemIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = if (itemIndex > 0) theme.primary else theme.border,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onMoveDown,
                        enabled = itemIndex < totalCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = if (itemIndex < totalCount - 1) theme.primary else theme.border,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onSaveAsTemplate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "Save as Template",
                            tint = theme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete Context",
                            tint = theme.textSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.background)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "$modesCount Mode${if (modesCount > 1) "s" else ""}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Current Mode: ${activeMode.name}",
                            fontSize = 11.5.sp,
                            color = theme.primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (activeMode.autoLaunchAppEnabled && activeMode.autoLaunchPackage.isNotBlank()) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = "Auto-Open App", tint = theme.primary, modifier = Modifier.size(16.dp))
                        }
                        if (activeMode.dndEnabled) {
                            Icon(Icons.Default.DoNotDisturb, contentDescription = "DND", tint = theme.primary, modifier = Modifier.size(16.dp))
                        }
                        if (activeMode.eyeComfortEnabled) {
                            Icon(Icons.Default.NightlightRound, contentDescription = "Eye Comfort", tint = theme.primary, modifier = Modifier.size(16.dp))
                        }
                        if (activeMode.appRestrictionsEnabled && activeMode.totalRestrictedCount > 0) {
                            Icon(Icons.Default.Apps, contentDescription = "Restricted Apps", tint = theme.primary, modifier = Modifier.size(16.dp))
                        }
                        if (activeMode.brightnessEnabled) {
                            Icon(Icons.Default.BrightnessMedium, contentDescription = "Brightness", tint = theme.primary, modifier = Modifier.size(16.dp))
                        }
                        if (activeMode.stickyNoteEnabled && (activeMode.stickyNoteText.isNotBlank() || activeMode.stickyNoteChecklist.isNotEmpty())) {
                            Icon(Icons.Default.StickyNote2, contentDescription = "Sticky Note", tint = theme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paired Triggers (${assignedTriggers.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textSecondary
                )

                TextButton(
                    onClick = onPairTrigger,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+ Pair Trigger", fontSize = 12.sp, color = theme.primary, fontWeight = FontWeight.Bold)
                }
            }

            if (assignedTriggers.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    assignedTriggers.forEach { trg ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.background)
                                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(trg.name, fontSize = 11.sp, color = theme.textPrimary, fontWeight = FontWeight.Medium)
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unpair",
                                    tint = theme.textSecondary,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable { onDetachTrigger(trg.id) }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectContext,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(40.dp),
                    border = BorderStroke(1.dp, if (isActive) theme.primary else theme.border)
                ) {
                    Text(
                        text = if (isActive) "Active Space" else "Activate Now",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) theme.primary else theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onEditContext,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    )
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Edit Context",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// Backward compatibility alias
@Composable
fun ProfilesScreen(
    rooms: List<ContextItem>,
    activeRoomId: String?,
    beacons: List<TriggerItem>,
    onSelectRoom: (ContextItem) -> Unit,
    onAddRoom: (name: String, subtitle: String, iconName: String) -> Unit,
    onDeleteRoom: (roomId: String) -> Unit,
    onEditProfile: (ContextItem) -> Unit,
    onAttachBeacon: (beaconId: String, roomName: String) -> Unit,
    onDetachBeacon: (beaconId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val roomRepo = remember { RoomProfileRepository.getInstance(context) }
    val beaconRepo = remember { BeaconRepository.getInstance(context) }
    val lockManager = remember { AppLockManager.getInstance(context) }

    ContextsScreen(
        contexts = rooms,
        activeContextId = activeRoomId,
        triggers = beacons,
        roomRepo = roomRepo,
        beaconRepo = beaconRepo,
        lockManager = lockManager,
        onSelectContext = onSelectRoom,
        onAddContext = onAddRoom,
        onDeleteContext = onDeleteRoom,
        onEditContext = onEditProfile,
        onAttachTrigger = { trigId, ctxId, ctxName -> onAttachBeacon(trigId, ctxName) },
        onDetachTrigger = onDetachBeacon,
        modifier = modifier
    )
}
