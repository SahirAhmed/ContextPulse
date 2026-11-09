package com.contextpulse.phase0.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.contextpulse.phase0.ble.Phase0BleService
import com.contextpulse.phase0.repository.RoomProfileRepository

/**
 * BroadcastReceiver triggered by AlarmManager when a scheduled mode timeframe boundary arrives.
 * Reapplies active mode automations deterministically and schedules the next boundary.
 */
class ScheduleBoundaryReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleBoundaryRecv"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Mode timeframe boundary alarm triggered. Evaluating current mode.")

        // 1. Guard against overriding active preview session
        if (ActiveConfigSnapshotManager.isPreviewActive(context)) {
            Log.d(TAG, "Preview mode active; deferring scheduled mode reapplication.")
            ModeScheduleManager.scheduleNextBoundaryAlarm(context)
            return
        }

        // 2. Guard against overriding active snooze
        val snoozeManager = SnoozeManager.getInstance(context)
        if (snoozeManager.isSnoozeActive()) {
            Log.d(TAG, "Snooze active; scheduled mode change deferred until snooze completes.")
            ModeScheduleManager.scheduleNextBoundaryAlarm(context)
            return
        }

        val roomRepo = RoomProfileRepository.getInstance(context)
        val activeRoom = roomRepo.activeRoom.value

        if (activeRoom != null) {
            val resolvedMode = activeRoom.profile.resolveActiveMode()
            Log.d(TAG, "Applying active mode '${resolvedMode.name}' for room '${activeRoom.name}' at schedule boundary.")
            ProfileActivationHelper.reapplyCurrentActive(context)

            // Refresh ongoing notification in background service
            try {
                val serviceIntent = Intent(context, Phase0BleService::class.java).apply {
                    action = Phase0BleService.ACTION_REAPPLY_ACTIVE
                }
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to notify Phase0BleService of schedule boundary", e)
            }
        }

        // 3. Schedule the next upcoming boundary
        ModeScheduleManager.scheduleNextBoundaryAlarm(context)
    }
}
