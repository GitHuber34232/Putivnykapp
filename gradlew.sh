#!/usr/bin/env bash

set -Eeuo pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -x
fi

##############################################################################
#
# Gradle startup script for Linux
#
##############################################################################

APP_HOME="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
APP_BASE_NAME="${0##*/}"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
declare -a DEFAULT_JVM_OPTS=("-Xmx64m" "-Xms64m")

local_properties_file="$APP_HOME/local.properties"
default_linux_sdk_dir="${PUTIVNYK_ANDROID_SDK_DIR:-$HOME/Android/Sdk}"
android_cmdline_tools_version="14742923"
android_cmdline_tools_url="https://dl.google.com/android/repository/commandlinetools-linux-${android_cmdline_tools_version}_latest.zip"
android_cmdline_tools_archive="$HOME/.cache/putivnyk/commandlinetools-linux-${android_cmdline_tools_version}_latest.zip"

declare -a required_android_sdk_packages=(
    "platform-tools"
    "platforms;android-35"
    "build-tools;35.0.1"
    "cmake;3.22.1"
    "ndk;28.2.13676358"
)

restore_local_properties=false
backup_local_properties=""
temp_local_properties=""

cleanup() {
    local exit_code="${1:-$?}"

    trap - EXIT INT TERM

    if [[ "$restore_local_properties" == true ]]; then
        if [[ -n "$backup_local_properties" && -f "$backup_local_properties" ]]; then
            mv "$backup_local_properties" "$local_properties_file"
        else
            rm -f "$local_properties_file"
        fi
    fi

    if [[ -n "$temp_local_properties" ]]; then
        rm -f "$temp_local_properties"
    fi

    exit "$exit_code"
}

trap 'cleanup $?' EXIT
trap 'cleanup 130' INT
trap 'cleanup 143' TERM

fail() {
    printf '\nERROR: %s\n\n' "$1" >&2
    exit 1
}

warn() {
    printf 'gradlew.sh: warning: %s\n' "$1" >&2
}

info() {
    printf 'gradlew.sh: %s\n' "$1" >&2
}

is_truthy() {
    case "${1,,}" in
        1|true|yes|on)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' is not available."
}

resolve_java_home() {
    local resolved_java_bin

    resolved_java_bin="$(readlink -f "$1" 2>/dev/null || printf '%s\n' "$1")"
    if [[ "$resolved_java_bin" == */bin/java ]]; then
        printf '%s\n' "${resolved_java_bin%/bin/java}"
        return 0
    fi

    return 1
}

args_define_gradle_java_home() {
    local arg

    for arg in "$@"; do
        [[ "$arg" == -Dorg.gradle.java.home=* ]] && return 0
    done

    return 1
}

env_define_gradle_java_home() {
    local combined_opts=" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} "
    [[ "$combined_opts" == *" -Dorg.gradle.java.home="* ]]
}

append_shell_words() {
    local array_name="$1"
    local shell_words="$2"

    [[ -n "$shell_words" ]] || return 0

    eval "$array_name+=( $shell_words )"
}

args_require_android_sdk() {
    local arg
    local saw_task=false

    for arg in "$@"; do
        [[ "$arg" == -* ]] && continue
        saw_task=true

        case "$arg" in
            help|tasks|projects|properties|components|dependencyInsight|dependencies)
                ;;
            *)
                return 0
                ;;
        esac
    done

    [[ "$saw_task" == true ]] && return 1
    return 1
}

