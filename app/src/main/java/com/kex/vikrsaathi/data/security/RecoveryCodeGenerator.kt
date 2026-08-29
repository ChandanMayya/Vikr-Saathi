package com.kex.vikrsaathi.data.security

import java.security.SecureRandom

object RecoveryCodeGenerator {

    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 12

    fun generate(): String = format(generateRaw())

    fun generateRaw(): String {
        val random = SecureRandom()
        return buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) {
                append(CHARSET[random.nextInt(CHARSET.length)])
            }
        }
    }

    fun format(raw: String): String {
        val normalized = normalize(raw)
        if (normalized.length != CODE_LENGTH) return raw.uppercase()
        return "VKRS-${normalized.substring(0, 4)}-${normalized.substring(4, 8)}-${normalized.substring(8, 12)}"
    }

    fun normalize(input: String): String {
        return input.uppercase()
            .replace("VKRS", "")
            .replace("-", "")
            .replace(" ", "")
            .trim()
    }

    fun isValidFormat(input: String): Boolean {
        val normalized = normalize(input)
        return normalized.length == CODE_LENGTH && normalized.all { it in CHARSET }
    }
}
