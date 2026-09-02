import json
import re
import subprocess
from pathlib import Path
from urllib.parse import quote_plus

import pytest
import yaml
from sqlalchemy import create_engine, text
from sqlalchemy.exc import OperationalError


BASE_DIR = Path(__file__).parents[1]
TEMPLATE_PATH = BASE_DIR / "kg_builder" / "web" / "templates" / "index.html"
DASHBOARD_DIR = BASE_DIR / "output" / "dashboards"


def _javascript_function_source(name: str) -> str:
    html = TEMPLATE_PATH.read_text(encoding="utf-8")
    marker = f"function {name}("
    try:
        start = html.index(marker)
    except ValueError as exc:
        raise AssertionError(f"production JavaScript function is missing: {name}") from exc
    next_function = re.search(r"\nfunction [A-Za-z_$][A-Za-z0-9_$]*\(", html[start + 1 :])
    end = len(html) if next_function is None else start + 1 + next_function.start()
    return html[start:end].strip()


def _run_javascript(function_names: list[str], invocation: str, prelude: str = ""):
    source = "\n\n".join(_javascript_function_source(name) for name in function_names)
    script = f"""
{prelude}
{source}
{invocation}
"""
    result = subprocess.run(
        ["node", "-"],
        input=script,
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    return json.loads(result.stdout)


def test_active_hr_dashboard_specs_expose_drillable_measures():
    panorama = json.loads(
        (DASHBOARD_DIR / "dash_hr_human_capital_panorama.json").read_text(
            encoding="utf-8"
        )
    )
    pulse = json.loads(
        (DASHBOARD_DIR / "dash_hr_talent_vitality_pulse.json").read_text(
            encoding="utf-8"
        )
    )

    assert len(panorama["widgets"]) == 15
    for widget in panorama["widgets"]:
        spec = widget["spec"]
        assert spec["query"]["measures"], widget["id"]
        assert spec["drill"]["mode"] == "dynamic", widget["id"]

    pulse_widget = pulse["widgets"][0]
    assert pulse_widget["spec"]["query"]["measures"]
    assert pulse_widget["spec"]["view"]["encoding"]["reportTemplate"] == "hr_talent_pulse"


def test_hr_derived_dimensions_accept_bound_filter_values():
    config_path = BASE_DIR / "config.local.yaml"
    if not config_path.exists():
        pytest.skip("local HRRDB datasource is unavailable")
    datasource = yaml.safe_load(config_path.read_text(encoding="utf-8"))["datasources"][0]
    if str(datasource.get("database", "")).upper() != "HRRDB":
        pytest.skip("local datasource is not HRRDB")
    url = (
        f"mysql+pymysql://{quote_plus(str(datasource['username']))}:"
        f"{quote_plus(str(datasource['password']))}@{datasource['host']}:"
        f"{datasource.get('port', 3306)}/{datasource['database']}"
    )
    engine = create_engine(url)
    view_dimensions = {
        "vw_hr_employee_profile": [
            "tenure_band",
            "org_level_band",
            "compa_ratio_band",
            "salary_band_status",
            "commission_eligibility",
            "manager_status",
            "mobility_history_status",
        ],
        "vw_hr_department_profile": [
            "staffing_status",
            "manager_assignment_status",
            "department_size_band",
        ],
        "vw_hr_career_mobility": ["mobility_type"],
    }
    try:
        with engine.connect() as connection:
            for view, dimensions in view_dimensions.items():
                for dimension in dimensions:
                    value = connection.execute(
                        text(
                            f"SELECT `{dimension}` FROM HRRDB.`{view}` "
                            f"WHERE `{dimension}` IS NOT NULL LIMIT 1"
                        )
                    ).scalar_one()
                    count = connection.execute(
                        text(
                            f"SELECT COUNT(*) FROM HRRDB.`{view}` "
                            f"WHERE `{dimension}` = :value"
                        ),
                        {"value": value},
                    ).scalar_one()
                    assert count > 0, f"{view}.{dimension}"
    except OperationalError as exc:
        if exc.orig.args and exc.orig.args[0] in {1045, 2003}:
            pytest.skip("local HRRDB connection is unavailable")
        raise


def test_heatmap_drill_preserves_row_and_column_filters():
    result = _run_javascript(
        ["adhocHeatmapDrillData"],
        """
const result = adhocHeatmapDrillData(
  'ad.tenure_band', '3-5年', 'tenure-3-5',
  'ad.org_level_band', 'L3', 'level-3'
);
console.log(JSON.stringify(result));
""",
    )

    assert result["__member"] == "ad.tenure_band"
    assert result["__filterValues"] == ["tenure-3-5"]
    assert result["__drillFilters"] == [
        {
            "member": "ad.tenure_band",
            "operator": "equals",
            "values": ["tenure-3-5"],
            "label": "3-5年",
        },
        {
            "member": "ad.org_level_band",
            "operator": "equals",
            "values": ["level-3"],
            "label": "L3",
        },
    ]


def test_dynamic_drill_entries_keep_every_clicked_heatmap_dimension():
    result = _run_javascript(
        ["dynamicDrillEntries"],
        """
const result = dynamicDrillEntries({
  member:'ad.tenure_band',
  value:'3-5年',
  filterValues:['tenure-3-5'],
  drillFilters:[
    {member:'ad.tenure_band', operator:'equals', values:['tenure-3-5'], label:'3-5年'},
    {member:'ad.org_level_band', operator:'equals', values:['level-3'], label:'L3'},
  ],
}, 'ad.department');
console.log(JSON.stringify(result));
""",
    )

    assert [entry["member"] for entry in result] == [
        "ad.tenure_band",
        "ad.org_level_band",
    ]
    assert result[0].get("nextMember") is None
    assert result[1]["nextMember"] == "ad.department"


def test_heatmap_dimension_drill_renders_single_dimension_as_bar_chart():
    result = _run_javascript(
        ["renderAdhocResult"],
        """
renderAdhocResult(
  {data:[{'ad.salary_band_status':'带内', 'ad.workforce_headcount':7}]},
  {measures:['ad.workforce_headcount'], dimensions:['ad.salary_band_status']}
);
console.log(JSON.stringify({renderedChartType}));
""",
        prelude="""
const target = {innerHTML:''};
const document = {getElementById:() => target};
const window = {};
const _adhocRenderedQueries = {};
const _adhocState = {view:{type:'chart', chartType:'heatmap'}};
let renderedChartType = '';
function adhocChartClicked() {}
function ensureAdhocAlertStyles() {}
function renderKgAttributionWidget() {}
function renderExecutiveReportWidget() {}
function adhocClearChartTarget() {}
function adhocRecommendChart() { return 'heatmap'; }
function adhocKpiShouldRenderDrillChart() { return false; }
function adhocRecommendDrillChart() { return 'bar'; }
function renderAdhocTable() { renderedChartType = 'table'; }
function renderAdhocKpi() { renderedChartType = 'kpi'; }
function renderAdhocFunnel() { renderedChartType = 'funnel'; }
function renderAdhocSankey() { renderedChartType = 'sankey'; }
function renderAdhocHeatmap() { renderedChartType = 'heatmap'; }
function adhocGroupRowsForChart() { return [{label:'带内', value:7}]; }
function adhocChartDensityPlan(items) { return {items}; }
function adhocChartPalette() { return []; }
function prepareAdhocChartCanvas() { return target; }
function renderAdhocSvgChart(_target, chartType) { renderedChartType = chartType; }
""",
    )

    assert result["renderedChartType"] == "bar"


def test_hr_metric_cell_uses_its_own_measure_and_filters():
    result = _run_javascript(
        ["hrTalentPulseDrillContext", "hrTalentPulseMetricCellHtml"],
        """
const filters = [{member:'ad.department', operator:'equals', values:['Sales']}];
const html = hrTalentPulseMetricCellHtml(
  'ad.monthly_base_payroll', '1,234', filters,
  {rowLabel:'Sales', columnLabel:'月度薪酬'}
);
console.log(JSON.stringify({html, calls}));
""",
        prelude="""
const calls = [];
function escHtml(value) { return String(value ?? ''); }
function adhocMemberLabel(member) { return member; }
function callReportDrillAttrs(measure, filters, context) {
  calls.push({measure, filters, context});
  return `data-test-measure="${measure}"`;
}
""",
    )

    assert result["calls"] == [
        {
            "measure": "ad.monthly_base_payroll",
            "filters": [
                {
                    "member": "ad.department",
                    "operator": "equals",
                    "values": ["Sales"],
                }
            ],
            "context": {
                "metricLabel": "ad.monthly_base_payroll",
                "rowLabel": "Sales",
                "columnLabel": "月度薪酬",
            },
        }
    ]
    assert 'data-test-measure="ad.monthly_base_payroll"' in result["html"]
    assert "call-report-drillable" in result["html"]


def test_hr_bar_rows_drill_by_the_rendered_dimension_value():
    result = _run_javascript(
        ["execReportBarRows"],
        """
const html = execReportBarRows(
  [{'ad.department':'Sales', 'ad.workforce_headcount':12}],
  'ad.department',
  'ad.workforce_headcount',
  {drillable:true, filters:[{member:'ad.region', operator:'equals', values:['East']}]}
);
console.log(JSON.stringify({html, calls}));
""",
        prelude="""
const calls = [];
function execReportValue(row, key) { return Number(row?.[key] || 0); }
function execReportNumber(value) { return String(value); }
function execReportWan(value) { return String(value); }
function escHtml(value) { return String(value ?? ''); }
function adhocMemberLabel(member) { return member; }
function callReportDimensionFilter(member, value) {
  return {member, operator:'equals', values:[value]};
}
function callReportMergedFilters(base, extra) { return [...base, ...extra]; }
function callReportDrillAttrs(measure, filters, context) {
  calls.push({measure, filters, context});
  return `data-test-measure="${measure}"`;
}
""",
    )

    assert result["calls"][0]["measure"] == "ad.workforce_headcount"
    assert result["calls"][0]["filters"][-1] == {
        "member": "ad.department",
        "operator": "equals",
        "values": ["Sales"],
    }
    assert "call-report-drillable" in result["html"]


def test_hr_heatmap_cell_drills_with_both_axis_filters():
    result = _run_javascript(
        ["execReportHeatmapHtml"],
        """
const html = execReportHeatmapHtml(
  [{'ad.tenure_band':'3-5年', 'ad.org_level_band':'L3', 'ad.workforce_headcount':7}],
  'ad.tenure_band',
  'ad.org_level_band',
  'ad.workforce_headcount',
  [],
  {drillable:true, filters:[]}
);
console.log(JSON.stringify({html, calls}));
""",
        prelude="""
const calls = [];
function execReportValue(row, key) { return Number(row?.[key] || 0); }
function execReportNumber(value) { return String(value); }
function escHtml(value) { return String(value ?? ''); }
function adhocMemberLabel(member) { return member; }
function callReportDimensionFilter(member, value) {
  return {member, operator:'equals', values:[value]};
}
function callReportMergedFilters(base, extra) { return [...base, ...extra]; }
function callReportDrillAttrs(measure, filters, context) {
  calls.push({measure, filters, context});
  return `data-test-measure="${measure}"`;
}
""",
    )

    assert result["calls"][0]["measure"] == "ad.workforce_headcount"
    assert result["calls"][0]["filters"] == [
        {
            "member": "ad.tenure_band",
            "operator": "equals",
            "values": ["3-5年"],
        },
        {
            "member": "ad.org_level_band",
            "operator": "equals",
            "values": ["L3"],
        },
    ]
    assert "call-report-drillable" in result["html"]


def test_shared_drill_explanation_uses_the_active_metric_domain():
    result = _run_javascript(
        ["callReportBusinessExplainCopy"],
        """
console.log(JSON.stringify(callReportBusinessExplainCopy({metricLabel:'在册人数'})));
""",
    )

    assert result == {
        "title": "业务指标解释",
        "description": (
            "当前单元格用于观察在册人数，已继承看板公共筛选和当前点击点位。"
            "建议优先按推荐维度定位差异，再查看明细记录和业务证据。"
        ),
    }


def test_shared_drill_attributes_escape_apostrophes_in_payloads():
    attrs = _run_javascript(
        ["_escJs", "callReportDrillAttrs"],
        """
const attrs = callReportDrillAttrs(
  'ad.workforce_headcount',
  [{member:'ad.department', operator:'equals', values:["O'Reilly Sales"]}],
  {rowLabel:"O'Reilly Sales"}
);
console.log(JSON.stringify(attrs));
""",
    )

    assert "O'Reilly Sales" not in attrs
    assert "O\\'Reilly%20Sales" in attrs


def test_staffed_department_summary_drills_the_displayed_measure():
    result = _run_javascript(
        [
            "hrTalentPulseDrillContext",
            "hrTalentPulseSummaryItemHtml",
            "hrTalentPulseSummaryMetricHtml",
        ],
        """
const html = hrTalentPulseSummaryMetricHtml('staffedDepartments', '12', []);
console.log(JSON.stringify({html, calls}));
""",
        prelude="""
const calls = [];
function escHtml(value) { return String(value ?? ''); }
function adhocMemberLabel(member) { return member; }
function callReportDrillAttrs(measure, filters, context) {
  calls.push({measure, filters, context});
  return `data-test-measure="${measure}"`;
}
""",
    )

    assert result["calls"][0]["measure"] == "ad.staffed_department_count"
    assert "有人员部门" in result["html"]
    assert 'data-test-measure="ad.staffed_department_count"' in result["html"]