read_local_properties_sdk_dir() {
    [[ -f "$local_properties_file" ]] || return 1

    local line raw_value
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ "$line" == \#* ]] && continue
        [[ "$line" != sdk.dir=* ]] && continue

        raw_value=${line#sdk.dir=}
        raw_value=${raw_value//\\:/:}
        raw_value=${raw_value//\\\\/\\}
        printf '%s\n' "$raw_value"
        return 0
    done < "$local_properties_file"

    return 1
}

find_linux_sdk_dir() {
    local candidate

    for candidate in \
        "$default_linux_sdk_dir" \
        "${ANDROID_SDK_ROOT:-}" \
        "${ANDROID_HOME:-}" \
        "$HOME/Android/Sdk" \
        "$HOME/Android/sdk" \
        "/opt/android-sdk" \
        "/usr/lib/android-sdk"; do
        if [[ -n "$candidate" && -d "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}

android_sdk_packages_ready() {
    local sdk_dir="$1"

    [[ -d "$sdk_dir/platform-tools" ]] &&
        [[ -d "$sdk_dir/platforms/android-35" ]] &&
        [[ -d "$sdk_dir/build-tools/35.0.1" ]] &&
        [[ -d "$sdk_dir/cmake/3.22.1" ]] &&
        [[ -d "$sdk_dir/ndk/28.2.13676358" ]]
}

bootstrap_android_commandline_tools() {
    local sdk_dir="$1"
    local tools_dir="$sdk_dir/cmdline-tools"
    local latest_tools_dir="$tools_dir/latest"
    local temp_tools_dir="$tools_dir/tmp"

    if [[ -x "$latest_tools_dir/bin/sdkmanager" ]]; then
        return 0
    fi

    require_command curl
    require_command unzip

    mkdir -p "$HOME/.cache/putivnyk" "$tools_dir"

    info "downloading Android command-line tools into $sdk_dir"
    curl -L --fail --show-error "$android_cmdline_tools_url" -o "$android_cmdline_tools_archive"

    rm -rf "$latest_tools_dir" "$temp_tools_dir"
    mkdir -p "$temp_tools_dir"
    unzip -q -o "$android_cmdline_tools_archive" -d "$temp_tools_dir"
    mv "$temp_tools_dir/cmdline-tools" "$latest_tools_dir"
    rmdir "$temp_tools_dir"
}

accept_android_sdk_licenses() {
    local sdk_dir="$1"
    local sdkmanager_bin="$sdk_dir/cmdline-tools/latest/bin/sdkmanager"
    local status=0

    set +o pipefail
    yes | "$sdkmanager_bin" --sdk_root="$sdk_dir" --licenses >/dev/null || status=$?
    set -o pipefail

    if [[ "$status" -ne 0 ]]; then
        fail "Failed to accept Android SDK licenses automatically."
    fi
}

install_required_android_sdk_packages() {
    local sdk_dir="$1"
    local sdkmanager_bin="$sdk_dir/cmdline-tools/latest/bin/sdkmanager"

    if android_sdk_packages_ready "$sdk_dir"; then
        return 0
    fi

    info "installing Android SDK packages required for this project"
    accept_android_sdk_licenses "$sdk_dir"
    "$sdkmanager_bin" --sdk_root="$sdk_dir" "${required_android_sdk_packages[@]}"
}

bootstrap_linux_sdk_if_needed() {
    local sdk_dir="$1"

    bootstrap_android_commandline_tools "$sdk_dir"
    install_required_android_sdk_packages "$sdk_dir"
}

escape_property_value() {
    local value="$1"
    value=${value//\\/\\\\}
    value=${value//:/\\:}
    printf '%s' "$value"
}

write_local_properties_with_sdk() {
    local sdk_dir="$1"
    local replaced=false
    local line

    temp_local_properties="$(mktemp "${TMPDIR:-/tmp}/putivnyk-local.properties.XXXXXX")"

    if [[ -f "$local_properties_file" ]]; then
        backup_local_properties="$(mktemp "${TMPDIR:-/tmp}/putivnyk-local.properties.backup.XXXXXX")"
        cp "$local_properties_file" "$backup_local_properties"

        while IFS= read -r line || [[ -n "$line" ]]; do
            if [[ "$line" == sdk.dir=* ]]; then
                printf 'sdk.dir=%s\n' "$(escape_property_value "$sdk_dir")" >> "$temp_local_properties"
                replaced=true
            else
                printf '%s\n' "$line" >> "$temp_local_properties"
            fi
        done < "$local_properties_file"
    fi

    if [[ "$replaced" == false ]]; then
        printf 'sdk.dir=%s\n' "$(escape_property_value "$sdk_dir")" >> "$temp_local_properties"
    fi

    mv "$temp_local_properties" "$local_properties_file"
    temp_local_properties=""
    restore_local_properties=true
}

prepare_linux_sdk() {
    local configured_sdk linux_sdk sdk_required=false bootstrap_enabled=false

    if args_require_android_sdk "$@"; then
        sdk_required=true
    fi

    if is_truthy "${PUTIVNYK_BOOTSTRAP_ANDROID_SDK:-true}"; then
        bootstrap_enabled=true
    fi

    configured_sdk="$(read_local_properties_sdk_dir || true)"
    if [[ -n "$configured_sdk" && -d "$configured_sdk" ]]; then
        linux_sdk="$configured_sdk"
    else
        linux_sdk="$(find_linux_sdk_dir || true)"
    fi

    if [[ -n "$linux_sdk" && "$sdk_required" == true && "$bootstrap_enabled" == true ]]; then
        bootstrap_linux_sdk_if_needed "$linux_sdk"
    fi

    if [[ -z "$linux_sdk" ]]; then
        if [[ "$sdk_required" == true && "$bootstrap_enabled" == true ]]; then
            linux_sdk="$default_linux_sdk_dir"
            bootstrap_linux_sdk_if_needed "$linux_sdk"
        else
            if [[ -n "$configured_sdk" ]]; then
                warn "local.properties sdk.dir points to missing path: $configured_sdk"
                warn "set ANDROID_SDK_ROOT, PUTIVNYK_ANDROID_SDK_DIR, or allow bootstrap with PUTIVNYK_BOOTSTRAP_ANDROID_SDK=true"
            fi

            return 0
        fi
    fi

    export ANDROID_SDK_ROOT="$linux_sdk"
    export ANDROID_HOME="$linux_sdk"
    if [[ "$configured_sdk" != "$linux_sdk" ]]; then
        write_local_properties_with_sdk "$linux_sdk"
    fi
    info "using Android SDK at $linux_sdk"
}

[[ -f "$CLASSPATH" ]] || fail "Missing Gradle wrapper JAR at $CLASSPATH"

if [[ "$(uname -s)" == "Linux" ]]; then
    prepare_linux_sdk "$@"
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_HOME=${JAVA_HOME%\"}
    JAVA_HOME=${JAVA_HOME#\"}
    JAVACMD="$JAVA_HOME/bin/java"

    if [[ ! -x "$JAVACMD" ]]; then
        fail "JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="$(command -v java || true)"

    if [[ -z "$JAVACMD" ]]; then
        fail "JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

JAVA_HOME_OVERRIDE="$(resolve_java_home "$JAVACMD" || true)"

declare -a GRADLE_JAVA_HOME_ARGS=()
declare -a ENV_JVM_OPTS=()

if [[ -n "$JAVA_HOME_OVERRIDE" && -d "$JAVA_HOME_OVERRIDE" ]] && ! args_define_gradle_java_home "$@" && ! env_define_gradle_java_home; then
    GRADLE_JAVA_HOME_ARGS+=("-Dorg.gradle.java.home=$JAVA_HOME_OVERRIDE")
fi

append_shell_words ENV_JVM_OPTS "${JAVA_OPTS:-}"
append_shell_words ENV_JVM_OPTS "${GRADLE_OPTS:-}"

"$JAVACMD" \
    "${DEFAULT_JVM_OPTS[@]}" \
    "${ENV_JVM_OPTS[@]}" \
    "${GRADLE_JAVA_HOME_ARGS[@]}" \
    -Dorg.gradle.appname="$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"