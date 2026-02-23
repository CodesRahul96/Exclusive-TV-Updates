# ExclusiveTV v1.1.1 - Stability & EPG Fixes

## ✨ Highlights

### 📺 EPG Accuracy

- **Fixed EPG Matching**: Corrected matching logic to ensure EPG data appears even if the user renames a channel. It now uses immutable M3U identifiers for lookups.
- **Improved EPG Fetching**: Switched to a more general EPG source for better coverage.

### 🧭 Navigation & UX Improvements

- **Fragment Management**: Updated back-navigation to correctly dismiss the Channel Info Card and Channel Entry overlays before exiting the app.
- **Enhanced Loaders**: Optimized the loader timeout and synchronization for faster visual feedback.

### 🚀 Under-the-Hood Optimizations

- **Playlist Handling**: Audited `TVList` for OOM safety; large M3U/JSON playlists are now handled via high-performance async streaming.
- **ExoPlayer Tuning**: Refined buffer settings for low-latency playback while maintaining anti-freeze stability on FireTV devices.
- **Lifecycle Auditing**: Verified all observers and handlers are properly cleared to prevent long-term memory leaks.

---

# ExclusiveTV v1.0.67 - Premium Aesthetics & Privacy

## ✨ Highlights

### 🎨 Premium Glass UI

- **Glassmorphism Design**: Implemented sleek, translucent UI elements across the app for a modern, premium feel.
- **Micro-Animations**: Added smooth transitions and hover effects to enhance navigation responsiveness.
- **Dynamic Backdrop**: The UI now adapts subtley to the active content.

### 🔒 Exclusive Source Mode

- **Privacy Focused**: Added a toggle for "Exclusive Mode" which allows loading _only_ user-provided custom playlists.
- **Clean Interface**: When active, all default system channels and default APIs are completely hidden, ensuring a distraction-free experience.
- **Improved Source Management**: Simplified adding and removing remote M3U/JSON sources in the Settings menu.

### 🚀 Performance & Stability (v1.0.65 - v1.0.67)

- **Splash Screen**: Added a high-fidelity animated splash screen for a smoother startup experience.
- **Navigation Displacement Fix**: Resolved issues where channel indexing would drift during long sessions or after filtering.
- **Stream Stability**: Enhanced DASH/HLS buffer handling for 4K streams on high-end Android TV devices.
- **In-App Update Engine**: Seamless one-tap updates with progress tracking and glassmorphism styling.

---

# ExclusiveTV v1.0.65 - Major Update & UI Refinement

## ✨ Highlights

### 🚀 In-App Update Engine

...
