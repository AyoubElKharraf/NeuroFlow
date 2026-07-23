# Stop all NeuroFlow local processes and Docker stack (keeps volumes)
$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

Write-Step "Stopping app processes on ports 3000 / 8001 / 8080..."
foreach ($port in 3000, 8001, 8080) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object {
            Write-Host "Killing PID $($_.OwningProcess) on port $port"
            Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
        }
}

# Backend jar holders
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -match "neuroflow-backend" } |
    ForEach-Object {
        Write-Host "Killing Java PID $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

Write-Step "Stopping Docker Compose services..."
docker compose down

Write-Host ""
Write-Host "NeuroFlow stopped. Volumes kept." -ForegroundColor Green
Write-Host "Full wipe (delete DB data): docker compose down -v"
