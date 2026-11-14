package com.contextpulse.phase0.repository

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.ble.BeaconRegistry
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
class BeaconRepository(
    context: Context,
    private val customPrefsName: String = PREFS_NAME,
    private val syncToLiveRegistry: Boolean = true
) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(customPrefsName, Context.MODE_PRIVATE)

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
        val clean = tagId.trim()
        if (clean.isBlank()) return null
        return _beacons.value.find {
            it.type == TriggerType.NFC_TAG &&
                (it.nfcTagId.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true))
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

    /**
     * Returns the locked TriggerType already attached to a Context, or null if no triggers are attached yet.
     * Contexts may only have triggers of a single homogeneous TriggerType (BLE Beacon, QR Code, or NFC Tag).
     */
    fun getLockedTriggerTypeForContext(
        contextId: String,
        contextName: String = "",
        excludeTriggerId: String? = null
    ): TriggerType? {
        if (contextId.isBlank() && contextName.isBlank()) return null
        return _beacons.value.firstOrNull { b ->
            b.id != excludeTriggerId &&
                b.type != TriggerType.UNCONFIGURED &&
                ((contextId.isNotBlank() && b.assignedRoomId == contextId) ||
                    (contextName.isNotBlank() && b.assignedRoomName.equals(contextName, ignoreCase = true)))
        }?.type
    }

    fun canAttachTriggerToContext(
        triggerId: String,
        contextId: String,
        contextName: String = ""
    ): Boolean {
        val trigger = _beacons.value.find { it.id == triggerId } ?: return false
        if (trigger.type == TriggerType.UNCONFIGURED) return true
        val lockedType = getLockedTriggerTypeForContext(
            contextId = contextId,
            contextName = contextName,
            excludeTriggerId = triggerId
        )
        return lockedType == null || lockedType == trigger.type
    }

    fun saveOrUpdateBeacon(beacon: RegisteredBeacon) {
        val sanitizedBeacon = if (
            beacon.type != TriggerType.UNCONFIGURED &&
            (beacon.assignedRoomId.isNotBlank() || beacon.assignedRoomName.isNotBlank())
        ) {
            val lockedType = getLockedTriggerTypeForContext(
                contextId = beacon.assignedRoomId,
                contextName = beacon.assignedRoomName,
                excludeTriggerId = beacon.id
            )
            if (lockedType != null && lockedType != beacon.type) {
                beacon.copy(assignedRoomId = "", assignedRoomName = "")
            } else {
                beacon
            }
        } else {
            beacon
        }

        val current = _beacons.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == sanitizedBeacon.id }
        if (existingIdx >= 0) {
            current[existingIdx] = sanitizedBeacon
        } else {
            current.add(sanitizedBeacon)
        }
        saveBeaconsInternal(current)
        if (syncToLiveRegistry) {
            syncBeaconRegistryForBeacon(sanitizedBeacon)
            checkAndNotifyTriggerStatus()
        }
    }

    fun deleteBeacon(beaconId: String) {
        val target = _beacons.value.find { it.id == beaconId }
        val updated = _beacons.value.filterNot { it.id == beaconId }
        saveBeaconsInternal(updated)
        if (syncToLiveRegistry && target != null && target.type == TriggerType.BLE_BEACON && target.macAddress.isNotBlank()) {
            BeaconRegistry.unregisterBeacon(appContext, target.macAddress)
        }
    }

    fun deleteSensor(sensorId: String) = deleteBeacon(sensorId)

    fun saveTrigger(trigger: RegisteredBeacon) = saveOrUpdateBeacon(trigger)

    fun reconcileWithRooms(validRooms: List<com.contextpulse.phase0.model.RoomItem>) {
        val roomMap = validRooms.associateBy { it.id }
        val updated = _beacons.value.map { b ->
            if (b.assignedRoomId.isNotBlank()) {
                val matchingRoom = roomMap[b.assignedRoomId]
                if (matchingRoom != null) {
                    // Context renamed: update display label cache while preserving stable ID
                    b.copy(assignedRoomName = matchingRoom.name)
                } else {
                    // Context was deleted: unassign beacon
                    b.copy(assignedRoomId = "", assignedRoomName = "")
                }
            } else if (b.assignedRoomName.isNotBlank()) {
                // Legacy data: resolve stable roomId from name if possible
                val matchingRoom = validRooms.find { it.name.equals(b.assignedRoomName, ignoreCase = true) }
                if (matchingRoom != null) {
                    b.copy(assignedRoomId = matchingRoom.id, assignedRoomName = matchingRoom.name)
                } else {
                    b.copy(assignedRoomId = "", assignedRoomName = "")
                }
            } else {
                b
            }
        }
        if (updated != _beacons.value) {
            saveBeaconsInternal(updated)
            syncAllToBeaconRegistry()
        }
    }

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
            syncAllToBeaconRegistry()
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
        syncAllToBeaconRegistry()
    }

    fun attachBeaconToContext(triggerId: String, contextId: String, contextName: String): Boolean {
        if (!canAttachTriggerToContext(triggerId, contextId, contextName)) {
            return false
        }
        val updated = _beacons.value.map { b ->
            if (b.id == triggerId) {
                b.copy(assignedRoomId = contextId, assignedRoomName = contextName)
            } else {
                b
            }
        }
        saveBeaconsInternal(updated)
        syncBeaconRegistryForTriggerId(triggerId, contextId, contextName)
        return true
    }

    fun attachBeaconToRoom(triggerId: String, roomName: String): Boolean {
        if (!canAttachTriggerToContext(triggerId, contextId = "", contextName = roomName)) {
            return false
        }
        val updated = _beacons.value.map { b ->
            if (b.id == triggerId) {
                b.copy(assignedRoomName = roomName)
            } else {
                b
            }
        }
        saveBeaconsInternal(updated)
        syncAllToBeaconRegistry()
        return true
    }

    fun detachBeaconFromContext(triggerId: String) {
        detachBeaconFromRoom(triggerId)
    }

    fun detachBeaconFromRoom(triggerId: String) {
        val target = _beacons.value.find { it.id == triggerId }
        val updated = _beacons.value.map { b ->
            if (b.id == triggerId) {
                b.copy(assignedRoomId = "", assignedRoomName = "")
            } else {
                b
            }
        }
        saveBeaconsInternal(updated)
        if (syncToLiveRegistry) {
            if (target != null && target.type == TriggerType.BLE_BEACON && target.macAddress.isNotBlank()) {
                BeaconRegistry.unregisterBeacon(appContext, target.macAddress)
            }
        }
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
        syncAllToBeaconRegistry()
    }

    fun findDuplicateIBeacon(uuid: String, major: Int, minor: Int, excludeId: String? = null): RegisteredBeacon? {
        return _beacons.value.find { it.id != excludeId && it.matchesIBeaconIdentity(uuid, major, minor) }
    }

    /**
     * Continuously updates live RSSI, passive Eddystone-TLM battery percentage, and reachability.
     * Updates in-memory StateFlow immediately for smooth visual telemetry in the UI.
     */
    fun updateBeaconTelemetry(
        macAddress: String,
        rssi: Int,
        batteryPercent: Int? = null,
        isReachable: Boolean = true
    ) {
        if (macAddress.isBlank()) return
        val current = _beacons.value
        var updatedAny = false
        var batteryChanged = false
        val updated = current.map { b ->
            if (b.type == TriggerType.BLE_BEACON && b.macAddress.equals(macAddress, ignoreCase = true)) {
                updatedAny = true
                val nextBattery = batteryPercent ?: b.batteryPercent
                if (batteryPercent != null && batteryPercent != b.batteryPercent) {
                    batteryChanged = true
                }
                b.copy(
                    rssi = rssi,
                    batteryPercent = nextBattery,
                    lastSeenMillis = System.currentTimeMillis(),
                    isReachable = isReachable,
                    isOffline = false
                )
            } else {
                b
            }
        }
        if (updatedAny) {
            if (batteryChanged) {
                saveBeaconsInternal(updated)
            } else {
                _beacons.value = updated
            }
        }
    }

    private fun syncBeaconRegistryForTriggerId(triggerId: String, contextId: String, contextName: String) {
        val beacon = _beacons.value.find { it.id == triggerId } ?: return
        syncBeaconRegistryForBeacon(beacon.copy(assignedRoomId = contextId, assignedRoomName = contextName))
    }

    private fun syncBeaconRegistryForBeacon(beacon: RegisteredBeacon) {
        if (!syncToLiveRegistry) return
        if (beacon.type != TriggerType.BLE_BEACON) return
        if (beacon.assignedRoomId.isNotBlank()) {
            val (major, minor) = beacon.parseMajorMinor()
            BeaconRegistry.registerBeacon(
                context = appContext,
                roomId = beacon.assignedRoomId,
                roomName = beacon.assignedRoomName.ifBlank { "Room" },
                macAddress = beacon.macAddress,
                uuid = beacon.namespaceOrUuid.ifBlank { null },
                major = major,
                minor = minor,
                beaconType = com.contextpulse.phase0.model.BeaconType.IBEACON
            )
        } else {
            // Unassigned: guarantee it is NOT registered as an active room beacon in BeaconRegistry
            if (beacon.macAddress.isNotBlank()) {
                BeaconRegistry.unregisterBeacon(appContext, beacon.macAddress)
            }
        }
    }

    private fun syncAllToBeaconRegistry() {
        for (beacon in _beacons.value) {
            if (beacon.type == TriggerType.BLE_BEACON) {
                syncBeaconRegistryForBeacon(beacon)
            }
        }
    }

    fun addUnconfiguredTrigger(name: String, type: TriggerType = TriggerType.BLE_BEACON): RegisteredBeacon {
        val cleanName = name.trim()
        val generatedId = "trigger_${System.currentTimeMillis() % 100000}"
        val appLinkUrl = com.contextpulse.phase0.util.NfcTagHelper.buildAppLinkUri(generatedId)
        val newTrigger = RegisteredBeacon(
            id = generatedId,
            name = cleanName,
            type = type,
            macAddress = if (type == TriggerType.BLE_BEACON) "00:11:22:33:44:55" else "",
            qrPayload = if (type == TriggerType.QR_CODE || type == TriggerType.NFC_TAG) appLinkUrl else "",
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
            if (!notifManager.isNotificationPermissionGranted()) return

            val lowBatteryTriggers = _beacons.value.filter { it.isLowBattery() }
            lowBatteryTriggers.forEach { trigger ->
                val pct = trigger.batteryPercent ?: return@forEach
                notifManager.notifyLowBeaconBattery(trigger.name, pct)
            }
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
