package com.contextpulse.phase0.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

private data class TourStep(
    val stepNumber: Int,
    val targetTab: ContextPulseTab? = null,
    val isSettingsScreen: Boolean = false,
    val badgeTitle: String,
    val title: String,
    val description: String,
    val bulletPoints: List<String>,
    val icon: ImageVector
)

@Composable
fun TourGuideOverlay(
    onDismiss: (() -> Unit)? = null,
    onNavigateStep: ((targetTab: ContextPulseTab?, isSettings: Boolean) -> Unit)? = null,
    onFinishTour: (() -> Unit)? = null
) {
    val theme = LocalContextPulseTheme.current
    val handleFinish: () -> Unit = {
        onFinishTour?.invoke()
        onDismiss?.invoke()
        Unit
    }

    val steps = remember {
        listOf(
            TourStep(
                stepNumber = 1,
                targetTab = ContextPulseTab.HOME,
                badgeTitle = "WELCOME TO CONTEXTPULSE",
                title = "Live Context Dashboard & Snooze",
                description = "Your Home screen displays your currently active Context and its active automation chips in real time, with at-a-glance metrics and recent activity.",
                bulletPoints = listOf(
                    "Active Context Card: Live status chips for Screen Brightness, Do Not Disturb, Eye Comfort, Blocked Apps, Auto-Opens, and Sticky Notes.",
                    "At a Glance Cards: Real-time counts of your configured Contexts and Triggers with direct one-tap navigation.",
                    "Recent Context Activity: Interactive diary showing your history of context switches and transitions until cleared.",
                    "Snooze: Tap the Snooze button in the top header to temporarily pause all automations without deactivating."
                ),
                icon = Icons.Outlined.Home
            ),
            TourStep(
                stepNumber = 2,
                targetTab = ContextPulseTab.CONTEXTS,
                badgeTitle = "STEP 2 • CONTEXTS & MODES",
                title = "Default Baseline & Scheduled Modes",
                description = "Create contextual profiles for your environments (e.g., Work, Home, Gym, Sleep). Each Context features a baseline Default Mode and optional scheduled Custom Modes.",
                bulletPoints = listOf(
                    "Default Mode: Baseline automations applied whenever no custom scheduled mode is active.",
                    "Custom Modes: Automatically take priority during their configured hours and active days of the week.",
                    "6 Automations: Configure Do Not Disturb, Screen Brightness, Eye Comfort, Blocked Apps, Auto-Open App, and Sticky Notes with task checklists.",
                    "Reorder Contexts: Use visual up and down arrows to arrange contexts in your preferred order."
                ),
                icon = Icons.Outlined.Layers
            ),
            TourStep(
                stepNumber = 3,
                targetTab = ContextPulseTab.TRIGGERS,
                badgeTitle = "STEP 3 • PHYSICAL TRIGGERS",
                title = "BLE Beacons, NFC Tags & Room QR Codes",
                description = "Link physical environment hardware or printable QR codes so your device switches contexts automatically.",
                bulletPoints = listOf(
                    "BLE Beacons: Real-time room entry and exit detection with live broadcasting and battery percentage status.",
                    "NFC Tags: Tap your phone against an NFC tag or sticker to toggle the associated context.",
                    "Room QR Codes: Generate high-resolution QR codes that instantly activate connected contexts when scanned.",
                    "Reorder Triggers: Use visual arrows on trigger cards to organize your hardware list."
                ),
                icon = Icons.Outlined.Sensors
            ),
            TourStep(
                stepNumber = 4,
                targetTab = ContextPulseTab.PERMISSIONS,
                badgeTitle = "STEP 4 • SYSTEM PERMISSIONS",
                title = "Automation Permissions & Access",
                description = "ContextPulse requires specific Android system permissions to automate device settings seamlessly in the background.",
                bulletPoints = listOf(
                    "Do Not Disturb & System Settings: Allows the app to adjust DND modes and screen brightness automatically.",
                    "Usage Access & Notifications: Required to detect and block restricted apps, and send context switch alerts.",
                    "Bluetooth & Nearby Devices: Enables background scanning and detection of physical BLE beacons.",
                    "Biometric & Fingerprint: Links your device security to protect ContextPulse with App Lock."
                ),
                icon = Icons.Outlined.Security
            ),
            TourStep(
                stepNumber = 5,
                targetTab = null,
                isSettingsScreen = true,
                badgeTitle = "STEP 5 • SETTINGS & SECURITY",
                title = "Themes, Notifications & App Lock",
                description = "Tap the Settings gear in the top right to customize your experience, manage alert preferences, and protect your configuration.",
                bulletPoints = listOf(
                    "3 Colour Themes: Select between Modern Navy, Forest Emerald, and Canary Yellow across the entire app.",
                    "Context Alerts: Toggle notifications for context switches and low beacon battery warnings.",
                    "Blocked App Groups: Create custom application groups to easily block collections of apps during focus sessions.",
                    "App Lock: Protect context editing, deactivation, and settings with a 6-digit PIN and biometric fingerprint."
                ),
                icon = Icons.Outlined.Settings
            )
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps[currentIndex]

    LaunchedEffect(currentIndex) {
        onNavigateStep?.invoke(currentStep.targetTab, currentStep.isSettingsScreen)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = true, onClick = {})
            .systemBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            border = BorderStroke(1.5.dp, theme.primary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.tertiary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentStep.icon,
                                contentDescription = null,
                                tint = theme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = currentStep.badgeTitle,
                            color = theme.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.9.sp
                        )
                    }

                    IconButton(
                        onClick = handleFinish,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Skip Tour",
                            tint = theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = currentStep.title,
                    color = theme.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currentStep.description,
                    color = theme.textSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.surfaceVariant)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    currentStep.bulletPoints.forEach { point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = point,
                                color = theme.textPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.indices.forEach { idx ->
                            val isActive = idx == currentIndex
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(if (isActive) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) theme.primary else theme.border
                                    )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentIndex > 0) {
                            OutlinedButton(
                                onClick = { currentIndex -= 1 },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, theme.border),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = theme.textPrimary
                                )
                            ) {
                                Text("Back", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            TextButton(onClick = handleFinish) {
                                Text(
                                    text = "Skip",
                                    color = theme.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (currentIndex < steps.size - 1) {
                                    currentIndex += 1
                                } else {
                                    handleFinish()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            val isLast = currentIndex == steps.size - 1
                            Text(
                                text = if (isLast) "Get Started" else "Next",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isLast) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

object TourPreferences {
    private const val PREFS_NAME = "contextpulse_tour_prefs"
    private const val KEY_TOUR_DONE = "initial_tour_completed_v1"

    fun hasCompletedInitialTour(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TOUR_DONE, false)
    }

    fun setInitialTourCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TOUR_DONE, true).apply()
    }
}
