package com.contextpulse.phase0.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import com.contextpulse.phase0.ui.components.contextPulseScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.contextpulse.phase0.repository.PermissionsRepository
import com.contextpulse.phase0.repository.PermissionsState
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

@Composable
fun PermissionsScreen(
    state: PermissionsState,
    permissionsRepository: PermissionsRepository,
    onRequestLocation: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live refresh whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsRepository.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWide = maxWidth >= 640.dp

        Column(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .contextPulseScrollbar(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Spacer(modifier = Modifier.height(2.dp))

        // Pre-header
        Text(
            text = "SYSTEM ACCESS & CONTROLS",
            color = theme.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        // Headline
        Text(
            text = "Permissions",
            color = theme.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Subtitle
        Text(
            text = "Grant hardware and system access to enable BLE beacon detection, brightness control, and contextual automation.",
            color = theme.textSecondary,
            fontSize = 13.5.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Hero Card: "Automation System Status"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(theme.primary)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x33000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Automation Status",
                            tint = theme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (state.enabledCount >= state.totalCount) "Fully Configured" else "System Integration",
                            color = theme.onPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${state.enabledCount} of ${state.totalCount} automation services enabled",
                            color = theme.onPrimary.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { state.enabledCount.toFloat() / state.totalCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = theme.onPrimary,
                    trackColor = Color(0x33000000)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Category 1 Header: Essential Proximity Permissions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ESSENTIAL PROXIMITY PERMISSIONS",
                color = theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }

        val lockManager = remember { com.contextpulse.phase0.security.AppLockManager.getInstance(context) }
        val isBiometricToggleActive by lockManager.isBiometricEnabled.collectAsState()
        val hasBiometricHardware = remember {
            com.contextpulse.phase0.security.BiometricAuthHelper.canAuthenticate(context)
        }
        val hasEnrolledBiometrics = remember {
            com.contextpulse.phase0.security.BiometricAuthHelper.hasEnrolledBiometrics(context)
        }

        val essentialCards: List<@Composable (Modifier) -> Unit> = listOf(
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.LocationOn,
                    title = "Location Access",
                    subtitle = "Required by Android OS for Bluetooth Low Energy beacon scanning",
                    isGranted = state.locationEnabled,
                    onToggle = { granted ->
                        if (granted) onRequestLocation() else permissionsRepository.openAppSettings(context)
                    },
                    onOpenSettings = { permissionsRepository.openAppSettings(context) },
                    modifier = cardMod
                )
            },
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth & Nearby Devices",
                    subtitle = "Scan and receive telemetry signals from room beacons",
                    isGranted = state.bluetoothEnabled,
                    onToggle = { granted ->
                        if (granted) onRequestBluetooth() else permissionsRepository.openAppSettings(context)
                    },
                    onOpenSettings = { permissionsRepository.openAppSettings(context) },
                    modifier = cardMod
                )
            },
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "Send alerts for context changes, battery drops (<20%), and active status in status bar",
                    isGranted = state.notificationsEnabled,
                    onToggle = { granted ->
                        if (granted) onRequestNotifications() else permissionsRepository.openNotificationSettings(context)
                    },
                    onOpenSettings = { permissionsRepository.openNotificationSettings(context) },
                    modifier = cardMod
                )
            },
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.Fingerprint,
                    title = "Biometric & Fingerprint Security",
                    subtitle = if (hasEnrolledBiometrics && isBiometricToggleActive) {
                        "Fingerprint biometric unlock is enabled. Tap to open device security settings, or toggle off to use PIN only."
                    } else if (hasEnrolledBiometrics && !isBiometricToggleActive) {
                        "Fingerprint sensor is registered on device, but currently toggled off in ContextPulse. Toggle on to enable."
                    } else if (hasBiometricHardware) {
                        "Fingerprint sensor detected! Tap here to register your fingerprints in Android Settings."
                    } else {
                        "Biometric sensor detected on device. Tap to open device security settings."
                    },
                    isGranted = hasEnrolledBiometrics && isBiometricToggleActive,
                    onToggle = { enabled ->
                        if (enabled) {
                            if (hasEnrolledBiometrics) lockManager.setBiometricEnabled(true)
                            else openBiometricSettings(context)
                        } else {
                            lockManager.setBiometricEnabled(false)
                        }
                        permissionsRepository.refreshPermissions()
                    },
                    onOpenSettings = { openBiometricSettings(context) },
                    modifier = cardMod
                )
            }
        )

        if (isWide) {
            essentialCards.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { card -> card(Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            essentialCards.forEach { card -> card(Modifier.fillMaxWidth()) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category 2 Header: Android System Automation Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYSTEM AUTOMATION SETTINGS",
                color = theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }

        val automationCards: List<@Composable (Modifier) -> Unit> = listOf(
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.DoNotDisturbOn,
                    title = "Do Not Disturb (DND) Access",
                    subtitle = "Automatically toggle priority or silent DND mode when entering rooms",
                    isGranted = state.dndEnabled,
                    onToggle = { permissionsRepository.openDndSettings(context) },
                    onOpenSettings = { permissionsRepository.openDndSettings(context) },
                    modifier = cardMod
                )
            },
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.BrightnessMedium,
                    title = "Modify System Settings",
                    subtitle = "Adjust display brightness target when switching active room profiles",
                    isGranted = state.writeSettingsEnabled,
                    onToggle = { permissionsRepository.openWriteSettings(context) },
                    onOpenSettings = { permissionsRepository.openWriteSettings(context) },
                    modifier = cardMod
                )
            },
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.BatterySaver,
                    title = "Unrestricted Battery",
                    subtitle = "Prevents Android from killing BLE proximity scans in the background",
                    isGranted = state.batteryOptimizationIgnored,
                    onToggle = { permissionsRepository.openBatteryOptimizationSettings(context) },
                    onOpenSettings = { permissionsRepository.openBatteryOptimizationSettings(context) },
                    modifier = cardMod
                )
            },
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = "App Usage Access",
                    subtitle = "Detect foreground apps to enforce room distraction blocks",
                    isGranted = state.usageStatsEnabled,
                    onToggle = { permissionsRepository.openUsageAccessSettings(context) },
                    onOpenSettings = { permissionsRepository.openUsageAccessSettings(context) },
                    modifier = cardMod
                )
            },
            { cardMod ->
                DetailedPermissionCard(
                    icon = Icons.Outlined.Layers,
                    title = "Display Over Other Apps",
                    subtitle = "Display distraction barrier popup when restricted apps are launched",
                    isGranted = state.overlayEnabled,
                    onToggle = { permissionsRepository.openOverlaySettings(context) },
                    onOpenSettings = { permissionsRepository.openOverlaySettings(context) },
                    modifier = cardMod
                )
            }
        )

        if (isWide) {
            automationCards.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { card -> card(Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            automationCards.forEach { card -> card(Modifier.fillMaxWidth()) }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // OEM Autostart & Background Guidance Card (Adaptive to OEM)
        val isXiaomi = remember { com.contextpulse.phase0.system.XiaomiHyperOsHelper.isXiaomiOrHyperOs() }
        if (isXiaomi) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .clickable {
                        com.contextpulse.phase0.system.OemBackgroundHelper.openAutostartSettings(context)
                    }
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BatterySaver,
                                contentDescription = null,
                                tint = theme.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OEM Background & Autostart (Xiaomi / HyperOS)",
                                color = theme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enable Autostart & No Restrictions to keep service running",
                                color = theme.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = "Open Settings",
                            tint = theme.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                    .clickable {
                        com.contextpulse.phase0.system.OemBackgroundHelper.openBatteryOptimization(context)
                    }
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BatterySaver,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Optimization Exemption",
                                color = theme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Set to 'Unrestricted' for continuous background triggers",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = "Open Settings",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Device Scope & Automation Guarantee Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(theme.surface)
                .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "On-Device Automation",
                    tint = theme.primary,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    text = "Hardware & Automation Access: All system permissions are used strictly on-device to scan local BLE beacons, adjust display brightness, set DND modes, and enforce app distraction barriers. No telemetry or location coordinates are uploaded externally.",
                    color = theme.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
        }
    }
}

