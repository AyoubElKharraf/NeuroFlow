"""NeuroFlow Analytics — Phase 5 pytest suite."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture(scope="module")
def client() -> TestClient:
    with TestClient(app) as test_client:
        yield test_client


def test_health(client: TestClient) -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == "neuroflow-analytics"


def test_drift_check_normal_sample(client: TestClient) -> None:
    sample = client.get("/api/v1/drift/samples/production-like", params={"n": 80, "seed": 7})
    assert sample.status_code == 200
    payload = {
        "model_name": "loan-risk-model",
        "current_data": sample.json()["current_data"],
    }
    response = client.post("/api/v1/drift/check", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["dataset_drift"] is False
    assert body["number_of_drifted_features"] == 0
    assert "age" in body["feature_metrics"]
    age_metric = body["feature_metrics"]["age"]
    assert "baseline_mean" in age_metric
    assert "production_mean" in age_metric


def test_drift_check_detects_shifted_distribution(client: TestClient) -> None:
    sample = client.get("/api/v1/drift/samples/production-like", params={"n": 80, "seed": 11})
    assert sample.status_code == 200
    drifted = []
    for row in sample.json()["current_data"]:
        drifted.append(
            {
                "age": row["age"],
                "income": row["income"] * 5.0,
                "credit_score": max(300.0, row["credit_score"] - 180.0),
                "loan_amount": row["loan_amount"] * 3.5,
            }
        )

    response = client.post(
        "/api/v1/drift/check",
        json={"model_name": "loan-risk-model", "current_data": drifted},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["dataset_drift"] is True
    assert body["number_of_drifted_features"] >= 1
