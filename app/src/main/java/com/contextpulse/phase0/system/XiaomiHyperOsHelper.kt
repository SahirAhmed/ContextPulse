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

    private var mockIsXiaomiOverride: Boolean? = null

    /**
     * For isolated automated unit tests to verify behavior on non-Xiaomi device profiles
     * without modifying physical device hardware.
     */
    fun setMockDeviceProfile(isXiaomi: Boolean?) {
        mockIsXiaomiOverride = isXiaomi
    }

    fun isXiaomiOrHyperOs(): Boolean {
        mockIsXiaomiOverride?.let { return it }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()

        val isXiaomiMfrOrBrand = manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") ||
                brand.contains("xiaomi") ||
                brand.contains("redmi") ||
                brand.contains("poco") ||
                model.contains("poco") ||
                model.contains("redmi")

        val hasMiuiOrHyperOsProp = getSystemProperty("ro.miui.ui.version.name").isNotEmpty() ||
                getSystemProperty("ro.miui.ui.version.code").isNotEmpty() ||
                getSystemProperty("ro.miui.version.code_name").isNotEmpty()

        return isXiaomiMfrOrBrand || hasMiuiOrHyperOsProp
    }

    fun isXiaomiOrPocoDevice(): Boolean = isXiaomiOrHyperOs()

    fun getDeviceDisplaySummary(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (${getHyperOsVersion()})"
    }

    fun getHyperOsChecklist(): List<String> {
        return listOf(
            "1. Enable Autostart: Open Xiaomi Security → Manage Apps → ContextPulse → enable 'Autostart' so BLE Beacon & schedule services survive reboots.",
            "2. Battery Saver: Set ContextPulse Battery Saver mode to 'No restrictions' so HyperOS / MIUI does not suspend background proximity scanning.",
            "3. Lock in Recents: Open Recent Apps, long-press ContextPulse, and tap the Lock icon.",
            "4. Background Pop-up Permission: Enable 'Display pop-up windows while running in the background' so Blocked Apps focus shields appear immediately."
        )
    }

    fun openAutoStartSettings(context: Context): Boolean = openAutostartSettings(context)

    fun openBatterySaverSettings(context: Context): Boolean = openBatterySaverNoRestrictions(context)

    fun getHyperOsVersion(): String {
        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        val miuiCode = getSystemProperty("ro.miui.ui.version.code")
        return when {
            miuiVersion.isNotEmpty() -> "HyperOS / MIUI $miuiVersion"
            miuiCode.isNotEmpty() -> "HyperOS (Build $miuiCode)"
            isXiaomiOrHyperOs() -> "Xiaomi HyperOS"
            else -> "Stock Android"
        }
    }

    fun openAutostartSettings(context: Context): Boolean {
        val candidateIntents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManageActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
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

        return openAppDetails(context)
    }

    fun openBatterySaverNoRestrictions(context: Context): Boolean {
        val candidateIntents = listOf(
            Intent().apply {
                component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                putExtra("package_name", context.packageName)
                putExtra("package_label", "ContextPulse")
            },
            Intent().apply {
                component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
            },
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
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
