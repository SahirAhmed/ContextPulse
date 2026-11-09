package com.contextpulse.phase0.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.contextpulse.phase0.model.StickyNoteCheckItem
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import org.json.JSONArray

/**
 * System-wide floating overlay Activity that displays an upfront Sticky Note reminder
 * directly over whatever app or screen the user is currently on when a context activates.
 *
 * If tasks are configured with checkboxes, the user must check off all boxes before
 * the overlay can be dismissed.
 */
class StickyNoteOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow displaying over lock screen / turning on screen if triggered while locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val contextName = intent.getStringExtra(EXTRA_CONTEXT_NAME).orEmpty().ifBlank { "Active Context" }
        val modeName = intent.getStringExtra(EXTRA_MODE_NAME).orEmpty().ifBlank { "Mode Reminder" }
        val noteText = intent.getStringExtra(EXTRA_NOTE_TEXT).orEmpty()
        val checklistJson = intent.getStringExtra(EXTRA_CHECKLIST_JSON).orEmpty()

        val initialItems = mutableListOf<StickyNoteCheckItem>()
        if (checklistJson.isNotBlank()) {
            try {
                val arr = JSONArray(checklistJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    initialItems.add(StickyNoteCheckItem.fromJsonObject(obj))
                }
            } catch (e: Exception) {
                android.util.Log.e("StickyNoteOverlay", "Error parsing checklist JSON", e)
            }
        }

        setContent {
            val themeRepo = remember { com.contextpulse.phase0.ui.theme.AppThemeRepository.getInstance(applicationContext) }
            val currentTheme by themeRepo.currentTheme.collectAsState()

            CompositionLocalProvider(LocalContextPulseTheme provides currentTheme) {
                StickyNoteOverlayScreen(
                    contextName = contextName,
                    modeName = modeName,
                    noteText = noteText,
                    checklistItems = initialItems,
                    onDismiss = {
                        finish()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            overridePendingTransition(0, 0)
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_CONTEXT_NAME = "extra_context_name"
        const val EXTRA_MODE_NAME = "extra_mode_name"
        const val EXTRA_NOTE_TEXT = "extra_note_text"
        const val EXTRA_CHECKLIST_JSON = "extra_checklist_json"

        fun show(
            context: Context,
            contextName: String,
            modeName: String,
            noteText: String,
            checklist: List<StickyNoteCheckItem>
        ) {
            val intent = Intent(context, StickyNoteOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_CONTEXT_NAME, contextName)
                putExtra(EXTRA_MODE_NAME, modeName)
                putExtra(EXTRA_NOTE_TEXT, noteText)
                val arr = JSONArray()
                checklist.forEach { arr.put(it.toJsonObject()) }
                putExtra(EXTRA_CHECKLIST_JSON, arr.toString())
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun StickyNoteOverlayScreen(
    contextName: String,
    modeName: String,
    noteText: String,
    checklistItems: List<StickyNoteCheckItem>,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current

    // Track checked state per checklist item
    val checkedMap = remember(checklistItems) {
        mutableStateMapOf<String, Boolean>().apply {
            checklistItems.forEach { put(it.id, false) }
        }
    }

    val totalItems = checklistItems.size
    val completedCount = checkedMap.count { it.value }
    val allCompleted = totalItems == 0 || completedCount == totalItems

    // Prevent hardware back button from closing overlay until all checklist tasks are completed
    BackHandler(enabled = !allCompleted) {
        // Block dismiss until tasks are checked
    }

    // Full screen overlay scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (allCompleted) {
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = theme.surface,
            border = BorderStroke(1.5.dp, theme.primary),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Prevent clicking dialog card from propagating to scrim */ }
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.StickyNote2,
                                contentDescription = "Sticky Note Overlay",
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = theme.textSecondary
                            )
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

                    // Checklist Section with mandatory completion
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
