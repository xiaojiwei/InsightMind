"""Implicit relationship discovery with optional configured-LLM semantics."""
from __future__ import annotations

import json
import logging
import math
import re
from collections import defaultdict
from typing import Callable, Dict, List, Protocol, Set, Tuple

from kg_builder.entities.models import ColumnEntity, EntityGraph
from kg_builder.relations.explicit import Relation
from kg_builder.utils.llm_config import (
    chat_completions_url,
    llm_config_from_env,
    llm_request_headers,
    validate_llm_config,
)

logger = logging.getLogger(__name__)

# Implicit relation type constants
REL_SIMILAR_TO    = "similarTo"
REL_POTENTIAL_FK  = "potentialFK"
REL_CO_OCCURS_WITH = "coOccursWith"

# Naming patterns that suggest ID/code columns (likely join candidates)
_ID_PATTERNS = re.compile(
    r"(_id|_no|_number|_code|_key|_fk|_ref|编号|代码|标识|主键)$",
    re.IGNORECASE,
)

# Keep opt-in LLM discovery bounded. Deterministic rules still run above this limit.
_LLM_MAX_COLUMNS = 200


class SemanticRelationDiscoverer(Protocol):
    def discover(
        self,
        columns: List[ColumnEntity],
        *,
        similarity_threshold: float,
    ) -> List[Relation]: ...


class LLMImplicitRelationDiscoverer:
    """Find semantic column equivalence through the configured chat model."""

    def __init__(
        self,
        *,
        completion: Callable[[str], str] | None = None,
        config_loader: Callable[[], dict[str, str]] = llm_config_from_env,
    ) -> None:
        self._completion = completion
        self._config_loader = config_loader

    @staticmethod
    def _prompt(columns: List[ColumnEntity]) -> str:
        catalog = [
            {
                "id": column.id,
                "tableId": column.table_id,
                "name": column.name,
                "normalizedName": column.normalized_name,
                "comment": column.comment or "",
                "dataType": column.data_type,
            }
            for column in columns
        ]
        return (
            "你是数据建模专家。请识别下列不同数据表中业务含义相同或高度相似的字段。\n"
            "只返回 JSON，不要返回 Markdown。格式："
            '{"relations":[{"source":"字段ID","target":"字段ID",'
            '"confidence":0.0,"reason":"简短理由"}]}。\n'
            "要求：只使用输入中的字段 ID；忽略同表字段；没有可靠关系时返回空数组；"
            "confidence 必须在 0 到 1 之间。\n"
            f"字段目录：{json.dumps(catalog, ensure_ascii=False, separators=(',', ':'))}"
        )

    @staticmethod
    def _configured_completion(prompt: str, cfg: dict[str, str]) -> str:
        import httpx

        response = httpx.post(
            chat_completions_url(cfg["base_url"]),
            headers=llm_request_headers(cfg),
            json={
                "model": cfg["model"],
                "temperature": 0.1,
                "max_tokens": 4096,
                "messages": [
                    {
                        "role": "system",
                        "content": "你只输出严格 JSON，并遵守用户给出的字段范围。",
                    },
                    {"role": "user", "content": prompt},
                ],
            },
            timeout=120,
        )
        response.raise_for_status()
        data = response.json()
        message = (data.get("choices") or [{}])[0].get("message") or {}
        content = (message.get("content") or "").strip()
        if not content:
            raise RuntimeError("configured LLM returned empty content")
        return content

    @staticmethod
    def _json_object(raw: str) -> dict:
        start = raw.find("{")
        end = raw.rfind("}")
        if start < 0 or end < start:
            raise ValueError("configured LLM did not return a JSON object")
        payload = json.loads(raw[start:end + 1])
        if not isinstance(payload, dict):
            raise ValueError("configured LLM response must be a JSON object")
        return payload

    def discover(
        self,
        columns: List[ColumnEntity],
        *,
        similarity_threshold: float,
    ) -> List[Relation]:
        if len(columns) < 2:
            return []
        if len(columns) > _LLM_MAX_COLUMNS:
            logger.warning(
                "跳过大模型语义关系发现：%d 个字段超过单次分析上限 %d；规则关系仍会继续。",
                len(columns),
                _LLM_MAX_COLUMNS,
            )
            return []

        try:
            prompt = self._prompt(columns)
            if self._completion is not None:
                raw = self._completion(prompt)
            else:
                cfg = self._config_loader()
                validate_llm_config(cfg, purpose="LLM semantic relation discovery")
                raw = self._configured_completion(prompt, cfg)
            payload = self._json_object(raw)
        except Exception as exc:
            logger.warning("大模型语义关系发现不可用，已跳过且继续规则建图：%s", exc)
            return []

        by_id = {column.id: column for column in columns}
        relations: List[Relation] = []
        candidates = payload.get("relations")
        if not isinstance(candidates, list):
            return []
        for candidate in candidates:
            if not isinstance(candidate, dict):
                continue
            source = by_id.get(str(candidate.get("source") or ""))
            target = by_id.get(str(candidate.get("target") or ""))
            if source is None or target is None or source.id == target.id:
                continue
            if source.table_id == target.table_id:
                continue
            try:
                confidence = float(candidate.get("confidence"))
            except (TypeError, ValueError):
                continue
            if not math.isfinite(confidence) or not 0.0 <= confidence <= 1.0:
                continue
            if confidence < similarity_threshold:
                continue
            reason = str(candidate.get("reason") or "").strip()
            properties = {"source": "llm", "reason": reason}
            relations.extend([
                Relation(
                    subject_id=source.id,
                    predicate=REL_SIMILAR_TO,
                    object_id=target.id,
                    confidence=confidence,
                    properties=properties,
                ),
                Relation(
                    subject_id=target.id,
                    predicate=REL_SIMILAR_TO,
                    object_id=source.id,
                    confidence=confidence,
                    properties=properties,
                ),
            ])
        return relations


