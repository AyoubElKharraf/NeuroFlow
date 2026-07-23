# neuroflow-analytics

MLOps analytics — **Python 3.11 / FastAPI** + Evidently AI (Phase 3).

## Run (local)

```powershell
cd neuroflow-analytics
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Health: `GET http://localhost:8000/health`  
Drift scaffold: `POST http://localhost:8000/api/v1/drift/check`
