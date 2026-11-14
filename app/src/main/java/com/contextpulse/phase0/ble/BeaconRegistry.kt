package com.contextpulse.phase0.ble

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.BeaconType
import com.contextpulse.phase0.model.RoomBeaconDefinition
import org.json.JSONArray
import org.json.JSONObject

object BeaconRegistry {

    private const val PREFS_NAME = "contextpulse_beacon_registry"
    private const val KEY_REGISTERED_BEACONS = "key_registered_beacons"

    private fun getPrefs(context: Context, customPrefsName: String? = null): SharedPreferences {
        return context.getSharedPreferences(customPrefsName ?: PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Gets all currently registered room beacons.
     * If empty on first launch, seeds with test room beacon definitions (only for production storage).
     */
    fun getRegisteredBeacons(context: Context, customPrefsName: String? = null): List<RoomBeaconDefinition> {
        val prefs = getPrefs(context, customPrefsName)
        val jsonString = prefs.getString(KEY_REGISTERED_BEACONS, null)
        if (jsonString.isNullOrEmpty()) {
            if (customPrefsName == null) {
                val defaults = getDefaultRooms()
                saveRegisteredBeacons(context, defaults, null)
                return defaults
            } else {
                return emptyList()
            }
        }

        return try {
            val list = mutableListOf<RoomBeaconDefinition>()
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val beaconTypeStr = obj.optString("beaconType", BeaconType.IBEACON.name)
                val beaconType = try {
                    BeaconType.valueOf(beaconTypeStr)
                } catch (_: Exception) {
                    BeaconType.IBEACON
                }

                list.add(
                    RoomBeaconDefinition(
                        roomId = obj.getString("roomId"),
                        roomName = obj.getString("roomName"),
                        macAddress = obj.getString("macAddress"),
                        uuid = if (obj.has("uuid") && !obj.isNull("uuid")) obj.getString("uuid") else null,
                        major = if (obj.has("major") && !obj.isNull("major")) obj.getInt("major") else null,
                        minor = if (obj.has("minor") && !obj.isNull("minor")) obj.getInt("minor") else null,
                        namespace = if (obj.has("namespace") && !obj.isNull("namespace")) obj.getString("namespace") else null,
                        instance = if (obj.has("instance") && !obj.isNull("instance")) obj.getString("instance") else null,
                        beaconType = beaconType
                    )
                )
            }
            list
        } catch (e: Exception) {
            if (customPrefsName == null) getDefaultRooms() else emptyList()
        }
    }

    fun saveRegisteredBeacons(context: Context, beacons: List<RoomBeaconDefinition>, customPrefsName: String? = null) {
        val jsonArray = JSONArray()
        for (b in beacons) {
            val obj = JSONObject().apply {
                put("roomId", b.roomId)
                put("roomName", b.roomName)
                put("macAddress", b.macAddress)
                b.uuid?.let { put("uuid", it) }
                b.major?.let { put("major", it) }
                b.minor?.let { put("minor", it) }
                b.namespace?.let { put("namespace", it) }
                b.instance?.let { put("instance", it) }
                put("beaconType", b.beaconType.name)
            }
            jsonArray.put(obj)
        }
        getPrefs(context, customPrefsName).edit().putString(KEY_REGISTERED_BEACONS, jsonArray.toString()).apply()
    }

    /**
     * Registers a beacon for a room context.
     * Supports MULTIPLE beacons per room.
     * When customPrefsName is provided, operates strictly in isolated test storage.
     */
    fun registerBeacon(
        context: Context,
        roomId: String? = null,
        roomName: String,
        macAddress: String,
        uuid: String? = null,
        major: Int? = null,
        minor: Int? = null,
        namespace: String? = null,
        instance: String? = null,
        beaconType: BeaconType = BeaconType.IBEACON,
        customPrefsName: String? = null
    ) {
        val current = getRegisteredBeacons(context, customPrefsName).toMutableList()

        // Only remove an existing beacon if it represents the identical physical beacon:
        // matching MAC address or matching full beacon identifier
        current.removeAll { existing ->
            when {
                macAddress.isNotBlank() && existing.macAddress.equals(macAddress, ignoreCase = true) -> true
                uuid != null && existing.uuid != null && existing.uuid.equals(uuid, ignoreCase = true) &&
                        existing.major == major && existing.minor == minor -> true
                namespace != null && existing.namespace != null && existing.namespace.equals(namespace, ignoreCase = true) &&
                        existing.instance != null && existing.instance.equals(instance, ignoreCase = true) -> true
                else -> false
            }
        }

        val targetRoomId = roomId ?: "room_${roomName.lowercase().replace(" ", "_")}"
        current.add(
            RoomBeaconDefinition(
                roomId = targetRoomId,
                roomName = roomName,
                macAddress = macAddress,
                uuid = uuid,
                major = major,
                minor = minor,
                namespace = namespace,
                instance = instance,
                beaconType = beaconType
            )
        )
        saveRegisteredBeacons(context, current, customPrefsName)
    }

    fun unregisterBeacon(context: Context, macAddress: String, customPrefsName: String? = null) {
        val current = getRegisteredBeacons(context, customPrefsName).toMutableList()
        current.removeAll { it.macAddress.equals(macAddress, ignoreCase = true) }
        saveRegisteredBeacons(context, current, customPrefsName)
    }

    /**
     * Checks if a discovered device matches any registered room beacon.
     */
    fun findMatchingRoom(
        context: Context,
        macAddress: String,
        uuid: String? = null,
        major: Int? = null,
        minor: Int? = null,
        namespace: String? = null,
        instance: String? = null,
        customPrefsName: String? = null
    ): RoomBeaconDefinition? {
        val registered = getRegisteredBeacons(context, customPrefsName)
        return registered.find { room ->
            // 1. Match MAC address (case-insensitive)
            if (room.macAddress.isNotEmpty() && room.macAddress.equals(macAddress, ignoreCase = true)) {
                return@find true
            }

            // 2. Match iBeacon / AltBeacon UUID + Major + Minor
            if (uuid != null && room.uuid != null) {
                val cleanRoomUuid = room.uuid.replace("-", "").trim()
                val cleanIncomingUuid = uuid.replace("-", "").trim()
                if (cleanRoomUuid.equals(cleanIncomingUuid, ignoreCase = true)) {
                    if (room.major == null || room.major == major) {
                        if (room.minor == null || room.minor == minor) {
                            return@find true
                        }
                    }
                }
            }

            // 3. Match Eddystone-UID
            // Check direct namespace and instance if available
            if (namespace != null && room.namespace != null && room.namespace.equals(namespace, ignoreCase = true)) {
                if (room.instance == null || room.instance.equals(instance, ignoreCase = true)) {
                    return@find true
                }
            }

            // Check if uuid was parsed as "namespace-instance" from BeaconParser
            if (uuid != null && room.namespace != null) {
                val expectedCombined = "${room.namespace}-${room.instance ?: ""}"
                if (uuid.startsWith(room.namespace, ignoreCase = true) || uuid.equals(expectedCombined, ignoreCase = true)) {
                    return@find true
                }
            }

            false
        }
    }

    private fun getDefaultRooms(): List<RoomBeaconDefinition> {
        return listOf(
            RoomBeaconDefinition(
                roomId = "room_bedroom",
                roomName = "Bedroom",
                macAddress = "DD:34:02:88:1A:E1",
                uuid = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
                major = 101,
                minor = 1,
                beaconType = BeaconType.IBEACON
            ),
            RoomBeaconDefinition(
                roomId = "room_office",
                roomName = "Study / Desk",
                macAddress = "E1:89:12:33:F2:B4",
                uuid = "7B94B88F-D2A4-4822-927A-1910BA8021B1",
                major = 102,
                minor = 2,
                beaconType = BeaconType.IBEACON
            ),
            RoomBeaconDefinition(
                roomId = "room_living_room",
                roomName = "Living Room",
                macAddress = "C4:55:78:90:11:02",
                uuid = "5A243454-CF6D-4A0F-ADF2-F4911BA9FFA6",
                major = 103,
                minor = 3,
                beaconType = BeaconType.IBEACON
            )
        )
    }
}
