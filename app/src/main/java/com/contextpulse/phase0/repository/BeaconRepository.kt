package com.contextpulse.phase0.repository

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.RegisteredBeacon
import com.contextpulse.phase0.model.TriggerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists registered Triggers (BLE Beacons, NFC Tags, QR Codes) in SharedPreferences.
 */
class BeaconRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _beacons = MutableStateFlow<List<RegisteredBeacon>>(emptyList())
    val beacons: StateFlow<List<RegisteredBeacon>> = _beacons.asStateFlow()

    init {
        loadBeacons()
    }

    private fun loadBeacons() {
        val rawJson = prefs.getString(KEY_BEACONS_JSON, null)
        if (rawJson == null) {
            // Clean slate on fresh install
            saveBeaconsInternal(emptyList())
            return
        }
        if (rawJson.isBlank()) {
            _beacons.value = emptyList()
            return
        }

        try {
            val jsonArray = JSONArray(rawJson)
            val parsed = mutableListOf<RegisteredBeacon>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val typeStr = obj.optString("type", TriggerType.BLE_BEACON.name)
                val type = try {
                    TriggerType.valueOf(typeStr)
                } catch (_: Exception) {
                    TriggerType.BLE_BEACON
                }

                parsed.add(
                    RegisteredBeacon(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = type,
                        macAddress = obj.optString("macAddress", ""),
                        namespaceOrUuid = obj.optString("namespaceOrUuid", ""),
                        instanceOrMajorMinor = obj.optString("instanceOrMajorMinor", ""),
                        nfcTagId = obj.optString("nfcTagId", ""),
                        qrPayload = obj.optString("qrPayload", ""),
                        assignedRoomId = obj.optString("assignedRoomId", ""),
                        assignedRoomName = obj.optString("assignedRoomName", ""),
                        enterRssiThreshold = obj.optInt("enterRssiThreshold", -70),
                        exitRssiThreshold = obj.optInt("exitRssiThreshold", -85),
                        batteryPercent = if (obj.has("batteryPercent") && !obj.isNull("batteryPercent")) obj.getInt("batteryPercent") else null,
                        lastSeenMillis = if (obj.has("lastSeenMillis") && !obj.isNull("lastSeenMillis")) obj.getLong("lastSeenMillis") else null,
                        isReachable = obj.optBoolean("isReachable", true)
                    )
                )
            }
            _beacons.value = parsed
        } catch (e: Exception) {
            seedDefaultBeacons()
        }
    }

    private fun seedDefaultBeacons() {
        val defaults = listOf(
            RegisteredBeacon(
                id = "beacon_bedroom_01",
                name = "Nightstand Beacon",
                type = TriggerType.BLE_BEACON,
                macAddress = "AA:BB:CC:11:22:33",
                namespaceOrUuid = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
                instanceOrMajorMinor = "100 / 1",
                assignedRoomId = "room_bedroom",
                assignedRoomName = "Bedroom",
                enterRssiThreshold = -68,
                exitRssiThreshold = -82,
                batteryPercent = 94,
                lastSeenMillis = System.currentTimeMillis() - 120_000L,
                isReachable = true
            ),
            RegisteredBeacon(
                id = "nfc_office_desk_01",
                name = "Office Desk NFC Tag",
                type = TriggerType.NFC_TAG,
                nfcTagId = "04:A1:2B:8C:99:1E:80",
                assignedRoomId = "room_office",
                assignedRoomName = "Office",
                isReachable = true
            ),
            RegisteredBeacon(
                id = "qr_office_door_01",
                name = "Office Door QR Code",
                type = TriggerType.QR_CODE,
                qrPayload = "contextpulse://trigger/qr_office_door_01",
                assignedRoomId = "room_office",
                assignedRoomName = "Office",
                isReachable = true
            )
        )
        saveBeaconsInternal(defaults)
    }

    private fun saveBeaconsInternal(list: List<RegisteredBeacon>) {
        val jsonArray = JSONArray()
        list.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("name", b.name)
            obj.put("type", b.type.name)
            obj.put("macAddress", b.macAddress)
            obj.put("namespaceOrUuid", b.namespaceOrUuid)
            obj.put("instanceOrMajorMinor", b.instanceOrMajorMinor)
            obj.put("nfcTagId", b.nfcTagId)
            obj.put("qrPayload", b.qrPayload)
            obj.put("assignedRoomId", b.assignedRoomId)
            obj.put("assignedRoomName", b.assignedRoomName)
            obj.put("enterRssiThreshold", b.enterRssiThreshold)
            obj.put("exitRssiThreshold", b.exitRssiThreshold)
            if (b.batteryPercent != null) obj.put("batteryPercent", b.batteryPercent)
            if (b.lastSeenMillis != null) obj.put("lastSeenMillis", b.lastSeenMillis)
            obj.put("isReachable", b.isReachable)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_BEACONS_JSON, jsonArray.toString()).apply()
        _beacons.value = list
    }

    fun getBeaconById(id: String): RegisteredBeacon? {
        return _beacons.value.find { it.id == id }
    }

    fun findByNfcTagId(tagId: String): RegisteredBeacon? {
        return _beacons.value.find {
            it.type == TriggerType.NFC_TAG &&
                (it.nfcTagId.equals(tagId, ignoreCase = true) || it.id.equals(tagId, ignoreCase = true))
        }
    }

    fun findByQrPayload(payload: String): RegisteredBeacon? {
        val cleanPayload = payload.trim()
        return _beacons.value.find {
            it.type == TriggerType.QR_CODE &&
                (it.qrPayload.equals(cleanPayload, ignoreCase = true) ||
                    it.id.equals(cleanPayload, ignoreCase = true) ||
                    cleanPayload.endsWith("/${it.id}", ignoreCase = true) ||
                    cleanPayload.contains("trigger=${it.id}", ignoreCase = true) ||
                    cleanPayload.contains("id=${it.id}", ignoreCase = true))
        }
    }

    fun saveOrUpdateBeacon(beacon: RegisteredBeacon) {
        val current = _beacons.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == beacon.id }
        if (existingIdx >= 0) {
            current[existingIdx] = beacon
        } else {
            current.add(beacon)
        }
        saveBeaconsInternal(current)
        checkAndNotifyTriggerStatus()
    }

    fun deleteBeacon(beaconId: String) {
        val updated = _beacons.value.filterNot { it.id == beaconId }
        saveBeaconsInternal(updated)
    }

    fun deleteSensor(sensorId: String) = deleteBeacon(sensorId)

    fun saveTrigger(trigger: RegisteredBeacon) = saveOrUpdateBeacon(trigger)

    fun reconcileWithRoomNames(validRoomNames: Set<String>) {
        val updated = _beacons.value.map { b ->
            if (b.assignedRoomName.isNotBlank() && !validRoomNames.contains(b.assignedRoomName)) {
                b.copy(assignedRoomId = "", assignedRoomName = "")
            } else {
                b
            }
        }
        if (updated != _beacons.value) {
            saveBeaconsInternal(updated)
        }
    }

    fun detachBeaconByRoomName(roomName: String) {
        val updated = _beacons.value.map { b ->
            if (b.assignedRoomName.equals(roomName, ignoreCase = true)) {
                b.copy(assignedRoomId = "", assignedRoomName = "")
            } else {
                b
            }
        }
        saveBeaconsInternal(updated)
    }

    fun attachBeaconToRoom(triggerId: String, roomName: String) {
        val updated = _beacons.value.map { b ->
            if (b.id == triggerId) {
                b.copy(assignedRoomName = roomName)
            } else {
                b
            }
        }
        saveBeaconsInternal(updated)
    }

    fun detachBeaconFromRoom(triggerId: String) {
        val updated = _beacons.value.map { b ->
            if (b.id == triggerId) {
                b.copy(assignedRoomId = "", assignedRoomName = "")
            } else {
                b
            }
        }
        saveBeaconsInternal(updated)
    }

    fun updateAssignedRoomName(oldName: String, newName: String) {
        val updated = _beacons.value.map { b ->
            if (b.assignedRoomName.equals(oldName, ignoreCase = true)) {
                b.copy(assignedRoomName = newName)
            } else {
                b
            }
        }
        saveBeaconsInternal(updated)
    }

    fun addUnconfiguredTrigger(name: String, type: TriggerType = TriggerType.BLE_BEACON): RegisteredBeacon {
        val cleanName = name.trim()
        val newTrigger = RegisteredBeacon(
            id = "trigger_${System.currentTimeMillis() % 100000}",
            name = cleanName,
            type = type,
            macAddress = if (type == TriggerType.BLE_BEACON) "00:11:22:33:44:55" else "",
            qrPayload = if (type == TriggerType.QR_CODE) "https://sahirahmed.github.io/ContextPulse/?trigger=${cleanName.lowercase().replace(" ", "_")}" else "",
            isReachable = true
        )
        saveOrUpdateBeacon(newTrigger)
        return newTrigger
    }

    fun isDuplicateName(name: String, excludeId: String? = null): Boolean {
        val clean = name.trim()
        return _beacons.value.any { it.name.equals(clean, ignoreCase = true) && it.id != excludeId }
    }

    fun moveTrigger(fromIndex: Int, toIndex: Int) {
        val current = _beacons.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            saveBeaconsInternal(current)
        }
    }

    fun wipeAllBeacons() {
        saveBeaconsInternal(emptyList())
    }

    fun checkAndNotifyTriggerStatus() {
        try {
            val notifPrefs = NotificationPreferencesRepository.getInstance(appContext)
            val notifManager = com.contextpulse.phase0.system.ContextPulseNotificationManager.getInstance(appContext)
            if (!notifPrefs.lowBatteryNotificationsEnabled.value || !notifManager.isBeaconBatteryEnabled()) return
            if (!notifPrefs.hasNotificationPermission()) return

            val lowBatteryTriggers = _beacons.value.filter { it.isLowBattery() }
            if (lowBatteryTriggers.isEmpty()) return

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "contextpulse_trigger_alerts"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Trigger & Beacon Battery Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a BLE Beacon is low on battery or unreachable"
                }
                nm.createNotificationChannel(channel)
            }

            val names = lowBatteryTriggers.joinToString(", ") { "${it.name} (${it.batteryPercent}%)" }
            val body = "Low battery detected on beacon(s): $names. Please replace or recharge soon."

            val notif = androidx.core.app.NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("ContextPulse: Beacon Low Battery")
                .setContentText(body)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            nm.notify(8801, notif)
        } catch (_: Exception) {
            // Ignore in test environments
        }
    }

    companion object {
        private const val PREFS_NAME = "contextpulse_beacons_prefs"
        private const val KEY_BEACONS_JSON = "beacons_json_v2"

        @Volatile
        private var INSTANCE: BeaconRepository? = null

        fun getInstance(context: Context): BeaconRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BeaconRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
