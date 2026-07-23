# NeuroFlow Phase 4 — frontend build verification
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Ui = Join-Path $Root "neuroflow-ui"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

Write-Step "Installing dependencies"
Push-Location $Ui
try {
    if (-not (Test-Path "node_modules")) {
        npm install
    } else {
        npm install --prefer-offline
    }

    Write-Step "Building Next.js production bundle"
    npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "npm run build failed"
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Phase 4 OK - neuroflow-ui build succeeded." -ForegroundColor Green
