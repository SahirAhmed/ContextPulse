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

    val label: String
        get() = getDisplayName()

    fun getDisplayName(): String = when (this) {
        UNCONFIGURED -> "Needs Setup"
        BLE_BEACON -> "BLE Beacon"
        NFC_TAG -> "NFC Tag"
        QR_CODE -> "QR Code"
    }
}

typealias SensorType = TriggerType

/**
 * Live broadcast health status for triggers.
 */
enum class BroadcastStatus {
    BROADCASTING,
    LOW_BATTERY,
    OFFLINE;

    fun getDisplayName(): String = when (this) {
        BROADCASTING -> "Broadcasting"
        LOW_BATTERY -> "Low Battery (<15%)"
        OFFLINE -> "Broadcast Stopped"
    }
}

/**
 * Unified model for a registered Trigger (BLE Beacon, NFC Tag, or QR Code).
 */
data class RegisteredBeacon(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TriggerType = TriggerType.BLE_BEACON,
    val isConfigured: Boolean = true,
    val code: String = "",
    val macAddress: String = "",
    val namespaceOrUuid: String = "",
    val instanceOrMajorMinor: String = "",
    val nfcTagId: String = "",
    val qrPayload: String = "",
    val assignedRoomId: String = "",
    val assignedRoomName: String = "",
    val enterRssiThreshold: Int = -70,
    val exitRssiThreshold: Int = -85,
    val batteryPercent: Int? = null,
    val rssi: Int = -50,
    val lastSeenMillis: Long? = System.currentTimeMillis(),
    val isReachable: Boolean = true,
    val isOffline: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val nfcTagUid: String get() = nfcTagId

    fun parseMajorMinor(): Pair<Int?, Int?> {
        if (instanceOrMajorMinor.isBlank()) return Pair(null, null)
        val parts = instanceOrMajorMinor.split('/', ':', ',').map { it.trim() }.filter { it.isNotEmpty() }
        val major = parts.getOrNull(0)?.toIntOrNull()
        val minor = parts.getOrNull(1)?.toIntOrNull()
        return Pair(major, minor)
    }

    fun matchesIBeaconIdentity(uuid: String, major: Int, minor: Int): Boolean {
        if (type != TriggerType.BLE_BEACON) return false
        val cleanThisUuid = namespaceOrUuid.replace("-", "").trim()
        val cleanTargetUuid = uuid.replace("-", "").trim()
        if (!cleanThisUuid.equals(cleanTargetUuid, ignoreCase = true)) return false
        val (thisMajor, thisMinor) = parseMajorMinor()
        return thisMajor == major && thisMinor == minor
    }

    fun isLowBattery(): Boolean {
        return type == TriggerType.BLE_BEACON && batteryPercent != null && batteryPercent in 0 until 15
    }

    fun hasFaultAlert(): Boolean {
        return isLowBattery() || !isReachable || isOffline
    }

    fun getBroadcastStatus(): BroadcastStatus {
        if (!isConfigured || isOffline || !isReachable) return BroadcastStatus.OFFLINE
        if (isLowBattery()) return BroadcastStatus.LOW_BATTERY
        return BroadcastStatus.BROADCASTING
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type.name)
            put("isConfigured", isConfigured)
            put("code", code)
            put("macAddress", macAddress)
            put("namespaceOrUuid", namespaceOrUuid)
            put("instanceOrMajorMinor", instanceOrMajorMinor)
            put("nfcTagId", nfcTagId)
            put("qrPayload", qrPayload)
            put("assignedRoomId", assignedRoomId)
            put("assignedRoomName", assignedRoomName)
            put("enterRssiThreshold", enterRssiThreshold)
            put("exitRssiThreshold", exitRssiThreshold)
            if (batteryPercent != null) put("batteryPercent", batteryPercent)
            put("rssi", rssi)
            if (lastSeenMillis != null) put("lastSeenMillis", lastSeenMillis)
            put("isReachable", isReachable)
            put("isOffline", isOffline)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): RegisteredBeacon {
            val typeStr = obj.optString("type", "BLE_BEACON")
            val resolvedType = try {
                TriggerType.valueOf(typeStr)
            } catch (_: Exception) {
                TriggerType.BLE_BEACON
            }
            return RegisteredBeacon(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Trigger"),
                type = resolvedType,
                isConfigured = obj.optBoolean("isConfigured", true),
                code = obj.optString("code", ""),
                macAddress = obj.optString("macAddress", ""),
                namespaceOrUuid = obj.optString("namespaceOrUuid", ""),
                instanceOrMajorMinor = obj.optString("instanceOrMajorMinor", ""),
                nfcTagId = obj.optString("nfcTagId", obj.optString("nfcTagUid", "")),
                qrPayload = obj.optString("qrPayload", ""),
                assignedRoomId = obj.optString("assignedRoomId", ""),
                assignedRoomName = obj.optString("assignedRoomName", ""),
                enterRssiThreshold = obj.optInt("enterRssiThreshold", -70),
                exitRssiThreshold = obj.optInt("exitRssiThreshold", -85),
                batteryPercent = if (obj.has("batteryPercent") && !obj.isNull("batteryPercent")) obj.getInt("batteryPercent") else null,
                rssi = obj.optInt("rssi", -50),
                lastSeenMillis = if (obj.has("lastSeenMillis") && !obj.isNull("lastSeenMillis")) obj.getLong("lastSeenMillis") else null,
                isReachable = obj.optBoolean("isReachable", true),
                isOffline = obj.optBoolean("isOffline", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

typealias TriggerItem = RegisteredBeacon
typealias SensorItem = RegisteredBeacon
typealias BeaconItem = RegisteredBeacon

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
