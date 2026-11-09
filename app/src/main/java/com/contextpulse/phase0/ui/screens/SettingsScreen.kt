package com.contextpulse.phase0.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.ui.components.contextPulseLazyScrollbar
import com.contextpulse.phase0.ui.theme.AppThemeKey
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

/**
 * Root Settings Screen.
 * Presents a clean, streamlined list of settings categories where each option navigates to its own dedicated sub-page.
 */
@Composable
fun SettingsScreen(
    currentTheme: AppThemeKey,
    onThemeSelected: (AppThemeKey) -> Unit,
    onBack: () -> Unit,
    onResetConfigurations: () -> Unit = {},
    onTakeTour: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }
    var showTestAppDialog by remember { mutableStateOf(false) }

    if (showTestAppDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        com.contextpulse.phase0.ui.components.TestAppSuiteDialog(
            context = context,
            onDismiss = { showTestAppDialog = false }
        )
    }

    // Intercept back button when inside a sub-page to smoothly return to the main Settings menu
    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MAIN) {
        currentSubScreen = SettingsSubScreen.MAIN
    }

    // When on the main Settings menu, BackHandler calls onBack() to return to Home
    BackHandler(enabled = currentSubScreen == SettingsSubScreen.MAIN) {
        onBack()
    }

    AnimatedContent(
        targetState = currentSubScreen,
        transitionSpec = {
            if (targetState != SettingsSubScreen.MAIN) {
                // Forward navigation into sub-page
                (slideInHorizontally { width -> width } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
            } else {
                // Return navigation to main Settings list
                (slideInHorizontally { width -> -width } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> width } + fadeOut())
            }
        },
        label = "SettingsSubScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { subScreen ->
        when (subScreen) {
            SettingsSubScreen.MAIN -> {
                SettingsMainMenu(
                    currentTheme = currentTheme,
                    onNavigateTo = { currentSubScreen = it },
                    onOpenTestApp = { showTestAppDialog = true },
                    onBack = onBack
                )
            }
            SettingsSubScreen.COLOUR_PROFILE -> {
                ColourProfileSubScreen(
                    currentTheme = currentTheme,
                    onThemeSelected = onThemeSelected,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }
            SettingsSubScreen.NOTIFICATIONS -> {
                NotificationsSubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }
            SettingsSubScreen.BLOCKED_APP_GROUPS -> {
                BlockedAppGroupsSubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }
            SettingsSubScreen.SECURITY_LOCK -> {
                SecurityLockSubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }
            SettingsSubScreen.BACKGROUND_RELIABILITY -> {
                BackgroundReliabilitySubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }
            SettingsSubScreen.RESET_APP -> {
                ResetAppSubScreen(
                    onResetConfirmed = {
                        currentSubScreen = SettingsSubScreen.MAIN
                        onResetConfigurations()
                    },
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }
            SettingsSubScreen.ABOUT -> {
                AboutAppSubScreen(
                    onTakeTour = onTakeTour,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }
        }
    }
}

/**
 * Main Settings Navigation Menu.
 * Each item cleanly leads to a dedicated sub-page.
 */
@Composable
private fun SettingsMainMenu(
    currentTheme: AppThemeKey,
    onNavigateTo: (SettingsSubScreen) -> Unit,
    onOpenTestApp: () -> Unit,
    onBack: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Navigation Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.primary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "APPEARANCE & SYSTEM",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Settings",
                        color = theme.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Setting Item 1: Colour Profile & App Icon
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.Palette,
                iconTint = theme.primary,
                iconBg = theme.primary.copy(alpha = 0.15f),
                title = "Colour Profile & App Icon",
                subtitle = "Active: ${currentTheme.displayName} • Tap to choose theme & icon",
                onClick = { onNavigateTo(SettingsSubScreen.COLOUR_PROFILE) }
            )
        }

        // Setting Item 2: Notifications
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.Notifications,
                iconTint = theme.primary,
                iconBg = theme.primary.copy(alpha = 0.15f),
                title = "Notifications",
                subtitle = "Manage context change transitions & low beacon battery alerts",
                onClick = { onNavigateTo(SettingsSubScreen.NOTIFICATIONS) }
            )
        }

        // Setting Item 3: Blocked App Groups
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.Category,
                iconTint = theme.primary,
                iconBg = theme.primary.copy(alpha = 0.15f),
                title = "Blocked App Groups",
                subtitle = "Create and manage reusable app bundles for 1-tap blocking",
                onClick = { onNavigateTo(SettingsSubScreen.BLOCKED_APP_GROUPS) }
            )
        }

        // Setting Item 4: Security & App Lock
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.Lock,
                iconTint = theme.primary,
                iconBg = theme.primary.copy(alpha = 0.15f),
                title = "Security & App Lock",
                subtitle = "Set up PIN or biometrics to protect contexts and triggers",
                onClick = { onNavigateTo(SettingsSubScreen.SECURITY_LOCK) }
            )
        }

        // Setting Item 5: Background Reliability & OEM Guide
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.BatterySaver,
                iconTint = theme.primary,
                iconBg = theme.primary.copy(alpha = 0.15f),
                title = "Background Reliability & OEM Guide",
                subtitle = "Xiaomi HyperOS autostart, battery saver exemption & persistent execution",
                onClick = { onNavigateTo(SettingsSubScreen.BACKGROUND_RELIABILITY) }
            )
        }

        // Setting Item 6: Test App (System Diagnostics)
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.CheckCircle,
                iconTint = theme.accent,
                iconBg = theme.accent.copy(alpha = 0.15f),
                title = "Test App",
                subtitle = "Automated dry-run diagnostics for features, restrictions & permissions",
                onClick = onOpenTestApp
            )
        }

        // Setting Item 6: Reset Application (Danger Zone)
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.DeleteOutline,
                iconTint = Color(0xFFEF4444),
                iconBg = Color(0xFFEF4444).copy(alpha = 0.15f),
                title = "Reset Application",
                subtitle = "Wipe all configurations, custom room profiles & sensors",
                isDestructive = true,
                onClick = { onNavigateTo(SettingsSubScreen.RESET_APP) }
            )
        }

        // Setting Item 7: About ContextPulse
        item {
            SettingsNavigationRow(
                icon = Icons.Outlined.Info,
                iconTint = theme.textSecondary,
                iconBg = theme.surfaceVariant,
                title = "About ContextPulse",
                subtitle = "Version 1.0, system architecture & privacy policy",
                onClick = { onNavigateTo(SettingsSubScreen.ABOUT) }
            )
        }
    }
}

/**
 * Reusable menu row card for each setting destination.
 */
@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalContextPulseTheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surface)
            .border(
                width = 1.dp,
                color = if (isDestructive) Color(0xFFEF4444).copy(alpha = 0.35f) else theme.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = if (isDestructive) Color(0xFFEF4444).copy(alpha = 0.85f) else theme.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open $title",
                tint = if (isDestructive) Color(0xFFEF4444) else theme.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
