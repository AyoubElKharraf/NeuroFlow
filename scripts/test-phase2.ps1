# NeuroFlow Phase 2 verification script (cmd.exe / PowerShell)
# Prerequisites: docker compose UP, Java 21, Maven

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "neuroflow-backend"
$BackendPort = 8080
$BaseUrl = "http://localhost:$BackendPort"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

Write-Step "Checking infra..."
docker compose -f (Join-Path $Root "docker-compose.yml") ps

Write-Step "Checking Java / Maven..."
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    foreach ($candidate in @("C:\Java\jdk-25", "C:\Java\jdk-21", "C:\Java\jdk-17")) {
        if (Test-Path "$candidate\bin\java.exe") {
            $env:JAVA_HOME = $candidate
            break
        }
    }
}
if ($env:JAVA_HOME) {
    $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
    Write-Host "JAVA_HOME=$env:JAVA_HOME"
}
java -version
mvn -version

Write-Step "Building backend..."
Push-Location $Backend
try {
    mvn -q -DskipTests package
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $Backend "target\*-SNAPSHOT.jar") |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Select-Object -First 1

if (-not $jar) {
    throw "Backend JAR not found in target/"
}

Write-Step "Starting backend: $($jar.Name)"
$logFile = Join-Path $Root "scripts\phase2-backend.log"
if (Test-Path $logFile) { Remove-Item $logFile -Force }

$proc = Start-Process -FilePath "java" `
    -ArgumentList "-jar", $jar.FullName `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $logFile `
    -PassThru `
    -WindowStyle Hidden

try {
    Write-Step "Waiting for health endpoint..."
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -TimeoutSec 2
            if ($health.status -eq "UP") {
                $ready = $true
                break
            }
        } catch {
            # keep waiting
        }
        if ($proc.HasExited) {
            Get-Content $logFile -Tail 80
            throw "Backend exited early with code $($proc.ExitCode)"
        }
    }

    if (-not $ready) {
        Get-Content $logFile -Tail 80
        throw "Backend did not become healthy in time"
    }

    Write-Step "Baseline ClickHouse counts..."
    $beforeTelemetry = docker compose -f (Join-Path $Root "docker-compose.yml") exec -T clickhouse `
        clickhouse-client --user neuroflow --password neuroflow `
        --query "SELECT count() FROM neuroflow_analytics.telemetry_events"
    $beforeMl = docker compose -f (Join-Path $Root "docker-compose.yml") exec -T clickhouse `
        clickhouse-client --user neuroflow --password neuroflow `
        --query "SELECT count() FROM neuroflow_analytics.ml_predictions"

    Write-Host "telemetry before: $beforeTelemetry"
    Write-Host "ml before:        $beforeMl"

    Write-Step "Starting simulation (8 seconds)..."
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/simulation/start" | Out-Host
    Start-Sleep -Seconds 8
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/simulation/stop" | Out-Host

    Write-Step "Waiting for Kafka -> ClickHouse lag..."
    Start-Sleep -Seconds 5

    $afterTelemetry = docker compose -f (Join-Path $Root "docker-compose.yml") exec -T clickhouse `
        clickhouse-client --user neuroflow --password neuroflow `
        --query "SELECT count() FROM neuroflow_analytics.telemetry_events"
    $afterMl = docker compose -f (Join-Path $Root "docker-compose.yml") exec -T clickhouse `
        clickhouse-client --user neuroflow --password neuroflow `
        --query "SELECT count() FROM neuroflow_analytics.ml_predictions"

    Write-Host "telemetry after: $afterTelemetry"
    Write-Host "ml after:        $afterMl"

    $metrics = Invoke-RestMethod -Uri "$BaseUrl/api/v1/metrics/ingestion"
    Write-Host "API metrics:" ($metrics | ConvertTo-Json -Compress)

    $t0 = [long]$beforeTelemetry.ToString().Trim()
    $t1 = [long]$afterTelemetry.ToString().Trim()
    if ($t1 -le $t0) {
        throw "Phase 2 FAILED: no new telemetry rows in ClickHouse"
    }

    Write-Host ""
    Write-Host "Phase 2 OK - Kafka ingestion wrote rows to ClickHouse." -ForegroundColor Green
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Write-Step "Stopping backend (PID $($proc.Id))..."
        Stop-Process -Id $proc.Id -Force
    }
}
