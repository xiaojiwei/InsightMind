"""Typed contracts shared by catalog, retriever, NLQ, and analysis."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass(frozen=True)
class CatalogItem:
    code: str
    semantic_type: str
    cn_name: str
    en_name: str = ""
    definition: str = ""
    description: str = ""
    aliases: tuple[str, ...] = ()
    tables: frozenset[str] = frozenset()
    hierarchy_code: str = ""
    level_code: str = ""
    view_type: int | None = None
    unit: str = ""
    caliber: str = ""
    domain_code: str = "default"
    metadata: dict[str, Any] = field(default_factory=dict, compare=False, hash=False)

    @property
    def is_time(self) -> bool:
        return self.semantic_type == "dimension" and (
            self.hierarchy_code.lower() in {"h_date", "hier_stat_time", "hier_time"}
            or self.level_code.lower() in {"hour", "day", "week", "month", "quarter", "year"}
            or (self.view_type is not None and 1 <= self.view_type <= 6)
        )

    def embedding_text(self) -> str:
        parts = [
            f"类型:{'指标' if self.semantic_type == 'measure' else '维度'}",
            f"编码:{self.code}",
            f"名称:{self.cn_name}",
            f"英文:{self.en_name}" if self.en_name else "",
            f"别名:{'、'.join(self.aliases)}" if self.aliases else "",
            f"定义:{self.definition}" if self.definition else "",
            f"描述:{self.description}" if self.description else "",
            f"口径:{self.caliber}" if self.caliber else "",
            f"单位:{self.unit}" if self.unit else "",
            f"分类:{self.metadata.get('category')}" if self.metadata.get("category") else "",
            f"粒度:{self.level_code}" if self.level_code else "",
            f"事实表:{'、'.join(sorted(self.tables))}" if self.tables else "",
        ]
        return "；".join(part for part in parts if part)

    def to_dict(self) -> dict[str, Any]:
        value = asdict(self)
        value["semanticType"] = value.pop("semantic_type")
        value["cnName"] = value.pop("cn_name")
        value["enName"] = value.pop("en_name")
        value["hierarchyCode"] = value.pop("hierarchy_code")
        value["levelCode"] = value.pop("level_code")
        value["viewType"] = value.pop("view_type")
        value["domainCode"] = value.pop("domain_code")
        value["aliases"] = list(self.aliases)
        value["tables"] = sorted(self.tables)
        value["isTime"] = self.is_time
        return value


@dataclass(frozen=True)
class CatalogValue:
    dimension_code: str
    canonical_value: str
    aliases: tuple[str, ...] = ()
    source: str = "kg_sample"
    tables: frozenset[str] = frozenset()
    sensitive: bool = False
    metadata: dict[str, Any] = field(default_factory=dict, compare=False, hash=False)

    def to_dict(self) -> dict[str, Any]:
        return {
            "dimensionCode": self.dimension_code,
            "canonicalValue": self.canonical_value,
            "aliases": list(self.aliases),
            "source": self.source,
            "tables": sorted(self.tables),
            "sensitive": self.sensitive,
            "metadata": dict(self.metadata),
        }


@dataclass(frozen=True)
class AliasRecord:
    term: str
    normalized_term: str
    semantic_type: str
    canonical_code: str
    source: str
    weight: float
    status: str = "ENABLED"
    domain_code: str = "default"


@dataclass
class CatalogSnapshot:
    version: str
    graph_hash: str
    source_graph_hash: str
    dictionary_hash: str
    items: dict[str, CatalogItem]
    values: list[CatalogValue]
    aliases: list[AliasRecord]
    generated_at: str
    warnings: list[str] = field(default_factory=list)

    @property
    def snapshot_key(self) -> str:
        return (
            f"{self.graph_hash}:{self.source_graph_hash}:"
            f"{self.dictionary_hash}:{self.version}"
        )

    def to_dict(self, *, include_values: bool = False) -> dict[str, Any]:
        result = {
            "version": self.version,
            "graphHash": self.graph_hash,
            "sourceGraphHash": self.source_graph_hash,
            "dictionaryHash": self.dictionary_hash,
            "snapshotKey": self.snapshot_key,
            "generatedAt": self.generated_at,
            "itemCount": len(self.items),
            "measureCount": sum(i.semantic_type == "measure" for i in self.items.values()),
            "dimensionCount": sum(i.semantic_type == "dimension" for i in self.items.values()),
            "valueCount": len(self.values),
            "aliasCount": len(self.aliases),
            "warnings": list(self.warnings),
        }
        if include_values:
            result["items"] = [item.to_dict() for item in self.items.values()]
            result["values"] = [value.to_dict() for value in self.values]
        return result


@dataclass
class MatchEvidence:
    match_type: str
    matched_text: str
    source: str
    detail: str = ""
    score: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        return {
            "matchType": self.match_type,
            "matchedText": self.matched_text,
            "source": self.source,
            "detail": self.detail,
            "score": round(float(self.score), 4),
        }


@dataclass
class RecallCandidate:
    semantic_type: str
    code: str
    name: str
    score: float
    match_type: str
    tables: set[str] = field(default_factory=set)
    evidence: list[MatchEvidence] = field(default_factory=list)
    canonical_value: str = ""
    dimension_code: str = ""
    confidence: str = "low"
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def identity(self) -> tuple[str, str, str, str]:
        return (
            self.semantic_type,
            self.code,
            self.dimension_code,
            self.canonical_value,
        )

    def to_dict(self) -> dict[str, Any]:
        result = {
            "semanticType": self.semantic_type,
            "code": self.code,
            "name": self.name,
            "score": round(float(self.score), 4),
            "matchType": self.match_type,
            "confidence": self.confidence,
            "tables": sorted(self.tables),
            "evidence": [item.to_dict() for item in self.evidence],
        }
        if self.dimension_code:
            result["dimensionCode"] = self.dimension_code
        if self.canonical_value:
            result["canonicalValue"] = self.canonical_value
        if self.metadata:
            result["metadata"] = dict(self.metadata)
        return result


@dataclass
class ValueBinding:
    dimension_code: str
    input_text: str
    canonical_value: str
    score: float
    confidence: str
    source: str
    filter_safe: bool = False
    evidence: list[MatchEvidence] = field(default_factory=list)
    applicable_tables: set[str] = field(default_factory=set)

    def to_dict(self) -> dict[str, Any]:
        return {
            "dimensionCode": self.dimension_code,
            "input": self.input_text,
            "canonicalValue": self.canonical_value,
            "score": round(float(self.score), 4),
            "confidence": self.confidence,
            "source": self.source,
            "filterSafe": self.filter_safe,
            "applicableTables": sorted(self.applicable_tables),
            "evidence": [item.to_dict() for item in self.evidence],
        }


@dataclass
class SemanticMatchResult:
    question: str
    measure_candidates: list[RecallCandidate]
    dimension_candidates: list[RecallCandidate]
    value_bindings: list[ValueBinding]
    confidence: str
    decision: str
    needs_clarification: bool
    snapshot_key: str
    elapsed_ms: int = 0
    diagnostics: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "question": self.question,
            "measureCandidates": [item.to_dict() for item in self.measure_candidates],
            "dimensionCandidates": [item.to_dict() for item in self.dimension_candidates],
            "valueBindings": [item.to_dict() for item in self.value_bindings],
            "confidence": self.confidence,
            "decision": self.decision,
            "needsClarification": self.needs_clarification,
            "snapshotKey": self.snapshot_key,
            "elapsedMs": self.elapsed_ms,
            "diagnostics": dict(self.diagnostics),
        }
