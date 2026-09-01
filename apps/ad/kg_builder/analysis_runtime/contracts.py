"""Stable contracts between semantic compilation and deterministic execution.

The public AnalysisSpec deliberately contains only logical business semantics.
The physical DA payload is kept on ``CompiledAnalysis`` as an internal,
ephemeral compatibility artifact and is never included in ``AnalysisSpec`` or
its hash.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
import hashlib
import json
from typing import Any, Callable


def _jsonable(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, tuple):
        return [_jsonable(item) for item in value]
    if isinstance(value, list):
        return [_jsonable(item) for item in value]
    if isinstance(value, dict):
        return {str(key): _jsonable(item) for key, item in value.items()}
    return value


def _stable_hash(value: Any) -> str:
    payload = json.dumps(
        _jsonable(value),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        default=str,
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


class PlanStatus(str, Enum):
    READY = "ready"
    REQUIRES_INPUT = "requires_input"
    REJECTED = "rejected"


@dataclass(frozen=True)
class SemanticSelection:
    measure_codes: tuple[str, ...] = ()
    dimension_codes: tuple[str, ...] = ()
    fact_tables: tuple[str, ...] = ()
    execution_table_candidates: tuple[str, ...] = ()

    def to_dict(self) -> dict[str, Any]:
        return {
            "measureCodes": list(self.measure_codes),
            "dimensionCodes": list(self.dimension_codes),
            "factTables": list(self.fact_tables),
            "executionTableCandidates": list(self.execution_table_candidates),
        }


@dataclass(frozen=True)
class AnalysisSpecV1:
    question: str
    query_mode: str
    analysis_kind: str
    selection: SemanticSelection
    filters: tuple[dict[str, Any], ...] = ()
    time_range: dict[str, Any] = field(default_factory=dict)
    semantic_mapping: dict[str, Any] = field(default_factory=dict)
    output: dict[str, Any] = field(default_factory=dict)
    versions: dict[str, str] = field(default_factory=dict)
    spec_version: str = "1.0"

    def to_dict(self) -> dict[str, Any]:
        return {
            "specVersion": self.spec_version,
            "question": self.question,
            "queryMode": self.query_mode,
            "analysisKind": self.analysis_kind,
            "semantic": {
                **self.selection.to_dict(),
                "filters": _jsonable(self.filters),
                "timeRange": _jsonable(self.time_range),
                "mapping": _jsonable(self.semantic_mapping),
            },
            "output": _jsonable(self.output),
            "versions": _jsonable(self.versions),
        }

    @property
    def spec_hash(self) -> str:
        return _stable_hash(self.to_dict())


@dataclass(frozen=True)
class ClarificationSpec:
    reason_code: str
    message: str
    required_slots: tuple[str, ...] = ()
    candidates: tuple[dict[str, Any], ...] = ()
    resume_token: str = ""
    context_version: str = "1"

    def to_dict(self) -> dict[str, Any]:
        return {
            "reasonCode": self.reason_code,
            "message": self.message,
            "requiredSlots": list(self.required_slots),
            "candidates": _jsonable(self.candidates),
            "resumeToken": self.resume_token,
            "contextVersion": self.context_version,
        }


@dataclass(frozen=True)
class PlanResult:
    status: PlanStatus
    spec: AnalysisSpecV1
    diagnostic_code: str = ""
    clarification: ClarificationSpec | None = None

    @property
    def ready(self) -> bool:
        return self.status is PlanStatus.READY

    def to_dict(self) -> dict[str, Any]:
        return {
            "status": self.status.value,
            "specHash": self.spec.spec_hash,
            "analysisSpec": self.spec.to_dict(),
            "diagnosticCode": self.diagnostic_code,
            "clarification": self.clarification.to_dict() if self.clarification else None,
        }


@dataclass
class CompiledAnalysis:
    """Internal bridge while existing NLQ responses still expose ``daPayload``."""

    result: PlanResult
    legacy_plan: dict[str, Any]
    execution_payload: dict[str, Any] | None = None

    @property
    def status(self) -> PlanStatus:
        return self.result.status

    @property
    def ready(self) -> bool:
        return self.result.ready


@dataclass(frozen=True)
class ExecutionPolicy:
    allowed_tools: frozenset[str] = frozenset({"semantic.query"})
    max_rows_per_query: int = 10_000
    max_configure_items: int = 32
    max_filters: int = 32
    max_values_per_filter: int = 1_000
    max_string_length: int = 2_000


@dataclass(frozen=True)
class ExecutionResult:
    tool_name: str
    payload: dict[str, Any]
    result: dict[str, Any]


ExecuteCallable = Callable[[dict[str, Any]], dict[str, Any]]
