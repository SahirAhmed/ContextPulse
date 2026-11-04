package com.contextpulse.phase0.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import com.contextpulse.phase0.model.BeaconItem
import com.contextpulse.phase0.model.BroadcastStatus
import com.contextpulse.phase0.model.RecentActivityItem
import com.contextpulse.phase0.model.TriggerItem
import com.contextpulse.phase0.model.TriggerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.UUID

/**
 * Repository for managing triggers (BLE beacons, NFC chips, QR codes) and recent activities.
 * Fresh install starts completely clean with NO preset dummy triggers.
 */
class BeaconRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_beacons_v1"
        private const val KEY_BEACONS_JSON = "key_beacons_json"
        private const val KEY_ACTIVITIES_JSON = "key_activities_json"
        private const val CHANNEL_TRIGGER_ALERTS = "channel_trigger_alerts"

        @Volatile
        private var INSTANCE: BeaconRepository? = null

        fun getInstance(context: Context): BeaconRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BeaconRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _beacons = MutableStateFlow<List<TriggerItem>>(emptyList())
    val beacons: StateFlow<List<TriggerItem>> = _beacons.asStateFlow()
    val sensors: StateFlow<List<TriggerItem>> = _beacons.asStateFlow()
    val triggers: StateFlow<List<TriggerItem>> = _beacons.asStateFlow()

    private val _activities = MutableStateFlow<List<RecentActivityItem>>(emptyList())
    val activities: StateFlow<List<RecentActivityItem>> = _activities.asStateFlow()

    init {
        loadData()
    }

    @Synchronized
    fun loadData() {
        val beaconsJson = prefs.getString(KEY_BEACONS_JSON, null)
        val loadedTriggers: List<TriggerItem> = if (beaconsJson == null) {
            // Fresh first install: completely clean slate with NO preset triggers
            saveBeacons(emptyList())
            emptyList()
        } else {
            try {
                val list = mutableListOf<TriggerItem>()
                val array = JSONArray(beaconsJson)
                for (i in 0 until array.length()) {
                    list.add(TriggerItem.fromJsonObject(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
        _beacons.value = loadedTriggers

        val activitiesJson = prefs.getString(KEY_ACTIVITIES_JSON, null)
        val loadedActivities: List<RecentActivityItem> = if (activitiesJson == null) {
            saveActivities(emptyList())
            emptyList()
        } else {
            try {
                val list = mutableListOf<RecentActivityItem>()
                val array = JSONArray(activitiesJson)
                for (i in 0 until array.length()) {
                    list.add(RecentActivityItem.fromJsonObject(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
        _activities.value = loadedActivities
    }

    @Synchronized
    fun reconcileWithRoomNames(existingRoomNames: Set<String>) {
        val lowerNames = existingRoomNames.map { it.lowercase().trim() }.toSet()
        var changed = false
        val updated = _beacons.value.map { trigger ->
            if (trigger.assignedRoomName != null && trigger.assignedRoomName!!.lowercase().trim() !in lowerNames) {
                changed = true
                trigger.copy(assignedRoomName = null)
            } else {
                trigger
            }
        }
        if (changed) {
            _beacons.value = updated
            saveBeacons(updated)
        }
    }

    @Synchronized
    fun updateBeaconName(triggerId: String, newName: String) {
        val updated = _beacons.value.map { trigger ->
            if (trigger.id == triggerId) {
                trigger.copy(name = newName.trim())
            } else {
                trigger
            }
        }
        _beacons.value = updated
        saveBeacons(updated)
    }

    @Synchronized
    fun updateSensor(updatedTrigger: TriggerItem) {
        val updated = _beacons.value.map { trigger ->
            if (trigger.id == updatedTrigger.id) {
                updatedTrigger
            } else {
                trigger
            }
        }
        _beacons.value = updated
        saveBeacons(updated)
    }

    @Synchronized
    fun saveTrigger(updatedTrigger: TriggerItem) {
        updateSensor(updatedTrigger)
    }


    @Synchronized
    fun updateAssignedRoomName(oldRoomName: String, newRoomName: String) {
        val updated = _beacons.value.map { trigger ->
            if (trigger.assignedRoomName?.equals(oldRoomName, ignoreCase = true) == true) {
                trigger.copy(assignedRoomName = newRoomName.trim())
            } else {
                trigger
            }
        }
        _beacons.value = updated
        saveBeacons(updated)
    }

    /**
     * Creates an unconfigured trigger with a user-provided name.
     */
    @Synchronized
    fun addUnconfiguredTrigger(name: String): TriggerItem {
        val triggerName = name.trim()
        val item = TriggerItem(
            name = triggerName,
            type = TriggerType.UNCONFIGURED,
            isConfigured = false,
            code = "CP-${UUID.randomUUID().toString().take(4).uppercase()}",
            assignedRoomName = null,
            batteryPercent = 100,
            rssi = -55
        )
        val updated = _beacons.value.toMutableList().apply { add(item) }
        _beacons.value = updated
        saveBeacons(updated)
        return item
    }

    @Synchronized
    fun addUnconfiguredSensor(name: String = ""): TriggerItem {
        val finalName = if (name.isNotBlank()) name.trim() else "Trigger #${_beacons.value.size + 1}"
        return addUnconfiguredTrigger(finalName)
    }

    /**
     * Configures a trigger with one of the 3 primary connection methods: BLE Beacon, NFC Tag, or QR Code.
     */
    @Synchronized
    fun configureSensor(
        sensorId: String,
        name: String,
        type: TriggerType,
        roomName: String? = null,
        code: String = "",
        macAddress: String = "",
        nfcTagUid: String = "",
        qrPayload: String = ""
    ): TriggerItem? {
        var result: TriggerItem? = null
        val targetRoom = roomName?.trim()?.ifBlank { null }

        val updated = _beacons.value.map { trigger ->
            if (targetRoom != null && trigger.assignedRoomName?.equals(targetRoom, ignoreCase = true) == true && trigger.id != sensorId) {
                trigger.copy(assignedRoomName = null)
            } else if (trigger.id == sensorId) {
                val configured = trigger.copy(
                    name = name.trim().ifBlank { trigger.name },
                    type = type,
                    isConfigured = true,
                    assignedRoomName = targetRoom ?: trigger.assignedRoomName,
                    code = if (code.isNotBlank()) code.trim() else trigger.code,
                    macAddress = macAddress.trim(),
                    nfcTagUid = nfcTagUid.trim(),
                    qrPayload = qrPayload.trim()
                )
                result = configured
                configured
            } else {
                trigger
            }
        }
        _beacons.value = updated
        saveBeacons(updated)
        return result
    }

    @Synchronized
    fun addBeacon(name: String, code: String, roomName: String?, batteryPercent: Int = 100): TriggerItem {
        val item = TriggerItem(
            name = name.trim(),
            type = TriggerType.BLE_BEACON,
            isConfigured = true,
            code = code.trim(),
            assignedRoomName = if (roomName.isNullOrBlank()) null else roomName.trim(),
            batteryPercent = batteryPercent,
            rssi = -55
        )
        val updated = _beacons.value.toMutableList().apply { add(item) }
        _beacons.value = updated
        saveBeacons(updated)
        return item
    }

    @Synchronized
    fun deleteBeacon(triggerId: String) {
        val updated = _beacons.value.filterNot { it.id == triggerId }
        _beacons.value = updated
        saveBeacons(updated)
    }

    @Synchronized
    fun deleteSensor(triggerId: String) = deleteBeacon(triggerId)

    @Synchronized
    fun attachBeaconToRoom(triggerId: String, roomName: String) {
        val targetRoom = roomName.trim()
        val updated = _beacons.value.map { trigger ->
            when {
                trigger.assignedRoomName?.equals(targetRoom, ignoreCase = true) == true && trigger.id != triggerId -> {
                    trigger.copy(assignedRoomName = null)
                }
                trigger.id == triggerId -> {
                    trigger.copy(assignedRoomName = targetRoom)
                }
                else -> trigger
            }
        }
        _beacons.value = updated
        saveBeacons(updated)
    }

    @Synchronized
    fun detachBeaconFromRoom(triggerId: String) {
        val updated = _beacons.value.map { trigger ->
            if (trigger.id == triggerId) {
                trigger.copy(assignedRoomName = null)
            } else {
                trigger
            }
        }
        _beacons.value = updated
        saveBeacons(updated)
    }

    @Synchronized
    fun detachBeaconByRoomName(roomName: String) {
        val targetRoom = roomName.trim()
        val updated = _beacons.value.map { trigger ->
            if (trigger.assignedRoomName?.equals(targetRoom, ignoreCase = true) == true) {
                trigger.copy(assignedRoomName = null)
            } else {
                trigger
            }
        }
        _beacons.value = updated
        saveBeacons(updated)
    }

    @Synchronized
    fun getBeaconForRoom(roomName: String): TriggerItem? {
        return _beacons.value.find { it.assignedRoomName?.equals(roomName.trim(), ignoreCase = true) == true }
    }

    @Synchronized
    fun wipeAllBeacons() {
        prefs.edit().clear().commit()
        saveBeacons(emptyList())
        saveActivities(emptyList())
        _beacons.value = emptyList()
        _activities.value = emptyList()
    }

    fun isDuplicateName(name: String, excludeId: String? = null): Boolean {
        val trimmed = name.trim()
        return _beacons.value.any {
            it.name.equals(trimmed, ignoreCase = true) && (excludeId == null || it.id != excludeId)
        }
    }

    /**
     * Checks triggers for low battery (<20%) or offline status and dispatches alert notification.
     */
    fun checkAndNotifyTriggerStatus(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_TRIGGER_ALERTS,
                "Trigger Health & Battery Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for low beacon battery or stopped trigger broadcasts"
            }
            nm.createNotificationChannel(channel)
        }

        _beacons.value.forEachIndexed { index, trigger ->
            if (trigger.isConfigured) {
                val status = trigger.getBroadcastStatus()
                if (status == BroadcastStatus.LOW_BATTERY) {
                    val notif = NotificationCompat.Builder(context, CHANNEL_TRIGGER_ALERTS)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Low Battery: ${trigger.name}")
                        .setContentText("Trigger battery is at ${trigger.batteryPercent}%. Please replace or recharge soon.")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(10000 + index, notif)
                } else if (status == BroadcastStatus.OFFLINE) {
                    val notif = NotificationCompat.Builder(context, CHANNEL_TRIGGER_ALERTS)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Broadcast Stopped: ${trigger.name}")
                        .setContentText("The trigger has stopped broadcasting proximity signals.")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(20000 + index, notif)
                }
            }
        }
    }

    private fun saveBeacons(list: List<TriggerItem>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_BEACONS_JSON, array.toString()).apply()
    }

    @Synchronized
    fun moveTrigger(fromIndex: Int, toIndex: Int) {
        val current = _beacons.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _beacons.value = current
            saveBeacons(current)
        }
    }

    @Synchronized
    fun reorderTriggers(newOrder: List<TriggerItem>) {
        _beacons.value = newOrder
        saveBeacons(newOrder)
    }

    private fun saveActivities(list: List<RecentActivityItem>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_ACTIVITIES_JSON, array.toString()).apply()
    }
}
