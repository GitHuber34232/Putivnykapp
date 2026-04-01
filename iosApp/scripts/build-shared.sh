#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd -P)
IOS_APP_DIR=$(cd "$SCRIPT_DIR/.." && pwd -P)
ROOT_DIR=$(cd "$IOS_APP_DIR/.." && pwd -P)

CONFIGURATION_NAME=${CONFIGURATION:-Debug}
CONFIGURATION_LOWER=$(printf '%s' "$CONFIGURATION_NAME" | tr '[:upper:]' '[:lower:]')

if [ "$CONFIGURATION_NAME" = "Release" ]; then
  GRADLE_TASK=":shared:assemblePutivnykSharedReleaseXCFramework"
else
  GRADLE_TASK=":shared:assemblePutivnykSharedDebugXCFramework"
fi

"$ROOT_DIR/gradlew" "$GRADLE_TASK" --no-daemon

SOURCE_FRAMEWORK="$ROOT_DIR/shared/build/XCFrameworks/$CONFIGURATION_LOWER/PutivnykShared.xcframework"
TARGET_FRAMEWORK="$IOS_APP_DIR/Frameworks/PutivnykShared.xcframework"

mkdir -p "$IOS_APP_DIR/Frameworks"
rm -rf "$TARGET_FRAMEWORK"
cp -R "$SOURCE_FRAMEWORK" "$TARGET_FRAMEWORK"