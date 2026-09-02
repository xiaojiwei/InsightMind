"""Versioned semantic-plan and explain-plan contracts for feedback learning.

The contract is deliberately observational in v1: it records what the real
query path selected and executed, but it never rewrites a query or updates the
business KG.  This makes it safe to attach to every backend entry point while
the correction and memory workflow is being productised.
"""

from __future__ import annotations

import hashlib
import json
from typing import Any, Iterable

from .sanitizer import sanitize_text


SEMANTIC_PLAN_VERSION = "1.0"
EXPLAIN_PLAN_VERSION = "1.0"


def _strings(values: Any, *, limit: int = 50) -> list[str]:
    if values is None:
        return []
    if not isinstance(values, (list, tuple, set)):
        values = [values]
    result: list[str] = []
    for value in values:
        if isinstance(value, dict):
            value = (
                value.get("code")
                or value.get("member")
                or value.get("measureCode")
                or value.get("dimensionCode")
                or value.get("name")
            )
        text = sanitize_text(value, max_chars=255).strip()
        if text and text not in result:
            result.append(text)
        if len(result) >= limit:
            break
    return result


def _raw_strings(values: Any, *, limit: int = 100) -> list[str]:
    """Sanitize members while preserving duplicates for correction evidence."""
    if values is None:
        return []
    if not isinstance(values, (list, tuple, set)):
        values = [values]
    result = []
    for value in values:
        text = sanitize_text(value, max_chars=255).strip()
        if text:
            result.append(text)
        if len(result) >= limit:
            break
    return result


