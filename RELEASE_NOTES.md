# ExclusiveTV v1.2.4 - Official Audio Stability Update

This update introduces professional audio management and stabilizes the rendering pipeline for a smoother TV experience.

### ✨ What's New (Audio & Dolby)
- **Dolby Audio Support (Toggle)**: Added a new setting for **Surround Passthrough**.
  - **ON**: Prioritizes multi-channel audio for Home Theaters/AVRs.
  - **OFF**: High-quality **Smart Stereo Downmixing** for standard TV speakers.
- **Pro Audio Track Logic**: Re-engineered track selection to be metadata-aware.
  - **Detailed Labels**: Audio tracks now show high-fidelity data: `[Hindi] Stereo (AAC)` or `[English] 5.1 Surround (AC3)`.
  - **Smart Language Preference**: Your favorite language is now automatically applied across all channels.
  - **Auto-Priority**: The app now automatically selects the best audio format based on your Dolby settings.

### 🛡️ System Stability & Performance
- **Flicker Fix**: Eliminated screen flickering when opening UI menus during playback by restoring universal Z-Order stability.
- **Lag Reduction**: Optimized the background channel checker to reduce system resource usage and prevent UI stutters.
- **Renderer Safeguard**: Fixed critical `ThreadedRenderer` timeouts that previously led to app crashes on certain hardware.
- **Persistence Fix**: Resolved the issue where channels would disappear after deleting a custom playlist.

### ⚙️ Optimization
- **Gapless Logic**: Improved audio track switching to be seamless and non-destructive.
- **Build Compression**: Optimized APK packaging to maintain a compact footprint for fast downloads.

---
**Build #16934656** - v1.2.4
