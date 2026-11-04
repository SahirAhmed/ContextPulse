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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.contextpulse.phase0.model.BroadcastStatus
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.TriggerItem
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.security.AppLockManager
import com.contextpulse.phase0.ui.components.AppLockDialog
import com.contextpulse.phase0.ui.components.contextPulseLazyScrollbar
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

@Composable
fun TriggersScreen(
    triggers: List<TriggerItem>,
    contexts: List<ContextItem>,
    beaconRepo: BeaconRepository,
    lockManager: AppLockManager,
    onAddTriggerWithName: (name: String) -> Unit,
    onOpenConfigureTrigger: (triggerId: String) -> Unit,
    onDeleteTrigger: (triggerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val isLockActive by lockManager.isLockEnabled.collectAsState()
    var showLockDialog by remember { mutableStateOf(false) }
    var pendingLockedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newTriggerName by remember { mutableStateOf("") }
    var triggerPendingDelete by remember { mutableStateOf<TriggerItem?>(null) }

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
            subtitle = "Enter PIN to manage triggers",
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

    // Add Trigger Dialog with Name Prompt & Duplicate Check
    if (showAddDialog) {
        val isDuplicate = remember(newTriggerName) {
            newTriggerName.isNotBlank() && (
                beaconRepo.isDuplicateName(newTriggerName) ||
                contexts.any { it.name.trim().equals(newTriggerName.trim(), ignoreCase = true) }
            )
        }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "New Trigger",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Enter a unique name for this trigger sensor:",
                        fontSize = 13.sp,
                        color = theme.textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newTriggerName,
                        onValueChange = { newTriggerName = it },
                        placeholder = { Text("e.g. Office Door Beacon, Bedside NFC") },
                        singleLine = true,
                        isError = isDuplicate,
                        supportingText = {
                            if (isDuplicate) {
                                Text("A trigger or context with this name already exists", color = Color(0xFFE53935))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel", color = theme.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTriggerName.isNotBlank() && !isDuplicate) {
                                    val name = newTriggerName.trim()
                                    showAddDialog = false
                                    newTriggerName = ""
                                    onAddTriggerWithName(name)
                                }
                            },
                            enabled = newTriggerName.isNotBlank() && !isDuplicate,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            Text("Create Trigger", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (triggerPendingDelete != null) {
        val t = triggerPendingDelete!!
        AlertDialog(
            onDismissRequest = { triggerPendingDelete = null },
            title = { Text("Delete Trigger?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${t.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTrigger(t.id)
                        triggerPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { triggerPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "DETECTION HARDWARE & SIGNALS",
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
                    text = "Triggers",
                    color = theme.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Button(
                    onClick = {
                        runWithLockProtection {
                            newTriggerName = ""
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
                        contentDescription = "Add Trigger",
                        tint = theme.onPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Trigger",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Manage Bluetooth beacons, NFC chips, and room QR codes that trigger context changes.",
                color = theme.textSecondary,
                fontSize = 13.5.sp
            )
        }

        if (triggers.isEmpty()) {
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
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = "No Triggers Configured",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Tap 'Add Trigger' above to create a BLE beacon, NFC chip, or Room QR code.",
                            fontSize = 13.sp,
                            color = theme.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(triggers, key = { it.id }) { trigger ->
                val status = trigger.getBroadcastStatus()
                val statusColor = when (status) {
                    BroadcastStatus.BROADCASTING -> Color(0xFF10B981) // Green
                    BroadcastStatus.LOW_BATTERY -> Color(0xFFF59E0B)   // Amber
                    BroadcastStatus.OFFLINE -> Color(0xFFEF4444)       // Red
                }

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
                        // Header row: Icon, Name (no pencil), Status Indicator light, Delete
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
                                        imageVector = when (trigger.type) {
                                            TriggerType.BLE_BEACON -> Icons.Outlined.Bluetooth
                                            TriggerType.NFC_TAG -> Icons.Default.Nfc
                                            TriggerType.QR_CODE -> Icons.Default.QrCode2
                                            TriggerType.UNCONFIGURED -> Icons.Outlined.WarningAmber
                                        },
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = trigger.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )

                                        // Status Indicator Light
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(statusColor)
                                        )
                                    }

                                    Text(
                                        text = trigger.type.getDisplayName(),
                                        fontSize = 12.sp,
                                        color = theme.textSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val itemIndex = triggers.indexOf(trigger)
                                IconButton(
                                    onClick = {
                                        runWithLockProtection {
                                            if (itemIndex > 0) {
                                                beaconRepo.moveTrigger(itemIndex, itemIndex - 1)
                                            }
                                        }
                                    },
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
                                    onClick = {
                                        runWithLockProtection {
                                            if (itemIndex < triggers.size - 1) {
                                                beaconRepo.moveTrigger(itemIndex, itemIndex + 1)
                                            }
                                        }
                                    },
                                    enabled = itemIndex < triggers.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down",
                                        tint = if (itemIndex < triggers.size - 1) theme.primary else theme.border,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        runWithLockProtection {
                                            triggerPendingDelete = trigger
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete Trigger",
                                        tint = theme.textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Context Assignment Summary sentence
                        val isAssigned = !trigger.assignedRoomName.isNullOrBlank()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isAssigned) theme.primary.copy(alpha = 0.09f)
                                    else theme.background
                                )
                                .border(
                                    1.dp,
                                    if (isAssigned) theme.primary.copy(alpha = 0.25f)
                                    else theme.border.copy(alpha = 0.6f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isAssigned) Icons.Outlined.Layers else Icons.Outlined.LinkOff,
                                        contentDescription = null,
                                        tint = if (isAssigned) theme.primary else theme.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isAssigned) {
                                            "Assigned to ${trigger.assignedRoomName}"
                                        } else {
                                            "Currently unassigned to any context"
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isAssigned) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isAssigned) theme.textPrimary else theme.textSecondary
                                    )
                                }

                                if (isAssigned) {
                                    Text(
                                        text = "Detach",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                runWithLockProtection {
                                                    beaconRepo.detachBeaconFromRoom(trigger.id)
                                                    Toast.makeText(context, "Detached from ${trigger.assignedRoomName}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Broadcast & Hardware info banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.background)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = status.getDisplayName(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }

                            if (trigger.type == TriggerType.BLE_BEACON) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BatteryFull,
                                        contentDescription = "Battery",
                                        tint = if (trigger.batteryPercent <= 20) Color(0xFFF59E0B) else theme.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${trigger.batteryPercent}%",
                                        fontSize = 12.sp,
                                        color = if (trigger.batteryPercent <= 20) Color(0xFFF59E0B) else theme.textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Needs configuration alert or Action button
                        if (!trigger.isConfigured || trigger.type == TriggerType.UNCONFIGURED) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        runWithLockProtection {
                                            onOpenConfigureTrigger(trigger.id)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Needs configuration. Tap to configure hardware.",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }

                        // ONLY the Configure Button (Simulate/test button completely removed)
                        Button(
                            onClick = {
                                runWithLockProtection {
                                    onOpenConfigureTrigger(trigger.id)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary.copy(alpha = 0.12f),
                                contentColor = theme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Build,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Configure",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
