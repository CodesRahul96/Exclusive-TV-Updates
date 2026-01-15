# Exclusive TV

A premium TV network video player for Android TV, optimized for a seamless and immersive viewing experience.

[Exclusive TV Repository](https://github.com/CodesRahul96/Exclusive-TV-APP)

## Key Features

- **Premium UI Overhaul:** Modern "Midnight Blue & Gold" theme with Glassmorphism effects.
- **Focused Navigation:** Optimized half-screen menu layout for easy browsing while watching.
- **Enhanced Security:** Supports **Gua64** (Hexagram-based) encoding for secure API communication.
- **Auto-Update:** Intelligent update mechanism checking GitHub releases.
- **Leanback Optimized:** Full support for Android TV remote controls and D-pad navigation.

## Usage

- **Open Program List:** Press **DPAD_LEFT** or use the touch screen left edge.
- **Open Settings:** Press **DPAD_RIGHT** or double-click to access configuration.
- **Navigation:** Use DPAD buttons to browse categories (left) and channels (right).
- **Favorites:** Press the **SELECT/OK** button long-press (or right-click) to add/cancel favorites.
- **Configuration:** Enter your video source URL in the Settings page.

## Configuration Format

Exclusive TV supports encrypted and standard JSON formats. For the best experience, use the [Gua64 encryption](https://github.com/CodesRahul96/Exclusive-TV-APP/blob/main/walkthrough.md#3-gua64-encryption-new).

### Standard JSON Schema:
```json
[
  {
    "group": "Category Name",
    "logo": "Icon URL",
    "name": "Standard ID",
    "title": "Display Title",
    "uris": ["Stream URL"],
    "headers": {
      "User-Agent": "Custom User Agent (optional)"
    }
  }
]
```

## Installation

1.  **Direct Download:** Get the latest APK from the [GitHub Releases](https://github.com/CodesRahul96/Exclusive-TV-APP/releases).
2.  **ADB Install:**
    ```shell
    adb install exclusive-tv.apk
    ```
3.  **Xiaomi TV:** Install via Xiaomi TV Assistant.

## Changelog

Detailed changes can be found in [HISTORY.md](./HISTORY.md).

## Development

- **Package Name:** `com.codesrahul.exclusivetv`
- **Compiler:** Android SDK 34
- **Language:** Kotlin

## TODO

- [ ] Program EPG Preview integration
- [ ] Plugin Store for extended functionality
- [ ] Multi-language support
