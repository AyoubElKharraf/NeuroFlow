@echo off
REM NeuroFlow Kafka smoke test — works from cmd.exe and PowerShell
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0kafka-smoke-test.ps1" %*
