"""Candidate-controlled, time-aligned cross-metric insight detection."""

from __future__ import annotations

import math
from itertools import combinations
from typing import Any, Iterable

import numpy as np
import pandas as pd

from .contracts import InsightFact, SeriesSnapshot, stable_hash


MODEL_VERSION = "cross-metric-v1"


def benjamini_hochberg(values: list[float | None]) -> list[float | None]:
    indexed = sorted(
        ((index, float(value)) for index, value in enumerate(values) if value is not None and math.isfinite(float(value))),
        key=lambda item: item[1],
    )
    adjusted: list[float | None] = [None] * len(values)
    running = 1.0
    count = len(indexed)
    for rank_index in range(count - 1, -1, -1):
        original_index, p_value = indexed[rank_index]
        rank = rank_index + 1
        running = min(running, p_value * count / rank)
        adjusted[original_index] = min(1.0, running)
    return adjusted


def _pearson(left: np.ndarray, right: np.ndarray) -> tuple[float | None, float | None]:
    if len(left) < 3 or len(left) != len(right) or np.std(left) <= 1e-12 or np.std(right) <= 1e-12:
        return None, None
    try:
        from scipy.stats import pearsonr

        result = pearsonr(left, right)
        return float(result.statistic), float(result.pvalue)
    except Exception:
        return float(np.corrcoef(left, right)[0, 1]), None


def _spearman(left: np.ndarray, right: np.ndarray) -> tuple[float | None, float | None]:
    if (
        len(left) < 3
        or len(left) != len(right)
        or len(np.unique(left)) < 2
        or len(np.unique(right)) < 2
    ):
        return None, None
    try:
        from scipy.stats import spearmanr

        result = spearmanr(left, right)
        statistic = float(result.statistic)
        p_value = float(result.pvalue)
        return (
            statistic if math.isfinite(statistic) else None,
            p_value if math.isfinite(p_value) else None,
        )
    except Exception:
        return None, None


def _robust_z(values: np.ndarray) -> np.ndarray:
    if len(values) == 0:
        return values
    median = float(np.median(values))
    mad = float(np.median(np.abs(values - median)))
    if mad <= 1e-12:
        std = float(np.std(values))
        return (values - median) / std if std > 1e-12 else np.zeros_like(values)
    return 0.67448975 * (values - median) / mad


class MetricCandidateBuilder:
    """Limit discovery to governed, graph-compatible metric pairs."""

    def build(
        self,
        catalog: dict[str, Any],
        metric_codes: Iterable[str],
        *,
        manual_candidates: Iterable[dict[str, Any]] = (),
    ) -> list[dict[str, Any]]:
        requested = {str(code) for code in metric_codes if code}
        measures: dict[str, dict[str, Any]] = {}
        for requested_code in requested:
            requested_tail = requested_code.rsplit(".", 1)[-1]
            for item in catalog.get("measures") or []:
                internal_code = str(item.get("code") or "")
                internal_tail = internal_code.removeprefix("MEAS_")
                if requested_code == internal_code or requested_tail in {internal_code, internal_tail}:
                    measures[requested_code] = item
                    break
        result: dict[tuple[str, str], dict[str, Any]] = {}

        for item in manual_candidates:
            left, right = sorted([str(item.get("metricA") or ""), str(item.get("metricB") or "")])
            if left in requested and right in requested and left != right:
                result[(left, right)] = {
                    "metricA": left,
                    "metricB": right,
                    "relationSource": item.get("relationSource") or "manual",
                    "commonDimensions": list(item.get("commonDimensions") or []),
                    "commonTables": list(item.get("commonTables") or []),
                    "kgPath": item.get("kgPath") or "",
                    "businessPriority": float(item.get("businessPriority") or 1.0),
                }

        for left, right in combinations(sorted(measures), 2):
            left_item, right_item = measures[left], measures[right]
            common_tables = sorted(set(left_item.get("tables") or []) & set(right_item.get("tables") or []))
            common_dimensions = sorted(set(left_item.get("dimensionCodes") or []) & set(right_item.get("dimensionCodes") or []))
            left_deps = set(left_item.get("dependencies") or [])
            right_deps = set(right_item.get("dependencies") or [])
            dependency = str(right_item.get("code") or "") in left_deps or str(left_item.get("code") or "") in right_deps
            if not dependency and not common_tables and not common_dimensions:
                continue
            source = "formula_dependency" if dependency else "shared_fact_table" if common_tables else "compatible_dimension"
            priority = 1.0 if dependency else 0.85 if common_tables else 0.7
            result.setdefault((left, right), {
                "metricA": left,
                "metricB": right,
                "relationSource": source,
                "commonDimensions": common_dimensions,
                "commonTables": common_tables,
                "kgPath": f"{left} -[{source}]-> {right}",
                "businessPriority": priority,
            })
        return list(result.values())


