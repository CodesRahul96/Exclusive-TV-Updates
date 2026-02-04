# Exclusive TV

A premium IPTV player for Android TV, optimized for a seamless and immersive viewing experience. **Exclusive TV** combines a high-end aesthetic with robust playback capabilities, intelligent content management, and advanced features for power users.

![Exclusive TV](https://img.shields.io/badge/Platform-Android%20TV-green?style=flat-square) ![Version](https://img.shields.io/badge/Version-v1.0.58-blue?style=flat-square) ![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

**Repository:** [CodesRahul96/Exclusive-TV-APP](https://github.com/CodesRahul96/Exclusive-TV-APP)

---

## 📺 Key Features

### 🎨 Premium User Experience
- **Modern UI Design:** "Midnight Blue & Gold" theme with glassmorphism effects and smooth focus animations
- **Optimized Navigation:** Half-screen menu layout allows browsing while video continues playing
- **Smart Auto-Hide:** Intelligent timer management for menus (15s) and settings (30s) with user interaction detection
- **Touch-Outside-to-Close:** Tap anywhere to close settings or audio selector dialogs
- **Leanback Optimized:** Tailored for Android TV D-pad navigation with power user shortcuts

### 🎵 Advanced Playback
- **Audio Track Selection:** Real-time audio track switching with timed long-press gestures
- **Multi-Source Support:** Automatic fallback between primary and backup stream sources
- **Force High Quality:** Option to always select the highest quality stream available
- **Auto-Retry System:** Recovers from network drops or stream failures (up to 8 retries)
- **WakeLock Integration:** Prevents device sleep during long viewing sessions

### 🔐 Security & Privacy
- **DRM Support:** Widevine, PlayReady, and ClearKey DRM schemes
- **Custom Headers:** Per-channel HTTP headers including User-Agent customization
- **Root/VPN Detection:** Security checks to prevent unauthorized usage

### 📊 Content Management
- **EPG Integration:** Electronic Program Guide with current and upcoming show information
- **Favorites System:** Quick access to your favorite channels with "My Collection"
- **Channel Reordering:** Drag-and-drop channel and category organization
- **Channel Renaming:** Custom names for channels and categories
- **Auto-Sync & Cache:** Intelligent background updates with local caching for offline access

### ⚙️ Advanced Settings
- **Dual-Source Updates:** Automatic fallback to secondary repository if primary is unavailable
- **Update Blocking:** Prevents channel loading when forced update is required
- **Factory Reset:** Complete data/cache/settings wipe with automatic app restart
- **Auto-Update:** Configurable automatic playlist updates on startup
- **Channel Pruning:** Option to remove dead/non-working channels
- **Boot Startup:** Launch app automatically when device boots
- **Server Status:** Built-in HTTP server for remote playlist management

---

## 🎮 Navigation & Shortcuts

### Remote Control (TV/TV Box)
| Action | Control |
| :--- | :--- |
| **Open Menu** | Press **DPAD_LEFT** |
| **Audio Selector** | Long Press **DPAD_RIGHT** (3 seconds) |
| **Settings Menu** | **MENU** button or Long Press **DPAD_RIGHT** (5 seconds) |
| **Add to Favorites** | Long Press **SELECT/OK** on a channel |
| **Channel Up/Down** | **CHANNEL_UP** / **CHANNEL_DOWN** buttons |
| **Direct Channel** | Number keys (0-9) for quick channel selection |

### Mobile Touch Controls
| Action | Control |
| :--- | :--- |
| **Open Menu** | Single tap anywhere |
| **Audio Selector** | 3-second hold on right half of screen |
| **Settings Menu** | 5-second hold on right half of screen or double tap |
| **Close Dialog** | Tap anywhere outside the dialog |
| **Channel Navigation** | Swipe up/down in center third |

---

## 🛠 Configuration & Storage

Exclusive TV uses a hybrid storage model to ensure content is always available:

1. **Local Cache (`channels.txt`)**: Stores your current playlist in internal private storage for instant loading
2. **Auto-Update**: Checks remote URL on startup and refreshes changes automatically (if enabled)
3. **Internal Fallback**: Includes default channel list bundled within the APK for fresh installs

### Standard JSON Format

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
    },
    "drm_scheme": "widevine",
    "drm_license_url": "https://license.example.com/widevine"
  }
]
```

---

## 🚀 Installation

### From GitHub Releases (Recommended)
1. Download the latest APK from [GitHub Releases](https://github.com/CodesRahul96/Exclusive-TV-APP/releases)
2. Install via ADB: `adb install ExclusiveTV-v1.0.58.apk`
3. Or copy to USB drive and install directly on your Android TV

### Dual-Repository Update System
The app supports automatic updates from two sources:
- **Primary**: [CodesRahul96/Exclusive-TV-APP](https://github.com/CodesRahul96/Exclusive-TV-APP)
- **Fallback**: [CodesRahul96/Exclusive-TV-Updates](https://github.com/CodesRahul96/Exclusive-TV-Updates)

This ensures updates work even if the primary repository becomes private.

### From Source
```bash
git clone https://github.com/CodesRahul96/Exclusive-TV-APP.git
cd Exclusive-TV-APP
./gradlew assembleRelease
```

---

## 🔧 What's New in v1.0.58

### Touch & Gesture Controls
- **Timed Long-Press Gestures:** 3-second hold for audio selector, 5-second hold for settings
- **Touch-Outside-to-Close:** Tap anywhere to dismiss settings or audio track dialogs
- **Improved Mobile Experience:** Optimized touch controls for phones and tablets

### Update System Enhancements
- **Dual-Repository Support:** Automatic fallback to secondary update source
- **Update Blocking:** Prevents channel loading when forced update is required
- **Silent Updates:** Background refresh without interrupting playback

### UI/UX Improvements
- **Updated Messages:** "Channels updated" instead of "Channel imported successfully"
- **Cleaner Notifications:** Removed "App is up to date" message
- **Better Feedback:** Update notifications only appear when update is available

---

## 👨‍💻 Development

- **Package Name:** `com.codesrahul.exclusivetv`
- **Minimum SDK:** 21 (Android 5.0 Lollipop)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **Build System:** Gradle (Kotlin DSL)
- **Architecture:** MVVM with LiveData

### Key Dependencies
- ExoPlayer 2.19.1 (Media playback)
- Glide 4.16.0 (Image loading)
- Gson 2.10.1 (JSON parsing)
- NanoHTTPD 2.3.1 (Built-in HTTP server)
- Retrofit 2.9.0 (Network requests)

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📧 Support

For issues, questions, or feature requests, please open an issue on [GitHub Issues](https://github.com/CodesRahul96/Exclusive-TV-APP/issues).

---

*Maintained by CodesRahul & The Exclusive TV Team.*