def _hash_json(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _member_kind(value: str) -> str:
    code = str(value or "").upper()
    if code.startswith("MEAS_") or ".MEAS_" in code:
        return "measure"
    if code.startswith("DIM_") or ".DIM_" in code or "HIER_" in code:
        return "dimension"
    return ""


def _configure_codes(payload: Any) -> tuple[list[str], list[str]]:
    if not isinstance(payload, dict):
        return [], []
    measures: list[str] = []
    dimensions: list[str] = []
    for item in payload.get("configureList") or []:
        if not isinstance(item, dict):
            continue
        code = sanitize_text(
            item.get("code") or item.get("measureCode") or item.get("dimensionCode"),
            max_chars=255,
        ).strip()
        if not code:
            continue
        kind = str(item.get("kind") or item.get("type") or "").lower()
        inferred = _member_kind(code)
        if "measure" in kind or inferred == "measure":
            measures.append(code)
        elif "dimension" in kind or inferred == "dimension":
            dimensions.append(code)
    return _strings(measures), _strings(dimensions)


def _filter_descriptors(filters: Any) -> list[dict[str, str]]:
    """Keep semantic filter shape, never values or arbitrary request content."""
    if not isinstance(filters, list):
        return []
    result = []
    for item in filters[:50]:
        if not isinstance(item, dict):
            continue
        member = sanitize_text(
            item.get("member") or item.get("code") or item.get("dimensionCode"),
            max_chars=255,
        ).strip()
        if not member:
            continue
        descriptor = {
            "member": member,
            "operator": sanitize_text(item.get("operator") or "", max_chars=40),
            "scope": sanitize_text(item.get("scope") or "both", max_chars=40),
        }
        if descriptor not in result:
            result.append(descriptor)
    return result


def _normalization_step(
    raw_context: dict[str, Any], normalized: dict[str, list[str]], *, fallback_used: bool
) -> list[dict[str, Any]]:
    before = {
        "measureCodes": _raw_strings(raw_context.get("measureCodes")),
        "dimensionCodes": _raw_strings(raw_context.get("dimensionCodes")),
        "factTables": _raw_strings(raw_context.get("factTables")),
    }
    if before == normalized and not fallback_used:
        return []
    reason = "去重并规范化指标、维度和事实表编码"
    if fallback_used:
        reason = "主匹配结果缺少语义编码，已从实际 DA 执行载荷补齐"
    return [{
        "sequenceNo": 1,
        "correctorCode": "TRACE_CONTEXT_FALLBACK" if fallback_used else "CODE_NORMALIZER",
        "status": "applied",
        "reason": reason,
        "evidence": {"source": "executed_result"},
        "beforeHash": _hash_json(before),
        "afterHash": _hash_json(normalized),
        "patch": {"selection": normalized},
        "confidence": 1.0,
        "elapsedMs": 0,
    }]


def build_semantic_contract(
    *,
    trace_id: str,
    source: str,
    question: str = "",
    query_mode: str = "",
    status: str = "",
    semantic_context: dict[str, Any] | None = None,
    result: dict[str, Any] | None = None,
    graph: dict[str, Any] | None = None,
    operation: str = "",
) -> dict[str, Any]:
    """Build a stable plan from the same context that was actually executed."""
    semantic_context = dict(semantic_context or {})
    result = result if isinstance(result, dict) else {}
    graph = graph or {}
    matched = result.get("matched") if isinstance(result.get("matched"), dict) else {}
    da_payload = result.get("daPayload") if isinstance(result.get("daPayload"), dict) else {}
    da_measures, da_dimensions = _configure_codes(da_payload)

    raw_measures = semantic_context.get("measureCodes") or []
    if matched.get("measureCode"):
        raw_measures = [*(_strings(raw_measures)), matched.get("measureCode")]
    raw_dimensions = [
        *(_strings(semantic_context.get("dimensionCodes"))),
        *(_strings(matched.get("dimensionCodes"))),
    ]
    raw_facts = [
        *(_strings(semantic_context.get("factTables"))),
        *(_strings(matched.get("factTables"))),
    ]
    fallback_used = False
    if not _strings(raw_measures) and da_measures:
        raw_measures = da_measures
        fallback_used = True
    if not _strings(raw_dimensions) and da_dimensions:
        raw_dimensions = da_dimensions
        fallback_used = True

    selection = {
        "measureCodes": _strings(raw_measures),
        "dimensionCodes": _strings(raw_dimensions),
        "factTables": _strings(raw_facts),
    }
    filters = _filter_descriptors(
        semantic_context.get("filters")
        or result.get("filters")
        or da_payload.get("filters")
    )
    validation = result.get("validation") if isinstance(result.get("validation"), dict) else {}
    validation_status = sanitize_text(validation.get("status") or "not_available", max_chars=40)
    execution_status = sanitize_text(status or ("succeeded" if result.get("ok") else "failed"), max_chars=40)
    analysis_spec = result.get("analysisSpec") if isinstance(result.get("analysisSpec"), dict) else {}
    analysis_spec_meta = {
        "version": sanitize_text(analysis_spec.get("specVersion") or "", max_chars=20),
        "hash": sanitize_text(result.get("analysisSpecHash") or "", max_chars=64),
        "kind": sanitize_text(analysis_spec.get("analysisKind") or "", max_chars=80),
        "status": sanitize_text(result.get("planStatus") or "", max_chars=40),
    }
    plan = {
        "version": SEMANTIC_PLAN_VERSION,
        "traceId": sanitize_text(trace_id, max_chars=100),
        "source": sanitize_text(source, max_chars=40),
        "operation": sanitize_text(operation, max_chars=80),
        "queryMode": sanitize_text(query_mode or result.get("queryMode") or "", max_chars=40),
        "selection": selection,
        "filters": filters,
        "algorithm": {
            "variant": sanitize_text(result.get("algorithmVariant") or "default", max_chars=100),
            "reason": sanitize_text(result.get("algorithmReason") or "按当前语义模型默认口径执行", max_chars=500),
            "operands": result.get("operandPlans") if isinstance(result.get("operandPlans"), list) else [],
        },
        # Store only the identity of the pre-execution spec.  Logical filter
        # values remain represented by the redacted descriptors above.
        "analysisSpec": analysis_spec_meta,
        "authorization": {
            "status": "not_evaluated",
            "policyIds": [],
            "note": "v1 记录权限计划字段；执行层统一强制校验尚未接入此观测合同。",
        },
        "execution": {
            "status": execution_status,
            "validationStatus": validation_status,
            "resultRowCount": result.get("resultRowCount"),
            "elapsedMs": result.get("elapsedMs"),
        },
        "versions": {
            "businessKgHash": sanitize_text(graph.get("sha256") or graph.get("businessKgHash"), max_chars=64),
            "businessKgFile": sanitize_text(graph.get("filename") or graph.get("businessKgFile"), max_chars=255),
            "ontologyVersion": sanitize_text(graph.get("ontologyVersion"), max_chars=100),
            "schemaSnapshotId": sanitize_text(graph.get("schemaSnapshotId"), max_chars=100),
        },
    }
    plan_hash = _hash_json(plan)
    explain = {
        "version": EXPLAIN_PLAN_VERSION,
        "traceId": plan["traceId"],
        "summary": {
            "question": sanitize_text(question, max_chars=1000),
            "queryMode": plan["queryMode"],
            "status": execution_status,
        },
        "semanticSelection": selection,
        "filterScopes": filters,
        "algorithm": plan["algorithm"],
        "analysisSpec": plan["analysisSpec"],
        "authorization": plan["authorization"],
        "versions": plan["versions"],
        "validation": {
            "status": validation_status,
            "warningCount": len(validation.get("warnings") or []),
        },
        "replay": {"traceId": plan["traceId"], "planHash": plan_hash},
    }
    corrections = _normalization_step(semantic_context, selection, fallback_used=fallback_used)
    return {
        "plan": plan,
        "planHash": plan_hash,
        "explainPlan": explain,
        "corrections": corrections,
        "eligibleForMemory": bool(
            execution_status in {"succeeded", "empty"}
            and validation_status not in {"failed", "error"}
            and selection["measureCodes"]
        ),
    }


def compatible_memory_status(event_name: str) -> str:
    """Map explicit user feedback to the safe memory state transition."""
    event = str(event_name or "").upper()
    if event == "RESULT_HELPFUL":
        return "ENABLED"
    if event in {"RESULT_UNHELPFUL", "RESULT_CORRECTION_SUBMITTED"}:
        return "DISABLED"
    return ""
