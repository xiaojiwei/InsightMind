import datetime
from pathlib import Path

from web_app import (
    _call_workbench_date_label,
    _call_workbench_iso_date,
    _call_workbench_time_label,
)
from kg_builder.call_quality_workspace import WORKSPACE_VERSION, _parse_segments


def test_workbench_formats_da_timestamp_arrays():
    value = [2026, 7, 2, 14, 24]

    assert _call_workbench_iso_date(value) == "2026-07-02"
    assert _call_workbench_date_label(value) == "07-02"
    assert _call_workbench_time_label(value) == "14:24"


def test_workbench_formats_python_datetime_values():
    value = datetime.datetime(2026, 7, 2, 9, 5, 3)

    assert _call_workbench_iso_date(value) == "2026-07-02"
    assert _call_workbench_date_label(value) == "07-02"
    assert _call_workbench_time_label(value) == "09:05"


def test_workbench_formats_da_epoch_milliseconds():
    value = 1_782_921_600_000

    assert _call_workbench_iso_date(value) == "2026-07-02"
    assert _call_workbench_date_label(value) == "07-02"


def test_asr_transcript_is_split_at_each_speaker_without_pipe_delimiters():
    transcript = (
        "专家:您好，想邀请您试驾。您周六方便吗？"
        "客户：周六上午吧，也想对比续航。"
        "专家:好的，我发定位。"
        "客户:可以，没问题。"
    )

    segments = _parse_segments(transcript)

    assert WORKSPACE_VERSION == "call_quality_workspace_v1.1"
    assert [item["role"] for item in segments] == ["expert", "customer", "expert", "customer"]
    assert [item["text"] for item in segments] == [
        "您好，想邀请您试驾。您周六方便吗？",
        "周六上午吧，也想对比续航。",
        "好的，我发定位。",
        "可以，没问题。",
    ]
    assert [item["offsetSeconds"] for item in segments] == sorted(
        item["offsetSeconds"] for item in segments
    )


def test_call_quality_dashboards_use_a_scoped_filter_catalog():
    template = Path(__file__).parents[1] / "kg_builder" / "web" / "templates" / "index.html"
    html = template.read_text(encoding="utf-8")

    assert "CALL_QUALITY_DASHBOARD_IDS" in html
    assert "CALL_QUALITY_FILTER_MEMBER_KEYS" in html
    assert "const items = dashboardFilterCatalogItems(kind)" in html
    assert "'celn_funnel_group'" not in html[
        html.index("const CALL_QUALITY_FILTER_MEMBER_KEYS"):
        html.index("function renderDashboardFilterOptions")
    ]


def test_workbench_kpis_open_the_shared_drill_chooser():
    template = Path(__file__).parents[1] / "kg_builder" / "web" / "templates" / "index.html"
    html = template.read_text(encoding="utf-8")
    section = html[
        html.index("function callWorkbenchOpenKpiDrill"):
        html.index("function callWorkbenchFiltersFromUrl")
    ]

    assert "openCallReportDrillChooser" in section
    assert "ad.sop_total_call_count" in section
    assert "ad.effective_connect_rate" in section
    assert "ad.avg_call_duration_seconds" in section
    assert "ad.filtered_call_count" in section
    assert "workbenchDrill:{connected:'true'}" in section
    assert 'data-workbench-kpi-drill="duration"' in html
    assert 'data-workbench-kpi-drill="filtered"' in html
    assert "row['ad.avg_call_duration_seconds']" in html
    assert "row['ad.filtered_call_count']" in html
    assert '<span>业务定义</span>' in html
    assert '<span>统计范围</span>' in html
    assert '<span>解读方法</span>' in html
    assert '<span>使用建议</span>' in html
    assert "callWorkbenchApplyKpiDrill" not in html


def test_active_demo_dashboards_are_hr_only():
    base = Path(__file__).parents[1]
    dashboard_dir = base / "output" / "dashboards"

    assert {path.stem for path in dashboard_dir.glob("*.json")} == {
        "dash_hr_human_capital_panorama",
        "dash_hr_talent_vitality_pulse",
    }
    assert not (dashboard_dir / "dash_mall_robot_after_sales_marketing.json").exists()
    assert not (dashboard_dir / "dash_da_tms_call_quality_pulse.json").exists()
    assert not (dashboard_dir / "dash_da_tms_call_sop_workbench.json").exists()
    assert not (dashboard_dir / "dash_da_tms_call_monitor_alert.json").exists()
