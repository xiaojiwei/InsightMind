# HR Dashboard Drill-down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every KPI card, metric table cell, chart point, and heatmap cell in the two active HR dashboards open a valid drill-down flow.

**Architecture:** Keep the existing AD dashboard renderer and shared drill chooser. Extend the generic heatmap click payload so both row and column filters survive dynamic drill-down, then add small HR report HTML helpers that attach the existing shared chooser to metric-bearing elements with the correct measure and filters.

**Tech Stack:** FastAPI-served HTML template, vanilla JavaScript, ECharts fallback renderer, pytest, Node.js VM behavior tests.

**Spec:** `apps/ad/output/dashboards/dash_hr_human_capital_panorama.json` and `apps/ad/output/dashboards/dash_hr_talent_vitality_pulse.json`

## Global Constraints

- Modify the existing AD dashboard subsystem only; do not create a new subsystem.
- Use only the two active HR dashboard specifications and the active business graph.
- Reuse the existing shared drill chooser and semantic query APIs.
- Preserve unrelated uncommitted changes in the working tree.

---

### Task 1: Preserve Heatmap Cell Context

**Files:**
- Modify: `apps/ad/kg_builder/web/templates/index.html`
- Test: `apps/ad/tests/test_hr_dashboard_drilldown.py`

**Interfaces:**
- Produces: `adhocHeatmapDrillData(rowDim, rowValue, rowFilterValue, colDim, colValue, colFilterValue)` returning a dashboard click payload with `__drillFilters` for both dimensions.
- Produces: `dynamicDrillEntries(pending, nextMember)` returning drill stack entries with the selected next dimension on the last entry.

- [ ] **Step 1: Write the failing test**

```python
def test_heatmap_drill_preserves_row_and_column_filters():
    result = run_js_function("adhocHeatmapDrillData", [
        "ad.tenure_band", "3-5年", "3-5年",
        "ad.org_level_band", "L3", "L3",
    ])
    assert result["__drillFilters"] == [
        {"member": "ad.tenure_band", "operator": "equals", "values": ["3-5年"], "label": "3-5年"},
        {"member": "ad.org_level_band", "operator": "equals", "values": ["L3"], "label": "L3"},
    ]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `PYTHONPATH=apps/ad apps/ad/venv/bin/pytest apps/ad/tests/test_hr_dashboard_drilldown.py -q`

Expected: FAIL because the heatmap drill payload helper does not exist.

- [ ] **Step 3: Write minimal implementation**

```javascript
function adhocHeatmapDrillData(rowDim, rowValue, rowFilterValue, colDim, colValue, colFilterValue) {
  const drillFilters = [
    {member:rowDim, operator:'equals', values:[String(rowFilterValue ?? rowValue)], label:String(rowValue)},
    {member:colDim, operator:'equals', values:[String(colFilterValue ?? colValue)], label:String(colValue)},
  ];
  return {__member:rowDim, __value:rowValue, __filterValues:drillFilters[0].values, __drillFilters:drillFilters};
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `PYTHONPATH=apps/ad apps/ad/venv/bin/pytest apps/ad/tests/test_hr_dashboard_drilldown.py -q`

Expected: PASS.

### Task 2: Add HR Metric Drill Markup

**Files:**
- Modify: `apps/ad/kg_builder/web/templates/index.html`
- Test: `apps/ad/tests/test_hr_dashboard_drilldown.py`

**Interfaces:**
- Produces: `hrTalentPulseMetricCardHtml(...)`, `hrTalentPulseSummaryItemHtml(...)`, and `hrTalentPulseMetricCellHtml(...)`.
- Extends: `execReportBarRows(..., options)` and `execReportHeatmapHtml(..., options)` with optional drill metadata.

- [ ] **Step 1: Write the failing tests**

```python
def test_hr_metric_cell_uses_its_own_measure_and_filters():
    result = run_hr_metric_cell_helper("ad.monthly_base_payroll", "1,234", department_filters)
    assert result["measure"] == "ad.monthly_base_payroll"
    assert result["filters"] == department_filters

def test_hr_bar_row_drills_by_dimension_value():
    result = run_exec_report_bar_rows()
    assert result["measure"] == "ad.workforce_headcount"
    assert result["filters"][-1]["member"] == "ad.department"
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `PYTHONPATH=apps/ad apps/ad/venv/bin/pytest apps/ad/tests/test_hr_dashboard_drilldown.py -q`

Expected: FAIL because the HR helpers and drill options do not exist.

- [ ] **Step 3: Write minimal implementation**

Use the existing `callReportDrillAttrs` function for every metric-bearing HR element. Build row filters with `callReportMergedFilters` and `callReportDimensionFilter`, and bind each table metric cell to its own measure code.

- [ ] **Step 4: Run tests to verify they pass**

Run: `PYTHONPATH=apps/ad apps/ad/venv/bin/pytest apps/ad/tests/test_hr_dashboard_drilldown.py -q`

Expected: PASS.

### Task 3: Integration Verification

**Files:**
- Verify: `apps/ad/kg_builder/web/templates/index.html`
- Verify: `apps/ad/output/dashboards/dash_hr_human_capital_panorama.json`
- Verify: `apps/ad/output/dashboards/dash_hr_talent_vitality_pulse.json`

**Interfaces:**
- Consumes: the shared drill chooser, dashboard query renderer, and active HR dashboard JSON.
- Produces: two running dashboard pages whose metric interactions open drill-down UI without console or API errors.

- [ ] **Step 1: Run focused and regression tests**

```bash
PYTHONPATH=apps/ad apps/ad/venv/bin/pytest \
  apps/ad/tests/test_hr_dashboard_drilldown.py \
  apps/ad/tests/test_call_workbench_formatting.py -q
```

- [ ] **Step 2: Restart AD**

```bash
./scripts/insightmind.sh restart ad
```

- [ ] **Step 3: Verify both dashboard routes**

```text
http://localhost:8080/dashboard/view/dash_hr_human_capital_panorama
http://localhost:8080/dashboard/view/dash_hr_talent_vitality_pulse
```

Click KPI cards, bar/donut points, heatmap cells, and metric table cells; confirm the shared chooser opens and preserves the selected metric and dimensions.
