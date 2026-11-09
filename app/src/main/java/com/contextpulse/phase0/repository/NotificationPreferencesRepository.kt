package com.contextpulse.phase0.repository

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
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user preferences for device notifications (Context Switch notifications
 * and Low Beacon Battery alerts) and dispatches system notifications.
 */
class NotificationPreferencesRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _contextSwitchNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_CONTEXT_SWITCH_NOTIFS, true)
    )
    val contextSwitchNotificationsEnabled: StateFlow<Boolean> =
        _contextSwitchNotificationsEnabled.asStateFlow()

    private val _lowBatteryNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOW_BATTERY_NOTIFS, true)
    )
    val lowBatteryNotificationsEnabled: StateFlow<Boolean> =
        _lowBatteryNotificationsEnabled.asStateFlow()

    fun setContextSwitchNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTEXT_SWITCH_NOTIFS, enabled).apply()
        _contextSwitchNotificationsEnabled.value = enabled
    }

    fun setLowBatteryNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_BATTERY_NOTIFS, enabled).apply()
        _lowBatteryNotificationsEnabled.value = enabled
        if (enabled) {
            try {
                BeaconRepository.getInstance(appContext).checkAndNotifyTriggerStatus()
            } catch (_: Exception) {
            }
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.areNotificationsEnabled()
        }
    }

    fun sendContextSwitchNotification(
        fromContextName: String?,
        toContextName: String?,
        triggerSourceLabel: String,
        reason: String
    ) {
        if (!_contextSwitchNotificationsEnabled.value) return
        if (!com.contextpulse.phase0.system.ContextPulseNotificationManager.getInstance(appContext).isContextChangesEnabled()) return
        if (!hasNotificationPermission()) return

        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_CONTEXT_SWITCH,
                    "Context Switch Activity",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when ContextPulse switches between contexts"
                }
                nm.createNotificationChannel(channel)
            }

            val reasonSummary = when {
                triggerSourceLabel.equals("Manual Switch", ignoreCase = true) -> "Manual Switch"
                triggerSourceLabel.isNotBlank() -> triggerSourceLabel
                reason.isNotBlank() -> reason
                else -> "Context Switch"
            }

            val bodyText = when {
                !fromContextName.isNullOrBlank() && !toContextName.isNullOrBlank() ->
                    "Context has changed from $fromContextName to $toContextName (Reason: $reasonSummary)"
                fromContextName.isNullOrBlank() && !toContextName.isNullOrBlank() ->
                    "Context has changed to $toContextName (Reason: $reasonSummary)"
                !fromContextName.isNullOrBlank() && toContextName.isNullOrBlank() ->
                    "Context has changed from $fromContextName to Baseline (Reason: $reasonSummary)"
                else ->
                    "Context state updated (Reason: $reasonSummary)"
            }

            val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                9100,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_CONTEXT_SWITCH)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Context Switched")
                .setContentText(bodyText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(NOTIFICATION_ID_CONTEXT_SWITCH, notification)
        } catch (_: Exception) {
            // Ignore in headless unit test environments
        }
    }

    companion object {
        private const val PREFS_NAME = "contextpulse_notification_prefs"
        private const val KEY_CONTEXT_SWITCH_NOTIFS = "context_switch_notifs_enabled"
        private const val KEY_LOW_BATTERY_NOTIFS = "low_battery_notifs_enabled"
        private const val CHANNEL_CONTEXT_SWITCH = "contextpulse_context_switches"
        private const val NOTIFICATION_ID_CONTEXT_SWITCH = 7701

        @Volatile
        private var INSTANCE: NotificationPreferencesRepository? = null

        fun getInstance(context: Context): NotificationPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationPreferencesRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