class ImplicitRelationExtractor:
    """
    Discover implicit relationships between columns using:
      1. Optional configured-LLM semantic analysis on column names/comments
      2. Naming pattern matching (*_id, *_no, *_code) — directional (non-PK → PK)
      3. Data-type + statistical profile similarity, gated on detected_patterns
    """

    def __init__(
        self,
        similarity_threshold: float = 0.85,
        co_occurs_threshold: float = 0.70,
        enable_llm_semantics: bool = True,
        semantic_discoverer: SemanticRelationDiscoverer | None = None,
    ) -> None:
        try:
            normalized_threshold = float(similarity_threshold)
        except (TypeError, ValueError) as exc:
            raise ValueError("similarity_threshold must be between 0 and 1") from exc
        if not math.isfinite(normalized_threshold) or not 0.0 <= normalized_threshold <= 1.0:
            raise ValueError("similarity_threshold must be between 0 and 1")
        self.similarity_threshold = normalized_threshold
        self.co_occurs_threshold = co_occurs_threshold
        self.enable_llm_semantics = enable_llm_semantics
        self.semantic_discoverer = semantic_discoverer or LLMImplicitRelationDiscoverer()

    # ------------------------------------------------------------------ #

    def extract(self, entity_graph: EntityGraph) -> List[Relation]:
        columns = entity_graph.columns
        if len(columns) < 2:
            return []

        relations: List[Relation] = []

        # 1. Semantic similarity via the configured LLM (safe optional path)
        if self.enable_llm_semantics:
            relations.extend(self.semantic_discoverer.discover(
                columns,
                similarity_threshold=self.similarity_threshold,
            ))

        # 2. Naming pattern matching (directional)
        pattern_rels = self._pattern_matching(columns)
        relations.extend(pattern_rels)

        # 3. Statistical profile similarity (gated on detected_patterns)
        relations.extend(self._profile_similarity(columns, pattern_rels))

        # Deduplicate — keep highest confidence for each (subj, pred, obj) triple
        return self._deduplicate(relations)

    # ------------------------------------------------------------------ #
    # Strategy 2: Naming pattern matching — directional potentialFK
    # ------------------------------------------------------------------ #

    def _pattern_matching(self, columns: List[ColumnEntity]) -> List[Relation]:
        """
        Columns matching *_id / *_no / *_code patterns are grouped by
        their prefix stem.  Cross-table columns with the same stem get a
        potentialFK relation.

        Direction: non-PK column → PK column (FK points to the primary key).
        When both or neither are PKs the relation is bidirectional (ambiguous).
        Confidence is 0.7 baseline; bumped to 0.8 when one side is a PK.
        """
        stem_groups: Dict[str, List[ColumnEntity]] = defaultdict(list)
        for col in columns:
            name_lc = col.name.lower()
            if _ID_PATTERNS.search(name_lc):
                stem = _ID_PATTERNS.sub("", name_lc)
                stem_groups[stem].append(col)

        relations: List[Relation] = []
        for stem, group in stem_groups.items():
            if len(group) < 2:
                continue
            for i, col_a in enumerate(group):
                for col_b in group[i + 1:]:
                    if col_a.table_id == col_b.table_id:
                        continue

                    # Determine direction
                    a_is_pk = col_a.is_pk
                    b_is_pk = col_b.is_pk

                    if a_is_pk and not b_is_pk:
                        # col_b is the FK side → col_b references col_a's table
                        pairs = [(col_b, col_a)]
                        confidence = 0.8
                    elif b_is_pk and not a_is_pk:
                        pairs = [(col_a, col_b)]
                        confidence = 0.8
                    else:
                        # Both PK, both non-PK, or unknown — keep bidirectional
                        pairs = [(col_a, col_b), (col_b, col_a)]
                        confidence = 0.7

                    for src, tgt in pairs:
                        relations.append(Relation(
                            subject_id=src.id,
                            predicate=REL_POTENTIAL_FK,
                            object_id=tgt.id,
                            confidence=confidence,
                            properties={"stem": stem},
                        ))
        return relations

    # ------------------------------------------------------------------ #
    # Strategy 3: Statistical profile similarity
    # ------------------------------------------------------------------ #

    def _profile_similarity(
        self,
        columns: List[ColumnEntity],
        existing_pattern_rels: List[Relation],
    ) -> List[Relation]:
        """
        Two columns from different tables that share:
          - the same base type
          - similar cardinality and null_rate
          - AND at least one common detected_pattern (e.g. both are "uuid")

        are marked coOccursWith.

        If the pair already has a potentialFK relation, the coOccursWith
        confidence is set to 0 (skipped; instead the potentialFK confidence
        is promoted to 0.85 via _boost_potential_fk_confidence).
        """
        # Build set of existing potentialFK pairs for confidence boosting
        potential_fk_pairs: Set[Tuple[str, str]] = set()
        for rel in existing_pattern_rels:
            if rel.predicate == REL_POTENTIAL_FK:
                potential_fk_pairs.add((rel.subject_id, rel.object_id))
                potential_fk_pairs.add((rel.object_id, rel.subject_id))

        def base_type(dt: str) -> str:
            dt = dt.upper()
            for t in ("INT", "BIGINT", "SMALLINT", "TINYINT", "NUMERIC",
                      "DECIMAL", "FLOAT", "DOUBLE", "REAL", "NUMBER"):
                if t in dt:
                    return "NUMERIC"
            for t in ("VARCHAR", "CHAR", "TEXT", "NVARCHAR", "NCHAR",
                      "STRING", "CLOB"):
                if t in dt:
                    return "STRING"
            for t in ("DATE", "TIME", "TIMESTAMP", "DATETIME"):
                if t in dt:
                    return "DATETIME"
            return "OTHER"

        # Group columns by base_type to reduce O(n²) to O(k²) per group
        type_groups: Dict[str, List[ColumnEntity]] = defaultdict(list)
        for col in columns:
            type_groups[base_type(col.data_type)].append(col)

        relations: List[Relation] = []
        boosted_fk_ids: Set[str] = set()  # relation ids whose confidence we bump

        for btype, group in type_groups.items():
            n = len(group)
            for i in range(n):
                for j in range(i + 1, n):
                    col_a, col_b = group[i], group[j]
                    if col_a.table_id == col_b.table_id:
                        continue
                    if col_a.cardinality == 0 or col_b.cardinality == 0:
                        continue

                    # Cardinality ratio (0 → 1, higher = more similar)
                    card_ratio = min(col_a.cardinality, col_b.cardinality) / max(
                        col_a.cardinality, col_b.cardinality
                    )
                    # Null rate closeness
                    null_diff = abs(col_a.null_rate - col_b.null_rate)
                    null_score = max(0.0, 1.0 - null_diff * 5)

                    score = 0.6 * card_ratio + 0.4 * null_score
                    if score < self.co_occurs_threshold:
                        continue

                    # Gate: require at least one common detected_pattern
                    common_patterns = set(col_a.detected_patterns) & set(col_b.detected_patterns)
                    if not common_patterns:
                        continue

                    # If there is already a potentialFK link, boost its confidence
                    # instead of emitting a separate coOccursWith relation.
                    if (col_a.id, col_b.id) in potential_fk_pairs:
                        for rel in existing_pattern_rels:
                            if (rel.predicate == REL_POTENTIAL_FK and
                                    rel.subject_id == col_a.id and
                                    rel.object_id == col_b.id):
                                rel.confidence = max(rel.confidence, 0.85)
                            if (rel.predicate == REL_POTENTIAL_FK and
                                    rel.subject_id == col_b.id and
                                    rel.object_id == col_a.id):
                                rel.confidence = max(rel.confidence, 0.85)
                        continue

                    relations.append(Relation(
                        subject_id=col_a.id,
                        predicate=REL_CO_OCCURS_WITH,
                        object_id=col_b.id,
                        confidence=round(score, 4),
                        properties={"common_patterns": sorted(common_patterns)},
                    ))
        return relations

    # ------------------------------------------------------------------ #

    @staticmethod
    def _deduplicate(relations: List[Relation]) -> List[Relation]:
        seen: dict = {}
        for rel in relations:
            key = (rel.subject_id, rel.predicate, rel.object_id)
            if key not in seen or rel.confidence > seen[key].confidence:
                seen[key] = rel
        return list(seen.values())
