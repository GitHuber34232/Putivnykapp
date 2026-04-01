#!/bin/sh
set -eu

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd -P)

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen is required. Install it with: brew install xcodegen" >&2
  exit 1
fi

cd "$ROOT_DIR"
./gradlew :shared:assemblePutivnykSharedDebugXCFramework --no-daemon

cd "$ROOT_DIR/iosApp"
xcodegen generate

xcodebuild \
  -project PutivnykIOS.xcodeproj \
  -scheme PutivnykIOS \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  build