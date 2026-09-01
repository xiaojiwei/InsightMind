from __future__ import annotations

import time

import pytest
from fastapi.testclient import TestClient

from kg_builder.agent_runtime import (
    AgentExecutionPolicy,
    AgentRequest,
    InsightAgentRunner,
    InsightRuntimeDependencies,
    RequestContext,
    RunStatus,
    StreamEvent,
    StreamEventType,
    ToolDefinition,
    ToolExecutionBlocked,
    ToolExecutor,
    ToolInvocation,
    ToolRegistry,
    bind_request_context,
    current_request_context,
    legacy_insight_sse_payload,
)


def _context() -> RequestContext:
    return RequestContext(trace_id="trace-1", run_id="run-1", conversation_id="conv-1")


def test_tool_executor_enforces_policy_and_keeps_request_context() -> None:
    registry = ToolRegistry()
    seen = {}

    def handler(arguments, invocation):
        seen["arguments"] = arguments
        seen["context"] = current_request_context()
        return {"ok": True}

    registry.register(ToolDefinition("test.tool", "test", handler))
    executor = ToolExecutor(registry)
    invocation = ToolInvocation(_context(), lambda: False, lambda _event: None)
    policy = AgentExecutionPolicy(frozenset({"test.tool"}))

    with bind_request_context(invocation.request_context):
        result = executor.execute("test.tool", {"value": 1}, invocation, policy)

    assert result == {"ok": True}
    assert seen["arguments"] == {"value": 1}
    assert seen["context"] == invocation.request_context
    assert current_request_context() is None


def test_tool_executor_blocks_tools_outside_policy() -> None:
    registry = ToolRegistry()
    registry.register(ToolDefinition("test.tool", "test", lambda _args, _invocation: {}))
    invocation = ToolInvocation(_context(), lambda: False, lambda _event: None)

    with pytest.raises(ToolExecutionBlocked):
        ToolExecutor(registry).execute(
            "test.tool", {}, invocation, AgentExecutionPolicy(frozenset())
        )


def test_legacy_adapter_keeps_existing_insight_payloads() -> None:
    event = StreamEvent(
        event_id="evt-1",
        sequence=1,
        event_type=StreamEventType.RESULT,
        run_id="run-1",
        trace_id="trace-1",
        timestamp="2026-08-24T00:00:00Z",
        payload={"legacy": {"part": 1, "result": {"value": 3}}},
    )
    done = StreamEvent(
        event_id="evt-2",
        sequence=2,
        event_type=StreamEventType.DONE,
        run_id="run-1",
        trace_id="trace-1",
        timestamp="2026-08-24T00:00:01Z",
        payload={"status": "succeeded"},
    )

    assert '"part": 1' in (legacy_insight_sse_payload(event) or "")
    assert legacy_insight_sse_payload(done) == "__DONE__"


def test_insight_runner_emits_governed_plan_and_tool_events() -> None:
    runner = InsightAgentRunner(InsightRuntimeDependencies(
        data_agent_url="http://unused",
        ttl_path="unused.ttl",
        llm_config_factory=lambda: {},
        semantic_mapping_service_factory=lambda: None,
    ))
    seen_contexts = []

    def handler(arguments, invocation):
        seen_contexts.append(current_request_context())
        invocation.emit_legacy({"step": "kg_match", "result": {"meas_code": "MEAS_x"}})
        return {"question": arguments["question"], "ok": True}

    runner.registry._tools["insight.metric_diagnosis"] = ToolDefinition(
        "insight.metric_diagnosis", "test metric workflow", handler
    )
    run = runner.start(AgentRequest(question="为什么销售额下降", conversation_id="conv-1"))

    deadline = time.monotonic() + 1.0
    while run.status not in {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED}:
        assert time.monotonic() < deadline
        time.sleep(0.01)

    events = run.events_after()
    event_types = [event.event_type for event in events]
    assert run.status is RunStatus.SUCCEEDED
    assert event_types[:3] == [StreamEventType.STATUS, StreamEventType.ROUTE, StreamEventType.PLAN]
    assert StreamEventType.TOOL_CALL in event_types
    assert StreamEventType.TOOL_RESULT in event_types
    assert event_types[-1] is StreamEventType.DONE
    assert any(event.payload.get("legacy", {}).get("step") == "kg_match" for event in events)
    assert seen_contexts == [run.context]


def test_document_trace_is_a_fixed_governed_route() -> None:
    plan = InsightAgentRunner._plan(AgentRequest(
        question="追踪异常订单",
        analysis_mode="document_trace",
    ))

    assert plan.route == "document_trace"
    assert plan.steps[0].tool_name == "insight.document_trace"


@pytest.mark.parametrize(
    ("question", "expected_route"),
    [
        ("查询本月销售额", "nlq_query"),
        ("为什么本月销售额下降", "metric_diagnosis"),
        ("订单号: A1001", "nlq_query"),
    ],
)
def test_backend_router_preserves_smart_insight_intent_boundaries(question, expected_route) -> None:
    from kg_builder.agent_runtime import SmartIntentRouter

    assert SmartIntentRouter().decide(question).route == expected_route


def test_agent_run_api_and_legacy_insight_sse_share_the_same_runner(monkeypatch) -> None:
    import web_app

    runner = InsightAgentRunner(InsightRuntimeDependencies(
        data_agent_url="http://unused",
        ttl_path="unused.ttl",
        llm_config_factory=lambda: {},
        semantic_mapping_service_factory=lambda: None,
    ))

    def handler(_arguments, invocation):
        invocation.emit_legacy({"step": "kg_match", "result": {"meas_code": "MEAS_x"}})
        invocation.emit_legacy({"insight_text": "已完成"})
        return {"ok": True}

    runner.registry._tools["insight.metric_diagnosis"] = ToolDefinition(
        "insight.metric_diagnosis", "test metric workflow", handler
    )
    monkeypatch.setattr(web_app, "_insight_agent_runner", runner)
    client = TestClient(web_app.app)

    route = client.post("/api/agent/route", json={"question": "为什么销售额下降"})
    assert route.status_code == 200
    assert route.json()["decision"]["route"] == "metric_diagnosis"

    created = client.post("/api/agent/runs", json={"question": "为什么销售额下降"})
    assert created.status_code == 200
    run_id = created.json()["runId"]
    events = client.get(f"/api/agent/runs/{run_id}/events")
    assert events.status_code == 200
    assert '"type": "route"' in events.text
    assert '"type": "tool_call"' in events.text
    assert '"type": "done"' in events.text

    legacy_start = client.post("/api/insight/start", json={"question": "为什么销售额下降"})
    assert legacy_start.status_code == 200
    task_id = legacy_start.json()["taskId"]
    legacy_events = client.get(f"/api/insight/{task_id}/log")
    assert legacy_events.status_code == 200
    assert '"step": "kg_match"' in legacy_events.text
    assert '"insight_text": "已完成"' in legacy_events.text
    assert "__DONE__" in legacy_events.text
