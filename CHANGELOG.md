# Changelog
All notable changes to this project will be documented in this file.

## [1.2.0] - 2026-04-03
### Added
- **AI Voice Search**: Integrated user toggle in Settings with persistent storage and microphone UI support.
- **Improved Registration**: Resolved Firestore "Permission Denied" errors and optimized registration flow for all devices.
- **Settings UI & Community**:
    - Added **Official Telegram Group** link for group support and updates.
    - Relocated "Official App Website" to the new "Community & Support" section.
- **Session & Security**:
    - Fixed **Logout** and **Factory Reset** logic.
    - Ensured full data erasure (including encrypted preferences) before app restart.
- **FireTV Optimization**: Enhanced D-Pad navigation and display support for MediaTek-based TV hardware.

### Fixed
- **Stability**: Resolved random crashes during long viewing sessions.
- **Connectivity**: Improved stream loading speed and fallback handling.
- **Security Protocols**: Updated device integrity checks and security verification.

### Changed
- **Mandatory Update**: Implemented a non-bypassable update mechanism to ensure production stability.

## [1.1.8] - 2026-03-28
### Added
- Unified Bitrate & Quality Priority settings.
- Custom User-Agent rotation for blocked streams.

### Fixed
- D-Pad navigation improvements for UI focus.

## [1.1.7] - 2026-03-26
### Added
- 7-Day Premium Trial with device fingerprinting.
- Google Play Integrity API integration.
- HLS hinting for `.php` and `.aspx` streams.

### Changed
- Refactored M3U parser for 2x faster channel loading.
- Optimized Category Modal layout for accessibility.

### Fixed
- Manifest handling for universal dynamic streams.
- UnsatisfiedLinkError on legacy Android 9 devices.
