from datetime import datetime

from kg_builder.insights.contracts import (
    ForecastPoint,
    ForecastResult,
    GoalSpec,
    MetricSeriesSpec,
)
from kg_builder.insights.pace import PaceToGoalService
from kg_builder.insights.series import MetricSeriesService


def _forecast(values):
    points = []
    for month, value in enumerate(values, start=4):
        points.append(ForecastPoint(
            datetime(2026, month, 1), value,
            value - 3, value + 3, value - 5, value + 5,
        ))
    return ForecastResult(
        run_id="fc_test",
        metric_code="MEAS_sales",
        status="QUALIFIED",
        model_name="ets",
        horizon=len(points),
        points=points,
        quality_score=0.9,
        confidence=0.9,
        backtest={},
        data_quality={},
    )


def test_pace_to_goal_projects_period_total_and_probability():
    spec = MetricSeriesSpec("MEAS_sales", "DIM_month", "month")
    snapshot = MetricSeriesService.from_points(spec, [
        {"period": "2026-01-01", "value": 100},
        {"period": "2026-02-01", "value": 110},
        {"period": "2026-03-01", "value": 120},
    ])
    goal = GoalSpec(
        goal_id="1",
        metric_code="MEAS_sales",
        target_value=600,
        period_start="2026-01-01",
        period_end="2026-06-01",
    )

    result = PaceToGoalService().evaluate(snapshot, _forecast([100, 100, 100]), goal)

    assert result.actual_to_date == 330
    assert result.projected_end == 630
    assert result.remaining_periods == 3
    assert result.status == "ON_TRACK"
    assert result.probability > 0.7


def test_pace_rejects_ratio_in_first_version():
    spec = MetricSeriesSpec("MEAS_rate", "DIM_month", "month", aggregation_type="RATIO")
    snapshot = MetricSeriesService.from_points(spec, [{"period": "2026-01-01", "value": 0.5}])
    goal = GoalSpec("2", "MEAS_rate", 0.7, "2026-01-01", "2026-06-01", aggregation_type="RATIO")

    result = PaceToGoalService().evaluate(snapshot, _forecast([0.6]), goal)

    assert result.status == "UNSUPPORTED"
