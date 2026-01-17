# Setting Up Fallback Update Repository

This guide explains how to set up the `Exclusive-TV-Updates` repository for the fallback update mechanism.

## 📋 Repository Setup

### 1. Create the Fallback Repository

1. Go to [GitHub](https://github.com/new)
2. Create a new **public** repository named: `Exclusive-TV-Updates`
3. Add a description: "Public update repository for Exclusive TV app"
4. Initialize with a README (optional)

### 2. Repository Structure

Your fallback repository should have this structure:

```
Exclusive-TV-Updates/
├── main/
│   └── version.json          # Version information
└── releases/
    ├── v1.0.31/
    │   └── ExclusiveTV-v1.0.31.apk
    ├── v1.0.32/
    │   └── ExclusiveTV-v1.0.32.apk
    └── ...
```

### 3. Create version.json

In the `main` branch, create a file named `version.json` with this content:

```json
{
  "version_code": 16785152,
  "version_name": "v1.0.31"
}
```

**Important**: This file must be in the root of the `main` branch.

### 4. Upload APK Files

For each release:

1. Go to **Releases** → **Create a new release**
2. Tag version: `v1.0.31` (match the version_name)
3. Release title: `ExclusiveTV v1.0.31`
4. Upload the APK file named: `ExclusiveTV-v1.0.31.apk`
5. Publish release

## 🔄 Update Workflow

When releasing a new version:

### Step 1: Update version.json in Fallback Repo
```bash
cd Exclusive-TV-Updates
# Edit version.json
{
  "version_code": 16785408,  # Increment by 256
  "version_name": "v1.0.32"
}
git add version.json
git commit -m "Update to v1.0.32"
git push origin main
```

### Step 2: Create GitHub Release in Fallback Repo
1. Go to Releases → New Release
2. Tag: `v1.0.32`
3. Title: `ExclusiveTV v1.0.32`
4. Upload: `ExclusiveTV-v1.0.32.apk`
5. Publish

### Step 3: (Optional) Update Main Private Repo
If your main repository is still accessible, update it the same way to keep both in sync.

## 🧪 Testing the Fallback

### Test Scenario 1: Both Sources Available
- App should use **PRIMARY** source
- Check logs: "Successfully fetched from PRIMARY source"

### Test Scenario 2: Primary Unavailable
- Make main repo private (or temporarily block access)
- App should automatically use **FALLBACK** source
- Check logs: "Primary source failed, trying FALLBACK source..."
- Check logs: "Successfully fetched from FALLBACK source"

### Test Scenario 3: Both Unavailable
- App should show error: "Failed to obtain version"
- Check logs: "Both PRIMARY and FALLBACK sources failed"

## 📝 Version Code Calculation

Version codes increment by 256 for each minor version:
- v1.0.31 = 16785152 (0x01001F00)
- v1.0.32 = 16785408 (0x01002000)
- v1.0.33 = 16785664 (0x01002100)

Formula: `(major << 24) | (minor << 16) | (patch << 8)`

## 🔐 Security Considerations

### What to Include in Fallback Repo
✅ version.json
✅ APK files (in Releases)
✅ README.md (optional)

### What NOT to Include
❌ Source code
❌ Signing keys
❌ API keys or secrets
❌ Build configurations

## 🚀 Automation (Optional)

You can automate the update process using GitHub Actions:

```yaml
# .github/workflows/sync-version.yml
name: Sync Version to Fallback

on:
  release:
    types: [published]

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - name: Update fallback repo
        run: |
          # Script to update version.json in fallback repo
          # and create matching release
```

## 📞 Support

If you encounter issues:
1. Check that `version.json` is in the root of `main` branch
2. Verify APK filename matches: `ExclusiveTV-{version_name}.apk`
3. Ensure release tag matches `version_name` in JSON
4. Check repository is public and accessible

---

## Quick Reference

**Fallback Repository URL**: `https://github.com/CodesRahul96/Exclusive-TV-Updates`

**version.json URL**: `https://raw.githubusercontent.com/CodesRahul96/Exclusive-TV-Updates/main/version.json`

**APK Download URL Pattern**: `https://github.com/CodesRahul96/Exclusive-TV-Updates/releases/download/{version_name}/ExclusiveTV-{version_name}.apk`
