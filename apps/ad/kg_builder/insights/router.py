"""Independent APIs for Forecast, Pace to Goal, and cross-metric discovery."""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
from datetime import datetime
from typing import Any, Callable, Optional

from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel, ConfigDict, Field

from .contracts import GoalSpec, InsightFact, MetricSeriesSpec, stable_hash
from .cross_metric import (
    CommonContributorAnalyzer,
    CrossMetricEngine,
    MetricCandidateBuilder,
    joint_goal_risk,
)
from .forecast import ForecastEngine
from .pace import PaceToGoalService
from .repository import get_insight_store, init_insight_store
from .series import MetricSeriesService, normalize_grain, periods_between


logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/insights", tags=["insights"])

SeriesLoader = Callable[[dict[str, Any], str], dict[str, Any]]
GoalLoader = Callable[[str, str], dict[str, Any]]
CatalogLoader = Callable[[], dict[str, Any]]

_series_loader: SeriesLoader | None = None
_goal_loader: GoalLoader | None = None
_catalog_loader: CatalogLoader | None = None


def configure_insight_runtime(
    *,
    series_loader: SeriesLoader | None = None,
    goal_loader: GoalLoader | None = None,
    catalog_loader: CatalogLoader | None = None,
) -> None:
    global _series_loader, _goal_loader, _catalog_loader
    if series_loader is not None:
        _series_loader = series_loader
    if goal_loader is not None:
        _goal_loader = goal_loader
    if catalog_loader is not None:
        _catalog_loader = catalog_loader


