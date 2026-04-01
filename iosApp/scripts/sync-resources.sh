#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd -P)
IOS_APP_DIR=$(cd "$SCRIPT_DIR/.." && pwd -P)
ROOT_DIR=$(cd "$IOS_APP_DIR/.." && pwd -P)
SOURCE_ASSETS_DIR="$ROOT_DIR/app/src/main/assets"
TARGET_RESOURCES_DIR="$IOS_APP_DIR/PutivnykIOS/Resources"

mkdir -p "$TARGET_RESOURCES_DIR"

cp "$SOURCE_ASSETS_DIR/kyiv_tourist_places.json" "$TARGET_RESOURCES_DIR/"
cp "$SOURCE_ASSETS_DIR/kyiv_extra_seed.json" "$TARGET_RESOURCES_DIR/"
find "$SOURCE_ASSETS_DIR/i18n" -maxdepth 1 -type f -name 'ui_*.json' -exec cp {} "$TARGET_RESOURCES_DIR/" \;