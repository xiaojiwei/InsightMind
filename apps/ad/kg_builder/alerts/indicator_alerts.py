"""Deterministic MVP alert detection for semantic and pivot query results.

The AD service owns alert intelligence. DA remains a graph parser/query
executor, so this module only consumes AD query results and, for expression
alerts, optionally asks AD's semantic service for child measure values.
"""

from __future__ import annotations

import json
import math
from functools import lru_cache
from pathlib import Path
from statistics import median
from typing import Any, Callable


AlertLoadFn = Callable[[dict[str, Any]], dict[str, Any]]

SEVERITY = {
    1: {"code": "notice", "label": "关注", "color": "#f3c969"},
    2: {"code": "warning", "label": "异常", "color": "#d99042"},
    3: {"code": "critical", "label": "严重", "color": "#c84b4b"},
}

_ENGINE_OPERATORS = {"statistical", "fluctuation"}
_SEVERITY_TO_LEVEL = {"notice": 1, "warning": 2, "critical": 3}


def annotate_semantic_result(
    result: dict[str, Any],
    query: dict[str, Any],
    catalog: dict[str, Any],
    ttl_path: str | Path,
    load_fn: AlertLoadFn | None = None,
) -> dict[str, Any]:
    """Attach row-level alert annotations to AD semantic API results."""
    rows = result.get("data") if isinstance(result, dict) else None
    if not isinstance(rows, list) or not rows:
        return _with_alert_summary(result, [])

    measure_members = [str(item) for item in (query.get("measures") or []) if item]
    dimension_members = [str(item) for item in (query.get("dimensions") or []) if item]
    if not measure_members:
        return _with_alert_summary(result, [])

    by_member = _measure_meta_by_member(catalog)
    member_to_code = {
        member: (by_member.get(member) or {}).get("code") or _member_to_code(member, "MEAS_")
        for member in measure_members
    }

    persisted_rules = _persisted_query_rules(query, set(member_to_code.values()), {v: k for k, v in member_to_code.items()})

    alerts: list[dict[str, Any]] = []
    for measure_member in measure_members:
        measure_code = member_to_code.get(measure_member, "")
        measure_rules = [rule for rule in persisted_rules if rule.get("measureCode") == measure_code]
        if not measure_rules:
            continue
        values = [_to_float(row.get(measure_member)) for row in rows]

        if any(_is_engine_rule(rule, "self") for rule in measure_rules):
            row_alerts = _filter_alerts_by_rules(
                rows,
                _detect_self_alerts(values, measure_member, measure_code),
                [rule for rule in measure_rules if _is_engine_rule(rule, "self")],
            )
            _attach_row_alerts(rows, row_alerts)
            alerts.extend(_inflate_alerts(rows, row_alerts))

        if any(_is_engine_rule(rule, "path") for rule in measure_rules):
            path_alerts = _filter_alerts_by_rules(
                rows,
                _detect_path_alerts(rows, values, measure_member, measure_code, dimension_members),
                [rule for rule in measure_rules if _is_engine_rule(rule, "path")],
            )
            _attach_row_alerts(rows, path_alerts)
            alerts.extend(_inflate_alerts(rows, path_alerts))

    if load_fn and any(_is_engine_rule(rule, "expression") for rule in persisted_rules):
        expression_alerts = _detect_expression_alerts(
            rows=rows,
            query={**query, "enableAlerts": False},
            measure_members=[
                member for member in measure_members
                if any(_is_engine_rule(rule, "expression") and rule.get("measure") == member for rule in persisted_rules)
            ],
            member_to_code=member_to_code,
            catalog=catalog,
            ttl_path=ttl_path,
            load_fn=load_fn,
        )
        _attach_row_alerts(rows, expression_alerts)
        alerts.extend(_inflate_alerts(rows, expression_alerts))

    rule_alerts = _detect_configured_alerts(rows, persisted_rules)
    _attach_row_alerts(rows, rule_alerts)
    alerts.extend(_inflate_alerts(rows, rule_alerts))

    return _with_alert_summary(result, alerts)


