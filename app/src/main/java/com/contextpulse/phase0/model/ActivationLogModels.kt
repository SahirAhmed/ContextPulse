package com.contextpulse.phase0.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TriggerSource {
    BLE_BEACON,
    NFC_TAG,
    QR_CODE,
    MANUAL,
    SYSTEM_RESTORE;

    val label: String
        get() = getDisplayName()

    fun getDisplayName(): String = when (this) {
        BLE_BEACON -> "BLE Beacon"
        NFC_TAG -> "NFC Tag"
        QR_CODE -> "QR Code"
        MANUAL -> "Manual Switch"
        SYSTEM_RESTORE -> "System Restore"
    }

    companion object {
        val BEACON_SIGNAL: TriggerSource get() = BLE_BEACON
        val NFC: TriggerSource get() = NFC_TAG
        val SYSTEM: TriggerSource get() = SYSTEM_RESTORE

        fun fromString(value: String): TriggerSource {
            return when (value.uppercase(Locale.ROOT)) {
                "BEACON_SIGNAL", "BLE_BEACON" -> BLE_BEACON
                "NFC_TAG", "NFC" -> NFC_TAG
                "QR_CODE", "QR" -> QR_CODE
                "MANUAL" -> MANUAL
                "SYSTEM_RESTORE", "SYSTEM" -> SYSTEM_RESTORE
                else -> MANUAL
            }
        }
    }
}

/**
 * Log entry recording a context activation event, including timestamp,
 * transition origin ("changed from"), and detailed reasoning.
 */
data class ProfileActivationLog(
    val id: String = UUID.randomUUID().toString(),
    val toProfileName: String,
    val fromProfileName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String,
    val triggerSource: TriggerSource = TriggerSource.BLE_BEACON
) {
    val timestampMillis: Long get() = timestamp
    val fromRoomName: String? get() = fromProfileName?.takeIf { it.isNotBlank() }
    val toRoomName: String? get() = toProfileName.takeIf { it.isNotBlank() && !it.equals("Baseline", ignoreCase = true) }

    fun getFormattedTime(): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    fun getFormattedDate(): String {
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("toProfileName", toProfileName)
            fromProfileName?.let { put("fromProfileName", it) }
            put("timestamp", timestamp)
            put("reason", reason)
            put("triggerSource", triggerSource.name)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ProfileActivationLog {
            return ProfileActivationLog(
                id = obj.optString("id", UUID.randomUUID().toString()),
                toProfileName = obj.optString("toProfileName", obj.optString("toRoomName", "Context")),
                fromProfileName = if (obj.has("fromProfileName") && !obj.isNull("fromProfileName")) {
                    obj.getString("fromProfileName").takeIf { it.isNotBlank() }
                } else if (obj.has("fromRoomName") && !obj.isNull("fromRoomName")) {
                    obj.getString("fromRoomName").takeIf { it.isNotBlank() }
                } else {
                    null
                },
                timestamp = obj.optLong("timestamp", obj.optLong("timestampMillis", System.currentTimeMillis())),
                reason = obj.optString("reason", "Context switch detected"),
                triggerSource = TriggerSource.fromString(obj.optString("triggerSource", "BLE_BEACON"))
            )
        }
    }
}

typealias ActivationLogEntry = ProfileActivationLog