class CrossMetricEngine:
    def __init__(
        self,
        *,
        min_points: int = 30,
        min_coverage: float = 0.8,
        min_effect: float = 0.35,
        max_q_value: float = 0.05,
        max_lag: int = 6,
    ) -> None:
        self.min_points = min_points
        self.min_coverage = min_coverage
        self.min_effect = min_effect
        self.max_q_value = max_q_value
        self.max_lag = max_lag

    def discover(
        self,
        snapshots: dict[str, SeriesSnapshot],
        candidates: list[dict[str, Any]],
    ) -> list[InsightFact]:
        raw_results: list[dict[str, Any]] = []
        for candidate in candidates:
            left = snapshots.get(str(candidate.get("metricA") or ""))
            right = snapshots.get(str(candidate.get("metricB") or ""))
            if left is None or right is None:
                continue
            result = self._analyze_pair(left, right, candidate)
            if result:
                raw_results.append(result)

        q_values = benjamini_hochberg([result.get("primaryPValue") for result in raw_results])
        facts: list[InsightFact] = []
        for result, q_value in zip(raw_results, q_values):
            result["qValue"] = q_value
            effect = abs(float(result.get("primaryEffect") or 0.0))
            if effect < self.min_effect:
                continue
            if q_value is not None and q_value > self.max_q_value:
                continue
            significance = 1.0 - min(1.0, float(q_value)) if q_value is not None else 0.5
            score = 100.0 * effect * significance * result["stability"] * result["coverage"] * result["businessPriority"]
            confidence = max(0.0, min(1.0, 0.35 * significance + 0.25 * result["stability"] + 0.25 * result["coverage"] + 0.15 * result["businessPriority"]))
            left_code, right_code = result["metricA"], result["metricB"]
            left_snapshot = snapshots[left_code]
            facts.append(InsightFact.create(
                fact_type="CROSS_METRIC_RELATION",
                metric_codes=[left_code, right_code],
                permission_scope_hash=left_snapshot.spec.permission_scope_hash,
                metric_scope_hash=stable_hash({
                    "metrics": sorted([left_code, right_code]),
                    "leftScope": left_snapshot.spec.identity(),
                    "rightScope": snapshots[right_code].spec.identity(),
                }),
                grain=left_snapshot.spec.grain,
                window_start=result["windowStart"],
                window_end=result["windowEnd"],
                status="DETECTED",
                impact_score=score,
                confidence=confidence,
                payload={key: value for key, value in result.items() if key != "evidence"},
                evidence=result["evidence"],
                model_version=MODEL_VERSION,
            ))
        return sorted(facts, key=lambda fact: (-fact.impact_score, -fact.confidence))

    def _analyze_pair(
        self,
        left: SeriesSnapshot,
        right: SeriesSnapshot,
        candidate: dict[str, Any],
    ) -> dict[str, Any] | None:
        if left.spec.grain != right.spec.grain:
            return None
        if left.spec.permission_scope_hash != right.spec.permission_scope_hash:
            return None
        left_series = pd.Series({point.period: point.value for point in left.points}, name="left")
        right_series = pd.Series({point.period: point.value for point in right.points}, name="right")
        aligned = pd.concat([left_series, right_series], axis=1, join="inner").dropna().sort_index()
        union_count = len(left_series.index.union(right_series.index))
        coverage = len(aligned) / union_count if union_count else 0.0
        if len(aligned) < self.min_points or coverage < self.min_coverage:
            return None

        raw_left, raw_right = aligned["left"].to_numpy(float), aligned["right"].to_numpy(float)
        raw_r, raw_p = _pearson(raw_left, raw_right)
        raw_s, raw_sp = _spearman(raw_left, raw_right)
        diff = aligned.diff().dropna()
        diff_left, diff_right = diff["left"].to_numpy(float), diff["right"].to_numpy(float)
        diff_r, diff_p = _pearson(diff_left, diff_right)
        diff_s, diff_sp = _spearman(diff_left, diff_right)
        pct = aligned.pct_change(fill_method=None).replace([np.inf, -np.inf], np.nan).dropna()
        pct_r, pct_p = _pearson(pct["left"].to_numpy(float), pct["right"].to_numpy(float)) if len(pct) >= 3 else (None, None)

        primary_name, primary_effect, primary_p = "difference_pearson", diff_r, diff_p
        if primary_effect is None:
            primary_name, primary_effect, primary_p = "pct_change_pearson", pct_r, pct_p
        if primary_effect is None:
            return None

        stability = self._window_stability(diff if len(diff) >= 8 else aligned)
        lag = self._best_lag(raw_left, raw_right)
        joint_anomalies = self._joint_anomalies(aligned)
        direction = "same" if primary_effect >= 0 else "opposite"
        relation_label = "同向变化" if direction == "same" else "反向变化"
        if lag.get("lag", 0):
            relation_label += f"，{left.spec.metric_code if lag['lag'] > 0 else right.spec.metric_code} 领先约 {abs(lag['lag'])} 期"

        return {
            "metricA": left.spec.metric_code,
            "metricB": right.spec.metric_code,
            "grain": left.spec.grain,
            "windowStart": aligned.index.min().date().isoformat(),
            "windowEnd": aligned.index.max().date().isoformat(),
            "sampleCount": len(aligned),
            "coverage": round(coverage, 6),
            "rawPearson": raw_r,
            "rawPearsonPValue": raw_p,
            "rawSpearman": raw_s,
            "rawSpearmanPValue": raw_sp,
            "differencePearson": diff_r,
            "differencePearsonPValue": diff_p,
            "differenceSpearman": diff_s,
            "differenceSpearmanPValue": diff_sp,
            "pctChangePearson": pct_r,
            "pctChangePearsonPValue": pct_p,
            "primaryMethod": primary_name,
            "primaryEffect": primary_effect,
            "primaryPValue": primary_p,
            "direction": direction,
            "relationLabel": relation_label,
            "stability": round(stability, 6),
            "bestLag": lag,
            "jointAnomalies": joint_anomalies,
            "relationSource": candidate.get("relationSource") or "configured",
            "commonDimensions": candidate.get("commonDimensions") or [],
            "commonTables": candidate.get("commonTables") or [],
            "kgPath": candidate.get("kgPath") or "",
            "businessPriority": max(0.0, min(1.0, float(candidate.get("businessPriority") or 0.7))),
            "causalityBoundary": "统计关联和领先关系仅作为分析线索，不代表因果关系",
            "visualSpec": {
                "primary": "normalized_time_series",
                "secondary": "scatter",
                "xMetric": left.spec.metric_code,
                "yMetric": right.spec.metric_code,
            },
            "evidence": {
                "metricAQuery": left.query,
                "metricBQuery": right.query,
                "permissionScopeHash": left.spec.permission_scope_hash,
                "alignedPeriods": [period.date().isoformat() for period in aligned.index],
            },
        }

    @staticmethod
    def _window_stability(frame: pd.DataFrame) -> float:
        midpoint = len(frame) // 2
        first, second = frame.iloc[:midpoint], frame.iloc[midpoint:]
        first_r, _ = _pearson(first.iloc[:, 0].to_numpy(float), first.iloc[:, 1].to_numpy(float))
        second_r, _ = _pearson(second.iloc[:, 0].to_numpy(float), second.iloc[:, 1].to_numpy(float))
        if first_r is None or second_r is None:
            return 0.5
        sign_penalty = 1.0 if first_r * second_r >= 0 else 0.25
        return max(0.0, min(1.0, (1.0 - abs(first_r - second_r) / 2.0) * sign_penalty))

    def _best_lag(self, left: np.ndarray, right: np.ndarray) -> dict[str, Any]:
        best = {"lag": 0, "correlation": None, "sampleCount": len(left)}
        for lag in range(-self.max_lag, self.max_lag + 1):
            if lag > 0:
                x, y = left[:-lag], right[lag:]
            elif lag < 0:
                x, y = left[-lag:], right[:lag]
            else:
                x, y = left, right
            correlation, _ = _pearson(x, y)
            if correlation is None:
                continue
            if best["correlation"] is None or abs(correlation) > abs(float(best["correlation"])):
                best = {"lag": lag, "correlation": round(correlation, 6), "sampleCount": len(x)}
        return best

    @staticmethod
    def _joint_anomalies(aligned: pd.DataFrame) -> list[dict[str, Any]]:
        changes = aligned.diff().dropna()
        if len(changes) < 6:
            return []
        left_z = _robust_z(changes["left"].to_numpy(float))
        right_z = _robust_z(changes["right"].to_numpy(float))
        result = []
        for period, lz, rz in zip(changes.index, left_z, right_z):
            if abs(lz) >= 3.0 and abs(rz) >= 3.0:
                result.append({
                    "period": period.date().isoformat(),
                    "metricAZ": round(float(lz), 6),
                    "metricBZ": round(float(rz), 6),
                    "direction": "same" if lz * rz >= 0 else "opposite",
                })
        return result


