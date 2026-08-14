import datetime

import pytest

from kg_builder.analysis.time_parser import parse_question_time


TODAY = datetime.date(2026, 7, 24)


@pytest.mark.parametrize(
    ("question", "expected"),
    [
        (
            "分析20260202到20260215的质量分",
            ("2026-02-02", "2026-02-15", "2026-01-19", "2026-02-01", "day"),
        ),
        (
            "分析2026年2月2日至2月15日的质量分",
            ("2026-02-02", "2026-02-15", "2026-01-19", "2026-02-01", "day"),
        ),
        (
            "分析12月25日至1月5日的订单",
            ("2026-12-25", "2027-01-05", "2026-12-13", "2026-12-24", "day"),
        ),
        (
            "分析今年7月2日至7月5日的质量分",
            ("2026-07-02", "2026-07-05", "2026-06-28", "2026-07-01", "day"),
        ),
        (
            "分析去年7月2日的质量分",
            ("2025-07-02", "2025-07-02", "2025-07-01", "2025-07-01", "day"),
        ),
        (
            "查看最近七天的趋势",
            ("2026-07-18", "2026-07-24", "2026-07-11", "2026-07-17", "day"),
        ),
        (
            "查看近两周的趋势",
            ("2026-07-11", "2026-07-24", "2026-06-27", "2026-07-10", "week"),
        ),
        (
            "查看最近一个星期的趋势",
            ("2026-07-18", "2026-07-24", "2026-07-11", "2026-07-17", "week"),
        ),
        (
            "查看最近两个完整周的趋势",
            ("2026-07-06", "2026-07-19", "2026-06-22", "2026-07-05", "week"),
        ),
        (
            "查看过去三个月的趋势",
            ("2026-05-01", "2026-07-31", "2026-02-01", "2026-04-30", "month"),
        ),
        (
            "查看近半年的趋势",
            ("2026-02-01", "2026-07-31", "2025-08-01", "2026-01-31", "month"),
        ),
        (
            "查看上季度的经营情况",
            ("2026-04-01", "2026-06-30", "2026-01-01", "2026-03-31", "month"),
        ),
        (
            "查看去年第二季度的经营情况",
            ("2025-04-01", "2025-06-30", "2025-01-01", "2025-03-31", "month"),
        ),
        (
            "查看今年上半年的经营情况",
            ("2026-01-01", "2026-06-30", "2025-07-01", "2025-12-31", "month"),
        ),
        (
            "查看去年3月的经营情况",
            ("2025-03-01", "2025-03-31", "2025-02-01", "2025-02-28", "month"),
        ),
        (
            "查看2026年第一季度的经营情况",
            ("2026-01-01", "2026-03-31", "2025-10-01", "2025-12-31", "month"),
        ),
        (
            "查看2026年1月至3月的趋势",
            ("2026-01-01", "2026-03-31", "2025-10-01", "2025-12-31", "month"),
        ),
        (
            "查看2025年的经营情况",
            ("2025-01-01", "2025-12-31", "2024-01-01", "2024-12-31", "month"),
        ),
        (
            "查看2026-W05的质量分",
            ("2026-01-26", "2026-02-01", "2026-01-19", "2026-01-25", "week"),
        ),
        (
            "分析昨天的异常原因",
            ("2026-07-23", "2026-07-23", "2026-07-22", "2026-07-22", "day"),
        ),
    ],
)
def test_common_time_expressions(question, expected):
    parsed = parse_question_time(question, TODAY)

    assert parsed is not None
    assert (
        parsed["time_start"],
        parsed["time_end"],
        parsed["prev_start"],
        parsed["prev_end"],
        parsed["gran"],
    ) == expected


def test_explicit_grouping_granularity_overrides_default():
    parsed = parse_question_time("分析2026-02-02至2026-02-15的按周趋势", TODAY)

    assert parsed is not None
    assert parsed["gran"] == "week"
    assert parsed["time_start"] == "2026-02-02"
    assert parsed["time_end"] == "2026-02-15"


def test_year_over_year_uses_same_period_last_year():
    parsed = parse_question_time("分析2026-02-02至2026-02-15的同比变化", TODAY)

    assert parsed is not None
    assert parsed["prev_start"] == "2025-02-02"
    assert parsed["prev_end"] == "2025-02-15"


def test_named_month_year_over_year_uses_last_year_month():
    parsed = parse_question_time("分析本月同比变化", TODAY)

    assert parsed is not None
    assert parsed["time_start"] == "2026-07-01"
    assert parsed["time_end"] == "2026-07-31"
    assert parsed["prev_start"] == "2025-07-01"
    assert parsed["prev_end"] == "2025-07-31"


def test_unknown_time_expression_returns_none_for_caller_fallback():
    assert parse_question_time("分析平均电话质量分的原因", TODAY) is None


def test_recent_weeks_never_include_future_dates():
    parsed = parse_question_time("分析最近4周平均电话质量分", TODAY)

    assert parsed is not None
    assert parsed["time_end"] == TODAY.isoformat()
    assert datetime.date.fromisoformat(parsed["time_end"]) <= TODAY


def test_invalid_date_does_not_degrade_to_a_month_expression():
    assert parse_question_time("分析2026年2月30日的质量分", TODAY) is None
