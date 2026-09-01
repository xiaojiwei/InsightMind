"""Compile the current NLQ plan shape into AnalysisSpec V1."""

from __future__ import annotations

from copy import deepcopy
import hashlib
import json
from typing import Any

from .contracts import (
    AnalysisSpecV1,
    ClarificationSpec,
    CompiledAnalysis,
    PlanResult,
    PlanStatus,
    SemanticSelection,
)


def _strings(values: Any, *, limit: int = 50) -> tuple[str, ...]:
    if values is None:
        return ()
    if not isinstance(values, (list, tuple, set)):
        values = [values]
    result: list[str] = []
    for value in values:
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)
        if len(result) >= limit:
            break
    return tuple(result)


def _analysis_kind(query_mode: str, intent: dict[str, Any]) -> str:
    explicit = str(intent.get("analysisKind") or intent.get("analysisType") or "").strip()
    if explicit:
        return explicit
    return {
        "aggregate": "metric_query",
        "detail": "detail_query",
        "analyze_detail": "detail_analysis",
        "explain": "semantic_explanation",
    }.get(query_mode, "metric_query")


def _time_range(filters: tuple[dict[str, Any], ...]) -> dict[str, Any]:
    for item in filters:
        if str(item.get("type") or "") != "timeRange":
            continue
        return {
            "dimensionCode": str(item.get("dimensionCode") or ""),
            "dimensionName": str(item.get("dimensionName") or ""),
            "label": str(item.get("label") or ""),
            "start": str(item.get("start") or ""),
            "end": str(item.get("end") or ""),
        }
    return {}


def _required_slots(
    diagnostic_code: str,
    diagnostics: dict[str, Any] | None = None,
) -> tuple[str, ...]:
    code = diagnostic_code.upper()
    diagnostics = diagnostics or {}
    if "DIMENSION_VALUE" in code:
        return ("dimension", "value")
    if "DIMENSION" in code:
        return ("dimension",)
    if "METRIC" in code or code.startswith("LLM_"):
        return ("measure",)
    if "TABLE" in code or "FACT" in code:
        return ("factTableScope",)
    # Some planner diagnostics describe confidence rather than the semantic
    # object that needs confirmation. Prefer the concrete candidate evidence
    # so the client can ask for the right slot instead of a generic intent.
    if diagnostics.get("measureCandidates"):
        return ("measure",)
    if diagnostics.get("dimensionCandidates"):
        return ("dimension",)
    if diagnostics.get("uncertainValueBindings") or diagnostics.get("unsafeValueBindings"):
        return ("dimension", "value")
    if diagnostics.get("invalidInheritedFilters"):
        return ("filter",)
    return ("queryIntent",)


def _clarification_candidates(
    diagnostics: dict[str, Any],
    required_slots: tuple[str, ...],
) -> tuple[dict[str, Any], ...]:
    result: list[dict[str, Any]] = []
    allowed_keys: list[str] = []
    if "measure" in required_slots:
        allowed_keys.append("measureCandidates")
    if "dimension" in required_slots:
        allowed_keys.append("dimensionCandidates")
    if "value" in required_slots:
        allowed_keys.extend(("uncertainValueBindings", "unsafeValueBindings"))
    if "filter" in required_slots:
        allowed_keys.append("invalidInheritedFilters")
    for key in allowed_keys:
        values = diagnostics.get(key)
        if not isinstance(values, list):
            continue
        for value in values:
            if isinstance(value, dict):
                item = {"candidateType": key, **deepcopy(value)}
            else:
                item = {"candidateType": key, "value": value}
            result.append(item)
            if len(result) >= 20:
                return tuple(result)
    return tuple(result)


def _resume_token(spec: AnalysisSpecV1, diagnostic_code: str) -> str:
    raw = json.dumps(
        {"specHash": spec.spec_hash, "diagnosticCode": diagnostic_code},
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:32]


def compile_legacy_plan(
    *,
    question: str,
    query_mode: str,
    plan: dict[str, Any],
    versions: dict[str, str] | None = None,
) -> CompiledAnalysis:
    """Adapt the proven NLQ planner without leaking its physical DA payload."""

    plan = dict(plan or {})
    intent = plan.get("intent") if isinstance(plan.get("intent"), dict) else {}
    matched = plan.get("matched") if isinstance(plan.get("matched"), dict) else {}
    diagnostics = plan.get("diagnostics") if isinstance(plan.get("diagnostics"), dict) else {}
    raw_filters = intent.get("filters") if isinstance(intent.get("filters"), list) else []
    filters = tuple(deepcopy(item) for item in raw_filters if isinstance(item, dict))

    measure_codes = list(_strings(matched.get("measureCodes")))
    measure_code = str(matched.get("measureCode") or "").strip()
    if measure_code and measure_code not in measure_codes:
        measure_codes.insert(0, measure_code)

    selection = SemanticSelection(
        measure_codes=tuple(measure_codes),
        dimension_codes=_strings(matched.get("dimensionCodes")),
        fact_tables=_strings(matched.get("factTables")),
        execution_table_candidates=_strings(matched.get("executionTableCandidates")),
    )
    spec = AnalysisSpecV1(
        question=question,
        query_mode=query_mode,
        analysis_kind=_analysis_kind(query_mode, intent),
        selection=selection,
        filters=filters,
        time_range=_time_range(filters),
        semantic_mapping=deepcopy(plan.get("semanticMapping") or {}),
        output={
            "table": query_mode != "explain",
            "chart": query_mode == "aggregate",
            "conclusion": query_mode in {"analyze_detail", "explain"},
            "evidence": True,
        },
        versions=dict(versions or {}),
    )

    diagnostic_code = str(plan.get("diagnosticCode") or "")
    action = str(plan.get("action") or "")
    needs_clarification = bool(plan.get("needsClarification") or action == "clarify")
    if plan.get("ok"):
        status = PlanStatus.READY
    elif needs_clarification:
        status = PlanStatus.REQUIRES_INPUT
    else:
        status = PlanStatus.REJECTED

    clarification = None
    if status is PlanStatus.REQUIRES_INPUT:
        required_slots = _required_slots(diagnostic_code, diagnostics)
        clarification = ClarificationSpec(
            reason_code=diagnostic_code or "ANALYSIS_INPUT_REQUIRED",
            message=str(plan.get("clarification") or "需要补充分析条件"),
            required_slots=required_slots,
            candidates=_clarification_candidates(diagnostics, required_slots),
            resume_token=_resume_token(spec, diagnostic_code),
        )

    return CompiledAnalysis(
        result=PlanResult(
            status=status,
            spec=spec,
            diagnostic_code=diagnostic_code,
            clarification=clarification,
        ),
        legacy_plan=plan,
        execution_payload=deepcopy(plan.get("daPayload")) if isinstance(plan.get("daPayload"), dict) else None,
    )
