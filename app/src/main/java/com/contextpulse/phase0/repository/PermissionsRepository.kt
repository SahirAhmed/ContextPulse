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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionsState(
    val locationEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val dndEnabled: Boolean = false,
    val writeSettingsEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val usageStatsEnabled: Boolean = false,
    val overlayEnabled: Boolean = false
) {
    val totalCount: Int = 8
    val enabledCount: Int
        get() = listOf(
            locationEnabled,
            bluetoothEnabled,
            notificationsEnabled,
            dndEnabled,
            writeSettingsEnabled,
            batteryOptimizationIgnored,
            usageStatsEnabled,
            overlayEnabled
        ).count { it }
}

class PermissionsRepository(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: PermissionsRepository? = null

        fun getInstance(context: Context): PermissionsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PermissionsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _state = MutableStateFlow(checkAllPermissions(context))
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    fun refreshPermissions(): PermissionsState {
        val updated = checkAllPermissions(context)
        _state.value = updated
        return updated
    }

    fun revokeAllPermissions(targetContext: Context? = null) {
        val ctx = targetContext ?: context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val perms = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                ctx.revokeSelfPermissionsOnKill(perms)
            } catch (e: Exception) {
                // Ignore API compatibility issues
            }
        }
        _state.value = PermissionsState(
            locationEnabled = false,
            bluetoothEnabled = false,
            notificationsEnabled = false,
            dndEnabled = false,
            writeSettingsEnabled = false,
            batteryOptimizationIgnored = false,
            usageStatsEnabled = false,
            overlayEnabled = false
        )
    }

    fun setLocationEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(locationEnabled = enabled)
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(bluetoothEnabled = enabled)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(notificationsEnabled = enabled)
    }

    fun setDndEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(dndEnabled = enabled)
    }

    fun setWriteSettingsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(writeSettingsEnabled = enabled)
    }

    fun setBatteryOptimizationIgnored(enabled: Boolean) {
        _state.value = _state.value.copy(batteryOptimizationIgnored = enabled)
    }

    fun setUsageStatsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(usageStatsEnabled = enabled)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(overlayEnabled = enabled)
    }

    // Permission Verification Checkers
    fun isLocationGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun isDndGranted(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun isWriteSettingsGranted(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun isUsageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
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

    fun isOverlayGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun checkAllPermissions(ctx: Context): PermissionsState {
        return PermissionsState(
            locationEnabled = isLocationGranted(),
            bluetoothEnabled = isBluetoothGranted(),
            notificationsEnabled = isNotificationsGranted(),
            dndEnabled = isDndGranted(),
            writeSettingsEnabled = isWriteSettingsGranted(),
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            usageStatsEnabled = isUsageStatsGranted(),
            overlayEnabled = isOverlayGranted()
        )
    }

    // Android System Settings Prompters
    fun openAppSettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openNotificationSettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            openAppSettings(ctx)
        }
    }

    fun openDndSettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openWriteSettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openBatteryOptimizationSettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(fallback)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun openUsageAccessSettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openOverlaySettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${ctx.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
