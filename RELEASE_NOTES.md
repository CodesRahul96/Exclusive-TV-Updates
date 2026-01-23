# ExclusiveTV App v1.0.45

This release introduces significant UI improvements and a critical bug fix for playlist management.

## 🚀 New Features & Improvements
*   **Professional Update Checker**: Replaced the plain text loading screen with a modern, glassmorphism-styled dialog (`dialog_checking.xml`) featuring a smooth spinner and better error handling.
*   **Enhanced Factory Reset**: Completely refactored the factory reset experience. It now uses a custom styled warning dialog and performs data wiping in the background with a "Resetting..." progress indicator to prevent UI freezing.
*   **Info Card UI Polish**: Centered the text in the Audio and Video quality badges for a cleaner, more professional look.

## 🐛 Bug Fixes
*   **Playlist Persistence**: Fixed an annoying issue where removed playlists (like the "Eagle" playlist) would reappear after restarting the app. Added a robust sanitization check on startup to permanently remove legacy config entries.

## Technical Details
*   **Version Code**: 16788736
*   **Build**: Release
