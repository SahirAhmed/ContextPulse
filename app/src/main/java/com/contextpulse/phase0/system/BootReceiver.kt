package com.contextpulse.phase0.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.contextpulse.phase0.ble.Phase0BleService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Device boot or update completed with action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Immediately re-evaluate active context and scheduled mode on device reboot
            ProfileActivationHelper.reapplyCurrentActive(context)

            // Recalculate and schedule the next boundary alarm
            ModeScheduleManager.scheduleNextBoundaryAlarm(context)

            // Start BLE foreground service only if permissions are present
            Phase0BleService.start(context)

            // Recover any interrupted guided flash tests where Android permissions permit
            try {
                val pending = com.contextpulse.phase0.testing.GuidedTestRecoveryJournal.getPendingRecord(context)
                if (pending != null) {
                    val (success, msg) = com.contextpulse.phase0.testing.GuidedTestRecoveryJournal.attemptRestoration(context, pending)
                    Log.i("BootReceiver", "Boot guided test recovery result ($success): $msg")
                }
            } catch (e: Throwable) {
                Log.w("BootReceiver", "Error checking pending flash test recovery on boot", e)
            }
        }
    }
}