class _Model(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class PointInput(_Model):
    period: str
    value: float


class SeriesInput(_Model):
    metric_code: str = Field(alias="metricCode")
    time_dimension: str = Field(alias="timeDimension")
    grain: str
    start: str = ""
    end: str = ""
    filters: list[dict[str, Any]] = Field(default_factory=list)
    space_id: int | None = Field(default=None, alias="spaceId")
    permission_scope_hash: str = Field(default="", alias="permissionScopeHash")
    fill_policy: str = Field(default="none", alias="fillPolicy")
    aggregation_type: str = Field(default="SUM", alias="aggregationType")
    series: list[PointInput] | None = None


class ForecastRequest(SeriesInput):
    horizon: int = 3
    seasonal_period: int | None = Field(default=None, alias="seasonalPeriod")


class GoalInput(_Model):
    goal_id: str = Field(alias="goalId")
    metric_code: str = Field(alias="metricCode")
    target_value: float = Field(alias="targetValue")
    period_start: str = Field(alias="periodStart")
    period_end: str = Field(alias="periodEnd")
    aggregation_type: str = Field(default="SUM", alias="aggregationType")
    favorable_direction: str = Field(default="HIGHER", alias="favorableDirection")
    lower_bound: float | None = Field(default=None, alias="lowerBound")
    upper_bound: float | None = Field(default=None, alias="upperBound")
    calendar_code: str = Field(default="NATURAL", alias="calendarCode")
    filters: list[dict[str, Any]] = Field(default_factory=list)
    timezone: str = "Asia/Shanghai"


class PaceRequest(_Model):
    goal: GoalInput
    series_spec: SeriesInput = Field(alias="seriesSpec")
    seasonal_period: int | None = Field(default=None, alias="seasonalPeriod")


class CandidateInput(_Model):
    metric_a: str = Field(alias="metricA")
    metric_b: str = Field(alias="metricB")
    relation_source: str = Field(default="manual", alias="relationSource")
    common_dimensions: list[str] = Field(default_factory=list, alias="commonDimensions")
    common_tables: list[str] = Field(default_factory=list, alias="commonTables")
    common_grains: list[str] = Field(default_factory=list, alias="commonGrains")
    kg_path: str = Field(default="", alias="kgPath")
    business_priority: float = Field(default=1.0, alias="businessPriority")
    enabled: bool = True


class ComparisonWindow(_Model):
    current_start: str = Field(alias="currentStart")
    current_end: str = Field(alias="currentEnd")
    previous_start: str = Field(alias="previousStart")
    previous_end: str = Field(alias="previousEnd")


class CrossMetricRequest(_Model):
    metric_codes: list[str] = Field(alias="metricCodes")
    time_dimension: str = Field(alias="timeDimension")
    grain: str
    start: str
    end: str
    filters: list[dict[str, Any]] = Field(default_factory=list)
    space_id: int | None = Field(default=None, alias="spaceId")
    permission_scope_hash: str = Field(default="", alias="permissionScopeHash")
    fill_policy: str = Field(default="none", alias="fillPolicy")
    series: dict[str, list[PointInput]] = Field(default_factory=dict)
    candidates: list[CandidateInput] = Field(default_factory=list)
    breakdown_dimensions: list[str] = Field(default_factory=list, alias="breakdownDimensions")
    comparison_window: ComparisonWindow | None = Field(default=None, alias="comparisonWindow")
    contribution_vectors: dict[str, dict[str, dict[str, float]]] = Field(default_factory=dict, alias="contributionVectors")
    pace_results: list[dict[str, Any]] = Field(default_factory=list, alias="paceResults")
    min_points: int = Field(default=30, alias="minPoints")
    min_coverage: float = Field(default=0.8, alias="minCoverage")
    min_effect: float = Field(default=0.35, alias="minEffect")
    max_q_value: float = Field(default=0.05, alias="maxQValue")
    max_lag: int = Field(default=6, alias="maxLag")


def _authorization(request: Request) -> str:
    return str(request.headers.get("authorization") or "")


def _scope_hash(explicit: str, authorization: str, context: Any) -> str:
    raw = str(explicit or "").strip()
    if raw:
        return raw if len(raw) == 64 else hashlib.sha256(raw.encode("utf-8")).hexdigest()
    if authorization:
        return hashlib.sha256(authorization.encode("utf-8")).hexdigest()
    return stable_hash({"principal": "anonymous"})


def _series_spec(item: SeriesInput, scope_hash: str) -> MetricSeriesSpec:
    return MetricSeriesSpec(
        metric_code=item.metric_code,
        time_dimension=item.time_dimension,
        grain=normalize_grain(item.grain),
        start=item.start,
        end=item.end,
        filters=tuple(item.filters),
        space_id=item.space_id,
        permission_scope_hash=scope_hash,
        fill_policy=item.fill_policy,
        aggregation_type=item.aggregation_type,
    )


def _goal_spec(item: GoalInput) -> GoalSpec:
    return GoalSpec(
        goal_id=item.goal_id,
        metric_code=item.metric_code,
        target_value=item.target_value,
        period_start=item.period_start,
        period_end=item.period_end,
        aggregation_type=item.aggregation_type,
        favorable_direction=item.favorable_direction,
        lower_bound=item.lower_bound,
        upper_bound=item.upper_bound,
        calendar_code=item.calendar_code,
        filters=tuple(item.filters),
        timezone=item.timezone,
    )


def _safe_persist_forecast(result, scope_hash: str) -> None:
    try:
        get_insight_store().save_forecast(result, scope_hash)
    except Exception as exc:
        logger.warning("Forecast persistence failed: %s", exc)


def _safe_persist_fact(fact: InsightFact) -> None:
    try:
        get_insight_store().save_fact(fact)
    except Exception as exc:
        logger.warning("Insight fact persistence failed: %s", exc)


def _forecast_fact(snapshot, result) -> InsightFact | None:
    if not snapshot.points or not result.points:
        return None
    return InsightFact.create(
        fact_type="FORECAST",
        metric_codes=[snapshot.spec.metric_code],
        permission_scope_hash=snapshot.spec.permission_scope_hash,
        metric_scope_hash=stable_hash(snapshot.spec.identity()),
        grain=snapshot.spec.grain,
        window_start=snapshot.points[0].period.date().isoformat(),
        window_end=snapshot.points[-1].period.date().isoformat(),
        status=result.status,
        impact_score=result.confidence * 100.0,
        confidence=result.confidence,
        payload=result.to_dict(),
        evidence={"series": snapshot.to_dict(include_points=False), "query": snapshot.query},
        model_version=result.model_version,
    )


async def _run_forecast(body: ForecastRequest, request: Request) -> dict[str, Any]:
    authorization = _authorization(request)
    scope_hash = _scope_hash(body.permission_scope_hash, authorization, body.model_dump(by_alias=True, exclude={"series"}))
    spec = _series_spec(body, scope_hash)
    supplied = [point.model_dump() for point in body.series] if body.series is not None else None
    service = MetricSeriesService(_series_loader)
    snapshot = await asyncio.to_thread(service.load, spec, authorization=authorization, supplied_points=supplied)
    result = await asyncio.to_thread(ForecastEngine().forecast, snapshot, body.horizon, seasonal_period=body.seasonal_period)
    await asyncio.to_thread(_safe_persist_forecast, result, scope_hash)
    fact = _forecast_fact(snapshot, result)
    if fact:
        await asyncio.to_thread(_safe_persist_fact, fact)
    return {
        "ok": result.status not in {"FAILED"},
        "snapshot": snapshot.to_dict(include_points=False),
        "forecast": result.to_dict(),
        "fact": fact.to_dict() if fact else None,
    }


@router.post("/forecast")
async def forecast_metric(body: ForecastRequest, request: Request):
    try:
        return await _run_forecast(body, request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


async def _run_pace(body: PaceRequest, request: Request) -> dict[str, Any]:
    authorization = _authorization(request)
    series_input = body.series_spec.model_copy(update={"metric_code": body.goal.metric_code, "aggregation_type": body.goal.aggregation_type})
    scope_hash = _scope_hash(series_input.permission_scope_hash, authorization, body.model_dump(by_alias=True, exclude={"series_spec": {"series"}}))
    spec = _series_spec(series_input, scope_hash)
    supplied = [point.model_dump() for point in series_input.series] if series_input.series is not None else None
    service = MetricSeriesService(_series_loader)
    snapshot = await asyncio.to_thread(service.load, spec, authorization=authorization, supplied_points=supplied)
    if not snapshot.points:
        raise ValueError("没有可用于 Pace to Goal 的历史时序")
    goal = _goal_spec(body.goal)
    goal_end = datetime.fromisoformat(goal.period_end.replace("Z", "+00:00")).replace(tzinfo=None)
    horizon = max(1, periods_between(snapshot.points[-1].period, goal_end, spec.grain))
    forecast = await asyncio.to_thread(ForecastEngine().forecast, snapshot, horizon, seasonal_period=body.seasonal_period)
    pace = PaceToGoalService().evaluate(snapshot, forecast, goal)
    await asyncio.to_thread(_safe_persist_forecast, forecast, scope_hash)
    fact = InsightFact.create(
        fact_type="PACE_TO_GOAL",
        metric_codes=[goal.metric_code],
        permission_scope_hash=scope_hash,
        metric_scope_hash=stable_hash(spec.identity()),
        grain=spec.grain,
        window_start=goal.period_start,
        window_end=goal.period_end,
        status=pace.status,
        impact_score=max(0.0, min(100.0, (1.0 - pace.probability) * 100.0)),
        confidence=forecast.confidence,
        payload={"pace": pace.to_dict(), "forecast": forecast.to_dict()},
        evidence={"series": snapshot.to_dict(include_points=False), "query": snapshot.query},
        model_version="pace-to-goal-v1",
    )
    await asyncio.to_thread(_safe_persist_fact, fact)
    return {
        "ok": pace.status not in {"UNSUPPORTED"},
        "snapshot": snapshot.to_dict(include_points=False),
        "forecast": forecast.to_dict(),
        "pace": pace.to_dict(),
        "fact": fact.to_dict(),
    }


@router.post("/goals/pace")
async def pace_to_goal(body: PaceRequest, request: Request):
    try:
        return await _run_pace(body, request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


def _goal_payload(raw: dict[str, Any]) -> dict[str, Any]:
    payload = raw.get("data") if isinstance(raw.get("data"), dict) else raw
    if not isinstance(payload, dict):
        raise ValueError("DA 未返回有效目标")
    return payload


def _goal_filters(payload: dict[str, Any]) -> list[dict[str, Any]]:
    value = payload.get("filters")
    if isinstance(value, list):
        return value
    raw = payload.get("filtersJson")
    if isinstance(raw, str) and raw.strip():
        try:
            parsed = json.loads(raw)
            return parsed if isinstance(parsed, list) else []
        except json.JSONDecodeError:
            return []
    return []


def _default_history_start(period_start: str, grain: str) -> str:
    import pandas as pd

    start = pd.Timestamp(period_start)
    offsets = {
        "day": pd.DateOffset(days=180),
        "week": pd.DateOffset(weeks=104),
        "month": pd.DateOffset(months=36),
        "quarter": pd.DateOffset(months=48),
        "year": pd.DateOffset(years=10),
    }
    return (start - offsets[normalize_grain(grain)]).date().isoformat()


@router.get("/goals/{goal_id}/pace")
async def pace_to_goal_by_id(
    goal_id: str,
    request: Request,
    time_dimension: str = Query("", alias="timeDimension"),
    grain: str = Query("month"),
    history_start: str = Query("", alias="historyStart"),
    permission_scope_hash: str = Query("", alias="permissionScopeHash"),
):
    if _goal_loader is None:
        raise HTTPException(status_code=503, detail="尚未配置 DA 目标加载器")
    authorization = _authorization(request)
    try:
        raw = await asyncio.to_thread(_goal_loader, goal_id, authorization)
        payload = _goal_payload(raw)
        period_start = str(payload.get("periodStart") or payload.get("dateValue") or "")
        period_end = str(payload.get("periodEnd") or payload.get("dateValue") or "")
        metric_code = str(payload.get("measureCode") or "")
        dimension = time_dimension or str(payload.get("dateDimCode") or "")
        if not all([period_start, period_end, metric_code, dimension]):
            raise ValueError("目标缺少 periodStart/periodEnd/measureCode/dateDimCode")
        filters = _goal_filters(payload)
        series = SeriesInput(
            metricCode=metric_code,
            timeDimension=dimension,
            grain=grain,
            start=history_start or _default_history_start(period_start, grain),
            end=datetime.now().date().isoformat(),
            filters=filters,
            spaceId=payload.get("spaceId"),
            permissionScopeHash=permission_scope_hash,
            aggregationType=str(payload.get("aggregationType") or "SUM"),
        )
        body = PaceRequest(
            goal=GoalInput(
                goalId=str(payload.get("id") or goal_id),
                metricCode=metric_code,
                targetValue=float(payload.get("targetValue") or payload.get("targetNum")),
                periodStart=period_start,
                periodEnd=period_end,
                aggregationType=str(payload.get("aggregationType") or "SUM"),
                favorableDirection=str(payload.get("favorableDirection") or "HIGHER"),
                lowerBound=payload.get("lowerBound"),
                upperBound=payload.get("upperBound"),
                calendarCode=str(payload.get("calendarCode") or "NATURAL"),
                filters=filters,
                timezone=str(payload.get("timezone") or "Asia/Shanghai"),
            ),
            seriesSpec=series,
            seasonalPeriod=payload.get("seasonalPeriod"),
        )
        return await _run_pace(body, request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


def _candidate_dict(item: CandidateInput) -> dict[str, Any]:
    return item.model_dump(by_alias=True)


async def _breakdown_contributors(
    body: CrossMetricRequest,
    specs: dict[str, MetricSeriesSpec],
    authorization: str,
    candidates: list[dict[str, Any]],
) -> dict[str, list[dict[str, Any]]]:
    analyzer = CommonContributorAnalyzer()
    result: dict[str, list[dict[str, Any]]] = {}
    supplied_vectors = body.contribution_vectors
    for candidate in candidates:
        pair = [candidate["metricA"], candidate["metricB"]]
        pair_key = "|".join(sorted(pair))
        rows: list[dict[str, Any]] = []
        for dimension in body.breakdown_dimensions or supplied_vectors.keys():
            vectors: dict[str, dict[str, float]] = {}
            if dimension in supplied_vectors:
                vectors = {metric: supplied_vectors[dimension].get(metric, {}) for metric in pair}
            elif body.comparison_window and _series_loader is not None:
                service = MetricSeriesService(_series_loader)
                for metric in pair:
                    current, previous = await asyncio.gather(
                        asyncio.to_thread(service.load_breakdown, specs[metric], dimension, body.comparison_window.current_start, body.comparison_window.current_end, authorization=authorization),
                        asyncio.to_thread(service.load_breakdown, specs[metric], dimension, body.comparison_window.previous_start, body.comparison_window.previous_end, authorization=authorization),
                    )
                    vectors[metric] = analyzer.contribution_vector(current, previous)
            compared = analyzer.compare(vectors)
            for row in compared:
                rows.append({"dimension": dimension, **row})
        if rows:
            result[pair_key] = sorted(rows, key=lambda item: -item["score"])[:20]
    return result


async def _run_cross_metric(body: CrossMetricRequest, request: Request) -> dict[str, Any]:
    metric_codes = list(dict.fromkeys(body.metric_codes))
    if len(metric_codes) < 2:
        raise ValueError("跨指标分析至少需要两个指标")
    authorization = _authorization(request)
    scope_hash = _scope_hash(body.permission_scope_hash, authorization, body.model_dump(by_alias=True, exclude={"series", "contribution_vectors"}))
    specs = {
        code: MetricSeriesSpec(
            metric_code=code,
            time_dimension=body.time_dimension,
            grain=normalize_grain(body.grain),
            start=body.start,
            end=body.end,
            filters=tuple(body.filters),
            space_id=body.space_id,
            permission_scope_hash=scope_hash,
            fill_policy=body.fill_policy,
        )
        for code in metric_codes
    }
    supplied = {code: [point.model_dump() for point in points] for code, points in body.series.items()}
    snapshots = await asyncio.to_thread(
        MetricSeriesService(_series_loader).load_many,
        list(specs.values()),
        authorization=authorization,
        supplied=supplied,
    )

    manual = [_candidate_dict(item) for item in body.candidates]
    try:
        manual.extend(await asyncio.to_thread(get_insight_store().list_candidates, metric_codes))
    except Exception as exc:
        logger.warning("Metric candidate persistence unavailable: %s", exc)
    catalog = _catalog_loader() if _catalog_loader is not None else {"measures": []}
    candidates = MetricCandidateBuilder().build(catalog, metric_codes, manual_candidates=manual)
    if not candidates and supplied:
        candidates = [
            {"metricA": left, "metricB": right, "relationSource": "supplied", "businessPriority": 0.7}
            for index, left in enumerate(metric_codes)
            for right in metric_codes[index + 1:]
        ]

    engine = CrossMetricEngine(
        min_points=body.min_points,
        min_coverage=body.min_coverage,
        min_effect=body.min_effect,
        max_q_value=body.max_q_value,
        max_lag=body.max_lag,
    )
    facts = await asyncio.to_thread(engine.discover, snapshots, candidates)
    contributors = await _breakdown_contributors(body, specs, authorization, candidates)
    for fact in facts:
        pair_key = "|".join(sorted(fact.metric_codes))
        if pair_key in contributors:
            fact.payload["sharedContributors"] = contributors[pair_key]
        await asyncio.to_thread(_safe_persist_fact, fact)

    return {
        "ok": True,
        "metricCount": len(snapshots),
        "candidateCount": len(candidates),
        "factCount": len(facts),
        "snapshots": {code: snapshot.to_dict(include_points=False) for code, snapshot in snapshots.items()},
        "candidates": candidates,
        "facts": [fact.to_dict() for fact in facts],
        "commonContributors": contributors,
        "jointGoalRisk": joint_goal_risk(body.pace_results),
    }


@router.post("/cross-metric/discover")
async def discover_cross_metric(body: CrossMetricRequest, request: Request):
    try:
        return await _run_cross_metric(body, request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@router.post("/cross-metric/candidates")
async def save_metric_candidate(body: CandidateInput):
    try:
        candidate = await asyncio.to_thread(get_insight_store().save_candidate, _candidate_dict(body))
        return {"ok": True, "candidate": candidate}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"候选关系存储不可用: {exc}") from exc


@router.get("/cross-metric/candidates")
async def list_metric_candidates(metric_codes: str = Query("", alias="metricCodes")):
    codes = [item.strip() for item in metric_codes.split(",") if item.strip()]
    if len(codes) < 2:
        raise HTTPException(status_code=400, detail="metricCodes 至少包含两个逗号分隔的指标")
    try:
        candidates = await asyncio.to_thread(get_insight_store().list_candidates, codes)
        return {"candidates": candidates, "total": len(candidates)}
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"候选关系存储不可用: {exc}") from exc


@router.get("/facts")
async def list_insight_facts(
    request: Request,
    permission_scope_hash: str = Query("", alias="permissionScopeHash"),
    fact_type: str = Query("", alias="factType"),
    metric_code: str = Query("", alias="metricCode"),
    limit: int = 100,
):
    scope_hash = _scope_hash(permission_scope_hash, _authorization(request), {"metricCode": metric_code})
    try:
        facts = await asyncio.to_thread(
            get_insight_store().list_facts,
            permission_scope_hash=scope_hash,
            fact_type=fact_type,
            metric_code=metric_code,
            limit=limit,
        )
        return {"facts": facts, "total": len(facts)}
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"洞察存储不可用: {exc}") from exc


@router.post("/jobs/run")
async def run_insight_job(body: dict[str, Any], request: Request):
    job_type = str(body.get("jobType") or "").strip().lower()
    payload = body.get("payload") or {}
    if job_type == "forecast":
        return await _run_forecast(ForecastRequest.model_validate(payload), request)
    if job_type in {"pace", "pace_to_goal"}:
        return await _run_pace(PaceRequest.model_validate(payload), request)
    if job_type in {"cross_metric", "cross-metric"}:
        return await _run_cross_metric(CrossMetricRequest.model_validate(payload), request)
    raise HTTPException(status_code=400, detail="jobType 必须是 forecast、pace_to_goal 或 cross_metric")


@router.get("/{fact_id}")
async def get_insight_fact(
    fact_id: str,
    request: Request,
    permission_scope_hash: str = Query("", alias="permissionScopeHash"),
):
    scope_hash = _scope_hash(permission_scope_hash, _authorization(request), {"factId": fact_id})
    try:
        fact = await asyncio.to_thread(get_insight_store().get_fact, fact_id, scope_hash)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"洞察存储不可用: {exc}") from exc
    if not fact:
        raise HTTPException(status_code=404, detail="洞察不存在或无权访问")
    return fact
