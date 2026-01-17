
# release-dual.ps1
# Enhanced release script for dual-repository deployment
# Usage: .\release-dual.ps1 v1.0.X

param (
    [Parameter(Mandatory = $true)]
    [string]$Version
)

# Configuration
$PRIMARY_REPO = "CodesRahul96/Exclusive-TV-APP"
$FALLBACK_REPO = "CodesRahul96/Exclusive-TV-Updates"

# Start Release Process
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Dual-Repository Release: $Version" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Generate version.json
Write-Host "`n--> Generating version.json..." -ForegroundColor Yellow

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
$jsonContent = "{`"version_code`": $versionCode, `"version_name`": `"$Version`"}"

$jsonContent | Out-File -FilePath "version.json" -Encoding ascii
Write-Host "version.json generated:" -ForegroundColor Green
Get-Content version.json

# 2. Build Release APK
Write-Host "`n--> Building Release APK..." -ForegroundColor Yellow
if (Test-Path ".\gradlew.bat") {
    $gradleArgs = @("assembleRelease", "-PversionCodeOverride=$versionCode", "-PversionNameOverride=$Version")
    & .\gradlew.bat $gradleArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build Failed. Aborting release."
        exit 1
    }
    Write-Host "Build Successful." -ForegroundColor Green
}
else {
    Write-Error "gradlew.bat not found."
    exit 1
}

# 3. Git Operations
Write-Host "`n--> Committing and Tagging..." -ForegroundColor Yellow
git add version.json
git commit -m "Release $Version"

git tag $Version
if ($LASTEXITCODE -ne 0) {
    Write-Error "Error creating tag. It might already exist."
    exit 1
}
Write-Host "Tag $Version created." -ForegroundColor Green

$currentBranch = git rev-parse --abbrev-ref HEAD
Write-Host "--> Pushing to origin/$currentBranch..." -ForegroundColor Yellow
git push origin $currentBranch
git push origin $Version

# 4. Prepare APK
$apkPath = "app\build\outputs\apk\release\app-release.apk"
$targetName = "ExclusiveTV-$Version.apk"

if (Test-Path $apkPath) {
    Copy-Item -Path $apkPath -Destination ".\$targetName"
    Write-Host "APK copied to .\$targetName" -ForegroundColor Green
}
else {
    Write-Error "APK not found at $apkPath"
    exit 1
}

# 5. Check GitHub CLI
if (-not (Test-Path "C:\Program Files\GitHub CLI\gh.exe")) {
    Write-Error "GitHub CLI (gh) not found. Please install it first."
    exit 1
}

$ghExe = "C:\Program Files\GitHub CLI\gh.exe"

# Validate Auth
& $ghExe auth status
if ($LASTEXITCODE -ne 0) {
    Write-Error "GitHub CLI not authenticated. Run 'gh auth login' first."
    exit 1
}

# 6. Release to Primary Repository
Write-Host "`n--> Creating Release on PRIMARY Repository ($PRIMARY_REPO)..." -ForegroundColor Yellow

$notes = "Release $Version"
if (Test-Path "RELEASE_NOTES.md") {
    $notes = Get-Content "RELEASE_NOTES.md" -Raw
}

& $ghExe release create "$Version" "$targetName" --repo $PRIMARY_REPO --title "$Version" --notes "$notes"
if ($LASTEXITCODE -eq 0) {
    Write-Host "PRIMARY release published successfully!" -ForegroundColor Green
}
else {
    Write-Error "Failed to create PRIMARY release."
    exit 1
}

# 7. Release to Fallback Repository
Write-Host "`n--> Creating Release on FALLBACK Repository ($FALLBACK_REPO)..." -ForegroundColor Yellow

& $ghExe release create "$Version" "$targetName" --repo $FALLBACK_REPO --title "$Version" --notes "$notes"
if ($LASTEXITCODE -eq 0) {
    Write-Host "FALLBACK release published successfully!" -ForegroundColor Green
}
else {
    Write-Warning "Failed to create FALLBACK release. Please create it manually."
}

# 8. Update version.json in Fallback Repo
Write-Host "`n--> Updating version.json in FALLBACK repository..." -ForegroundColor Yellow

$fallbackLocalPath = "..\Exclusive-TV-Updates"
if (Test-Path $fallbackLocalPath) {
    Write-Host "Found local fallback repo at $fallbackLocalPath" -ForegroundColor Cyan
    
    # Copy files
    Copy-Item -Path "version.json" -Destination $fallbackLocalPath -Force
    if (Test-Path "RELEASE_NOTES.md") {
        Copy-Item -Path "RELEASE_NOTES.md" -Destination $fallbackLocalPath -Force
    }

    # Commit and Push
    $currentDir = Get-Location
    Set-Location $fallbackLocalPath
    
    try {
        git add version.json RELEASE_NOTES.md
        git commit -m "Update version to $Version"
        git push origin main
        Write-Host "Fallback repository updated successfully." -ForegroundColor Green
    }
    catch {
        Write-Warning "Failed to push to fallback repo: $_"
    }
    finally {
        Set-Location $currentDir
    }

}
else {
    Write-Warning "Fallback repo folder not found at $fallbackLocalPath. Skipping file update."
    Write-Host "You must manually update version.json in $FALLBACK_REPO to ensure updates work!" -ForegroundColor Red
}

# Summary
Write-Host "`n=============================================" -ForegroundColor Cyan
Write-Host "  Release $Version Complete!" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "PRIMARY:  https://github.com/$PRIMARY_REPO/releases/tag/$Version" -ForegroundColor Green
Write-Host "FALLBACK: https://github.com/$FALLBACK_REPO/releases/tag/$Version" -ForegroundColor Green
Write-Host "`nPlease verify both releases are accessible." -ForegroundColor Yellow
