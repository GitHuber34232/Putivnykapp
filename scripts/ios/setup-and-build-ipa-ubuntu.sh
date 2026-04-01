#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd -P)
DEFAULT_REPO="GitHuber34232/Putivnykapp"
DEFAULT_P12_PATH="$HOME/ios/signing_certificate.p12"
DEFAULT_PROFILE_PATH="$HOME/ios/Putivnyk.mobileprovision"
REPO=${GITHUB_REPOSITORY:-$DEFAULT_REPO}
EXPORT_METHOD=${IPA_EXPORT_METHOD:-ad-hoc}
P12_PATH=${IOS_P12_PATH:-$DEFAULT_P12_PATH}
PROFILE_PATH=${IOS_PROFILE_PATH:-$DEFAULT_PROFILE_PATH}
P12_PASSWORD=${IOS_P12_PASSWORD:-}

resolve_default_tag() {
  local marketing_version
  marketing_version=$(sed -n 's/.*MARKETING_VERSION: "\([^"]*\)".*/\1/p' "$ROOT_DIR/iosApp/project.yml" | head -n 1)
  if [ -n "$marketing_version" ]; then
    printf 'v%s-ios' "$marketing_version"
  else
    printf 'v1.0.0-ios'
  fi
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

install_dependencies() {
  local missing=0

  for cmd in git gh openssl python3 base64; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      missing=1
      break
    fi
  done

  if [ "$missing" = "0" ]; then
    return
  fi

  if ! command -v apt-get >/dev/null 2>&1; then
    echo "apt-get not found. This script expects Ubuntu/Debian with apt." >&2
    exit 1
  fi

  echo "Installing required packages via apt-get..."
  sudo apt-get update
  sudo apt-get install -y gh openssl python3 coreutils git
}

ensure_github_auth() {
  if gh auth status >/dev/null 2>&1; then
    gh auth setup-git >/dev/null 2>&1 || true
    return
  fi

  echo "GitHub CLI is not authenticated. Starting gh auth login..."
  gh auth login --hostname github.com --git-protocol https --web
  gh auth setup-git >/dev/null 2>&1 || true
}

normalize_origin_remote() {
  local desired_url="https://github.com/$REPO.git"
  local current_url

  current_url=$(git -C "$ROOT_DIR" remote get-url origin 2>/dev/null || true)
  if [ -z "$current_url" ]; then
    return
  fi

  if [ "$current_url" != "$desired_url" ] && printf '%s' "$current_url" | grep -qi 'github\.com'; then
    git -C "$ROOT_DIR" remote set-url origin "$desired_url"
  fi
}

install_dependencies

for cmd in git gh openssl python3 base64 sed mktemp; do
  require_cmd "$cmd"
done

ensure_github_auth
TAG_NAME=${IOS_GIT_TAG:-$(resolve_default_tag)}

if [ -z "$REPO" ]; then
  echo "Cannot determine GitHub repository. Set GITHUB_REPOSITORY or update DEFAULT_REPO in this script." >&2
  exit 1
fi

if [ -z "$P12_PATH" ] || [ ! -f "$P12_PATH" ]; then
  echo "Signing certificate not found: $P12_PATH" >&2
  echo "Place the file there or override with IOS_P12_PATH." >&2
  exit 1
fi

if [ -z "$PROFILE_PATH" ] || [ ! -f "$PROFILE_PATH" ]; then
  echo "Provisioning profile not found: $PROFILE_PATH" >&2
  echo "Place the file there or override with IOS_PROFILE_PATH." >&2
  exit 1
fi

if [ -z "$P12_PASSWORD" ]; then
  echo "IOS_P12_PASSWORD is not set. Export it before running this script." >&2
  exit 1
fi

TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

PROFILE_PLIST="$TMP_DIR/profile.plist"
CERT_PEM="$TMP_DIR/cert.pem"

openssl smime -inform der -verify -noverify -in "$PROFILE_PATH" -out "$PROFILE_PLIST" >/dev/null 2>&1
openssl pkcs12 -in "$P12_PATH" -clcerts -nokeys -passin "pass:$P12_PASSWORD" -out "$CERT_PEM" >/dev/null 2>&1

mapfile -t META < <(python3 - "$PROFILE_PLIST" "$CERT_PEM" <<'PY'
import plistlib
import re
import subprocess
import sys

profile_path, cert_path = sys.argv[1], sys.argv[2]
with open(profile_path, 'rb') as fh:
    profile = plistlib.load(fh)

name = profile.get('Name', '')
team_id = ''
team = profile.get('TeamIdentifier') or []
if team:
    team_id = team[0]

entitlements = profile.get('Entitlements') or {}
app_identifier = entitlements.get('application-identifier', '')
bundle_id = app_identifier.split('.', 1)[1] if '.' in app_identifier else app_identifier

subject = subprocess.check_output(
    ['openssl', 'x509', '-in', cert_path, '-noout', '-subject', '-nameopt', 'RFC2253'],
    text=True
).strip()
match = re.search(r'CN=([^,]+)', subject)
cn = match.group(1) if match else ''
identity = 'Apple Distribution'
if cn.startswith('Apple Development'):
    identity = 'Apple Development'
elif cn.startswith('Apple Distribution'):
    identity = 'Apple Distribution'
elif cn.startswith('iPhone Developer'):
    identity = 'Apple Development'
elif cn.startswith('iPhone Distribution'):
    identity = 'Apple Distribution'

for value in (name, team_id, bundle_id, identity):
    print(value)
PY
)

PROFILE_NAME=${META[0]:-}
TEAM_ID=${META[1]:-}
BUNDLE_ID=${META[2]:-ua.kyiv.putivnyk.ios}
CODE_SIGN_IDENTITY=${META[3]:-Apple Distribution}

if [ -z "$PROFILE_NAME" ] || [ -z "$TEAM_ID" ] || [ -z "$BUNDLE_ID" ]; then
  echo "Failed to extract provisioning profile metadata." >&2
  exit 1
fi

P12_BASE64=$(base64 -w 0 "$P12_PATH")
PROFILE_BASE64=$(base64 -w 0 "$PROFILE_PATH")

normalize_origin_remote

echo "Uploading GitHub secrets to $REPO..."
printf '%s' "$P12_BASE64" | gh secret set IOS_CERTIFICATE_P12_BASE64 --repo "$REPO" --body -
printf '%s' "$P12_PASSWORD" | gh secret set IOS_CERTIFICATE_PASSWORD --repo "$REPO" --body -
printf '%s' "$PROFILE_BASE64" | gh secret set IOS_PROVISIONING_PROFILE_BASE64 --repo "$REPO" --body -
printf '%s' "$PROFILE_NAME" | gh secret set IOS_PROVISIONING_PROFILE_NAME --repo "$REPO" --body -
printf '%s' "$TEAM_ID" | gh secret set IOS_TEAM_ID --repo "$REPO" --body -
printf '%s' "$BUNDLE_ID" | gh secret set IOS_BUNDLE_IDENTIFIER --repo "$REPO" --body -
printf '%s' "$CODE_SIGN_IDENTITY" | gh secret set IOS_CODE_SIGN_IDENTITY --repo "$REPO" --body -

echo "Prepared secrets:"
echo "  IOS_PROVISIONING_PROFILE_NAME=$PROFILE_NAME"
echo "  IOS_TEAM_ID=$TEAM_ID"
echo "  IOS_BUNDLE_IDENTIFIER=$BUNDLE_ID"
echo "  IOS_CODE_SIGN_IDENTITY=$CODE_SIGN_IDENTITY"
echo "  IPA_EXPORT_METHOD=$EXPORT_METHOD"
echo "  TAG_NAME=$TAG_NAME"

if [ -n "$(git -C "$ROOT_DIR" status --short)" ]; then
  echo "Working tree is dirty. Commit or stash changes before running this script." >&2
  exit 1
fi

echo "Pushing current branch to origin..."
git -C "$ROOT_DIR" push origin HEAD

if ! git -C "$ROOT_DIR" rev-parse "$TAG_NAME" >/dev/null 2>&1; then
  git -C "$ROOT_DIR" tag "$TAG_NAME"
fi
git -C "$ROOT_DIR" push origin "$TAG_NAME"

gh workflow run ios-ipa.yml --repo "$REPO" -f export_method="$EXPORT_METHOD"
echo "Triggered ios-ipa.yml with export_method=$EXPORT_METHOD"

echo "Done. Watch artifacts in: https://github.com/$REPO/actions"