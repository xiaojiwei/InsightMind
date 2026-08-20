"""Lightweight business explanation for an abnormal pivot cell."""
from __future__ import annotations

from collections import Counter
from typing import Any


def _text(value: Any) -> str:
    return str(value if value is not None else "").strip()


def _path_items(payload: dict[str, Any]) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    for key in ("rowPath", "columnPath"):
        raw = payload.get(key)
        for item in raw if isinstance(raw, list) else []:
            if not isinstance(item, dict):
                continue
            code = _text(item.get("code") or item.get("dimensionCode"))
            value = _text(item.get("value") or item.get("filterValue"))
            if not code and not value:
                continue
            items.append({
                "code": code,
                "name": _text(item.get("name") or item.get("dimensionName") or code),
                "value": value,
                "filterValue": _text(item.get("filterValue") or value),
                "axis": "row" if key == "rowPath" else "column",
            })
    return items


def _doc_hits(payload: dict[str, Any]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for item in payload.get("documentAlertResults") or []:
        if not isinstance(item, dict):
            continue
        rule = item.get("rule") or {}
        result = item.get("result") or {}
        matches = result.get("matches") or []
        matched_rows = int((result.get("summary") or {}).get("matchedRows") or len(matches) or 0)
        if matched_rows <= 0:
            continue
        for match in matches[:20]:
            record = match.get("record") if isinstance(match, dict) else {}
            out.append({
                "ruleName": rule.get("name") or "单据追踪",
                "severity": rule.get("severity") or "warning",
                "documentNo": match.get("orderNumber") if isinstance(match, dict) else "",
                "field": result.get("targetColumn") or "",
                "fieldName": result.get("targetColumnName") or result.get("targetColumn") or "",
                "value": match.get("targetValue") if isinstance(match, dict) else "",
                "record": record or {},
            })
    return out


def _alert_hits(payload: dict[str, Any]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for item in payload.get("alertResults") or []:
        if not isinstance(item, dict):
            continue
        label = _text(item.get("label") or item.get("name") or item.get("type") or "指标预警")
        reason = _text(item.get("reason") or item.get("message") or item.get("description"))
        level_raw = item.get("level")
        try:
            level_num = int(level_raw or 0)
        except (TypeError, ValueError):
            level_num = 0
        severity = _text(item.get("severity"))
        if not severity:
            severity = "critical" if level_num >= 3 else "warning" if level_num >= 2 else "notice"
        out.append({
            "type": _text(item.get("type") or "metric"),
            "label": label,
            "severity": severity,
            "level": level_num,
            "reason": reason,
        })
    return out


def _severity_rank(severity: str) -> int:
    return {"critical": 3, "warning": 2, "notice": 1}.get(_text(severity), 0)


def _alert_anomaly_type(alerts: list[dict[str, Any]]) -> str:
    types = {_text(item.get("type")).lower() for item in alerts}
    labels = " ".join(_text(item.get("label")) + " " + _text(item.get("reason")) for item in alerts)
    if "path" in types or any(token in labels for token in ("环比", "波动", "路径", "趋势")):
        return "trend_anomaly"
    if "expression" in types or any(token in labels for token in ("表达式", "算子", "结构", "内部")):
        return "metric_spike"
    if "self" in types or any(token in labels for token in ("阈值", "偏低", "偏高", "自身")):
        return "metric_spike"
    return "dimension_slice"


def _anomaly_shape(anomaly_type: str) -> str:
    mapping = {
        "document_trace": "document",
        "metric_drop": "drop",
        "metric_rise": "rise",
        "metric_spike": "spike",
        "trend_anomaly": "trend",
        "dimension_slice": "slice",
        "data_quality": "quality",
    }
    return mapping.get(_text(anomaly_type), "unknown")


def _confidence_label(score: float) -> str:
    if score >= 0.8:
        return "高"
    if score >= 0.6:
        return "中"
    return "低"


def _strength_label(score: int) -> str:
    if score >= 80:
        return "强"
    if score >= 55:
        return "中"
    return "弱"


def _anomaly_source(doc_count: int, alert_count: int) -> str:
    if doc_count:
        return "document_rule"
    if alert_count:
        return "metric_alert"
    return "cell_context"


def _evidence_strength(doc_count: int, alerts: list[dict[str, Any]], contributions: list[dict[str, Any]]) -> int:
    if doc_count:
        return min(98, 78 + min(doc_count, 10) * 2)
    if alerts:
        top_rank = max((_severity_rank(item.get("severity")) for item in alerts), default=1)
        return min(90, 58 + top_rank * 8 + min(len(alerts), 5) * 3)
    if contributions:
        top_score = max((float(item.get("score") or 0) for item in contributions), default=0)
        return max(35, min(68, int(38 + top_score * 0.25)))
    return 30


def _confidence_score(source: str, strength: int, has_context: bool) -> float:
    base = {
        "document_rule": 0.88,
        "metric_alert": 0.7,
        "cell_context": 0.45,
    }.get(source, 0.35)
    score = base + max(0, strength - 55) * 0.003
    if has_context:
        score += 0.04
    return round(max(0.1, min(0.98, score)), 2)


def _evidence_items(
    documents: list[dict[str, Any]],
    alerts: list[dict[str, Any]],
    contributions: list[dict[str, Any]],
    context_label: str,
) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    if documents:
        first = documents[0]
        items.append({
            "type": "document",
            "label": f"命中 {len(documents)} 条异常单据",
            "detail": (
                f"代表单据 {first.get('documentNo') or '-'}，"
                f"{first.get('fieldName') or first.get('field') or '异常字段'}={first.get('value')}"
            ),
            "weight": 95,
        })
    if alerts:
        labels = "、".join(item.get("label") or "指标预警" for item in alerts[:4])
        items.append({
            "type": "alert",
            "label": f"命中 {len(alerts)} 个指标预警",
            "detail": labels,
            "weight": 75,
        })
    if context_label and context_label != "全部":
        items.append({
            "type": "context",
            "label": "当前分析范围",
            "detail": context_label,
            "weight": 60,
        })
    for item in contributions[:3]:
        label = item.get("dimensionName") or item.get("dimensionCode") or ""
        if not label:
            continue
        items.append({
            "type": "dimension",
            "label": f"推荐下钻维度：{label}",
            "detail": item.get("reason") or "",
            "weight": int(float(item.get("score") or 0)),
        })
    return items


def _diagnostic_hypotheses(
    anomaly_type: str,
    documents: list[dict[str, Any]],
    alerts: list[dict[str, Any]],
    contributions: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    if documents:
        first = documents[0]
        out.append({
            "type": "document_rule",
            "title": "单据规则命中",
            "reason": (
                f"优先核对规则「{first.get('ruleName') or '单据追踪'}」以及字段"
                f"「{first.get('fieldName') or first.get('field') or '异常字段'}」的业务含义。"
            ),
        })
    if alerts:
        types = {_text(item.get("type")) for item in alerts}
        if "expression" in types:
            out.append({
                "type": "metric_expression",
                "title": "指标表达式内部异常",
                "reason": "优先拆开指标算子，判断收入、成本、折扣等组成项是否出现结构性变化。",
            })
        elif "path" in types:
            out.append({
                "type": "trend_path",
                "title": "路径/趋势异常",
                "reason": "优先定位异常发生的时间段，再比较同口径切片的变化幅度。",
            })
        else:
            out.append({
                "type": "metric_alert",
                "title": "指标阈值预警",
                "reason": "先判断预警阈值是否命中业务预期，再下钻贡献维度。",
            })
    if not out and contributions:
        first_dim = contributions[0].get("dimensionName") or contributions[0].get("dimensionCode")
        out.append({
            "type": "dimension_drill",
            "title": "建议继续下钻核查",
            "reason": f"当前未发现规则判定的直接异常，建议先按「{first_dim}」查看不同业务分组的贡献差异。",
        })
    if not out:
        out.append({
            "type": anomaly_type or "unknown",
            "title": "证据不足",
            "reason": "当前只有单元格上下文，尚不足以形成稳定归因，需要补充时间对比、明细或预警规则。",
        })
    return out[:3]


def _candidate_dimensions(payload: dict[str, Any], catalog: dict[str, Any]) -> list[dict[str, Any]]:
    pivot_config = payload.get("pivotConfig") or {}
    selected = {
        _text(item.get("code") if isinstance(item, dict) else item)
        for group in ("rows", "columns")
        for item in (pivot_config.get(group) or [])
    }
    selected.update(item["code"] for item in _path_items(payload) if item.get("code"))
    measure_code = _text(payload.get("measureCode"))
    measures = catalog.get("measures") or []
    measure = next((m for m in measures if m.get("code") == measure_code), None)
    compatible = set(measure.get("dimensionCodes") or []) if measure else set()
    dims = []
    for dim in catalog.get("dimensions") or []:
        code = _text(dim.get("code"))
        if not code or code in selected:
            continue
        if compatible and code not in compatible:
            continue
        score = 60
        name = _text(dim.get("name") or code)
        if dim.get("isTime"):
            score -= 8
        if any(token in name for token in ("客户", "渠道", "商品", "门店", "区域", "仓库", "订单")):
            score += 18
        if dim.get("hasDimColumnExpr"):
            score += 6
        dims.append({
            "dimensionCode": code,
            "dimensionName": name,
            "score": max(1, min(99, score)),
            "reason": "与当前指标兼容，适合作为下一层业务下钻维度",
        })
    return sorted(dims, key=lambda item: (-item["score"], item["dimensionName"]))[:6]


def _document_contributions(documents: list[dict[str, Any]], candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not documents:
        return []
    dim_by_code = {item["dimensionCode"]: item for item in candidates}
    counters: dict[str, Counter[str]] = {}
    for doc in documents:
        record = doc.get("record") or {}
        for code, meta in dim_by_code.items():
            value = record.get(code) or record.get(meta["dimensionName"])
            if value not in (None, ""):
                counters.setdefault(code, Counter())[str(value)] += 1
    contributions = []
    total = max(1, len(documents))
    for code, counter in counters.items():
        if not counter:
            continue
        value, count = counter.most_common(1)[0]
        meta = dim_by_code.get(code) or {}
        contributions.append({
            "dimensionCode": code,
            "dimensionName": meta.get("dimensionName") or code,
            "value": value,
            "score": round(count / total * 100, 1),
            "reason": f"异常单据中该维度值出现 {count} 次",
        })
    return sorted(contributions, key=lambda item: item["score"], reverse=True)[:5]


class CellInsightService:
    """Build a quick, evidence-based explanation for one pivot metric cell."""

    def __init__(self, catalog: dict[str, Any]):
        self.catalog = catalog or {}

    def explain(self, payload: dict[str, Any]) -> dict[str, Any]:
        measure_code = _text(payload.get("measureCode"))
        measure_name = _text(payload.get("measureName") or measure_code)
        cell_value = payload.get("cellValue")
        path_items = _path_items(payload)
        context_label = " / ".join(
            f"{item['name']}={item['value']}" for item in path_items if item.get("value")
        ) or "全部"
        documents = _doc_hits(payload)
        alerts = _alert_hits(payload)
        candidates = _candidate_dimensions(payload, self.catalog)
        contributions = _document_contributions(documents, candidates)
        if not contributions:
            contributions = [
                {
                    **item,
                    "value": "",
                    "reason": item.get("reason") or "与当前指标兼容，建议作为下一步下钻维度",
                }
                for item in candidates[:3]
            ]

        severities = [doc.get("severity") for doc in documents]
        severities.extend(alert.get("severity") for alert in alerts)
        max_rank = max([_severity_rank(item) for item in severities] or [0])
        level = "critical" if max_rank >= 3 else "warning" if max_rank >= 2 else "notice"
        doc_count = len(documents)
        alert_count = len(alerts)
        anomaly_type = "document_trace" if doc_count else _alert_anomaly_type(alerts) if alerts else "dimension_slice"
        source = _anomaly_source(doc_count, alert_count)
        shape = _anomaly_shape(anomaly_type)
        evidence_strength = _evidence_strength(doc_count, alerts, contributions)
        confidence = _confidence_score(source, evidence_strength, bool(path_items))
        evidence = _evidence_items(documents, alerts, contributions, context_label)
        hypotheses = _diagnostic_hypotheses(anomaly_type, documents, alerts, contributions)
        if doc_count:
            anomaly_title = "发现单据级异常"
            anomaly_reason = f"当前单元格命中 {doc_count} 条单据追踪异常。"
        elif alert_count:
            names = "、".join(item["label"] for item in alerts[:4])
            anomaly_title = "发现指标预警"
            anomaly_reason = f"当前单元格命中 {alert_count} 个指标预警：{names}。"
        else:
            anomaly_title = "未发现单据级异常"
            anomaly_reason = "基于当前已配置的单据追踪规则，当前单元格未发现异常单据；仍可继续查看维度贡献或明细。"

        recommendations: list[dict[str, Any]] = []
        if doc_count:
            recommendations.append({"type": "detail", "label": "查看命中异常单据"})
        for item in contributions[:3]:
            if item.get("dimensionCode"):
                recommendations.append({
                    "type": "dimension_drill",
                    "dimensionCode": item["dimensionCode"],
                    "dimensionName": item.get("dimensionName") or item["dimensionCode"],
                    "label": f"按{item.get('dimensionName') or item['dimensionCode']}下钻",
                })
        recommendations.append({"type": "smart_insight", "label": "发送到智能Insight继续追问"})

        first_dim = contributions[0].get("dimensionName") if contributions else ""
        if doc_count:
            summary = (
                f"{measure_name} 在 {context_label} 下出现单据级异常，当前值为 {cell_value}，"
                f"命中 {doc_count} 条异常单据。建议先查看命中明细"
                f"{'，再按' + first_dim + '继续下钻。' if first_dim else '。'}"
            )
        elif alert_count:
            alert_names = "、".join(item["label"] for item in alerts[:4])
            summary = (
                f"{measure_name} 在 {context_label} 下命中指标预警（{alert_names}），当前值为 {cell_value}。"
                f"建议先按{first_dim or '推荐维度'}继续下钻，并发送到智能Insight做归因。"
            )
        else:
            summary = (
                f"{measure_name} 在 {context_label} 下当前值为 {cell_value}。"
                f"基于当前已配置的单据追踪规则，未发现单据级异常。"
                f"可按{first_dim or '推荐维度'}继续下钻观察贡献结构。"
            )

        return {
            "measure": {"code": measure_code, "name": measure_name},
            "cellContext": {
                "label": context_label,
                "filters": path_items,
                "entryType": _text(payload.get("entryType") or "metric_value"),
            },
            "cellValue": cell_value,
            "anomaly": {
                "level": level,
                "title": anomaly_title,
                "reason": anomaly_reason,
                "type": anomaly_type,
                "source": source,
                "shape": shape,
                "confidence": confidence,
                "confidenceLabel": _confidence_label(confidence),
                "evidenceStrength": evidence_strength,
                "evidenceStrengthLabel": _strength_label(evidence_strength),
                "alertTypes": sorted({_text(item.get("type")) for item in alerts if item.get("type")}),
            },
            "diagnosis": {
                "source": source,
                "shape": shape,
                "confidence": confidence,
                "confidenceLabel": _confidence_label(confidence),
                "evidenceStrength": evidence_strength,
                "evidenceStrengthLabel": _strength_label(evidence_strength),
                "evidence": evidence,
                "hypotheses": hypotheses,
            },
            "alerts": alerts,
            "contributions": contributions,
            "documents": documents,
            "recommendations": recommendations,
            "summary": summary,
            "source": "local",
        }
