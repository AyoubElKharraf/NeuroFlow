"""NeuroFlow Analytics — FastAPI entrypoint (MLOps drift engine)."""

from fastapi import FastAPI

from app.api.v1.drift import router as drift_router

app = FastAPI(
    title="NeuroFlow Analytics",
    description="MLOps data-drift & model performance analytics",
    version="0.1.0",
)

app.include_router(drift_router, prefix="/api/v1")


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "service": "neuroflow-analytics",
        "phase": "1-scaffold",
    }
