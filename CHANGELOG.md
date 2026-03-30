# Changelog
All notable changes to this project will be documented in this file.

## [1.1.9] - 2026-03-30
### Added
- **Instant Audio Switching**: Real-time track switching from settings without stream restart.
- **Improved Watchdog**: Automatic UA rotation on black-screen stalls.

### Fixed
- **Quality Adaptation**: Enforced **480p Max** and **1.0 Mbps** bitrate for "Data Saver" mode.
- **Root False Positive**: Resolved incorrect "Rooted Device" detection on Android TV hardware (MT5862/MT9255L).
- **SunNxt DRM**: Fixed "BAD_VALUE" errors on Android 14 devices.
- **Track Selection**: Sync implementation for Aspect Ratio (Resize) and Bitrate logic.

### Changed
- Refined player overlay indicators for quality and language.

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
