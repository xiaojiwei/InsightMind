"""Small lifecycle hook manager for audit and observability integrations."""

from __future__ import annotations

from collections import defaultdict
from typing import Any, Callable


Hook = Callable[[dict[str, Any]], None]


class HookManager:
    def __init__(self) -> None:
        self._hooks: dict[str, list[Hook]] = defaultdict(list)

    def register(self, event_name: str, hook: Hook) -> None:
        self._hooks[event_name].append(hook)

    def emit(self, event_name: str, payload: dict[str, Any]) -> None:
        for hook in tuple(self._hooks.get(event_name, ())):
            try:
                hook(dict(payload))
            except Exception:
                # Hooks are observational.  A failed telemetry sink must not
                # alter governed query execution.
                continue
