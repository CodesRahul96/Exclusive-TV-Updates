# Exclusive TV - Updates Repository

This is the **public updates repository** for the Exclusive TV Android application. It contains version information and release APK files.

## 📦 Purpose

This repository serves as a **fallback update source** for the Exclusive TV app when the main repository is private. The app automatically checks this repository if the primary source is unavailable.

## 📁 Repository Structure

```
Exclusive-TV-Updates/
├── version.json          # Current version information
└── releases/             # GitHub Releases contain APK files
    ├── v1.0.31/
    │   └── ExclusiveTV-v1.0.31.apk
    ├── v1.0.32/
    │   └── ExclusiveTV-v1.0.32.apk
    └── ...
```

## 🔄 Update Mechanism

The Exclusive TV app uses a **dual-source update system**:

1. **Primary Source**: Main repository (may be private)
2. **Fallback Source**: This repository (always public)

If the primary source fails, the app automatically falls back to this repository.

## 📝 version.json Format

```json
{
  "version_code": 16785152,
  "version_name": "v1.0.31"
}
```

- **version_code**: Integer version code (increments by 256 for each minor version)
- **version_name**: Semantic version string (e.g., "v1.0.31")

## 🚀 Release Process

When releasing a new version:

1. **Update version.json** in the main branch
2. **Create a GitHub Release** with tag matching `version_name`
3. **Upload APK** named `ExclusiveTV-{version_name}.apk`

### Example Release

- **Tag**: `v1.0.32`
- **Title**: `ExclusiveTV v1.0.32`
- **APK**: `ExclusiveTV-v1.0.32.apk`

## 🔗 URLs

- **version.json**: `https://raw.githubusercontent.com/CodesRahul96/Exclusive-TV-Updates/main/version.json`
- **APK Download**: `https://github.com/CodesRahul96/Exclusive-TV-Updates/releases/download/{version}/ExclusiveTV-{version}.apk`

## 📱 App Repository

The main application source code is hosted at: [Exclusive-TV-APP](https://github.com/CodesRahul96/Exclusive-TV-APP) (may be private)

## 🔐 Security

This repository contains **only**:
- ✅ version.json (version information)
- ✅ APK files (in GitHub Releases)
- ✅ Documentation

It does **NOT** contain:
- ❌ Source code
- ❌ Signing keys
- ❌ API keys or secrets

## 📄 License

This repository follows the same license as the main Exclusive TV application.

---

*Maintained by CodesRahul & The Exclusive TV Team*
