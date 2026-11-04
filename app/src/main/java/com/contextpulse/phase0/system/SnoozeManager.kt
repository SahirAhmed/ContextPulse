package com.contextpulse.phase0.system

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.widget.ContextPulseWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages temporary automation pause (Snooze / Sleep mode)
 * Allows users to temporarily suspend all profile automations & app blocking for 5m, 10m, 30m, or 1h.
 */
class SnoozeManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_snooze_prefs"
        private const val KEY_SNOOZE_END_TIME = "key_snooze_end_time"

        @Volatile
        private var INSTANCE: SnoozeManager? = null

        fun getInstance(context: Context): SnoozeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SnoozeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snoozeEndTime = MutableStateFlow(prefs.getLong(KEY_SNOOZE_END_TIME, 0L))
    val snoozeEndTime: StateFlow<Long> = _snoozeEndTime.asStateFlow()

    fun isSnoozeActive(): Boolean {
        val endTime = _snoozeEndTime.value
        return endTime > System.currentTimeMillis()
    }

    fun getRemainingSeconds(): Long {
        val diff = _snoozeEndTime.value - System.currentTimeMillis()
        return if (diff > 0) diff / 1000L else 0L
    }

    fun getRemainingMinutes(): Int {
        val diff = _snoozeEndTime.value - System.currentTimeMillis()
        return if (diff > 0) ((diff / 60000) + 1).toInt() else 0
    }

    fun getFormattedRemainingTime(): String {
        val totalSec = getRemainingSeconds()
        if (totalSec <= 0) return "00:00"
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    fun getFormattedRemainingDetailed(): String {
        val totalSec = getRemainingSeconds()
        if (totalSec <= 0) return "0s"
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    fun startSnooze(durationMinutes: Int) {
        val endTime = System.currentTimeMillis() + durationMinutes * 60 * 1000L
        prefs.edit().putLong(KEY_SNOOZE_END_TIME, endTime).apply()
        _snoozeEndTime.value = endTime

        try {
            ContextPulseWidgetProvider.updateAllWidgets(context)
        } catch (e: Throwable) {
            // Ignored
        }
    }

    fun cancelSnooze() {
        prefs.edit().remove(KEY_SNOOZE_END_TIME).apply()
        _snoozeEndTime.value = 0L

        try {
            ContextPulseWidgetProvider.updateAllWidgets(context)
        } catch (e: Throwable) {
            // Ignored
        }
    }
}
