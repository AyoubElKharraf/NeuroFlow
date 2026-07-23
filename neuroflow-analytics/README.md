# neuroflow-analytics

MLOps analytics — **Python / FastAPI** + **Evidently AI** (Phase 3).

## Run (cmd.exe)

```bat
scripts\run-analytics.cmd
```

Or manually:

```bat
cd neuroflow-analytics
.venv\Scripts\activate.bat
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

> `Activate.ps1` only works in **PowerShell**. In **cmd.exe** use `activate.bat`.

## Run (PowerShell)

```powershell
cd neuroflow-analytics
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Liveness |
| POST | `/api/v1/drift/check` | JSON drift report vs training baseline |
| GET | `/api/v1/drift/report-html` | Evidently interactive HTML report |

### Verify

```bat
scripts\test-phase3.cmd
```
