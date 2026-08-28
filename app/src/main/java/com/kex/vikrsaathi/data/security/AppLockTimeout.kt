package com.kex.vikrsaathi.data.security

enum class AppLockTimeout(val millis: Long) {
    IMMEDIATE(0L),
    SECONDS_30(30_000L),
    MINUTE_1(60_000L),
    MINUTES_5(300_000L),
    MINUTES_15(900_000L);

    companion object {
        fun fromMillis(value: Long): AppLockTimeout {
            return entries.firstOrNull { it.millis == value } ?: SECONDS_30
        }
    }
}
