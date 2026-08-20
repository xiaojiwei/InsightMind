"""Forecasting with rolling-origin backtests and calibrated confidence bands."""

from __future__ import annotations

import math
import uuid
from dataclasses import dataclass
from typing import Any, Callable

import numpy as np

from .contracts import ForecastPoint, ForecastResult, SeriesSnapshot
from .series import next_periods, normalize_grain


MODEL_VERSION = "forecast-v1"
DEFAULT_SEASONAL_PERIODS = {"day": 7, "week": 52, "month": 12, "quarter": 4}


@dataclass
class _Candidate:
    name: str
    forecast: Callable[[np.ndarray, int, int | None], np.ndarray]
    requires_seasonality: bool = False


def _naive(values: np.ndarray, horizon: int, _season: int | None) -> np.ndarray:
    return np.repeat(values[-1], horizon).astype(float)


def _drift(values: np.ndarray, horizon: int, _season: int | None) -> np.ndarray:
    slope = (values[-1] - values[0]) / max(1, len(values) - 1)
    return np.asarray([values[-1] + slope * step for step in range(1, horizon + 1)], dtype=float)


def _seasonal_naive(values: np.ndarray, horizon: int, season: int | None) -> np.ndarray:
    if not season or len(values) < season:
        raise ValueError("季节数据不足")
    pattern = values[-season:]
    return np.asarray([pattern[index % season] for index in range(horizon)], dtype=float)


def _holt(values: np.ndarray, horizon: int, _season: int | None) -> np.ndarray:
    from statsmodels.tsa.holtwinters import Holt

    fit = Holt(values, damped_trend=True, initialization_method="estimated").fit(optimized=True)
    return np.asarray(fit.forecast(horizon), dtype=float)


def _ets(values: np.ndarray, horizon: int, season: int | None) -> np.ndarray:
    from statsmodels.tsa.holtwinters import ExponentialSmoothing

    kwargs: dict[str, Any] = {
        "trend": "add",
        "damped_trend": True,
        "initialization_method": "estimated",
    }
    if season and len(values) >= season * 2:
        kwargs.update({"seasonal": "add", "seasonal_periods": season})
    fit = ExponentialSmoothing(values, **kwargs).fit(optimized=True, remove_bias=True)
    return np.asarray(fit.forecast(horizon), dtype=float)


def _metrics(actual: np.ndarray, predicted: np.ndarray, train: np.ndarray, season: int | None) -> dict[str, float]:
    errors = actual - predicted
    absolute = np.abs(errors)
    denominator = np.maximum(np.abs(actual) + np.abs(predicted), 1e-9)
    smape = float(np.mean(2.0 * absolute / denominator))
    wape = float(np.sum(absolute) / max(float(np.sum(np.abs(actual))), 1e-9))
    lag = season if season and len(train) > season else 1
    scale_values = np.abs(train[lag:] - train[:-lag]) if len(train) > lag else np.asarray([])
    scale = float(np.mean(scale_values)) if len(scale_values) else 0.0
    mase = float(np.mean(absolute) / max(scale, 1e-9))
    return {"mase": mase, "wape": wape, "smape": smape, "mae": float(np.mean(absolute))}


