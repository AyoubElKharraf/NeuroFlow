@echo off
REM Stop all NeuroFlow local processes and Docker stack (keeps volumes)
cd /d "%~dp0.."

echo ==> Stopping app processes on ports 3000 / 8001 / 8080 ...
for %%P in (3000 8001 8080) do (
  for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%%P" ^| findstr LISTENING') do (
    echo Killing PID %%a on port %%P
    taskkill /F /PID %%a >nul 2>&1
  )
)

echo ==> Stopping Docker Compose services...
docker compose down

echo.
echo NeuroFlow stopped.
echo Volumes kept (Postgres/ClickHouse/Kafka data). To wipe volumes too:
echo   docker compose down -v
echo.
pause
