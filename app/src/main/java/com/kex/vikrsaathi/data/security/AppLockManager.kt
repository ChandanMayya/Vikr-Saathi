package com.kex.vikrsaathi.data.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager

sealed class PinVerifyResult {
    data object Success : PinVerifyResult()
    data object InvalidPin : PinVerifyResult()
    data class LockedOut(val remainingMillis: Long) : PinVerifyResult()
}

sealed class PinSetupResult {
    data class Success(val recoveryCode: String?) : PinSetupResult()
    data object InvalidFormat : PinSetupResult()
}

class AppLockManager(
    private val store: AppLockStore
) {
    @Volatile
    private var unlocked = false

    private var backgroundAtMillis: Long? = null

    val isLockEnabled: Boolean
        get() = store.isEnabled && store.hasPin()

    val biometricEnabled: Boolean
        get() = store.biometricEnabled && isLockEnabled

    fun shouldShowLock(): Boolean = isLockEnabled && !unlocked

    fun markBackground(now: Long = System.currentTimeMillis()) {
        if (isLockEnabled && unlocked) {
            backgroundAtMillis = now
        }
    }

    fun shouldLockOnResume(now: Long = System.currentTimeMillis()): Boolean {
        if (!isLockEnabled) return false
        if (!unlocked) return true
        val backgroundAt = backgroundAtMillis ?: return false
        return when (store.timeout) {
            AppLockTimeout.IMMEDIATE -> true
            else -> now - backgroundAt >= store.timeout.millis
        }
    }

    fun lock() {
        unlocked = false
    }

    fun unlock() {
        unlocked = true
        backgroundAtMillis = null
        store.resetFailedAttempts()
    }

    fun verifyPin(pin: String): PinVerifyResult {
        if (store.isLockedOut()) {
            return PinVerifyResult.LockedOut(store.lockoutRemainingMillis())
        }
        if (!PinHasher.isValidPinFormat(pin)) {
            return PinVerifyResult.InvalidPin
        }
        return if (store.verifyPin(pin)) {
            unlock()
            PinVerifyResult.Success
        } else {
            store.registerFailedAttempt()
            if (store.isLockedOut()) {
                PinVerifyResult.LockedOut(store.lockoutRemainingMillis())
            } else {
                PinVerifyResult.InvalidPin
            }
        }
    }

    fun setupPin(pin: String): PinSetupResult {
        if (!PinHasher.isValidPinFormat(pin)) return PinSetupResult.InvalidFormat
        val recoveryCode = store.setupPinWithRecovery(pin)
        return PinSetupResult.Success(recoveryCode)
    }

    fun resetPin(newPin: String): Boolean {
        if (!PinHasher.isValidPinFormat(newPin)) return false
        store.savePinHash(newPin)
        unlock()
        return true
    }

    fun verifyRecoveryCode(code: String): Boolean = store.verifyRecoveryCode(code)

    fun hasRecoveryCode(): Boolean = store.hasRecoveryCode()

    fun canUseDeviceCredential(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
        if (!keyguard.isDeviceSecure) return false
        val status = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun changePin(currentPin: String, newPin: String): PinVerifyResult {
        when (val current = verifyPinForChange(currentPin)) {
            is PinVerifyResult.LockedOut -> return current
            PinVerifyResult.InvalidPin -> return PinVerifyResult.InvalidPin
            PinVerifyResult.Success -> Unit
        }
        if (!PinHasher.isValidPinFormat(newPin)) return PinVerifyResult.InvalidPin
        store.savePinHash(newPin)
        return PinVerifyResult.Success
    }

    fun disableLock(pin: String): PinVerifyResult {
        when (val result = verifyPinForChange(pin)) {
            is PinVerifyResult.LockedOut -> return result
            PinVerifyResult.InvalidPin -> return PinVerifyResult.InvalidPin
            PinVerifyResult.Success -> Unit
        }
        store.isEnabled = false
        store.biometricEnabled = false
        store.clearPin()
        unlock()
        return PinVerifyResult.Success
    }

    fun enableLock() {
        store.isEnabled = true
    }

    fun setBiometricEnabled(enabled: Boolean) {
        store.biometricEnabled = enabled
    }

    fun setTimeout(timeout: AppLockTimeout) {
        store.timeout = timeout
    }

    fun lockoutRemainingMillis(): Long = store.lockoutRemainingMillis()

    fun isLockedOut(): Boolean = store.isLockedOut()

    fun hasPin(): Boolean = store.hasPin()

    fun getTimeout(): AppLockTimeout = store.timeout

    fun verifyPinForSettings(pin: String): PinVerifyResult = verifyPinForChange(pin)

    private fun verifyPinForChange(pin: String): PinVerifyResult {
        if (store.isLockedOut()) {
            return PinVerifyResult.LockedOut(store.lockoutRemainingMillis())
        }
        if (!PinHasher.isValidPinFormat(pin)) {
            return PinVerifyResult.InvalidPin
        }
        return if (store.verifyPin(pin)) {
            store.resetFailedAttempts()
            PinVerifyResult.Success
        } else {
            store.registerFailedAttempt()
            if (store.isLockedOut()) {
                PinVerifyResult.LockedOut(store.lockoutRemainingMillis())
            } else {
                PinVerifyResult.InvalidPin
            }
        }
    }
}
