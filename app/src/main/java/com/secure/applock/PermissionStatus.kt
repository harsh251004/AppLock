package com.secure.applock

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.biometric.BiometricManager

/**
 * Permission/setting item for the settings page: title, status, and intent to open settings.
 */
data class PermissionItem(
    val title: String,
    val isEnabled: Boolean,
    val openSettings: () -> Unit
)

object PermissionStatus {

    fun overlay(context: Context): PermissionItem {
        val enabled = try {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)
        } catch (_: Exception) { false }
        return PermissionItem(
            title = context.getString(R.string.permission_overlay_title),
            isEnabled = enabled,
            openSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
    }

    fun usageAccess(context: Context): PermissionItem {
        val enabled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                appOps != null && appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                ) == AppOpsManager.MODE_ALLOWED
            } else false
        } catch (_: Exception) { false }
        return PermissionItem(
            title = context.getString(R.string.permission_usage_title),
            isEnabled = enabled,
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }

    fun accessibility(context: Context): PermissionItem {
        val enabled = try { isAccessibilityServiceEnabled(context) } catch (_: Exception) { false }
        return PermissionItem(
            title = context.getString(R.string.permission_accessibility_title),
            isEnabled = enabled,
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }

    /**
     * Checks whether our accessibility service is enabled.
     * Uses Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES (and fallback to AccessibilityManager).
     * Looks for our component: packageName/AppLockAccessibilityService (or equivalent).
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val ourPackage = context.packageName
        val ourComponent = ComponentName(context, AppLockAccessibilityService::class.java)
        val ourComponentFlat = ourComponent.flattenToString()

        // Method 1: Settings.Secure (format varies by device: "pkg/cls:pkg/cls" or similar)
        try {
            val enabledSecure = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            if (enabledSecure.isNotEmpty()) {
                // Loose match: our package + AppLock in the enabled list
                if (enabledSecure.contains(ourPackage) && enabledSecure.contains("AppLock")) {
                    return true
                }
                val list = enabledSecure.split(':', ';', ',').map { it.trim() }.filter { it.isNotEmpty() }
                for (component in list) {
                    if (component == ourComponentFlat) return true
                    if (component.equals("$ourPackage/.AppLockAccessibilityService", ignoreCase = true)) return true
                    if (component.contains(ourPackage) && component.contains("AppLockAccessibilityService")) return true
                }
            }
        } catch (_: Exception) { }

        // Method 2: AccessibilityManager (try FEEDBACK_GENERIC = 1; some devices use 0 for "all")
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            @Suppress("DEPRECATION")
            val enabledList = am.getEnabledAccessibilityServiceList(1)
                ?: am.getEnabledAccessibilityServiceList(0)
                ?: emptyList()
            for (info in enabledList) {
                val id = info.id ?: ""
                if (id.contains(ourPackage) && id.contains("AppLock")) return true
                val si = info.resolveInfo?.serviceInfo
                if (si != null && si.packageName == ourPackage &&
                    si.name?.contains("AppLockAccessibilityService") == true
                ) return true
            }
        } catch (_: Exception) { }
        return false
    }

    fun biometric(context: Context): PermissionItem {
        val enabled = try {
            val manager = BiometricManager.from(context)
            manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) { false }
        return PermissionItem(
            title = context.getString(R.string.permission_biometric_title),
            isEnabled = enabled,
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }

    /** Battery optimization unrestricted: app can run in background reliably (for lock overlay, accessibility). */
    fun batteryOptimization(context: Context): PermissionItem {
        val enabled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
        } catch (_: Exception) { false }
        return PermissionItem(
            title = context.getString(R.string.permission_battery_title),
            isEnabled = enabled,
            openSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activity = context.getActivity()
                    val packageUri = Uri.parse("package:${context.packageName}")
                    val tryIntents = listOf(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = packageUri
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = packageUri
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    for (intent in tryIntents) {
                        try {
                            activity?.startActivity(intent) ?: context.applicationContext.startActivity(intent)
                            break
                        } catch (_: ActivityNotFoundException) { }
                    }
                }
            }
        )
    }

    /** True if this app is an active device admin (uninstall prevention enabled). */
    fun isDeviceAdminActive(context: Context): Boolean {
        val component = ComponentName(context, AppLockDeviceAdminReceiver::class.java)
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isAdminActive(component) == true
        } catch (_: Exception) { false }
    }

    /** All permission items for the Settings permissions list (excludes uninstall prevention, which has its own toggle). */
    fun all(context: Context): List<PermissionItem> = listOf(
        overlay(context),
        usageAccess(context),
        accessibility(context),
        biometric(context),
        batteryOptimization(context)
    )
}

private fun Context.getActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Opens the system screen to add this app as device admin (uninstall prevention). Launch from Activity when possible; do not use FLAG_ACTIVITY_NEW_TASK for the Add Device Admin screen as some Android versions block it. */
fun openAddDeviceAdmin(context: Context) {
    val component = ComponentName(context, AppLockDeviceAdminReceiver::class.java)
    val activity = context.getActivity()
    if (activity != null) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_explanation)
            )
            // Do NOT add FLAG_ACTIVITY_NEW_TASK when launching from Activity; some versions block the Device Admin UI otherwise.
        }
        activity.startActivity(intent)
    } else {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_explanation)
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.applicationContext.startActivity(intent)
    }
}

/** Opens the Device Admin list screen so the user can deactivate this app as device admin. Falls back to Security settings if the direct screen is not available. */
fun openDeviceAdminSettings(context: Context) {
    val deviceAdminIntent = Intent().setComponent(
        ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")
    )
    val activity = context.getActivity()
    try {
        if (activity != null) {
            activity.startActivity(deviceAdminIntent)
        } else {
            deviceAdminIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(deviceAdminIntent)
        }
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity?.startActivity(fallback) ?: context.applicationContext.startActivity(fallback)
    }
}
