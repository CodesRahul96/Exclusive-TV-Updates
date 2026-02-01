# ExclusiveTV App v1.0.56

This release significantly improves the stability and reliability of the in-app update mechanism, especially on older hardware.

## 🐛 Critical Fixes
*   **Update Dialog Crash**: Resolved `java.lang.RuntimeException: Unable to instantiate fragment ConfirmationFragment` (fixed no-argument constructor).
*   **Legacy Compatibility**: Fixed a crash on Android 12 and below when registering update-related system events.
*   **Robust Networking**: Fixed potential "broken link" issues when processing update URLs from diverse servers.
*   **Direct Update Restored**: Re-implemented the direct download and install mechanism for in-app updates.
*   **Progress Feedback**: Enabled real-time download progress updates.

## 🚀 Key Features (from v1.0.55/56)
*   **Update Server Connection**: Improved compatibility with older Android TV boxes using `UnsafeHttpClient`.
*   **Mobile UI Optimization**: Redesigned dialogs and list items for better mobile compatibility.

## 🚀 Key Features (from v1.0.54)
*   **Mobile UI Optimization**: Redesigned dialogs and list items for better mobile compatibility.
*   **Favorites System**: Fixed "My Collection" persistence and display logic.
*   **Visual Polish**: Compact buttons and smoother animations.

## Technical Details
*   **Version Code**: 16791041
*   **Build**: Debug
