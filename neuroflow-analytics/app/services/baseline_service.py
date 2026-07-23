"""Synthetic reference (baseline) dataset for drift comparisons."""

from __future__ import annotations

from functools import lru_cache

import numpy as np
import pandas as pd

FEATURE_COLUMNS: list[str] = ["age", "income", "credit_score", "loan_amount"]


class BaselineService:
    """Holds a stable training-like reference distribution."""

    def __init__(self, n_rows: int = 500, seed: int = 42) -> None:
        self._n_rows = n_rows
        self._seed = seed
        self._reference: pd.DataFrame | None = None

    def get_reference_dataframe(self) -> pd.DataFrame:
        if self._reference is None:
            self._reference = self._generate_reference(self._n_rows, self._seed)
        return self._reference.copy()

    def get_feature_columns(self) -> list[str]:
        return list(FEATURE_COLUMNS)

    @staticmethod
    def _generate_reference(n_rows: int, seed: int) -> pd.DataFrame:
        rng = np.random.default_rng(seed)
        age = rng.normal(loc=42.0, scale=12.0, size=n_rows).clip(18, 80)
        income = rng.lognormal(mean=10.5, sigma=0.45, size=n_rows).clip(15_000, 250_000)
        credit_score = rng.normal(loc=680.0, scale=55.0, size=n_rows).clip(300, 850)
        loan_amount = rng.lognormal(mean=9.5, sigma=0.55, size=n_rows).clip(1_000, 150_000)
        return pd.DataFrame(
            {
                "age": np.round(age, 1),
                "income": np.round(income, 2),
                "credit_score": np.round(credit_score, 1),
                "loan_amount": np.round(loan_amount, 2),
            }
        )

    def sample_like_reference(self, n_rows: int = 100, seed: int | None = None) -> pd.DataFrame:
        """Draw a production-like batch from the same distribution (no drift)."""
        effective_seed = self._seed + 7 if seed is None else seed
        return self._generate_reference(n_rows, effective_seed)


@lru_cache(maxsize=1)
def get_baseline_service() -> BaselineService:
    return BaselineService()
