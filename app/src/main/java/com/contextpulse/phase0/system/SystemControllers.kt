package com.contextpulse.phase0.system

import android.app.Activity
import android.app.AppOpsManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.contextpulse.phase0.Phase0Application
import com.contextpulse.phase0.model.SystemStateSnapshot
import com.contextpulse.phase0.model.ZenRuleOpResult
import com.contextpulse.phase0.ui.MainActivity

object SystemControllers {

    private const val TAG = "SystemControllers"
    private const val ZEN_RULE_CONDITION_ID = "contextpulse://room/bedroom"

    // ==========================================
    // TEST 5: Detecting Foreground Application
    // ==========================================

    // Cached system services and permission status for high-efficiency polling
    private var cachedUsageStatsManager: UsageStatsManager? = null
    private var cachedActivityManager: android.app.ActivityManager? = null
    private var cachedAppOpsManager: AppOpsManager? = null
    private var lastPermissionCheckTime = 0L
    private var cachedHasUsageStats = false

    fun hasUsageStatsPermission(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPermissionCheckTime < 2500L) {
            return cachedHasUsageStats
        }
        val appOps = cachedAppOpsManager ?: (context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager).also {
            cachedAppOpsManager = it
        } ?: return false

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
        val granted = mode == AppOpsManager.MODE_ALLOWED
        cachedHasUsageStats = granted
        lastPermissionCheckTime = now
        return granted
    }

    fun openUsageAccessSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Usage Access Settings", e)
            false
        }
    }

    /**
     * Queries the foreground app via UsageStatsManager with caching and tight windowing.
     * Returns a pair of: (packageName, eventLatencyMillis)
     */
    fun getForegroundAppWithLatency(context: Context): Pair<String?, Long> {
        val endTime = System.currentTimeMillis()
        var lastForegroundApp: String? = null
        var lastEventTimestamp = 0L

        // Method 1: UsageStats queryEvents & queryUsageStats
        if (hasUsageStatsPermission(context)) {
            val usageStatsManager = cachedUsageStatsManager ?: (context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager).also {
                cachedUsageStatsManager = it
            }
            if (usageStatsManager != null) {
                try {
                    // Optimized 1.8s window for rapid foreground detection
                    val startTime = endTime - 1_800L
                    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
                    val event = UsageEvents.Event()

                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(event)
                        val isForeground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                        if (isForeground && event.timeStamp > lastEventTimestamp) {
                            lastForegroundApp = event.packageName
                            lastEventTimestamp = event.timeStamp
                        }
                    }

                    if (lastForegroundApp == null) {
                        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, endTime - 10_000L, endTime)
                        lastForegroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying usage events", e)
                }
            }
        }

        // Method 2: Running App Processes (fallback)
        if (lastForegroundApp == null) {
            try {
                val am = cachedActivityManager ?: (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager).also {
                    cachedActivityManager = it
                }
                val processes = am?.runningAppProcesses
                val fg = processes?.find {
                    it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    !it.pkgList.isNullOrEmpty()
                }
                if (fg != null && !fg.pkgList.isNullOrEmpty()) {
                    lastForegroundApp = fg.pkgList[0]
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val latency = if (lastEventTimestamp > 0) (endTime - lastEventTimestamp) else 0L
        return Pair(lastForegroundApp, latency)
    }

    // ==========================================
    // TEST 6: App Restriction & Interception
    // ==========================================

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open overlay settings", e)
            false
        }
    }

    fun showRestrictionOverlay(
        context: Context,
        blockedPackage: String,
        activeRoomName: String? = null,
        blockReason: String? = null
    ) {
        val normTarget = blockedPackage.substringBefore('/').trim().lowercase()
        val normSelf = context.packageName.lowercase()
        if (normTarget == normSelf) {
            Log.w(TAG, "Prevented restriction overlay from targeting ContextPulse itself: $blockedPackage")
            return
        }

        val intent = Intent(context, OverlayBlockerActivity::class.java).apply {
            putExtra(OverlayBlockerActivity.EXTRA_BLOCKED_PKG, blockedPackage)
            activeRoomName?.let { putExtra(OverlayBlockerActivity.EXTRA_ACTIVE_ROOM_NAME, it) }
            blockReason?.let { putExtra(OverlayBlockerActivity.EXTRA_BLOCK_REASON, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity for restriction overlay failed", e)
        }

        // Full-screen notification guarantee: on Android 10+ background execution,
        // if ContextPulse is not currently open, posting a high-priority Full-Screen Intent
        // causes the OS to immediately surface the blocker overlay over the restricted third-party app.
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    blockedPackage.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                val notif = NotificationCompat.Builder(context, Phase0Application.CHANNEL_ALERTS)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("App Access Blocked")
                    .setContentText("Restricted during active context")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .build()
                nm.notify(1099, notif)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Full-screen notification launch fallback failed", e)
        }
    }

    fun kickToHomeScreen(context: Context) {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }

    // ==========================================
    // TEST 7: Screen Brightness Modification
    // ==========================================

    fun canModifySystemSettings(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun openWriteSettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Write Settings", e)
            false
        }
    }

    fun setScreenBrightness(context: Context, brightness0to255: Int): Boolean {
        if (!canModifySystemSettings(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness0to255.coerceIn(1, 255)
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing screen brightness", e)
            false
        }
    }

    /**
     * Applies brightness both at the window level (for instant on-screen feedback)
     * and system-wide if WRITE_SETTINGS permission is available.
     */
    fun applyBrightness(context: Context, brightnessPercent: Int): Boolean {
        val clamped = brightnessPercent.coerceIn(1, 100)
        var applied = false

        if (context is Activity) {
            try {
                val lp = context.window.attributes
                lp.screenBrightness = clamped / 100f
                context.window.attributes = lp
                applied = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed setting activity window brightness", e)
            }
        }

        if (canModifySystemSettings(context)) {
            val rawValue = ((clamped * 255) / 100).coerceIn(1, 255)
            val sysSuccess = setScreenBrightness(context, rawValue)
            if (sysSuccess) applied = true
        }

        return applied
    }

    fun restoreBrightness(context: Context): Boolean {
        var restored = false
        if (context is Activity) {
            try {
                val lp = context.window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                context.window.attributes = lp
                restored = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed restoring activity window brightness", e)
            }
        }
        return restored
    }

    fun getCurrentBrightness(context: Context): Pair<Int, Int> {
        val mode = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        } catch (e: Exception) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        val value = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
        return Pair(mode, value)
    }

    // ==========================================
    // TEST 8: Do Not Disturb & AutomaticZenRule (Modern Android 14/15)
    // ==========================================

    fun isDndAccessGranted(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    fun openDndAccessSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DND access settings", e)
            false
        }
    }

    fun applyDndMode(context: Context, modeLabel: String): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "Cannot apply DND: ACCESS_NOTIFICATION_POLICY permission not granted")
            return false
        }

        val filter = when {
            modeLabel.contains("silence", ignoreCase = true) || modeLabel.contains("none", ignoreCase = true) ->
                NotificationManager.INTERRUPTION_FILTER_NONE
            modeLabel.contains("alarm", ignoreCase = true) ->
                NotificationManager.INTERRUPTION_FILTER_ALARMS
            else ->
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }

        return try {
            nm.setInterruptionFilter(filter)
            setAutomaticZenRuleState(context, "ContextPulse Room Rule", true)
            Log.d(TAG, "Applied real system DND filter: $filter ($modeLabel)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting system DND filter", e)
            false
        }
    }

    fun restoreDnd(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false

        return try {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            setAutomaticZenRuleState(context, "ContextPulse Room Rule", false)
            Log.d(TAG, "Restored system DND to INTERRUPTION_FILTER_ALL")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed restoring system DND", e)
            false
        }
    }

    /**
     * Modern Android 14/15 implementation of AutomaticZenRule:
     * - Uses configurationActivity ComponentName pointing to MainActivity (NOT an ordinary Service).
     * - Owner is set to null, avoiding invalid ConditionProviderService crashes.
     * - Uses NotificationManager.setAutomaticZenRuleState() with Condition.STATE_TRUE / STATE_FALSE.
     * - Returns a detailed ZenRuleOpResult with actual verified status.
     */
    fun setAutomaticZenRuleState(
        context: Context,
        ruleName: String,
        activate: Boolean
    ): ZenRuleOpResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ZenRuleOpResult(false, null, false, "NotificationManager service unavailable")

        if (!nm.isNotificationPolicyAccessGranted) {
            return ZenRuleOpResult(false, null, false, "ACCESS_NOTIFICATION_POLICY permission not granted")
        }

        return try {
            val conditionUri = Uri.parse(ZEN_RULE_CONDITION_ID)
            val configActivity = ComponentName(context, MainActivity::class.java)

            // 1. Search for existing rule with this condition URI or name
            val existingRules = nm.automaticZenRules
            var targetRuleId: String? = null
            for ((id, rule) in existingRules) {
                if (rule.conditionId == conditionUri || rule.name == ruleName) {
                    targetRuleId = id
                    break
                }
            }

            // 2. Create rule if it does not already exist
            if (targetRuleId == null) {
                // In Android 10+ (API 29+), pass owner=null and configurationActivity to avoid ConditionProviderService requirement
                val newZenRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val policy = ZenPolicy.Builder()
                        .allowAlarms(true)
                        .allowRepeatCallers(true)
                        .build()
                    AutomaticZenRule(
                        ruleName,
                        null, // owner is null
                        configActivity, // configurationActivity
                        conditionUri,
                        policy,
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                        true // initially enabled in system settings
                    )
                } else {
                    @Suppress("DEPRECATION")
                    AutomaticZenRule(
                        ruleName,
                        null,
                        conditionUri,
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                        true
                    )
                }

                targetRuleId = nm.addAutomaticZenRule(newZenRule)
                if (targetRuleId == null) {
                    return ZenRuleOpResult(false, null, false, "Failed creating AutomaticZenRule in NotificationManager")
                }
                Log.d(TAG, "Created new AutomaticZenRule with ID: $targetRuleId")
            }

            // 3. Activate or Deactivate rule using setAutomaticZenRuleState / Condition
            val conditionState = if (activate) Condition.STATE_TRUE else Condition.STATE_FALSE
            val conditionSummary = if (activate) "ContextPulse Room Active" else "ContextPulse Room Inactive"
            val condition = Condition(conditionUri, conditionSummary, conditionState)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                nm.setAutomaticZenRuleState(targetRuleId, condition)
            } else {
                val rule = nm.getAutomaticZenRule(targetRuleId)
                if (rule != null) {
                    rule.isEnabled = activate
                    nm.updateAutomaticZenRule(targetRuleId, rule)
                }
            }

            // 4. Verify actual rule state from system
            val verifiedRule = nm.getAutomaticZenRule(targetRuleId)
            val isRuleEnabled = verifiedRule?.isEnabled == true
            val currentFilter = nm.currentInterruptionFilter

            ZenRuleOpResult(
                success = true,
                ruleId = targetRuleId,
                isActive = activate,
                message = "ZenRule '$ruleName' (${targetRuleId.take(8)}) ${if (activate) "ACTIVATED" else "DEACTIVATED"}. System Interruption Filter: $currentFilter"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing AutomaticZenRule operation", e)
            ZenRuleOpResult(false, null, false, "Exception: ${e.message}")
        }
    }

    // ==========================================
    // TEST 10: Launching Another Application
    // ==========================================

    fun launchAppByPackage(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                Log.w(TAG, "No launch intent found for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching package: $packageName", e)
            false
        }
    }

    // ==========================================
    // TEST 14: State Snapshot & Restoration
    // ==========================================

    fun takeSystemStateSnapshot(context: Context): SystemStateSnapshot {
        val (mode, brightness) = getCurrentBrightness(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val ringer = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val dnd = if (nm?.isNotificationPolicyAccessGranted == true) {
            nm.currentInterruptionFilter
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        return SystemStateSnapshot(
            brightnessMode = mode,
            brightnessValue = brightness,
            ringerMode = ringer,
            dndFilter = dnd
        )
    }

    fun restoreSystemState(context: Context, snapshot: SystemStateSnapshot): Boolean {
        var success = true

        // 1. Restore Brightness
        if (canModifySystemSettings(context)) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    snapshot.brightnessMode
                )
                if (snapshot.brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        snapshot.brightnessValue
                    )
                }
            } catch (e: Exception) {
                success = false
            }
        }

        // 2. Restore DND
        if (isDndAccessGranted(context)) {
            try {
                // Deactivate room AutomaticZenRule
                setAutomaticZenRuleState(context, "ContextPulse Room Rule", false)
            } catch (e: Exception) {
                success = false
            }
        }

        // 3. Restore Ringer Mode
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.ringerMode = snapshot.ringerMode
        } catch (e: Exception) {
            // Can be suppressed if system DND is active
        }

        return success
    }

    // ==========================================
    // Eye Comfort / Night Light Controls
    // ==========================================

    fun isNightLightSupported(context: Context): Boolean {
        val intent = Intent("android.settings.NIGHT_DISPLAY_SETTINGS")
        val pm = context.packageManager
        return intent.resolveActivity(pm) != null
    }

    fun openNightLightSettings(context: Context): Boolean {
        return try {
            val intent = Intent("android.settings.NIGHT_DISPLAY_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed opening display settings", ex)
                false
            }
        }
    }

    fun applyEyeComfort(context: Context, enabled: Boolean, level: Int = 50): Boolean {
        return try {
            val success = Settings.Secure.putInt(
                context.contentResolver,
                "night_display_activated",
                if (enabled) 1 else 0
            )
            Log.d(TAG, "Applied Eye Comfort setting: enabled=$enabled, level=$level, res=$success")
            success
        } catch (e: Exception) {
            Log.w(TAG, "Eye comfort system write restricted (requires secure settings): enabled=$enabled, level=$level")
            false
        }
    }
}

