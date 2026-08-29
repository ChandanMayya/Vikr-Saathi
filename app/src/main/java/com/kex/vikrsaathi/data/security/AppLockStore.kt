package com.kex.vikrsaathi.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppLockStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    var timeout: AppLockTimeout
        get() = AppLockTimeout.fromMillis(prefs.getLong(KEY_TIMEOUT, AppLockTimeout.SECONDS_30.millis))
        set(value) = prefs.edit().putLong(KEY_TIMEOUT, value.millis).apply()

    var pinSalt: String
        get() = prefs.getString(KEY_PIN_SALT, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

    var pinHash: String
        get() = prefs.getString(KEY_PIN_HASH, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        private set(value) = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, value).apply()

    var lockoutUntilMillis: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        private set(value) = prefs.edit().putLong(KEY_LOCKOUT_UNTIL, value).apply()

    var recoverySalt: String
        get() = prefs.getString(KEY_RECOVERY_SALT, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_RECOVERY_SALT, value).apply()

    var recoveryHash: String
        get() = prefs.getString(KEY_RECOVERY_HASH, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_RECOVERY_HASH, value).apply()

    fun hasPin(): Boolean = pinHash.isNotBlank() && pinSalt.isNotBlank()

    fun hasRecoveryCode(): Boolean = recoveryHash.isNotBlank() && recoverySalt.isNotBlank()

    /** Saves PIN hash. Returns a new recovery code when one is created for first-time setup. */
    fun setupPinWithRecovery(pin: String): String? {
        savePinHash(pin)
        if (hasRecoveryCode()) return null
        val code = RecoveryCodeGenerator.generate()
        saveRecoveryCode(code)
        return code
    }

    fun savePinHash(pin: String) {
        val salt = PinHasher.createSalt()
        pinSalt = salt
        pinHash = PinHasher.hashPin(pin, salt)
        resetFailedAttempts()
    }

    fun savePin(pin: String) {
        savePinHash(pin)
    }

    private fun saveRecoveryCode(code: String) {
        val salt = PinHasher.createSalt()
        recoverySalt = salt
        recoveryHash = PinHasher.hashPin(RecoveryCodeGenerator.normalize(code), salt)
    }

    fun verifyRecoveryCode(code: String): Boolean {
        if (!hasRecoveryCode()) return false
        if (!RecoveryCodeGenerator.isValidFormat(code)) return false
        return PinHasher.verifyPin(
            RecoveryCodeGenerator.normalize(code),
            recoverySalt,
            recoveryHash
        )
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .remove(KEY_RECOVERY_SALT)
            .remove(KEY_RECOVERY_HASH)
            .apply()
        resetFailedAttempts()
    }

    fun verifyPin(pin: String): Boolean {
        return PinHasher.verifyPin(pin, pinSalt, pinHash)
    }

    fun registerFailedAttempt() {
        val attempts = failedAttempts + 1
        failedAttempts = attempts
        if (attempts >= PinHasher.MAX_FAILED_ATTEMPTS) {
            lockoutUntilMillis = System.currentTimeMillis() + PinHasher.LOCKOUT_MILLIS
            failedAttempts = 0
        }
    }

    fun resetFailedAttempts() {
        failedAttempts = 0
        lockoutUntilMillis = 0L
    }

    fun lockoutRemainingMillis(now: Long = System.currentTimeMillis()): Long {
        val remaining = lockoutUntilMillis - now
        return if (remaining > 0L) remaining else 0L
    }

    fun isLockedOut(now: Long = System.currentTimeMillis()): Boolean {
        return lockoutRemainingMillis(now) > 0L
    }

    companion object {
        private const val PREFS_NAME = "vikr_saathi_app_lock"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_TIMEOUT = "timeout"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
    }
}
