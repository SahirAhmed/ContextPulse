package com.contextpulse.phase0.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppInterceptorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppInterceptorA11y"
        var targetBlockedPackages = setOf<String>()
        var activeRoomName: String? = null
        var onAppIntercepted: ((String) -> Unit)? = null

        fun isServiceEnabled(context: Context): Boolean {
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                enabledServices.contains(context.packageName, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return

            AppRestrictionMonitor.verifyCurrentScheduleStateIfNeeded(applicationContext)
            if (AppRestrictionMonitor.isPackageRestricted(applicationContext, pkg)) {
                Log.w(TAG, "Restricted application detected via Accessibility: $pkg")
                onAppIntercepted?.invoke(pkg)

                val hasBudget = AppUsageBudgetManager.getActiveBudgetForPackage(pkg) != null
                val isBudgetExhausted = hasBudget && AppUsageBudgetManager.getRemainingSecondsForPackage(applicationContext, pkg) <= 0
                val blockReason = if (isBudgetExhausted) OverlayBlockerActivity.REASON_BUDGET_EXHAUSTED else OverlayBlockerActivity.REASON_BLOCK_LIST

                val room = activeRoomName ?: AppRestrictionMonitor.getCurrentActiveRoomName()
                SystemControllers.showRestrictionOverlay(applicationContext, pkg, room, blockReason)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.")
    }
}
