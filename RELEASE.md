# Exclusive TV Release Guide

Follow these instructions to build and publish a new version of **Exclusive TV**.

## Automated Release (Recommended)

### Prerequisites
1. **GitHub CLI**: [Install GitHub CLI](https://cli.github.com/) and run `gh auth login`.
2. **Signing Setup**: Ensure `keystore.properties` is present in the root directory.

### Build and Publish
Run the PowerShell script:
```powershell
.\release.ps1 -Version v1.0.10
```
This script will:
- Generate version info
- Commit and tag the release
- Build the signed APK
- Upload to GitHub Both Releases

---

## Manual Release Process

### 1. Update Version Info
Update `version.json` with the new version name and code.
- **version_name**: e.g., `"v1.0.10"`
- **version_code**: Increment the previous integer.

### 2. Commit and Tag
```bash
git add .
git commit -m "Release v1.0.10"
git tag v1.0.10
git push origin main --tags
```

### 3. Build Signed APK
In Android Studio:
1. **Build** > **Generate Signed Bundle / APK**.
2. Select **APK** > **Next**.
3. Use your keystore details.
4. Select **release** variant > **Finish**.

### 4. Publish to GitHub
1. Go to: [Exclusive-TV-APP Releases](https://github.com/CodesRahul96/Exclusive-TV-APP/releases)
2. Click **Draft a new release**.
3. Choose the tag (e.g., `v1.0.10`).
4. **IMPORTANT**: Rename your built APK to `ExclusiveTV-v1.0.10.apk`.
5. Attach the APK and click **Publish release**.

---

## Verification
1. Open an older version of the app.
2. Go to **Settings** > **Update app**.
3. Verify it detects and downloads the new version correctly.
