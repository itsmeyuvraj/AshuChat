#!/usr/bin/env bash
set -e

echo "=========================================="
echo "    🚀 AshuChat Release & Update Publisher"
echo "=========================================="

# Build release/debug APK
./gradlew assembleDebug

# Copy to root
cp app/build/outputs/apk/debug/app-debug.apk AshuChat.apk

echo "✓ Built APK: AshuChat.apk"

# Prompt for version tag
read -p "Enter version name (e.g. v1.0.1): " VERSION_NAME
if [ -z "$VERSION_NAME" ]; then
    echo "Version name cannot be empty."
    exit 1
fi

read -p "Enter release notes / changelog: " RELEASE_NOTES
if [ -z "$RELEASE_NOTES" ]; then
    RELEASE_NOTES="Bug fixes and performance improvements."
fi

# Check if gh CLI is available
if command -v gh &> /dev/null; then
    echo "Creating GitHub Release $VERSION_NAME..."
    gh release create "$VERSION_NAME" AshuChat.apk --title "$VERSION_NAME" --notes "$RELEASE_NOTES" || true
    DOWNLOAD_URL="https://github.com/itsmeyuvraj/AshuChat/releases/download/$VERSION_NAME/AshuChat.apk"
else
    DOWNLOAD_URL="https://github.com/itsmeyuvraj/AshuChat/releases/download/$VERSION_NAME/AshuChat.apk"
    echo "Tip: Install gh (GitHub CLI) to auto-upload to GitHub Releases with one command."
fi

# Also publish to Firebase Realtime Database
FIREBASE_URL="https://ashuchat-51b3e-default-rtdb.asia-southeast1.firebasedatabase.app/app_update.json"
echo "Publishing update metadata to Firebase ($FIREBASE_URL)..."

PAYLOAD=$(cat <<EOF
{
  "versionName": "$VERSION_NAME",
  "releaseNotes": "$RELEASE_NOTES",
  "downloadUrl": "$DOWNLOAD_URL",
  "timestamp": $(date +%s%3N)
}
EOF
)

curl -s -X PUT -H "Content-Type: application/json" -d "$PAYLOAD" "$FIREBASE_URL" > /dev/null

echo "=========================================="
echo "🎉 Update $VERSION_NAME published successfully!"
echo "AshuChat will automatically notify users and allow one-tap in-app updating."
echo "=========================================="
