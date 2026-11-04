package com.contextpulse.phase0.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

data class TourStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val tip: String
)

@Composable
fun TourGuideOverlay(
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    var currentStepIndex by remember { mutableStateOf(0) }

    val steps = listOf(
        TourStep(
            title = "Welcome to ContextPulse",
            subtitle = "Smart automation for your physical spaces",
            description = "ContextPulse adapts your phone automatically as you move between rooms—adjusting display brightness, muting alerts, and blocking distracting apps so you stay present.",
            icon = Icons.Default.Explore,
            tip = "Tap 'Contexts' below whenever you're ready to create your first space."
        ),
        TourStep(
            title = "Contexts & Smart Modes",
            subtitle = "Tailored settings for every hour",
            description = "Customize Do Not Disturb, screen brightness, eye comfort, and app limits. You can even set timeframes so a room switches between daytime focus and evening rest.",
            icon = Icons.Default.Layers,
            tip = "Add app groups in Settings to quickly restrict multiple apps at once."
        ),
        TourStep(
            title = "Connect Physical Triggers",
            subtitle = "Automatic detection around your home",
            description = "Link your spaces to Bluetooth beacons for hands-free proximity detection, tap an NFC sticker on your desk, or scan a printed room QR code to switch instantly.",
            icon = Icons.Default.Sensors,
            tip = "ContextPulse monitors trigger connection health to ensure reliable switching."
        ),
        TourStep(
            title = "Pause Automations Anytime",
            subtitle = "Quick temporary breaks with Zzz",
            description = "Need quick access to a blocked app? Tap the Zzz button in the top bar to pause automations for 5 to 60 minutes. It ticks down live and can be resumed anytime.",
            icon = Icons.Default.Bedtime,
            tip = "Keep your setup secure with the optional 6-digit PIN in Settings."
        )
    )

    val step = steps[currentStepIndex]
    val isLast = currentStepIndex == steps.size - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = theme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.border),
            shadowElevation = 24.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header progress dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { index, _ ->
                        val isCurrent = index == currentStepIndex
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isCurrent) 22.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isCurrent) theme.primary else theme.border)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TourStepContent"
                ) { current ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(theme.primary.copy(alpha = 0.14f))
                                .border(1.dp, theme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = current.icon,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = current.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = current.subtitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = current.description,
                            fontSize = 14.sp,
                            color = theme.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.primary.copy(alpha = 0.08f))
                                .border(1.dp, theme.primary.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = current.tip,
                                    fontSize = 12.sp,
                                    color = theme.textPrimary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        TextButton(
                            onClick = { currentStepIndex-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back", color = theme.textSecondary, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Skip Tour", color = theme.textSecondary, fontWeight = FontWeight.Medium)
                        }
                    }

                    Button(
                        onClick = {
                            if (isLast) {
                                onDismiss()
                            } else {
                                currentStepIndex++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isLast) "Get Started" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
