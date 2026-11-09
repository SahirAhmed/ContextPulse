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
        }
    }
}
