App Lock 🔒
A robust, dual-engine security framework built for stock Android and Pixel devices. This project provides deep system-level app protection with a focus on Material 3 design and intelligent locking triggers.

🚀 Features
1. Dual-Engine Security
System Master Lock: Integrates with the native Android security framework. Use your device's existing Fingerprint, Pattern, PIN, or Password to authenticate.

Custom UI Engine: A standalone, internal security layer with a dedicated PIN/Pattern system, independent of the device's main lock screen.

2. Intelligent Lock Triggers
Customize when your apps should re-lock based on your workflow:

Immediate: Locks as soon as the app loses focus.

Screen-Off: Remains unlocked while the display is active; re-locks once the screen turns off.

Recents-Clear: Only locks once the app is cleared from the Overview (Recents) menu.

3. Smart Multitasking
PiP Awareness: Intelligent detection ensures that Picture-in-Picture mode (like YouTube or Maps) is not interrupted by the lock screen.

Material 3 UI: Fully compliant with modern Android design standards, featuring smooth animations and dynamic color support.

🛠 Technical Stack
Language: Kotlin

UI Framework: Jetpack Compose / Material 3

Architecture: MVVM (Model-View-ViewModel)

Key APIs:

AccessibilityService: For real-time foreground app detection.

UsageStatsManager: For tracking app usage intervals.

DevicePolicyManager: For advanced device-level security integration.

📋 Requirements
Android Version: Android 12 (API 31) and above.

Permissions Required:

PACKAGE_USAGE_STATS: To detect which app is currently open.

SYSTEM_ALERT_WINDOW: To display the custom lock overlay.

BIND_ACCESSIBILITY_SERVICE: To ensure the lock triggers immediately on app launch.

📥 Installation
Clone the repository:

Bash
git clone https://github.com/YourUsername/app-lock.git
Open the project in Android Studio (Ladybug or newer).

Build the APK and install it on your device.

Enable the required permissions in the app settings.
