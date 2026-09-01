"""Thread-safe in-memory run and event store used by the initial runtime."""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
import queue
import threading
import time
from typing import Any, Iterator
import uuid

from .contracts import RequestContext, RunStatus, StreamEvent, StreamEventType


@dataclass
class AgentRun:
    context: RequestContext
    status: RunStatus = RunStatus.PENDING
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)
    cancel_event: threading.Event = field(default_factory=threading.Event)
    _events: deque[StreamEvent] = field(default_factory=lambda: deque(maxlen=2000))
    _queue: queue.Queue[StreamEvent] = field(default_factory=queue.Queue)
    _sequence: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock)

    def publish(self, event_type: StreamEventType, payload: dict[str, Any] | None = None) -> StreamEvent:
        with self._lock:
            self._sequence += 1
            event = StreamEvent(
                event_id=f"evt_{uuid.uuid4().hex}",
                sequence=self._sequence,
                event_type=event_type,
                run_id=self.context.run_id,
                trace_id=self.context.trace_id,
                timestamp=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                payload=dict(payload or {}),
            )
            self._events.append(event)
            self._queue.put(event)
            self.updated_at = time.time()
            return event

    def events_after(self, sequence: int = 0) -> list[StreamEvent]:
        with self._lock:
            return [event for event in self._events if event.sequence > sequence]

    def next_event(self, timeout: float = 0.4) -> StreamEvent | None:
        try:
            return self._queue.get(timeout=timeout)
        except queue.Empty:
            return None


class AgentRunStore:
    def __init__(self, ttl_seconds: int = 3600) -> None:
        self._ttl_seconds = ttl_seconds
        self._runs: dict[str, AgentRun] = {}
        self._lock = threading.Lock()

    def create(self, context: RequestContext) -> AgentRun:
        self.cleanup()
        run = AgentRun(context=context)
        with self._lock:
            self._runs[context.run_id] = run
        return run

    def get(self, run_id: str) -> AgentRun | None:
        with self._lock:
            return self._runs.get(run_id)

    def cleanup(self) -> None:
        cutoff = time.time() - self._ttl_seconds
        terminal = {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED}
        with self._lock:
            expired = [
                run_id for run_id, run in self._runs.items()
                if run.status in terminal and run.updated_at < cutoff
            ]
            for run_id in expired:
                self._runs.pop(run_id, None)

    def iter_events(self, run: AgentRun, after: int = 0) -> Iterator[StreamEvent]:
        for event in run.events_after(after):
            after = event.sequence
            yield event
        while True:
            event = run.next_event()
            if event is not None and event.sequence > after:
                after = event.sequence
                yield event
            if run.status in {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED}:
                return
