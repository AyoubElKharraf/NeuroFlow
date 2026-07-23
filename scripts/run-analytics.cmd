@echo off
REM Start NeuroFlow analytics (works from cmd.exe)
cd /d "%~dp0..\neuroflow-analytics"

REM If already healthy on 8001, reuse it
curl.exe -sS -m 2 http://127.0.0.1:8001/health >nul 2>&1
if %ERRORLEVEL%==0 (
  echo Analytics already running on http://127.0.0.1:8001
  curl.exe -sS http://127.0.0.1:8001/health
  echo.
  goto :eof
)

REM Free port 8001 if a stale process holds it
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8001" ^| findstr LISTENING') do (
  echo Freeing port 8001 ^(PID %%p^)...
  taskkill /F /PID %%p >nul 2>&1
)

if not exist ".venv\Scripts\uvicorn.exe" (
  echo Creating venv and installing deps...
  python -m venv .venv
  call .venv\Scripts\activate.bat
  python -m pip install --upgrade pip
  pip install -r requirements.txt
) else (
  call .venv\Scripts\activate.bat
)

echo Starting analytics on http://127.0.0.1:8001
uvicorn app.main:app --host 127.0.0.1 --port 8001
