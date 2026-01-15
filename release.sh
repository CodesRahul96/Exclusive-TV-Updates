#!/bin/bash

# release.sh
# Usage: ./release.sh v1.0.X

if [ -z "$1" ]; then
    echo "Usage: ./release.sh v1.0.X"
    exit 1
fi

VERSION=$1

echo "============================================="
echo "  Preparing Release: $VERSION"
echo "============================================="

# 1. Generate version.json
echo "--> Generating version.json..."
# Logic to calculate version code
# Remove 'v'
V_NUM=${VERSION#v}
IFS='.' read -r -a parts <<< "$V_NUM"
V1=${parts[0]}
V2=${parts[1]}
V3=${parts[2]}
# Make sure they are numbers
V1=$((V1 + 0))
V2=$((V2 + 0))
V3=$((V3 + 0))

# Calculate versionCode matches kotlin/powershell logic
VERSION_CODE=$(( (V1 * 16777216) + (V2 * 65536) + (V3 * 256) ))

echo "{\"version_code\": $VERSION_CODE, \"version_name\": \"$VERSION\"}" > version.json

if [ ! -f "version.json" ]; then
    echo "Error: Failed to generate version.json"
    exit 1
fi

cat version.json
echo ""

# 2. Build APK (Before Tagging)
echo "--> Building Release APK..."
if [ -f "./gradlew" ]; then
    # Pass overrides
    ./gradlew assembleRelease -PversionCodeOverride=$VERSION_CODE -PversionNameOverride=$VERSION
    if [ $? -eq 0 ]; then
        echo "Build Successful."
    else
        echo "Build Failed. Aborting release."
        exit 1
    fi
else
    echo "Error: ./gradlew not found."
    exit 1
fi

# 3. Git Operations
echo "--> Committing and Tagging..."
git add version.json
git commit -m "Release $VERSION"

# Tagging
if git tag $VERSION; then
    echo "Tag $VERSION created."
else
    echo "Error creating tag. Check if it already exists."
    exit 1
fi

# Push
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "--> Pushing to origin/$CURRENT_BRANCH..."
git push origin $CURRENT_BRANCH
git push origin $VERSION

# 4. Prepare Artifact
APK_PATH="app/build/outputs/apk/release/app-release.apk"
TARGET_NAME="ExclusiveTV-$VERSION.apk"

if [ -f "$APK_PATH" ]; then
    cp "$APK_PATH" "$TARGET_NAME"
    echo "--> APK copied to ./$TARGET_NAME"
else
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi

# 5. Create GitHub Release
echo "--> Checking for GitHub CLI..."
if command -v gh &> /dev/null; then
    echo "Creating GitHub Release..."
    
    NOTES="Release $VERSION"
    gh release create "$VERSION" "$TARGET_NAME" --title "$VERSION" --notes "$NOTES"
    echo "Release $VERSION published to GitHub!"
else
    echo "GitHub CLI (gh) not found."
    echo "DONE. Please manually upload '$TARGET_NAME' to GitHub Releases."
fi
