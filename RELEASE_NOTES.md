# ExclusiveTV App v1.0.52

This release brings major improvements to live TV playback compatibility, specifically for Star/Hotstar network channels.

## 🚀 New Features & Improvements
*   **Robust Star Playlist Support**: Enhanced the core `KodiParser` to natively handle Star network playlists. It now automatically extracts authentication headers (cookies) and generates readable channel names from nested URL patterns.
*   **Enhanced Playback Compatibility**: Added automatic injection of `Origin` and `Referer` headers for Hotstar streams to prevent "403 Forbidden" errors.
*   **UI Symbol Fix**: Resolved an issue where Audio and Video quality icons were invisible on some Android versions (fixed drawable tint compatibility).

## 🐛 Bug Fixes
*   **Critical DRM Fix**: Resolved an issue where Hotstar channels would fail to play because DRM license requests were missing the required session cookies. 
*   **Build Optimization**: Consolidated parsing logic for better performance and smaller app size.

## Technical Details
*   **Version Code**: 16790528
*   **Build**: Release