@Composable
fun DetailedPermissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(theme.surface)
            .border(
                1.dp,
                if (isGranted) theme.border else Color(0x66F59E0B),
                RoundedCornerShape(18.dp)
            )
            .clickable { onOpenSettings() }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isGranted) theme.primary else theme.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = theme.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Switch
                Switch(
                    checked = isGranted,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.onPrimary,
                        checkedTrackColor = theme.primary,
                        checkedBorderColor = theme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action / Status Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (isGranted) theme.primary.copy(alpha = 0.15f) else Color(0x1AF59E0B),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            0.5.dp,
                            if (isGranted) theme.primary.copy(alpha = 0.4f) else Color(0x66F59E0B),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isGranted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (isGranted) "ENABLED" else "REQUIRES SETUP",
                            color = if (isGranted) theme.primary else Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // "Android Settings" Shortcut
                Row(
                    modifier = Modifier
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Android Settings",
                        color = theme.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "Open Settings",
                        tint = theme.accent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

private fun openBiometricSettings(context: Context) {
    android.widget.Toast.makeText(context, "Opening Device Security & Fingerprint Settings...", android.widget.Toast.LENGTH_SHORT).show()

    val securityIntent = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(securityIntent)
        return
    } catch (_: Exception) {
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        try {
            val enrollIntent = android.content.Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(enrollIntent)
            return
        } catch (_: Exception) {
        }
    }

    try {
        val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(settingsIntent)
    } catch (_: Exception) {
    }
}
