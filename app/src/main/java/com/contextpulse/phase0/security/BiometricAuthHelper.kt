package com.contextpulse.phase0.security

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.hardware.fingerprint.FingerprintManagerCompat

/**
 * Helper to launch fingerprint / biometric authentication dialogs.
 */
object BiometricAuthHelper {

    private const val TAG = "BiometricAuthHelper"

    fun canAuthenticate(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val biometricManager = context.getSystemService(Context.BIOMETRIC_SERVICE) as? android.hardware.biometrics.BiometricManager
                if (biometricManager != null) {
                    val canAuth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        biometricManager.canAuthenticate(
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                        )
                    } else {
                        biometricManager.canAuthenticate()
                    }
                    if (canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS ||
                        canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                        return true
                    }
                }
            }
            val compat = FingerprintManagerCompat.from(context)
            compat.isHardwareDetected
        } catch (e: Exception) {
            false
        }
    }

    fun hasEnrolledBiometrics(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val biometricManager = context.getSystemService(Context.BIOMETRIC_SERVICE) as? android.hardware.biometrics.BiometricManager
                if (biometricManager != null) {
                    val canAuth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        biometricManager.canAuthenticate(
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                        )
                    } else {
                        biometricManager.canAuthenticate()
                    }
                    return canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
                }
            }
            val compat = FingerprintManagerCompat.from(context)
            compat.isHardwareDetected && compat.hasEnrolledFingerprints()
        } catch (e: Exception) {
            false
        }
    }

    fun promptBiometric(
        activity: Activity,
        title: String = "Unlock ContextPulse",
        subtitle: String = "Touch the fingerprint sensor to authenticate",
        negativeButtonText: String = "Use PIN Instead",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cancellationSignal = CancellationSignal()
                val executor = activity.mainExecutor

                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton(negativeButtonText, executor, DialogInterface.OnClickListener { _, _ ->
                        onError("Fingerprint canceled")
                    })
                    .build()

                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            onError(errString?.toString() ?: "Authentication error")
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Fingerprint not recognized. Try again or use PIN.")
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to launch BiometricPrompt", e)
                onError("Biometric authentication unavailable")
            }
        } else {
            onError("Biometrics require Android 9.0 or higher. Please use PIN.")
        }
    }
}