def annotate_pivot_result(
    result: dict[str, Any],
    measures: list[dict[str, Any]],
    rows: list[dict[str, Any]],
    columns: list[dict[str, Any]],
    rules: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Attach cell-level alert annotations to pivot query results."""
    cells = result.get("cells") if isinstance(result, dict) else None
    if not isinstance(cells, list) or not cells:
        return _with_alert_summary(result, [])

    measure_codes = [str(item.get("code") or "") for item in measures if item.get("code")]
    persisted_rules = _persisted_query_rules({}, set(measure_codes), {})
    alerts: list[dict[str, Any]] = []
    for code in measure_codes:
        measure_rules = [rule for rule in persisted_rules if rule.get("measureCode") == code]
        if not measure_rules:
            continue
        indexes = [idx for idx, cell in enumerate(cells) if str(cell.get("measureCode") or "") == code]
        values = [_to_float(cells[idx].get("value")) for idx in indexes]

        if any(_is_engine_rule(rule, "self") for rule in measure_rules):
            row_alerts = _detect_self_alerts(values, code, code)
            for local_idx, alert in row_alerts.items():
                alert["cellIndex"] = indexes[local_idx]
                alert["rowKey"] = cells[indexes[local_idx]].get("rowKey")
                alert["columnKey"] = cells[indexes[local_idx]].get("columnKey")
            _attach_cell_alerts(cells, row_alerts, indexes)
            alerts.extend(_inflate_cell_alerts(cells, row_alerts, indexes))

        if any(_is_engine_rule(rule, "path") for rule in measure_rules):
            path_alerts = _detect_pivot_path_alerts(cells, indexes, values, rows, columns, code)
            _attach_cell_alerts(cells, path_alerts, indexes)
            alerts.extend(_inflate_cell_alerts(cells, path_alerts, indexes))

    configured = _detect_pivot_configured_alerts(cells, persisted_rules)
    for local_idx, alert in configured.items():
        cell = cells[local_idx]
        bucket = cell.setdefault("alerts", [])
        if not _contains_alert(bucket, alert):
            bucket.append(alert)
    alerts.extend({**alert, "cellIndex": idx} for idx, alert in configured.items())

    return _with_alert_summary(result, alerts)


def _detect_self_alerts(values: list[float | None], member: str, code: str) -> dict[int, dict[str, Any]]:
    numeric = [value for value in values if value is not None]
    if len(numeric) < 3:
        return {}

    med = median(numeric)
    abs_dev = [abs(value - med) for value in numeric]
    mad = median(abs_dev) or 0.0
    q1, q3 = _quantile(numeric, 0.25), _quantile(numeric, 0.75)
    iqr = max(q3 - q1, 0.0)
    non_zero_baseline = median([abs(value) for value in numeric if abs(value) > 1e-12] or [0.0])
    alerts: dict[int, dict[str, Any]] = {}

    for idx, value in enumerate(values):
        if value is None:
            continue
        severity = 0
        reason = ""
        if non_zero_baseline > 0 and abs(value) <= max(non_zero_baseline * 0.01, 1e-9):
            severity = 3
            reason = "当前值接近 0，但同组样本存在明显非零基线"
        elif mad > 0:
            robust_z = abs(0.6745 * (value - med) / mad)
            if robust_z >= 5.0:
                severity = 3
                reason = f"偏离样本中位数 {robust_z:.1f} 个 robust z-score"
            elif robust_z >= 3.5:
                severity = 2
                reason = f"偏离样本中位数 {robust_z:.1f} 个 robust z-score"
        if severity == 0 and iqr > 0:
            if value < q1 - 3 * iqr or value > q3 + 3 * iqr:
                severity = 2
                reason = "超过 IQR 极值边界"

        prev = _previous_numeric(values, idx)
        if prev is not None and abs(prev) > 1e-12:
            ratio = (value - prev) / abs(prev)
            if ratio <= -0.9:
                severity = max(severity, 3)
                reason = f"较上一点下降 {abs(ratio) * 100:.0f}%"
            elif ratio >= 3.0:
                severity = max(severity, 3)
                reason = f"较上一点增长 {ratio * 100:.0f}%"
            elif abs(ratio) >= 1.5:
                severity = max(severity, 2)
                reason = f"较上一点波动 {ratio * 100:.0f}%"

        if severity:
            alerts[idx] = _alert(
                "self",
                severity,
                "自身数据异常",
                member,
                code,
                reason,
                style="background",
            )
    return alerts


def _detect_path_alerts(
    rows: list[dict[str, Any]],
    values: list[float | None],
    member: str,
    code: str,
    dimensions: list[str],
) -> dict[int, dict[str, Any]]:
    numeric = [value for value in values if value is not None]
    if not dimensions or len(numeric) < 4:
        return {}
    med = median(numeric)
    q1, q3 = _quantile(numeric, 0.25), _quantile(numeric, 0.75)
    iqr = max(q3 - q1, 0.0)
    if iqr <= 0 and med == 0:
        return {}

    hi_indexes = []
    lo_indexes = []
    for idx, value in enumerate(values):
        if value is None:
            continue
        if iqr > 0 and value > q3 + 2.5 * iqr:
            hi_indexes.append(idx)
        if iqr > 0 and value < q1 - 2.5 * iqr:
            lo_indexes.append(idx)
        if med > 0 and value <= max(med * 0.02, 1e-9):
            lo_indexes.append(idx)
        if med > 0 and value >= med * 5:
            hi_indexes.append(idx)

    if not hi_indexes and not lo_indexes:
        return {}
    severity = 3 if hi_indexes and lo_indexes else 2
    alerts: dict[int, dict[str, Any]] = {}
    for idx in sorted(set(hi_indexes + lo_indexes)):
        dim_text = _row_dimension_text(rows[idx], dimensions)
        direction = "暴涨" if idx in hi_indexes else "跌零/极低"
        alerts[idx] = _alert(
            "path",
            severity,
            "路径异常",
            member,
            code,
            f"{dim_text} 出现{direction}，整体路径内部分化明显",
            style="dashedBorder",
        )
    return alerts


def _detect_expression_alerts(
    rows: list[dict[str, Any]],
    query: dict[str, Any],
    measure_members: list[str],
    member_to_code: dict[str, str],
    catalog: dict[str, Any],
    ttl_path: str | Path,
    load_fn: AlertLoadFn,
) -> dict[int, dict[str, Any]]:
    meta = _expression_meta(str(ttl_path))
    code_to_member = {value: key for key, value in member_to_code.items()}
    result: dict[int, dict[str, Any]] = {}

    for parent_member in measure_members:
        parent_code = member_to_code.get(parent_member, "")
        operands = [code for code in (meta.get(parent_code) or {}).get("operands", []) if code]
        if not operands:
            continue
        operand_members = [
            _catalog_member_name(catalog, code)
            for code in operands
            if _catalog_member_name(catalog, code)
        ]
        if not operand_members:
            continue
        operand_query = {
            **query,
            "measures": operand_members,
            "limit": query.get("limit") or query.get("pageSize") or 1000,
        }
        try:
            child_result = load_fn(operand_query)
        except Exception:
            continue
        child_rows = child_result.get("data") or []
        child_by_key = {
            _dimension_key(row, query.get("dimensions") or []): row
            for row in child_rows if isinstance(row, dict)
        }
        for operand_member in operand_members:
            child_values = [_to_float(row.get(operand_member)) for row in child_rows]
            child_self = _detect_self_alerts(
                child_values,
                operand_member,
                member_to_code.get(operand_member) or _member_to_code(operand_member, "MEAS_"),
            )
            for child_idx, child_alert in child_self.items():
                child_row = child_rows[child_idx] if child_idx < len(child_rows) else {}
                row_idx = _find_row_index_by_key(rows, query.get("dimensions") or [], _dimension_key(child_row, query.get("dimensions") or []))
                if row_idx is None:
                    continue
                parent_self = [
                    alert for alert in rows[row_idx].get("__alerts", [])
                    if alert.get("type") == "self" and alert.get("measure") == parent_member
                ]
                if parent_self:
                    continue
                severity = max(2, int(child_alert.get("level") or 2))
                result[row_idx] = _alert(
                    "expression",
                    severity,
                    "计算表达式内部异常",
                    parent_member,
                    parent_code,
                    f"子指标 {child_alert.get('measure')} {child_alert.get('reason')}，但父指标整体未明显异常",
                    style="solidBorder",
                    relatedMeasures=[child_alert.get("measureCode") or child_alert.get("measure")],
                )
    return result


def _detect_pivot_path_alerts(
    cells: list[dict[str, Any]],
    indexes: list[int],
    values: list[float | None],
    row_headers: list[dict[str, Any]],
    column_headers: list[dict[str, Any]],
    code: str,
) -> dict[int, dict[str, Any]]:
    if len(indexes) < 4 or (len(row_headers) <= 1 and len(column_headers) <= 1):
        return {}
    fake_rows = [
        {
            "__path": " / ".join(
                [str(x.get("value") or "-") for x in cells[indexes[i]].get("rowPath", [])]
                + [str(x.get("value") or "-") for x in cells[indexes[i]].get("columnPath", [])]
            )
        }
        for i in range(len(indexes))
    ]
    alerts = _detect_path_alerts(fake_rows, values, code, code, ["__path"])
    for alert in alerts.values():
        alert["measureCode"] = code
    return alerts


def _detect_pivot_configured_alerts(
    cells: list[dict[str, Any]],
    rules: list[dict[str, Any]],
) -> dict[int, dict[str, Any]]:
    alerts: dict[int, dict[str, Any]] = {}
    if not isinstance(rules, list):
        return alerts
    for rule in rules:
        if not isinstance(rule, dict) or rule.get("enabled") is False:
            continue
        measure = str(rule.get("measure") or "")
        measure_code = _member_to_code(measure, "MEAS_") if measure else ""
        kind = str(rule.get("type") or "self")
        level = int(rule.get("severity") or rule.get("level") or 2)
        operator = str(rule.get("operator") or "always").lower()
        threshold = _to_float(rule.get("threshold"))
        threshold2 = _to_float(rule.get("threshold2"))
        dimensions = rule.get("dimensions") or rule.get("where") or {}
        for idx, cell in enumerate(cells):
            if measure_code and str(cell.get("measureCode") or "") != measure_code:
                continue
            if not _pivot_rule_dimensions_match(cell, dimensions):
                continue
            value = _to_float(cell.get("value"))
            if not _rule_value_match(value, operator, threshold, threshold2):
                continue
            label = {
                "self": "自身数据异常",
                "expression": "计算表达式内部异常",
                "path": "路径异常",
            }.get(kind, "自身数据异常")
            style = {
                "self": "background",
                "expression": "solidBorder",
                "path": "dashedBorder",
            }.get(kind, "background")
            alerts[idx] = _alert(
                kind,
                level,
                label,
                measure or str(cell.get("measureName") or cell.get("measureCode") or ""),
                str(cell.get("measureCode") or measure_code),
                str(rule.get("reason") or _rule_reason(operator, threshold, threshold2)),
                style=style,
                ruleId=str(rule.get("id") or ""),
            )
    return alerts


def _pivot_rule_dimensions_match(cell: dict[str, Any], dim_match: Any) -> bool:
    if not isinstance(dim_match, dict) or not dim_match:
        return True
    path_values = [str(item.get("value") or "") for item in (cell.get("rowPath") or []) + (cell.get("columnPath") or [])]
    for _member, expected in dim_match.items():
        values = expected if isinstance(expected, list) else [expected]
        if not any(str(value) in path_values for value in values):
            return False
    return True


def _detect_configured_alerts(
    rows: list[dict[str, Any]],
    rules: list[dict[str, Any]],
) -> dict[int, dict[str, Any]]:
    """Apply threshold-style rules loaded from the persistent alert_rule table."""
    if not isinstance(rules, list):
        return {}
    alerts: dict[int, dict[str, Any]] = {}
    for rule in rules:
        if not isinstance(rule, dict) or rule.get("enabled") is False:
            continue
        if str(rule.get("operator") or "").lower() in _ENGINE_OPERATORS | {"document"}:
            continue
        measure = str(rule.get("measure") or "")
        if not measure:
            continue
        kind = str(rule.get("type") or "self")
        level = int(rule.get("severity") or rule.get("level") or 2)
        operator = str(rule.get("operator") or "always").lower()
        threshold = _to_float(rule.get("threshold"))
        threshold2 = _to_float(rule.get("threshold2"))
        dim_match = rule.get("dimensions") or rule.get("where") or {}
        for idx, row in enumerate(rows):
            if not _rule_dimensions_match(row, dim_match):
                continue
            value = _to_float(row.get(measure))
            if not _rule_value_match(value, operator, threshold, threshold2):
                continue
            label = {
                "self": "自身数据异常",
                "expression": "计算表达式内部异常",
                "path": "路径异常",
            }.get(kind, "自身数据异常")
            style = {
                "self": "background",
                "expression": "solidBorder",
                "path": "dashedBorder",
            }.get(kind, "background")
            reason = str(rule.get("reason") or _rule_reason(operator, threshold, threshold2))
            alerts[idx] = _alert(
                kind,
                level,
                label,
                measure,
                str(rule.get("measureCode") or ""),
                reason,
                style=style,
                ruleId=str(rule.get("id") or ""),
            )
    return alerts


def _rule_dimensions_match(row: dict[str, Any], dim_match: Any) -> bool:
    if not isinstance(dim_match, dict) or not dim_match:
        return True
    for member, expected in dim_match.items():
        actual = row.get(member)
        values = expected if isinstance(expected, list) else [expected]
        if str(actual) not in {str(value) for value in values}:
            return False
    return True


def _rule_value_match(value: float | None, operator: str, threshold: float | None, threshold2: float | None) -> bool:
    if operator == "always":
        return True
    if value is None:
        return False
    if operator in {"lt", "less_than"}:
        return threshold is not None and value < threshold
    if operator in {"lte", "le", "less_than_or_equal"}:
        return threshold is not None and value <= threshold
    if operator in {"gt", "greater_than"}:
        return threshold is not None and value > threshold
    if operator in {"gte", "ge", "greater_than_or_equal"}:
        return threshold is not None and value >= threshold
    if operator in {"eq", "equals", "equal"}:
        return threshold is not None and value == threshold
    if operator == "between":
        return threshold is not None and threshold2 is not None and threshold <= value <= threshold2
    if operator == "outside":
        return threshold is not None and threshold2 is not None and not (threshold <= value <= threshold2)
    if operator in {"zero", "drop_to_zero"}:
        return abs(value) <= 1e-9
    return False


def _rule_reason(operator: str, threshold: float | None, threshold2: float | None) -> str:
    if operator == "always":
        return "命中手工配置的演示规则"
    if operator == "between":
        return f"命中区间规则 [{threshold}, {threshold2}]"
    if operator == "outside":
        return f"超出规则区间 [{threshold}, {threshold2}]"
    if operator in {"zero", "drop_to_zero"}:
        return "命中跌零规则"
    return f"命中规则 {operator} {threshold}"


def _persisted_query_rules(
    query: dict[str, Any],
    measure_codes: set[str],
    code_to_member: dict[str, str],
) -> list[dict[str, Any]]:
    try:
        from . import models

        rows = models.list_enabled_rules()
    except Exception:
        return []

    requested_ids = {int(item) for item in (query.get("alertRuleIds") or []) if str(item).isdigit()}
    rules: list[dict[str, Any]] = []
    for row in rows:
        try:
            rule_id = int(row.get("id"))
        except Exception:
            rule_id = 0
        if requested_ids and rule_id not in requested_ids:
            continue
        measure_code = str(row.get("measure_code") or "")
        if measure_codes and measure_code not in measure_codes:
            continue
        rules.append(_runtime_rule_from_persisted(row, code_to_member))
    return rules


def _runtime_rule_from_persisted(row: dict[str, Any], code_to_member: dict[str, str]) -> dict[str, Any]:
    measure_code = str(row.get("measure_code") or "")
    severity = str(row.get("severity") or "warning")
    operator = str(row.get("operator") or "always").lower()
    kind = str(row.get("builtin_type") or "").lower()
    if kind not in {"self", "path", "expression"}:
        kind = "path" if operator == "fluctuation" else "self"
    return {
        "id": row.get("id"),
        "type": kind,
        "measure": code_to_member.get(measure_code) or _catalog_member_name_from_code(measure_code),
        "measureCode": measure_code,
        "operator": operator,
        "threshold": row.get("threshold"),
        "threshold2": row.get("threshold2"),
        "severity": _SEVERITY_TO_LEVEL.get(severity, 2),
        "dimensions": _dimensions_from_persisted(row.get("dimensions_json")),
        "reason": row.get("description") or _rule_reason(operator, row.get("threshold"), row.get("threshold2")),
        "enabled": bool(row.get("enabled", True)),
    }


def _dimensions_from_persisted(raw: Any) -> dict[str, Any]:
    if not raw:
        return {}
    try:
        data = json.loads(raw) if isinstance(raw, str) else raw
    except Exception:
        return {}
    if not isinstance(data, dict):
        return {}
    out = {}
    for key, value in data.items():
        member = _dimension_member_from_code(str(key))
        if isinstance(value, str) and "," in value:
            out[member] = [part.strip() for part in value.split(",") if part.strip()]
        else:
            out[member] = value
    return out


def _dimension_member_from_code(code: str) -> str:
    if code.startswith("ad."):
        return code
    if code.startswith("DIM_"):
        return f"ad.{_normalize_member_key(code)}"
    return code


def _catalog_member_name_from_code(code: str) -> str:
    if not code:
        return ""
    return f"ad.{_normalize_member_key(code)}"


def _is_engine_rule(rule: dict[str, Any], kind: str) -> bool:
    return str(rule.get("operator") or "").lower() in _ENGINE_OPERATORS and str(rule.get("type") or "self") == kind


def _filter_alerts_by_rules(
    rows: list[dict[str, Any]],
    alerts: dict[int, dict[str, Any]],
    rules: list[dict[str, Any]],
) -> dict[int, dict[str, Any]]:
    if not rules:
        return {}
    out = {}
    for idx, alert in alerts.items():
        row = rows[idx] if idx < len(rows) else {}
        matching = [rule for rule in rules if _rule_dimensions_match(row, rule.get("dimensions") or {})]
        if not matching:
            continue
        rule = matching[0]
        out[idx] = {**alert, "ruleId": str(rule.get("id") or "")}
    return out


def _attach_row_alerts(rows: list[dict[str, Any]], alerts: dict[int, dict[str, Any]]) -> None:
    for idx, alert in alerts.items():
        if idx < 0 or idx >= len(rows):
            continue
        bucket = rows[idx].setdefault("__alerts", [])
        if not _contains_alert(bucket, alert):
            bucket.append(alert)


def _attach_cell_alerts(cells: list[dict[str, Any]], alerts: dict[int, dict[str, Any]], indexes: list[int]) -> None:
    for local_idx, alert in alerts.items():
        if local_idx < 0 or local_idx >= len(indexes):
            continue
        cell = cells[indexes[local_idx]]
        bucket = cell.setdefault("alerts", [])
        if not _contains_alert(bucket, alert):
            bucket.append(alert)


def _with_alert_summary(result: dict[str, Any], alerts: list[dict[str, Any]]) -> dict[str, Any]:
    summary = {
        "enabled": True,
        "count": len(alerts),
        "maxLevel": max([int(item.get("level") or 0) for item in alerts] or [0]),
        "items": alerts[:200],
        "legend": [
            {"type": "self", "label": "自身数据异常", "style": "background"},
            {"type": "expression", "label": "计算表达式内部异常", "style": "solidBorder"},
            {"type": "path", "label": "路径异常", "style": "dashedBorder"},
        ],
        "severity": SEVERITY,
    }
    result["alerts"] = summary
    diagnostics = result.setdefault("diagnostics", {})
    diagnostics["alertCount"] = summary["count"]
    diagnostics["alertMaxLevel"] = summary["maxLevel"]
    return result


def _inflate_alerts(rows: list[dict[str, Any]], alerts: dict[int, dict[str, Any]]) -> list[dict[str, Any]]:
    out = []
    for idx, alert in alerts.items():
        out.append({**alert, "rowIndex": idx, "row": _public_row(rows[idx]) if idx < len(rows) else {}})
    return out


def _inflate_cell_alerts(cells: list[dict[str, Any]], alerts: dict[int, dict[str, Any]], indexes: list[int]) -> list[dict[str, Any]]:
    out = []
    for local_idx, alert in alerts.items():
        if local_idx < len(indexes):
            out.append({**alert, "cellIndex": indexes[local_idx]})
    return out


def _alert(kind: str, level: int, label: str, measure: str, code: str, reason: str, style: str, **extra: Any) -> dict[str, Any]:
    sev = SEVERITY[max(1, min(3, int(level)))]
    return {
        "type": kind,
        "label": label,
        "level": max(1, min(3, int(level))),
        "severity": sev["code"],
        "severityLabel": sev["label"],
        "color": sev["color"],
        "style": style,
        "measure": measure,
        "measureCode": code,
        "reason": reason,
        **extra,
    }


def _measure_meta_by_member(catalog: dict[str, Any]) -> dict[str, dict[str, Any]]:
    out = {}
    for item in catalog.get("measures") or []:
        code = str(item.get("code") or "")
        if not code:
            continue
        aliases = {code, _catalog_member_name(catalog, code), f"ad.{_normalize_member_key(code)}"}
        for alias in aliases:
            if alias:
                out[alias] = item
    return out


def _catalog_member_name(catalog: dict[str, Any], code: str) -> str:
    for item in catalog.get("measures") or []:
        if str(item.get("code") or "") == code:
            return f"ad.{_normalize_member_key(code)}"
    return ""


def _member_to_code(member: str, prefix: str) -> str:
    raw = str(member or "").strip()
    simple = raw.rsplit(".", 1)[-1]
    if simple.startswith(prefix):
        return simple
    return prefix + simple.upper()


def _normalize_member_key(code: str) -> str:
    value = str(code or "")
    if value.startswith("MEAS_"):
        value = value[5:]
    elif value.startswith("DIM_"):
        value = value[4:]
    return value.lower()


@lru_cache(maxsize=8)
def _expression_meta(ttl_path: str) -> dict[str, dict[str, Any]]:
    path = Path(ttl_path)
    if not path.exists():
        return {}
    try:
        from rdflib import Graph, Namespace
    except ImportError:
        return {}

    graph = Graph()
    graph.parse(str(path), format="turtle")
    ind = Namespace("http://indicator.insightmind.com/ontology#")
    meta: dict[str, dict[str, Any]] = {}
    for measure in graph.subjects(None, None):
        code_node = graph.value(measure, ind.code)
        code = str(code_node or "")
        if not code.startswith("MEAS_"):
            continue
        operands: list[str] = []
        for app in graph.objects(measure, ind.hasMeasureApp):
            expression = str(graph.value(app, ind.expression) or "")
            operands.extend(_parse_expression_operands(expression))
        if operands:
            meta[code] = {"operands": sorted(set(operands))}
    return meta


def _parse_expression_operands(expression: str) -> list[str]:
    if not expression:
        return []
    try:
        parsed = json.loads(expression)
    except Exception:
        return []
    operands: list[str] = []

    def walk(value: Any) -> None:
        if isinstance(value, dict):
            meas_code = value.get("measCode")
            if isinstance(meas_code, str) and meas_code.startswith("MEAS_"):
                operands.append(meas_code)
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(parsed)
    return operands


def _to_float(value: Any) -> float | None:
    try:
        text = str(value).replace(",", "").strip()
        if text in {"", "-", "None", "null"}:
            return None
        number = float(text)
        if math.isnan(number) or math.isinf(number):
            return None
        return number
    except Exception:
        return None


def _quantile(values: list[float], q: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    pos = (len(ordered) - 1) * q
    lo = int(math.floor(pos))
    hi = int(math.ceil(pos))
    if lo == hi:
        return ordered[lo]
    return ordered[lo] * (hi - pos) + ordered[hi] * (pos - lo)


def _previous_numeric(values: list[float | None], idx: int) -> float | None:
    for pos in range(idx - 1, -1, -1):
        if values[pos] is not None:
            return values[pos]
    return None


def _row_dimension_text(row: dict[str, Any], dimensions: list[str]) -> str:
    parts = []
    for dim in dimensions[:3]:
        value = row.get(dim)
        if value not in (None, ""):
            parts.append(f"{dim}={value}")
    return "、".join(parts) or "当前路径"


def _dimension_key(row: dict[str, Any], dimensions: list[str]) -> tuple[Any, ...]:
    return tuple(row.get(dim) for dim in dimensions)


def _find_row_index_by_key(rows: list[dict[str, Any]], dimensions: list[str], key: tuple[Any, ...]) -> int | None:
    for idx, row in enumerate(rows):
        if _dimension_key(row, dimensions) == key:
            return idx
    return None


def _public_row(row: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in row.items() if not str(key).startswith("__")}


def _contains_alert(bucket: list[dict[str, Any]], alert: dict[str, Any]) -> bool:
    return any(
        item.get("type") == alert.get("type")
        and item.get("measure") == alert.get("measure")
        and item.get("reason") == alert.get("reason")
        for item in bucket
    )
