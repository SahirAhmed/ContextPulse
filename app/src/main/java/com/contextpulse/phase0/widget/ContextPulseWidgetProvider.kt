package com.contextpulse.phase0.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
 * Home Screen Widget for ContextPulse:
 * - Allows users to view and switch between up to 3 chosen Contexts directly from their home screen.
 * - Dynamically inherits the exact color scheme (including tertiary accents) chosen by the user.
 * - Accurately reflects live Snooze / Pause status and updates immediately when the timer expires.
 */
class ContextPulseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SWITCH_PROFILE = "com.contextpulse.phase0.widget.ACTION_SWITCH_PROFILE"
        const val ACTION_REFRESH_WIDGET = "com.contextpulse.phase0.widget.ACTION_REFRESH_WIDGET"
        const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, ContextPulseWidgetProvider::class.java)
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
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_contextpulse_4x1)

            val themeRepo = AppThemeRepository.getInstance(context)
            val currentTheme = themeRepo.currentTheme.value

            val roomRepo = RoomProfileRepository.getInstance(context)
            val allRooms = roomRepo.rooms.value
            val activeRoom = roomRepo.activeRoom.value
            val activeRoomId = activeRoom?.id

            val snoozeManager = SnoozeManager.getInstance(context)
            val isSnoozed = snoozeManager.isSnoozeActive()
            val remainingSnoozeMins = snoozeManager.getRemainingMinutes()

            val chosenProfileIds = WidgetPreferences.getChosenProfileIds(context, appWidgetId)
            val chosenRooms = mutableListOf<RoomItem?>()
            for (id in chosenProfileIds) {
                val match = allRooms.find { it.id == id }
                if (match != null) {
                    chosenRooms.add(match)
                }
            }

            while (chosenRooms.size < 3) {
                chosenRooms.add(null)
            }

            // 1. Theme-based Widget Root Background Card
            val bgBitmap = createCardBitmap(
                widthPx = 720,
                heightPx = 140,
                cornerRadiusPx = 28f,
                fillColor = currentTheme.surfaceHex.toInt(),
                strokeColor = currentTheme.borderHex.toInt(),
                strokeWidthPx = 3f
            )
            remoteViews.setImageViewBitmap(R.id.widget_bg, bgBitmap)

            // Brand Logo
            val iconDrawable = androidx.core.content.ContextCompat.getDrawable(context, currentTheme.getLauncherIconResId())
            if (iconDrawable != null) {
                val logoBmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(logoBmp)
                val path = Path().apply {
                    addRoundRect(RectF(0f, 0f, 96f, 96f), 22f, 22f, Path.Direction.CW)
                }
                canvas.clipPath(path)
                iconDrawable.setBounds(0, 0, 96, 96)
                iconDrawable.draw(canvas)
                remoteViews.setImageViewBitmap(R.id.widget_logo, logoBmp)
            } else {
                remoteViews.setImageViewResource(R.id.widget_logo, currentTheme.getLauncherIconResId())
            }

            val appLaunchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                1000 + appWidgetId,
                appLaunchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_brand_container, appPendingIntent)

            // 2. Configure Button (Tinted with theme secondary accent color)
            remoteViews.setInt(R.id.widget_configure_icon, "setColorFilter", currentTheme.accentHex.toInt())
            val configIntent = Intent(context, ContextPulseWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                2000 + appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_btn_configure, configPendingIntent)

            // 3. Render 3 Context Slots
            val slotContainers = listOf(R.id.slot_1_container, R.id.slot_2_container, R.id.slot_3_container)
            val slotBgs = listOf(R.id.slot_1_bg, R.id.slot_2_bg, R.id.slot_3_bg)
            val slotTitles = listOf(R.id.slot_1_title, R.id.slot_2_title, R.id.slot_3_title)
            val slotStatuses = listOf(R.id.slot_1_status, R.id.slot_2_status, R.id.slot_3_status)
            val slotIndicators = listOf(R.id.slot_1_indicator, R.id.slot_2_indicator, R.id.slot_3_indicator)

            for (i in 0 until 3) {
                val containerId = slotContainers[i]
                val bgId = slotBgs[i]
                val titleId = slotTitles[i]
                val statusId = slotStatuses[i]
                val indicatorId = slotIndicators[i]

                val room = chosenRooms.getOrNull(i)

                if (room != null) {
                    val isActive = room.id == activeRoomId

                    if (isActive) {
                        val activeCardBitmap = createCardBitmap(
                            widthPx = 210,
                            heightPx = 110,
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
                        val dotBitmap = createDotBitmap(16, dotColor)
                        remoteViews.setImageViewBitmap(indicatorId, dotBitmap)
                        remoteViews.setViewVisibility(indicatorId, View.VISIBLE)
                    } else {
                        val inactiveCardBitmap = createCardBitmap(
                            widthPx = 210,
                            heightPx = 110,
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

                    val switchIntent = Intent(context, ContextPulseWidgetProvider::class.java).apply {
                        action = ACTION_SWITCH_PROFILE
                        putExtra(EXTRA_ROOM_ID, room.id)
                    }
                    val switchPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (appWidgetId * 10) + i,
                        switchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    remoteViews.setOnClickPendingIntent(containerId, switchPendingIntent)
                } else {
                    val emptyCardBitmap = createCardBitmap(
                        widthPx = 210,
                        heightPx = 110,
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

                    // Tapping empty slot opens widget configuration directly
                    remoteViews.setOnClickPendingIntent(containerId, configPendingIntent)
                }
            }

            return remoteViews
        }

        private fun createCardBitmap(
            widthPx: Int,
            heightPx: Int,
            cornerRadiusPx: Float,
            fillColor: Int,
            strokeColor: Int? = null,
            strokeWidthPx: Float = 0f
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(10), heightPx.coerceAtLeast(10), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = fillColor
                style = Paint.Style.FILL
            }
            val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

            if (strokeColor != null && strokeWidthPx > 0f) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = strokeColor
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidthPx
                }
                val inset = strokeWidthPx / 2f
                val strokeRect = RectF(inset, inset, widthPx - inset, heightPx - inset)
                canvas.drawRoundRect(strokeRect, cornerRadiusPx, cornerRadiusPx, strokePaint)
            }
            return bitmap
        }

        private fun createDotBitmap(sizePx: Int, color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.5f, paint)
            return bitmap
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
                    updateAllWidgets(context)
                }
            }
            ACTION_REFRESH_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // Ensure SnoozeManager evaluates expiration before refreshing widgets
                SnoozeManager.getInstance(context).isSnoozeActive()
                updateAllWidgets(context)
            }
        }
    }
}
