from datetime import datetime

import numpy as np
import pandas as pd

from kg_builder.insights.contracts import MetricSeriesSpec
from kg_builder.insights.forecast import ForecastEngine
from kg_builder.insights.series import MetricSeriesService


def _monthly_points(count=60):
    periods = pd.date_range("2021-01-01", periods=count, freq="MS")
    values = 100 + np.arange(count) * 1.5 + 12 * np.sin(np.arange(count) * 2 * np.pi / 12)
    return [{"period": period.date().isoformat(), "value": value} for period, value in zip(periods, values)]


def test_forecast_runs_backtest_and_returns_intervals():
    spec = MetricSeriesSpec("MEAS_revenue", "DIM_month", "month")
    snapshot = MetricSeriesService.from_points(spec, _monthly_points())

    result = ForecastEngine().forecast(snapshot, 4)

    assert result.status in {"QUALIFIED", "LOW_CONFIDENCE"}
    assert len(result.points) == 4
    assert result.backtest["folds"] >= 1
    assert result.backtest["baselineModel"] == "seasonal_naive"
    assert all(point.lower95 <= point.lower80 <= point.value <= point.upper80 <= point.upper95 for point in result.points)


def test_forecast_rejects_short_series():
    spec = MetricSeriesSpec("MEAS_revenue", "DIM_month", "month")
    snapshot = MetricSeriesService.from_points(spec, _monthly_points(8))

    result = ForecastEngine().forecast(snapshot, 3)

    assert result.status == "INSUFFICIENT_DATA"
    assert result.points == []


def test_series_service_reports_missing_periods_without_silent_fill():
    spec = MetricSeriesSpec("MEAS_revenue", "DIM_month", "month", fill_policy="none")
    points = _monthly_points(10)
    points.pop(4)

    snapshot = MetricSeriesService.from_points(spec, points)

    assert snapshot.missing_points == 1
    assert snapshot.missing_rate > 0
    assert any("缺失周期" in warning for warning in snapshot.warnings)
