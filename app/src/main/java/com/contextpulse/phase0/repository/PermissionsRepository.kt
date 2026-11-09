package com.contextpulse.phase0.repository

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.model.CapabilityItem
import com.contextpulse.phase0.model.PermissionActionType
import com.contextpulse.phase0.system.AppInterceptorAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionsState(
    val locationEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val dndEnabled: Boolean = false,
    val writeSettingsEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val usageStatsEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false
) {
    val enabledCount: Int
        get() = listOf(
            locationEnabled,
            bluetoothEnabled,
            notificationsEnabled,
            biometricEnabled,
            dndEnabled,
            writeSettingsEnabled,
            batteryOptimizationIgnored,
            usageStatsEnabled,
            overlayEnabled
        ).count { it }

    val totalCount: Int = 9
}

class PermissionsRepository(private val context: Context) {

    private val _state = MutableStateFlow(getSnapshotState())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    fun getSnapshotState(): PermissionsState {
        return PermissionsState(
            locationEnabled = isLocationPermissionGranted(),
            bluetoothEnabled = isBlePermissionsGranted(),
            notificationsEnabled = isNotificationPermissionGranted(),
            biometricEnabled = isBiometricReady(),
            dndEnabled = isDndAccessGranted(),
            writeSettingsEnabled = isWriteSettingsGranted(),
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            usageStatsEnabled = isUsageStatsGranted(),
            overlayEnabled = isOverlayPermissionGranted(),
            accessibilityEnabled = isAccessibilityServiceEnabled()
        )
    }

    fun refreshPermissions() {
        _state.value = getSnapshotState()
    }

    fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDndAccessGranted(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun isDndGranted(): Boolean = isDndAccessGranted()

    fun isWriteSettingsGranted(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isUsageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return AppInterceptorAccessibilityService.isServiceEnabled(context)
    }

    fun isBluetoothPermissionOnlyGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBlePermissionsGranted(): Boolean {
        return isBluetoothPermissionOnlyGranted()
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.areNotificationsEnabled()
        }
    }

    fun isBiometricReady(): Boolean {
        val hasEnrolled = com.contextpulse.phase0.security.BiometricAuthHelper.hasEnrolledBiometrics(context)
        val isEnabled = com.contextpulse.phase0.security.AppLockManager.getInstance(context).isBiometricEnabled.value
        return hasEnrolled && isEnabled
    }

    fun isNfcReady(): Boolean {
        return try {
            val adapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
            adapter != null && adapter.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    // Navigation Intent Helpers to take user directly to the relevant Android Settings screens
    fun openAppSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openNotificationSettings(ctx: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", ctx.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        ctx.startActivity(intent)
    }

    fun openDndSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openWriteSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openBatteryOptimizationSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openUsageAccessSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openOverlaySettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun revokeAllPermissions() {
        // Safe placeholder
    }

    fun getAllCapabilities(): List<CapabilityItem> {
        return listOf(
            CapabilityItem(
                id = "cap_dnd",
                title = "Do Not Disturb Policy Access",
                description = "Feature Enabled: Allows ContextPulse to automatically silence calls and alerts when an active Context Mode has Do Not Disturb enabled.",
                isGranted = isDndAccessGranted(),
                actionType = PermissionActionType.DND_POLICY
            ),
            CapabilityItem(
                id = "cap_write_settings",
                title = "Modify System Settings",
                description = "Feature Enabled: Allows ContextPulse to adjust Screen Brightness and apply warm Eye Comfort Shield filtering for each Context Mode.",
                isGranted = isWriteSettingsGranted(),
                actionType = PermissionActionType.WRITE_SETTINGS
            ),
            CapabilityItem(
                id = "cap_accessibility",
                title = "App Blocker Accessibility Service",
                description = "Feature Enabled: Allows ContextPulse to detect when you open a restricted app or Blocked App Group in real time and immediately display the focus shield.",
                isGranted = isAccessibilityServiceEnabled(),
                actionType = PermissionActionType.ACCESSIBILITY_SERVICE
            ),
            CapabilityItem(
                id = "cap_usage_stats",
                title = "Usage Access Permission",
                description = "Feature Enabled: Provides fallback foreground app detection to support Blocked Apps when Accessibility is unavailable.",
                isGranted = isUsageStatsGranted(),
                actionType = PermissionActionType.USAGE_ACCESS
            ),
            CapabilityItem(
                id = "cap_overlay",
                title = "Display Over Other Apps",
                description = "Feature Enabled: Allows ContextPulse to display the full-screen focus blocker overlay over restricted apps during active Modes.",
                isGranted = isOverlayPermissionGranted(),
                actionType = PermissionActionType.OVERLAY_PERMISSION
            ),
            CapabilityItem(
                id = "cap_ble",
                title = "Nearby Devices & Fine Location",
                description = "Feature Enabled: Allows ContextPulse to scan for Bluetooth Low Energy (BLE) Beacons in the background and automatically switch Contexts based on room proximity.",
                isGranted = isBlePermissionsGranted(),
                actionType = PermissionActionType.RUNTIME_BLE_AND_NOTIFICATIONS
            ),
            CapabilityItem(
                id = "cap_notifications",
                title = "Post Notifications (Context & Battery Alerts)",
                description = "Feature Enabled: Allows ContextPulse to send device notifications when your Context changes (e.g., 'Context has changed from Bedroom to Office') and alert you when a BLE Beacon battery is low.",
                isGranted = isNotificationPermissionGranted(),
                actionType = PermissionActionType.RUNTIME_BLE_AND_NOTIFICATIONS
            ),
            CapabilityItem(
                id = "cap_biometric",
                title = "Biometric & Fingerprint Access",
                description = "Feature Enabled: Allows you to unlock ContextPulse, snooze automations, and authorize edits using your fingerprint alongside your 6-digit PIN fallback.",
                isGranted = isBiometricReady(),
                actionType = PermissionActionType.RUNTIME_BLE_AND_NOTIFICATIONS
            ),
            CapabilityItem(
                id = "cap_nfc",
                title = "NFC Hardware Access",
                description = "Feature Enabled: Allows ContextPulse to read physical NFC tags when tapped so your device immediately switches to the linked Context.",
                isGranted = isNfcReady(),
                actionType = PermissionActionType.RUNTIME_BLE_AND_NOTIFICATIONS
            )
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: PermissionsRepository? = null

        fun getInstance(context: Context): PermissionsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PermissionsRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
