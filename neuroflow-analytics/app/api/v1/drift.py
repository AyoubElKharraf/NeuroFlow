"""Backward-compatible re-export — prefer app.api.drift."""

from app.api.drift import router

__all__ = ["router"]
