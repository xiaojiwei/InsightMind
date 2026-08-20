"""Optional bridge from reviewed feedback dictionary entries to retrieval."""

from __future__ import annotations

import os
from typing import Any


def feedback_dictionary_enabled() -> bool:
    return os.getenv("INSIGHTMIND_SEMANTIC_FEEDBACK_ENABLED", "false").strip().lower() in {
        "1", "true", "yes", "on",
    }


def load_enabled_dictionary_entries(graph_hash: str) -> list[dict[str, Any]]:
    """Only load explicitly reviewed entries; never infer aliases from whole questions."""
    if not feedback_dictionary_enabled():
        return []
    from kg_builder.feedback.store import store

    if not hasattr(store, "list_dictionary_entries"):
        return []
    result = store.list_dictionary_entries(
        status="ENABLED",
        business_kg_hash=graph_hash,
        limit=5000,
        offset=0,
    )
    return [item for item in result.get("items") or [] if isinstance(item, dict)]
