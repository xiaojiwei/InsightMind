"""Typed tool registration and policy-enforced execution."""

from __future__ import annotations

from typing import Any

from .contracts import AgentExecutionPolicy, ToolDefinition, ToolInvocation
from .hooks import HookManager


class ToolNotFoundError(KeyError):
    pass


class ToolExecutionBlocked(PermissionError):
    pass


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: dict[str, ToolDefinition] = {}

    def register(self, tool: ToolDefinition) -> None:
        if tool.name in self._tools:
            raise ValueError(f"tool already registered: {tool.name}")
        self._tools[tool.name] = tool

    def get(self, tool_name: str) -> ToolDefinition:
        try:
            return self._tools[tool_name]
        except KeyError as exc:
            raise ToolNotFoundError(tool_name) from exc

    def names(self) -> tuple[str, ...]:
        return tuple(sorted(self._tools))


class ToolExecutor:
    def __init__(self, registry: ToolRegistry, hooks: HookManager | None = None) -> None:
        self.registry = registry
        self.hooks = hooks or HookManager()

    def execute(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        invocation: ToolInvocation,
        policy: AgentExecutionPolicy,
    ) -> dict[str, Any]:
        if tool_name not in policy.allowed_tools:
            raise ToolExecutionBlocked(f"tool is not allowed by policy: {tool_name}")
        if invocation.cancelled():
            raise ToolExecutionBlocked("agent run was cancelled before tool execution")
        tool = self.registry.get(tool_name)
        payload = {
            "toolName": tool_name,
            "arguments": dict(arguments),
            "traceId": invocation.request_context.trace_id,
            "runId": invocation.request_context.run_id,
        }
        self.hooks.emit("tool.before_execute", payload)
        try:
            result = tool.handler(dict(arguments), invocation)
            if not isinstance(result, dict):
                raise TypeError(f"tool {tool_name} must return a dictionary")
            self.hooks.emit("tool.after_execute", {**payload, "result": result})
            return result
        except Exception as exc:
            self.hooks.emit("tool.error", {**payload, "error": str(exc)})
            raise
