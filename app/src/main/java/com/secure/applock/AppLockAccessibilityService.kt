package com.secure.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Monitors window state changes to detect when a locked app comes to the foreground.
 * Applies lock timing: LOCK_ON_EXIT / LOCK_ON_RECENTS_CLEAR = lock when user leaves the app;
 * LOCK_ON_SCREEN_OFF = lock only on screen off (handled by ScreenOffReceiver).
 * When the user switches to a locked app (and it's not session-unlocked), starts
 * LockOverlayActivity to show the lock screen.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        (application as? AppLockApplication)?.appLockRepository?.refreshSnapshotBlocking()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val app = application as? AppLockApplication ?: return
        val repository = app.appLockRepository

        val previous = lastForegroundPackage
        lastForegroundPackage = pkg

        if (previous != null && previous != pkg) {
            val mode = repository.getLockMode()
            if (repository.isPackageLocked(previous) && repository.isSessionUnlocked(previous)) {
                when (mode) {
                    LockMode.LOCK_ON_EXIT, LockMode.LOCK_ON_RECENTS_CLEAR ->
                        repository.lockPackage(previous)
                    LockMode.LOCK_ON_SCREEN_OFF -> { }
                }
            }
        }

        if (!repository.isPackageLocked(pkg)) return
        if (repository.isSessionUnlocked(pkg)) return
        val intent = Intent().apply {
            setClassName(this@AppLockAccessibilityService, "com.secure.applock.LockOverlayActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_LOCKED_PACKAGE, pkg)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) { }
    }

    override fun onInterrupt() {}

    companion object {
        const val EXTRA_LOCKED_PACKAGE = "locked_package"
    }
}
