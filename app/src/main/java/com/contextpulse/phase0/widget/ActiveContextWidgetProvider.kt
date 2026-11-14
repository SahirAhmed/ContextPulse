package com.contextpulse.phase0.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.SnoozeManager
import com.contextpulse.phase0.ui.MainActivity
import com.contextpulse.phase0.ui.theme.AppThemeRepository
import com.example.R

/**
 * 2x2 Active Context Home Screen Widget:
 * - Mirrors the top Active Context card on the Home screen cleanly, focusing on status, name, and live active duration.
 * - Timer persists across mode switches and resets only when the active context changes.
 * - Dynamically adapts to the active ContextPulse color scheme and Snooze state.
 */
class ActiveContextWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.contextpulse.phase0.widget.ACTION_REFRESH_ACTIVE_WIDGET"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, ActiveContextWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val remoteViews = buildRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_active_context_2x2)

            val currentTheme = AppThemeRepository.getInstance(context).currentTheme.value
            val roomRepo = RoomProfileRepository.getInstance(context)
            val activeRoom = roomRepo.activeRoom.value
            val startedAtMillis = roomRepo.activeRoomStartedAt.value

            val snoozeManager = SnoozeManager.getInstance(context)
            val isSnoozed = snoozeManager.isSnoozeActive()
            val remainingSnoozeMins = snoozeManager.getRemainingMinutes()

            val strokeColor = if (activeRoom != null) {
                currentTheme.primaryHex.toInt()
            } else {
                currentTheme.borderHex.toInt()
            }
            val bgBitmap = ContextPulseWidgetProvider.createCardBitmap(
                widthPx = 360,
                heightPx = 360,
                cornerRadiusPx = 28f,
                fillColor = currentTheme.surfaceHex.toInt(),
                strokeColor = strokeColor,
                strokeWidthPx = if (activeRoom != null) 4f else 3f
            )
            remoteViews.setImageViewBitmap(R.id.widget_active_bg, bgBitmap)

            val dotColor = when {
                isSnoozed -> currentTheme.accentHex.toInt()
                activeRoom != null -> currentTheme.primaryHex.toInt()
                else -> (currentTheme.textSecondaryHex.toInt() and 0x00FFFFFF) or 0x80000000.toInt()
            }
            remoteViews.setImageViewBitmap(
                R.id.widget_active_dot,
                ContextPulseWidgetProvider.createDotBitmap(18, dotColor)
            )

            val statusLabel = when {
                isSnoozed -> "AUTOMATIONS PAUSED"
                activeRoom != null -> "ACTIVE CONTEXT"
                else -> "BASELINE STATE"
            }
            val statusColor = when {
                isSnoozed -> currentTheme.accentHex.toInt()
                activeRoom != null -> currentTheme.primaryHex.toInt()
                else -> currentTheme.accentHex.toInt()
            }
            remoteViews.setTextViewText(R.id.widget_active_status_label, statusLabel)
            remoteViews.setTextColor(R.id.widget_active_status_label, statusColor)

            val logoBmp = ContextPulseWidgetProvider.createThemeLogoBitmap(
                context,
                currentTheme.getLauncherIconResId(),
                64,
                16f
            )
            if (logoBmp != null) {
                remoteViews.setImageViewBitmap(R.id.widget_active_logo, logoBmp)
            } else {
                remoteViews.setImageViewResource(R.id.widget_active_logo, currentTheme.getLauncherIconResId())
            }

            remoteViews.setTextViewText(
                R.id.widget_active_name,
                activeRoom?.name ?: "No Active Context"
            )
            remoteViews.setTextColor(R.id.widget_active_name, currentTheme.textPrimaryHex.toInt())

            val subtitleText = when {
                isSnoozed && remainingSnoozeMins > 0 -> "Paused (${remainingSnoozeMins}m left)"
                isSnoozed -> "Automations paused"
                activeRoom != null -> "Active on device"
                else -> "Default baseline state"
            }
            val subtitleColor = when {
                isSnoozed -> currentTheme.accentHex.toInt()
                activeRoom != null -> currentTheme.tertiaryHex.toInt()
                else -> currentTheme.textSecondaryHex.toInt()
            }
            remoteViews.setTextViewText(R.id.widget_active_subtitle, subtitleText)
            remoteViews.setTextColor(R.id.widget_active_subtitle, subtitleColor)

            if (activeRoom != null && startedAtMillis != null) {
                val elapsedMs = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
                val chronometerBase = SystemClock.elapsedRealtime() - elapsedMs

                val timerPillBg = ContextPulseWidgetProvider.createCardBitmap(
                    widthPx = 220,
                    heightPx = 56,
                    cornerRadiusPx = 16f,
                    fillColor = currentTheme.surfaceVariantHex.toInt(),
                    strokeColor = currentTheme.borderHex.toInt(),
                    strokeWidthPx = 2f
                )
                remoteViews.setImageViewBitmap(R.id.widget_active_timer_bg, timerPillBg)
                remoteViews.setImageViewBitmap(
                    R.id.widget_active_timer_dot,
                    ContextPulseWidgetProvider.createDotBitmap(
                        12,
                        if (isSnoozed) currentTheme.accentHex.toInt() else currentTheme.primaryHex.toInt()
                    )
                )
                remoteViews.setTextColor(R.id.widget_active_timer, currentTheme.textPrimaryHex.toInt())
                remoteViews.setChronometer(R.id.widget_active_timer, chronometerBase, "Active %s", true)
                remoteViews.setViewVisibility(R.id.widget_active_timer_container, View.VISIBLE)
            } else {
                remoteViews.setChronometer(R.id.widget_active_timer, SystemClock.elapsedRealtime(), "%s", false)
                remoteViews.setViewVisibility(R.id.widget_active_timer_container, View.GONE)
            }

            val appLaunchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                70000 + appWidgetId,
                appLaunchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_active_root, appPendingIntent)

            return remoteViews
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_REFRESH_WIDGET || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            SnoozeManager.getInstance(context).isSnoozeActive()
            updateAllWidgets(context)
        }
    }
}
