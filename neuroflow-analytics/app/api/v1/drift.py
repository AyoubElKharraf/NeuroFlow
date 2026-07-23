"""Data drift endpoints — Evidently AI integration in Phase 3."""

from fastapi import APIRouter
from pydantic import BaseModel, Field

router = APIRouter(tags=["drift"])


class DriftCheckRequest(BaseModel):
    model_name: str = Field(..., examples=["fraud-classifier"])
    reference_rows: list[dict] = Field(default_factory=list)
    production_rows: list[dict] = Field(default_factory=list)
    feature_columns: list[str] = Field(default_factory=list)


class DriftFeatureResult(BaseModel):
    feature: str
    drift_score: float
    drifted: bool
    method: str = "kolmogorov_smirnov"


class DriftCheckResponse(BaseModel):
    model_name: str
    overall_drifted: bool
    features: list[DriftFeatureResult]
    message: str


@router.post("/drift/check", response_model=DriftCheckResponse)
def check_drift(payload: DriftCheckRequest) -> DriftCheckResponse:
    """
    Compare reference vs production feature distributions.

    Phase 1: scaffold response.
    Phase 3: Evidently AI (KS / Wasserstein) computation.
    """
    feature_results = [
        DriftFeatureResult(
            feature=col,
            drift_score=0.0,
            drifted=False,
            method="pending-evidently",
        )
        for col in payload.feature_columns
    ]

    return DriftCheckResponse(
        model_name=payload.model_name,
        overall_drifted=False,
        features=feature_results,
        message="Drift engine scaffold — Evidently AI wiring comes in Phase 3",
    )
