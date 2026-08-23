package com.argol.awsprofile.domain

enum class AccessLevel {
    STANDING,
    ELEVATED;

    fun displayName(): String = when (this) {
        STANDING -> "STANDING"
        ELEVATED -> "ELEVATED"
    }
}
