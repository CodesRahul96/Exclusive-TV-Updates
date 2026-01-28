# ExclusiveTV App v1.0.52

This release brings major improvements to live TV playback compatibility, specifically for Star/Hotstar channels.

## 🚀 New Features & Improvements
*   **Native Star Playlist Support**: Implemented a new intelligent parser (`StarParser`) to handle Star network playlists, automatically extracting authentication headers and cleaning up channel names.
*   **Enhanced Playback Compatibility**: Added automatic injection of `Origin` and `Referer` headers for Hotstar streams to prevent 403 Forbidden errors.

## 🐛 Bug Fixes
*   **DRM Authentication Fix**: Resolved an issue where Hotstar channels would not play because DRM license requests were missing the required session cookies.
*   **Rollback Stability**: Cleaned up the project structure to ensure a stable build baseline.

## Technical Details
*   **Version Code**: 16790528
*   **Build**: Release
