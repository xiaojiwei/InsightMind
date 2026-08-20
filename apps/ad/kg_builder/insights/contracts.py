"""Typed contracts shared by forecasting, pace, and cross-metric detection."""

from __future__ import annotations

import hashlib
import json
import math
import uuid
from dataclasses import asdict, dataclass, field
from datetime import date, datetime, timezone
from typing import Any


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def json_default(value: Any) -> Any:
    if isinstance(value, (date, datetime)):
        return value.isoformat()
    if hasattr(value, "item"):
        return value.item()
    raise TypeError(f"Object of type {type(value).__name__} is not JSON serializable")


def compact_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=json_default)


def stable_hash(value: Any) -> str:
    return hashlib.sha256(compact_json(value).encode("utf-8")).hexdigest()


def finite_float(value: Any) -> float | None:
    try:
        parsed = float(str(value).replace(",", ""))
    except (TypeError, ValueError):
        return None
    return parsed if math.isfinite(parsed) else None


@dataclass(frozen=True)
class SeriesPoint:
    period: datetime
    value: float

    def to_dict(self) -> dict[str, Any]:
        return {"period": self.period.date().isoformat(), "value": round(float(self.value), 8)}


@dataclass(frozen=True)
class MetricSeriesSpec:
    metric_code: str
    time_dimension: str
    grain: str
    start: str = ""
    end: str = ""
    filters: tuple[dict[str, Any], ...] = ()
    space_id: int | None = None
    permission_scope_hash: str = "anonymous"
    fill_policy: str = "none"
    aggregation_type: str = "SUM"

    def identity(self) -> dict[str, Any]:
        return {
            "metricCode": self.metric_code,
            "timeDimension": self.time_dimension,
            "grain": self.grain,
            "start": self.start,
            "end": self.end,
            "filters": list(self.filters),
            "spaceId": self.space_id,
            "permissionScopeHash": self.permission_scope_hash,
        }


@dataclass
class SeriesSnapshot:
    spec: MetricSeriesSpec
    points: list[SeriesPoint]
    expected_points: int
    missing_points: int
    duplicate_points: int = 0
    warnings: list[str] = field(default_factory=list)
    query: dict[str, Any] = field(default_factory=dict)
    diagnostics: dict[str, Any] = field(default_factory=dict)

    @property
    def missing_rate(self) -> float:
        return self.missing_points / self.expected_points if self.expected_points else 0.0

    @property
    def values(self) -> list[float]:
        return [point.value for point in self.points]

    def to_dict(self, *, include_points: bool = True) -> dict[str, Any]:
        payload = {
            "metricCode": self.spec.metric_code,
            "timeDimension": self.spec.time_dimension,
            "grain": self.spec.grain,
            "start": self.points[0].period.date().isoformat() if self.points else self.spec.start,
            "end": self.points[-1].period.date().isoformat() if self.points else self.spec.end,
            "pointCount": len(self.points),
            "expectedPoints": self.expected_points,
            "missingPoints": self.missing_points,
            "missingRate": round(self.missing_rate, 6),
            "duplicatePoints": self.duplicate_points,
            "warnings": list(self.warnings),
            "query": self.query,
            "diagnostics": self.diagnostics,
        }
        if include_points:
            payload["points"] = [point.to_dict() for point in self.points]
        return payload


@dataclass(frozen=True)
class ForecastPoint:
    period: datetime
    value: float
    lower80: float
    upper80: float
    lower95: float
    upper95: float

    def to_dict(self) -> dict[str, Any]:
        return {
            "period": self.period.date().isoformat(),
            "value": round(float(self.value), 8),
            "lower80": round(float(self.lower80), 8),
            "upper80": round(float(self.upper80), 8),
            "lower95": round(float(self.lower95), 8),
            "upper95": round(float(self.upper95), 8),
        }


@dataclass
class ForecastResult:
    run_id: str
    metric_code: str
    status: str
    model_name: str
    horizon: int
    points: list[ForecastPoint]
    quality_score: float
    confidence: float
    backtest: dict[str, Any]
    data_quality: dict[str, Any]
    model_version: str = "forecast-v1"
    reason: str = ""
    generated_at: datetime = field(default_factory=utc_now)

    def to_dict(self) -> dict[str, Any]:
        return {
            "runId": self.run_id,
            "metricCode": self.metric_code,
            "status": self.status,
            "reason": self.reason,
            "model": self.model_name,
            "modelVersion": self.model_version,
            "horizon": self.horizon,
            "qualityScore": round(float(self.quality_score), 6),
            "confidence": round(float(self.confidence), 6),
            "backtest": self.backtest,
            "dataQuality": self.data_quality,
            "forecast": [point.to_dict() for point in self.points],
            "generatedAt": self.generated_at.isoformat(),
        }


