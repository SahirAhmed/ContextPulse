package com.contextpulse.phase0.testing

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log

enum class FlashTestType {
    BRIGHTNESS_FLASH,
    DND_FLASH
}

enum class RecoveryStatus {
    PENDING_RESTORATION,
    RESTORATION_COMPLETED,
    RESTORATION_CONFLICT,
    RESTORATION_FAILED
}

data class RecoveryRecord(
    val testId: String,
    val testType: FlashTestType,
    val originalBrightnessValue: Int,
    val originalBrightnessMode: Int,
    val originalDndFilter: Int,
    val temporaryValueApplied: Int,
    val timestamp: Long,
    val status: RecoveryStatus
)

/**
 * Synchronous Persistent Recovery Journal for Guided Flash Tests.
 *
 * Guarantees:
 * 1. Synchronously written via commit() BEFORE any hardware or system change is applied.
 * 2. If writing the journal fails, the test is immediately aborted.
 * 3. Never overwrites an existing PENDING_RESTORATION record.
 * 4. Preserves both SCREEN_BRIGHTNESS and SCREEN_BRIGHTNESS_MODE (automatic vs manual).
 * 5. Preserves exact NotificationManager interruption filters (ALL, PRIORITY, NONE, ALARMS).
 * 6. Conflict detection: if the current system setting no longer matches the temporary value
 *    applied by the test, it avoids overwriting the user's newer setting and flags RESTORATION_CONFLICT.
 * 7. Verified before clearing: checks system settings post-restoration before transitioning to RESTORATION_COMPLETED.
 * 8. Centralized recovery: checked at app startup (Application.onCreate), Activity onResume, and BootReceiver.
 */
object GuidedTestRecoveryJournal {

    private const val TAG = "GuidedRecoveryJournal"
    private const val PREFS_NAME = "contextpulse_guided_test_recovery"

