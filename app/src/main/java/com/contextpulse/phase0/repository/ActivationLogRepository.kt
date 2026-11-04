package com.contextpulse.phase0.repository

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.ProfileActivationLog
import com.contextpulse.phase0.model.TriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Persistent repository storing profile activation and transition logs with detailed reasoning.
 */
class ActivationLogRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_activation_logs_v1"
        private const val KEY_LOGS_JSON = "key_logs_json"

        @Volatile
        private var INSTANCE: ActivationLogRepository? = null

        fun getInstance(context: Context): ActivationLogRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivationLogRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activationLogs = MutableStateFlow<List<ProfileActivationLog>>(emptyList())
    val activationLogs: StateFlow<List<ProfileActivationLog>> = _activationLogs.asStateFlow()

    init {
        loadLogs()
    }

    @Synchronized
    fun loadLogs() {
        val wasCleared = prefs.getBoolean("key_logs_cleared", false)
        val json = prefs.getString(KEY_LOGS_JSON, null)
        if (wasCleared || json == "[]") {
            _activationLogs.value = emptyList()
            return
        }

        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val list = if (json.isNullOrEmpty()) {
            saveLogs(emptyList())
            emptyList()
        } else {
            try {
                val array = JSONArray(json)
                val items = mutableListOf<ProfileActivationLog>()
                for (i in 0 until array.length()) {
                    val log = ProfileActivationLog.fromJsonObject(array.getJSONObject(i))
                    // 7-day retention policy: discard logs older than 7 days
                    if (log.timestamp >= sevenDaysAgo) {
                        items.add(log)
                    }
                }
                items
            } catch (e: Exception) {
                emptyList()
            }
        }
        _activationLogs.value = list
    }

    @Synchronized
    fun logActivation(
        toProfileName: String,
        fromProfileName: String?,
        reason: String,
        triggerSource: TriggerSource = TriggerSource.BEACON_SIGNAL
    ): ProfileActivationLog {
        val newEntry = ProfileActivationLog(
            toProfileName = toProfileName,
            fromProfileName = fromProfileName,
            timestamp = System.currentTimeMillis(),
            reason = reason,
            triggerSource = triggerSource
        )
        val current = _activationLogs.value.toMutableList()
        current.add(0, newEntry)
        // Keep last 40 logs
        if (current.size > 40) {
            current.removeAt(current.size - 1)
        }
        _activationLogs.value = current
        saveLogs(current)
        return newEntry
    }

    @Synchronized
    fun clearLogs() {
        _activationLogs.value = emptyList()
        prefs.edit().putString(KEY_LOGS_JSON, "[]").putBoolean("key_logs_cleared", true).commit()
    }

    @Synchronized
    fun resetToFactoryDefaults() {
        prefs.edit().clear().commit()
        saveLogs(emptyList())
        _activationLogs.value = emptyList()
    }

    private fun saveLogs(list: List<ProfileActivationLog>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_LOGS_JSON, array.toString()).apply()
    }
}
