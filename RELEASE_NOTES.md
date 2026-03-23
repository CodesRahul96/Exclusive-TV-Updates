# ExclusiveTV v1.1.8

## 🛠️ Enhancements & Bug Fixes
- **Hidden Playlist Option**: The custom playlist URL option in the Settings is now hidden by default to simplify the UI. It can be unlocked by clicking the "CodesRahul" developer info card 10 times consecutively.
- **Improved UI Focus**: Adjusted focus routing when hidden elements are not visible to ensure smooth navigation with D-Pad controls.

# ExclusiveTV v1.1.7

## 🛡️ Security & Anti-Fraud
- **7-Day Premium Trial**: Introduced a secure 7-day trial for new users with device-based anti-fraud protection.
- **Google Play Integrity**: Integrated the Integrity API to ensure the app is running on a genuine, untampered device.
- **Server-Side Registration**: Moved user registration to Firebase Cloud Functions for enhanced security.
- **Advanced Root Detection**: Refined detection logic for better compatibility with Android TV devices.
- **Shadow Banning**: Implemented a stealth banning system to protect premium content.

## 📺 Playback & UI Enhancements
- **Universal Dynamic Streams**: Added HLS hinting for `.php` and `.aspx` streams, ensuring reliable playback across more providers.
- **Refined Category Modal**: Added a native-feeling drag handle and optimized bottom padding for better accessibility.
- **SafeArea Fixes**: Improved layout responsiveness to prevent UI elements from being cut off by system bars.

## 🛠️ Bug Fixes & Stability
- Corrected Enter key interception on the login screen and emulators.
- Fixed native library extraction issues (`UnsatisfiedLinkError`) for broader device support.
- Optimized `EmbeddedPlayer` buffer handling to prevent crashes during long sessions.

