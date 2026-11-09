package com.contextpulse.phase0.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.ui.theme.AppThemeKey
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

@Composable
fun ContextPulseLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    theme: AppThemeKey? = null
) {
    val currentTheme = theme ?: LocalContextPulseTheme.current
    val cornerRadius = size * 0.22f
    val iconRes = currentTheme.getLauncherIconResId()
    val borderColor = if (currentTheme.isLight) Color(0xFFD0D7E3) else currentTheme.border.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "ContextPulse Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Shared Header Bar used across ContextPulse screens with brand logo, Moon Snooze button, and Tour button.
 */
@Composable
fun ContextPulseHeader(
    onHomeClick: (() -> Unit)? = null,
    onSnoozeClick: (() -> Unit)? = null,
    isSnoozed: Boolean = false,
    onStartTour: (() -> Unit)? = null
) {
    val theme = LocalContextPulseTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onHomeClick?.invoke() }
        ) {
            ContextPulseLogo(size = 38.dp)
            Text(
                text = "ContextPulse",
                color = theme.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onSnoozeClick != null) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSnoozed) theme.primary.copy(alpha = 0.18f) else theme.surface)
                        .border(
                            1.dp,
                            if (isSnoozed) theme.primary else theme.border,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSnoozeClick() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Snooze Automations",
                            tint = if (isSnoozed) theme.primary else theme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (isSnoozed) {
                            Text(
                                text = "Active",
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            if (onStartTour != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .clickable { onStartTour() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = "Interactive App Tour",
                        tint = theme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Top bar used at the top of the app in MainActivity with Settings navigation, QR scanner, and Snooze trigger.
 */
@Composable
fun ContextPulseTopBar(
    onHomeClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSnoozeClick: () -> Unit,
    onScanQrClick: () -> Unit = {},
    isSnoozed: Boolean = false,
    snoozeRemainingMinutes: Int = 0,
    snoozeRemainingText: String = ""
) {
    val theme = LocalContextPulseTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onHomeClick?.invoke() }
        ) {
            ContextPulseLogo(size = 38.dp)
            Text(
                text = "ContextPulse",
                color = theme.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Snooze button
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSnoozed) theme.primary.copy(alpha = 0.18f) else theme.surface)
                    .border(
                        1.dp,
                        if (isSnoozed) theme.primary else theme.border,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSnoozeClick() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Snooze Automations",
                        tint = if (isSnoozed) theme.primary else theme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    if (isSnoozed && snoozeRemainingText.isNotEmpty()) {
                        Text(
                            text = snoozeRemainingText,
                            color = theme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // In-app QR Code Scanner button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                    .clickable { onScanQrClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "Scan QR Code",
                    tint = theme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Settings button (Top Right)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = theme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

enum class ContextPulseTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CONTEXTS("Contexts", Icons.Outlined.Layers),
    TRIGGERS("Triggers", Icons.Default.Sensors),
    PERMISSIONS("Permissions", Icons.Outlined.Shield);

    companion object {
        val PROFILES: ContextPulseTab get() = CONTEXTS
        val SENSORS: ContextPulseTab get() = TRIGGERS
        val BEACONS: ContextPulseTab get() = TRIGGERS
    }
}

@Composable
fun ContextPulseBottomBar(
    currentTab: ContextPulseTab = ContextPulseTab.HOME,
    selectedTab: ContextPulseTab = currentTab,
    onTabSelected: (ContextPulseTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveTab = if (selectedTab != ContextPulseTab.HOME || currentTab == ContextPulseTab.HOME) selectedTab else currentTab
    val theme = LocalContextPulseTheme.current
    val tabs = remember {
        listOf(
            ContextPulseTab.HOME,
            ContextPulseTab.CONTEXTS,
            ContextPulseTab.TRIGGERS,
            ContextPulseTab.PERMISSIONS
        )
    }
    val selectedIndex = tabs.indexOf(effectiveTab).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(theme.surface)
                .border(1.dp, theme.border, RoundedCornerShape(34.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            val totalWidth = maxWidth
            val tabWidth = totalWidth / tabs.size

            val animatedOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "NavbarSlidingIndicator"
            )

            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(theme.primary)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = effectiveTab == tab
                    val interactionSource = remember { MutableInteractionSource() }

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) theme.onPrimary else theme.textSecondary.copy(alpha = 0.85f),
                        animationSpec = tween(durationMillis = 200),
                        label = "TabContentColor"
                    )

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.06f else 0.94f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "TabIconScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.scale(iconScale)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = contentColor,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
