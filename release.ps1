
# release.ps1
# Usage: .\release.ps1 v1.0.X

param (
    [Parameter(Mandatory=$true)]
    [string]$Version
)

# Start Release Process
Write-Host "============================================="
Write-Host "  Preparing Release: $Version"
Write-Host "============================================="

# 1. Generate version.json content (Don't commit yet)
Write-Host "--> Generating version.json..."

# Parse version (v1.0.9 -> 1 0 9)
$v = $Version -replace "v", ""
$parts = $v.Split(".")
if ($parts.Length -lt 3) {
    Write-Error "Invalid version format. Expected vX.Y.Z"
    exit 1
}

$v1 = [int]$parts[0]
$v2 = [int]$parts[1]
$v3 = [int]$parts[2]
$v4 = 0
if ($parts.Length -ge 4) { $v4 = [int]$parts[3] }

$versionCode = ($v1 * 16777216) + ($v2 * 65536) + ($v3 * 256) + $v4
$jsonContent = '{"version_code": ' + $versionCode + ', "version_name": "' + $Version + '"}'

# Write version.json
$jsonContent | Out-File -FilePath "version.json" -Encoding ascii
Write-Host "version.json generated."
Get-Content version.json
Write-Host ""

# 2. Build APK (Before Tagging)
Write-Host "--> Building Release APK (Pre-tag check)..."
if (Test-Path ".\gradlew.bat") {
    # Pass overrides so build gets correct version without git tag
    # Pass overrides so build gets correct version without git tag
    $gradleArgs = @("assembleRelease", "-PversionCodeOverride=$versionCode", "-PversionNameOverride=$Version")
    & .\gradlew.bat $gradleArgs
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Build Successful."
    } else {
        Write-Error "Build Failed. Aborting release (Tag not created)."
        exit 1
    }
} else {
    Write-Error "Error: .\gradlew.bat not found."
    exit 1
}

# 3. Git Operations (Commit and Tag)
Write-Host "--> Committing and Tagging..."
git add version.json
git commit -m "Release $Version"

# Tagging
git tag $Version
if ($LASTEXITCODE -eq 0) {
    Write-Host "Tag $Version created."
} else {
    Write-Error "Error creating tag. It might already exist."
    exit 1
}

# Push
$currentBranch = git rev-parse --abbrev-ref HEAD
Write-Host "--> Pushing to origin/$currentBranch..."
git push origin $currentBranch
git push origin $Version

# 4. Prepare Artifact
$apkPath = "app\build\outputs\apk\release\app-release.apk"
$targetName = "ExclusiveTV-$Version.apk"

if (Test-Path $apkPath) {
    Copy-Item -Path $apkPath -Destination ".\$targetName"
    Write-Host "--> APK copied to .\$targetName"
} else {
    Write-Error "Error: APK not found at $apkPath"
    exit 1
}

# 5. Create GitHub Release
Write-Host "--> Checking for GitHub CLI..."
if (Get-Command "gh" -ErrorAction SilentlyContinue) {
    Write-Host "Creating GitHub Release..."

    $notes = ""
    # Optional history generation notes...
    
    if ([string]::IsNullOrEmpty($notes)) {
        $notes = "Release $Version"
    }

    gh release create "$Version" "$targetName" --title "$Version" --notes "$notes"
    Write-Host "Release $Version published to GitHub!"
} else {
    Write-Host "GitHub CLI (gh) not found."
    Write-Host "DONE. Please manually upload '$targetName' to GitHub Releases."
}
