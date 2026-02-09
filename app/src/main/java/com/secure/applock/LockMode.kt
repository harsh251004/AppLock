package com.secure.applock

/**
 * When to require unlock again for a locked app.
 * - LOCK_ON_EXIT: Lock as soon as the app is not in the foreground.
 * - LOCK_ON_SCREEN_OFF: Lock only when the screen is turned off.
 * - LOCK_ON_RECENTS_CLEAR: Lock when the app task is removed from recents.
 */
enum class LockMode(val value: String) {
    LOCK_ON_EXIT("lock_on_exit"),
    LOCK_ON_SCREEN_OFF("lock_on_screen_off"),
    LOCK_ON_RECENTS_CLEAR("lock_on_recents_clear");

    companion object {
        fun from(value: String?) = entries.find { it.value == value } ?: LOCK_ON_EXIT
    }
}
