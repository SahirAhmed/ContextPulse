package com.contextpulse.phase0.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.contextpulse.phase0.model.AppUsageBudget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks foreground usage time for budgeted apps within active contexts.
 * - Emits an 80% warning notification when budget is near exhaustion.
 * - Strictly locks and blocks the application once 100% of the allowance is consumed.
 */
object AppUsageBudgetManager {

    private const val TAG = "AppUsageBudgetManager"
    private const val PREFS_NAME = "contextpulse_app_usage_budgets"
    private const val CHANNEL_ID = "contextpulse_budget_alerts"

    private var activeContextId: String? = null
    private var activeContextName: String? = null
    private var activeModeId: String? = null
    private var activeBudgets: List<AppUsageBudget> = emptyList()

    private var lastTrackedPackage: String? = null
    private var lastTrackedTimestamp: Long = 0L
    private var accumulatedMillis: Long = 0L

    // High performance in-memory cache to eliminate repetitive disk writes in polling loop
    private val memoryUsageCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var lastDiskFlushTime: Long = 0L

    private val warnedPackagesToday = mutableSetOf<String>()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    private fun makeKey(contextId: String, packageName: String, modeId: String? = activeModeId): String {
        val scopedMode = modeId?.takeIf { it.isNotBlank() } ?: "default"
        return "usage_${contextId}_${scopedMode}_${packageName}_${getTodayDateString()}"
    }

    private fun makeLegacyKey(contextId: String, packageName: String): String {
        return "usage_${contextId}_${packageName}_${getTodayDateString()}"
    }

    fun resetUsageForContext(context: Context, contextId: String?) {
        if (contextId.isNullOrBlank()) return
        val prefix = "usage_${contextId}_"
        val toRemove = memoryUsageCache.keys.filter { it.startsWith(prefix) }
        toRemove.forEach { memoryUsageCache.remove(it) }

        try {
            val prefs = getPrefs(context)
            val editor = prefs.edit()
            for (k in prefs.all.keys) {
                if (k.startsWith(prefix)) {
                    editor.remove(k)
                }
            }
            editor.apply()
        } catch (_: Exception) {}

        lastTrackedPackage = null
        lastTrackedTimestamp = 0L
        accumulatedMillis = 0L
        Log.d(TAG, "Reset usage session for context '$contextId'")
    }

    /**
     * Configures the active budgets for the current context and mode.
     */
    fun setActiveContextBudgets(
        context: Context,
        contextId: String?,
        contextName: String?,
        budgets: List<AppUsageBudget>,
        resetSession: Boolean = true,
        modeId: String? = null
    ) {
        if (budgets.isEmpty() || contextId.isNullOrBlank()) {
            if (resetSession && !contextId.isNullOrBlank()) {
                resetUsageForContext(context, contextId)
            }
            activeContextId = null
            activeContextName = null
            activeModeId = null
            activeBudgets = emptyList()
            lastTrackedPackage = null
            lastTrackedTimestamp = 0L
            accumulatedMillis = 0L
            FloatingTimerOverlayManager.clearBudgetOverlay(context)
            Log.d(TAG, "Cleared active usage budgets")
            return
        }

        if (resetSession) {
            resetUsageForContext(context, contextId)
        }

        activeContextId = contextId
        activeContextName = contextName
        activeModeId = modeId
        activeBudgets = budgets
        lastTrackedPackage = null
        lastTrackedTimestamp = 0L
        accumulatedMillis = 0L

        Log.d(TAG, "Configured ${budgets.size} budgets for context '$contextName' ($contextId, mode=$modeId)")

        if (!resetSession) {
            checkAndEnforceExhaustedBudgets(context)
        }
    }

    private fun normalizePkg(pkg: String): String {
        return pkg.substringBefore('/').trim().lowercase()
    }

    fun getUsageSeconds(context: Context, contextId: String, packageName: String): Int {
        val norm = normalizePkg(packageName)
        val key = makeKey(contextId, norm)
        memoryUsageCache[key]?.let { return it }

        val prefs = getPrefs(context)
        val direct = prefs.getInt(key, -1)
        val value = when {
            direct >= 0 -> direct
            prefs.contains(makeKey(contextId, packageName)) -> prefs.getInt(makeKey(contextId, packageName), 0)
            else -> prefs.getInt(makeLegacyKey(contextId, norm), 0)
        }
        memoryUsageCache[key] = value
        return value
    }

    fun getUsageMinutes(context: Context, contextId: String, packageName: String): Int {
        return getUsageSeconds(context, contextId, packageName) / 60
    }

    /**
     * Pure evaluation helper for test isolation.
     * Evaluates budget matching against an explicit list without reading or mutating singleton state.
     */
    fun resolveBudgetForPackagePure(packageName: String, budgets: List<AppUsageBudget>): AppUsageBudget? {
        val target = normalizePkg(packageName)
        return budgets.find { budget ->
            val bPkg = normalizePkg(budget.packageName)
            bPkg == target
        }
    }

    fun getActiveBudgetForPackage(packageName: String): AppUsageBudget? {
        return resolveBudgetForPackagePure(packageName, activeBudgets)
    }

