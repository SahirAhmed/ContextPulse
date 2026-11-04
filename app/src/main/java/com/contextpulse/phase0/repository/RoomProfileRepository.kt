package com.contextpulse.phase0.repository

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.model.RoomProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Repository for managing Context spaces and their timeframe modes.
 * Fresh install starts completely wiped/clean without preset mock contexts.
 */
class RoomProfileRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_room_profiles_v1"
        private const val KEY_ROOMS_JSON = "key_rooms_json"
        private const val KEY_ACTIVE_ROOM_ID = "key_active_room_id"
        private const val KEY_SCHEMA_VERSION = "key_schema_version"
        private const val CURRENT_SCHEMA_VERSION = 2

        @Volatile
        private var INSTANCE: RoomProfileRepository? = null

        fun getInstance(context: Context): RoomProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoomProfileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _rooms = MutableStateFlow<List<RoomItem>>(emptyList())
    val rooms: StateFlow<List<RoomItem>> = _rooms.asStateFlow()

    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

    private val _activeRoom = MutableStateFlow<RoomItem?>(null)
    val activeRoom: StateFlow<RoomItem?> = _activeRoom.asStateFlow()

    init {
        loadData()
    }

    @Synchronized
    fun loadData() {
        val storedVersion = prefs.getInt(KEY_SCHEMA_VERSION, 1)
        val jsonString = prefs.getString(KEY_ROOMS_JSON, null)

        val loadedRooms: List<RoomItem> = if (jsonString == null) {
            // Fresh first install: clean slate with NO preset profiles
            saveRoomsToPrefs(emptyList())
            emptyList()
        } else {
            try {
                val list = mutableListOf<RoomItem>()
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    list.add(RoomItem.fromJsonObject(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        _rooms.value = loadedRooms

        val activeId = prefs.getString(KEY_ACTIVE_ROOM_ID, null)
        _activeRoomId.value = activeId
        _activeRoom.value = loadedRooms.find { it.id == activeId }
    }

    private fun saveRoomsToPrefs(roomsList: List<RoomItem>) {
        val array = JSONArray()
        roomsList.forEach { array.put(it.toJsonObject()) }
        prefs.edit()
            .putString(KEY_ROOMS_JSON, array.toString())
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .commit()
    }

    @Synchronized
    fun createRoom(
        name: String,
        subtitle: String = "",
        iconName: String = "meeting_room",
        profile: RoomProfile = RoomProfile.defaultProfile(name)
    ): RoomItem {
        val newRoom = RoomItem(
            name = name.trim(),
            subtitle = subtitle.trim(),
            iconName = iconName,
            profile = profile
        )
        val current = _rooms.value.toMutableList()
        current.add(newRoom)
        _rooms.value = current
        saveRoomsToPrefs(current)
        return newRoom
    }

    @Synchronized
    fun updateRoomName(roomId: String, newName: String) {
        val current = _rooms.value.toMutableList()
        val index = current.indexOfFirst { it.id == roomId }
        if (index != -1) {
            val updated = current[index].copy(
                name = newName.trim(),
                updatedAt = System.currentTimeMillis()
            )
            current[index] = updated
            _rooms.value = current
            saveRoomsToPrefs(current)

            if (_activeRoomId.value == roomId) {
                _activeRoom.value = updated
            }
        }
    }

    @Synchronized
    fun updateRoomFull(
        roomId: String,
        newName: String,
        newSubtitle: String,
        newIconName: String,
        newProfile: RoomProfile
    ) {
        val current = _rooms.value.toMutableList()
        val index = current.indexOfFirst { it.id == roomId }
        if (index != -1) {
            val updated = current[index].copy(
                name = newName.trim(),
                subtitle = newSubtitle.trim(),
                iconName = newIconName,
                profile = newProfile,
                updatedAt = System.currentTimeMillis()
            )
            current[index] = updated
            _rooms.value = current
            saveRoomsToPrefs(current)

            if (_activeRoomId.value == roomId) {
                _activeRoom.value = updated
            }
        }
    }

    @Synchronized
    fun deleteRoom(roomId: String) {
        val current = _rooms.value.toMutableList()
        current.removeAll { it.id == roomId }
        _rooms.value = current
        saveRoomsToPrefs(current)

        if (_activeRoomId.value == roomId) {
            _activeRoomId.value = null
            _activeRoom.value = null
            prefs.edit().remove(KEY_ACTIVE_ROOM_ID).apply()
        }
    }

    @Synchronized
    fun setActiveRoom(roomId: String?) {
        _activeRoomId.value = roomId
        if (roomId == null) {
            _activeRoom.value = null
            prefs.edit().remove(KEY_ACTIVE_ROOM_ID).apply()
        } else {
            val found = _rooms.value.find { it.id == roomId }
            _activeRoom.value = found
            prefs.edit().putString(KEY_ACTIVE_ROOM_ID, roomId).apply()
        }
    }

    @Synchronized
    fun wipeAllProfiles() {
        prefs.edit().clear().commit()
        _rooms.value = emptyList()
        _activeRoomId.value = null
        _activeRoom.value = null
    }

    @Synchronized
    fun moveRoom(fromIndex: Int, toIndex: Int) {
        val current = _rooms.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _rooms.value = current
            saveRoomsToPrefs(current)
        }
    }

    @Synchronized
    fun reorderRooms(newOrder: List<RoomItem>) {
        _rooms.value = newOrder
        saveRoomsToPrefs(newOrder)
    }

    fun isDuplicateName(name: String, excludeId: String? = null): Boolean {
        val trimmed = name.trim()
        return _rooms.value.any {
            it.name.equals(trimmed, ignoreCase = true) && (excludeId == null || it.id != excludeId)
        }
    }
}
