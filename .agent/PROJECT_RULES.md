# Exclusive TV Project Rules

These rules are permanent and must be followed by every agent working on this project.

## 1. Public Repository Exposure (`Exclusive-TV-Updates`)
- **User-Centric**: The [Exclusive-TV-Updates](https://github.com/CodesRahul96/Exclusive-TV-Updates) repository must serve as a **marketing and download portal**.
- **No Technical Spoilers**: Never explain how `version.json` works, the dual-update mechanism, or internal API structures in the public README.
- **Content**: Focus on Features, Screenshots, and simple Installation Guide for end-users.

## 2. Update Enforcement (Strict Blocking)
- **Mandatory Updates**: If a new version is available, the app **must** be unusable.
- **Enforcement Layer**: Use `SecurityInterceptor` to block ALL network requests (Playlists, EPG, etc.) when `isAppOutdated` is true.
- **Cache Destruction**: Call `TVList.clear()` to delete local `channels.txt` when an update is detected to prevent offline usage of old versions.

## 3. Dual Repository Release Synchronization
- **Version Parity**: When releasing a new version, `version.json` **must** be updated and pushed to both the primary (`Exclusive-TV-APP`) and fallback (`Exclusive-TV-Updates`) repositories immediately.
- **Scripted Success**: Use the provided `release-dual.ps1` script whenever possible to ensure atomic updates across both repositories.

## 4. Repository Hygiene
- **No Artifacts**: Never commit `.apk`, `.jks`, or `.txt` logs to any repository.
- **Gitignore Maintenance**: Ensure `*.apk`, `*.log`, and `debug_build_log_*.txt` are always in `.gitignore`.
- **Clean File List**: The GitHub file list should look clean; APKs belong in the "Releases" section only.

---
*Last modified: January 20, 2026*
