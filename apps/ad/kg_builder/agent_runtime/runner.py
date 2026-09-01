"""Initial AgentRunner for governed Insight workflows.

This runner intentionally uses fixed plans.  Plan-Execute will be added only
after the tool contracts, traces, and policy boundaries have proven stable.
"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import threading
from typing import Any, Callable
import uuid

from .context import bind_request_context
from .contracts import (
    AgentExecutionPolicy,
    AgentPlan,
    AgentPlanStep,
    AgentRequest,
    RequestContext,
    RunStatus,
    StreamEventType,
    ToolInvocation,
)
from .events import AgentRun, AgentRunStore
from .hooks import HookManager
from .registry import ToolExecutor, ToolRegistry
from .router import SmartIntentRouter
from .tools import (
    analysis_compile_tool,
    document_trace_tool,
    metric_diagnosis_tool,
    nlq_query_tool,
)


@dataclass(frozen=True)
class InsightRuntimeDependencies:
    data_agent_url: str
    ttl_path: str
    llm_config_factory: Callable[[], dict[str, Any]]
    semantic_mapping_service_factory: Callable[[], Any]


class InsightAgentRunner:
    def __init__(
        self,
        dependencies: InsightRuntimeDependencies,
        *,
        store: AgentRunStore | None = None,
        hooks: HookManager | None = None,
    ) -> None:
        self.store = store or AgentRunStore()
        self.hooks = hooks or HookManager()
        self.registry = ToolRegistry()
        self.registry.register(analysis_compile_tool(
            data_agent_url=dependencies.data_agent_url,
            ttl_path=dependencies.ttl_path,
            semantic_mapping_service_factory=dependencies.semantic_mapping_service_factory,
        ))
        self.registry.register(nlq_query_tool(
            data_agent_url=dependencies.data_agent_url,
            ttl_path=dependencies.ttl_path,
            semantic_mapping_service_factory=dependencies.semantic_mapping_service_factory,
        ))
        self.registry.register(metric_diagnosis_tool(
            data_agent_url=dependencies.data_agent_url,
            ttl_path=dependencies.ttl_path,
            llm_config_factory=dependencies.llm_config_factory,
            semantic_mapping_service_factory=dependencies.semantic_mapping_service_factory,
        ))
        self.registry.register(document_trace_tool(
            llm_config_factory=dependencies.llm_config_factory,
        ))
        self.executor = ToolExecutor(self.registry, self.hooks)

    def start(self, request: AgentRequest) -> AgentRun:
        question = str(request.question or "").strip()
        if not question:
            raise ValueError("question 不能为空")
        run_id = uuid.uuid4().hex
        trace_id = self._trace_id(run_id, request)
        context = RequestContext(
            trace_id=trace_id,
            run_id=run_id,
            conversation_id=request.conversation_id,
            user_id=request.user_id,
            tenant_id=request.tenant_id,
            permission_scope_hash=request.permission_scope_hash,
            semantic_token=request.semantic_token,
            graph_version=request.graph_version,
            execution_policy=request.execution_policy,
            locale=request.locale,
            timezone=request.timezone,
        )
        run = self.store.create(context)
        thread = threading.Thread(target=self._run, args=(run, request), daemon=True)
        thread.start()
        return run

    def cancel(self, run_id: str) -> AgentRun | None:
        run = self.store.get(run_id)
        if run is None:
            return None
        if run.status not in {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED}:
            run.cancel_event.set()
            run.publish(StreamEventType.STATUS, {"status": "cancelling"})
        return run

    @staticmethod
    def _trace_id(run_id: str, request: AgentRequest) -> str:
        supplied = str(request.context.get("traceId") or "").strip()
        if supplied:
            return supplied
        raw = f"{run_id}:{request.conversation_id}:{request.question}".encode("utf-8")
        return f"agent_{hashlib.sha256(raw).hexdigest()[:24]}"

    @staticmethod
    def _plan(request: AgentRequest) -> AgentPlan:
        context = dict(request.context)
        if request.analysis_mode:
            context["analysisMode"] = request.analysis_mode
        decision = SmartIntentRouter().decide(request.question, context)
        if decision.route == "document_trace":
            return AgentPlan(
                route="document_trace",
                reason_code=decision.reason_code,
                steps=(AgentPlanStep(
                    step_id="document_trace",
                    tool_name="insight.document_trace",
                    title="单据追踪分析",
                    arguments={"question": request.question, "context": context},
                    expected_evidence="document trace findings",
                ),),
            )
        if decision.route == "nlq_query":
            return AgentPlan(
                route="nlq_query",
                reason_code=decision.reason_code,
                steps=(AgentPlanStep(
                    step_id="nlq_query",
                    tool_name="nlq.query",
                    title="图谱问数",
                    arguments={"question": request.question, "context": context},
                    expected_evidence="governed AnalysisSpec and semantic query result",
                ),),
            )
        return AgentPlan(
            route="metric_diagnosis",
            reason_code=decision.reason_code,
            steps=(AgentPlanStep(
                step_id="metric_diagnosis",
                tool_name="insight.metric_diagnosis",
                title="指标波动归因",
                arguments={"question": request.question, "context": context},
                expected_evidence="semantic match and deterministic analysis parts",
            ),),
        )

    def _run(self, run: AgentRun, request: AgentRequest) -> None:
        plan = self._plan(request)
        policy = AgentExecutionPolicy.standard()
        with bind_request_context(run.context):
            try:
                run.status = RunStatus.RUNNING
                run.publish(StreamEventType.STATUS, {"status": "running"})
                run.publish(StreamEventType.ROUTE, {
                    "route": plan.route,
                    "reasonCode": plan.reason_code,
                    "confidence": 1.0,
                })
                run.publish(StreamEventType.PLAN, plan.to_dict())
                self.hooks.emit("agent.before_run", {
                    "runId": run.context.run_id,
                    "traceId": run.context.trace_id,
                    "plan": plan.to_dict(),
                })
                for step in plan.steps:
                    if run.cancel_event.is_set():
                        break
                    run.publish(StreamEventType.TOOL_CALL, {
                        "stepId": step.step_id,
                        "toolName": step.tool_name,
                        "arguments": step.arguments,
                    })
                    invocation = ToolInvocation(
                        request_context=run.context,
                        cancelled=run.cancel_event.is_set,
                        emit_legacy=lambda event: self._emit_legacy_event(run, event),
                    )
                    result = self.executor.execute(
                        step.tool_name, step.arguments, invocation, policy
                    )
                    run.publish(StreamEventType.TOOL_RESULT, {
                        "stepId": step.step_id,
                        "toolName": step.tool_name,
                        "result": result,
                    })
                    if step.tool_name == "nlq.query":
                        run.publish(StreamEventType.RESULT, {"result": result})
                if run.cancel_event.is_set():
                    run.publish(StreamEventType.DONE, {"status": "cancelled"})
                    run.status = RunStatus.CANCELLED
                else:
                    run.publish(StreamEventType.DONE, {"status": "succeeded"})
                    run.status = RunStatus.SUCCEEDED
                self.hooks.emit("agent.after_run", {
                    "runId": run.context.run_id,
                    "traceId": run.context.trace_id,
                    "status": run.status.value,
                })
            except Exception as exc:
                run.publish(StreamEventType.ERROR, {
                    "code": "AGENT_RUN_FAILED",
                    "message": str(exc),
                })
                run.publish(StreamEventType.DONE, {"status": "failed"})
                run.status = RunStatus.FAILED
                self.hooks.emit("agent.error", {
                    "runId": run.context.run_id,
                    "traceId": run.context.trace_id,
                    "error": str(exc),
                })

    @staticmethod
    def _emit_legacy_event(run: AgentRun, event: dict[str, Any]) -> None:
        raw = dict(event or {})
        if "log" in raw or "step" in raw:
            event_type = StreamEventType.STATUS
        elif "insight_text" in raw:
            event_type = StreamEventType.RESULT
        elif "part" in raw or "report" in raw:
            event_type = StreamEventType.RESULT
        else:
            event_type = StreamEventType.TRACE
        run.publish(event_type, {"legacy": raw})
