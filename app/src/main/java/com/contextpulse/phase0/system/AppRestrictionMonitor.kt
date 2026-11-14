package com.contextpulse.phase0.system

import android.content.Context
import android.util.Log
import com.contextpulse.phase0.repository.RoomProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class InterceptionLogEntry(
    val timestampMs: Long,
    val packageName: String,
    val isRestricted: Boolean,
    val latencyMs: Long,
    val actionTaken: String,
    val success: Boolean
)

object AppRestrictionMonitor {

    private const val TAG = "AppRestrictionMonitor"

    private var monitorJob: Job? = null
    private val monitorScope = CoroutineScope(Dispatchers.Default)

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _lastDetectedApp = MutableStateFlow<String?>(null)
    val lastDetectedApp: StateFlow<String?> = _lastDetectedApp.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _interceptionHistory = MutableStateFlow<List<InterceptionLogEntry>>(emptyList())
    val interceptionHistory: StateFlow<List<InterceptionLogEntry>> = _interceptionHistory.asStateFlow()

    private var targetRestrictedPackages = setOf<String>()
    private var currentActiveRoomName: String? = null
    private var currentActiveModeId: String? = null
    @Volatile
    private var lastScheduleCheckMs: Long = 0L

    fun setRestrictedPackages(packages: Set<String>, roomName: String? = null, modeId: String? = null) {
        targetRestrictedPackages = packages
        currentActiveRoomName = roomName
        if (packages.isEmpty() && roomName == null) {
            currentActiveModeId = null
        } else if (modeId != null) {
            currentActiveModeId = modeId
        }
        // Sync immediately with Accessibility Service
        AppInterceptorAccessibilityService.targetBlockedPackages = packages
        AppInterceptorAccessibilityService.activeRoomName = roomName
        Log.d(TAG, "Configured ${packages.size} restricted packages for room '$roomName' (mode=$currentActiveModeId)")
    }

