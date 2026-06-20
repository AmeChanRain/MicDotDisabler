# Mic Dot Disabler

Disable the annoying green privacy indicator dot on Android 12+ — no root required.

![](https://img.shields.io/badge/Android-12%2B-34A853?logo=android)
![](https://img.shields.io/badge/Kotlin-2.0-7C4DFF?logo=kotlin)
![](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose)
![](https://img.shields.io/badge/Shizuku-12.2.0-FF6D00)

## What is this?

Android 12 introduced privacy indicators — a green dot in the status bar whenever an app uses your microphone or camera. While useful for privacy awareness, many users find it distracting, especially during calls, voice recording, or gaming.

**Mic Dot Disabler** lets you turn it off with one tap, using [Shizuku](https://github.com/RikkaApps/Shizuku) to execute privileged commands without rooting your device.

## How it works

The app runs two `device_config` commands via Shizuku's ADB shell identity:

```bash
cmd device_config put privacy camera_mic_icons_enabled false default
cmd device_config set_sync_disabled_for_tests persistent
```

- **Command 1** — sets the privacy indicator flag to `false`
- **Command 2** — prevents the system from re-syncing the config (persists across reboots)

> **Note:** After a system OTA update, the setting may be reset and you'll need to tap the button again.

## Screenshots

| Setup Wizard | Main Screen | Success! |
|---|---|---|
| Guided Shizuku installation steps | One-tap disable button | Confetti celebration |

## Compatibility

| Android Version | Status |
|---|---|
| **12 – 13** | ✅ Fully supported |
| **XR (Samsung OneUI XR)** | ✅ Fully supported |
| **14+ (Pixel / AOSP)** | ⚠️ Flag is allowlist-protected; cannot be modified via ADB shell. Root + [GreenDotHide](https://github.com/Dorian399/GreenDotHide) LSPosed module is required on these devices |

The app detects allowlist-protected devices and displays a clear explanation in the error panel.

## Requirements

- **Android 12** (API 31) or later
- [**Shizuku**](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) installed and running
- Wireless debugging enabled (Developer Options)
- **No root required**
- **No extra permissions** — the app declares zero Android permissions

## Getting Started

### 1. Install Shizuku

Download Shizuku from [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).

### 2. Start Shizuku via Wireless Debugging

1. Enable **Developer Options** on your device
2. Turn on **Wireless Debugging**
3. Open Shizuku, tap **"Pairing"** and follow the in-app instructions
4. Once paired, tap **"Start"** to begin the service

### 3. Authorize Mic Dot Disabler

1. Open Mic Dot Disabler — it will detect Shizuku automatically
2. Tap **"Grant Permission"** when prompted by Shizuku
3. Once authorized, tap **"Disable Mic Dot"**

That's it! The green dot is gone.

## Building from Source

```bash
git clone https://github.com/AmeChanRain/MicDotDisabler
cd MicDotDisabler
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Tech Stack

- **Kotlin 2.0** + **Jetpack Compose**
- **Material 3** (Material You) dynamic theming
- **Shizuku API 12.2.0** for ADB shell command execution
- **Min SDK 31** / **Target SDK 34**

## Project Structure

```
app/src/main/java/io/ame/micdotdisabler/
├── MainActivity.kt              # Single-activity entry point
├── shizuku/
│   └── ShizukuManager.kt        # Shizuku state, permission, command execution
├── ui/
│   ├── Screen.kt                # AppState sealed interface
│   ├── setup/
│   │   └── SetupScreen.kt       # Onboarding wizard
│   ├── main/
│   │   └── MainScreen.kt        # Main action screen + result panels
│   ├── components/
│   │   ├── ConfettiOverlay.kt   # Canvas-drawn confetti animation
│   │   └── StatusBadge.kt       # Shizuku connection status chip
│   └── theme/
│       ├── Color.kt
│       ├── Type.kt
│       └── Theme.kt
└── util/
```

## License

This project is licensed under the Apache License 2.0 — see [LICENSE](LICENSE) for details.

## Acknowledgments

- [Shizuku](https://github.com/RikkaApps/Shizuku) — the privilege bridge that makes this possible
- [GreenDotHide](https://github.com/Dorian399/GreenDotHide) — root-based alternative for allowlist-protected devices
