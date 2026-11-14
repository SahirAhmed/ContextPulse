package com.contextpulse.phase0.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.ProfileActivationHelper
import com.contextpulse.phase0.system.SnoozeManager
import com.contextpulse.phase0.ui.MainActivity
import com.contextpulse.phase0.ui.theme.AppThemeRepository
import com.example.R

/**
 * 4x2 Quick Switch Context (6) Home Screen Widget:
 * - Allows users to view and switch between up to 6 chosen Contexts directly from their home screen.
 * - Dynamically inherits the active ContextPulse color scheme and launcher badge.
 * - Reflects live Snooze / Pause status and updates across all widgets on switch.
 */
class QuickSwitch6WidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SWITCH_PROFILE = "com.contextpulse.phase0.widget.ACTION_SWITCH_PROFILE_6"
        const val ACTION_REFRESH_WIDGET = "com.contextpulse.phase0.widget.ACTION_REFRESH_WIDGET_6"
        const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        private const val MAX_SLOTS = 6

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuickSwitch6WidgetProvider::class.java)
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
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_quick_switch_6)

            val themeRepo = AppThemeRepository.getInstance(context)
            val currentTheme = themeRepo.currentTheme.value

            val roomRepo = RoomProfileRepository.getInstance(context)
            val allRooms = roomRepo.rooms.value
            val activeRoomId = roomRepo.activeRoom.value?.id

            val snoozeManager = SnoozeManager.getInstance(context)
            val isSnoozed = snoozeManager.isSnoozeActive()
            val remainingSnoozeMins = snoozeManager.getRemainingMinutes()

            val chosenProfileIds = WidgetPreferences.getChosenProfileIds(context, appWidgetId, MAX_SLOTS)
            val chosenRooms = mutableListOf<RoomItem?>()
            for (id in chosenProfileIds) {
                val match = allRooms.find { it.id == id }
                if (match != null) {
                    chosenRooms.add(match)
                }
            }
            while (chosenRooms.size < MAX_SLOTS) {
                chosenRooms.add(null)
            }

            // 1. Theme-based Widget Root Background Card (4x2 dimensions)
            val bgBitmap = ContextPulseWidgetProvider.createCardBitmap(
                widthPx = 720,
                heightPx = 280,
                cornerRadiusPx = 28f,
                fillColor = currentTheme.surfaceHex.toInt(),
                strokeColor = currentTheme.borderHex.toInt(),
                strokeWidthPx = 3f
            )
            remoteViews.setImageViewBitmap(R.id.widget_bg, bgBitmap)

            // Brand Logo
            val logoBmp = ContextPulseWidgetProvider.createThemeLogoBitmap(
                context,
                currentTheme.getLauncherIconResId(),
                96,
                22f
            )
            if (logoBmp != null) {
                remoteViews.setImageViewBitmap(R.id.widget_logo, logoBmp)
            } else {
                remoteViews.setImageViewResource(R.id.widget_logo, currentTheme.getLauncherIconResId())
            }

            val appLaunchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                31000 + appWidgetId,
                appLaunchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_brand_container, appPendingIntent)

            // 2. Configure Button
            remoteViews.setInt(R.id.widget_configure_icon, "setColorFilter", currentTheme.accentHex.toInt())
            val configIntent = Intent(context, ContextPulseWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(ContextPulseWidgetConfigureActivity.EXTRA_MAX_SLOTS, MAX_SLOTS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                32000 + appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_btn_configure, configPendingIntent)

            // 3. Render 6 Context Slots
            val slotContainers = listOf(
                R.id.slot_1_container, R.id.slot_2_container, R.id.slot_3_container,
                R.id.slot_4_container, R.id.slot_5_container, R.id.slot_6_container
            )
            val slotBgs = listOf(
                R.id.slot_1_bg, R.id.slot_2_bg, R.id.slot_3_bg,
                R.id.slot_4_bg, R.id.slot_5_bg, R.id.slot_6_bg
            )
            val slotTitles = listOf(
                R.id.slot_1_title, R.id.slot_2_title, R.id.slot_3_title,
                R.id.slot_4_title, R.id.slot_5_title, R.id.slot_6_title
            )
            val slotStatuses = listOf(
                R.id.slot_1_status, R.id.slot_2_status, R.id.slot_3_status,
                R.id.slot_4_status, R.id.slot_5_status, R.id.slot_6_status
            )
            val slotIndicators = listOf(
                R.id.slot_1_indicator, R.id.slot_2_indicator, R.id.slot_3_indicator,
                R.id.slot_4_indicator, R.id.slot_5_indicator, R.id.slot_6_indicator
            )

            for (i in 0 until MAX_SLOTS) {
                val containerId = slotContainers[i]
                val bgId = slotBgs[i]
                val titleId = slotTitles[i]
                val statusId = slotStatuses[i]
                val indicatorId = slotIndicators[i]
                val room = chosenRooms.getOrNull(i)

                if (room != null) {
                    val isActive = room.id == activeRoomId

                    if (isActive) {
                        val activeCardBitmap = ContextPulseWidgetProvider.createCardBitmap(
                            widthPx = 210,
                            heightPx = 115,
                            cornerRadiusPx = 20f,
                            fillColor = currentTheme.primaryHex.toInt(),
                            strokeColor = null
                        )
                        remoteViews.setImageViewBitmap(bgId, activeCardBitmap)
                        remoteViews.setTextViewText(titleId, room.name)
                        remoteViews.setTextColor(titleId, currentTheme.onPrimaryHex.toInt())

                        val activeLabel = if (isSnoozed && remainingSnoozeMins > 0) {
                            "PAUSED (${remainingSnoozeMins}m)"
                        } else {
                            "ACTIVE"
                        }
                        remoteViews.setTextViewText(statusId, activeLabel)
                        remoteViews.setTextColor(statusId, currentTheme.onPrimaryHex.toInt())

                        val dotColor = if (isSnoozed && remainingSnoozeMins > 0) {
                            0xFFF59E0B.toInt()
                        } else {
                            currentTheme.onPrimaryHex.toInt()
                        }
                        remoteViews.setImageViewBitmap(indicatorId, ContextPulseWidgetProvider.createDotBitmap(16, dotColor))
                        remoteViews.setViewVisibility(indicatorId, View.VISIBLE)
                    } else {
                        val inactiveCardBitmap = ContextPulseWidgetProvider.createCardBitmap(
                            widthPx = 210,
                            heightPx = 115,
                            cornerRadiusPx = 20f,
                            fillColor = currentTheme.surfaceVariantHex.toInt(),
                            strokeColor = currentTheme.borderHex.toInt(),
                            strokeWidthPx = 2f
                        )
                        remoteViews.setImageViewBitmap(bgId, inactiveCardBitmap)
                        remoteViews.setTextViewText(titleId, room.name)
                        remoteViews.setTextColor(titleId, currentTheme.textPrimaryHex.toInt())

                        remoteViews.setTextViewText(statusId, "Switch")
                        remoteViews.setTextColor(statusId, currentTheme.tertiaryHex.toInt())
                        remoteViews.setViewVisibility(indicatorId, View.GONE)
                    }

                    val switchIntent = Intent(context, QuickSwitch6WidgetProvider::class.java).apply {
                        action = ACTION_SWITCH_PROFILE
                        putExtra(EXTRA_ROOM_ID, room.id)
                    }
                    val switchPendingIntent = PendingIntent.getBroadcast(
                        context,
                        50000 + (appWidgetId * 10) + i,
                        switchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    remoteViews.setOnClickPendingIntent(containerId, switchPendingIntent)
                } else {
                    val emptyCardBitmap = ContextPulseWidgetProvider.createCardBitmap(
                        widthPx = 210,
                        heightPx = 115,
                        cornerRadiusPx = 20f,
                        fillColor = (currentTheme.surfaceHex.toInt() and 0x00FFFFFF) or 0x44000000,
                        strokeColor = currentTheme.borderHex.toInt(),
                        strokeWidthPx = 1.5f
                    )
                    remoteViews.setImageViewBitmap(bgId, emptyCardBitmap)
                    remoteViews.setTextViewText(titleId, "+ Context")
                    remoteViews.setTextColor(titleId, currentTheme.tertiaryHex.toInt())
                    remoteViews.setTextViewText(statusId, "Configure")
                    remoteViews.setTextColor(statusId, currentTheme.textSecondaryHex.toInt())
                    remoteViews.setViewVisibility(indicatorId, View.GONE)

                    remoteViews.setOnClickPendingIntent(containerId, configPendingIntent)
                }
            }

            return remoteViews
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetPreferences.deleteWidgetPreferences(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_SWITCH_PROFILE -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID)
                if (!roomId.isNullOrBlank()) {
                    val roomRepo = RoomProfileRepository.getInstance(context)
                    val targetRoom = roomRepo.rooms.value.find { it.id == roomId }

                    val success = ProfileActivationHelper.activateProfile(
                        context = context,
                        roomId = roomId,
                        triggerSource = TriggerSource.MANUAL,
                        reason = "Switched via Home Screen Widget"
                    )

                    if (success && targetRoom != null) {
                        Toast.makeText(context, "ContextPulse: '${targetRoom.name}' context activated", Toast.LENGTH_SHORT).show()
                    }
                    ContextPulseWidgetProvider.updateAllWidgets(context)
                }
            }
            ACTION_REFRESH_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                SnoozeManager.getInstance(context).isSnoozeActive()
                updateAllWidgets(context)
            }
        }
    }
}
