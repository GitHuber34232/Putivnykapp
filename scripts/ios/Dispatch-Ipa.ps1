$ErrorActionPreference = 'Stop'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'GitHub CLI (gh) is required. Install it from https://cli.github.com/'
}

if (-not $env:GITHUB_REPOSITORY) {
    throw 'Set GITHUB_REPOSITORY=owner/repo before dispatching the IPA workflow.'
}

$exportMethod = if ($env:IPA_EXPORT_METHOD) { $env:IPA_EXPORT_METHOD } else { 'ad-hoc' }

gh workflow run ios-ipa.yml --repo $env:GITHUB_REPOSITORY -f "export_method=$exportMethod"
Write-Host "Triggered iOS IPA workflow for $($env:GITHUB_REPOSITORY) with export_method=$exportMethod"