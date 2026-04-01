#!/bin/sh
set -eu

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd -P)

cd "$ROOT_DIR"
./gradlew :shared:assemblePutivnykSharedReleaseXCFramework --no-daemon

echo "Release XCFramework is ready under shared/build/XCFrameworks/release/PutivnykShared.xcframework"