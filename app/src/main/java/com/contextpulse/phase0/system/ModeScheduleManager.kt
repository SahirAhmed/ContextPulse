package com.contextpulse.phase0.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.repository.RoomProfileRepository
import java.util.Calendar

/**
 * Manages calculating and scheduling exact or low-drift AlarmManager wakeups
 * for scheduled Context/Mode timeframe boundaries (e.g. 09:00 Work Mode, 17:00 Default Mode).
 *
 * Eliminates 24/7 background polling loops while respecting Android 12+ exact alarm restrictions.
 */
object ModeScheduleManager {

    private const val TAG = "ModeScheduleManager"
    const val ACTION_SCHEDULE_BOUNDARY = "com.contextpulse.phase0.system.ACTION_SCHEDULE_BOUNDARY"
    private const val ALARM_REQUEST_CODE = 9902

    /**
     * Calculates the nearest upcoming mode boundary timestamp (in ms) and schedules an alarm.
     */
    fun scheduleNextBoundaryAlarm(context: Context) {
        val roomRepo = RoomProfileRepository.getInstance(context)
        val activeRoom = roomRepo.activeRoom.value
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ScheduleBoundaryReceiver::class.java).apply {
            action = ACTION_SCHEDULE_BOUNDARY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (activeRoom == null || activeRoom.profile.modes.isEmpty()) {
            // No active context or no modes configured: cancel any pending alarm
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "No active room or modes; boundary alarm cancelled.")
            return
        }

        val nextTriggerMillis = calculateNextBoundaryMillis(activeRoom)
        if (nextTriggerMillis == null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "No timeframe modes configured; boundary alarm cancelled.")
            return
        }

        val delaySeconds = (nextTriggerMillis - System.currentTimeMillis()) / 1000
        Log.d(TAG, "Scheduling next mode boundary in ${delaySeconds}s (at epoch $nextTriggerMillis)")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed scheduling mode boundary alarm with exact; falling back to inexact window", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Critical failure scheduling boundary alarm", e2)
            }
        }
    }

    /**
     * Finds the soonest upcoming start or end time among all timeframe-enabled modes in this room.
     */
    fun calculateNextBoundaryMillis(room: RoomItem): Long? {
        val modes = room.profile.modes.filter { !it.isAllTime && it.isEnabled }
        if (modes.isEmpty()) return null

        val now = Calendar.getInstance()
        var soonestMillis: Long? = null

        // Check boundaries across today and the next 7 days
        for (dayOffset in 0..7) {
            val checkCal = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calDay = checkCal.get(Calendar.DAY_OF_WEEK)
            // Convert Calendar (Sunday=1, Monday=2..Saturday=7) to 1=Mon..7=Sun
            val dayOfWeek1to7 = if (calDay == Calendar.SUNDAY) 7 else calDay - 1

            for (mode in modes) {
                if (mode.daysOfWeek.isNotEmpty() && !mode.daysOfWeek.contains(dayOfWeek1to7)) {
                    continue
                }

                val boundaryPoints = listOf(
                    Pair(mode.startHour, mode.startMinute),
                    Pair(mode.endHour, mode.endMinute)
                )

                for (point in boundaryPoints) {
                    val boundaryCal = (checkCal.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, point.first)
                        set(Calendar.MINUTE, point.second)
                    }

                    val boundaryMillis = boundaryCal.timeInMillis
                    // Must be strictly in the future (at least 2 seconds ahead)
                    if (boundaryMillis > now.timeInMillis + 2000L) {
                        if (soonestMillis == null || boundaryMillis < soonestMillis) {
                            soonestMillis = boundaryMillis
                        }
                    }
                }
            }
        }

        return soonestMillis
    }

    fun cancelBoundaryAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleBoundaryReceiver::class.java).apply {
            action = ACTION_SCHEDULE_BOUNDARY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
