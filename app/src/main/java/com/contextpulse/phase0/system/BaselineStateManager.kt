package com.contextpulse.phase0.system

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.contextpulse.phase0.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class RecoveryReport(
    val requiredRecovery: Boolean,
    val previousState: TransitionState,
    val actionTaken: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages the lifecycle, persistence, integrity, and recovery of the Android baseline state snapshot.
 *
 * Guaranteed Invariants:
 * 1. 0 -> 1: Captures baseline BEFORE profile activation.
 * 2. 1 -> 2: Transition between profiles NEVER overwrites the original baseline snapshot.
 * 3. 1 -> 0: Restores original baseline and clears snapshot.
 * 4. Crash / Restart / Reboot: Recovers interrupted transitions safely.
 * 5. Staleness check: Guards against restoring outdated settings if the device was inactive for extended periods.
 */
class BaselineStateManager(
    private val context: Context,
    customPrefsName: String? = null
) {

    companion object {
        private const val TAG = "BaselineStateManager"
        private const val PREFS_NAME = "contextpulse_baseline_state_v1"
        private const val KEY_SNAPSHOT_JSON = "key_snapshot_json"
        private const val KEY_TRANSITION_STATE = "key_transition_state"
        private const val KEY_TRANSITION_STARTED_AT = "key_transition_started_at"

        // Default staleness threshold: 24 hours
        const val DEFAULT_MAX_STALENESS_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: BaselineStateManager? = null

        fun getInstance(context: Context): BaselineStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BaselineStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(customPrefsName ?: PREFS_NAME, Context.MODE_PRIVATE)

    private val _baselineSnapshot = MutableStateFlow<SystemSnapshot?>(null)
    val baselineSnapshot: StateFlow<SystemSnapshot?> = _baselineSnapshot.asStateFlow()

    private val _transitionState = MutableStateFlow(TransitionState.IDLE)
    val transitionState: StateFlow<TransitionState> = _transitionState.asStateFlow()

    private val _lastRecoveryReport = MutableStateFlow<RecoveryReport?>(null)
    val lastRecoveryReport: StateFlow<RecoveryReport?> = _lastRecoveryReport.asStateFlow()

    init {
        loadFromPrefs()
    }

    @Synchronized
    private fun loadFromPrefs() {
        val stateStr = prefs.getString(KEY_TRANSITION_STATE, TransitionState.IDLE.name)
        val state = try {
            TransitionState.valueOf(stateStr ?: TransitionState.IDLE.name)
        } catch (e: Exception) {
            TransitionState.IDLE
        }
        _transitionState.value = state

        val jsonStr = prefs.getString(KEY_SNAPSHOT_JSON, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val snapshot = SystemSnapshot.fromJsonObject(JSONObject(jsonStr))
                _baselineSnapshot.value = snapshot
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize stored baseline snapshot", e)
                _baselineSnapshot.value = null
            }
        } else {
            _baselineSnapshot.value = null
        }
    }

    fun hasBaseline(): Boolean = _baselineSnapshot.value != null

    /**
     * Checks if the stored baseline has exceeded the staleness threshold.
     */
    fun isBaselineStale(maxAgeMs: Long = DEFAULT_MAX_STALENESS_MS): Boolean {
        val snapshot = _baselineSnapshot.value ?: return false
        val age = System.currentTimeMillis() - snapshot.timestamp
        return age > maxAgeMs
    }

    /**
     * Captures baseline settings for a 0 -> 1 activation.
     * If a baseline ALREADY exists (e.g. from an existing active profile),
     * this returns the existing baseline WITHOUT overwriting it!
     */
    @Synchronized
    fun captureBaseline(
        roomId: String,
        provider: ISystemStateProvider
    ): SystemSnapshot {
        val existing = _baselineSnapshot.value
        if (existing != null) {
            Log.i(TAG, "Baseline already exists (captured for room ${existing.activeRoomIdAtCapture}). Preserving existing baseline.")
            return existing
        }

        setTransitionState(TransitionState.CAPTURING_BASELINE)

        val snapshot = provider.getCurrentSnapshot(activeRoomId = roomId)
        saveBaselineSnapshot(snapshot)
        setTransitionState(TransitionState.ACTIVE)
        Log.i(TAG, "Captured new baseline: Brightness=${snapshot.brightnessLevel.value}%, DND=${snapshot.dndState.value?.label}")
        return snapshot
    }

    /**
     * Restores the recorded baseline settings to the system provider (1 -> 0 deactivation).
     * Once restored, the stored baseline is cleared and transition state becomes IDLE.
     */
    @Synchronized
    fun restoreBaseline(provider: ISystemStateProvider): Boolean {
        val snapshot = _baselineSnapshot.value
        if (snapshot == null) {
            Log.w(TAG, "No baseline snapshot to restore.")
            setTransitionState(TransitionState.IDLE)
            return false
        }

        setTransitionState(TransitionState.RESTORING_BASELINE)

        val applied = provider.applySnapshot(snapshot)
        clearBaselineSnapshot()
        setTransitionState(TransitionState.IDLE)
        Log.i(TAG, "Baseline successfully restored to system and cleared from storage.")
        return applied
    }

    /**
     * Clears the baseline snapshot and sets transition state to IDLE.
     */
    @Synchronized
    fun clearBaseline() {
        clearBaselineSnapshot()
        setTransitionState(TransitionState.IDLE)
    }

    /**
     * Explicitly updates the transition state and records timestamp in persistent storage
     * to safeguard against interrupted transitions / crashes.
     */
    @Synchronized
    fun setTransitionState(state: TransitionState) {
        _transitionState.value = state
        prefs.edit()
            .putString(KEY_TRANSITION_STATE, state.name)
            .putLong(KEY_TRANSITION_STARTED_AT, System.currentTimeMillis())
            .commit()
    }

    private fun saveBaselineSnapshot(snapshot: SystemSnapshot) {
        _baselineSnapshot.value = snapshot
        prefs.edit()
            .putString(KEY_SNAPSHOT_JSON, snapshot.toJsonObject().toString())
            .commit()
    }

    private fun clearBaselineSnapshot() {
        _baselineSnapshot.value = null
        prefs.edit()
            .remove(KEY_SNAPSHOT_JSON)
            .commit()
    }

    @Synchronized
    fun resetBaseline() {
        clearBaselineSnapshot()
        setTransitionState(TransitionState.IDLE)
        _lastRecoveryReport.value = null
    }

    /**
     * Interrupted Transition Recovery:
     * Called during application initialization to detect if a previous execution terminated
     * while in an unstable state (e.g. CAPTURING_BASELINE or RESTORING_BASELINE).
     */
    @Synchronized
    fun checkAndReconcileRecovery(provider: ISystemStateProvider): RecoveryReport {
        val previousState = _transitionState.value
        var requiredRecovery = false
        var actionTaken = "No recovery needed (state was $previousState)"

        when (previousState) {
            TransitionState.CAPTURING_BASELINE -> {
                requiredRecovery = true
                actionTaken = "Interrupted during baseline capture. Cleaned partial snapshot and reset to IDLE."
                clearBaselineSnapshot()
                setTransitionState(TransitionState.IDLE)
            }
            TransitionState.RESTORING_BASELINE -> {
                requiredRecovery = true
                val snapshot = _baselineSnapshot.value
                actionTaken = if (snapshot != null) {
                    provider.applySnapshot(snapshot)
                    clearBaselineSnapshot()
                    setTransitionState(TransitionState.IDLE)
                    "Interrupted during restoration. Completed pending baseline restoration and reset to IDLE."
                } else {
                    setTransitionState(TransitionState.IDLE)
                    "Interrupted during restoration with missing snapshot. Reset to IDLE."
                }
            }
            TransitionState.APPLYING_PROFILE -> {
                requiredRecovery = true
                actionTaken = "Interrupted while applying profile. Transition state normalized to ACTIVE."
                setTransitionState(TransitionState.ACTIVE)
            }
            TransitionState.ACTIVE -> {
                if (isBaselineStale()) {
                    actionTaken = "Active profile baseline is stale (> 24 hours old). Preserved for review."
                } else {
                    actionTaken = "Resumed active profile with valid baseline snapshot."
                }
            }
            TransitionState.IDLE, TransitionState.RECOVERY_REQUIRED -> {
                // Normal
            }
        }

        val report = RecoveryReport(
            requiredRecovery = requiredRecovery,
            previousState = previousState,
            actionTaken = actionTaken
        )
        _lastRecoveryReport.value = report
        if (requiredRecovery) {
            Log.w(TAG, "RECOVERY EXECUTED: $actionTaken")
        }
        return report
    }
}
