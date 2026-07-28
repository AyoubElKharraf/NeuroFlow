"""Data drift engine powered by Evidently AI."""

from __future__ import annotations

from typing import Any

import pandas as pd
from evidently import Report
from evidently.presets import DataDriftPreset

from app.services.baseline_service import FEATURE_COLUMNS, BaselineService, get_baseline_service


class DriftComputationError(ValueError):
    """Raised when drift cannot be computed from the given payload."""


class DriftService:
    def __init__(self, baseline: BaselineService | None = None) -> None:
        self._baseline = baseline or get_baseline_service()
        self._last_current: pd.DataFrame | None = None
        self._last_html: str | None = None

    def calculate_data_drift(self, current_data_json: list[dict[str, Any]]) -> dict[str, Any]:
        if not current_data_json:
            raise DriftComputationError("current_data must contain at least one row")

        reference = self._baseline.get_reference_dataframe()
        current = self._to_feature_frame(current_data_json)
        self._last_current = current

        report = Report([DataDriftPreset()])
        snapshot = report.run(current_data=current, reference_data=reference)
        self._last_html = snapshot.get_html_str(as_iframe=False)

        metrics = self._extract_metrics(snapshot.dict())
        self._attach_feature_means(metrics, reference, current)
        return metrics

    def get_last_html_report(self) -> str | None:
        return self._last_html

    def build_html_report(self, current_data_json: list[dict[str, Any]] | None = None) -> str:
        if current_data_json:
            self.calculate_data_drift(current_data_json)
        elif self._last_html is None:
            sample = self._baseline.sample_like_reference(n_rows=120)
            self.calculate_data_drift(sample.to_dict(orient="records"))
        assert self._last_html is not None
        return self._last_html

    def _to_feature_frame(self, rows: list[dict[str, Any]]) -> pd.DataFrame:
        frame = pd.DataFrame(rows)
        missing = [col for col in FEATURE_COLUMNS if col not in frame.columns]
        if missing:
            raise DriftComputationError(
                f"Missing required feature columns: {', '.join(missing)}"
            )
        try:
            typed = frame[FEATURE_COLUMNS].apply(pd.to_numeric, errors="coerce")
        except Exception as exc:  # pragma: no cover - defensive
            raise DriftComputationError(f"Unable to parse feature values: {exc}") from exc

        if typed.isna().any().any():
            raise DriftComputationError(
                "Feature values must be numeric; found null/non-numeric cells"
            )
        return typed

    def _extract_metrics(self, report_dict: dict[str, Any]) -> dict[str, Any]:
        """Parse Evidently >=0.7 snapshot.dict() structure."""
        feature_metrics: dict[str, dict[str, Any]] = {}
        drifted_count = 0
        drift_share = 0.0

        for metric in report_dict.get("metrics", []):
            config = metric.get("config") or {}
            value = metric.get("value")
            metric_type = str(config.get("type", ""))

            if "DriftedColumnsCount" in metric_type and isinstance(value, dict):
                drifted_count = int(value.get("count") or 0)
                drift_share = float(value.get("share") or 0.0)
                continue

            if "ValueDrift" in metric_type:
                feature = str(config.get("column") or "unknown")
                method = str(config.get("method") or "unknown")
                threshold = float(config.get("threshold") or 0.05)
                score = float(value) if isinstance(value, (int, float)) else 0.0

                # KS p-value: drift when p-value < threshold
                # Distance-like methods: drift when score > threshold
                method_l = method.lower()
                if "p_value" in method_l or "p-value" in method_l or "ks" in method_l:
                    drifted = score < threshold
                else:
                    drifted = score > threshold

                feature_metrics[feature] = {
                    "drift_score": score,
                    "drift_detected": drifted,
                    "stat_test_name": method,
                }

        if not drifted_count and feature_metrics:
            drifted_count = sum(
                1 for item in feature_metrics.values() if item["drift_detected"]
            )
            total = max(len(feature_metrics), 1)
            drift_share = drifted_count / total

        dataset_drift = drifted_count > 0 and drift_share >= 0.5
        # Fallback: if share threshold not met but majority features drifted
        if not dataset_drift and feature_metrics:
            detected = sum(1 for item in feature_metrics.values() if item["drift_detected"])
            dataset_drift = detected / max(len(feature_metrics), 1) >= 0.5

        return {
            "dataset_drift": bool(dataset_drift),
            "drift_share": float(drift_share),
            "number_of_drifted_features": int(drifted_count),
            "feature_metrics": feature_metrics,
        }

    @staticmethod
    def _attach_feature_means(
        metrics: dict[str, Any],
        reference: pd.DataFrame,
        current: pd.DataFrame,
    ) -> None:
        feature_metrics = metrics.get("feature_metrics") or {}
        for feature, details in feature_metrics.items():
            if feature in reference.columns:
                details["baseline_mean"] = float(reference[feature].mean())
            else:
                details["baseline_mean"] = 0.0
            if feature in current.columns:
                details["production_mean"] = float(current[feature].mean())
            else:
                details["production_mean"] = 0.0


_drift_service: DriftService | None = None


def get_drift_service() -> DriftService:
    global _drift_service
    if _drift_service is None:
        _drift_service = DriftService()
    return _drift_service
