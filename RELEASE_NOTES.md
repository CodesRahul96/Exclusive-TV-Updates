# ExclusiveTV v1.0.65 - Major Update & UI Refinement

## ✨ Highlights

### 🚀 In-App Update Engine

- **Seamless Updates**: Implemented a robust in-app update system for one-tap installation directly within the app.
- **Download Guard**: Added a safeguard to prevent multiple simultaneous downloads, ensuring bandwidth efficiency and UI consistency.
- **Dual-Source Reliability**: Leverages both primary and fallback hosts to ensure update availability even during outages.
- **Real-time Feedback**: Includes a premium-styled download progress bar with glassmorphism effects.

### 🎨 UI/UX Refinement

- **Group Filtering**: Automatically hides empty or "Uncategorized" channel groups from the side menu. Group-less channels remain easily accessible via "All channels".
- **Improved Focus Handling**: Fixed D-pad focus issues on older Android TV versions and optimized overall navigation smoothness.

### 🛠️ Performance & Stability

- **Parser Optimization**: Enhanced `KodiParser` and other parsers for faster playlist loading and better memory management.
- **DASH Stream Reliability**: Improved handling of DASH/MPD streams for Star and JioCinema playback.
- **Startup Guard**: Optimized startup sequence to prevent crashes on Fire TV and low-end devices.
