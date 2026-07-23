"""Drift API routes — Evidently AI reports."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException, status
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field, field_validator

from app.services.baseline_service import get_baseline_service
from app.services.drift_service import DriftComputationError, get_drift_service

router = APIRouter(tags=["drift"])


class DriftCheckRequest(BaseModel):
    model_name: str = Field(default="loan-risk-model", examples=["loan-risk-model"])
    current_data: list[dict[str, Any]] = Field(
        ...,
        min_length=1,
        description="Production feature rows (age, income, credit_score, loan_amount)",
    )

    @field_validator("current_data")
    @classmethod
    def reject_empty_rows(cls, value: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if not value:
            raise ValueError("current_data must not be empty")
        return value


class FeatureDriftMetric(BaseModel):
    drift_score: float
    drift_detected: bool
    stat_test_name: str


class DriftCheckResponse(BaseModel):
    model_name: str
    dataset_drift: bool
    drift_share: float
    number_of_drifted_features: int
    feature_metrics: dict[str, FeatureDriftMetric]
    message: str


@router.post("/drift/check", response_model=DriftCheckResponse)
def check_drift(payload: DriftCheckRequest) -> DriftCheckResponse:
    """Compare production feature distributions against the training baseline."""
    try:
        metrics = get_drift_service().calculate_data_drift(payload.current_data)
    except DriftComputationError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc
    except Exception as exc:  # pragma: no cover
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Drift computation failed: {exc}",
        ) from exc

    return DriftCheckResponse(
        model_name=payload.model_name,
        dataset_drift=metrics["dataset_drift"],
        drift_share=metrics["drift_share"],
        number_of_drifted_features=metrics["number_of_drifted_features"],
        feature_metrics={
            name: FeatureDriftMetric(**details)
            for name, details in metrics["feature_metrics"].items()
        },
        message=(
            "DATA DRIFT DETECTED"
            if metrics["dataset_drift"]
            else "No significant dataset drift detected"
        ),
    )


@router.get("/drift/report-html", response_class=HTMLResponse)
def drift_report_html() -> HTMLResponse:
    """Return the latest Evidently HTML report (or a baseline demo report)."""
    try:
        html = get_drift_service().build_html_report()
    except Exception as exc:  # pragma: no cover
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Unable to render HTML report: {exc}",
        ) from exc
    return HTMLResponse(content=html, status_code=200)


@router.get("/drift/baseline/sample")
def baseline_sample(n: int = 5) -> dict[str, Any]:
    """Debug helper: peek at reference distribution samples."""
    n_rows = max(1, min(n, 50))
    frame = get_baseline_service().get_reference_dataframe().head(n_rows)
    return {
        "features": get_baseline_service().get_feature_columns(),
        "rows": frame.to_dict(orient="records"),
    }


@router.get("/drift/samples/production-like")
def production_like_sample(n: int = 150, seed: int = 99) -> dict[str, Any]:
    """Return a no-drift production-like batch drawn from the baseline generator."""
    n_rows = max(10, min(n, 500))
    frame = get_baseline_service().sample_like_reference(n_rows=n_rows, seed=seed)
    return {
        "model_name": "loan-risk-model",
        "current_data": frame.to_dict(orient="records"),
    }
