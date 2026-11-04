package com.contextpulse.phase0.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Layers
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.ui.theme.AppThemeKey
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.example.R

/**
 * Official Brand Logo:
 * Uses the exact matching app drawer icon corresponding to the selected color theme.
 * For White & Blue or White & Black, displays the white background icon.
 * For dark themes, displays the dark background icon.
 */
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
 * Top App Bar with brand logo, title, ZZZ Snooze button, and Settings button.
 */
@Composable
fun ContextPulseTopBar(
    onSettingsClick: () -> Unit = {},
    onSnoozeClick: () -> Unit = {},
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            // ZZZ Temporary Snooze Sleep Button with live ticking countdown
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (isSnoozed) theme.primary.copy(alpha = 0.18f) else theme.surface)
                    .border(
                        1.dp,
                        if (isSnoozed) theme.primary else theme.border,
                        RoundedCornerShape(13.dp)
                    )
                    .clickable { onSnoozeClick() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Zzz",
                        color = if (isSnoozed) theme.primary else theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (isSnoozed) {
                        val displayTime = if (snoozeRemainingText.isNotEmpty()) {
                            snoozeRemainingText
                        } else if (snoozeRemainingMinutes > 0) {
                            "${snoozeRemainingMinutes}m"
                        } else {
                            ""
                        }
                        if (displayTime.isNotEmpty()) {
                            Text(
                                text = displayTime,
                                color = theme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Settings Logo button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(13.dp))
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = theme.primary,
                    modifier = Modifier.size(22.dp)
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

/**
 * Docked Pill Bottom Navigation Bar with a smooth sliding transition on icon & indicator.
 * Sits securely at the bottom of the screen with content stopping cleanly above it.
 */
@Composable
fun ContextPulseBottomBar(
    selectedTab: ContextPulseTab,
    onTabSelected: (ContextPulseTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    val tabs = ContextPulseTab.values()
    val selectedIndex = selectedTab.ordinal

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
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

            // Sliding active indicator pill
            val animatedOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "NavbarSlidingIndicator"
            )

            // Animated sliding indicator background
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(theme.primary)
            )

            // Tab items row overlay
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == tab
                    val interactionSource = remember { MutableInteractionSource() }

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) theme.onPrimary else theme.textSecondary.copy(alpha = 0.8f),
                        animationSpec = tween(durationMillis = 200),
                        label = "TabContentColor"
                    )

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 0.94f,
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
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = contentColor,
                                fontSize = if (isSelected) 11.sp else 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
