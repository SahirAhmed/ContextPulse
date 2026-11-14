package com.contextpulse.phase0.testing

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware Test Runner coordinating execution across:
 * 1. System Diagnostics
 * 2. Automated Functional Tests
 * 3. Guided Physical Device Tests
 *
 * Guarantees safe cancellation and complete cleanup after every test run.
 */
class DiagnosticsCoordinator private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DiagnosticsCoordinator? = null

        fun getInstance(context: Context): DiagnosticsCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiagnosticsCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var runnerJob: Job? = null

    private val _systemDiagnostics = MutableStateFlow<List<DiagnosticTestResult>>(emptyList())
    val systemDiagnostics: StateFlow<List<DiagnosticTestResult>> = _systemDiagnostics.asStateFlow()

    private val _functionalTests = MutableStateFlow<List<DiagnosticTestResult>>(emptyList())
    val functionalTests: StateFlow<List<DiagnosticTestResult>> = _functionalTests.asStateFlow()

    private val _guidedDeviceTests = MutableStateFlow<List<DiagnosticTestResult>>(emptyList())
    val guidedDeviceTests: StateFlow<List<DiagnosticTestResult>> = _guidedDeviceTests.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentRunningTestName = MutableStateFlow<String?>(null)
    val currentRunningTestName: StateFlow<String?> = _currentRunningTestName.asStateFlow()

    init {
        // Initialize default registries
        _systemDiagnostics.value = SystemDiagnosticsRunner.runAllDiagnostics(appContext)
        _guidedDeviceTests.value = GuidedDeviceTestsRegistry.getGuidedDeviceTests(appContext)
        // Functional tests initially in NOT_TESTED state
        _functionalTests.value = prepareInitialFunctionalPlaceholders()
    }

    private fun prepareInitialFunctionalPlaceholders(): List<DiagnosticTestResult> {
        val tests = listOf(
            "App Restriction Exact Canonical Matching",
            "YouTube vs Google Search Regression Test",
            "Context Lifecycle & CRUD Verification",
            "Single Active Context Invariant Enforcement",
            "Recreated Context Clean-Slate Invariant",
            "Timeframe Mode Schedule Resolution",
            "AlarmManager Boundary Calculation Logic",
            "Preview Mode Lifecycle & Schedule Recovery",
            "Snooze State Machine & Alarm Cancellation",
            "Baseline State Snapshot Capture & Invariant",
            "Context Switch (1➔2) Baseline Preservation",
            "Intelligent TransitionDiffEngine Calculation",
            "App Usage Budget Active Resolution",
            "Multi-Beacon per Context Registry Support",
            "Eddystone-UID & Generic Beacon Matching",
            "RSSI Smoothing & 4dBm Hysteresis Arbitration",
            "QR URL & Deep-Link Trigger Key Extraction",
            "NFC Tag Payload Matching & Identification",
            "Sticky Note Checklist State & Completion Logic",
            "Automatic App-Launch Decision Filtering",
            "Preview Recovery Boundary-Crossing Verification",
            "SenseCAP BC03 iBeacon Identity & Normalization",
            "Stable Context ID Binding & Unassigned Safety",
            "Mode Boundary & Restriction Carryover Prevention",
            "Single Trigger Type per Context Enforcement",
            "NFC GitHub App Link Generation & Lookup",
            "Eddystone-TLM Battery & <15% Alert Threshold"
        )
        return tests.mapIndexed { index, name ->
            DiagnosticTestResult(
                id = "func_item_$index",
                name = name,
                category = DiagnosticCategory.AUTOMATED_FUNCTIONAL,
                subcategory = "Automated Engine",
                status = DiagnosticStatus.NOT_TESTED,
                explanation = "Awaiting execution...",
                expectedResult = "Assertion verified."
            )
        }
    }

    private fun getFunctionalTestLambdas(): List<() -> DiagnosticTestResult> {
        return listOf(
            { FunctionalTestsRunner.testExactPackageMatching(appContext) },
            { FunctionalTestsRunner.testYouTubeVsGoogleSearchRegression(appContext) },
            { FunctionalTestsRunner.testContextCreationUpdateDeleteIsolation(appContext) },
            { FunctionalTestsRunner.testContextSwitchingArbitrationSingleActive(appContext) },
            { FunctionalTestsRunner.testRecreatingContextCleanSlate(appContext) },
            { FunctionalTestsRunner.testScheduledModeResolution(appContext) },
            { FunctionalTestsRunner.testScheduleBoundaryAlarmCalculation(appContext) },
            { FunctionalTestsRunner.testPreviewModeScheduleBoundaryRecovery(appContext) },
            { FunctionalTestsRunner.testSnoozeAutomationPauseAndResume(appContext) },
            { FunctionalTestsRunner.testBaselineCaptureAndPersistence(appContext) },
            { FunctionalTestsRunner.testContextSwitchPreservesOriginalBaseline(appContext) },
            { FunctionalTestsRunner.testTransitionDiffEngineDelta(appContext) },
            { FunctionalTestsRunner.testUsageBudgetExhaustion(appContext) },
            { FunctionalTestsRunner.testBeaconRegistryMultiBeaconPerContext(appContext) },
            { FunctionalTestsRunner.testBeaconRegistryEddystoneAndGenericMatching(appContext) },
            { FunctionalTestsRunner.testRssiFilterHysteresisAndArbitration(appContext) },
            { FunctionalTestsRunner.testQrPayloadParsingAndDirectActivation(appContext) },
            { FunctionalTestsRunner.testNfcPayloadMatchingAndIdentification(appContext) },
            { FunctionalTestsRunner.testStickyNoteChecklistLogic(appContext) },
            { FunctionalTestsRunner.testAutoLaunchDecisionFiltering(appContext) },
            { FunctionalTestsRunner.testPreviewBoundaryCrossingRecovery(appContext) },
            { FunctionalTestsRunner.testSenseCapIBeaconIdentityAndParsing(appContext) },
            { FunctionalTestsRunner.testStableContextIdRenamingAndUnassignedSafety(appContext) },
            { FunctionalTestsRunner.testModeBoundaryNoRestrictionCarryover(appContext) },
            { FunctionalTestsRunner.testHomogeneousTriggerTypePerContext(appContext) },
            { FunctionalTestsRunner.testNfcAppLinkGenerationAndResolution(appContext) },
            { FunctionalTestsRunner.testEddystoneTlmBatteryAndLowBatteryThreshold(appContext) }
        )
    }

    fun runAllTests(category: DiagnosticCategory? = null) {
        if (_isRunning.value) return
        _isRunning.value = true

        runnerJob = scope.launch {
            try {
                if (category == null || category == DiagnosticCategory.SYSTEM_DIAGNOSTICS) {
                    _currentRunningTestName.value = "Running System Diagnostics..."
                    _systemDiagnostics.value = SystemDiagnosticsRunner.runAllDiagnostics(appContext)
                }

                if (category == null || category == DiagnosticCategory.AUTOMATED_FUNCTIONAL) {
                    val currentList = _functionalTests.value.toMutableList()
                    val tests = getFunctionalTestLambdas()

                    for (i in tests.indices) {
                        val placeholder = currentList.getOrNull(i)
                        _currentRunningTestName.value = placeholder?.name ?: "Executing test ${i + 1}"
                        if (placeholder != null) {
                            currentList[i] = placeholder.copy(status = DiagnosticStatus.RUNNING)
                            _functionalTests.value = currentList.toList()
                        }
                        // Execute authentic test logic
                        val result = tests[i].invoke()
                        currentList[i] = result
                        _functionalTests.value = currentList.toList()
                    }
                }

                // Refresh guided device tests
                _guidedDeviceTests.value = GuidedDeviceTestsRegistry.getGuidedDeviceTests(appContext)

            } finally {
                _isRunning.value = false
                _currentRunningTestName.value = null
            }
        }
    }

    fun runSingleFunctionalTest(index: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        runnerJob = scope.launch {
            try {
                val tests = getFunctionalTestLambdas()

                if (index in tests.indices) {
                    val currentList = _functionalTests.value.toMutableList()
                    val target = currentList[index]
                    _currentRunningTestName.value = target.name
                    currentList[index] = target.copy(status = DiagnosticStatus.RUNNING)
                    _functionalTests.value = currentList.toList()

                    val result = tests[index].invoke()
                    currentList[index] = result
                    _functionalTests.value = currentList.toList()
                }
            } finally {
                _isRunning.value = false
                _currentRunningTestName.value = null
            }
        }
    }

    fun retestFailed() {
        if (_isRunning.value) return
        _isRunning.value = true

        runnerJob = scope.launch {
            try {
                // Refresh system diagnostics
                _systemDiagnostics.value = SystemDiagnosticsRunner.runAllDiagnostics(appContext)

                // Rerun only failed functional tests
                val current = _functionalTests.value.toMutableList()
                val tests = getFunctionalTestLambdas()

                for (i in current.indices) {
                    if (current[i].status == DiagnosticStatus.FAILED || current[i].status == DiagnosticStatus.NOT_TESTED) {
                        _currentRunningTestName.value = current[i].name
                        val updated = tests[i].invoke()
                        current[i] = updated
                        _functionalTests.value = current.toList()
                    }
                }
            } finally {
                _isRunning.value = false
                _currentRunningTestName.value = null
            }
        }
    }

    fun updateGuidedDeviceTest(id: String, status: DiagnosticStatus, actualResult: String) {
        _guidedDeviceTests.value = _guidedDeviceTests.value.map { item ->
            if (item.id == id) {
                item.copy(status = status, actualResult = actualResult)
            } else {
                item
            }
        }
    }

    fun refreshGuidedDeviceTests() {
        _guidedDeviceTests.value = GuidedDeviceTestsRegistry.getGuidedDeviceTests(appContext)
    }

    fun cancelRunningTests() {
        runnerJob?.cancel()
        runnerJob = null
        _isRunning.value = false
        _currentRunningTestName.value = null
    }
}
