---
description: Push latest code changes to GitHub without creating a release or building an APK
---

# Push Changes to GitHub

This workflow commits and pushes the current code to the repository.

1. **Check Status**
   - Run `git status` to see pending changes.

2. **Stage and Commit**
   - Run `git add .` (or specific files).
   - Run `git commit -m "Your commit message"` (Ask user for message if not provided).

3. **Push**
   - Run `git push origin <branch_name>` (usually `main`).

**IMPORTANT**: Do NOT run `release.ps1` or create git tags/releases unless explicitly asked to "Release APK".
