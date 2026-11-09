package com.contextpulse.phase0.system

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FloatingTimerType {
    NONE,
    SLEEP_TIMER,
    APP_USAGE_BUDGET
}

data class FloatingTimerState(
    val type: FloatingTimerType = FloatingTimerType.NONE,
    val title: String = "",
    val remainingSeconds: Int = 0,
    val totalMinutes: Int = 0,
    val appPackage: String? = null
)

/**
 * Manages the dynamic evaluation and lifecycle of the floating timer overlay.
 * Only displays when:
 * 1. Sleep/snooze timer is active.
 * 2. User is foregrounded in an app governed by an active usage budget limit.
 */
object FloatingTimerOverlayManager {

    private const val TAG = "FloatingTimerManager"

    private val _currentState = MutableStateFlow(FloatingTimerState())
    val currentState: StateFlow<FloatingTimerState> = _currentState.asStateFlow()

    private var lastForegroundPackage: String? = null
    private var nonBudgetStartTime: Long = 0L

    private val TRANSIENT_SYSTEM_PACKAGES = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "android"
    )

    /**
     * Evaluates current sleep and app usage budget status and updates the overlay.
     */
    fun updateTimerState(context: Context, currentForegroundPackage: String? = null) {
        val normCurrent = currentForegroundPackage?.substringBefore('/')?.trim()?.lowercase()
        val isTransientSystem = normCurrent != null && TRANSIENT_SYSTEM_PACKAGES.contains(normCurrent)

        if (currentForegroundPackage != null && !isTransientSystem) {
            lastForegroundPackage = currentForegroundPackage
        }
        val targetPkg = if (!isTransientSystem) (currentForegroundPackage ?: lastForegroundPackage) else lastForegroundPackage

        // 1. Check Sleep / Snooze timer first
        val snoozeManager = SnoozeManager.getInstance(context)
        if (snoozeManager.isSnoozeActive()) {
            val remainingSec = snoozeManager.getRemainingSeconds().toInt()
            if (remainingSec > 0) {
                nonBudgetStartTime = 0L
                val newState = FloatingTimerState(
                    type = FloatingTimerType.SLEEP_TIMER,
                    title = "Sleep Mode",
                    remainingSeconds = remainingSec,
                    totalMinutes = (remainingSec / 60) + 1
                )
                applyState(context, newState)
                return
            }
        }

        // 2. Check if current foreground app has an active usage budget limit
        if (!targetPkg.isNullOrBlank()) {
            val normTarget = targetPkg.substringBefore('/').trim().lowercase()
            val normSelf = context.packageName.lowercase()
            if (normTarget != normSelf) {
                val budget = AppUsageBudgetManager.getActiveBudgetForPackage(targetPkg)
                if (budget != null) {
                    val remainingSec = AppUsageBudgetManager.getRemainingSecondsForPackage(context, targetPkg)
                    if (remainingSec > 0) {
                        nonBudgetStartTime = 0L
                        val newState = FloatingTimerState(
                            type = FloatingTimerType.APP_USAGE_BUDGET,
                            title = budget.appName.ifBlank { "App Limit" },
                            remainingSeconds = remainingSec,
                            totalMinutes = budget.maxMinutes,
                            appPackage = budget.packageName
                        )
                        applyState(context, newState)
                        return
                    } else {
                        // Budget exhausted: trigger strict enforcement
                        AppUsageBudgetManager.checkAndEnforceExhaustedBudgets(context)
                    }
                }
            }
        }

        // Grace period before dismissing overlay: if the overlay is currently showing an APP_USAGE_BUDGET,
        // don't dismiss immediately when user pulls down shade or switches sub-activity; require 4 seconds of sustained absence.
        val currentStateValue = _currentState.value
        if (currentStateValue.type == FloatingTimerType.APP_USAGE_BUDGET) {
            val now = System.currentTimeMillis()
            if (nonBudgetStartTime == 0L) {
                nonBudgetStartTime = now
                return
            } else if (now - nonBudgetStartTime < 4000L) {
                // Within grace period: retain existing timer overlay without interruption
                return
            }
        }

        nonBudgetStartTime = 0L
        lastForegroundPackage = null
        val emptyState = FloatingTimerState(type = FloatingTimerType.NONE)
        applyState(context, emptyState)
    }

    private var lastPermissionWarnTime = 0L

    private fun applyState(context: Context, newState: FloatingTimerState) {
        val oldState = _currentState.value
        _currentState.value = newState

        if (newState.type == FloatingTimerType.NONE) {
            if (oldState.type != FloatingTimerType.NONE) {
                try {
                    FloatingTimerOverlayService.stop(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping overlay service", e)
                }
            }
        } else {
            // Only send startOrUpdate intent if state type changed, target package changed, or overlay was previously NONE
            val shouldSendIntent = oldState.type != newState.type ||
                oldState.appPackage != newState.appPackage ||
                oldState.type == FloatingTimerType.NONE

            if (shouldSendIntent) {
                if (Settings.canDrawOverlays(context)) {
                    try {
                        FloatingTimerOverlayService.startOrUpdate(context, newState)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting overlay service", e)
                    }
                } else {
                    Log.w(TAG, "Cannot show floating timer overlay: ACTION_MANAGE_OVERLAY_PERMISSION is not granted")
                    val now = System.currentTimeMillis()
                    if (now - lastPermissionWarnTime > 20_000L) {
                        lastPermissionWarnTime = now
                        try {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(
                                    context,
                                    "Grant 'Display over other apps' in Settings to view the floating timer overlay",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
