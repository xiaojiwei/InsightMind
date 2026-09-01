"""Policy gate and deterministic execution adapter for compiled analysis plans."""

from __future__ import annotations

from copy import deepcopy
from typing import Any

from .contracts import CompiledAnalysis, ExecuteCallable, ExecutionPolicy, ExecutionResult


class AnalysisExecutionBlocked(RuntimeError):
    pass


class PayloadPolicyError(ValueError):
    pass


class AnalysisExecutor:
    TOOL_NAME = "semantic.query"
    _ALLOWED_TOP_LEVEL = {
        "configureList",
        "filterList",
        "pageSize",
        "pageNum",
        "measureDetail",
        "traceId",
    }
    _FORBIDDEN_KEYS = {
        "sql",
        "reviewsql",
        "expression",
        "username",
        "spaceid",
        "connection",
        "dataconnection",
        "databaseinfo",
        "sourcetype",
        "onlysql",
        "directquery",
        "downfile",
        "tablename",
        "schemaname",
        "columnname",
        "jdbcurl",
        "password",
    }

    def __init__(
        self,
        execute: ExecuteCallable,
        policy: ExecutionPolicy | None = None,
    ) -> None:
        self._execute = execute
        self.policy = policy or ExecutionPolicy()

    def execute(self, compiled: CompiledAnalysis, *, trace_id: str = "") -> ExecutionResult:
        if not compiled.ready:
            raise AnalysisExecutionBlocked(
                f"analysis plan is {compiled.status.value}; deterministic execution is not allowed"
            )
        if self.TOOL_NAME not in self.policy.allowed_tools:
            raise AnalysisExecutionBlocked(f"tool {self.TOOL_NAME} is not allowed by policy")
        if not isinstance(compiled.execution_payload, dict):
            raise AnalysisExecutionBlocked("ready analysis plan has no semantic execution payload")

        payload = deepcopy(compiled.execution_payload)
        if trace_id:
            payload["traceId"] = trace_id
        self._validate_payload(payload)
        result = self._execute(payload)
        if not isinstance(result, dict):
            raise TypeError("semantic query executor must return a dictionary")
        return ExecutionResult(self.TOOL_NAME, payload, result)

    def _validate_payload(self, payload: dict[str, Any]) -> None:
        unknown = set(payload) - self._ALLOWED_TOP_LEVEL
        if unknown:
            raise PayloadPolicyError(
                f"semantic execution payload contains unsupported fields: {', '.join(sorted(unknown))}"
            )

        page_size = self._integer(payload.get("pageSize"), default=100)
        if page_size < 1 or page_size > self.policy.max_rows_per_query:
            raise PayloadPolicyError(
                f"pageSize must be between 1 and {self.policy.max_rows_per_query}"
            )
        page_num = self._integer(payload.get("pageNum"), default=1)
        if page_num < 1:
            raise PayloadPolicyError("pageNum must be at least 1")

        configure = payload.get("configureList")
        if not isinstance(configure, list) or not configure:
            raise PayloadPolicyError("configureList must contain at least one semantic member")
        if len(configure) > self.policy.max_configure_items:
            raise PayloadPolicyError("configureList exceeds the execution policy limit")
        for item in configure:
            if not isinstance(item, dict):
                raise PayloadPolicyError("configureList items must be objects")
            code = str(item.get("code") or "")
            if not code.startswith(("MEAS_", "DIM_")):
                raise PayloadPolicyError(f"unsupported semantic member code: {code}")

        filters = payload.get("filterList") or []
        if not isinstance(filters, list):
            raise PayloadPolicyError("filterList must be a list")
        if len(filters) > self.policy.max_filters:
            raise PayloadPolicyError("filterList exceeds the execution policy limit")

        self._walk(payload)

    def _walk(self, value: Any, *, key: str = "") -> None:
        normalized_key = key.replace("_", "").lower()
        if normalized_key in self._FORBIDDEN_KEYS:
            raise PayloadPolicyError(f"field {key} is forbidden in semantic execution")
        if isinstance(value, dict):
            for child_key, child_value in value.items():
                self._walk(child_value, key=str(child_key))
            return
        if isinstance(value, list):
            if key == "dataList" and len(value) > self.policy.max_values_per_filter:
                raise PayloadPolicyError("filter value list exceeds the execution policy limit")
            for item in value:
                self._walk(item, key=key)
            return
        if isinstance(value, str) and len(value) > self.policy.max_string_length:
            raise PayloadPolicyError(f"field {key or '<value>'} exceeds the string length limit")

    @staticmethod
    def _integer(value: Any, *, default: int) -> int:
        try:
            return int(value if value is not None else default)
        except (TypeError, ValueError) as exc:
            raise PayloadPolicyError("pagination values must be integers") from exc
