package com.contextpulse.phase0

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class Phase0Application : Application() {

    companion object {
        const val CHANNEL_BLE_SERVICE = "channel_ble_service"
        const val CHANNEL_ALERTS = "channel_context_alerts"
        const val TAG = "ContextPulsePhase0"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application initialized. Device: ${Build.MANUFACTURER} ${Build.MODEL}, OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        try {
            val pm = packageManager
            val mainComp = android.content.ComponentName(packageName, "com.contextpulse.phase0.ui.MainActivity")
            if (pm.getComponentEnabledSetting(mainComp) != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    mainComp,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error ensuring MainActivity enabled on startup: ${e.message}")
        }
        createNotificationChannels()
        try {
            com.contextpulse.phase0.ble.Phase0BleService.start(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to auto-start Phase0BleService on app launch", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_BLE_SERVICE,
                "ContextPulse Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BLE room scanning alive in background"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Context Trigger Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications triggered upon room arrival or departure"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }
}
