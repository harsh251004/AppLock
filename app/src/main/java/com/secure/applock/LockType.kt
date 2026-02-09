package com.secure.applock

/**
 * How the user unlocks the app / locked apps.
 * - DEVICE_CREDENTIAL: System PIN, pattern, or password (BiometricPrompt with device credential).
 * - PIN: Custom numeric PIN (Android-style keypad).
 * - PATTERN: Custom pattern (3x3 dot grid).
 * - PASSWORD: Custom alphanumeric password.
 */
enum class LockType(val value: String) {
    DEVICE_CREDENTIAL("device_credential"),
    PIN("pin"),
    PATTERN("pattern"),
    PASSWORD("password");

    companion object {
        fun from(value: String?) = entries.find { it.value == value } ?: DEVICE_CREDENTIAL
    }

    val isCustom: Boolean get() = this != DEVICE_CREDENTIAL
}
