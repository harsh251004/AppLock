package com.secure.applock

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class AppLockApplication : Application() {

    val appLockRepository: AppLockRepository by lazy {
        AppLockRepository(this).also { it.refreshSnapshotBlocking() }
    }

    private var screenOffReceiver: ScreenOffReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        screenOffReceiver = ScreenOffReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, filter)
        }
    }
}