    fun getRemainingSecondsForPackage(context: Context, packageName: String): Int {
        val cId = activeContextId ?: return 0
        val budget = getActiveBudgetForPackage(packageName) ?: return 0
        val usedSeconds = getUsageSeconds(context, cId, budget.packageName)
        val totalAllowedSeconds = budget.maxMinutes * 60
        return (totalAllowedSeconds - usedSeconds).coerceAtLeast(0)
    }

    fun getActiveBudgets(): List<AppUsageBudget> = activeBudgets

    /**
     * Called by AppRestrictionMonitor polling loop whenever an app is detected in foreground.
     */
    fun onForegroundAppHeartbeat(context: Context, currentPackage: String) {
        val cId = activeContextId ?: return
        val budget = getActiveBudgetForPackage(currentPackage) ?: run {
            lastTrackedPackage = null
            lastTrackedTimestamp = 0L
            accumulatedMillis = 0L
            return
        }

        val now = System.currentTimeMillis()
        val normCurrent = normalizePkg(currentPackage)
        if (lastTrackedPackage == normCurrent && lastTrackedTimestamp > 0L) {
            val elapsed = (now - lastTrackedTimestamp).coerceIn(0L, 10_000L)
            accumulatedMillis += elapsed
            val deltaSeconds = (accumulatedMillis / 1000L).toInt()
            if (deltaSeconds > 0) {
                accumulatedMillis %= 1000L
                addUsageSeconds(context, cId, budget, deltaSeconds)
            }
        } else {
            accumulatedMillis = 0L
        }

        lastTrackedPackage = normCurrent
        lastTrackedTimestamp = now

        // Update floating timer overlay state dynamically
        FloatingTimerOverlayManager.updateTimerState(context, currentPackage)
    }

    private fun addUsageSeconds(
        context: Context,
        contextId: String,
        budget: AppUsageBudget,
        seconds: Int
    ) {
        val norm = normalizePkg(budget.packageName)
        val key = makeKey(contextId, norm)
        val currentSeconds = getUsageSeconds(context, contextId, norm)
        val updatedSeconds = currentSeconds + seconds
        memoryUsageCache[key] = updatedSeconds

        // Periodically flush to disk (every 4s) to eliminate continuous flash I/O
        val now = System.currentTimeMillis()
        val totalAllowedSeconds = budget.maxMinutes * 60
        if (now - lastDiskFlushTime > 4000L || updatedSeconds >= totalAllowedSeconds) {
            lastDiskFlushTime = now
            getPrefs(context).edit().putInt(key, updatedSeconds).apply()
        }

        val appDisplayName = budget.appName.ifBlank { budget.packageName }

        // Exact second exhaustion: strictly enforce block
        if (updatedSeconds >= totalAllowedSeconds) {
            Log.w(TAG, "USAGE BUDGET EXHAUSTED: $appDisplayName ($updatedSeconds s / $totalAllowedSeconds s). Strictly blocking.")
            getPrefs(context).edit().putInt(key, updatedSeconds).apply()
            enforceExhaustedApp(context, budget.packageName)
        }
    }

    fun checkAndEnforceExhaustedBudgets(context: Context) {
        val cId = activeContextId ?: return
        activeBudgets.forEach { budget ->
            val usedMin = getUsageMinutes(context, cId, budget.packageName)
            if (usedMin >= budget.maxMinutes) {
                enforceExhaustedApp(context, budget.packageName)
            }
        }
    }

    private fun enforceExhaustedApp(context: Context, packageName: String) {
        val normTarget = normalizePkg(packageName)
        val currentRestricted = AppRestrictionMonitor.getRestrictedPackages().toMutableSet()
        if (!currentRestricted.contains(normTarget) && !currentRestricted.contains(packageName)) {
            currentRestricted.add(packageName)
            AppRestrictionMonitor.setRestrictedPackages(
                packages = currentRestricted,
                roomName = activeContextName ?: "Context",
                modeId = activeModeId
            )
            AppRestrictionMonitor.startContinuousMonitoring(context, 200L)
        }

        // ONLY trigger restriction overlay if the user is currently foregrounded in that specific blocked app.
        // NEVER show restriction overlay if user is inside ContextPulse or navigating elsewhere!
        try {
            val (currentFgApp, _) = SystemControllers.getForegroundAppWithLatency(context)
            val normFg = currentFgApp?.let { normalizePkg(it) }
            val normSelf = normalizePkg(context.packageName)

            if (normFg != null && normFg != normSelf && normFg == normTarget) {
                SystemControllers.showRestrictionOverlay(
                    context = context,
                    blockedPackage = packageName,
                    activeRoomName = activeContextName ?: "Context",
                    blockReason = OverlayBlockerActivity.REASON_BUDGET_EXHAUSTED
                )
            }
        } catch (_: Exception) {
            // Handled
        }
    }

    private fun sendWarningNotification(
        context: Context,
        appName: String,
        usedMinutes: Int,
        maxMinutes: Int
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Usage Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when app allowances reach 80% or 100%"
                }
                nm.createNotificationChannel(channel)
            }

            val title = "$appName Allowance Warning"
            val body = "80% used: $usedMinutes of $maxMinutes minutes in ${activeContextName ?: "current context"}."

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            nm.notify(appName.hashCode(), notif)
            Toast.makeText(context, "$title: $body", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error posting budget notification", e)
        }
    }
}
