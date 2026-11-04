package com.contextpulse.phase0.ble

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.RoomBeaconDefinition
import org.json.JSONArray
import org.json.JSONObject

object BeaconRegistry {

    private const val PREFS_NAME = "contextpulse_beacon_registry"
    private const val KEY_REGISTERED_BEACONS = "key_registered_beacons"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Gets all currently registered room beacons.
     * If empty on first launch, seeds with test room beacon definitions.
     */
    fun getRegisteredBeacons(context: Context): List<RoomBeaconDefinition> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_REGISTERED_BEACONS, null)
        if (jsonString.isNullOrEmpty()) {
            val defaults = getDefaultRooms()
            saveRegisteredBeacons(context, defaults)
            return defaults
        }

        return try {
            val list = mutableListOf<RoomBeaconDefinition>()
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RoomBeaconDefinition(
                        roomId = obj.getString("roomId"),
                        roomName = obj.getString("roomName"),
                        macAddress = obj.getString("macAddress"),
                        uuid = if (obj.has("uuid")) obj.getString("uuid") else null,
                        major = if (obj.has("major") && !obj.isNull("major")) obj.getInt("major") else null,
                        minor = if (obj.has("minor") && !obj.isNull("minor")) obj.getInt("minor") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            getDefaultRooms()
        }
    }

    fun saveRegisteredBeacons(context: Context, beacons: List<RoomBeaconDefinition>) {
        val jsonArray = JSONArray()
        for (b in beacons) {
            val obj = JSONObject().apply {
                put("roomId", b.roomId)
                put("roomName", b.roomName)
                put("macAddress", b.macAddress)
                b.uuid?.let { put("uuid", it) }
                b.major?.let { put("major", it) }
                b.minor?.let { put("minor", it) }
            }
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_REGISTERED_BEACONS, jsonArray.toString()).apply()
    }

    fun registerBeacon(
        context: Context,
        roomName: String,
        macAddress: String,
        uuid: String? = null,
        major: Int? = null,
        minor: Int? = null
    ) {
        val current = getRegisteredBeacons(context).toMutableList()
        // Remove existing definition with same MAC or roomName if any
        current.removeAll { it.macAddress.equals(macAddress, ignoreCase = true) || it.roomName.equals(roomName, ignoreCase = true) }
        current.add(
            RoomBeaconDefinition(
                roomId = "room_${System.currentTimeMillis() % 10000}",
                roomName = roomName,
                macAddress = macAddress,
                uuid = uuid,
                major = major,
                minor = minor
            )
        )
        saveRegisteredBeacons(context, current)
    }

    fun unregisterBeacon(context: Context, macAddress: String) {
        val current = getRegisteredBeacons(context).toMutableList()
        current.removeAll { it.macAddress.equals(macAddress, ignoreCase = true) }
        saveRegisteredBeacons(context, current)
    }

    /**
     * Checks if a discovered device matches any registered room beacon.
     * Matches primarily on MAC address, or secondary on UUID + Major + Minor if MAC randomized.
     */
    fun findMatchingRoom(
        context: Context,
        macAddress: String,
        uuid: String? = null,
        major: Int? = null,
        minor: Int? = null
    ): RoomBeaconDefinition? {
        val registered = getRegisteredBeacons(context)
        return registered.find { room ->
            // Match MAC address (case-insensitive)
            if (room.macAddress.isNotEmpty() && room.macAddress.equals(macAddress, ignoreCase = true)) {
                return@find true
            }
            // Or match iBeacon UUID + Major + Minor
            if (uuid != null && room.uuid != null && room.uuid.equals(uuid, ignoreCase = true)) {
                if (room.major == null || room.major == major) {
                    if (room.minor == null || room.minor == minor) {
                        return@find true
                    }
                }
            }
            false
        }
    }

    private fun getDefaultRooms(): List<RoomBeaconDefinition> {
        return listOf(
            RoomBeaconDefinition(
                roomId = "bedroom",
                roomName = "Bedroom",
                macAddress = "DD:34:02:88:1A:E1",
                uuid = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
                major = 101,
                minor = 1
            ),
            RoomBeaconDefinition(
                roomId = "desk",
                roomName = "Study / Desk",
                macAddress = "E1:89:12:33:F2:B4",
                uuid = "7B94B88F-D2A4-4822-927A-1910BA8021B1",
                major = 102,
                minor = 2
            ),
            RoomBeaconDefinition(
                roomId = "living",
                roomName = "Living Room",
                macAddress = "C4:55:78:90:11:02",
                uuid = "5A243454-CF6D-4A0F-ADF2-F4911BA9FFA6",
                major = 103,
                minor = 3
            )
        )
    }
}