@dataclass(frozen=True)
class GoalSpec:
    goal_id: str
    metric_code: str
    target_value: float
    period_start: str
    period_end: str
    aggregation_type: str = "SUM"
    favorable_direction: str = "HIGHER"
    lower_bound: float | None = None
    upper_bound: float | None = None
    calendar_code: str = "NATURAL"
    filters: tuple[dict[str, Any], ...] = ()
    timezone: str = "Asia/Shanghai"


@dataclass
class PaceResult:
    goal_id: str
    metric_code: str
    status: str
    actual_to_date: float
    target_value: float
    projected_end: float
    projected_lower95: float
    projected_upper95: float
    achievement_rate: float | None
    probability: float
    remaining_periods: int
    required_pace: float | None
    current_pace: float | None
    gap: float
    forecast_run_id: str
    reason: str = ""

    def to_dict(self) -> dict[str, Any]:
        value = asdict(self)
        aliases = {
            "goal_id": "goalId",
            "metric_code": "metricCode",
            "actual_to_date": "actualToDate",
            "target_value": "targetValue",
            "projected_end": "projectedEnd",
            "projected_lower95": "projectedLower95",
            "projected_upper95": "projectedUpper95",
            "achievement_rate": "achievementRate",
            "remaining_periods": "remainingPeriods",
            "required_pace": "requiredPace",
            "current_pace": "currentPace",
            "forecast_run_id": "forecastRunId",
        }
        return {aliases.get(key, key): val for key, val in value.items()}


@dataclass
class InsightFact:
    fact_id: str
    fact_type: str
    metric_codes: list[str]
    permission_scope_hash: str
    metric_scope_hash: str
    grain: str
    window_start: str
    window_end: str
    status: str
    impact_score: float
    confidence: float
    payload: dict[str, Any]
    evidence: dict[str, Any]
    model_version: str
    dedupe_key: str
    generated_at: datetime = field(default_factory=utc_now)
    expires_at: datetime | None = None

    @classmethod
    def create(
        cls,
        *,
        fact_type: str,
        metric_codes: list[str],
        permission_scope_hash: str,
        metric_scope_hash: str,
        grain: str,
        window_start: str,
        window_end: str,
        status: str,
        impact_score: float,
        confidence: float,
        payload: dict[str, Any],
        evidence: dict[str, Any],
        model_version: str,
    ) -> "InsightFact":
        identity = {
            "type": fact_type,
            "metrics": sorted(metric_codes),
            "scope": permission_scope_hash,
            "metricScope": metric_scope_hash,
            "grain": grain,
            "start": window_start,
            "end": window_end,
            "status": status,
            "modelVersion": model_version,
        }
        return cls(
            fact_id=f"fact_{uuid.uuid4().hex}",
            fact_type=fact_type,
            metric_codes=list(metric_codes),
            permission_scope_hash=permission_scope_hash,
            metric_scope_hash=metric_scope_hash,
            grain=grain,
            window_start=window_start,
            window_end=window_end,
            status=status,
            impact_score=max(0.0, min(100.0, float(impact_score))),
            confidence=max(0.0, min(1.0, float(confidence))),
            payload=payload,
            evidence=evidence,
            model_version=model_version,
            dedupe_key=stable_hash(identity),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "factId": self.fact_id,
            "factType": self.fact_type,
            "metricCodes": self.metric_codes,
            "permissionScopeHash": self.permission_scope_hash,
            "metricScopeHash": self.metric_scope_hash,
            "grain": self.grain,
            "windowStart": self.window_start,
            "windowEnd": self.window_end,
            "status": self.status,
            "impactScore": round(float(self.impact_score), 6),
            "confidence": round(float(self.confidence), 6),
            "payload": self.payload,
            "evidence": self.evidence,
            "modelVersion": self.model_version,
            "dedupeKey": self.dedupe_key,
            "generatedAt": self.generated_at.isoformat(),
            "expiresAt": self.expires_at.isoformat() if self.expires_at else None,
        }
