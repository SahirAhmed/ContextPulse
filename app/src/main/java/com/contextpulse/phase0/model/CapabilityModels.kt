package com.contextpulse.phase0.model

enum class CapabilityTestId {
    BLE_SCANNING,
    BLE_BEACON_ID,
    BLE_BACKGROUND_SCAN,
    BLE_SCREEN_LOCKED,
    FOREGROUND_APP_DETECTION,
    APP_RESTRICTION_INTERCEPTION,
    SCREEN_BRIGHTNESS,
    DO_NOT_DISTURB_ZENRULE,
    NOTIFICATION_CONTROL,
    LAUNCH_EXTERNAL_APP,
    BLE_SIGNAL_DISAPPEARANCE,
    MULTI_BEACON_ARBITRATION,
    BACKGROUND_RELIABILITY_OEM,
    STATE_RESTORATION
}

enum class TestExecutionType(val label: String) {
    NATIVE_SYSTEM_API("Real Native Android API"),
    SPECIAL_PERMISSION_REQUIRED("Requires Special Permission"),
    PHYSICAL_HARDWARE_REQUIRED("Requires Physical BLE Beacons"),
    ALGORITHMIC_DEVICE("On-Device Algorithmic Logic"),
    UNRESOLVED_LIMITATION("Unresolved Android Policy Limitation")
}

sealed class TestResultState {
    object NotTested : TestResultState()
    data class Running(val message: String = "Executing test...") : TestResultState()
    data class Passed(val details: String) : TestResultState()
    data class Failed(val error: String) : TestResultState()
    data class MissingPermission(val permissionName: String) : TestResultState()
    data class AwaitingHardware(val message: String = "Requires physical BLE beacon in range") : TestResultState()
    data class FeasibilityLimitation(val explanation: String) : TestResultState()
}

data class CapabilityTestState(
    val id: CapabilityTestId,
    val number: Int,
    val title: String,
    val description: String,
    val executionType: TestExecutionType,
    val requiredPermissions: List<String>,
    val minApiLevel: Int,
    val playStorePolicyStatus: String,
    val oemHyperOsNotes: String,
    val result: TestResultState = TestResultState.NotTested,
    val logs: List<String> = emptyList()
)

enum class BeaconType {
    IBEACON,
    ALTBEACON,
    EDDYSTONE_UID,
    EDDYSTONE_URL,
    GENERIC_MANUFACTURER
}

data class BleBeaconInfo(
    val macAddress: String,
    val name: String?,
    val rawRssi: Int,
    val smoothedRssi: Double,
    val beaconType: BeaconType,
    val uuid: String? = null,
    val major: Int? = null,
    val minor: Int? = null,
    val txPower: Int? = null,
    val estimatedDistanceMeters: Double = 0.0,
    val lastSeenMonotonicMs: Long = android.os.SystemClock.elapsedRealtime(),
    val isRegisteredRoomBeacon: Boolean = false,
    val assignedRoomName: String? = null
)

data class RoomBeaconDefinition(
    val roomId: String,
    val roomName: String,
    val macAddress: String,
    val uuid: String? = null,
    val major: Int? = null,
    val minor: Int? = null
)

data class SystemStateSnapshot(
    val brightnessMode: Int, // 0 = Manual, 1 = Auto
    val brightnessValue: Int, // 0..255
    val ringerMode: Int, // RINGER_MODE_NORMAL, SILENT, VIBRATE
    val dndFilter: Int, // INTERRUPTION_FILTER_ALL, PRIORITY, NONE, ALARMS
    val timestamp: Long = System.currentTimeMillis()
)

data class ZenRuleOpResult(
    val success: Boolean,
    val ruleId: String?,
    val isActive: Boolean,
    val message: String
)
