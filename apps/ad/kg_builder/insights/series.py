"""Build regular, permission-scoped metric time-series snapshots."""

from __future__ import annotations

import re
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from typing import Any, Callable, Iterable

import pandas as pd

from .contracts import MetricSeriesSpec, SeriesPoint, SeriesSnapshot, finite_float


_GRAIN_FREQ = {
    "day": "D",
    "week": "W-MON",
    "month": "MS",
    "quarter": "QS",
    "year": "YS",
}


def normalize_grain(value: str) -> str:
    aliases = {
        "daily": "day", "d": "day",
        "weekly": "week", "w": "week",
        "monthly": "month", "m": "month",
        "quarterly": "quarter", "q": "quarter",
        "yearly": "year", "y": "year",
    }
    result = aliases.get(str(value or "").strip().lower(), str(value or "").strip().lower())
    if result not in _GRAIN_FREQ:
        raise ValueError(f"不支持的时间粒度: {value}")
    return result


def parse_period(value: Any, grain: str) -> datetime:
    grain = normalize_grain(grain)
    text = str(value or "").strip()
    digits = re.sub(r"\D", "", text)
    try:
        if grain == "day" and len(digits) >= 8:
            return datetime.strptime(digits[:8], "%Y%m%d")
        if grain == "week" and len(digits) >= 6:
            return datetime.fromisocalendar(int(digits[:4]), int(digits[4:6]), 1)
        if grain == "month" and len(digits) >= 6:
            return datetime.strptime(digits[:6], "%Y%m")
        if grain == "quarter":
            match = re.search(r"(\d{4}).*?([1-4])", text)
            if match:
                return datetime(int(match.group(1)), (int(match.group(2)) - 1) * 3 + 1, 1)
        if grain == "year" and len(digits) >= 4:
            return datetime(int(digits[:4]), 1, 1)
        return pd.Timestamp(text).to_pydatetime().replace(tzinfo=None)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"无法将 {value!r} 解析为 {grain} 时间") from exc


def next_periods(last: datetime, grain: str, count: int) -> list[datetime]:
    grain = normalize_grain(grain)
    start = pd.Timestamp(last)
    values = pd.date_range(start=start, periods=count + 1, freq=_GRAIN_FREQ[grain])[1:]
    return [value.to_pydatetime().replace(tzinfo=None) for value in values]


def periods_between(start: datetime, end: datetime, grain: str) -> int:
    if end <= start:
        return 0
    return max(0, len(pd.date_range(start=start, end=end, freq=_GRAIN_FREQ[normalize_grain(grain)])) - 1)


def _matches_member(key: str, code: str) -> bool:
    return key == code or key.rsplit(".", 1)[-1] == code.rsplit(".", 1)[-1]


