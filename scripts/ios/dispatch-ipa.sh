#!/bin/sh
set -eu

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI is required. Install it from https://cli.github.com/" >&2
  exit 1
fi

REPO=${GITHUB_REPOSITORY:-}
METHOD=${IPA_EXPORT_METHOD:-ad-hoc}

if [ -z "$REPO" ]; then
  echo "Set GITHUB_REPOSITORY=owner/repo before dispatching the IPA workflow." >&2
  exit 1
fi

gh workflow run ios-ipa.yml --repo "$REPO" -f export_method="$METHOD"
echo "Triggered iOS IPA workflow for $REPO with export_method=$METHOD"