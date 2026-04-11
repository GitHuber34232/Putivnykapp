#!/usr/bin/env bash

set -euo pipefail

APP_HOME="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
export GRADLEW_APP_BASE_NAME="${0##*/}"

exec "$APP_HOME/gradlew.sh" "$@"