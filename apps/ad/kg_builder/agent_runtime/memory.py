"""Conversation memory interfaces with explicit identity scoping."""

from __future__ import annotations

from abc import ABC, abstractmethod
from copy import deepcopy
import threading
from typing import Any

from .contracts import RequestContext


class SessionMemory(ABC):
    @abstractmethod
    def load(self, context: RequestContext) -> dict[str, Any]:
        raise NotImplementedError

    @abstractmethod
    def save(self, context: RequestContext, values: dict[str, Any]) -> None:
        raise NotImplementedError


class InMemorySessionMemory(SessionMemory):
    """Development implementation; production can replace this with FeedbackStore."""

    def __init__(self) -> None:
        self._values: dict[tuple[str, str, str], dict[str, Any]] = {}
        self._lock = threading.Lock()

    @staticmethod
    def _key(context: RequestContext) -> tuple[str, str, str]:
        return (context.tenant_id, context.user_id, context.conversation_id)

    def load(self, context: RequestContext) -> dict[str, Any]:
        with self._lock:
            return deepcopy(self._values.get(self._key(context), {}))

    def save(self, context: RequestContext, values: dict[str, Any]) -> None:
        with self._lock:
            self._values[self._key(context)] = deepcopy(values)
