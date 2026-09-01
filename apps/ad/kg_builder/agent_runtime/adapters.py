"""Compatibility adapters for existing Insight web clients."""

from __future__ import annotations

import json

from .contracts import StreamEvent


def legacy_insight_sse_payload(event: StreamEvent) -> str | None:
    """Convert a V1 runtime event into the existing `/api/insight/*/log` body."""
    legacy = event.payload.get("legacy")
    if isinstance(legacy, dict):
        return json.dumps(legacy, ensure_ascii=False, default=str)
    if event.event_type.value == "error":
        return json.dumps({"log": f"✗ {event.payload.get('message') or 'Insight 分析失败'}"}, ensure_ascii=False)
    if event.event_type.value == "done":
        return "__CANCELLED__" if event.payload.get("status") == "cancelled" else "__DONE__"
    return None
