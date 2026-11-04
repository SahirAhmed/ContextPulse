package com.contextpulse.phase0.system

import android.content.Context
import android.util.Log
import com.contextpulse.phase0.model.*
import com.contextpulse.phase0.repository.RoomProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core Context Engine coordinating Room Profiles, Baseline State Lifecycle, and System State Simulation.
 *
 * Enforces architectural boundary:
 * UI and MainActivity do NOT contain state-machine or transition logic.
 * All context switching, snapshot retention, and restoration flows are handled here.
 */
class ContextEngine(
    private val repository: RoomProfileRepository,
    private val baselineManager: BaselineStateManager,
    val systemProvider: ISystemStateProvider = SimulatedSystemStateProvider()
) {
    companion object {
        private const val TAG = "ContextEngine"

        @Volatile
        private var INSTANCE: ContextEngine? = null

        fun getInstance(context: Context): ContextEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val repo = RoomProfileRepository.getInstance(context)
                    val baseManager = BaselineStateManager.getInstance(context)
                    ContextEngine(repo, baseManager).also { INSTANCE = it }
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _engineState = MutableStateFlow(
        ContextEngineState(
            activeRoomId = repository.activeRoomId.value,
            activeRoomName = repository.activeRoom.value?.name,
            activeProfile = repository.activeRoom.value?.profile,
            transitionState = baselineManager.transitionState.value,
            baselineSnapshot = baselineManager.baselineSnapshot.value,
            currentSimulatedState = systemProvider.getCurrentSnapshot()
        )
    )
    val engineState: StateFlow<ContextEngineState> = _engineState.asStateFlow()

    private val _transitionLogs = MutableStateFlow<List<String>>(emptyList())
    val transitionLogs: StateFlow<List<String>> = _transitionLogs.asStateFlow()

    init {
        // Run interrupted transition reconciliation on startup
        val recoveryReport = baselineManager.checkAndReconcileRecovery(systemProvider)
        if (recoveryReport.requiredRecovery) {
            logTransition("RECOVERY: ${recoveryReport.actionTaken}")
        }

        // Reconcile if repository active room is out of sync with baseline manager
        reconcileInitialState()

        updateEngineState()
    }

    private fun logTransition(msg: String) {
        val entry = "[${System.currentTimeMillis() % 100000}] $msg"
        val current = _transitionLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 20) current.removeAt(current.size - 1)
        _transitionLogs.value = current
        Log.d(TAG, msg)
    }

    @Synchronized
    private fun reconcileInitialState() {
        val activeRoom = repository.activeRoom.value
        val hasBaseline = baselineManager.hasBaseline()

        if (activeRoom != null && !hasBaseline) {
            // App was killed or active room loaded from persistent prefs: capture baseline for active state
            baselineManager.captureBaseline(activeRoom.id, systemProvider)
            applyProfileToProvider(activeRoom.profile)
            baselineManager.setTransitionState(TransitionState.ACTIVE)
            logTransition("Reconciled startup: Captured baseline for pre-existing active room '${activeRoom.name}'")
        } else if (activeRoom == null && hasBaseline) {
            // Baseline exists but no room active: clean up
            baselineManager.restoreBaseline(systemProvider)
            logTransition("Reconciled startup: Restored orphaned baseline with no active room")
        }
    }

    private fun updateEngineState() {
        val room = repository.activeRoom.value
        _engineState.value = ContextEngineState(
            activeRoomId = repository.activeRoomId.value,
            activeRoomName = room?.name,
            activeProfile = room?.profile,
            transitionState = baselineManager.transitionState.value,
            baselineSnapshot = baselineManager.baselineSnapshot.value,
            currentSimulatedState = systemProvider.getCurrentSnapshot(),
            lastTransitionTimestamp = System.currentTimeMillis(),
            transitionLogs = _transitionLogs.value
        )
    }

    /**
     * Manually activates a room profile.
     * Handles:
     * - 0 -> 1: Initial activation (captures baseline, applies profile)
     * - 1 -> 2: Room switch (preserves original baseline without overwriting, applies new profile)
     */
    @Synchronized
    fun activateRoom(roomId: String): Boolean {
        val targetRoom = repository.rooms.value.find { it.id == roomId }
        if (targetRoom == null) {
            Log.w(TAG, "Cannot activate room: ID '$roomId' not found")
            return false
        }

        val currentActiveId = repository.activeRoomId.value

        if (currentActiveId == roomId) {
            Log.i(TAG, "Room '${targetRoom.name}' is already active.")
            return true
        }

        val isInitialActivation = (currentActiveId == null)

        if (isInitialActivation) {
            // 0 -> 1 Transition
            logTransition("0 -> 1: Capturing baseline for initial activation of '${targetRoom.name}'")
            baselineManager.captureBaseline(roomId, systemProvider)
        } else {
            // 1 -> 2 Transition (Room Switch)
            val currentRoomName = repository.activeRoom.value?.name ?: "Unknown"
            logTransition("1 -> 2: Switching profile from '$currentRoomName' to '${targetRoom.name}'. Retaining original baseline.")
        }

        baselineManager.setTransitionState(TransitionState.APPLYING_PROFILE)

        // Apply profile settings to simulated provider
        applyProfileToProvider(targetRoom.profile)

        // Update repository
        repository.setActiveRoom(roomId)
        baselineManager.setTransitionState(TransitionState.ACTIVE)

        logTransition("Profile '${targetRoom.name}' is now ACTIVE (Simulation)")
        updateEngineState()
        return true
    }

    /**
     * Manually deactivates the currently active room profile (1 -> 0 Transition).
     * Restores original baseline settings and clears stored baseline.
     */
    @Synchronized
    fun deactivateRoom(): Boolean {
        val currentRoomName = repository.activeRoom.value?.name ?: "Unknown"
        val activeId = repository.activeRoomId.value

        if (activeId == null) {
            Log.i(TAG, "No profile is currently active.")
            return true
        }

        logTransition("1 -> 0: Deactivating '$currentRoomName'. Restoring original baseline...")
        baselineManager.setTransitionState(TransitionState.RESTORING_BASELINE)

        val restored = baselineManager.restoreBaseline(systemProvider)
        repository.setActiveRoom(null)
        baselineManager.setTransitionState(TransitionState.IDLE)

        logTransition("Original baseline restored. Context Engine is now IDLE.")
        updateEngineState()
        return restored
    }

    /**
     * Toggles room activation status.
     */
    @Synchronized
    fun toggleRoom(roomId: String): Boolean {
        return if (repository.activeRoomId.value == roomId) {
            deactivateRoom()
        } else {
            activateRoom(roomId)
        }
    }

    private fun applyProfileToProvider(profile: RoomProfile) {
        if (profile.brightnessEnabled) {
            systemProvider.applyBrightness(profile.brightnessLevel, BrightnessMode.MANUAL)
        }
        if (profile.dndEnabled) {
            systemProvider.applyDnd(DndState.fromLabel(profile.dndMode))
        }
    }

    /**
     * Allows test simulation to directly mutate simulated hardware state
     * (e.g. simulating user changing brightness before profile activation).
     */
    fun simulateExternalStateChange(brightness: Int, mode: BrightnessMode, dnd: DndState) {
        systemProvider.applyBrightness(brightness, mode)
        systemProvider.applyDnd(dnd)
        updateEngineState()
    }
}