class MetricSeriesService:
    """Loads semantic results and turns them into regular metric series."""

    def __init__(self, loader: Callable[[dict[str, Any], str], dict[str, Any]] | None = None):
        self.loader = loader

    def load(
        self,
        spec: MetricSeriesSpec,
        *,
        authorization: str = "",
        supplied_points: Iterable[dict[str, Any]] | None = None,
    ) -> SeriesSnapshot:
        if supplied_points is not None:
            raw_points = list(supplied_points)
            query: dict[str, Any] = {"source": "supplied"}
            diagnostics: dict[str, Any] = {}
        else:
            if self.loader is None:
                raise RuntimeError("MetricSeriesService 尚未配置语义查询加载器")
            query = {
                "measures": [spec.metric_code],
                "timeDimensions": [{
                    "dimension": spec.time_dimension,
                    "granularity": normalize_grain(spec.grain),
                    **({"dateRange": [spec.start, spec.end]} if spec.start and spec.end else {}),
                }],
                "filters": list(spec.filters),
                "limit": 10000,
                "enableAlerts": False,
            }
            payload = self.loader(query, authorization)
            raw_points = []
            for row in payload.get("data") or []:
                if not isinstance(row, dict):
                    continue
                period_value = next((value for key, value in row.items() if _matches_member(str(key), spec.time_dimension)), None)
                metric_value = next((value for key, value in row.items() if _matches_member(str(key), spec.metric_code)), None)
                raw_points.append({"period": period_value, "value": metric_value})
            diagnostics = payload.get("diagnostics") or {}

        return self.from_points(spec, raw_points, query=query, diagnostics=diagnostics)

    def load_many(
        self,
        specs: list[MetricSeriesSpec],
        *,
        authorization: str = "",
        supplied: dict[str, list[dict[str, Any]]] | None = None,
    ) -> dict[str, SeriesSnapshot]:
        supplied = supplied or {}

        def _load(spec: MetricSeriesSpec) -> tuple[str, SeriesSnapshot]:
            points = supplied.get(spec.metric_code)
            return spec.metric_code, self.load(
                spec,
                authorization=authorization,
                supplied_points=points if points is not None else None,
            )

        with ThreadPoolExecutor(max_workers=min(4, max(1, len(specs)))) as pool:
            return dict(pool.map(_load, specs))

    @staticmethod
    def from_points(
        spec: MetricSeriesSpec,
        points: Iterable[dict[str, Any]],
        *,
        query: dict[str, Any] | None = None,
        diagnostics: dict[str, Any] | None = None,
    ) -> SeriesSnapshot:
        grain = normalize_grain(spec.grain)
        grouped: dict[datetime, list[float]] = defaultdict(list)
        warnings: list[str] = []
        for item in points:
            if not isinstance(item, dict):
                continue
            value = finite_float(item.get("value"))
            if value is None or item.get("period") in (None, ""):
                continue
            grouped[parse_period(item.get("period"), grain)].append(value)
        duplicate_points = sum(max(0, len(values) - 1) for values in grouped.values())
        if duplicate_points:
            warnings.append(f"发现 {duplicate_points} 个重复周期，已按指标聚合类型合并")

        aggregation = spec.aggregation_type.upper()
        collapsed: dict[datetime, float] = {}
        for period, values in grouped.items():
            if aggregation in {"AVG", "RATIO"}:
                collapsed[period] = sum(values) / len(values)
            elif aggregation == "LAST":
                collapsed[period] = values[-1]
            else:
                collapsed[period] = sum(values)

        if not collapsed:
            return SeriesSnapshot(spec, [], 0, 0, duplicate_points, warnings + ["没有可用时序数据"], query or {}, diagnostics or {})

        start, end = min(collapsed), max(collapsed)
        expected_index = pd.date_range(start=start, end=end, freq=_GRAIN_FREQ[grain])
        missing = [timestamp.to_pydatetime().replace(tzinfo=None) for timestamp in expected_index if timestamp.to_pydatetime().replace(tzinfo=None) not in collapsed]
        if missing:
            if spec.fill_policy == "zero":
                for period in missing:
                    collapsed[period] = 0.0
                warnings.append(f"按 zero 策略补齐 {len(missing)} 个周期")
                missing_count = 0
            elif spec.fill_policy == "interpolate":
                series = pd.Series(collapsed).sort_index().reindex(expected_index).interpolate(limit_area="inside")
                collapsed = {timestamp.to_pydatetime().replace(tzinfo=None): float(value) for timestamp, value in series.dropna().items()}
                missing_count = int(series.isna().sum())
                warnings.append(f"按 interpolate 策略处理 {len(missing)} 个缺失周期")
            else:
                missing_count = len(missing)
                warnings.append(f"存在 {len(missing)} 个缺失周期；模型将降低置信度")
        else:
            missing_count = 0

        normalized = [SeriesPoint(period, value) for period, value in sorted(collapsed.items())]
        return SeriesSnapshot(
            spec=spec,
            points=normalized,
            expected_points=len(expected_index),
            missing_points=missing_count,
            duplicate_points=duplicate_points,
            warnings=warnings,
            query=query or {},
            diagnostics=diagnostics or {},
        )

    def load_breakdown(
        self,
        spec: MetricSeriesSpec,
        dimension: str,
        start: str,
        end: str,
        *,
        authorization: str = "",
    ) -> dict[str, float]:
        if self.loader is None:
            raise RuntimeError("MetricSeriesService 尚未配置语义查询加载器")
        query = {
            "measures": [spec.metric_code],
            "dimensions": [dimension],
            "timeDimensions": [{"dimension": spec.time_dimension, "dateRange": [start, end]}],
            "filters": list(spec.filters),
            "limit": 10000,
            "enableAlerts": False,
        }
        payload = self.loader(query, authorization)
        result: dict[str, float] = {}
        for row in payload.get("data") or []:
            member = next((value for key, value in row.items() if _matches_member(str(key), dimension)), None)
            value = next((finite_float(value) for key, value in row.items() if _matches_member(str(key), spec.metric_code)), None)
            if member not in (None, "") and value is not None:
                result[str(member)] = result.get(str(member), 0.0) + value
        return result
