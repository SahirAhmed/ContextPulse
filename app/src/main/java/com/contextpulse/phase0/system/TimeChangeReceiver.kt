package com.contextpulse.phase0.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles device clock, timezone, and date changes.
 * Immediately reevaluates the active context and mode, and reschedules the next mode boundary alarm.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Clock or timezone change received: $action. Reevaluating schedule...")

        if (action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            // Reevaluate active context and currently scheduled mode
            ProfileActivationHelper.reapplyCurrentActive(context)

            // Recalculate and reschedule the next boundary alarm with the new time
            ModeScheduleManager.scheduleNextBoundaryAlarm(context)

            // Update persistent notification if service is running
            com.contextpulse.phase0.ble.Phase0BleService.updatePersistentNotification(context)
        }
    }
}
