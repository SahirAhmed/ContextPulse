package com.contextpulse.phase0.model

import org.json.JSONObject
import java.util.UUID

enum class SnapshotSource {
    SIMULATED,
    HARDWARE_INSPECTED
}

enum class DndState(val filterId: Int, val label: String) {
    ALL(1, "All / Normal"),
    PRIORITY(2, "Priority Only"),
    TOTAL_SILENCE(3, "Total Silence"),
    ALARMS(4, "Alarms Only"),
    UNKNOWN(-1, "Unknown / Permission Denied");

    companion object {
        fun fromLabel(label: String): DndState {
            return when {
                label.contains("Silence", ignoreCase = true) -> TOTAL_SILENCE
                label.contains("Priority", ignoreCase = true) -> PRIORITY
                label.contains("Alarm", ignoreCase = true) -> ALARMS
                label.contains("All", ignoreCase = true) -> ALL
                else -> PRIORITY
            }
        }

        fun fromFilterId(id: Int): DndState {
            return entries.find { it.filterId == id } ?: UNKNOWN
        }
    }
}

enum class BrightnessMode(val modeId: Int, val label: String) {
    MANUAL(0, "Manual"),
    AUTOMATIC(1, "Adaptive / Automatic"),
    UNKNOWN(-1, "Unknown / Permission Denied");

    companion object {
        fun fromModeId(id: Int): BrightnessMode {
            return entries.find { it.modeId == id } ?: UNKNOWN
        }
    }
}

/**
 * Encapsulates an individual system setting value alongside its readability,
 * permission requirement, and explicit permission grant status.
 * Prevents inventing or guessing unreadable values.
 */
data class SystemSettingValue<T>(
    val value: T?,
    val isReadable: Boolean = true,
    val requiresPermission: Boolean = false,
    val isPermissionGranted: Boolean = true,
    val statusReason: String = "OK"
) {
    fun toJsonObject(valueSerializer: (T?) -> Any?): JSONObject {
        return JSONObject().apply {
            put("value", valueSerializer(value) ?: JSONObject.NULL)
            put("isReadable", isReadable)
            put("requiresPermission", requiresPermission)
            put("isPermissionGranted", isPermissionGranted)
            put("statusReason", statusReason)
        }
    }

    companion object {
        fun <T> fromJsonObject(obj: JSONObject, valueDeserializer: (Any?) -> T?): SystemSettingValue<T> {
            val rawVal = if (obj.isNull("value")) null else obj.get("value")
            return SystemSettingValue(
                value = valueDeserializer(rawVal),
                isReadable = obj.optBoolean("isReadable", true),
                requiresPermission = obj.optBoolean("requiresPermission", false),
                isPermissionGranted = obj.optBoolean("isPermissionGranted", true),
                statusReason = obj.optString("statusReason", "OK")
            )
        }
    }
}

/**
 * SystemSnapshot represents an immutable snapshot of Android system settings
 * captured prior to applying a room profile, enabling clean restoration upon deactivation.
 */
data class SystemSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: SnapshotSource = SnapshotSource.SIMULATED,
    val brightnessLevel: SystemSettingValue<Int>, // 0..100 (%)
    val brightnessMode: SystemSettingValue<BrightnessMode>,
    val dndState: SystemSettingValue<DndState>,
    val ringerMode: SystemSettingValue<Int>, // 0=Silent, 1=Vibrate, 2=Normal
    val activeRoomIdAtCapture: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("source", source.name)
            put("brightnessLevel", brightnessLevel.toJsonObject { it })
            put("brightnessMode", brightnessMode.toJsonObject { it?.name })
            put("dndState", dndState.toJsonObject { it?.name })
            put("ringerMode", ringerMode.toJsonObject { it })
            activeRoomIdAtCapture?.let { put("activeRoomIdAtCapture", it) }
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): SystemSnapshot {
            val srcStr = obj.optString("source", SnapshotSource.SIMULATED.name)
            val src = try { SnapshotSource.valueOf(srcStr) } catch (e: Exception) { SnapshotSource.SIMULATED }

            val bLevel = SystemSettingValue.fromJsonObject(obj.getJSONObject("brightnessLevel")) { raw ->
                when (raw) {
                    is Number -> raw.toInt()
                    else -> null
                }
            }

            val bMode = SystemSettingValue.fromJsonObject(obj.getJSONObject("brightnessMode")) { raw ->
                when (raw) {
                    is String -> try { BrightnessMode.valueOf(raw) } catch (e: Exception) { null }
                    is Number -> BrightnessMode.fromModeId(raw.toInt())
                    else -> null
                }
            }

            val dnd = SystemSettingValue.fromJsonObject(obj.getJSONObject("dndState")) { raw ->
                when (raw) {
                    is String -> try { DndState.valueOf(raw) } catch (e: Exception) { null }
                    is Number -> DndState.fromFilterId(raw.toInt())
                    else -> null
                }
            }

            val ringer = SystemSettingValue.fromJsonObject(obj.getJSONObject("ringerMode")) { raw ->
                when (raw) {
                    is Number -> raw.toInt()
                    else -> null
                }
            }

            val activeRoomId = if (obj.has("activeRoomIdAtCapture") && !obj.isNull("activeRoomIdAtCapture")) {
                obj.getString("activeRoomIdAtCapture")
            } else null

            return SystemSnapshot(
                id = obj.optString("id", UUID.randomUUID().toString()),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                source = src,
                brightnessLevel = bLevel,
                brightnessMode = bMode,
                dndState = dnd,
                ringerMode = ringer,
                activeRoomIdAtCapture = activeRoomId
            )
        }

        fun createSimulated(
            brightness: Int = 70,
            brightnessMode: BrightnessMode = BrightnessMode.MANUAL,
            dnd: DndState = DndState.ALL,
            ringer: Int = 2,
            activeRoomId: String? = null
        ): SystemSnapshot {
            return SystemSnapshot(
                source = SnapshotSource.SIMULATED,
                brightnessLevel = SystemSettingValue(value = brightness, isReadable = true, requiresPermission = false),
                brightnessMode = SystemSettingValue(value = brightnessMode, isReadable = true, requiresPermission = false),
                dndState = SystemSettingValue(value = dnd, isReadable = true, requiresPermission = false),
                ringerMode = SystemSettingValue(value = ringer, isReadable = true, requiresPermission = false),
                activeRoomIdAtCapture = activeRoomId
            )
        }
    }
}

/**
 * Explicit state machine for tracking transitions and recovering safely
 * if the app or device is interrupted mid-transition.
 */
enum class TransitionState {
    IDLE,                  // No profile active; baseline empty
    CAPTURING_BASELINE,    // 0 -> 1: Capturing baseline before profile application
    APPLYING_PROFILE,      // Applying profile settings to simulated hardware
    ACTIVE,                // Profile active; baseline safely preserved
    RESTORING_BASELINE,    // 1 -> 0: Restoring baseline settings
    RECOVERY_REQUIRED      // Detected unclean exit mid-transition on launch
}

/**
 * Immutable representation of the Context Engine's state, published to the UI.
 */
data class ContextEngineState(
    val activeRoomId: String? = null,
    val activeRoomName: String? = null,
    val activeProfile: RoomProfile? = null,
    val transitionState: TransitionState = TransitionState.IDLE,
    val baselineSnapshot: SystemSnapshot? = null,
    val currentSimulatedState: SystemSnapshot,
    val lastTransitionTimestamp: Long = 0L,
    val transitionLogs: List<String> = emptyList()
)
