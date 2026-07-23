# NeuroFlow Phase 2 verification script (cmd.exe / PowerShell)
# Prerequisites: docker compose UP, Java 17+ (prefer 21/25), Maven

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "neuroflow-backend"
$BackendPort = 8080
$BaseUrl = "http://localhost:$BackendPort"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

function Set-NeuroFlowJavaHome {
    $candidates = @(
        "C:\Java\jdk-25",
        "C:\Java\jdk-21",
        "C:\Java\jdk-17",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-17*"
    )

    foreach ($pattern in $candidates) {
        $matches = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object FullName -Descending
        foreach ($match in $matches) {
            $javaExe = Join-Path $match.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                $env:JAVA_HOME = $match.FullName
                $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
                return
            }
        }
    }

    throw "Java 17+ not found. Install JDK 21/25 under C:\Java\ (Spring Boot 3 requires class file 61+)."
}

Write-Step "Checking infra..."
docker compose -f (Join-Path $Root "docker-compose.yml") ps

Write-Step "Stopping any previous backend on port $BackendPort..."
Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object {
        Write-Host "Killing PID $($_.OwningProcess) on port $BackendPort"
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
Start-Sleep -Seconds 2

# Also stop java processes holding the backend jar (blocks Maven repackage on Windows)
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -match "neuroflow-backend" } |
    ForEach-Object {
        Write-Host "Killing locked-jar Java PID $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
Start-Sleep -Seconds 1

Write-Step "Checking Java / Maven..."
# Always override JAVA_HOME if current JDK is too old for Spring Boot 3 (needs Java 17+)
# Note: java -version writes to stderr; do not let that abort the script under Stop.
$needOverride = $true
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $verText = (& cmd /c "`"$env:JAVA_HOME\bin\java.exe`" -version 2>&1") | Out-String
    if ($verText -match 'version "(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 17) { $needOverride = $false }
        Write-Host "Detected JAVA_HOME major=$major ($env:JAVA_HOME)"
    }
}
if ($needOverride) {
    Write-Host "Switching to Java 17+ (Spring Boot 3 requirement)..."
    Set-NeuroFlowJavaHome
}
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
Write-Host "JAVA_HOME=$env:JAVA_HOME"
cmd /c "java -version 2>&1"
cmd /c "mvn -version 2>&1"

Write-Step "Building backend..."
Push-Location $Backend
try {
    if (Test-Path "target\neuroflow-backend-0.1.0-SNAPSHOT.jar") {
        Remove-Item "target\neuroflow-backend-0.1.0-SNAPSHOT.jar*" -Force -ErrorAction SilentlyContinue
    }
    mvn -q -DskipTests clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }
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
$outLog = Join-Path $Root "scripts\phase2-backend.out.log"
$errLog = Join-Path $Root "scripts\phase2-backend.err.log"
Remove-Item $outLog, $errLog -Force -ErrorAction SilentlyContinue

$proc = Start-Process -FilePath "java" `
    -ArgumentList "-jar", $jar.FullName `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog `
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
            Write-Host "---- stdout ----"; Get-Content $outLog -Tail 40 -ErrorAction SilentlyContinue
            Write-Host "---- stderr ----"; Get-Content $errLog -Tail 40 -ErrorAction SilentlyContinue
            throw "Backend exited early with code $($proc.ExitCode)"
        }
    }

    if (-not $ready) {
        Write-Host "---- stdout ----"; Get-Content $outLog -Tail 40 -ErrorAction SilentlyContinue
        Write-Host "---- stderr ----"; Get-Content $errLog -Tail 40 -ErrorAction SilentlyContinue
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
