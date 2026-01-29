# Push only the flutter_lockscreen_alert package to its GitHub repo.
# Run from the package root: packages/flutter_lockscreen_alert
# Usage: .\scripts\push_package_to_repo.ps1

$ErrorActionPreference = "Stop"
# Script lives in packages/flutter_lockscreen_alert/scripts/ — package root is parent of scripts
$packageRoot = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path (Join-Path $packageRoot "pubspec.yaml"))) {
    Write-Error "Run this script from packages/flutter_lockscreen_alert or ensure package root contains pubspec.yaml"
    exit 1
}

Set-Location $packageRoot

$remote = "https://github.com/vairagadeharshwardhan/flutter_lockscreen_alert.git"

if (-not (Test-Path ".git")) {
    Write-Host "Initializing git in package directory..."
    git init
    git remote add origin $remote
    git branch -M main
}

if ((git remote get-url origin 2>$null) -ne $remote) {
    git remote set-url origin $remote
}

Write-Host "Adding files and committing..."
git add .
git status
git commit -m "Initial commit: flutter_lockscreen_alert package" 2>$null
if ($LASTEXITCODE -ne 0) {
    $c = git status --short
    if (-not $c) { Write-Host "Nothing to commit (clean)." } else { Write-Host "Commit had issues - check status above." }
}

# If the GitHub repo already had content (e.g. LICENSE), pull first
$branch = git rev-parse --abbrev-ref HEAD 2>$null
git fetch origin 2>$null
if ($LASTEXITCODE -eq 0) {
    $remoteMain = git rev-parse origin/main 2>$null
    if ($remoteMain) {
        Write-Host "Remote has existing commits. Pulling with --allow-unrelated-histories..."
        git pull origin main --allow-unrelated-histories --no-edit 2>$null
    }
}

Write-Host "Pushing to origin main..."
git push -u origin main

Write-Host "Done. Repo contains only the package: $remote"
