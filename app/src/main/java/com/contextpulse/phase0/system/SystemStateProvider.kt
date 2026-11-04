package com.contextpulse.phase0.system

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.contextpulse.phase0.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contract for inspecting and modifying system settings.
 * Allows switching seamlessly between simulated hardware and inspected device state.
 */
interface ISystemStateProvider {
    val isSimulation: Boolean
    val providerName: String

    fun getCurrentSnapshot(activeRoomId: String? = null): SystemSnapshot
    fun applyBrightness(level: Int, mode: BrightnessMode): Boolean
    fun applyDnd(state: DndState): Boolean
    fun applySnapshot(snapshot: SystemSnapshot): Boolean
}

/**
 * In-memory simulated system provider for Phase 2.
 * Strictly guarantees ZERO modifications to the host Android OS or device settings.
 */
class SimulatedSystemStateProvider(
    initialBrightness: Int = 75,
    initialBrightnessMode: BrightnessMode = BrightnessMode.MANUAL,
    initialDnd: DndState = DndState.ALL,
    initialRinger: Int = AudioManager.RINGER_MODE_NORMAL
) : ISystemStateProvider {

    override val isSimulation: Boolean = true
    override val providerName: String = "Simulated Hardware Provider"

    private val _simulatedBrightness = MutableStateFlow(initialBrightness)
    val simulatedBrightness: StateFlow<Int> = _simulatedBrightness.asStateFlow()

    private val _simulatedBrightnessMode = MutableStateFlow(initialBrightnessMode)
    val simulatedBrightnessMode: StateFlow<BrightnessMode> = _simulatedBrightnessMode.asStateFlow()

    private val _simulatedDnd = MutableStateFlow(initialDnd)
    val simulatedDnd: StateFlow<DndState> = _simulatedDnd.asStateFlow()

    private val _simulatedRinger = MutableStateFlow(initialRinger)
    val simulatedRinger: StateFlow<Int> = _simulatedRinger.asStateFlow()

    @Synchronized
    override fun getCurrentSnapshot(activeRoomId: String?): SystemSnapshot {
        return SystemSnapshot(
            source = SnapshotSource.SIMULATED,
            brightnessLevel = SystemSettingValue(
                value = _simulatedBrightness.value,
                isReadable = true,
                requiresPermission = false,
                isPermissionGranted = true,
                statusReason = "Simulated State"
            ),
            brightnessMode = SystemSettingValue(
                value = _simulatedBrightnessMode.value,
                isReadable = true,
                requiresPermission = false,
                isPermissionGranted = true,
                statusReason = "Simulated State"
            ),
            dndState = SystemSettingValue(
                value = _simulatedDnd.value,
                isReadable = true,
                requiresPermission = false,
                isPermissionGranted = true,
                statusReason = "Simulated State"
            ),
            ringerMode = SystemSettingValue(
                value = _simulatedRinger.value,
                isReadable = true,
                requiresPermission = false,
                isPermissionGranted = true,
                statusReason = "Simulated State"
            ),
            activeRoomIdAtCapture = activeRoomId
        )
    }

    @Synchronized
    override fun applyBrightness(level: Int, mode: BrightnessMode): Boolean {
        _simulatedBrightness.value = level.coerceIn(0, 100)
        _simulatedBrightnessMode.value = mode
        Log.d("SimulatedProvider", "Simulated brightness set to: $level% ($mode)")
        return true
    }

    @Synchronized
    override fun applyDnd(state: DndState): Boolean {
        _simulatedDnd.value = state
        Log.d("SimulatedProvider", "Simulated DND set to: ${state.label}")
        return true
    }

    @Synchronized
    override fun applySnapshot(snapshot: SystemSnapshot): Boolean {
        snapshot.brightnessLevel.value?.let { _simulatedBrightness.value = it }
        snapshot.brightnessMode.value?.let { _simulatedBrightnessMode.value = it }
        snapshot.dndState.value?.let { _simulatedDnd.value = it }
        snapshot.ringerMode.value?.let { _simulatedRinger.value = it }
        Log.d("SimulatedProvider", "Restored snapshot: brightness=${snapshot.brightnessLevel.value}%, dnd=${snapshot.dndState.value?.label}")
        return true
    }
}

/**
 * Hardware inspection provider capable of reading real device settings where accessible,
 * but STRICTLY SUPPRESSING actual mutations during Phase 2 testing.
 */
class HardwareInspectionStateProvider(private val context: Context) : ISystemStateProvider {

    override val isSimulation: Boolean = false
    override val providerName: String = "Hardware Inspected (Mutations Suppressed in Phase 2)"

    override fun getCurrentSnapshot(activeRoomId: String?): SystemSnapshot {
        val cr = context.contentResolver

        // Brightness value (0..255 converted to 0..100)
        val (bLevel, bReadable, bReason) = try {
            val raw = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            val pct = ((raw / 255.0) * 100).toInt()
            Triple(pct, true, "Read from Settings.System")
        } catch (e: Exception) {
            Triple(null, false, "Unable to read: ${e.message}")
        }

        // Brightness mode (0=Manual, 1=Auto)
        val (bMode, bModeReadable, bModeReason) = try {
            val rawMode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE)
            Triple(BrightnessMode.fromModeId(rawMode), true, "Read from Settings.System")
        } catch (e: Exception) {
            Triple(BrightnessMode.UNKNOWN, false, "Unable to read: ${e.message}")
        }

        // Notification Policy / DND Filter
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val hasDndPermission = nm?.isNotificationPolicyAccessGranted == true
        val (dndState, dndReadable, dndReason) = if (hasDndPermission && nm != null) {
            val filter = nm.currentInterruptionFilter
            Triple(DndState.fromFilterId(filter), true, "Read via NotificationManager")
        } else {
            Triple(DndState.UNKNOWN, false, "Requires ACCESS_NOTIFICATION_POLICY permission")
        }

        // AudioManager Ringer Mode
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringer = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        return SystemSnapshot(
            source = SnapshotSource.HARDWARE_INSPECTED,
            brightnessLevel = SystemSettingValue(
                value = bLevel,
                isReadable = bReadable,
                requiresPermission = false,
                isPermissionGranted = true,
                statusReason = bReason
            ),
            brightnessMode = SystemSettingValue(
                value = bMode,
                isReadable = bModeReadable,
                requiresPermission = false,
                isPermissionGranted = true,
                statusReason = bModeReason
            ),
            dndState = SystemSettingValue(
                value = dndState,
                isReadable = dndReadable,
                requiresPermission = true,
                isPermissionGranted = hasDndPermission,
                statusReason = dndReason
            ),
            ringerMode = SystemSettingValue(
                value = ringer,
                isReadable = true,
                requiresPermission = false,
                isPermissionGranted = true,
                statusReason = "Read from AudioManager"
            ),
            activeRoomIdAtCapture = activeRoomId
        )
    }

    override fun applyBrightness(level: Int, mode: BrightnessMode): Boolean {
        Log.i("HardwareProvider", "[SIMULATION GUARD] Suppressed native brightness write: $level% ($mode)")
        return true
    }

    override fun applyDnd(state: DndState): Boolean {
        Log.i("HardwareProvider", "[SIMULATION GUARD] Suppressed native DND write: ${state.label}")
        return true
    }

    override fun applySnapshot(snapshot: SystemSnapshot): Boolean {
        Log.i("HardwareProvider", "[SIMULATION GUARD] Suppressed native snapshot restore (Phase 2 constraint)")
        return true
    }
}
