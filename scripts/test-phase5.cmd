@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-phase5.ps1"
exit /b %ERRORLEVEL%
