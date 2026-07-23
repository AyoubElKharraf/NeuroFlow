"""NeuroFlow Analytics — FastAPI entrypoint (MLOps drift engine)."""

from fastapi import FastAPI

from app.api.drift import router as drift_router

app = FastAPI(
    title="NeuroFlow Analytics",
    description="MLOps data-drift & model performance analytics",
    version="0.3.0",
)

app.include_router(drift_router, prefix="/api/v1")


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "service": "neuroflow-analytics",
        "phase": "3-drift",
    }
