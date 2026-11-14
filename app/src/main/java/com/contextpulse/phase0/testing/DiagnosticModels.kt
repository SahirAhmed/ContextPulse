package com.contextpulse.phase0.testing

/**
 * Meaningful execution result statuses for ContextPulse Diagnostics & Testing Centre.
 */
enum class DiagnosticStatus {
    PASSED,
    FAILED,
    NEEDS_ATTENTION,
    MANUAL_VERIFICATION_REQUIRED,
    NOT_APPLICABLE,
    NOT_TESTED,
    DEVICE_TEST_REQUIRED,
    SKIPPED,
    RUNNING
}

enum class DiagnosticCategory {
    SYSTEM_DIAGNOSTICS,
    AUTOMATED_FUNCTIONAL,
    GUIDED_DEVICE
}

data class DiagnosticTestResult(
    val id: String,
    val name: String,
    val category: DiagnosticCategory,
    val subcategory: String,
    val status: DiagnosticStatus = DiagnosticStatus.NOT_TESTED,
    val explanation: String = "",
    val expectedResult: String = "",
    val actualResult: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val correctiveAction: String? = null,
    val fixActionLabel: String? = null,
    val onFix: (() -> Unit)? = null
)
