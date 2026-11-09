package com.contextpulse.phase0.ui.components

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.model.StickyNoteCheckItem
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

/**
 * An upfront Sticky Note Reminder dialog presented when a mode/context with Sticky Note enabled is active.
 * If checklist tasks are configured, the user must check all checkboxes before the pop-up can be dismissed.
 */
@Composable
fun StickyNoteDialog(
    contextName: String,
    modeName: String,
    noteText: String,
    checklistItems: List<StickyNoteCheckItem>,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current

    // Local checklist state tracking checked items in this session
    val checkedMap = remember(checklistItems) {
        mutableStateMapOf<String, Boolean>().apply {
            checklistItems.forEach { put(it.id, false) }
        }
    }

    val totalItems = checklistItems.size
    val completedCount = checkedMap.count { it.value }
    val allCompleted = totalItems == 0 || completedCount == totalItems

    Dialog(
        onDismissRequest = {
            if (allCompleted) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = allCompleted,
            dismissOnBackPress = allCompleted,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = theme.surface,
            border = BorderStroke(1.5.dp, theme.primary),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.StickyNote2,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "STICKY NOTE",
                                    color = theme.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = theme.accent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = "$contextName • $modeName",
                                color = theme.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (allCompleted) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Freeform Note Section
                    if (noteText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.surfaceVariant)
                                .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = noteText,
                                color = theme.textPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Checklist Section
                    if (checklistItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ACTION CHECKLIST",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = theme.tertiary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "$completedCount of $totalItems completed",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (allCompleted) Color(0xFF10B981) else theme.primary
                                )
                            }

                            if (!allCompleted) {
                                Text(
                                    text = "You must check off all items below before dismissing this reminder.",
                                    fontSize = 11.5.sp,
                                    color = theme.textSecondary,
                                    lineHeight = 16.sp
                                )
                            }

                            // Items List
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(theme.surfaceVariant)
                                    .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                checklistItems.forEach { item ->
                                    val isChecked = checkedMap[item.id] == true
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                checkedMap[item.id] = !isChecked
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checkedMap[item.id] = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = theme.primary,
                                                checkmarkColor = theme.onPrimary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.text,
                                            color = if (isChecked) theme.textSecondary else theme.textPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium,
                                            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (noteText.isBlank() && checklistItems.isEmpty()) {
                        Text(
                            text = "Sticky Note is active for this mode.",
                            fontSize = 13.sp,
                            color = theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Action Button
                Button(
                    onClick = {
                        if (allCompleted) {
                            onDismiss()
                        }
                    },
                    enabled = allCompleted,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary,
                        disabledContainerColor = theme.surfaceVariant,
                        disabledContentColor = theme.textSecondary
                    )
                ) {
                    if (allCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (checklistItems.isNotEmpty()) "All Tasks Done — Dismiss" else "Acknowledge Note",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Check all tasks to dismiss ($completedCount/$totalItems)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
