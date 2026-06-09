"""Implicit relationship discovery via Sentence-Transformers semantic similarity."""
from __future__ import annotations

import logging
import re
from collections import defaultdict
from typing import Dict, List, Set, Tuple

import numpy as np

from kg_builder.entities.models import ColumnEntity, EntityGraph
from kg_builder.relations.explicit import Relation

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

# Max column count before semantic embedding is skipped (O(n²) memory)
_SEMANTIC_MAX_COLUMNS = 500


class ImplicitRelationExtractor:
    """
    Discover implicit relationships between columns using:
      1. Sentence-Transformers semantic similarity on column names/comments
      2. Naming pattern matching (*_id, *_no, *_code) — directional (non-PK → PK)
      3. Data-type + statistical profile similarity, gated on detected_patterns
    """

    def __init__(
        self,
        model_name: str = "paraphrase-multilingual-MiniLM-L12-v2",
        similarity_threshold: float = 0.85,
        co_occurs_threshold: float = 0.70,
    ) -> None:
        self.model_name = model_name
        self.similarity_threshold = similarity_threshold
        self.co_occurs_threshold = co_occurs_threshold
        self._model = None   # lazy load

    def _load_model(self):
        if self._model is None:
            import os
            # HuggingFace tokenizers uses Rust-based parallelism that conflicts
            # with Python's multiprocessing on macOS, causing BrokenPipeError.
            os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self.model_name)
        return self._model

    # ------------------------------------------------------------------ #

    def extract(self, entity_graph: EntityGraph) -> List[Relation]:
        columns = entity_graph.columns
        if len(columns) < 2:
            return []

        relations: List[Relation] = []

        # 1. Semantic similarity via embeddings
        relations.extend(self._semantic_similarity(columns))

        # 2. Naming pattern matching (directional)
        pattern_rels = self._pattern_matching(columns)
        relations.extend(pattern_rels)

        # 3. Statistical profile similarity (gated on detected_patterns)
        relations.extend(self._profile_similarity(columns, pattern_rels))

        # Deduplicate — keep highest confidence for each (subj, pred, obj) triple
        return self._deduplicate(relations)

    # ------------------------------------------------------------------ #
    # Strategy 1: Sentence-Transformer embedding similarity
    # ------------------------------------------------------------------ #

    def _semantic_similarity(self, columns: List[ColumnEntity]) -> List[Relation]:
        if len(columns) > _SEMANTIC_MAX_COLUMNS:
            logger.warning(
                "Skipping semantic similarity: %d columns exceeds limit %d.",
                len(columns), _SEMANTIC_MAX_COLUMNS,
            )
            return []

        model = self._load_model()

        # Build text representation: "column_name: comment" or just name
        texts = []
        for col in columns:
            text = col.normalized_name.replace("_", " ")
            if col.comment:
                text += f": {col.comment}"
            texts.append(text)

        # Compute embeddings (batch)
        embeddings = model.encode(texts, normalize_embeddings=True)

        # Cosine similarity matrix (normalised → dot product)
        sim_matrix = np.dot(embeddings, embeddings.T)

        relations: List[Relation] = []
        n = len(columns)
        for i in range(n):
            for j in range(i + 1, n):
                score = float(sim_matrix[i, j])
                if score >= self.similarity_threshold:
                    # Skip if same table (CONTAINS already covers that)
                    if columns[i].table_id != columns[j].table_id:
                        relations.append(Relation(
                            subject_id=columns[i].id,
                            predicate=REL_SIMILAR_TO,
                            object_id=columns[j].id,
                            confidence=round(score, 4),
                        ))
                        # similarTo is symmetric — add reverse direction
                        relations.append(Relation(
                            subject_id=columns[j].id,
                            predicate=REL_SIMILAR_TO,
                            object_id=columns[i].id,
                            confidence=round(score, 4),
                        ))
        return relations

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
