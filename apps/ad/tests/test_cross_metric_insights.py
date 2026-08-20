import numpy as np
import pandas as pd

from kg_builder.insights.contracts import MetricSeriesSpec
from kg_builder.insights.cross_metric import (
    CommonContributorAnalyzer,
    CrossMetricEngine,
    MetricCandidateBuilder,
    benjamini_hochberg,
)
from kg_builder.insights.series import MetricSeriesService


def _snapshot(code, values):
    periods = pd.date_range("2021-01-01", periods=len(values), freq="MS")
    spec = MetricSeriesSpec(code, "DIM_month", "month", permission_scope_hash="scope")
    return MetricSeriesService.from_points(spec, [
        {"period": period.date().isoformat(), "value": value}
        for period, value in zip(periods, values)
    ])


def test_cross_metric_detects_aligned_change_relationship():
    rng = np.random.default_rng(42)
    changes = rng.normal(0, 1, 72)
    left = np.cumsum(changes)
    right = np.cumsum(changes * 1.8 + rng.normal(0, 0.08, 72))
    snapshots = {"A": _snapshot("A", left), "B": _snapshot("B", right)}
    candidates = [{"metricA": "A", "metricB": "B", "relationSource": "manual", "businessPriority": 1.0}]

    facts = CrossMetricEngine().discover(snapshots, candidates)

    assert len(facts) == 1
    assert facts[0].payload["primaryMethod"] == "difference_pearson"
    assert facts[0].payload["qValue"] <= 0.05
    assert facts[0].payload["causalityBoundary"]


def test_cross_metric_does_not_promote_shared_trend_without_change_correlation():
    rng = np.random.default_rng(7)
    left = np.cumsum(rng.normal(1.0, 1.0, 100))
    right = np.cumsum(rng.normal(1.0, 1.0, 100))
    snapshots = {"A": _snapshot("A", left), "B": _snapshot("B", right)}
    candidates = [{"metricA": "A", "metricB": "B", "businessPriority": 1.0}]

    facts = CrossMetricEngine().discover(snapshots, candidates)

    assert facts == []


def test_benjamini_hochberg_controls_multiple_tests():
    adjusted = benjamini_hochberg([0.001, 0.02, 0.5, None])

    assert adjusted[0] <= adjusted[1] <= adjusted[2]
    assert adjusted[3] is None


def test_common_contributor_analyzer_finds_same_and_opposite_members():
    analyzer = CommonContributorAnalyzer()
    rows = analyzer.compare({
        "A": {"east": 0.6, "west": -0.4, "north": 0.2},
        "B": {"east": 0.5, "west": -0.3, "north": -0.1},
    })
    by_member = {row["member"]: row for row in rows}

    assert by_member["east"]["relation"] == "common_driver"
    assert by_member["west"]["relation"] == "common_detractor"
    assert by_member["north"]["relation"] == "opposing_contributor"


def test_candidate_builder_accepts_public_member_names():
    catalog = {"measures": [
        {"code": "MEAS_sales", "tables": ["fact"], "dimensionCodes": ["DIM_month"]},
        {"code": "MEAS_orders", "tables": ["fact"], "dimensionCodes": ["DIM_month"]},
    ]}

    candidates = MetricCandidateBuilder().build(catalog, ["ad.sales", "ad.orders"])

    assert candidates[0]["metricA"] == "ad.orders"
    assert candidates[0]["metricB"] == "ad.sales"
