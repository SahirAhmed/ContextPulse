package com.contextpulse.phase0.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.contextpulse.phase0.model.ActivationLogEntry
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.security.AppLockManager
import com.contextpulse.phase0.ui.components.AppLockDialog
import com.contextpulse.phase0.ui.components.SnoozeDialog
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    activeRoom: RoomItem?,
    allRooms: List<RoomItem>,
    triggersCount: Int = 0,
    isSnoozed: Boolean,
    snoozeRemainingSeconds: Long,
    systemDndActive: Boolean,
    systemBrightnessPercent: Int,
    recentLogs: List<ActivationLogEntry>,
    onActivateRoom: (RoomItem) -> Unit,
    onDeactivateActiveRoom: () -> Unit,
    onEditActiveRoom: (RoomItem) -> Unit,
    onStartSnooze: (Int) -> Unit,
    onCancelSnooze: () -> Unit,
    onViewAllLogs: () -> Unit,
    onStartTour: () -> Unit = {},
    onNavigateToContexts: () -> Unit = {},
    onNavigateToTriggers: () -> Unit = {},
    onOpenStickyNote: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current
    val lockManager = remember { AppLockManager.getInstance(context) }
    val isLockEnabled by lockManager.isLockEnabled.collectAsState()

    var showSnoozeModal by remember { mutableStateOf(false) }
    var pendingProtectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun runProtected(action: () -> Unit) {
        if (isLockEnabled) {
            pendingProtectedAction = action
        } else {
            action()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Snooze Active Banner
        if (isSnoozed) {
            val mins = snoozeRemainingSeconds / 60
            val secs = snoozeRemainingSeconds % 60
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.primary.copy(alpha = 0.14f)
                ),
                border = BorderStroke(1.dp, theme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PauseCircle,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Automations Paused",
                                color = theme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = String.format("Resuming in %02d:%02d", mins, secs),
                                color = theme.textPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            runProtected {
                                onCancelSnooze()
                            }
                        }
                    ) {
                        Text(
                            text = "Resume Now",
                            color = theme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Active Context Card at top (Quite big, includes short summary of active mode and active configurations)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            border = BorderStroke(
                width = if (activeRoom != null) 1.5.dp else 1.dp,
                color = if (activeRoom != null) theme.primary else theme.border
            )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSnoozed -> theme.accent
                                        activeRoom != null -> theme.primary
                                        else -> theme.textSecondary.copy(alpha = 0.5f)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isSnoozed -> "AUTOMATIONS PAUSED"
                                activeRoom != null -> "ACTIVE CONTEXT"
                                else -> "BASELINE STATE"
                            },
                            color = when {
                                isSnoozed -> theme.accent
                                activeRoom != null -> theme.primary
                                else -> theme.accent
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    if (activeRoom != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    runProtected {
                                        onEditActiveRoom(activeRoom)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Configure Active Context",
                                    tint = theme.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    runProtected {
                                        onDeactivateActiveRoom()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Deactivate",
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = activeRoom?.name ?: "No Active Context",
                    color = theme.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when {
                        isSnoozed -> "All profile automations, DND rules, and app blocks are currently suspended."
                        activeRoom != null -> "Adapting your device settings and automations for this context."
                        else -> "Your device is in its default state with baseline automations active."
                    },
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Short summary of active mode and active configurations
                val activeMode = remember(activeRoom) {
                    activeRoom?.profile?.resolveActiveMode()
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.surfaceVariant)
                        .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE MODE",
                                color = theme.accent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = activeMode?.name ?: "Default Baseline",
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = theme.border.copy(alpha = 0.5f), thickness = 0.8.dp)

                        val hasAutoLaunch = activeMode?.autoLaunchAppEnabled == true && activeMode.autoLaunchPackage.isNotBlank()
                        val hasDnd = activeMode?.dndEnabled == true
                        val hasEyeComfort = activeMode?.eyeComfortEnabled == true
                        val hasRestrictedApps = activeMode?.appRestrictionsEnabled == true && (activeMode.totalRestrictedCount > 0)
                        val hasBrightness = activeMode?.brightnessEnabled == true
                        val hasStickyNote = activeMode?.stickyNoteEnabled == true && (activeMode.stickyNoteText.isNotBlank() || activeMode.stickyNoteChecklist.isNotEmpty())

                        val hasAnyAction = hasAutoLaunch || hasDnd || hasEyeComfort || hasRestrictedApps || hasBrightness || hasStickyNote

                        if (!hasAnyAction) {
                            Text(
                                text = "No automations currently enabled",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // 1. Auto-Open App
                                if (hasAutoLaunch) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surface)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RocketLaunch,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Auto-Opens: ${activeMode!!.autoLaunchAppName.ifBlank { activeMode.autoLaunchPackage }}",
                                            fontSize = 12.sp,
                                            color = theme.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // 2. Do Not Disturb
                                if (hasDnd) {
                                    val dndLabel = when (activeMode!!.dndMode) {
                                        "PRIORITY_ONLY" -> "Priority Only"
                                        "TOTAL_SILENCE" -> "Total Silence"
                                        "ALARMS_ONLY" -> "Alarms Only"
                                        else -> activeMode.dndMode
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surface)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DoNotDisturb,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "DND Mode: $dndLabel",
                                            fontSize = 12.sp,
                                            color = theme.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // 3. Eye Comfort
                                if (hasEyeComfort) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surface)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NightlightRound,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Eye Comfort: ${activeMode!!.eyeComfortLevel}%",
                                            fontSize = 12.sp,
                                            color = theme.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // 4. Restricted Apps
                                if (hasRestrictedApps) {
                                    val blockedCount = activeMode!!.outrightBlockedCount
                                    val budgetCount = activeMode.budgetedAppsCount
                                    val totalCount = activeMode.totalRestrictedCount
                                    val desc = buildString {
                                        append("Restricted Apps: $totalCount total")
                                        val parts = mutableListOf<String>()
                                        if (blockedCount > 0) parts.add("$blockedCount blocked")
                                        if (budgetCount > 0) parts.add("$budgetCount budgeted")
                                        if (parts.isNotEmpty()) append(" (${parts.joinToString(", ")})")
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surface)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Apps,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = desc,
                                            fontSize = 12.sp,
                                            color = theme.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // 5. Screen Brightness
                                if (hasBrightness) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surface)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BrightnessMedium,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Brightness: ${activeMode!!.brightnessLevel}%",
                                            fontSize = 12.sp,
                                            color = theme.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // 6. Sticky Note
                                if (hasStickyNote) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surface)
                                            .clickable { onOpenStickyNote?.invoke() }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.StickyNote2,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val summary = buildString {
                                            append("Sticky Note: ")
                                            if (activeMode!!.stickyNoteChecklist.isNotEmpty()) {
                                                append("${activeMode.stickyNoteChecklist.size} task${if (activeMode.stickyNoteChecklist.size == 1) "" else "s"}")
                                                if (activeMode.stickyNoteText.isNotBlank()) append(" & Note")
                                            } else {
                                                append(activeMode.stickyNoteText.take(28))
                                            }
                                        }
                                        Text(
                                            text = summary,
                                            fontSize = 12.sp,
                                            color = theme.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "View",
                                            fontSize = 11.5.sp,
                                            color = theme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // At a Glance Section (Replaces quick switch as requested by user)
        Text(
            text = "AT A GLANCE",
            color = theme.tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Contexts count card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToContexts() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = BorderStroke(1.dp, theme.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${allRooms.size}",
                        color = theme.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (allRooms.size == 1) "Context Configured" else "Contexts Configured",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Triggers count card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToTriggers() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = BorderStroke(1.dp, theme.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sensors,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$triggersCount",
                        color = theme.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (triggersCount == 1) "Trigger Configured" else "Triggers Configured",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Context Activity Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT CONTEXT ACTIVITY",
                color = theme.tertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onViewAllLogs() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View All (${recentLogs.size})",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (recentLogs.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = BorderStroke(1.dp, theme.border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No context switches recorded yet.",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentLogs.take(3).forEach { entry ->
                    CompactLogRow(entry = entry)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    if (showSnoozeModal) {
        val mins = (snoozeRemainingSeconds / 60).toInt()
        val secs = (snoozeRemainingSeconds % 60).toInt()
        SnoozeDialog(
            isActive = isSnoozed,
            remainingMinutes = mins,
            remainingTimeText = "${mins}m ${secs}s",
            onStartSnooze = { durationMins ->
                onStartSnooze(durationMins)
                showSnoozeModal = false
            },
            onCancelSnooze = {
                onCancelSnooze()
                showSnoozeModal = false
            },
            onDismiss = { showSnoozeModal = false }
        )
    }

    if (pendingProtectedAction != null) {
        AppLockDialog(
            lockManager = lockManager,
            title = "App Lock Verification",
            subtitle = "Enter your 6-digit PIN or scan your fingerprint to continue",
            onSuccess = {
                val action = pendingProtectedAction
                pendingProtectedAction = null
                action?.invoke()
            },
            onDismiss = { pendingProtectedAction = null }
        )
    }
}

@Composable
private fun CompactLogRow(entry: ActivationLogEntry) {
    val theme = LocalContextPulseTheme.current
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeStr = remember(entry.timestampMillis) {
        timeFormatter.format(Date(entry.timestampMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = BorderStroke(1.dp, theme.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (entry.triggerSource) {
                            TriggerSource.BLE_BEACON -> Icons.Outlined.BluetoothSearching
                            TriggerSource.NFC, TriggerSource.NFC_TAG -> Icons.Outlined.Nfc
                            TriggerSource.QR_CODE -> Icons.Outlined.QrCode2
                            TriggerSource.MANUAL -> Icons.Outlined.TouchApp
                            TriggerSource.SYSTEM, TriggerSource.SYSTEM_RESTORE -> Icons.Outlined.SettingsSuggest
                        },
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val transitionTitle = when {
                        entry.fromRoomName != null && entry.toRoomName != null ->
                            "${entry.fromRoomName} → ${entry.toRoomName}"
                        entry.fromRoomName == null && entry.toRoomName != null ->
                            "Activated ${entry.toRoomName}"
                        entry.fromRoomName != null && entry.toRoomName == null ->
                            "Deactivated ${entry.fromRoomName}"
                        else -> "Baseline Restored"
                    }
                    Text(
                        text = transitionTitle,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${entry.triggerSource.label} • ${entry.reason}",
                        color = theme.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = timeStr,
                color = theme.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
