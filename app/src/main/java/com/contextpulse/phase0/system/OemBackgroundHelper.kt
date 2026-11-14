package com.contextpulse.phase0.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Utilities for navigating OEM-specific background management, autostart,
 * and aggressive battery saver restrictions (specifically Xiaomi HyperOS / MIUI, Samsung, etc.).
 */
object OemBackgroundHelper {

    private const val TAG = "OemBackgroundHelper"

    fun isXiaomiOrHyperOs(): Boolean {
        return XiaomiHyperOsHelper.isXiaomiOrHyperOs()
    }

    fun isSamsung(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    fun isHuawei(): Boolean {
        val man = Build.MANUFACTURER.lowercase()
        return man.contains("huawei") || man.contains("honor")
    }

    /**
     * Attempts to open the device-specific Autostart / Background management screen.
     * Prioritizes Xiaomi / HyperOS / MIUI, then Honor/Huawei, Oppo/ColorOS, Vivo,
     * and falls back to standard Android Battery or App Details settings.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val intents = listOf(
            // Xiaomi / HyperOS / MIUI Autostart Management
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
            ),
            // Huawei / Honor
            Intent().setComponent(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            ),
            Intent().setComponent(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")
            ),
            // Oppo / Realme / ColorOS
            Intent().setComponent(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            ),
            Intent().setComponent(
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")
            ),
            // Vivo / iQOO
            Intent().setComponent(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            ),
            // Samsung Device Care
            Intent().setComponent(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            ),
            // Android System Battery Optimization Settings
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            // App Details Fallback
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched autostart intent: ${intent.component ?: intent.action}")
                return true
            } catch (e: Exception) {
                // Try next intent
            }
        }
        return false
    }

    /**
     * Opens the standard Android Ignore Battery Optimizations screen for this package.
     */
    fun openBatteryOptimization(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
