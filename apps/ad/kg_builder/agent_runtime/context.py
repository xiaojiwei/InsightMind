"""Request-local context propagation for agent runs."""

from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from typing import Iterator

from .contracts import RequestContext


_request_context: ContextVar[RequestContext | None] = ContextVar(
    "insightmind_agent_request_context", default=None
)


@contextmanager
def bind_request_context(context: RequestContext) -> Iterator[RequestContext]:
    token = _request_context.set(context)
    try:
        yield context
    finally:
        _request_context.reset(token)


def current_request_context() -> RequestContext | None:
    return _request_context.get()
