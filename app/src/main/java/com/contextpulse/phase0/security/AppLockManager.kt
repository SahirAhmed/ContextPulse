package com.contextpulse.phase0.security

import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager
import android.os.Build
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import kotlin.random.Random

enum class LockSessionPolicy(val key: String, val title: String, val description: String) {
    EVERY_ACTION("every_action", "Require On Every Action", "Strict security: prompts for your PIN or fingerprint every time you edit contexts or triggers."),
    GRACE_PERIOD_5_MIN("grace_5min", "5-Minute Inactivity Window", "Stays unlocked for 5 minutes after last verification.")
}

/**
 * Manages configuration and app security locking with PIN, Fingerprint/Biometrics, and 16-Digit Recovery Codes.
 * When enabled:
 * - Requires authentication on initial app launch.
 * - Protects Contexts, Modes, Triggers, and Snooze actions.
 * - Offers Fingerprint authentication with 6-digit PIN as the mandatory backup.
 * - Provides 16-digit one-time recovery code to disable lock if PIN is forgotten.
 */
class AppLockManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_lock_prefs"
        private const val KEY_LOCK_ENABLED = "key_lock_enabled"
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_RECOVERY_HASH = "key_recovery_hash"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_SESSION_POLICY = "key_session_policy"

        @Volatile
        private var INSTANCE: AppLockManager? = null

        fun getInstance(context: Context): AppLockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppLockManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK_ENABLED, false))
    val isLockEnabled: StateFlow<Boolean> = _isLockEnabled.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _sessionPolicy = MutableStateFlow(
        LockSessionPolicy.entries.find { it.key == prefs.getString(KEY_SESSION_POLICY, LockSessionPolicy.GRACE_PERIOD_5_MIN.key) }
            ?: LockSessionPolicy.GRACE_PERIOD_5_MIN
    )
    val sessionPolicy: StateFlow<LockSessionPolicy> = _sessionPolicy.asStateFlow()

    // App entrance lock state (requires unlocking upon opening app)
    private var appAccessUnlocked: Boolean = false
    private val _isAppAccessUnlocked = MutableStateFlow(false)
    val isAppAccessUnlockedFlow: StateFlow<Boolean> = _isAppAccessUnlocked.asStateFlow()

    // In-memory session state for action modifications
    private var sessionUnlocked: Boolean = false
    private var lastUnlockedTimestamp: Long = 0L

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    fun isAppAccessUnlocked(): Boolean {
        if (!_isLockEnabled.value || !hasPin()) return true
        return appAccessUnlocked
    }

    fun markAppAccessUnlocked() {
        appAccessUnlocked = true
        _isAppAccessUnlocked.value = true
        markSessionUnlocked()
    }

    fun lockAppAccess() {
        appAccessUnlocked = false
        _isAppAccessUnlocked.value = false
        lockSessionNow()
    }

    fun isLockedForAction(): Boolean {
        if (!_isLockEnabled.value) return false
        if (!hasPin()) return false

        return when (_sessionPolicy.value) {
            LockSessionPolicy.EVERY_ACTION -> true
            LockSessionPolicy.GRACE_PERIOD_5_MIN -> {
                if (!sessionUnlocked) true
                else {
                    val elapsed = System.currentTimeMillis() - lastUnlockedTimestamp
                    val isExpired = elapsed > 5 * 60 * 1000
                    if (isExpired) {
                        sessionUnlocked = false
                        _isSessionUnlocked.value = false
                    }
                    isExpired
                }
            }
        }
    }

    fun markSessionUnlocked() {
        sessionUnlocked = true
        lastUnlockedTimestamp = System.currentTimeMillis()
        _isSessionUnlocked.value = true
    }

    fun lockSessionNow() {
        sessionUnlocked = false
        lastUnlockedTimestamp = 0L
        _isSessionUnlocked.value = false
    }

    fun setSessionPolicy(policy: LockSessionPolicy) {
        prefs.edit().putString(KEY_SESSION_POLICY, policy.key).apply()
        _sessionPolicy.value = policy
    }

    fun isValidPin(pin: String): Boolean {
        return pin.length == 6 && pin.all { it.isDigit() }
    }

    fun hasPin(): Boolean {
        return prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun hasRecoveryCode(): Boolean {
        return prefs.getString(KEY_RECOVERY_HASH, null) != null
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val matches = stored == hashString(pin)
        if (matches) {
            markAppAccessUnlocked()
            markSessionUnlocked()
        }
        return matches
    }

    /**
     * Generates a 16-digit recovery code grouped with hyphens (e.g. '4920-8192-3810-7412').
     */
    fun generateRecoveryCode(): String {
        val digits = StringBuilder()
        repeat(16) {
            digits.append(Random.nextInt(0, 10))
        }
        return digits.toString()
    }

    /**
     * Helper to set PIN and return the generated 16-digit recovery code.
     */
    fun setupPinAndGenerateRecoveryCode(pin: String, biometricEnabled: Boolean = false): String {
        val code = generateRecoveryCode()
        setPinAndRecoveryCode(pin, code, biometricEnabled)
        return code
    }

    /**
     * Saves PIN, recovery code hash, and enables app lock.
     */
    fun setPinAndRecoveryCode(pin: String, recoveryCode: String, biometricEnabled: Boolean) {
        val cleanRecovery = recoveryCode.replace("-", "").trim()
        prefs.edit()
            .putString(KEY_PIN_HASH, hashString(pin))
            .putString(KEY_RECOVERY_HASH, hashString(cleanRecovery))
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putBoolean(KEY_BIOMETRIC_ENABLED, biometricEnabled)
            .apply()

        _isLockEnabled.value = true
        _isBiometricEnabled.value = biometricEnabled
        // Once lock has been enabled, leave the session locked straightaway by default
        appAccessUnlocked = true
        _isAppAccessUnlocked.value = true
        lockSessionNow()
    }

    /**
     * Verifies the 16-digit recovery code.
     * If valid, completely disables App Lock (sets it to toggled OFF) and unlocks access.
     */
    fun verifyRecoveryCodeAndDisableLock(enteredCode: String): Boolean {
        val cleanEntered = enteredCode.replace("-", "").replace(" ", "").trim()
        val storedHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return false
        val matches = storedHash == hashString(cleanEntered)
        if (matches) {
            disableLock()
            markAppAccessUnlocked()
            return true
        }
        return false
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _isBiometricEnabled.value = enabled
    }

    fun isBiometricHardwareAvailable(): Boolean {
        return BiometricAuthHelper.canAuthenticate(context)
    }

    fun hasEnrolledBiometrics(): Boolean {
        return BiometricAuthHelper.hasEnrolledBiometrics(context)
    }

    fun isBiometricAvailable(): Boolean = isBiometricHardwareAvailable()

    fun generateNewRecoveryCode(): String = generateRecoveryCode()

    fun setPinWithRecovery(pin: String, recoveryCode: String) {
        setPinAndRecoveryCode(pin, recoveryCode, _isBiometricEnabled.value)
    }

    fun disableLock() {
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, false)
            .remove(KEY_PIN_HASH)
            .remove(KEY_RECOVERY_HASH)
            .apply()
        _isLockEnabled.value = false
        appAccessUnlocked = true
        _isAppAccessUnlocked.value = true
        lockSessionNow()
    }

    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}
