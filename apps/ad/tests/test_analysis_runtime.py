from __future__ import annotations

import pytest

from kg_builder.analysis_runtime import (
    AnalysisExecutionBlocked,
    AnalysisExecutor,
    PayloadPolicyError,
    PlanStatus,
    compile_legacy_plan,
)


def _ready_compilation(payload: dict | None = None):
    return compile_legacy_plan(
        question="查询网络销售金额",
        query_mode="aggregate",
        versions={"businessKgHash": "kg-v1"},
        plan={
            "ok": True,
            "intent": {
                "queryMode": "aggregate",
                "filters": [{
                    "type": "timeRange",
                    "dimensionCode": "DIM_sales_date",
                    "start": "2026-08-01",
                    "end": "2026-08-20",
                }],
            },
            "matched": {
                "measureCode": "MEAS_web_sales_amount",
                "dimensionCodes": ["DIM_sales_date"],
                "factTables": ["web_sales"],
                "executionTableCandidates": ["web_sales"],
            },
            "diagnostics": {},
            "semanticMapping": {"decision": "accept"},
            "daPayload": payload or {
                "configureList": [
                    {"code": "MEAS_web_sales_amount"},
                    {"code": "DIM_sales_date", "order": {"sortType": 1}},
                ],
                "filterList": [],
                "pageSize": 100,
                "pageNum": 1,
            },
        },
    )


def test_analysis_spec_contains_only_logical_semantics() -> None:
    compiled = _ready_compilation()
    public = compiled.result.to_dict()

    assert compiled.status is PlanStatus.READY
    assert public["analysisSpec"]["semantic"]["measureCodes"] == [
        "MEAS_web_sales_amount"
    ]
    assert public["analysisSpec"]["versions"]["businessKgHash"] == "kg-v1"
    assert public["specHash"] == compiled.result.spec.spec_hash
    assert "daPayload" not in public["analysisSpec"]
    assert "configureList" not in str(public["analysisSpec"])


def test_clarification_is_structured_and_not_executable() -> None:
    compiled = compile_legacy_plan(
        question="查询利润",
        query_mode="aggregate",
        plan={
            "ok": False,
            "action": "clarify",
            "needsClarification": True,
            "diagnosticCode": "METRIC_AMBIGUOUS",
            "clarification": "指标匹配不唯一，请明确要查哪个指标",
            "intent": {},
            "matched": {"measureCode": "MEAS_candidate"},
            "diagnostics": {
                "measureCandidates": [
                    {"code": "MEAS_a", "name": "利润A"},
                    {"code": "MEAS_b", "name": "利润B"},
                ]
            },
            "daPayload": None,
        },
    )

    clarification = compiled.result.clarification
    assert compiled.status is PlanStatus.REQUIRES_INPUT
    assert clarification is not None
    assert clarification.required_slots == ("measure",)
    assert len(clarification.candidates) == 2
    assert clarification.resume_token

    with pytest.raises(AnalysisExecutionBlocked):
        AnalysisExecutor(lambda _payload: {}).execute(compiled)


def test_clarification_slots_follow_candidate_evidence_for_generic_confidence_code() -> None:
    compiled = compile_legacy_plan(
        question="查询订单量",
        query_mode="aggregate",
        plan={
            "ok": False,
            "action": "clarify",
            "needsClarification": True,
            "diagnosticCode": "SEMANTIC_MEDIUM_CONFIDENCE",
            "clarification": "请确认指标",
            "intent": {},
            "matched": {"measureCode": "MEAS_candidate"},
            "diagnostics": {
                "measureCandidates": [
                    {"code": "MEAS_a", "name": "订单量A"},
                    {"code": "MEAS_b", "name": "订单量B"},
                ]
            },
            "daPayload": None,
        },
    )

    clarification = compiled.result.clarification
    assert clarification is not None
    assert clarification.required_slots == ("measure",)


def test_clarification_hides_candidates_for_another_required_slot() -> None:
    compiled = compile_legacy_plan(
        question="按省份查看订单量",
        query_mode="aggregate",
        plan={
            "ok": False,
            "action": "clarify",
            "needsClarification": True,
            "diagnosticCode": "DIMENSION_VALUE_UNRESOLVED",
            "clarification": "请明确维度和值",
            "intent": {},
            "matched": {},
            "diagnostics": {
                "measureCandidates": [
                    {"code": "MEAS_a", "name": "已确认指标"},
                ]
            },
            "daPayload": None,
        },
    )

    clarification = compiled.result.clarification
    assert clarification is not None
    assert clarification.required_slots == ("dimension", "value")
    assert clarification.candidates == ()


def test_executor_adds_trace_and_runs_only_governed_semantic_payload() -> None:
    captured = {}

    def execute(payload):
        captured.update(payload)
        return {"ok": True, "data": {"cellList": []}}

    result = AnalysisExecutor(execute).execute(
        _ready_compilation(), trace_id="trace-1"
    )

    assert result.tool_name == "semantic.query"
    assert captured["traceId"] == "trace-1"
    assert captured["configureList"][0]["code"] == "MEAS_web_sales_amount"


@pytest.mark.parametrize(
    "payload",
    [
        {
            "configureList": [{"code": "MEAS_x", "expression": "[MEAS_secret]"}],
            "filterList": [],
            "pageSize": 100,
            "pageNum": 1,
        },
        {
            "configureList": [{"code": "MEAS_x"}],
            "filterList": [],
            "pageSize": 100,
            "pageNum": 1,
            "username": "another-user",
        },
        {
            "configureList": [{"code": "MEAS_x"}],
            "filterList": [],
            "pageSize": 10001,
            "pageNum": 1,
        },
    ],
)
def test_executor_rejects_physical_or_unbounded_payload(payload: dict) -> None:
    with pytest.raises(PayloadPolicyError):
        AnalysisExecutor(lambda _payload: {}).execute(_ready_compilation(payload))
