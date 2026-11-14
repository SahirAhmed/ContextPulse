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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.model.BroadcastStatus
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.TriggerItem
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.security.AppLockManager
import com.contextpulse.phase0.ui.components.AppLockDialog
import com.contextpulse.phase0.ui.components.contextPulseLazyScrollbar
import com.contextpulse.phase0.ui.components.BeaconPairingDialog
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.contextpulse.phase0.util.QrSheetPrinter

@Composable
fun TriggersScreen(
    triggers: List<TriggerItem>,
    contexts: List<ContextItem>,
    beaconRepo: BeaconRepository,
    lockManager: AppLockManager,
    onAddTriggerWithName: (name: String, type: TriggerType) -> Unit,
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
    var showBeaconPairingDialog by remember { mutableStateOf(false) }
    var showBeaconNoticeDialog by remember { mutableStateOf(false) }
    var showPrintQrDialog by remember { mutableStateOf(false) }
    var newTriggerName by remember { mutableStateOf("") }
    var newTriggerType by remember { mutableStateOf(TriggerType.BLE_BEACON) }
    var triggerPendingDelete by remember { mutableStateOf<TriggerItem?>(null) }

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

    val hasBtPermission = permState.bluetoothEnabled

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
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "New Trigger",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (newTriggerType != TriggerType.BLE_BEACON) {
                        Text(
                            text = "Enter a unique name for this trigger sensor:",
                            fontSize = 13.sp,
                            color = theme.textSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = newTriggerName,
                            onValueChange = { newTriggerName = it },
                            placeholder = { Text("e.g. Office Door QR, Bedside NFC") },
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
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant),
                            border = BorderStroke(1.dp, theme.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BluetoothSearching,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Make sure that you have a ContextPulse beacon to use this feature. The radar scanner will locate your beacon in immediate proximity.",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "TRIGGER TYPE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(TriggerType.BLE_BEACON, "Pair Beacon", Icons.Outlined.BluetoothSearching),
                            Triple(TriggerType.NFC_TAG, "NFC", Icons.Default.Nfc),
                            Triple(TriggerType.QR_CODE, "QR Code", Icons.Default.QrCode2)
                        ).forEach { (tType, tLabel, tIcon) ->
                            val isSel = newTriggerType == tType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) theme.primary.copy(alpha = 0.16f) else theme.surfaceVariant)
                                    .border(1.dp, if (isSel) theme.primary else theme.border, RoundedCornerShape(12.dp))
                                    .clickable {
                                        newTriggerType = tType
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = tIcon,
                                        contentDescription = null,
                                        tint = if (isSel) theme.primary else theme.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = tLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) theme.primary else theme.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showAddDialog = false
                            newTriggerType = TriggerType.BLE_BEACON
                        }) {
                            Text("Cancel", color = theme.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTriggerType == TriggerType.BLE_BEACON) {
                                    showBeaconNoticeDialog = true
                                } else if (newTriggerName.isNotBlank() && !isDuplicate) {
                                    val name = newTriggerName.trim()
                                    val chosenType = newTriggerType
                                    showAddDialog = false
                                    newTriggerName = ""
                                    newTriggerType = TriggerType.BLE_BEACON
                                    onAddTriggerWithName(name, chosenType)
                                }
                            },
                            enabled = (newTriggerType == TriggerType.BLE_BEACON) || (newTriggerName.isNotBlank() && !isDuplicate),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            Text(if (newTriggerType == TriggerType.BLE_BEACON) "Pair Beacon" else "Create Trigger", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showBeaconNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showBeaconNoticeDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BluetoothSearching,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "ContextPulse Beacon",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Make sure that you have a ContextPulse beacon to use this feature.",
                    fontSize = 14.sp,
                    color = theme.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBeaconNoticeDialog = false
                        showAddDialog = false
                        showBeaconPairingDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Proceed to Radar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBeaconNoticeDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = theme.surface
        )
    }

    if (showBeaconPairingDialog) {
        BeaconPairingDialog(
            beaconRepo = beaconRepo,
            onDismiss = { showBeaconPairingDialog = false },
            onBeaconSaved = { saved ->
                showBeaconPairingDialog = false
                Toast.makeText(context, "Beacon '${saved.name}' added. Assign it to a Context in the Contexts tab.", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showPrintQrDialog) {
        val qrTriggers = remember(triggers) { triggers.filter { it.type == TriggerType.QR_CODE } }
        PrintQrSelectionDialog(
            qrTriggers = qrTriggers,
            contexts = contexts,
            onPrintSelected = { selected ->
                showPrintQrDialog = false
                QrSheetPrinter.printQrCodesSheet(context, selected, contexts)
            },
            onDismiss = { showPrintQrDialog = false }
        )
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

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
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
                text = "DETECTION HARDWARE & SIGNALS",
                color = theme.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            val qrTriggers = remember(triggers) { triggers.filter { it.type == TriggerType.QR_CODE } }

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
                        fontSize = 13.sp
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

        if (!hasBtPermission) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Bluetooth Detection Disabled",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Beacon scanning is disabled until Bluetooth & Location permissions are granted. Triggers will not detect your physical presence.",
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            lineHeight = 16.sp
                        )
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Ignored
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Grant Permissions in Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
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
        } else if (isWide) {
            items(triggers.chunked(2), key = { row -> row.first().id }) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowItems.forEach { trigger ->
                        Box(modifier = Modifier.weight(1f)) {
                            val itemIndex = triggers.indexOf(trigger)
                            TriggerCardItem(
                                trigger = trigger,
                                itemIndex = itemIndex,
                                totalCount = triggers.size,
                                onMoveUp = {
                                    runWithLockProtection {
                                        if (itemIndex > 0) beaconRepo.moveTrigger(itemIndex, itemIndex - 1)
                                    }
                                },
                                onMoveDown = {
                                    runWithLockProtection {
                                        if (itemIndex < triggers.size - 1) beaconRepo.moveTrigger(itemIndex, itemIndex + 1)
                                    }
                                },
                                onDelete = {
                                    runWithLockProtection { triggerPendingDelete = trigger }
                                },
                                onDetach = {
                                    runWithLockProtection {
                                        beaconRepo.detachBeaconFromRoom(trigger.id)
                                        Toast.makeText(context, "Detached from ${trigger.assignedRoomName}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onConfigure = {
                                    runWithLockProtection { onOpenConfigureTrigger(trigger.id) }
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
            items(triggers, key = { it.id }) { trigger ->
                val itemIndex = triggers.indexOf(trigger)
                TriggerCardItem(
                    trigger = trigger,
                    itemIndex = itemIndex,
                    totalCount = triggers.size,
                    onMoveUp = {
                        runWithLockProtection {
                            if (itemIndex > 0) beaconRepo.moveTrigger(itemIndex, itemIndex - 1)
                        }
                    },
                    onMoveDown = {
                        runWithLockProtection {
                            if (itemIndex < triggers.size - 1) beaconRepo.moveTrigger(itemIndex, itemIndex + 1)
                        }
                    },
                    onDelete = {
                        runWithLockProtection { triggerPendingDelete = trigger }
                    },
                    onDetach = {
                        runWithLockProtection {
                            beaconRepo.detachBeaconFromRoom(trigger.id)
                            Toast.makeText(context, "Detached from ${trigger.assignedRoomName}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onConfigure = {
                        runWithLockProtection { onOpenConfigureTrigger(trigger.id) }
                    }
                )
            }
        }

        if (triggers.any { it.type == TriggerType.QR_CODE }) {
            val qrTriggers = triggers.filter { it.type == TriggerType.QR_CODE }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        showPrintQrDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = theme.primary
                    ),
                    border = BorderStroke(1.5.dp, theme.primary),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Print QR Codes",
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (qrTriggers.size == 1) "Print QR Code (${qrTriggers.first().name})" else "Print QR Codes (${qrTriggers.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun TriggerCardItem(
    trigger: TriggerItem,
    itemIndex: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onDetach: () -> Unit,
    onConfigure: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val status = trigger.getBroadcastStatus()
    val statusColor = when (status) {
        BroadcastStatus.BROADCASTING -> Color(0xFF10B981)
        BroadcastStatus.LOW_BATTERY -> Color(0xFFF59E0B)
        BroadcastStatus.OFFLINE -> Color(0xFFEF4444)
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

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = trigger.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (trigger.type == TriggerType.BLE_BEACON) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                            }
                        }

                        Text(
                            text = trigger.type.getDisplayName(),
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )
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
                        onClick = onDelete,
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
                            color = if (isAssigned) theme.textPrimary else theme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                .clickable { onDetach() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (trigger.type == TriggerType.BLE_BEACON) {
                val isRecentlySeen = (System.currentTimeMillis() - (trigger.lastSeenMillis ?: 0L)) <= 15_000L
                val estDistance = com.contextpulse.phase0.ble.BeaconParser.calculateDistance(trigger.rssi.toDouble(), -59)
                val proximityZone = when {
                    !isRecentlySeen -> "Out of Range"
                    estDistance <= 0.35 -> "Immediate (< 35cm)"
                    estDistance <= 1.5 -> "Near (< 1.5m)"
                    estDistance <= 4.0 -> "Mid (< 4m)"
                    else -> "Far (> 4m)"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.background)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SignalStrengthBars(
                            rssi = trigger.rssi,
                            isBroadcasting = isRecentlySeen,
                            tint = if (isRecentlySeen) Color(0xFF10B981) else Color(0xFFEF4444)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = if (isRecentlySeen) "${trigger.rssi} dBm" else "Offline",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecentlySeen) theme.textPrimary else theme.textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "• $proximityZone",
                                fontSize = 11.5.sp,
                                color = if (isRecentlySeen) theme.primary else theme.textSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val battery = trigger.batteryPercent
                        if (battery != null) {
                            val isLow = battery < 15
                            Icon(
                                imageVector = if (isLow) Icons.Outlined.BatteryAlert else Icons.Outlined.BatteryFull,
                                contentDescription = "Battery",
                                tint = if (isLow) Color(0xFFF59E0B) else Color(0xFF10B981),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${battery}%",
                                fontSize = 12.sp,
                                color = if (isLow) Color(0xFFF59E0B) else theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.BatteryUnknown,
                                contentDescription = "Battery telemetry unavailable",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "No TLM",
                                fontSize = 11.sp,
                                color = theme.textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (!trigger.isConfigured || trigger.type == TriggerType.UNCONFIGURED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { onConfigure() }
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

            Button(
                onClick = onConfigure,
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

/**
 * Clean 4-bar RSSI signal meter component with dynamic color mapping.
 */
@Composable
private fun SignalStrengthBars(
    rssi: Int,
    isBroadcasting: Boolean,
    tint: Color
) {
    val bars = when {
        !isBroadcasting || rssi <= -100 -> 0
        rssi >= -65 -> 4
        rssi >= -78 -> 3
        rssi >= -88 -> 2
        else -> 1
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(14.dp)
    ) {
        listOf(4.dp, 7.dp, 10.dp, 14.dp).forEachIndexed { index, height ->
            val isActive = index < bars
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isActive) tint else tint.copy(alpha = 0.22f)
                    )
            )
        }
    }
}

@Composable
private fun PrintQrSelectionDialog(
    qrTriggers: List<TriggerItem>,
    contexts: List<ContextItem>,
    onPrintSelected: (List<TriggerItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    var selectedIds by remember(qrTriggers) {
        mutableStateOf(qrTriggers.map { it.id }.toSet())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 20.dp,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "PRINT QR SHEET",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Select QR Codes to Print",
                            color = theme.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} of ${qrTriggers.size} selected",
                        fontSize = 12.5.sp,
                        color = theme.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { selectedIds = qrTriggers.map { it.id }.toSet() },
                            enabled = selectedIds.size < qrTriggers.size,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Select All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { selectedIds = emptySet() },
                            enabled = selectedIds.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp, color = theme.textSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(qrTriggers, key = { it.id }) { trigger ->
                            val isChecked = selectedIds.contains(trigger.id)
                            val linkedName = trigger.assignedRoomName.takeIf { it.isNotBlank() }
                                ?: contexts.find { it.id == trigger.assignedRoomId }?.name
                                ?: "Unattached"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isChecked) theme.primary.copy(alpha = 0.08f) else theme.background)
                                    .border(
                                        1.dp,
                                        if (isChecked) theme.primary.copy(alpha = 0.45f) else theme.border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedIds = if (isChecked) {
                                            selectedIds - trigger.id
                                        } else {
                                            selectedIds + trigger.id
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + trigger.id
                                        } else {
                                            selectedIds - trigger.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = theme.primary,
                                        checkmarkColor = theme.onPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trigger.name,
                                        color = theme.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Context: $linkedName",
                                        color = theme.textSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = if (isChecked) theme.primary else theme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = theme.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val selectedList = qrTriggers.filter { selectedIds.contains(it.id) }
                            onPrintSelected(selectedList)
                        },
                        enabled = selectedIds.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Print (${selectedIds.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
