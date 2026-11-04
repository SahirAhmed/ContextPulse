package com.contextpulse.phase0.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object XiaomiHyperOsHelper {

    private const val TAG = "XiaomiHyperOsHelper"

    /**
     * Checks if the device is a Xiaomi, Redmi, or POCO device.
     */
    fun isXiaomiOrHyperOs(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") ||
                brand.contains("poco") ||
                brand.contains("redmi") ||
                getSystemProperty("ro.miui.ui.version.name").isNotEmpty()
    }

    fun getHyperOsVersion(): String {
        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        return if (miuiVersion.isNotEmpty()) miuiVersion else "HyperOS / Stock Android"
    }

    /**
     * Tries multiple known Xiaomi / HyperOS Autostart management activities.
     * Falls back to general App Details if none of the vendor activities resolve.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val candidateIntents = listOf(
            // HyperOS / Modern MIUI 14
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // Legacy MIUI 12 / 13
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManageActivity")),
            // Alternative Xiaomi Security Center startup
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            // Generic OEM fallbacks (ColorOS, EMUI)
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
        )

        for (intent in candidateIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving autostart intent: ${intent.component}", e)
            }
        }

        // Final fallback: App details
        return openAppDetails(context)
    }

    /**
     * Tries to open Xiaomi's PowerKeeper (Battery Saver) configuration screen
     * where the user can select "No restrictions".
     */
    fun openBatterySaverNoRestrictions(context: Context): Boolean {
        val candidateIntents = listOf(
            // Modern HyperOS / MIUI PowerKeeper Hidden Apps
            Intent().apply {
                component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                putExtra("package_name", context.packageName)
                putExtra("package_label", "ContextPulse")
            },
            // Direct Xiaomi battery saver package detail
            Intent().apply {
                component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
            },
            // Standard Android Battery Optimization request
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            // Standard Android Battery Optimization listing
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        for (intent in candidateIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Battery saver intent fallback", e)
            }
        }

        return openAppDetails(context)
    }

    /**
     * Opens Xiaomi's permission editor for "Display pop-up windows while running in the background".
     */
    fun openPopUpWindowPermission(context: Context): Boolean {
        val candidateIntents = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            }
        )

        for (intent in candidateIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pop-up window permission intent failed", e)
            }
        }

        return openAppDetails(context)
    }

    private fun openAppDetails(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open App Details", e)
            false
        }
    }

    private fun getSystemProperty(propName: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            getMethod.invoke(null, propName) as String
        } catch (e: Exception) {
            ""
        }
    }
}
