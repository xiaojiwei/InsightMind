"""Deterministic metric forecasting and cross-metric insight services."""

from .router import (
    configure_insight_runtime,
    init_insight_store,
    router as insights_router,
)

__all__ = [
    "configure_insight_runtime",
    "init_insight_store",
    "insights_router",
]