    /**
     * Guards against delayed AlarmManager wakeups on OEM ROMs (MIUI/HyperOS):
     * if the live schedule has rolled past the mode that configured current restrictions,
     * immediately reapplies the active context/mode configuration.
     */
    fun verifyCurrentScheduleStateIfNeeded(context: Context) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastScheduleCheckMs < 1000L) return
        lastScheduleCheckMs = nowMs

        if (ActiveConfigSnapshotManager.isPreviewActive(context)) return

        val hasAnyRestriction = targetRestrictedPackages.isNotEmpty() || AppUsageBudgetManager.getActiveBudgets().isNotEmpty()
        if (!hasAnyRestriction && currentActiveModeId == null) return

        val snoozeActive = try {
            SnoozeManager.getInstance(context).isSnoozeActive()
        } catch (_: Exception) {
            false
        }
        if (snoozeActive) {
            Log.i(TAG, "Snooze active during restriction check; clearing active restrictions.")
            ProfileActivationHelper.reapplyCurrentActive(context)
            return
        }

        val activeRoom = RoomProfileRepository.getInstance(context).activeRoom.value
        if (activeRoom == null) {
            Log.i(TAG, "No active context remaining; clearing stale app restrictions.")
            ProfileActivationHelper.reapplyCurrentActive(context)
            return
        }

        val resolvedMode = activeRoom.profile.resolveActiveMode()
        val modeRollover = currentActiveModeId != null && resolvedMode.id != currentActiveModeId
        val disabledInCurrentMode = !resolvedMode.appRestrictionsEnabled && hasAnyRestriction
        if (modeRollover || disabledInCurrentMode) {
            Log.i(TAG, "Mode boundary / restriction change detected ($currentActiveModeId -> ${resolvedMode.id}). Reapplying.")
            ProfileActivationHelper.reapplyCurrentActive(context)
        }
    }

    fun getRestrictedPackages(): Set<String> = targetRestrictedPackages
    fun getCurrentActiveRoomName(): String? = currentActiveRoomName

    /**
     * Starts continuous foreground app monitoring.
     * Uses a fast, responsive polling loop (default 400ms) with dual UsageStats
     * and ActivityManager process detection.
     */
    fun startContinuousMonitoring(context: Context, intervalMs: Long = 200L) {
        if (_isMonitoring.value) return

        _isMonitoring.value = true
        monitorJob = monitorScope.launch {
            Log.d(TAG, "Started App Restriction monitoring loop (interval=${intervalMs}ms)")

            var lastHandledApp: String? = null
            var lastHandledTimestamp = 0L

            while (isActive) {
                try {
                    verifyCurrentScheduleStateIfNeeded(context)
                    if (!isActive) break

                    val (currentApp, latency) = SystemControllers.getForegroundAppWithLatency(context)
                    if (currentApp != null && currentApp != context.packageName) {
                        _lastDetectedApp.value = currentApp
                        _lastLatencyMs.value = latency

                        // Feed into App Usage Budget tracker
                        AppUsageBudgetManager.onForegroundAppHeartbeat(context, currentApp)

                        val isRestricted = isPackageRestricted(context, currentApp)

                        val now = System.currentTimeMillis()
                        // Intercept if restricted and user is trying to access it (throttle 1.2s for repeat app)
                        if (isRestricted && (currentApp != lastHandledApp || (now - lastHandledTimestamp) > 1200L)) {
                            lastHandledApp = currentApp
                            lastHandledTimestamp = now

                            val hasBudget = AppUsageBudgetManager.getActiveBudgetForPackage(currentApp) != null
                            val isBudgetExhausted = hasBudget && AppUsageBudgetManager.getRemainingSecondsForPackage(context, currentApp) <= 0
                            val blockReason = if (isBudgetExhausted) OverlayBlockerActivity.REASON_BUDGET_EXHAUSTED else OverlayBlockerActivity.REASON_BLOCK_LIST

                            Log.w(TAG, "INTERCEPTING RESTRICTED APP: $currentApp (Room: $currentActiveRoomName, reason=$blockReason)")
                            val actionTaken = try {
                                SystemControllers.showRestrictionOverlay(context, currentApp, currentActiveRoomName, blockReason)
                                "Launched Blocker Overlay"
                            } catch (e: Exception) {
                                SystemControllers.kickToHomeScreen(context)
                                "Overlay error -> Kicked to Home: ${e.message}"
                            }

                            val entry = InterceptionLogEntry(
                                timestampMs = now,
                                packageName = currentApp,
                                isRestricted = true,
                                latencyMs = latency,
                                actionTaken = actionTaken,
                                success = true
                            )
                            val list = _interceptionHistory.value.takeLast(40).toMutableList()
                            list.add(entry)
                            _interceptionHistory.value = list
                        }

                        // Update floating timer overlay dynamically for the current foreground app
                        FloatingTimerOverlayManager.updateTimerState(context, currentApp)
                    } else {
                        // User is in ContextPulse or on Launcher -> evaluate floating timer (e.g. sleep timer)
                        FloatingTimerOverlayManager.updateTimerState(context, currentApp)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in restriction polling loop", e)
                }

                delay(intervalMs)
            }
        }
    }

    /**
     * Pure matching function for testing and isolated verification.
     * Evaluates exact package matching without mutating or reading singleton state.
     */
    fun isPackageRestrictedPure(
        packageName: String,
        restrictedPackages: Set<String>,
        selfPackageName: String = "com.contextpulse.phase0"
    ): Boolean {
        if (packageName == selfPackageName) return false
        val normalizedCurrent = packageName.substringBefore('/').trim().lowercase()
        if (restrictedPackages.isEmpty()) return false

        for (item in restrictedPackages) {
            val normalizedItem = item.substringBefore('/').trim().lowercase()
            if (normalizedCurrent == normalizedItem) {
                return true
            }
        }
        return false
    }

    fun isPackageRestricted(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false // Never block ContextPulse

        // If app has an active budget that has run out of time (0s left), strictly block it
        val budget = AppUsageBudgetManager.getActiveBudgetForPackage(packageName)
        if (budget != null) {
            val remainingSec = AppUsageBudgetManager.getRemainingSecondsForPackage(context, packageName)
            if (remainingSec <= 0) {
                return true
            }
        }

        return isPackageRestrictedPure(
            packageName = packageName,
            restrictedPackages = targetRestrictedPackages,
            selfPackageName = context.packageName
        )
    }

    fun stopContinuousMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _isMonitoring.value = false
        Log.d(TAG, "Stopped App Restriction monitoring")
    }
}
