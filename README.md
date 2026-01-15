# Exclusive TV

A premium TV network video player for Android TV, optimized for a seamless and immersive viewing experience. "Exclusive TV" combines a high-end aesthetic with robust playback capabilities and intelligent content management.

![Exclusive TV Repository](https://github.com/CodesRahul96/Exclusive-TV-APP)

## 📺 Key Features

- **Premium UI Overhaul:** Modern "Midnight Blue & Gold" theme with Glassmorphism effects and smooth focus animations.
- **Audio Track Selection:** Real-time audio track switching during playback with a dedicated, glassy UI.
- **Focused Navigation:** Optimized half-screen menu layout allows you to browse categories while the video continues playing.
- **Enhanced Security:** Native support for **Gua64** (Hexagram-based) encryption for secure playlist distribution.
- **Auto-Sync & Local Cache:** Intelligent background update mechanism that fetches the latest channels and caches them locally for offline access.
- **Leanback Optimized:** Tailored for Android TV remote handles (D-pad) with shortcuts for power users.
- **Playback Controls:** "Force High Quality" and "Play Last Channel" settings for a predictable viewing experience.

## 🎮 Navigation & Shortcuts

| Action | Control (Remote / Mouse) |
| :--- | :--- |
| **Open Menu** | Press **DPAD_LEFT** or Click the left edge. |
| **Audio Selector** | Short Press **DPAD_RIGHT** (During playback). |
| **Settings Menu** | Long Press **DPAD_RIGHT** or Double-click. |
| **Favorites** | Long Press **SELECT/OK** on a channel. |
| **Category Nav** | Navigate Categories (Left) and Channels (Right) seamlessly. |

## 🛠 Configuration & Local Storage

Exclusive TV uses a hybrid storage model to ensure content is always available:

1. **Local Cache (`channels.txt`)**: The app stores your current playlist in its internal private storage. This allows instant loading even without internet.
2. **Auto-Update**: If "Auto-load configuration" is enabled in Settings, the app checks your remote URL on startup and refreshes any changes automatically.
3. **Internal Fallback**: Includes a default channel list bundled within the APK for fresh installs.

### Standard JSON Format:
```json
[
  {
    "group": "Entertainment",
    "logo": "https://example.com/logo.png",
    "name": "channel_01",
    "title": "Exclusive Movie Hub",
    "uris": ["https://cdn.example.com/stream.m3u8"],
    "headers": {
      "User-Agent": "ExclusiveTV/1.0"
    }
  }
]
```
*Note: You can also use [Gua64 encryption](https://github.com/CodesRahul96/Exclusive-TV-APP/blob/main/walkthrough.md#3-gua64-encryption-new) for your links.*

## 🚀 Installation

1. **Latest Release**: Download from [GitHub Releases](https://github.com/CodesRahul96/Exclusive-TV-APP/releases).
2. **ADB**: `adb install exclusive-tv.apk`
3. **Smart TV**: Copy to a USB drive or use a TV Assistant / Send Files to TV app.

## 👨‍💻 Development

- **Package Name:** `com.codesrahul.exclusivetv`
- **Minimum SDK:** 21 (Android 5.0)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **Build System:** Gradle (Kotlin DSL)

---
*Maintained by CodesRahul & The Exclusive TV Team.*
