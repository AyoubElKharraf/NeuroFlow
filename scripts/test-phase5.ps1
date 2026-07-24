# NeuroFlow Phase 5 — Testcontainers + CI local verification
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "neuroflow-backend"
$Analytics = Join-Path $Root "neuroflow-analytics"
$Ui = Join-Path $Root "neuroflow-ui"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

function Set-NeuroFlowJavaHome {
    $candidates = @(
        "C:\Java\jdk-25",
        "C:\Java\jdk-21",
        "C:\Java\jdk-17"
    )
    foreach ($pattern in $candidates) {
        $match = Get-Item $pattern -ErrorAction SilentlyContinue
        if ($match -and (Test-Path (Join-Path $match.FullName "bin\java.exe"))) {
            $env:JAVA_HOME = $match.FullName
            $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
            return
        }
    }
    throw "Java 17+ not found under C:\Java\"
}

Write-Step "Docker available?"
docker version --format "{{.Server.Version}}" | Out-Host

Write-Step "Java / Maven for backend tests"
$needOverride = $true
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $verText = (& cmd /c "`"$env:JAVA_HOME\bin\java.exe`" -version 2>&1") | Out-String
    if ($verText -match 'version "(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 17) { $needOverride = $false }
    }
}
if ($needOverride) { Set-NeuroFlowJavaHome }
Write-Host "JAVA_HOME=$env:JAVA_HOME"

Write-Step "Backend: mvn test (Testcontainers)"
# Docker Desktop on Windows: point Java clients at the Linux engine pipe
if ($env:OS -match "Windows" -and -not $env:DOCKER_HOST) {
    $env:DOCKER_HOST = "npipe:////./pipe/dockerDesktopLinuxEngine"
}
Push-Location $Backend
try {
    mvn -B test
    if ($LASTEXITCODE -ne 0) { throw "Backend tests failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

Write-Step "Analytics: pytest"
Push-Location $Analytics
try {
    if (-not (Test-Path ".venv\Scripts\python.exe")) {
        throw "Missing .venv — run scripts\run-analytics.cmd once first"
    }
    & .\.venv\Scripts\python.exe -m pip install -q -r requirements-dev.txt
    & .\.venv\Scripts\python.exe -m pytest -q
    if ($LASTEXITCODE -ne 0) { throw "Analytics pytest failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

Write-Step "UI: production build"
Push-Location $Ui
try {
    if (-not (Test-Path "node_modules")) { npm install }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "UI build failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Phase 5 OK - Testcontainers, pytest and UI build passed." -ForegroundColor Green
