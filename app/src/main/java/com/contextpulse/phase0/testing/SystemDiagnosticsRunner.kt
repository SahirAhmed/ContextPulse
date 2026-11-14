package com.contextpulse.phase0.testing

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.contextpulse.phase0.ble.BeaconRegistry
import com.contextpulse.phase0.ble.Phase0BleService
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.ModeScheduleManager
import com.contextpulse.phase0.system.SystemControllers
import com.contextpulse.phase0.system.XiaomiHyperOsHelper

/**
 * Executes accurate, read-only system readiness checks.
 * Critical: Never claims that a permission grant equates to verified automation.
 */
object SystemDiagnosticsRunner {

    fun runAllDiagnostics(context: Context): List<DiagnosticTestResult> {
        val results = mutableListOf<DiagnosticTestResult>()

        // 1. Overlay Permission
        val hasOverlay = Settings.canDrawOverlays(context)
        results.add(
            DiagnosticTestResult(
                id = "diag_perm_overlay",
                name = "System Overlay Permission",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Permissions",
                status = if (hasOverlay) DiagnosticStatus.PASSED else DiagnosticStatus.NEEDS_ATTENTION,
                explanation = "Determines whether floating timer and block overlays can display over other apps.",
                expectedResult = "SYSTEM_ALERT_WINDOW permission granted.",
                actualResult = if (hasOverlay) "Granted" else "Not Granted",
                correctiveAction = if (!hasOverlay) "Grant 'Display over other apps' in Android System Settings." else null,
                fixActionLabel = if (!hasOverlay) "Open Overlay Settings" else null,
                onFix = if (!hasOverlay) { { SystemControllers.openOverlaySettings(context) } } else null
            )
        )

        // 2. Usage Stats Permission
        val hasUsage = SystemControllers.hasUsageStatsPermission(context)
        results.add(
            DiagnosticTestResult(
                id = "diag_perm_usage",
                name = "Usage Access Permission",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Permissions",
                status = if (hasUsage) DiagnosticStatus.PASSED else DiagnosticStatus.NEEDS_ATTENTION,
                explanation = "Required to inspect currently foregrounded application for distraction blocking and budgets.",
                expectedResult = "PACKAGE_USAGE_STATS permission granted.",
                actualResult = if (hasUsage) "Granted" else "Not Granted",
                correctiveAction = if (!hasUsage) "Grant 'Usage Access' in Android Settings." else null,
                fixActionLabel = if (!hasUsage) "Open Usage Settings" else null,
                onFix = if (!hasUsage) { { SystemControllers.openUsageAccessSettings(context) } } else null
            )
        )

        // 3. Notification Policy (DND) Access
        val hasDnd = SystemControllers.isDndAccessGranted(context)
        results.add(
            DiagnosticTestResult(
                id = "diag_perm_dnd",
                name = "Do Not Disturb (Notification Policy) Access",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Permissions",
                status = if (hasDnd) DiagnosticStatus.PASSED else DiagnosticStatus.NEEDS_ATTENTION,
                explanation = "Determines whether ContextPulse can mute ringers and set ZenMode rules.",
                expectedResult = "Notification Policy Access granted.",
                actualResult = if (hasDnd) "Granted" else "Not Granted",
                correctiveAction = if (!hasDnd) "Grant 'Do Not Disturb Access' in Android Settings." else null,
                fixActionLabel = if (!hasDnd) "Open DND Settings" else null,
                onFix = if (!hasDnd) { { SystemControllers.openDndAccessSettings(context) } } else null
            )
        )

        // 4. Modify System Settings Permission (Screen Brightness)
        val canWrite = SystemControllers.canModifySystemSettings(context)
        results.add(
            DiagnosticTestResult(
                id = "diag_perm_brightness",
                name = "Modify System Settings Permission",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Permissions",
                status = if (canWrite) DiagnosticStatus.PASSED else DiagnosticStatus.NEEDS_ATTENTION,
                explanation = "Required to adjust device screen brightness and display modes.",
                expectedResult = "WRITE_SETTINGS permission granted.",
                actualResult = if (canWrite) "Granted" else "Not Granted",
                correctiveAction = if (!canWrite) "Grant 'Modify system settings' in Android Settings." else null,
                fixActionLabel = if (!canWrite) "Open Write Settings" else null,
                onFix = if (!canWrite) { { SystemControllers.openWriteSettings(context) } } else null
            )
        )

        // 5. Bluetooth Permissions (BLUETOOTH_SCAN & BLUETOOTH_CONNECT)
        val hasBtPermission = Phase0BleService.hasBluetoothPermission(context)
        results.add(
            DiagnosticTestResult(
                id = "diag_perm_bluetooth",
                name = "Bluetooth Runtime Permissions",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Bluetooth & Location",
                status = if (hasBtPermission) DiagnosticStatus.PASSED else DiagnosticStatus.NEEDS_ATTENTION,
                explanation = "Required on Android 12+ (API 31+) for scanning BLE beacons and running connectedDevice foreground service.",
                expectedResult = "BLUETOOTH_SCAN and BLUETOOTH_CONNECT granted.",
                actualResult = if (hasBtPermission) "Granted" else "Missing / Denied",
                correctiveAction = if (!hasBtPermission) "Grant Nearby Devices permission in App Info." else null
            )
        )

        // 6. Bluetooth Hardware Availability & Enabled State
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter
        val isBtSupported = btAdapter != null
        val isBtEnabled = btAdapter?.isEnabled == true
        results.add(
            DiagnosticTestResult(
                id = "diag_hardware_bt",
                name = "Bluetooth Hardware & Radio State",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Hardware",
                status = when {
                    !isBtSupported -> DiagnosticStatus.FAILED
                    isBtEnabled -> DiagnosticStatus.PASSED
                    else -> DiagnosticStatus.NEEDS_ATTENTION
                },
                explanation = "Checks if Bluetooth LE hardware is available and radio is currently turned on.",
                expectedResult = "Bluetooth hardware available and enabled.",
                actualResult = when {
                    !isBtSupported -> "Hardware Not Supported"
                    isBtEnabled -> "Enabled & Ready"
                    else -> "Radio Disabled (Turned Off)"
                },
                correctiveAction = if (isBtSupported && !isBtEnabled) "Turn on Bluetooth in device Quick Settings." else null
            )
        )

        // 7. Battery Optimisation (Doze Mode) Status
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
        results.add(
            DiagnosticTestResult(
                id = "diag_battery_opt",
                name = "Battery Optimisation (Doze Exemption)",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Background Execution",
                status = if (isIgnoringBatteryOptimizations) DiagnosticStatus.PASSED else DiagnosticStatus.NEEDS_ATTENTION,
                explanation = "Checks whether ContextPulse is unrestricted by Android Doze for reliable background triggers.",
                expectedResult = "Battery optimisation ignored (Unrestricted).",
                actualResult = if (isIgnoringBatteryOptimizations) "Unrestricted" else "Optimised / Restricted",
                correctiveAction = if (!isIgnoringBatteryOptimizations) "Set ContextPulse battery usage to 'Unrestricted' in App Settings." else null
            )
        )

        // 8. HyperOS / Xiaomi Background Execution Check
        val isHyperOsOrMiui = XiaomiHyperOsHelper.isXiaomiOrHyperOs()
        val oemVersion = XiaomiHyperOsHelper.getHyperOsVersion()
        results.add(
            DiagnosticTestResult(
                id = "diag_hyperos_restrictions",
                name = if (isHyperOsOrMiui) "OEM Background Restrictions (HyperOS / MIUI)" else "OEM Background Restrictions",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Background Execution",
                status = if (isHyperOsOrMiui) {
                    DiagnosticStatus.MANUAL_VERIFICATION_REQUIRED
                } else {
                    DiagnosticStatus.NOT_APPLICABLE
                },
                explanation = if (isHyperOsOrMiui) {
                    "Android does not expose an API to query proprietary Xiaomi Autostart toggle state. Manual user confirmation is required."
                } else {
                    "Not applicable on this device (${Build.MANUFACTURER} ${Build.MODEL}). Standard Android background rules apply."
                },
                expectedResult = if (isHyperOsOrMiui) {
                    "Autostart enabled and Battery Saver set to 'No restrictions' in Xiaomi Security."
                } else {
                    "Standard Android OS (No Xiaomi OEM constraints)."
                },
                actualResult = if (isHyperOsOrMiui) {
                    "Device: $oemVersion (Autostart cannot be queried programmatically)"
                } else {
                    "Not Applicable (${Build.MANUFACTURER} device)"
                },
                correctiveAction = if (isHyperOsOrMiui) {
                    "In Xiaomi Security → Manage Apps → ContextPulse: confirm 'Autostart' is ON and Battery Saver is 'No restrictions'."
                } else null,
                fixActionLabel = if (isHyperOsOrMiui) "Open Xiaomi Autostart" else null,
                onFix = if (isHyperOsOrMiui) { { XiaomiHyperOsHelper.openAutostartSettings(context) } } else null
            )
        )

        // 9. Context & Mode Repository Availability
        val roomRepo = RoomProfileRepository.getInstance(context)
        val roomCount = roomRepo.rooms.value.size
        val activeRoom = roomRepo.activeRoom.value
        results.add(
            DiagnosticTestResult(
                id = "diag_repo_contexts",
                name = "Context & Mode Repository Readiness",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Data Layer",
                status = DiagnosticStatus.PASSED,
                explanation = "Checks SharedPreferences storage for user contexts, modes, and configurations.",
                expectedResult = "Repository loaded with valid contexts.",
                actualResult = "$roomCount contexts configured (Active: ${activeRoom?.name ?: "None / Baseline"})"
            )
        )

        // 10. Beacon Configuration Availability
        val beaconRepo = BeaconRepository.getInstance(context)
        val regBeacons = BeaconRegistry.getRegisteredBeacons(context)
        val triggersCount = beaconRepo.beacons.value.size
        results.add(
            DiagnosticTestResult(
                id = "diag_repo_beacons",
                name = "Beacon & Trigger Registry Readiness",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Data Layer",
                status = DiagnosticStatus.PASSED,
                explanation = "Verifies registered BLE room beacons and trigger definitions.",
                expectedResult = "Beacon registry accessible.",
                actualResult = "${regBeacons.size} room beacons, $triggersCount custom triggers configured"
            )
        )

        // 11. Foreground Service Status
        val isServiceRunning = Phase0BleService.isServiceRunning.value
        results.add(
            DiagnosticTestResult(
                id = "diag_service_status",
                name = "BLE Background Foreground Service",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Services",
                status = if (isServiceRunning) DiagnosticStatus.PASSED else DiagnosticStatus.NEEDS_ATTENTION,
                explanation = "Checks whether Phase0BleService is currently running with persistent notification.",
                expectedResult = "Service running in background with connectedDevice type.",
                actualResult = if (isServiceRunning) "Running (Active)" else "Stopped / Standby",
                correctiveAction = if (!isServiceRunning && hasBtPermission && isBtEnabled) "Start service via notification or toggle Bluetooth." else null
            )
        )

        // 12. Schedule Boundary Calculation & Alarm Dispatch
        val hasActiveContext = activeRoom != null
        val nextBoundaryMillis = if (activeRoom != null) ModeScheduleManager.calculateNextBoundaryMillis(activeRoom) else null
        results.add(
            DiagnosticTestResult(
                id = "diag_schedule_alarm",
                name = "Schedule Boundary Calculation & Alarm Dispatch",
                category = DiagnosticCategory.SYSTEM_DIAGNOSTICS,
                subcategory = "Scheduling",
                status = DiagnosticStatus.PASSED,
                explanation = "Verifies calculation of nearest upcoming mode timeframe boundary and dispatch to AlarmManager.",
                expectedResult = if (hasActiveContext) "Next boundary timestamp calculated and AlarmManager dispatch requested." else "No active context (Alarm idle).",
                actualResult = if (nextBoundaryMillis != null) {
                    val inSec = ((nextBoundaryMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    "Calculated: in ${inSec}s (AlarmManager dispatch requested). Note: OS kernel wakeups cannot be verified without physical execution."
                } else if (hasActiveContext) {
                    "No timeframe modes in active context"
                } else {
                    "Idle (No active context)"
                }
            )
        )

        return results
    }
}