class ForecastEngine:
    def __init__(
        self,
        *,
        min_points: int = 20,
        max_missing_rate: float = 0.2,
        min_confidence: float = 0.55,
        max_folds: int = 5,
    ) -> None:
        self.min_points = min_points
        self.max_missing_rate = max_missing_rate
        self.min_confidence = min_confidence
        self.max_folds = max_folds

    def forecast(
        self,
        snapshot: SeriesSnapshot,
        horizon: int,
        *,
        seasonal_period: int | None = None,
    ) -> ForecastResult:
        horizon = max(1, min(int(horizon), 365))
        values = np.asarray(snapshot.values, dtype=float)
        quality = self._data_quality(snapshot, values)
        if len(values) < self.min_points:
            return self._empty(snapshot, horizon, "INSUFFICIENT_DATA", f"至少需要 {self.min_points} 个周期，当前只有 {len(values)} 个", quality)
        if snapshot.missing_rate > self.max_missing_rate:
            return self._empty(snapshot, horizon, "INSUFFICIENT_DATA", f"缺失率 {snapshot.missing_rate:.1%} 超过上限 {self.max_missing_rate:.0%}", quality)

        grain = normalize_grain(snapshot.spec.grain)
        season = seasonal_period if seasonal_period is not None else DEFAULT_SEASONAL_PERIODS.get(grain)
        if season and len(values) < season * 2:
            season = None
        candidates = [
            _Candidate("naive", _naive),
            _Candidate("drift", _drift),
            _Candidate("holt_damped", _holt),
            _Candidate("seasonal_naive", _seasonal_naive, True),
            _Candidate("ets", _ets),
        ]
        if season is None:
            candidates = [candidate for candidate in candidates if not candidate.requires_seasonality]

        evaluation_horizon = min(horizon, max(1, min(12, len(values) // 6)))
        evaluated = self._backtest(values, candidates, evaluation_horizon, season)
        if not evaluated:
            return self._empty(snapshot, horizon, "FAILED", "所有候选模型均未通过回测", quality)

        ranked = sorted(evaluated.values(), key=lambda item: (item["metrics"]["mase"], item["metrics"]["wape"]))
        best = ranked[0]
        baseline_name = "seasonal_naive" if "seasonal_naive" in evaluated else "naive"
        baseline = evaluated[baseline_name]
        baseline_mase = baseline["metrics"]["mase"]
        improvement = (baseline_mase - best["metrics"]["mase"]) / max(baseline_mase, 1e-9)

        candidate = next(item for item in candidates if item.name == best["name"])
        try:
            prediction = candidate.forecast(values, horizon, season)
        except Exception as exc:
            return self._empty(snapshot, horizon, "FAILED", f"最佳模型拟合失败: {exc}", quality)

        errors = np.asarray(best["errors"], dtype=float)
        if len(errors) < 3:
            errors = np.diff(values)
        sigma = float(np.std(errors, ddof=1)) if len(errors) > 1 else 0.0
        coverage80 = float(np.mean(np.abs(errors) <= 1.281552 * sigma)) if sigma > 0 and len(errors) else 1.0
        coverage95 = float(np.mean(np.abs(errors) <= 1.959964 * sigma)) if sigma > 0 and len(errors) else 1.0
        future_periods = next_periods(snapshot.points[-1].period, grain, horizon)
        points = []
        for index, (period, predicted) in enumerate(zip(future_periods, prediction), start=1):
            width = sigma * math.sqrt(index)
            points.append(ForecastPoint(
                period=period,
                value=float(predicted),
                lower80=float(predicted - 1.281552 * width),
                upper80=float(predicted + 1.281552 * width),
                lower95=float(predicted - 1.959964 * width),
                upper95=float(predicted + 1.959964 * width),
            ))

        fold_mases = best.get("foldMase") or []
        stability = 1.0 / (1.0 + float(np.std(fold_mases))) if fold_mases else 0.5
        error_score = 1.0 / (1.0 + best["metrics"]["mase"])
        improvement_score = max(0.0, min(1.0, 0.5 + improvement))
        calibration_score = max(0.0, 1.0 - (abs(coverage80 - 0.8) + abs(coverage95 - 0.95)) / 1.75)
        confidence = max(0.0, min(1.0, 0.4 * quality["score"] + 0.25 * error_score + 0.15 * stability + 0.1 * improvement_score + 0.1 * calibration_score))
        status = "QUALIFIED" if confidence >= self.min_confidence else "LOW_CONFIDENCE"
        backtest = {
            "selectedModel": best["name"],
            "baselineModel": baseline_name,
            "evaluationHorizon": evaluation_horizon,
            "folds": best["folds"],
            "metrics": {key: round(float(value), 6) for key, value in best["metrics"].items()},
            "baselineMetrics": {key: round(float(value), 6) for key, value in baseline["metrics"].items()},
            "improvementOverBaseline": round(float(improvement), 6),
            "intervalCoverage80": round(coverage80, 6),
            "intervalCoverage95": round(coverage95, 6),
            "candidates": [
                {"model": item["name"], **{key: round(float(value), 6) for key, value in item["metrics"].items()}}
                for item in ranked
            ],
        }
        return ForecastResult(
            run_id=f"fc_{uuid.uuid4().hex}",
            metric_code=snapshot.spec.metric_code,
            status=status,
            model_name=best["name"],
            horizon=horizon,
            points=points,
            quality_score=quality["score"],
            confidence=confidence,
            backtest=backtest,
            data_quality=quality,
            model_version=MODEL_VERSION,
            reason="" if status == "QUALIFIED" else "预测已返回，但置信度不足，不应主动推送",
        )

    def _backtest(
        self,
        values: np.ndarray,
        candidates: list[_Candidate],
        horizon: int,
        season: int | None,
    ) -> dict[str, dict[str, Any]]:
        min_train = max(8, (season or 1) * 2 if season else 8)
        latest_origin = len(values) - horizon
        if latest_origin < min_train:
            min_train = max(5, len(values) - horizon * 2)
        origins = list(range(min_train, latest_origin + 1, max(1, horizon)))
        origins = origins[-self.max_folds:]
        evaluated: dict[str, dict[str, Any]] = {}
        for candidate in candidates:
            all_actual: list[float] = []
            all_predicted: list[float] = []
            all_errors: list[float] = []
            fold_mase: list[float] = []
            for origin in origins:
                train = values[:origin]
                actual = values[origin:origin + horizon]
                if len(actual) == 0:
                    continue
                try:
                    predicted = candidate.forecast(train, len(actual), season)
                    if len(predicted) != len(actual) or not np.all(np.isfinite(predicted)):
                        continue
                except Exception:
                    continue
                fold_metrics = _metrics(actual, predicted, train, season)
                fold_mase.append(fold_metrics["mase"])
                all_actual.extend(actual.tolist())
                all_predicted.extend(predicted.tolist())
                all_errors.extend((actual - predicted).tolist())
            if not all_actual:
                continue
            evaluated[candidate.name] = {
                "name": candidate.name,
                "folds": len(fold_mase),
                "foldMase": fold_mase,
                "errors": all_errors,
                "metrics": _metrics(
                    np.asarray(all_actual),
                    np.asarray(all_predicted),
                    values[:max(origins) if origins else len(values)],
                    season,
                ),
            }
        return evaluated

    def _data_quality(self, snapshot: SeriesSnapshot, values: np.ndarray) -> dict[str, Any]:
        completeness = max(0.0, 1.0 - snapshot.missing_rate)
        length_score = min(1.0, len(values) / max(1, self.min_points * 2))
        finite_score = float(np.isfinite(values).mean()) if len(values) else 0.0
        variance_score = 1.0 if len(values) and float(np.std(values)) > 1e-12 else 0.8
        score = max(0.0, min(1.0, 0.45 * completeness + 0.25 * length_score + 0.2 * finite_score + 0.1 * variance_score))
        return {
            "score": round(score, 6),
            "pointCount": len(values),
            "expectedPoints": snapshot.expected_points,
            "missingPoints": snapshot.missing_points,
            "missingRate": round(snapshot.missing_rate, 6),
            "warnings": list(snapshot.warnings),
        }

    def _empty(
        self,
        snapshot: SeriesSnapshot,
        horizon: int,
        status: str,
        reason: str,
        quality: dict[str, Any],
    ) -> ForecastResult:
        return ForecastResult(
            run_id=f"fc_{uuid.uuid4().hex}",
            metric_code=snapshot.spec.metric_code,
            status=status,
            model_name="",
            horizon=horizon,
            points=[],
            quality_score=float(quality.get("score") or 0.0),
            confidence=0.0,
            backtest={},
            data_quality=quality,
            model_version=MODEL_VERSION,
            reason=reason,
        )
