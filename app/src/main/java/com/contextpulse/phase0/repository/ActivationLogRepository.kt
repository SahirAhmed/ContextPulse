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
 * Persists a complete history of context activations and deactivations until cleared
 * and dispatches device notifications when Context Switch Notifications are enabled.
 */
class ActivationLogRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _logs = MutableStateFlow<List<ProfileActivationLog>>(emptyList())
    val logs: StateFlow<List<ProfileActivationLog>> = _logs.asStateFlow()
    val recentLogs: StateFlow<List<ProfileActivationLog>> get() = logs
    val activationLogs: StateFlow<List<ProfileActivationLog>> get() = logs

    init {
        loadLogs()
    }

    private fun loadLogs() {
        val raw = prefs.getString(KEY_LOGS_JSON, null)
        if (raw.isNullOrBlank()) {
            _logs.value = emptyList()
            return
        }

        try {
            val arr = JSONArray(raw)
            val parsed = mutableListOf<ProfileActivationLog>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                parsed.add(ProfileActivationLog.fromJsonObject(obj))
            }
            _logs.value = parsed
        } catch (_: Exception) {
            _logs.value = emptyList()
        }
    }

    private fun defaultSeedLogs(): List<ProfileActivationLog> {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour
        return listOf(
            ProfileActivationLog(
                toProfileName = "Living Room",
                fromProfileName = "Home Office",
                timestamp = now - 25 * 60 * 1000L,
                reason = "BLE beacon signal entered threshold (-56 dBm)",
                triggerSource = TriggerSource.BLE_BEACON
            ),
            ProfileActivationLog(
                toProfileName = "Home Office",
                fromProfileName = "Bedroom",
                timestamp = now - 4 * oneHour,
                reason = "Context manually switched by user",
                triggerSource = TriggerSource.MANUAL
            ),
            ProfileActivationLog(
                toProfileName = "Bedroom",
                fromProfileName = "Living Room",
                timestamp = now - 1 * oneDay - 2 * oneHour,
                reason = "NFC Tag scanned: Nightstand NFC",
                triggerSource = TriggerSource.NFC_TAG
            ),
            ProfileActivationLog(
                toProfileName = "Living Room",
                fromProfileName = "Home Office",
                timestamp = now - 2 * oneDay - 5 * oneHour,
                reason = "BLE beacon signal entered threshold (-54 dBm)",
                triggerSource = TriggerSource.BLE_BEACON
            ),
            ProfileActivationLog(
                toProfileName = "Home Office",
                fromProfileName = "Living Room",
                timestamp = now - 3 * oneDay - 8 * oneHour,
                reason = "QR Code scanned: Desk QR Trigger",
                triggerSource = TriggerSource.QR_CODE
            ),
            ProfileActivationLog(
                toProfileName = "Mosque",
                fromProfileName = "Living Room",
                timestamp = now - 5 * oneDay - 3 * oneHour,
                reason = "NFC Tag scanned: Car Mount NFC",
                triggerSource = TriggerSource.NFC_TAG
            ),
            ProfileActivationLog(
                toProfileName = "Home Office",
                fromProfileName = "Bedroom",
                timestamp = now - 7 * oneDay - 1 * oneHour,
                reason = "BLE beacon signal entered threshold (-58 dBm)",
                triggerSource = TriggerSource.BLE_BEACON
            ),
            ProfileActivationLog(
                toProfileName = "Bedroom",
                fromProfileName = "Living Room",
                timestamp = now - 10 * oneDay - 6 * oneHour,
                reason = "Context manually switched by user",
                triggerSource = TriggerSource.MANUAL
            ),
            ProfileActivationLog(
                toProfileName = "Living Room",
                fromProfileName = "Home Office",
                timestamp = now - 14 * oneDay - 4 * oneHour,
                reason = "BLE beacon signal entered threshold (-55 dBm)",
                triggerSource = TriggerSource.BLE_BEACON
            ),
            ProfileActivationLog(
                toProfileName = "Home Office",
                fromProfileName = "Bedroom",
                timestamp = now - 18 * oneDay - 2 * oneHour,
                reason = "QR Code scanned: Office Door QR",
                triggerSource = TriggerSource.QR_CODE
            ),
            ProfileActivationLog(
                toProfileName = "Bedroom",
                fromProfileName = "Living Room",
                timestamp = now - 21 * oneDay - 7 * oneHour,
                reason = "NFC Tag scanned: Nightstand NFC",
                triggerSource = TriggerSource.NFC_TAG
            )
        )
    }

    fun logActivation(
        toProfileName: String,
        fromProfileName: String?,
        reason: String,
        triggerSource: TriggerSource = TriggerSource.MANUAL
    ): ProfileActivationLog {
        val entry = ProfileActivationLog(
            toProfileName = toProfileName,
            fromProfileName = fromProfileName,
            timestamp = System.currentTimeMillis(),
            reason = reason,
            triggerSource = triggerSource
        )

        val updated = listOf(entry) + _logs.value
        saveLogsInternal(updated)

        try {
            NotificationPreferencesRepository.getInstance(appContext)
                .sendContextSwitchNotification(
                    fromContextName = fromProfileName,
                    toContextName = toProfileName.takeIf { !it.equals("Baseline", ignoreCase = true) },
                    triggerSourceLabel = triggerSource.getDisplayName(),
                    reason = reason
                )
        } catch (_: Exception) {
        }

        return entry
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS_JSON).apply()
        _logs.value = emptyList()
    }

    fun resetToDefaultSeedLogs() {
        clearLogs()
    }

    fun clearAll() = clearLogs()

    private fun saveLogsInternal(entries: List<ProfileActivationLog>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(entry.toJsonObject())
        }
        prefs.edit().putString(KEY_LOGS_JSON, arr.toString()).apply()
        _logs.value = entries
    }

    companion object {
        private const val PREFS_NAME = "contextpulse_activation_logs_v1"
        private const val KEY_LOGS_JSON = "activation_logs_json_v1"

        @Volatile
        private var INSTANCE: ActivationLogRepository? = null

        fun getInstance(context: Context): ActivationLogRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivationLogRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
