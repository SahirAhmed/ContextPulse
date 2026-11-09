package com.contextpulse.phase0.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.contextpulse.phase0.widget.ContextPulseWidgetProvider

/**
 * BroadcastReceiver triggered by AlarmManager when a temporary automation snooze timer expires.
 * Immediately resumes normal context automations and updates all home screen widgets.
 */
class SnoozeAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE_EXPIRED = "com.contextpulse.phase0.system.ACTION_SNOOZE_EXPIRED"
        private const val TAG = "SnoozeAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Snooze timer expired. Resuming automations and updating widgets.")
        val snoozeManager = SnoozeManager.getInstance(context)
        snoozeManager.cleanupExpiredSnooze()

        // Reapply active profile automations
        ProfileActivationHelper.reapplyCurrentActive(context)

        // Force refresh all home screen widgets
        try {
            ContextPulseWidgetProvider.updateAllWidgets(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update widgets on snooze expiration", e)
        }
    }
}
