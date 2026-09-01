"""Public contracts for InsightMind's small, governed agent runtime.

The runtime orchestrates existing semantic and insight capabilities.  It does
not grant a model direct access to SQL, datasource connections, or DA payloads.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable


class RunStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"
    CANCELLED = "cancelled"


class StreamEventType(str, Enum):
    STATUS = "status"
    ROUTE = "route"
    PLAN = "plan"
    TOOL_CALL = "tool_call"
    TOOL_RESULT = "tool_result"
    SQL = "sql"
    CHART = "chart"
    WARNING = "warning"
    RESULT = "result"
    TRACE = "trace"
    DONE = "done"
    ERROR = "error"


@dataclass(frozen=True)
class RequestContext:
    """Request-scoped identity and governance data.

    The fields are deliberately logical identifiers and hashes.  Credentials,
    raw authorization headers, and physical database settings must not be
    retained in this object or emitted to a client trace.
    """

    trace_id: str
    run_id: str
    conversation_id: str = ""
    user_id: str = ""
    tenant_id: str = ""
    permission_scope_hash: str = ""
    semantic_token: str = ""
    graph_version: str = ""
    execution_policy: str = "default"
    locale: str = "zh-CN"
    timezone: str = "Asia/Shanghai"

    def to_dict(self) -> dict[str, str]:
        return {
            "traceId": self.trace_id,
            "runId": self.run_id,
            "conversationId": self.conversation_id,
            "userId": self.user_id,
            "tenantId": self.tenant_id,
            "permissionScopeHash": self.permission_scope_hash,
            "semanticToken": self.semantic_token,
            "graphVersion": self.graph_version,
            "executionPolicy": self.execution_policy,
            "locale": self.locale,
            "timezone": self.timezone,
        }


@dataclass(frozen=True)
class AgentRequest:
    question: str
    context: dict[str, Any] = field(default_factory=dict)
    analysis_mode: str = ""
    conversation_id: str = ""
    user_id: str = ""
    tenant_id: str = ""
    permission_scope_hash: str = ""
    semantic_token: str = ""
    graph_version: str = ""
    execution_policy: str = "default"
    locale: str = "zh-CN"
    timezone: str = "Asia/Shanghai"


@dataclass(frozen=True)
class AgentPlanStep:
    step_id: str
    tool_name: str
    title: str
    arguments: dict[str, Any] = field(default_factory=dict)
    depends_on: tuple[str, ...] = ()
    expected_evidence: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "stepId": self.step_id,
            "toolName": self.tool_name,
            "title": self.title,
            "arguments": self.arguments,
            "dependsOn": list(self.depends_on),
            "expectedEvidence": self.expected_evidence,
        }


@dataclass(frozen=True)
class AgentPlan:
    route: str
    reason_code: str
    steps: tuple[AgentPlanStep, ...]

    def to_dict(self) -> dict[str, Any]:
        return {
            "route": self.route,
            "reasonCode": self.reason_code,
            "steps": [step.to_dict() for step in self.steps],
        }


@dataclass(frozen=True)
class StreamEvent:
    event_id: str
    sequence: int
    event_type: StreamEventType
    run_id: str
    trace_id: str
    timestamp: str
    payload: dict[str, Any] = field(default_factory=dict)
    schema_version: str = "1.0"

    def to_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": self.schema_version,
            "eventId": self.event_id,
            "seq": self.sequence,
            "type": self.event_type.value,
            "runId": self.run_id,
            "traceId": self.trace_id,
            "timestamp": self.timestamp,
            "payload": self.payload,
        }


@dataclass(frozen=True)
class AgentExecutionPolicy:
    allowed_tools: frozenset[str]
    max_steps: int = 6
    max_tool_calls: int = 8

    @classmethod
    def insight_default(cls) -> "AgentExecutionPolicy":
        return cls(frozenset({"insight.metric_diagnosis", "insight.document_trace"}))

    @classmethod
    def standard(cls) -> "AgentExecutionPolicy":
        return cls(frozenset({
            "analysis.compile",
            "nlq.query",
            "insight.metric_diagnosis",
            "insight.document_trace",
        }))


ToolHandler = Callable[[dict[str, Any], "ToolInvocation"], dict[str, Any]]


@dataclass(frozen=True)
class ToolDefinition:
    name: str
    description: str
    handler: ToolHandler
    timeout_seconds: int = 120
    retryable: bool = False
    required_permission: str = "default"


@dataclass
class ToolInvocation:
    request_context: RequestContext
    cancelled: Callable[[], bool]
    emit_legacy: Callable[[dict[str, Any]], None]
