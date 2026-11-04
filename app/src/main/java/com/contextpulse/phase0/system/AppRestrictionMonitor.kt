package com.contextpulse.phase0.system

import android.content.Context
import android.util.Log
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

    fun setRestrictedPackages(packages: Set<String>, roomName: String? = null) {
        targetRestrictedPackages = packages
        currentActiveRoomName = roomName
        // Sync immediately with Accessibility Service
        AppInterceptorAccessibilityService.targetBlockedPackages = packages
        AppInterceptorAccessibilityService.activeRoomName = roomName
        Log.d(TAG, "Configured ${packages.size} restricted packages for room '$roomName'")
    }

    fun getRestrictedPackages(): Set<String> = targetRestrictedPackages
    fun getCurrentActiveRoomName(): String? = currentActiveRoomName

    /**
     * Starts continuous foreground app monitoring.
     * Uses a fast, responsive polling loop (default 400ms) with dual UsageStats
     * and ActivityManager process detection.
     */
    fun startContinuousMonitoring(context: Context, intervalMs: Long = 400L) {
        if (_isMonitoring.value) return

        _isMonitoring.value = true
        monitorJob = monitorScope.launch {
            Log.d(TAG, "Started App Restriction monitoring loop (interval=${intervalMs}ms)")

            var lastHandledApp: String? = null
            var lastHandledTimestamp = 0L

            while (isActive) {
                try {
                    val (currentApp, latency) = SystemControllers.getForegroundAppWithLatency(context)
                    if (currentApp != null && currentApp != context.packageName) {
                        _lastDetectedApp.value = currentApp
                        _lastLatencyMs.value = latency

                        val isRestricted = isPackageRestricted(context, currentApp)

                        val now = System.currentTimeMillis()
                        // Intercept if restricted and user is trying to access it (throttle 2.5s)
                        if (isRestricted && (currentApp != lastHandledApp || (now - lastHandledTimestamp) > 2500L)) {
                            lastHandledApp = currentApp
                            lastHandledTimestamp = now

                            Log.w(TAG, "INTERCEPTING RESTRICTED APP: $currentApp (Room: $currentActiveRoomName)")
                            val actionTaken = try {
                                SystemControllers.showRestrictionOverlay(context, currentApp, currentActiveRoomName)
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
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in restriction polling loop", e)
                }

                delay(intervalMs)
            }
        }
    }

    fun isPackageRestricted(context: Context, packageName: String): Boolean {
        if (targetRestrictedPackages.isEmpty()) return false
        if (packageName == context.packageName) return false // Never block ContextPulse

        if (targetRestrictedPackages.contains(packageName)) return true

        val lowerPkg = packageName.lowercase()
        for (item in targetRestrictedPackages) {
            val lowerItem = item.lowercase()
            if (lowerPkg == lowerItem || lowerPkg.contains(lowerItem) || lowerItem.contains(lowerPkg)) {
                return true
            }
        }

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(appInfo).toString().lowercase()
            for (item in targetRestrictedPackages) {
                val lowerItem = item.lowercase()
                if (appLabel == lowerItem || appLabel.contains(lowerItem) || lowerItem.contains(appLabel)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore package lookup errors
        }
        return false
    }

    fun stopContinuousMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _isMonitoring.value = false
        Log.d(TAG, "Stopped App Restriction monitoring")
    }
}
