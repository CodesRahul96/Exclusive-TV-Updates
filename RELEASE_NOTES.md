# ExclusiveTV v1.1.6

## 🚀 Features & Enhancements
- **Advanced Player Controls**: Integrated gesture zones for intuitive navigation and playback control.
- **Settings Synchronization**: Implemented Firestore settings sync to maintain user preferences across devices.
- **Sleep Timer**: Added sleep timer functionality with cloud synchronization.
- **UI/UX Polish**: 
    - Refined UI consistency with stability guardrails.
    - Mobile-optimized information cards for better readability.
    - Added quick-access shortcut: Hold Right Arrow (3s) to open Settings.
- **Performance Optimization**: Removed developer log overhead and optimized background data handling.

## 🛠 Parser & Backend Improvements
- **Resilient Metadata Parsing**: Enhanced M3U and JSON parsers with Base64 decoding and #EXT-X-KEY support.
- **Advanced Architecture**: Introduced `ParserFactory` and expanded alias sets for superior compatibility with JioTV, Kodi, and other formats.
- **Parser Resilience**: Robust Regex fallbacks and improved query header support for varied playlist sources.

## 🐛 Bug Fixes & Stability
- Corrected audio track synchronization during playback.
- Fixed last-channel state persistence and sync logic.
- Comprehensive cleanup of legacy log artifacts and repository state.
