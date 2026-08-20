"""Goal pacing built on qualified period-end forecasts."""

from __future__ import annotations

import math
from datetime import datetime

from .contracts import ForecastResult, GoalSpec, PaceResult, SeriesSnapshot


def _normal_cdf(value: float, mean: float, std: float) -> float:
    if std <= 1e-12:
        return 1.0 if value >= mean else 0.0
    return 0.5 * (1.0 + math.erf((value - mean) / (std * math.sqrt(2.0))))


def _goal_met(value: float, goal: GoalSpec) -> bool:
    direction = goal.favorable_direction.upper()
    if direction == "LOWER":
        return value <= goal.target_value
    if direction == "RANGE":
        lower = goal.lower_bound if goal.lower_bound is not None else goal.target_value
        upper = goal.upper_bound if goal.upper_bound is not None else goal.target_value
        return lower <= value <= upper
    return value >= goal.target_value


class PaceToGoalService:
    SUPPORTED_AGGREGATIONS = {"SUM", "COUNT"}

    def evaluate(self, snapshot: SeriesSnapshot, forecast: ForecastResult, goal: GoalSpec) -> PaceResult:
        aggregation = goal.aggregation_type.upper()
        if aggregation not in self.SUPPORTED_AGGREGATIONS:
            return self._unsupported(goal, forecast, f"首版 Pace to Goal 仅支持 SUM/COUNT，当前为 {aggregation}")

        start = datetime.fromisoformat(goal.period_start.replace("Z", "+00:00")).replace(tzinfo=None)
        end = datetime.fromisoformat(goal.period_end.replace("Z", "+00:00")).replace(tzinfo=None)
        actual_points = [point.value for point in snapshot.points if start <= point.period <= end]
        actual = float(sum(actual_points))
        remaining = [point for point in forecast.points if snapshot.points and snapshot.points[-1].period < point.period <= end]
        forecast_sum = float(sum(point.value for point in remaining))
        projected = actual + forecast_sum

        variances = []
        for point in remaining:
            sigma = max(0.0, (point.upper95 - point.lower95) / (2 * 1.959964))
            variances.append(sigma * sigma)
        projected_std = math.sqrt(sum(variances))
        projected_lower = projected - 1.959964 * projected_std
        projected_upper = projected + 1.959964 * projected_std

        direction = goal.favorable_direction.upper()
        if direction == "LOWER":
            probability = _normal_cdf(goal.target_value, projected, projected_std)
            gap = max(0.0, projected - goal.target_value)
        elif direction == "RANGE":
            lower = goal.lower_bound if goal.lower_bound is not None else goal.target_value
            upper = goal.upper_bound if goal.upper_bound is not None else goal.target_value
            probability = max(0.0, _normal_cdf(upper, projected, projected_std) - _normal_cdf(lower, projected, projected_std))
            gap = 0.0 if lower <= projected <= upper else min(abs(projected - lower), abs(projected - upper))
        else:
            probability = 1.0 - _normal_cdf(goal.target_value, projected, projected_std)
            gap = max(0.0, goal.target_value - projected)

        probability = max(0.0, min(1.0, probability))
        remaining_count = len(remaining)
        current_pace = actual / len(actual_points) if actual_points else None
        required_pace = None
        if remaining_count:
            if direction == "LOWER":
                required_pace = (goal.target_value - actual) / remaining_count
            else:
                required_pace = max(0.0, goal.target_value - actual) / remaining_count
        achievement_rate = actual / goal.target_value if goal.target_value else None

        if direction == "HIGHER" and _goal_met(actual, goal):
            status = "ACHIEVED"
        elif not remaining_count:
            status = "ACHIEVED" if _goal_met(actual, goal) else "OFF_TRACK"
        elif forecast.status not in {"QUALIFIED", "LOW_CONFIDENCE"}:
            status = "INSUFFICIENT_DATA"
        elif probability >= 0.7:
            status = "ON_TRACK"
        elif probability >= 0.3:
            status = "AT_RISK"
        else:
            status = "OFF_TRACK"

        return PaceResult(
            goal_id=goal.goal_id,
            metric_code=goal.metric_code,
            status=status,
            actual_to_date=round(actual, 8),
            target_value=goal.target_value,
            projected_end=round(projected, 8),
            projected_lower95=round(projected_lower, 8),
            projected_upper95=round(projected_upper, 8),
            achievement_rate=round(achievement_rate, 8) if achievement_rate is not None else None,
            probability=round(probability, 8),
            remaining_periods=remaining_count,
            required_pace=round(required_pace, 8) if required_pace is not None else None,
            current_pace=round(current_pace, 8) if current_pace is not None else None,
            gap=round(gap, 8),
            forecast_run_id=forecast.run_id,
            reason="" if forecast.status == "QUALIFIED" else forecast.reason,
        )

    @staticmethod
    def _unsupported(goal: GoalSpec, forecast: ForecastResult, reason: str) -> PaceResult:
        return PaceResult(
            goal_id=goal.goal_id,
            metric_code=goal.metric_code,
            status="UNSUPPORTED",
            actual_to_date=0.0,
            target_value=goal.target_value,
            projected_end=0.0,
            projected_lower95=0.0,
            projected_upper95=0.0,
            achievement_rate=None,
            probability=0.0,
            remaining_periods=0,
            required_pace=None,
            current_pace=None,
            gap=0.0,
            forecast_run_id=forecast.run_id,
            reason=reason,
        )
