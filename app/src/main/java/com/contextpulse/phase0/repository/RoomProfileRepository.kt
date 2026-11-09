package com.contextpulse.phase0.repository

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.model.RoomProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists user-created Contexts (Rooms) and their automation Modes in SharedPreferences.
 */
class RoomProfileRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _rooms = MutableStateFlow<List<RoomItem>>(emptyList())
    val rooms: StateFlow<List<RoomItem>> = _rooms.asStateFlow()

    private val _activeRoom = MutableStateFlow<RoomItem?>(null)
    val activeRoom: StateFlow<RoomItem?> = _activeRoom.asStateFlow()

    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

    init {
        loadRooms()
    }

    private fun loadRooms() {
        val rawJson = prefs.getString(KEY_ROOMS_JSON, null)
        if (rawJson == null) {
            // Clean slate on fresh install
            saveRoomsInternal(emptyList())
            return
        }
        if (rawJson.isBlank()) {
            _rooms.value = emptyList()
            _activeRoom.value = null
            _activeRoomId.value = null
            return
        }

        try {
            val jsonArray = JSONArray(rawJson)
            val parsedRooms = mutableListOf<RoomItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val subtitle = obj.optString("subtitle", "")
                val isActive = obj.optBoolean("isActive", false)

                val profileObj = obj.optJSONObject("profile")
                val profile = if (profileObj != null) {
                    val blockedArray = profileObj.optJSONArray("blockedApps")
                    val blockedList = mutableListOf<String>()
                    if (blockedArray != null) {
                        for (j in 0 until blockedArray.length()) {
                            blockedList.add(blockedArray.getString(j))
                        }
                    }

                    val groupArray = profileObj.optJSONArray("blockedAppGroupIds")
                    val groupList = mutableListOf<String>()
                    if (groupArray != null) {
                        for (j in 0 until groupArray.length()) {
                            groupList.add(groupArray.getString(j))
                        }
                    }

                    val modesArray = profileObj.optJSONArray("modes")
                    val parsedModes = mutableListOf<ContextTimeframeMode>()
                    if (modesArray != null) {
                        for (m in 0 until modesArray.length()) {
                            val modeObj = modesArray.getJSONObject(m)
                            val modeBlockedArr = modeObj.optJSONArray("blockedApps")
                            val modeBlocked = mutableListOf<String>()
                            if (modeBlockedArr != null) {
                                for (k in 0 until modeBlockedArr.length()) {
                                    modeBlocked.add(modeBlockedArr.getString(k))
                                }
                            }
                            val modeGroupArr = modeObj.optJSONArray("blockedAppGroupIds")
                            val modeGroups = mutableListOf<String>()
                            if (modeGroupArr != null) {
                                for (k in 0 until modeGroupArr.length()) {
                                    modeGroups.add(modeGroupArr.getString(k))
                                }
                            }
                            val daysArr = modeObj.optJSONArray("daysOfWeek")
                            val daysSet = mutableSetOf<Int>()
                            if (daysArr != null) {
                                for (d in 0 until daysArr.length()) {
                                    daysSet.add(daysArr.getInt(d))
                                }
                            } else {
                                daysSet.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
                            }

                            parsedModes.add(ContextTimeframeMode.fromJsonObject(modeObj))
                        }
                    }

                    RoomProfile(
                        spaceName = name,
                        dndEnabled = profileObj.optBoolean("dndEnabled", false),
                        dndMode = profileObj.optString("dndMode", "Priority Only"),
                        brightnessEnabled = profileObj.optBoolean("brightnessEnabled", true),
                        brightnessPercent = profileObj.optInt("brightnessPercent", 50),
                        appRestrictionsEnabled = profileObj.optBoolean("appRestrictionsEnabled", false),
                        blockedApps = blockedList,
                        blockedAppGroupIds = groupList,
                        eyeComfortEnabled = profileObj.optBoolean("eyeComfortEnabled", false),
                        eyeComfortIntensity = profileObj.optInt("eyeComfortIntensity", 50),
                        autoLaunchAppEnabled = profileObj.optBoolean("autoLaunchAppEnabled", false),
                        autoLaunchPackage = profileObj.optString("autoLaunchPackage", ""),
                        autoLaunchAppName = profileObj.optString("autoLaunchAppName", ""),
                        modes = parsedModes
                    ).syncFromModes()
                } else {
                    RoomProfile.defaultProfile(name)
                }

                parsedRooms.add(
                    RoomItem(
                        id = id,
                        name = name,
                        subtitle = subtitle,
                        isActive = isActive,
                        profile = profile
                    )
                )
            }
            _rooms.value = parsedRooms
            val currentActive = parsedRooms.find { it.isActive }
            _activeRoom.value = currentActive
            _activeRoomId.value = currentActive?.id
        } catch (e: Exception) {
            seedDefaultRooms()
        }
    }

    private fun seedDefaultRooms() {
        val defaultRooms = listOf(
            RoomItem(
                id = "room_bedroom",
                name = "Bedroom",
                subtitle = "",
                isActive = false,
                profile = RoomProfile.initialSeedProfile("Bedroom")
            ),
            RoomItem(
                id = "room_office",
                name = "Office",
                subtitle = "",
                isActive = false,
                profile = RoomProfile.initialSeedProfile("Office")
            ),
            RoomItem(
                id = "room_living_room",
                name = "Living Room",
                subtitle = "",
                isActive = false,
                profile = RoomProfile.initialSeedProfile("Living Room")
            ),
            RoomItem(
                id = "room_mosque",
                name = "Mosque",
                subtitle = "",
                isActive = false,
                profile = RoomProfile.initialSeedProfile("Mosque")
            )
        )
        saveRoomsInternal(defaultRooms)
    }

    private fun saveRoomsInternal(updatedRooms: List<RoomItem>) {
        val jsonArray = JSONArray()
        updatedRooms.forEach { room ->
            val obj = JSONObject()
            obj.put("id", room.id)
            obj.put("name", room.name)
            obj.put("subtitle", room.subtitle)
            obj.put("isActive", room.isActive)

            val syncedProfile = room.profile.syncFromModes()
            val profileObj = JSONObject()
            profileObj.put("dndEnabled", syncedProfile.dndEnabled)
            profileObj.put("dndMode", syncedProfile.dndMode)
            profileObj.put("brightnessEnabled", syncedProfile.brightnessEnabled)
            profileObj.put("brightnessPercent", syncedProfile.brightnessPercent)
            profileObj.put("appRestrictionsEnabled", syncedProfile.appRestrictionsEnabled)
            profileObj.put("eyeComfortEnabled", syncedProfile.eyeComfortEnabled)
            profileObj.put("eyeComfortIntensity", syncedProfile.eyeComfortIntensity)
            profileObj.put("autoLaunchAppEnabled", syncedProfile.autoLaunchAppEnabled)
            profileObj.put("autoLaunchPackage", syncedProfile.autoLaunchPackage)
            profileObj.put("autoLaunchAppName", syncedProfile.autoLaunchAppName)

            val blockedArray = JSONArray()
            syncedProfile.blockedApps.forEach { pkg -> blockedArray.put(pkg) }
            profileObj.put("blockedApps", blockedArray)

            val groupArray = JSONArray()
            syncedProfile.blockedAppGroupIds.forEach { gid -> groupArray.put(gid) }
            profileObj.put("blockedAppGroupIds", groupArray)

            val modesArray = JSONArray()
            syncedProfile.normalizedModes().forEach { mode ->
                modesArray.put(mode.toJsonObject())
            }
            profileObj.put("modes", modesArray)

            obj.put("profile", profileObj)
            jsonArray.put(obj)
        }

        prefs.edit().putString(KEY_ROOMS_JSON, jsonArray.toString()).apply()
        _rooms.value = updatedRooms
        val currentActive = updatedRooms.find { it.isActive }
        _activeRoom.value = currentActive
        _activeRoomId.value = currentActive?.id
        com.contextpulse.phase0.widget.ContextPulseWidgetProvider.updateAllWidgets(appContext)

        // Whenever room profiles or mode schedules are saved or modified:
        // Recalculate schedule boundary alarms so schedule timing stays updated
        com.contextpulse.phase0.system.ModeScheduleManager.scheduleNextBoundaryAlarm(appContext)
        if (currentActive != null) {
            com.contextpulse.phase0.system.ProfileActivationHelper.reapplyCurrentActive(appContext)
        }
    }

    fun addRoom(name: String, subtitle: String = ""): RoomItem {
        val cleanName = name.trim()
        val newRoom = RoomItem(
            id = "room_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            subtitle = subtitle.trim(),
            isActive = false,
            profile = RoomProfile.defaultProfile(cleanName)
        )
        val updated = _rooms.value + newRoom
        saveRoomsInternal(updated)
        return newRoom
    }

    fun deleteRoom(roomId: String) {
        val updated = _rooms.value.filterNot { it.id == roomId }
        saveRoomsInternal(updated)
        try {
            val beaconRepo = BeaconRepository.getInstance(appContext)
            beaconRepo.beacons.value
                .filter { it.assignedRoomId == roomId }
                .forEach { trigger ->
                    beaconRepo.saveOrUpdateBeacon(
                        trigger.copy(assignedRoomId = "", assignedRoomName = "")
                    )
                }
        } catch (_: Exception) {
        }
    }

    fun updateRoomDetails(roomId: String, name: String, subtitle: String = "") {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val updated = _rooms.value.map { room ->
            if (room.id == roomId) {
                room.copy(
                    name = cleanName,
                    subtitle = subtitle.trim(),
                    profile = room.profile.copy(spaceName = cleanName)
                )
            } else {
                room
            }
        }
        saveRoomsInternal(updated)
    }

    fun updateRoomProfile(roomId: String, updatedProfile: RoomProfile) {
        val synced = updatedProfile.syncFromModes()
        val updated = _rooms.value.map { room ->
            if (room.id == roomId) {
                room.copy(profile = synced)
            } else {
                room
            }
        }
        saveRoomsInternal(updated)
    }

    fun setActiveRoom(roomId: String?) {
        val updated = _rooms.value.map { room ->
            room.copy(isActive = (room.id == roomId))
        }
        saveRoomsInternal(updated)
    }

    fun getRoomById(roomId: String): RoomItem? {
        return _rooms.value.find { it.id == roomId }
    }

    fun getRoomByName(roomName: String): RoomItem? {
        return _rooms.value.find { it.name.equals(roomName, ignoreCase = true) }
    }

    fun isDuplicateName(name: String, excludeId: String? = null): Boolean {
        val clean = name.trim()
        return _rooms.value.any { it.name.equals(clean, ignoreCase = true) && it.id != excludeId }
    }

    fun createRoom(name: String, subtitle: String = "", iconName: String = "meeting_room"): RoomItem {
        val cleanName = name.trim()
        val newRoom = RoomItem(
            id = "room_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            subtitle = subtitle.trim(),
            iconName = iconName,
            isActive = false,
            profile = RoomProfile.defaultProfile(cleanName)
        )
        val updated = _rooms.value + newRoom
        saveRoomsInternal(updated)
        return newRoom
    }

    fun createRoomWithProfile(
        name: String,
        subtitle: String = "",
        iconName: String = "meeting_room",
        profile: RoomProfile
    ): RoomItem {
        val cleanName = name.trim()
        val newRoom = RoomItem(
            id = "room_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            subtitle = subtitle.trim(),
            iconName = iconName,
            isActive = false,
            profile = profile.copy(spaceName = cleanName)
        )
        val updated = _rooms.value + newRoom
        saveRoomsInternal(updated)
        return newRoom
    }

    fun updateRoomFull(
        roomId: String,
        newName: String,
        newSubtitle: String,
        newIconName: String,
        newProfile: RoomProfile
    ) {
        val synced = newProfile.syncFromModes()
        val updated = _rooms.value.map { room ->
            if (room.id == roomId) {
                room.copy(
                    name = newName.trim(),
                    subtitle = newSubtitle.trim(),
                    iconName = newIconName,
                    profile = synced.copy(spaceName = newName.trim())
                )
            } else {
                room
            }
        }
        saveRoomsInternal(updated)
    }

    fun moveRoom(fromIndex: Int, toIndex: Int) {
        val current = _rooms.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            saveRoomsInternal(current)
        }
    }

    fun wipeAllProfiles() {
        saveRoomsInternal(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "contextpulse_room_profiles_v1"
        private const val KEY_ROOMS_JSON = "rooms_json_v1"

        @Volatile
        private var INSTANCE: RoomProfileRepository? = null

        fun getInstance(context: Context): RoomProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoomProfileRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
