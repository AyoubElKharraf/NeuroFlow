"""Inference API for the loan-risk model."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field

from app.services.model_service import predict_one

router = APIRouter(tags=["predict"])


class PredictRequest(BaseModel):
    age: float = Field(..., examples=[42.0])
    income: float = Field(..., examples=[54000.0])
    credit_score: float = Field(..., examples=[680.0])
    loan_amount: float = Field(..., examples=[18000.0])


class PredictResponse(BaseModel):
    model_name: str
    model_version: str
    prediction: str
    confidence: float
    reject_probability: float
    features: dict[str, float]


@router.post("/predict", response_model=PredictResponse)
def predict(payload: PredictRequest) -> PredictResponse:
    try:
        result = predict_one(payload.model_dump())
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc
    except Exception as exc:  # pragma: no cover
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Prediction failed: {exc}",
        ) from exc
    return PredictResponse(**result)


@router.get("/predict/health")
def predict_health() -> dict[str, Any]:
    sample = predict_one(
        {"age": 42.0, "income": 54000.0, "credit_score": 680.0, "loan_amount": 18000.0}
    )
    return {
        "status": "UP",
        "model_name": sample["model_name"],
        "model_version": sample["model_version"],
    }
