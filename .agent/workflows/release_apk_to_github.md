---
description: Build APK, Tag Version, and Release to GitHub
---

# Release APK to GitHub

This workflow builds the APK, tags the version, and publishes a release to GitHub.

**CRITICAL**: Only run this when the user explicitly asks to "Release APK" or "Publish Release".

1. **Run Release Script**
   - Run `.\release.ps1 <version_tag>` (e.g., `.\release.ps1 v1.0.27`).
   - This script generates `version.json`, builds the signed APK, creates a git tag, pushes tags, and uses `gh` CLI to create a release.

2. **Verify**
   - Check GitHub to ensure the release is published with the APK asset.
