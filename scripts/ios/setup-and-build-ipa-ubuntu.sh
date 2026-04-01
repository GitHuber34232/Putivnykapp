#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd -P)
REPO=${GITHUB_REPOSITORY:-}
EXPORT_METHOD=${IPA_EXPORT_METHOD:-ad-hoc}
P12_PATH=${IOS_P12_PATH:-}
PROFILE_PATH=${IOS_PROFILE_PATH:-}
P12_PASSWORD=${IOS_P12_PASSWORD:-}
TAG_NAME=${IOS_GIT_TAG:-}
AUTO_PUSH=${IOS_AUTO_PUSH:-1}
AUTO_TRIGGER=${IOS_AUTO_TRIGGER:-1}

usage() {
  cat <<EOF
Usage:
  $(basename "$0") --p12 /path/cert.p12 --profile /path/profile.mobileprovision [options]

Options:
  --repo owner/repo             GitHub repository; defaults to GITHUB_REPOSITORY or git remote
  --p12 PATH                    Signing certificate .p12 file
  --profile PATH                Provisioning profile .mobileprovision file
  --export-method METHOD        development|ad-hoc|app-store|enterprise (default: ad-hoc)
  --tag TAG                     Create and push tag before triggering workflow
  --no-push                     Do not git push local changes
  --no-trigger                  Do not dispatch the workflow after secrets are uploaded
  --help                        Show this help

Environment overrides:
  IOS_P12_PATH, IOS_PROFILE_PATH, IOS_P12_PASSWORD, IPA_EXPORT_METHOD,
  GITHUB_REPOSITORY, IOS_GIT_TAG, IOS_AUTO_PUSH, IOS_AUTO_TRIGGER
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --repo)
      REPO="$2"
      shift 2
      ;;
    --p12)
      P12_PATH="$2"
      shift 2
      ;;
    --profile)
      PROFILE_PATH="$2"
      shift 2
      ;;
    --export-method)
      EXPORT_METHOD="$2"
      shift 2
      ;;
    --tag)
      TAG_NAME="$2"
      shift 2
      ;;
    --no-push)
      AUTO_PUSH=0
      shift
      ;;
    --no-trigger)
      AUTO_TRIGGER=0
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

for cmd in git gh openssl python3 base64; do
  require_cmd "$cmd"
done

if [ -z "$REPO" ]; then
  remote_url=$(git -C "$ROOT_DIR" remote get-url origin 2>/dev/null || true)
  if [ -n "$remote_url" ]; then
    REPO=$(printf '%s' "$remote_url" | sed -E 's#(https://[^/]+/|git@[^:]+:)([^/.]+/[^/.]+)(\.git)?#\2#')
  fi
fi

if [ -z "$REPO" ]; then
  echo "Cannot determine GitHub repository. Pass --repo owner/repo or set GITHUB_REPOSITORY." >&2
  exit 1
fi

if [ -z "$P12_PATH" ] || [ ! -f "$P12_PATH" ]; then
  echo "Provide a valid --p12 path to your signing certificate." >&2
  exit 1
fi

if [ -z "$PROFILE_PATH" ] || [ ! -f "$PROFILE_PATH" ]; then
  echo "Provide a valid --profile path to your provisioning profile." >&2
  exit 1
fi

if [ -z "$P12_PASSWORD" ]; then
  printf 'P12 password: ' >&2
  stty -echo
  read -r P12_PASSWORD
  stty echo
  printf '\n' >&2
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

if [ "$AUTO_PUSH" = "1" ]; then
  git -C "$ROOT_DIR" push
fi

if [ -n "$TAG_NAME" ]; then
  if ! git -C "$ROOT_DIR" rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    git -C "$ROOT_DIR" tag "$TAG_NAME"
  fi
  git -C "$ROOT_DIR" push origin "$TAG_NAME"
fi

if [ "$AUTO_TRIGGER" = "1" ]; then
  gh workflow run ios-ipa.yml --repo "$REPO" -f export_method="$EXPORT_METHOD"
  echo "Triggered ios-ipa.yml with export_method=$EXPORT_METHOD"
fi

echo "Done. Watch artifacts in: https://github.com/$REPO/actions"