package com.kex.vikrsaathi.data.security

import java.security.MessageDigest
import java.security.SecureRandom

object PinHasher {

    private const val SALT_BYTES = 16
    private const val HASH_ALGORITHM = "SHA-256"

    fun createSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hashPin(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val salt = saltHex.hexToBytes()
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest().toHex()
    }

    fun verifyPin(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (pin.isBlank() || saltHex.isBlank() || expectedHashHex.isBlank()) return false
        return hashPin(pin, saltHex) == expectedHashHex
    }

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length == PIN_LENGTH && pin.all { it.isDigit() }
    }

    const val PIN_LENGTH = 4
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_MILLIS = 30_000L

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