class CommonContributorAnalyzer:
    """Compare normalized period-over-period contribution vectors."""

    @staticmethod
    def contribution_vector(current: dict[str, float], previous: dict[str, float]) -> dict[str, float]:
        deltas = {member: current.get(member, 0.0) - previous.get(member, 0.0) for member in set(current) | set(previous)}
        scale = sum(abs(value) for value in deltas.values())
        return {member: value / scale for member, value in deltas.items()} if scale > 1e-12 else {}

    def compare(self, vectors: dict[str, dict[str, float]], limit: int = 10) -> list[dict[str, Any]]:
        metrics = sorted(vectors)
        if len(metrics) < 2:
            return []
        members = set.intersection(*(set(vectors[metric]) for metric in metrics)) if metrics else set()
        rows = []
        for member in members:
            values = {metric: float(vectors[metric][member]) for metric in metrics}
            signs = {1 if value > 0 else -1 if value < 0 else 0 for value in values.values()}
            if signs == {1}:
                relation = "common_driver"
            elif signs == {-1}:
                relation = "common_detractor"
            elif 1 in signs and -1 in signs:
                relation = "opposing_contributor"
            else:
                continue
            score = min(abs(value) for value in values.values())
            rows.append({"member": member, "relation": relation, "contributions": values, "score": round(score, 6)})
        return sorted(rows, key=lambda item: -item["score"])[:limit]


def joint_goal_risk(pace_results: Iterable[dict[str, Any]]) -> dict[str, Any] | None:
    risky = [
        item for item in pace_results
        if str(item.get("status") or "") in {"AT_RISK", "OFF_TRACK"}
    ]
    if len(risky) < 2:
        return None
    return {
        "type": "JOINT_GOAL_RISK",
        "metrics": [item.get("metricCode") for item in risky],
        "goals": [item.get("goalId") for item in risky],
        "statuses": {str(item.get("metricCode")): item.get("status") for item in risky},
        "message": f"{len(risky)} 个指标同时存在目标达成风险",
    }
