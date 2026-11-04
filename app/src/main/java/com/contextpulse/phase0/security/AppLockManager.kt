package com.contextpulse.phase0.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockSessionPolicy(val key: String, val title: String, val description: String) {
    ONCE_PER_SESSION("session", "Once Per App Session (Recommended)", "Enter your PIN once; stays unlocked until the app is closed or reopened."),
    EVERY_ACTION("every_action", "Require On Every Action", "Strict security: prompts for your PIN every time you add, edit, or configure."),
    GRACE_PERIOD_5_MIN("grace_5min", "5-Minute Inactivity Window", "Stays unlocked for 5 minutes after last PIN entry.")
}

/**
 * Manages configuration locking with PIN / Biometrics.
 * When enabled, prevents Contexts and Triggers from being edited, created, or configured without PIN verification.
 */
class AppLockManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_lock_prefs"
        private const val KEY_LOCK_ENABLED = "key_lock_enabled"
        private const val KEY_PIN_HASH = "key_pin_hash"
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

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _sessionPolicy = MutableStateFlow(
        LockSessionPolicy.entries.find { it.key == prefs.getString(KEY_SESSION_POLICY, LockSessionPolicy.ONCE_PER_SESSION.key) }
            ?: LockSessionPolicy.ONCE_PER_SESSION
    )
    val sessionPolicy: StateFlow<LockSessionPolicy> = _sessionPolicy.asStateFlow()

    // In-memory session state (resets when process ends / app restarts)
    private var sessionUnlocked: Boolean = false
    private var lastUnlockedTimestamp: Long = 0L

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    fun isLockedForAction(): Boolean {
        if (!_isLockEnabled.value) return false
        if (!hasPin()) return false

        return when (_sessionPolicy.value) {
            LockSessionPolicy.EVERY_ACTION -> true
            LockSessionPolicy.ONCE_PER_SESSION -> !sessionUnlocked
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

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val matches = stored == hashPin(pin)
        if (matches) {
            markSessionUnlocked()
        }
        return matches
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .apply()
        _isLockEnabled.value = true
        markSessionUnlocked()
    }

    fun setLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        _isLockEnabled.value = enabled
        if (!enabled) {
            lockSessionNow()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _isBiometricEnabled.value = enabled
    }

    fun disableLock() {
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, false)
            .remove(KEY_PIN_HASH)
            .apply()
        _isLockEnabled.value = false
        lockSessionNow()
    }

    private fun hashPin(pin: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin.hashCode().toString()
        }
    }
}
