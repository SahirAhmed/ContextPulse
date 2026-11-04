package com.contextpulse.phase0.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

@Composable
fun SnoozeDialog(
    isActive: Boolean,
    remainingMinutes: Int = 0,
    remainingTimeText: String = "",
    onStartSnooze: (Int) -> Unit,
    onCancelSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.border),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(theme.primary.copy(alpha = 0.15f))
                        .border(1.dp, theme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Snooze",
                        tint = theme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isActive) {
                    Text(
                        text = "Snooze Is Active",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "All profile automations, DND rules, and app blocks are currently suspended.",
                        fontSize = 13.5.sp,
                        color = theme.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.background)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (remainingTimeText.isNotEmpty()) "$remainingTimeText Remaining" else "$remainingMinutes Minutes Remaining",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onCancelSnooze()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume Automations",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume Automations Now", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    }
                } else {
                    Text(
                        text = "Temporarily Pause Automations",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Unblock all apps and suspend all context triggers temporarily for the chosen duration:",
                        fontSize = 13.5.sp,
                        color = theme.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    val durations = listOf(
                        Pair(5, "5 Minutes"),
                        Pair(10, "10 Minutes"),
                        Pair(30, "30 Minutes"),
                        Pair(60, "1 Hour")
                    )

                    durations.forEach { (mins, label) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.background)
                                .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                                .clickable {
                                    onStartSnooze(mins)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = theme.textSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
