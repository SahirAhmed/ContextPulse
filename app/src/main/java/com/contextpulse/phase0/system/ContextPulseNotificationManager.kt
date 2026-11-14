package com.contextpulse.phase0.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.ui.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages device-level notifications for ContextPulse:
 * - Context transition alerts (e.g., 'Context has changed from Bedroom to Office Room (Reason: Manual Switch)')
 * - Low-battery beacon alerts
 * - User preferences for enabling/disabling individual notification types
 */
class ContextPulseNotificationManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_notification_prefs"
        private const val KEY_NOTIFY_CONTEXT_CHANGES = "key_notify_context_changes"
        private const val KEY_NOTIFY_BEACON_BATTERY = "key_notify_beacon_battery"

        const val CHANNEL_CONTEXT_CHANGES = "channel_context_changes"
        const val CHANNEL_BEACON_ALERTS = "channel_beacon_alerts"

        private const val NOTIFICATION_ID_CONTEXT_SWITCH = 1001
        private const val NOTIFICATION_ID_BEACON_BATTERY = 1002

        @Volatile
        private var INSTANCE: ContextPulseNotificationManager? = null

        fun getInstance(context: Context): ContextPulseNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContextPulseNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _notifyContextChanges = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_CONTEXT_CHANGES, true))
    val notifyContextChanges: StateFlow<Boolean> = _notifyContextChanges.asStateFlow()

    private val _notifyBeaconBattery = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_BEACON_BATTERY, true))
    val notifyBeaconBattery: StateFlow<Boolean> = _notifyBeaconBattery.asStateFlow()

    // Tracking timestamps to debounce battery alerts (e.g. notify at most once per hour per beacon)
    private val lastAlertedBatteryTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    init {
        try {
            val repo = com.contextpulse.phase0.repository.NotificationPreferencesRepository.getInstance(context)
            _notifyContextChanges.value = repo.contextSwitchNotificationsEnabled.value && _notifyContextChanges.value
            _notifyBeaconBattery.value = repo.lowBatteryNotificationsEnabled.value && _notifyBeaconBattery.value
        } catch (_: Exception) {}
        createNotificationChannels()
    }

    fun setNotifyContextChanges(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_CONTEXT_CHANGES, enabled).apply()
        _notifyContextChanges.value = enabled
        try {
            com.contextpulse.phase0.repository.NotificationPreferencesRepository.getInstance(context).setContextSwitchNotificationsEnabled(enabled)
        } catch (_: Exception) {}
    }

    fun setNotifyBeaconBattery(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_BEACON_BATTERY, enabled).apply()
        _notifyBeaconBattery.value = enabled
        try {
            com.contextpulse.phase0.repository.NotificationPreferencesRepository.getInstance(context).setLowBatteryNotificationsEnabled(enabled)
        } catch (_: Exception) {}
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun isContextChangesEnabled(): Boolean = _notifyContextChanges.value
    fun isBeaconBatteryEnabled(): Boolean = _notifyBeaconBattery.value

    fun setContextChangesEnabled(enabled: Boolean) = setNotifyContextChanges(enabled)
    fun setBeaconBatteryEnabled(enabled: Boolean) = setNotifyBeaconBattery(enabled)

    fun notifyContextChange(oldContext: String?, newContext: String, reason: String) {
        notifyContextChanged(oldContext, newContext, reason)
    }

    fun notifyLowBattery(beaconName: String, batteryPercent: Int) {
        notifyLowBeaconBattery(beaconName, batteryPercent)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Channel for Context Changes
            val contextChannel = NotificationChannel(
                CHANNEL_CONTEXT_CHANGES,
                "Context Transitions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when the active Context changes"
                enableVibration(true)
            }

            // Channel for Beacon Alerts
            val beaconChannel = NotificationChannel(
                CHANNEL_BEACON_ALERTS,
                "Sensor & Beacon Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when BLE beacons have low battery"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(contextChannel)
            notificationManager.createNotificationChannel(beaconChannel)
        }
    }

    /**
     * Posts a device notification when context transitions occur.
     */
    fun notifyContextChanged(fromContextName: String?, toContextName: String, reason: String) {
        if (!_notifyContextChanges.value) return
        if (!isNotificationPermissionGranted()) return

        val fromLabel = fromContextName?.ifBlank { "Baseline (Default)" } ?: "Baseline (Default)"
        val message = "Context has changed from $fromLabel to $toContextName (Reason: $reason)"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_CONTEXT_SWITCH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CONTEXT_CHANGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Context Changed: $toContextName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CONTEXT_SWITCH, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    /**
     * Posts a notification when a beacon reports battery < 15%.
     */
    fun notifyLowBeaconBattery(beaconName: String, batteryPercent: Int) {
        if (batteryPercent < 0 || batteryPercent >= 15) return
        if (!_notifyBeaconBattery.value) return
        if (!isNotificationPermissionGranted()) return

        val now = System.currentTimeMillis()
        val lastSent = lastAlertedBatteryTimestamps[beaconName] ?: 0L
        // Debounce alert to at most once every 30 minutes for the same beacon
        if (now - lastSent < 30 * 60 * 1000L) {
            return
        }
        lastAlertedBatteryTimestamps[beaconName] = now

        val message = "Beacon '$beaconName' is low on charge ($batteryPercent%). Please recharge or replace its battery."
        val notifId = NOTIFICATION_ID_BEACON_BATTERY + (beaconName.hashCode() and 0x0FFF)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BEACON_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Beacon Battery Low ($batteryPercent%)")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    /**
     * Sends a test notification to verify channel configuration.
     */
    fun sendTestNotification() {
        notifyContextChanged(
            fromContextName = "Bedroom",
            toContextName = "Office Room",
            reason = "Manual Switch (Test)"
        )
    }
}
