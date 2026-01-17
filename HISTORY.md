# Exclusive TV - Version History

## v1.0.31 (2026-01-17) - Performance & Stability Update

### 🚀 New Features
- **Factory Reset Enhancement**: Complete data/cache/settings wipe with confirmation dialog and auto-restart
- **Server Status Display**: Real-time server IP and port display in Settings
- **App Version Display**: Current version shown in Settings page

### ⚡ Performance Improvements
- **DiffUtil Implementation**: Smooth list animations for channels and categories
- **Memory Leak Prevention**: Proper listener lifecycle management in RecyclerView
- **Multiple Listener Support**: Fixed critical SharedPreferences observer bug

### 🎨 UX Enhancements
- **Extended Auto-Hide Timeouts**: Settings (30s), Audio Track (30s), Menu (15s)
- **Robust Interaction Detection**: Any key/touch resets auto-hide timers
- **Centralized Timer Logic**: Prevents race conditions during navigation

### 🛠️ Code Quality
- **Modernized Lifecycle**: Replaced deprecated `onActivityCreated()` with `onViewCreated()`
- **Code Cleanup**: Removed 100+ lines of commented-out legacy code
- **Better Maintainability**: Following modern Android best practices

### 🐛 Bug Fixes
- Fixed "Server: offline" display issue
- Resolved settings update propagation bug
- Corrected auto-hide timer behavior
- Fixed potential memory leaks

---

## v1.0.30 (Previous Release)

### EPG Integration
- Electronic Program Guide with current and upcoming show information
- Auto-refresh EPG data every 6 hours
- Channel-specific program matching

### Channel Management
- Drag-and-drop channel reordering
- Category reordering support
- Channel and category renaming
- Persistent order/rename preferences

---

## v1.0.28 - Force Update & Stability

### 🚀 Force Update Mechanism
- Mandatory update system ensures all users on latest version
- Improved security and stability

### ⚡ Performance Overhaul
- Tuned ExoPlayer buffering for instant channel zapping
- Smoother playback experience

### 🔄 Auto-Retry System
- Automatically recovers from network drops (up to 10 retries)
- WakeLock integration prevents device sleep

### 🎨 UI Improvements
- Revamped Settings menu with dedicated "Check Update" button
- Audio Track selection highlights focused item in RED
- Modernized dialogs with glassmorphism effects

### 🛠️ Bug Fixes
- Fixed APK installation issues on certain Android TV devices
- Resolved "Dead Channel" detection false positives

---

## v1.0.7 - Playback Optimization

### Improvements
- Optimized video playback engine
- Added new channels
- Enhanced stream compatibility

---

## v1.0.6 - Compatibility Update

### Improvements
- Configuration URL compatibility handling
- Mobile device UI compatibility fixes
- Fixed non-continuous group sorting issue

---

## v1.0.5 - UI & Stability

### New Features
- Added new channels
- Fixed playback issues on certain TV devices

### UI Improvements
- Optimized channel list styling
- Left key no longer exits channel list

---

## v1.0.4 - Channel Expansion

### Improvements
- Added new channels
- Improved channel discovery

---

## v1.0.3 - Initial Optimization

### Improvements
- Added new channels
- UI/UX optimizations
- Performance improvements

---

## v1.0.0 - Initial Release

### Core Features
- Basic video playback
- Channel list management
- ExoPlayer integration
- Android TV optimization

---

## 📊 Statistics

- **Total Releases**: 10+
- **Active Users**: Growing
- **Supported Devices**: Android TV (API 21+)
- **Languages**: English, Chinese

---

## 🔮 Upcoming Features

- [ ] Multi-language EPG support
- [ ] Cloud sync for favorites and settings
- [ ] Picture-in-Picture mode
- [ ] Parental controls
- [ ] Advanced search and filtering

---

*For detailed release notes, see [RELEASE_NOTES.md](RELEASE_NOTES.md)*