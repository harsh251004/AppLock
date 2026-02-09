package com.secure.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * When ACTION_SCREEN_OFF is received and lock mode is LOCK_ON_SCREEN_OFF,
 * mark the last foreground locked package (and any session-unlocked locked packages) as locked
 * so they require auth again on next open.
 *
 * We lock all currently session-unlocked locked packages on screen off,
 * since we don't have a single "foreground" package at that moment.
 */
class ScreenOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        val repository = (context.applicationContext as? AppLockApplication)?.appLockRepository ?: return
        if (repository.getLockMode() != LockMode.LOCK_ON_SCREEN_OFF) return
        for (pkg in repository.getSessionUnlockedPackages()) {
            if (repository.isPackageLocked(pkg)) {
                repository.lockPackage(pkg)
            }
        }
    }
}