    private const val KEY_TEST_ID = "recovery_test_id"
    private const val KEY_TEST_TYPE = "recovery_test_type"
    private const val KEY_ORIG_BRIGHTNESS_VAL = "recovery_orig_brightness_val"
    private const val KEY_ORIG_BRIGHTNESS_MODE = "recovery_orig_brightness_mode"
    private const val KEY_ORIG_DND_FILTER = "recovery_orig_dnd_filter"
    private const val KEY_TEMP_VALUE_APPLIED = "recovery_temp_val_applied"
    private const val KEY_TIMESTAMP = "recovery_timestamp"
    private const val KEY_STATUS = "recovery_status"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getPendingRecord(context: Context): RecoveryRecord? {
        val prefs = getPrefs(context)
        val statusStr = prefs.getString(KEY_STATUS, null) ?: return null
        val status = try { RecoveryStatus.valueOf(statusStr) } catch (_: Exception) { return null }
        if (status != RecoveryStatus.PENDING_RESTORATION && status != RecoveryStatus.RESTORATION_CONFLICT) {
            return null
        }

        val testType = try {
            FlashTestType.valueOf(prefs.getString(KEY_TEST_TYPE, "") ?: "")
        } catch (_: Exception) { return null }

        return RecoveryRecord(
            testId = prefs.getString(KEY_TEST_ID, "") ?: "",
            testType = testType,
            originalBrightnessValue = prefs.getInt(KEY_ORIG_BRIGHTNESS_VAL, 128),
            originalBrightnessMode = prefs.getInt(KEY_ORIG_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL),
            originalDndFilter = prefs.getInt(KEY_ORIG_DND_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL),
            temporaryValueApplied = prefs.getInt(KEY_TEMP_VALUE_APPLIED, 0),
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0L),
            status = status
        )
    }

    /**
     * Synchronously persists a recovery record.
     * Returns true only if write committed successfully and no previous recovery was pending.
     */
    @Synchronized
    fun recordTestIntent(
        context: Context,
        testId: String,
        testType: FlashTestType,
        origBrightnessVal: Int,
        origBrightnessMode: Int,
        origDndFilter: Int,
        tempValApplied: Int
    ): Boolean {
        val prefs = getPrefs(context)
        val existing = getPendingRecord(context)
        if (existing != null) {
            Log.e(TAG, "Cannot start new flash test: Previous recovery '${existing.testId}' is still pending!")
            return false
        }

        val editor = prefs.edit()
            .putString(KEY_TEST_ID, testId)
            .putString(KEY_TEST_TYPE, testType.name)
            .putInt(KEY_ORIG_BRIGHTNESS_VAL, origBrightnessVal)
            .putInt(KEY_ORIG_BRIGHTNESS_MODE, origBrightnessMode)
            .putInt(KEY_ORIG_DND_FILTER, origDndFilter)
            .putInt(KEY_TEMP_VALUE_APPLIED, tempValApplied)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_STATUS, RecoveryStatus.PENDING_RESTORATION.name)

        // Must commit synchronously before modifying device settings
        val success = editor.commit()
        if (!success) {
            Log.e(TAG, "FATAL: Failed to synchronously commit recovery journal record. Aborting test.")
        }
        return success
    }

    /**
     * Executes restoration with conflict detection and verification.
     */
    @Synchronized
    fun attemptRestoration(context: Context, record: RecoveryRecord): Pair<Boolean, String> {
        val prefs = getPrefs(context)

        return when (record.testType) {
            FlashTestType.BRIGHTNESS_FLASH -> {
                if (!Settings.System.canWrite(context)) {
                    val msg = "Cannot restore brightness: WRITE_SETTINGS permission missing."
                    Log.w(TAG, msg)
                    prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_FAILED.name).commit()
                    Pair(false, msg)
                } else {
                    val currentVal = try {
                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    } catch (_: Exception) { -1 }

                    // Conflict check: Has the user adjusted brightness since the temporary test value was set?
                    if (currentVal != -1 && currentVal != record.temporaryValueApplied) {
                        val msg = "Brightness conflict: Current ($currentVal) does not match test temp (${record.temporaryValueApplied}). Retaining user setting."
                        Log.w(TAG, msg)
                        prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_CONFLICT.name).commit()
                        Pair(false, msg)
                    } else {
                        // Apply restoration
                        try {
                            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, record.originalBrightnessValue)
                            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, record.originalBrightnessMode)

                            // Verification check
                            val restoredVal = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                            val restoredMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)

                            if (restoredVal == record.originalBrightnessValue && restoredMode == record.originalBrightnessMode) {
                                clearRecord(context)
                                Log.i(TAG, "Verified brightness restoration: value=${record.originalBrightnessValue}, mode=${record.originalBrightnessMode}")
                                Pair(true, "Restored brightness ($restoredVal) and mode ($restoredMode) successfully.")
                            } else {
                                val msg = "Restoration verification failed (Val: $restoredVal, Mode: $restoredMode)."
                                prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_FAILED.name).commit()
                                Pair(false, msg)
                            }
                        } catch (e: Exception) {
                            val msg = "Error restoring brightness: ${e.message}"
                            prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_FAILED.name).commit()
                            Pair(false, msg)
                        }
                    }
                }
            }

            FlashTestType.DND_FLASH -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm == null || !nm.isNotificationPolicyAccessGranted) {
                    val msg = "Cannot restore DND: Notification Policy Access missing."
                    Log.w(TAG, msg)
                    prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_FAILED.name).commit()
                    Pair(false, msg)
                } else {
                    val currentFilter = nm.currentInterruptionFilter

                    // Conflict check: If current is neither the test's temp filter (PRIORITY) nor the original filter,
                    // the user likely adjusted DND manually. Avoid overwriting.
                    if (currentFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY && currentFilter != record.originalDndFilter) {
                        val msg = "DND conflict: Interruption filter ($currentFilter) modified by user. Preserving user change."
                        Log.w(TAG, msg)
                        prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_CONFLICT.name).commit()
                        Pair(false, msg)
                    } else {
                        try {
                            nm.setInterruptionFilter(record.originalDndFilter)
                            val verifiedFilter = nm.currentInterruptionFilter

                            if (verifiedFilter == record.originalDndFilter) {
                                clearRecord(context)
                                Log.i(TAG, "Verified DND restoration to filter ${record.originalDndFilter}")
                                Pair(true, "Restored DND interruption filter ($verifiedFilter) successfully.")
                            } else {
                                val msg = "DND restoration verification failed: Current=$verifiedFilter, Expected=${record.originalDndFilter}"
                                prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_FAILED.name).commit()
                                Pair(false, msg)
                            }
                        } catch (e: Exception) {
                            val msg = "Error restoring DND filter: ${e.message}"
                            prefs.edit().putString(KEY_STATUS, RecoveryStatus.RESTORATION_FAILED.name).commit()
                            Pair(false, msg)
                        }
                    }
                }
            }
        }
    }

    /**
     * Inspects for pending records and attempts restoration if found.
     * Safe to invoke on app startup, Activity onResume, and device boot.
     */
    @Synchronized
    fun checkAndAttemptRecovery(context: Context): Pair<Boolean, String?> {
        val pending = getPendingRecord(context) ?: return Pair(true, null)
        Log.i(TAG, "Pending recovery detected for testId=${pending.testId}, type=${pending.testType}. Attempting restoration...")
        return attemptRestoration(context, pending)
    }

    @Synchronized
    fun clearRecord(context: Context) {
        getPrefs(context).edit().clear().commit()
    }
}
