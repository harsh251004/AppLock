package com.secure.applock

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin receiver used for uninstall prevention.
 * When enabled as device admin, the user must deactivate it in Settings before uninstalling the app.
 * Declared in the manifest with android.app.action.DEVICE_ADMIN_ENABLED.
 */
class AppLockDeviceAdminReceiver : DeviceAdminReceiver()
