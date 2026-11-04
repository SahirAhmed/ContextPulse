package com.contextpulse.phase0.model

import org.json.JSONObject
import java.util.UUID

/**
 * Trigger hardware / detection connection type.
 */
enum class TriggerType {
    UNCONFIGURED,
    BLE_BEACON,
    NFC_TAG,
    QR_CODE;

    fun getDisplayName(): String = when (this) {
        UNCONFIGURED -> "Needs Configuration"
        BLE_BEACON -> "BLE Proximity Beacon"
        NFC_TAG -> "NFC Chip Tag"
        QR_CODE -> "Room QR Code"
    }
}

typealias SensorType = TriggerType

/**
 * Live broadcast health status for sensors/triggers.
 */
enum class BroadcastStatus {
    BROADCASTING,  // Active and broadcasting properly (Green)
    LOW_BATTERY,   // Battery <= 20% (Amber)
    OFFLINE;       // Hardware stopped broadcasting or signal lost (Red)

    fun getDisplayName(): String = when (this) {
        BROADCASTING -> "Broadcasting"
        LOW_BATTERY -> "Low Battery (<20%)"
        OFFLINE -> "Broadcast Stopped"
    }
}

/**
 * Trigger item representation supporting BLE beacons, NFC chips, and QR codes.
 */
data class TriggerItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TriggerType = TriggerType.BLE_BEACON,
    val isConfigured: Boolean = true,
    val code: String = "", // e.g. "CP-A1F4" or hardware UID
    val assignedRoomName: String? = null, // e.g. "Living Room", "Home Office", or null for Unassigned
    val batteryPercent: Int = 100, // 0..100 %
    val rssi: Int = -50, // in dBm
    val macAddress: String = "",
    val nfcTagUid: String = "",
    val qrPayload: String = "",
    val isOffline: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getBroadcastStatus(): BroadcastStatus {
        if (!isConfigured) return BroadcastStatus.OFFLINE
        if (isOffline) return BroadcastStatus.OFFLINE
        if (type == TriggerType.BLE_BEACON && batteryPercent in 1..20) return BroadcastStatus.LOW_BATTERY
        return BroadcastStatus.BROADCASTING
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type.name)
            put("isConfigured", isConfigured)
            put("code", code)
            assignedRoomName?.let { put("assignedRoomName", it) }
            put("batteryPercent", batteryPercent)
            put("rssi", rssi)
            put("macAddress", macAddress)
            put("nfcTagUid", nfcTagUid)
            put("qrPayload", qrPayload)
            put("isOffline", isOffline)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): TriggerItem {
            val typeStr = obj.optString("type", "")
            val resolvedType = when {
                typeStr.equals("BLE_BEACON", ignoreCase = true) -> TriggerType.BLE_BEACON
                typeStr.equals("NFC_TAG", ignoreCase = true) -> TriggerType.NFC_TAG
                typeStr.equals("QR_CODE", ignoreCase = true) -> TriggerType.QR_CODE
                typeStr.equals("UNCONFIGURED", ignoreCase = true) -> TriggerType.UNCONFIGURED
                obj.has("macAddress") || obj.has("code") -> TriggerType.BLE_BEACON
                else -> TriggerType.UNCONFIGURED
            }

            val isConfigured = if (obj.has("isConfigured")) {
                obj.optBoolean("isConfigured", true)
            } else {
                resolvedType != TriggerType.UNCONFIGURED
            }

            return TriggerItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Trigger"),
                type = resolvedType,
                isConfigured = isConfigured,
                code = obj.optString("code", "CP-${UUID.randomUUID().toString().take(4).uppercase()}"),
                assignedRoomName = if (obj.has("assignedRoomName") && !obj.isNull("assignedRoomName")) obj.getString("assignedRoomName") else null,
                batteryPercent = obj.optInt("batteryPercent", 100),
                rssi = obj.optInt("rssi", -50),
                macAddress = obj.optString("macAddress", ""),
                nfcTagUid = obj.optString("nfcTagUid", ""),
                qrPayload = obj.optString("qrPayload", ""),
                isOffline = obj.optBoolean("isOffline", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Typealiases preserving full backward compatibility.
 */
typealias SensorItem = TriggerItem
typealias BeaconItem = TriggerItem

/**
 * Proximity / Context activity event for the Home screen feed.
 */
data class RecentActivityItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val iconType: String = "beacon",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("subtitle", subtitle)
            put("iconType", iconType)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): RecentActivityItem {
            return RecentActivityItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", ""),
                subtitle = obj.optString("subtitle", ""),
                iconType = obj.optString("iconType", "beacon"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}
