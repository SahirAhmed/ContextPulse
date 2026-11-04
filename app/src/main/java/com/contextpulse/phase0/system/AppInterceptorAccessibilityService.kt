package com.contextpulse.phase0.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Test 6 Comparison: Accessibility Service based application restriction.
 *
 * CRITICAL GOOGLE PLAY POLICY AUDIT:
 * - Google Play restricts BIND_ACCESSIBILITY_SERVICE strictly to apps that directly assist users with disabilities.
 * - Using AccessibilityService solely for app-blocking or phone automation without disability assistance
 *   will result in REJECTION or APP REMOVAL under the "Accessibility API Policy" unless granted a specific
 *   exemption (such as an approved Parental Control or Digital Wellbeing app with extensive declaration).
 * - Therefore, this service is provided for feasibility testing and comparison only.
 */
class AppInterceptorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppInterceptorA11y"
        var targetBlockedPackages = setOf<String>()
        var activeRoomName: String? = null
        var onAppIntercepted: ((String) -> Unit)? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return // Never block ContextPulse

            if (AppRestrictionMonitor.isPackageRestricted(applicationContext, pkg)) {
                Log.w(TAG, "Restricted application detected via Accessibility: $pkg")
                onAppIntercepted?.invoke(pkg)

                // Show the Restriction Blocker Overlay immediately
                val room = activeRoomName ?: AppRestrictionMonitor.getCurrentActiveRoomName()
                SystemControllers.showRestrictionOverlay(applicationContext, pkg, room)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.")
    }
}
