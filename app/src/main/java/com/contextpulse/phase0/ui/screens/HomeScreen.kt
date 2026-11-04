package com.contextpulse.phase0.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.ProfileActivationLog
import com.contextpulse.phase0.model.TriggerItem
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.system.SnoozeManager
import com.contextpulse.phase0.ui.components.contextPulseScrollbar
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    activeRoom: ContextItem?,
    profileCount: Int,
    beacons: List<TriggerItem>,
    activationLogs: List<ProfileActivationLog>,
    onActiveProfileClick: () -> Unit,
    onBeaconsCardClick: () -> Unit,
    onProfilesCardClick: () -> Unit,
    onViewMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val formattedDate = SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date())
    var expandedLogIds by remember { mutableStateOf(setOf<String>()) }

    val snoozeManager = remember { SnoozeManager.getInstance(context) }
    val snoozeEndTime by snoozeManager.snoozeEndTime.collectAsState()
    var currentRemainingSeconds by remember { mutableStateOf(snoozeManager.getRemainingSeconds()) }
    LaunchedEffect(snoozeEndTime) {
        while (snoozeManager.isSnoozeActive()) {
            currentRemainingSeconds = snoozeManager.getRemainingSeconds()
            kotlinx.coroutines.delay(1000L)
        }
        currentRemainingSeconds = 0L
    }
    val isSnoozed = currentRemainingSeconds > 0
    val remainingSnoozeText = remember(currentRemainingSeconds) {
        snoozeManager.getFormattedRemainingDetailed()
    }

    // Find if the active room has an attached trigger
    val attachedTrigger = remember(activeRoom, beacons) {
        if (activeRoom != null) {
            beacons.find { it.assignedRoomName?.equals(activeRoom.name, ignoreCase = true) == true }
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .contextPulseScrollbar(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        // Date Pre-header in Title Case / Camel Caps
        Text(
            text = formattedDate,
            color = theme.accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Headline in Camel Caps
        Text(
            text = "Your World, In Context",
            color = theme.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Snooze / Pause Alert Banner with live ticking seconds if active
        if (isSnoozed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                    .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
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
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Zzz",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF59E0B)
                            )
                        }

                        Column {
                            Text(
                                text = "Automations Paused",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "$remainingSnoozeText Remaining",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    Button(
                        onClick = { snoozeManager.cancelSnooze() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Resume Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Hero Active Profile Card with Camel Caps
        val isRoomActive = activeRoom != null
        val activeMode = activeRoom?.profile?.resolveActiveMode()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(if (isRoomActive) theme.primary else theme.surface)
                .border(if (isRoomActive) 0.dp else 1.dp, theme.border, RoundedCornerShape(26.dp))
                .clickable { onActiveProfileClick() }
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active indicator tag in Camel Caps
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                if (isRoomActive) Color(0x28000000) else theme.surfaceVariant,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        if (isRoomActive) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x4D10B981)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B))
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = if (isRoomActive) "Active Environment" else "No Active Environment",
                            color = if (isRoomActive) theme.onPrimary else theme.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View Context",
                        tint = if (isRoomActive) theme.onPrimary.copy(alpha = 0.85f) else theme.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                val roomTitle = activeRoom?.name ?: "No Active Context"
                Text(
                    text = roomTitle,
                    color = if (isRoomActive) theme.onPrimary else theme.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                val subtitleText = when {
                    isRoomActive && activeMode != null -> "Mode: ${activeMode.name} • Actively Orchestrating Settings"
                    profileCount > 0 -> "Tap To Choose Or Activate A Context Profile"
                    else -> "No Context Profiles Configured. Tap To Create One."
                }

                Text(
                    text = subtitleText,
                    color = if (isRoomActive) theme.onPrimary.copy(alpha = 0.85f) else theme.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Active features row (DND, Brightness, Blocked Apps, Eye Comfort)
                if (isRoomActive && activeMode != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22000000))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                val parts = mutableListOf<String>()
                                if (activeMode.dndEnabled) parts.add("DND: ${activeMode.dndMode}")
                                if (activeMode.brightnessEnabled) parts.add("Brightness: ${activeMode.brightnessLevel}%")
                                if (activeMode.appRestrictionsEnabled) parts.add("Blocked: ${activeMode.restrictedApps.size} Apps")
                                if (activeMode.eyeComfortEnabled) parts.add("Eye Comfort: On")
                                if (parts.isEmpty()) append("Default System Pass-Through")
                                else append(parts.joinToString(" • "))
                            },
                            color = theme.onPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Connectivity / Trigger Info Pill
                if (isRoomActive && attachedTrigger != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33000000))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val triggerIcon = when (attachedTrigger.type) {
                                    TriggerType.BLE_BEACON -> Icons.Outlined.Bluetooth
                                    TriggerType.NFC_TAG -> Icons.Default.Nfc
                                    TriggerType.QR_CODE -> Icons.Default.QrCode2
                                    else -> Icons.Outlined.Sensors
                                }

                                Icon(
                                    imageVector = triggerIcon,
                                    contentDescription = null,
                                    tint = theme.onPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = attachedTrigger.name,
                                        color = theme.onPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val detailLine = when (attachedTrigger.type) {
                                        TriggerType.BLE_BEACON -> "BLE Signal Active • ${attachedTrigger.batteryPercent}% Battery"
                                        TriggerType.NFC_TAG -> "NFC Chip Linked"
                                        TriggerType.QR_CODE -> "Room QR Code Active"
                                        else -> "Trigger Connected"
                                    }
                                    Text(
                                        text = detailLine,
                                        color = theme.onPrimary.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = attachedTrigger.type.getDisplayName(),
                                    color = theme.onPrimary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (isRoomActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22000000))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.TouchApp,
                                contentDescription = null,
                                tint = theme.onPrimary.copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Manual Activation • No Physical Trigger Linked",
                                color = theme.onPrimary.copy(alpha = 0.8f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (profileCount > 0) "Standby • Proximity And Trigger Listeners Ready" else "Ready • Tap Add Context To Begin",
                                color = theme.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // "At A Glance" Section in Camel Caps
        Text(
            text = "At A Glance",
            color = theme.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Contexts Setup Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(20.dp))
                    .clickable { onProfilesCardClick() }
                    .padding(18.dp)
            ) {
                Column {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = "Contexts Configured",
                        tint = theme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = profileCount.toString(),
                        color = theme.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Contexts Configured",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Triggers Paired Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(20.dp))
                    .clickable { onBeaconsCardClick() }
                    .padding(18.dp)
            ) {
                Column {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Triggers Paired",
                        tint = theme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = beacons.size.toString(),
                        color = theme.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Triggers Paired",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // "Recent Activity Logs" Section Header in Camel Caps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Activity Logs",
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            // View More Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.primary.copy(alpha = 0.15f))
                    .border(1.dp, theme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onViewMoreClick() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View More",
                        color = theme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Profile Activation Logs Container Box (shows latest 3 logs)
        val recentLogs = remember(activationLogs) { activationLogs.take(3) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(theme.surface)
                .border(1.dp, theme.border, RoundedCornerShape(20.dp))
                .padding(vertical = 4.dp)
        ) {
            if (recentLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Context Activations Recorded Yet",
                        color = theme.textSecondary.copy(alpha = 0.7f),
                        fontSize = 13.5.sp
                    )
                }
            } else {
                Column {
                    recentLogs.forEachIndexed { index, log ->
                        val isExpanded = expandedLogIds.contains(log.id)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedLogIds = if (isExpanded) {
                                        expandedLogIds - log.id
                                    } else {
                                        expandedLogIds + log.id
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(theme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val icon = when (log.triggerSource) {
                                        TriggerSource.BLE_BEACON -> Icons.Default.Sensors
                                        TriggerSource.NFC_TAG -> Icons.Default.Nfc
                                        TriggerSource.QR_CODE -> Icons.Default.QrCode2
                                        TriggerSource.MANUAL -> Icons.Outlined.TouchApp
                                        TriggerSource.SYSTEM_RESTORE -> Icons.Outlined.Layers
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Trigger Type",
                                        tint = theme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = log.toProfileName,
                                            color = theme.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        if (log.toProfileName.equals(activeRoom?.name, ignoreCase = true)) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(theme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = theme.primary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    val changedFromText = if (!log.fromProfileName.isNullOrBlank()) {
                                        "Changed From ${log.fromProfileName}"
                                    } else {
                                        "Initial Context Activation"
                                    }

                                    Text(
                                        text = changedFromText,
                                        color = theme.textSecondary,
                                        fontSize = 12.5.sp
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = log.getFormattedTime(),
                                        color = theme.textSecondary.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle Details",
                                        tint = if (isExpanded) theme.primary else theme.textSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Trigger Hardware: ${log.triggerSource.name.replace("_", " ")}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.primary
                                        )
                                        Text(
                                            text = log.reason,
                                            fontSize = 12.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                }
                            }

                            if (index < recentLogs.size - 1) {
                                Divider(
                                    color = theme.border.copy(alpha = 0.5f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
