# App Lock

Lock selected apps with PIN, pattern, password, or device credential. Optional fingerprint unlock when using a custom lock.

## Features

- **Lock type**: Device PIN/pattern/password (system) or custom PIN, pattern, or password
- **Fingerprint**: When using a custom lock, you can enable “Use fingerprint” to unlock with fingerprint first; if fingerprint is not set up, only the custom lock screen is shown (no system dialog)
- **Lock timing**: Lock on exit, on screen off, or when app is removed from recents
- **Overlay**: When you open a locked app, a full-screen lock is shown until you unlock

## Requirements

- Android 7.0 (API 24) or higher
- Permissions: Overlay (display over other apps), Usage access, Accessibility (required for detecting foreground app), optional device admin for uninstall prevention

## Build

```bash
./gradlew assembleDebug
```

Release:

```bash
./gradlew assembleRelease
```

## Privacy

No data is collected. Lock settings and hashed credentials are stored locally on the device.

## License

See LICENSE file.
