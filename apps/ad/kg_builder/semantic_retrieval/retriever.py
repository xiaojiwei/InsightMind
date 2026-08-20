"""Multi-route deterministic, fuzzy, and vector catalog recall."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from difflib import SequenceMatcher
import heapq
import re
from typing import Iterable

from .embedding import SemanticVectorIndex
from .models import AliasRecord, CatalogSnapshot, MatchEvidence, RecallCandidate
from .normalizer import explicit_codes, normalize_text, tokenize


_LATIN_OR_NUMERIC_TERM_RE = re.compile(
    r"[A-Za-z0-9]+(?:[\s_-]+[A-Za-z0-9]+)*"
)
_CHINESE_TERM_RE = re.compile(r"[\u4e00-\u9fff]+")
_LEFT_VALUE_CONTEXT = (
    "查询", "查看", "分析", "筛选", "选择", "只看", "看看",
    "按", "看", "查", "选", "为", "是", "在", "到", "从",
    "和", "与", "及", "或", "非属于", "不要", "不看", "除了", "除", "非",
    "那", "那么", "not", "without",
)
_RIGHT_VALUE_CONTEXT = (
    "以及", "还有", "然后", "查询", "查看", "分析", "对比",
    "以外", "更正为", "和", "与", "及", "或", "的", "按", "看", "为",
    "外", "改", "换", "吗", "呢", "吧", "啊", "呀", "嘛",
)
_VECTOR_RESIDUAL_NOISE = tuple(sorted({
    "please", "show", "query", "analyze", "analysis", "business", "trend",
    "summary", "report", "compare", "comparison", "by", "for", "and", "the",
    "请帮我", "帮我", "查询", "查看", "看看", "分析", "统计", "展示", "计算",
    "对比", "比较", "按", "看", "请", "的", "和", "与", "及", "以及", "还有",
    "最近", "过去", "当前", "本月", "上月", "本年", "今年", "去年", "同比", "环比",
    "趋势", "情况", "数据", "指标", "维度", "为什么", "原因", "一下", "下降", "上升",
    "增长", "减少", "变化", "波动", "异常", "持续", "可能", "业务", "改进", "建议", "后续",
}, key=lambda value: (-len(value), value)))


def _char_bigrams(value: str) -> set[str]:
    return {
        value[index:index + 2]
        for index in range(max(0, len(value) - 1))
    }


def _is_latin_or_numeric_term(term: str) -> bool:
    return bool(_LATIN_OR_NUMERIC_TERM_RE.fullmatch(str(term).strip()))


def _word_boundary_present(term: str, query: str) -> bool:
    raw_term = str(term).strip()
    parts = re.split(r"[\s_-]+", raw_term)
    flexible_term = r"[\s_-]+".join(re.escape(part) for part in parts)
    return bool(re.search(
        rf"(?<![A-Za-z0-9_]){flexible_term}(?![A-Za-z0-9_])",
        str(query),
        re.I,
    ))


def _normalized_spans(term: str, query: str) -> list[tuple[int, int]]:
    spans: list[tuple[int, int]] = []
    start = 0
    while term:
        index = query.find(term, start)
        if index < 0:
            break
        spans.append((index, index + len(term)))
        start = index + 1
    return spans


def _is_adjacent_transposition(left: str, right: str) -> bool:
    if len(left) != len(right):
        return False
    mismatches = [
        index for index, (left_char, right_char) in enumerate(zip(left, right))
        if left_char != right_char
    ]
    return (
        len(mismatches) == 2
        and mismatches[1] == mismatches[0] + 1
        and left[mismatches[0]] == right[mismatches[1]]
        and left[mismatches[1]] == right[mismatches[0]]
    )


def _sequence_ratio_upper_bound(
    left_counts: Counter[str],
    left_length: int,
    right_counts: Counter[str],
    right_length: int,
) -> float:
    if not left_length or not right_length:
        return 0.0
    possible_matches = sum(
        min(count, right_counts.get(char, 0))
        for char, count in left_counts.items()
    )
    return 2.0 * possible_matches / (left_length + right_length)


def _indexed_context_present(
    text: str,
    *,
    at_end: bool,
    terms: frozenset[str],
    lengths: tuple[int, ...],
) -> bool:
    for size in lengths:
        if size > len(text):
            continue
        candidate = text[-size:] if at_end else text[:size]
        if candidate in terms:
            return True
    return False


@dataclass(frozen=True)
class _ValueTermRecord:
    dimension_code: str
    canonical_value: str
    term: str
    normalized_term: str
    source: str
    weight: float
    tables: frozenset[str]
    safe_tables: frozenset[str]


@dataclass
class RecallResponse:
    candidates: list[RecallCandidate]
    vector_used: bool = False
    vector_disabled_reason: str = ""
    diagnostics: dict[str, object] = field(default_factory=dict)


class SemanticRetriever:
    def __init__(
        self,
        snapshot: CatalogSnapshot,
        *,
        vector_index: SemanticVectorIndex | None = None,
        fuzzy_threshold: float = 0.72,
        vector_threshold: float = 0.45,
    ) -> None:
        self.snapshot = snapshot
        self.vector_index = vector_index
        self.fuzzy_threshold = max(0.0, min(float(fuzzy_threshold), 1.0))
        self.vector_threshold = max(-1.0, min(float(vector_threshold), 1.0))
        self._dimension_terms: dict[str, tuple[str, ...]] = {}
        dimension_terms: dict[str, set[str]] = {}
        for alias in snapshot.aliases:
            if alias.semantic_type == "dimension" and len(alias.normalized_term) >= 2:
                dimension_terms.setdefault(alias.canonical_code, set()).add(
                    alias.normalized_term
                )
        self._dimension_terms = {
            code: tuple(sorted(terms)) for code, terms in dimension_terms.items()
        }
        entity_context_terms = {
            alias.normalized_term
            for alias in snapshot.aliases
            if len(alias.normalized_term) >= 2
            and _CHINESE_TERM_RE.fullmatch(alias.normalized_term)
        }
        self._entity_context_terms = frozenset(entity_context_terms)
        self._entity_context_lengths = tuple(sorted(
            {len(term) for term in entity_context_terms}, reverse=True
        ))
        self._entity_term_bigrams = {
            alias.normalized_term: _char_bigrams(alias.normalized_term)
            for alias in snapshot.aliases
            if alias.normalized_term
        }

        self._value_terms_by_norm: dict[str, list[_ValueTermRecord]] = {}
        for value in snapshot.values:
            alias_weights = value.metadata.get("aliasWeights") or {}
            alias_sources = value.metadata.get("aliasSources") or {}
            alias_tables = value.metadata.get("aliasTables") or {}
            alias_safe_tables = value.metadata.get("aliasSafeTables") or {}
            default_weight = float(value.metadata.get("weight") or 0.97)
            default_safe_tables = set(value.metadata.get("safeTables") or [])
            terms_by_norm: dict[str, dict[str, object]] = {}

            def add_term(
                term: str,
                source: str,
                weight: float,
                is_canonical: bool,
                term_tables: set[str],
                term_safe_tables: set[str],
            ) -> None:
                term_norm = normalize_text(term)
                if not term_norm:
                    return
                previous = terms_by_norm.get(term_norm)
                if previous is None:
                    terms_by_norm[term_norm] = {
                        "term": term,
                        "source": source,
                        "weight": weight,
                        "canonical": is_canonical,
                        "tables": set(term_tables),
                        "safeTables": set(term_safe_tables),
                    }
                    return
                previous["canonical"] = bool(previous["canonical"]) or is_canonical
                previous["tables"] = set(previous["tables"]) | set(term_tables)
                previous["safeTables"] = (
                    set(previous["safeTables"]) | set(term_safe_tables)
                )
                if weight > float(previous["weight"]):
                    previous["term"] = term
                    previous["source"] = source
                    previous["weight"] = weight

            add_term(
                value.canonical_value,
                value.source,
                0.95,
                True,
                set(value.tables),
                default_safe_tables,
            )
            for alias in value.aliases:
                alias_norm = normalize_text(alias)
                scoped_tables = set(
                    alias_tables[alias_norm]
                    if alias_norm in alias_tables else value.tables
                )
                scoped_safe_tables = set(
                    alias_safe_tables[alias_norm]
                    if alias_norm in alias_safe_tables else default_safe_tables
                )
                add_term(
                    alias,
                    str(alias_sources.get(alias_norm) or value.source),
                    float(alias_weights.get(alias_norm) or default_weight),
                    False,
                    scoped_tables,
                    scoped_safe_tables,
                )

            for term_norm, term_row in terms_by_norm.items():
                record = _ValueTermRecord(
                    dimension_code=value.dimension_code,
                    canonical_value=value.canonical_value,
                    term=str(term_row["term"]),
                    normalized_term=term_norm,
                    source=str(term_row["source"]),
                    weight=float(term_row["weight"]),
                    tables=frozenset(term_row["tables"]),
                    safe_tables=frozenset(term_row["safeTables"]),
                )
                self._value_terms_by_norm.setdefault(term_norm, []).append(record)

        self._value_term_lengths = frozenset(
            len(term_norm) for term_norm in self._value_terms_by_norm
        )
        self._value_term_char_counts = {
            term_norm: Counter(term_norm) for term_norm in self._value_terms_by_norm
        }
        value_norms_by_bigram: dict[str, set[str]] = {}
        for term_norm in self._value_terms_by_norm:
            if len(term_norm) < 3:
                continue
            for gram in _char_bigrams(term_norm):
                value_norms_by_bigram.setdefault(gram, set()).add(term_norm)
        self._value_norms_by_bigram = value_norms_by_bigram

    def _chinese_value_phrase_present(self, term: str, query: str) -> bool:
        """Accept a Chinese value only as a standalone/contextual phrase.

        Chinese text has no universal word delimiter, so boundaries are based
        on punctuation, query operators, and adjacent known entity names. This
        rejects arbitrary compounds such as ``华东区`` while retaining normal
        compact NLQ forms such as ``查询成交额东区`` and ``东区成交额``.
        """
        raw_query = str(query)
        q_norm = normalize_text(query)
        term_norm = normalize_text(term)
        if any(
            len(entity_term) > len(term_norm)
            and term_norm in entity_term
            and entity_term in q_norm
            for entity_term in self._entity_context_terms
        ):
            # Mapper performs the final entity-overlap suppression and exposes
            # its diagnostic counter. Keep this evidence available to it.
            return True
        start = 0
        while term:
            index = raw_query.find(term, start)
            if index < 0:
                break
            end = index + len(term)
            left_raw = raw_query[:index]
            right_raw = raw_query[end:]
            left_char = left_raw[-1:] if left_raw else ""
            right_char = right_raw[:1] if right_raw else ""
            left_norm = normalize_text(left_raw)
            right_norm = normalize_text(right_raw)
            left_ok = (
                not left_char
                or not re.fullmatch(r"[0-9A-Za-z\u4e00-\u9fff_]", left_char)
                or any(left_norm.endswith(context) for context in _LEFT_VALUE_CONTEXT)
                or _indexed_context_present(
                    left_norm,
                    at_end=True,
                    terms=self._entity_context_terms,
                    lengths=self._entity_context_lengths,
                )
            )
            right_ok = (
                not right_char
                or not re.fullmatch(r"[0-9A-Za-z\u4e00-\u9fff_]", right_char)
                or any(right_norm.startswith(context) for context in _RIGHT_VALUE_CONTEXT)
                or _indexed_context_present(
                    right_norm,
                    at_end=False,
                    terms=self._entity_context_terms,
                    lengths=self._entity_context_lengths,
                )
            )
            if left_ok and right_ok:
                return True
            start = index + 1
        return False

    def _has_uncovered_query_span(
        self,
        query: str,
        candidates: Iterable[RecallCandidate],
    ) -> bool:
        residual = normalize_text(query)
        if not residual:
            return False

        covered_terms: set[str] = set()
        for candidate in candidates:
            for evidence in candidate.evidence:
                if evidence.match_type not in {"fuzzy", "vector"}:
                    term_norm = normalize_text(evidence.matched_text)
                    if term_norm:
                        covered_terms.add(term_norm)
        # Remove catalog entities of other semantic types as well. For
        # example, a measure search should not interpret an exact dimension
        # name as an uncovered second measure.
        for alias in self.snapshot.aliases:
            term_norm = alias.normalized_term
            if not term_norm or term_norm not in residual:
                continue
            if (
                not _is_latin_or_numeric_term(alias.term)
                or _word_boundary_present(alias.term, query)
            ):
                covered_terms.add(term_norm)
        for term_norm in sorted(covered_terms, key=lambda value: (-len(value), value)):
            residual = residual.replace(term_norm, "")
        for noise in _VECTOR_RESIDUAL_NOISE:
            residual = residual.replace(noise, "")
        return len(residual) >= 2

    @staticmethod
    def _confidence(score: float, match_type: str) -> str:
        if score >= 0.94 and match_type not in {"fuzzy", "vector"}:
            return "high"
        if score >= 0.68:
            return "medium"
        return "low"

    @staticmethod
    def _merge(
        candidates: dict[tuple[str, str, str, str], RecallCandidate],
        candidate: RecallCandidate,
    ) -> None:
        previous = candidates.get(candidate.identity)
        if previous is None:
            candidates[candidate.identity] = candidate
            return
        # A single canonical value may be mentioned through multiple aliases.
        # Its usable scope must satisfy every piece of matched evidence; taking
        # the first alias (or unioning scopes) could authorize a filter on a
        # table for which one of those aliases was never reviewed.
        previous.tables.intersection_update(candidate.tables)
        if "filterSafe" in previous.metadata or "filterSafe" in candidate.metadata:
            previous.metadata["filterSafe"] = bool(
                previous.tables
                and previous.metadata.get("filterSafe")
                and candidate.metadata.get("filterSafe")
            )
        if "safeTables" in previous.metadata or "safeTables" in candidate.metadata:
            previous_safe_tables = set(previous.metadata.get("safeTables") or [])
            candidate_safe_tables = set(candidate.metadata.get("safeTables") or [])
            previous.metadata["safeTables"] = sorted(
                previous_safe_tables & candidate_safe_tables
            )
        previous.evidence.extend(
            evidence for evidence in candidate.evidence
            if evidence.to_dict() not in [item.to_dict() for item in previous.evidence]
        )
        if candidate.score > previous.score:
            previous.score = candidate.score
            previous.match_type = candidate.match_type
            previous.confidence = candidate.confidence

    def _eligible(
        self,
        code: str,
        semantic_type: str,
        *,
        semantic_types: set[str] | None,
        allowed_codes: set[str] | None,
        allowed_tables: set[str] | None,
        strict_tables: bool,
    ) -> tuple[bool, float]:
        item = self.snapshot.items.get(code)
        if item is None or item.semantic_type != semantic_type:
            return False, 0.0
        if semantic_types and semantic_type not in semantic_types:
            return False, 0.0
        if allowed_codes is not None and code not in allowed_codes:
            return False, 0.0
        if allowed_tables:
            compatible = bool(item.tables & allowed_tables)
            if strict_tables and not compatible:
                return False, 0.0
            return True, 0.02 if compatible else 0.0
        return True, 0.0

    def search(
        self,
        query: str,
        *,
        semantic_types: Iterable[str] | None = None,
        allowed_codes: Iterable[str] | None = None,
        allowed_tables: Iterable[str] | None = None,
        strict_tables: bool = False,
        top_k: int = 20,
        include_vector: bool = True,
    ) -> RecallResponse:
        q_norm = normalize_text(query)
        if not q_norm:
            return RecallResponse([])
        type_filter = set(semantic_types or ()) or None
        code_filter = set(allowed_codes) if allowed_codes is not None else None
        table_filter = set(allowed_tables or ()) or None
        measure_codes = explicit_codes(query, "MEAS")
        dimension_codes = explicit_codes(query, "DIM")
        candidates: dict[tuple[str, str, str, str], RecallCandidate] = {}
        entity_phrase_spans: dict[
            str, dict[str, list[tuple[int, int]]]
        ] = {}
        for alias in self.snapshot.aliases:
            term = alias.normalized_term
            if (
                term
                and term in q_norm
                and (
                    not _is_latin_or_numeric_term(alias.term)
                    or _word_boundary_present(alias.term, query)
                )
            ):
                entity_phrase_spans.setdefault(alias.semantic_type, {})[term] = (
                    _normalized_spans(term, q_norm)
                )

        def entity_phrase_is_covered(alias) -> bool:
            term = alias.normalized_term
            own_spans = entity_phrase_spans.get(alias.semantic_type, {}).get(term, [])
            if not own_spans:
                return False
            longer_spans = [
                span
                for other_term, spans in entity_phrase_spans.get(
                    alias.semantic_type, {}
                ).items()
                if len(other_term) > len(term)
                for span in spans
            ]
            return bool(longer_spans) and all(
                any(
                    longer_start <= own_start and own_end <= longer_end
                    for longer_start, longer_end in longer_spans
                )
                for own_start, own_end in own_spans
            )

        for alias in self.snapshot.aliases:
            eligible, table_bonus = self._eligible(
                alias.canonical_code,
                alias.semantic_type,
                semantic_types=type_filter,
                allowed_codes=code_filter,
                allowed_tables=table_filter,
                strict_tables=strict_tables,
            )
            if not eligible:
                continue
            term = alias.normalized_term
            if not term:
                continue
            if entity_phrase_is_covered(alias):
                continue
            matched_text = alias.term
            code_suffix_match = re.fullmatch(
                r"(?:MEAS|DIM)_(.+)", alias.term.strip(), re.I
            ) if alias.source == "kg_code" else None
            code_suffix = code_suffix_match.group(1) if code_suffix_match else ""
            is_explicit_code = (
                alias.semantic_type == "measure" and alias.canonical_code.upper() in measure_codes
            ) or (
                alias.semantic_type == "dimension" and alias.canonical_code.upper() in dimension_codes
            )
            if is_explicit_code:
                match_type = "explicit_code"
                score = 1.0
            elif term == q_norm:
                match_type = "exact_name" if alias.source in {"kg_name", "kg_code"} else "exact_alias"
                score = alias.weight
            elif code_suffix and _word_boundary_present(code_suffix, query):
                match_type = "name_phrase"
                score = min(0.985, alias.weight)
                matched_text = code_suffix
            elif (
                term in q_norm
                and (
                    not _is_latin_or_numeric_term(alias.term)
                    or _word_boundary_present(alias.term, query)
                )
                and (len(term) >= 3 or alias.source not in {"legacy_synonym"})
            ):
                match_type = "name_phrase" if alias.source in {"kg_name", "kg_code"} else "alias_phrase"
                if alias.source in {"kg_name", "kg_code"}:
                    score = min(0.985, alias.weight)
                else:
                    score = min(0.985, alias.weight)
            else:
                continue
            score = min(1.0, score + table_bonus)
            item = self.snapshot.items[alias.canonical_code]
            evidence = MatchEvidence(
                match_type=match_type,
                matched_text=matched_text,
                source=alias.source,
                detail=f"{matched_text} → {item.code}",
                score=score,
            )
            self._merge(candidates, RecallCandidate(
                semantic_type=item.semantic_type,
                code=item.code,
                name=item.cn_name,
                score=score,
                match_type=match_type,
                tables=set(item.tables),
                evidence=[evidence],
                confidence=self._confidence(score, match_type),
            ))

        fuzzy_used = False
        chunks = list(dict.fromkeys([q_norm, *tokenize(query)]))
        chunk_lengths = {len(chunk) for chunk in chunks}
        fuzzy_alias_pool: list[tuple[AliasRecord, float, float, int]] = []

        chunk_features = {
            chunk: _char_bigrams(chunk) for chunk in chunks if len(chunk) >= 2
        }
        chunks_by_bigram: dict[str, set[str]] = {}
        for chunk, grams in chunk_features.items():
            for gram in grams:
                chunks_by_bigram.setdefault(gram, set()).add(chunk)
        four_char_chunks_by_signature: dict[str, set[str]] = {}
        for chunk in chunks:
            if len(chunk) == 4:
                four_char_chunks_by_signature.setdefault(
                    "".join(sorted(chunk)), set()
                ).add(chunk)
        long_windows_by_size: dict[int, dict[str, set[str]]] = {}
        query_bigrams = _char_bigrams(q_norm)

        def indexed_windows(size: int) -> dict[str, set[str]]:
            cached = long_windows_by_size.get(size)
            if cached is not None:
                return cached
            indexed: dict[str, set[str]] = {}
            if 2 <= size <= len(q_norm):
                for start in range(len(q_norm) - size + 1):
                    window = q_norm[start:start + size]
                    window_grams = _char_bigrams(window)
                    chunk_features.setdefault(window, window_grams)
                    for gram in window_grams:
                        indexed.setdefault(gram, set()).add(window)
            long_windows_by_size[size] = indexed
            return indexed

        def fuzzy_chunks(term: str) -> list[str]:
            grams = _char_bigrams(term)
            shared_grams = grams & query_bigrams
            transposition_pool = (
                four_char_chunks_by_signature.get("".join(sorted(term)), set())
                if len(term) == 4 else set()
            )
            if (not shared_grams and not transposition_pool) or (
                len(grams) >= 4 and len(shared_grams) / len(grams) < 0.25
            ):
                return []
            pool: set[str] = set(transposition_pool)
            for gram in grams:
                pool.update(chunks_by_bigram.get(gram, ()))
            if len(term) > 6 and len(q_norm) > len(term):
                for size in {len(term) - 1, len(term), len(term) + 1}:
                    windows = indexed_windows(size)
                    for gram in grams:
                        pool.update(windows.get(gram, ()))
            ranked_pool = sorted(
                pool,
                key=lambda chunk: (
                    -len(grams & chunk_features.get(chunk, set())) / max(1, len(grams)),
                    abs(len(chunk) - len(term)),
                    chunk,
                ),
            )
            return ranked_pool[:12]

        for alias in self.snapshot.aliases:
            eligible, table_bonus = self._eligible(
                alias.canonical_code,
                alias.semantic_type,
                semantic_types=type_filter,
                allowed_codes=code_filter,
                allowed_tables=table_filter,
                strict_tables=strict_tables,
            )
            if not eligible or len(alias.normalized_term) < 3:
                continue
            if entity_phrase_is_covered(alias):
                continue
            if (
                _is_latin_or_numeric_term(alias.term)
                and not _word_boundary_present(alias.term, query)
            ):
                continue
            grams = self._entity_term_bigrams.get(alias.normalized_term, set())
            shared_grams = grams & query_bigrams
            has_transposition_candidate = bool(
                len(alias.normalized_term) == 4
                and four_char_chunks_by_signature.get(
                    "".join(sorted(alias.normalized_term))
                )
            )
            if (not shared_grams and not has_transposition_candidate) or (
                len(grams) >= 4 and len(shared_grams) / len(grams) < 0.25
            ):
                continue
            coverage = len(shared_grams) / max(1, len(grams))
            length_delta = min(
                (abs(len(alias.normalized_term) - size) for size in chunk_lengths),
                default=len(alias.normalized_term),
            )
            fuzzy_alias_pool.append((alias, table_bonus, coverage, length_delta))

        fuzzy_limit = max(256, max(1, int(top_k)) * 32)
        fuzzy_alias_pool = heapq.nsmallest(
            fuzzy_limit,
            fuzzy_alias_pool,
            key=lambda row: (
                -row[2],
                row[3],
                row[0].canonical_code,
                row[0].source,
                row[0].term,
            ),
        )

        for alias, table_bonus, _coverage, _length_delta in fuzzy_alias_pool:
            best_chunk, ratio = max(
                (
                    (chunk, SequenceMatcher(None, chunk, alias.normalized_term).ratio())
                    for chunk in fuzzy_chunks(alias.normalized_term)
                ),
                key=lambda item: item[1],
                default=("", 0.0),
            )
            if ratio < self.fuzzy_threshold:
                continue
            fuzzy_used = True
            score = min(0.89, ratio * alias.weight * 0.92 + table_bonus)
            if (
                alias.weight >= 0.90
                and _is_adjacent_transposition(best_chunk, alias.normalized_term)
            ):
                score = max(0.68, score)
            item = self.snapshot.items[alias.canonical_code]
            self._merge(candidates, RecallCandidate(
                semantic_type=item.semantic_type,
                code=item.code,
                name=item.cn_name,
                score=score,
                match_type="fuzzy",
                tables=set(item.tables),
                evidence=[MatchEvidence(
                    match_type="fuzzy",
                    matched_text=best_chunk,
                    source=alias.source,
                    detail=f"{best_chunk} ≈ {alias.term}，文本相似度 {ratio:.3f}",
                    score=score,
                )],
                confidence=self._confidence(score, "fuzzy"),
            ))

        strongest = max((candidate.score for candidate in candidates.values()), default=0.0)
        vector_used = False
        uncovered_span_fallback = (
            strongest >= 0.90
            and self._has_uncovered_query_span(query, candidates.values())
        )
        if (
            include_vector
            and (strongest < 0.90 or uncovered_span_fallback)
            and self.vector_index is not None
        ):
            vector_hits = self.vector_index.search(
                query,
                semantic_types=type_filter,
                allowed_codes=code_filter,
                top_k=max(top_k, 10),
                min_score=self.vector_threshold,
            )
            vector_used = bool(vector_hits)
            for hit in vector_hits:
                eligible, table_bonus = self._eligible(
                    hit.code,
                    hit.semantic_type,
                    semantic_types=type_filter,
                    allowed_codes=code_filter,
                    allowed_tables=table_filter,
                    strict_tables=strict_tables,
                )
                if not eligible:
                    continue
                # Vector-only matches remain medium confidence even for a high cosine score.
                if self.vector_index.provider.provider_id.startswith("hashing-"):
                    # Hashing n-gram cosine has a much lower scale than a
                    # sentence-transformer cosine. It can only produce a
                    # medium-confidence confirmation candidate, never auto.
                    score = min(0.90, 0.62 + max(0.0, hit.score) * 1.20 + table_bonus)
                else:
                    score = min(0.90, 0.50 + max(0.0, hit.score) * 0.40 + table_bonus)
                item = self.snapshot.items[hit.code]
                self._merge(candidates, RecallCandidate(
                    semantic_type=item.semantic_type,
                    code=item.code,
                    name=item.cn_name,
                    score=score,
                    match_type="vector",
                    tables=set(item.tables),
                    evidence=[MatchEvidence(
                        match_type="vector",
                        matched_text=query,
                        source=self.vector_index.provider.provider_id,
                        detail=f"余弦相似度 {hit.score:.3f}",
                        score=score,
                    )],
                    confidence="medium" if score >= 0.68 else "low",
                ))

        ranked = sorted(
            candidates.values(),
            key=lambda candidate: (-candidate.score, candidate.code, candidate.canonical_value),
        )[:max(1, int(top_k))]
        return RecallResponse(
            ranked,
            vector_used=vector_used,
            vector_disabled_reason=(
                self.vector_index.disabled_reason if self.vector_index is not None else ""
            ),
            diagnostics={
                "dictionaryCandidates": sum(
                    candidate.match_type not in {"fuzzy", "vector"} for candidate in ranked
                ),
                "fuzzyUsed": fuzzy_used,
                "vectorUsed": vector_used,
                "uncoveredSpanVectorFallback": uncovered_span_fallback,
            },
        )

    def search_values(
        self,
        query: str,
        *,
        allowed_dimension_codes: Iterable[str] | None = None,
        allowed_tables: Iterable[str] | None = None,
        strict_tables: bool = False,
        top_k: int = 20,
    ) -> RecallResponse:
        q_norm = normalize_text(query)
        if not q_norm:
            return RecallResponse([])
        allowed_dims = set(allowed_dimension_codes) if allowed_dimension_codes is not None else None
        table_filter = set(allowed_tables or ()) or None
        candidates: dict[tuple[str, str, str, str], RecallCandidate] = {}
        quoted_term_norms = {
            normalize_text(quoted)
            for quoted in re.findall(
                r"[\"'“‘]\s*([^\"'“‘”’]+?)\s*[\"'”’]", str(query)
            )
            if normalize_text(quoted)
        }
        chunks = list(dict.fromkeys([q_norm, *tokenize(query)]))
        chunk_features = {
            chunk: _char_bigrams(chunk) for chunk in chunks if len(chunk) >= 2
        }
        chunk_char_counts = {chunk: Counter(chunk) for chunk in chunks}
        chunks_by_bigram: dict[str, set[str]] = {}
        for chunk, grams in chunk_features.items():
            for gram in grams:
                chunks_by_bigram.setdefault(gram, set()).add(chunk)

        # Exact and phrase candidates are obtained through normalized-term
        # length lookups, while fuzzy candidates require at least one shared
        # bigram. This keeps a long no-match question independent of catalog
        # cardinality after the indexes have been built.
        candidate_norms: set[str] = set()
        if q_norm in self._value_terms_by_norm:
            candidate_norms.add(q_norm)
        for size in self._value_term_lengths:
            if size < 2 or size > len(q_norm):
                continue
            for start in range(len(q_norm) - size + 1):
                possible_term = q_norm[start:start + size]
                if possible_term in self._value_terms_by_norm:
                    candidate_norms.add(possible_term)
        for gram in _char_bigrams(q_norm):
            candidate_norms.update(self._value_norms_by_bigram.get(gram, ()))
        ordered_candidate_norms = sorted(
            candidate_norms, key=lambda term_norm: (-len(term_norm), term_norm)
        )

        fuzzy_chunks_cache: dict[str, list[str]] = {}
        phrase_presence_cache: dict[tuple[str, str], bool] = {}

        def value_phrase_present(term_record: _ValueTermRecord) -> bool:
            cache_key = (term_record.normalized_term, term_record.term)
            cached = phrase_presence_cache.get(cache_key)
            if cached is not None:
                return cached
            term_norm = term_record.normalized_term
            if _is_latin_or_numeric_term(term_record.term):
                present = _word_boundary_present(term_record.term, query)
            elif _CHINESE_TERM_RE.fullmatch(term_norm):
                present = (
                    term_norm in quoted_term_norms
                    or self._chinese_value_phrase_present(term_record.term, query)
                )
            else:
                present = term_norm in q_norm
            phrase_presence_cache[cache_key] = present
            return present

        def fuzzy_chunks(term_norm: str) -> list[str]:
            cached = fuzzy_chunks_cache.get(term_norm)
            if cached is not None:
                return cached
            grams = _char_bigrams(term_norm)
            pool: set[str] = set()
            for gram in grams:
                pool.update(chunks_by_bigram.get(gram, ()))
            ranked_pool = sorted(
                pool,
                key=lambda chunk: (
                    -len(grams & chunk_features.get(chunk, set())) / max(1, len(grams)),
                    abs(len(chunk) - len(term_norm)),
                    chunk,
                ),
            )[:12]
            fuzzy_chunks_cache[term_norm] = ranked_pool
            return ranked_pool

        dimension_mentioned: dict[str, bool] = {}
        matched_phrase_spans: dict[
            tuple[str, str], dict[str, list[tuple[int, int]]]
        ] = {}
        for term_norm in ordered_candidate_norms:
            for term_record in self._value_terms_by_norm[term_norm]:
                phrase_present = value_phrase_present(term_record)
                if (
                    term_norm == q_norm
                    or (len(term_norm) >= 2 and phrase_present)
                    or (
                        _CHINESE_TERM_RE.fullmatch(term_norm)
                        and term_norm in q_norm
                    )
                ):
                    identity = (
                        term_record.dimension_code,
                        term_record.canonical_value,
                    )
                    matched_phrase_spans.setdefault(identity, {})[term_norm] = (
                        _normalized_spans(term_norm, q_norm)
                    )

        for term_norm in ordered_candidate_norms:
            for term_record in self._value_terms_by_norm[term_norm]:
                if (
                    allowed_dims is not None
                    and term_record.dimension_code not in allowed_dims
                ):
                    continue
                identity = (
                    term_record.dimension_code,
                    term_record.canonical_value,
                )
                own_spans = matched_phrase_spans.get(identity, {}).get(
                    term_norm, []
                )
                longer_spans = [
                    span
                    for other_norm, spans in matched_phrase_spans.get(
                        identity, {}
                    ).items()
                    if len(other_norm) > len(term_norm)
                    for span in spans
                ]
                if own_spans and longer_spans and all(
                    any(
                        longer_start <= own_start and own_end <= longer_end
                        for longer_start, longer_end in longer_spans
                    )
                    for own_start, own_end in own_spans
                ):
                    continue
                if table_filter:
                    relevant_tables = set(term_record.tables) & table_filter
                    if strict_tables and not relevant_tables:
                        continue
                    table_bonus = 0.02 if relevant_tables else 0.0
                else:
                    relevant_tables = set(term_record.tables)
                    table_bonus = 0.0
                if not relevant_tables:
                    continue
                latin_or_numeric = _is_latin_or_numeric_term(term_record.term)
                requires_context = (
                    term_norm.isdigit()
                    or (latin_or_numeric and len(term_norm) <= 2)
                ) and term_norm != q_norm
                if requires_context:
                    if term_record.dimension_code not in dimension_mentioned:
                        dimension_mentioned[term_record.dimension_code] = any(
                            dimension_term in q_norm
                            for dimension_term in self._dimension_terms.get(
                                term_record.dimension_code, ()
                            )
                        )
                    if (
                        not dimension_mentioned[term_record.dimension_code]
                        and term_norm not in quoted_term_norms
                    ):
                        continue

                phrase_present = value_phrase_present(term_record)
                if term_norm == q_norm:
                    match_type = "exact_value"
                    score = term_record.weight
                elif len(term_norm) >= 2 and phrase_present:
                    match_type = "value_phrase"
                    score = term_record.weight
                else:
                    embedded_chinese = bool(
                        _CHINESE_TERM_RE.fullmatch(term_norm)
                        and term_norm in q_norm
                    )
                    if embedded_chinese:
                        # Keep a conservative confirmation candidate so the
                        # mapper can clarify overlapping governed values, but
                        # never grant high/auto confidence to a compound.
                        match_type = "fuzzy_value"
                        score = min(0.84, term_record.weight * 0.9)
                    else:
                        # Latin/numeric values are identifiers, not typo-tolerant
                        # prose: fuzzy matching must not bypass their word boundary.
                        if latin_or_numeric:
                            continue
                        viable_chunks = [
                            chunk for chunk in fuzzy_chunks(term_norm)
                            if _sequence_ratio_upper_bound(
                                self._value_term_char_counts[term_norm],
                                len(term_norm),
                                chunk_char_counts[chunk],
                                len(chunk),
                            ) >= 0.86
                        ]
                        ratio = max(
                            (
                                SequenceMatcher(None, chunk, term_norm).ratio()
                                for chunk in viable_chunks
                            ),
                            default=0.0,
                        )
                        if len(term_norm) < 3 or ratio < 0.86:
                            continue
                        match_type = "fuzzy_value"
                        score = min(0.84, ratio * term_record.weight * 0.9)
                score = min(1.0, score + table_bonus)
                evidence = MatchEvidence(
                    match_type=match_type,
                    matched_text=term_record.term,
                    source=term_record.source,
                    detail=(
                        f"{term_record.term} → {term_record.dimension_code}="
                        f"{term_record.canonical_value}"
                    ),
                    score=score,
                )
                self._merge(candidates, RecallCandidate(
                    semantic_type="value",
                    code=term_record.dimension_code,
                    name=term_record.canonical_value,
                    score=score,
                    match_type=match_type,
                    tables=set(relevant_tables),
                    evidence=[evidence],
                    canonical_value=term_record.canonical_value,
                    dimension_code=term_record.dimension_code,
                    confidence=self._confidence(score, match_type),
                    metadata={
                        "filterSafe": bool(
                            relevant_tables
                            and relevant_tables.issubset(term_record.safe_tables)
                        ),
                        "safeTables": sorted(term_record.safe_tables),
                    },
                ))

        ranked = sorted(
            candidates.values(),
            key=lambda candidate: (-candidate.score, candidate.dimension_code, candidate.canonical_value),
        )[:max(1, int(top_k))]
        return RecallResponse(ranked)
