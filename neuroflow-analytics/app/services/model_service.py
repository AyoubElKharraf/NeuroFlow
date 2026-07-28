"""Train and persist the demo loan-risk classifier used by /api/v1/predict."""

from __future__ import annotations

from pathlib import Path

import joblib
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from app.services.baseline_service import FEATURE_COLUMNS, get_baseline_service

MODEL_DIR = Path(__file__).resolve().parents[1] / "models"
MODEL_PATH = MODEL_DIR / "loan_risk_model.joblib"
MODEL_VERSION = "v1.0.0"


def _label_row(row: dict[str, float]) -> int:
    # Heuristic risk score aligned with the synthetic feature ranges
    score = 0.0
    score += (700.0 - float(row["credit_score"])) / 100.0
    score += (float(row["loan_amount"]) / max(float(row["income"]), 1.0)) * 2.0
    score += (float(row["age"]) - 40.0) / 40.0
    return 1 if score > 1.1 else 0


def train_and_save(path: Path = MODEL_PATH) -> Path:
    frame = get_baseline_service().get_reference_dataframe()
    x = frame[FEATURE_COLUMNS].to_numpy(dtype=float)
    y = np.array([_label_row(row) for row in frame.to_dict(orient="records")], dtype=int)

    pipeline = Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            (
                "clf",
                LogisticRegression(max_iter=500, random_state=42),
            ),
        ]
    )
    pipeline.fit(x, y)

    path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "pipeline": pipeline,
            "features": FEATURE_COLUMNS,
            "model_name": "loan-risk-model",
            "model_version": MODEL_VERSION,
            "classes": ["approve", "reject"],
        },
        path,
    )
    return path


def load_model(path: Path = MODEL_PATH) -> dict:
    if not path.exists():
        train_and_save(path)
    return joblib.load(path)


def predict_one(features: dict[str, float], path: Path = MODEL_PATH) -> dict:
    artifact = load_model(path)
    pipeline = artifact["pipeline"]
    columns = artifact["features"]
    missing = [col for col in columns if col not in features]
    if missing:
        raise ValueError(f"Missing features: {', '.join(missing)}")

    row = np.array([[float(features[col]) for col in columns]], dtype=float)
    proba_reject = float(pipeline.predict_proba(row)[0][1])
    label = "reject" if proba_reject >= 0.5 else "approve"
    confidence = proba_reject if label == "reject" else 1.0 - proba_reject
    return {
        "model_name": artifact["model_name"],
        "model_version": artifact["model_version"],
        "prediction": label,
        "confidence": round(confidence, 4),
        "reject_probability": round(proba_reject, 4),
        "features": {col: float(features[col]) for col in columns},
    }


_model_cache: dict | None = None


def get_model_artifact() -> dict:
    global _model_cache
    if _model_cache is None:
        _model_cache = load_model()
    return _model_cache


if __name__ == "__main__":
    saved = train_and_save()
    print(f"Saved model to {saved}")
