"""Compose recall candidates into a constrained semantic mapping result."""

from __future__ import annotations

import time
import re
from typing import Iterable

from .models import (
    CatalogSnapshot,
    MatchEvidence,
    RecallCandidate,
    SemanticMatchResult,
    ValueBinding,
)
from .retriever import SemanticRetriever
from .normalizer import explicit_codes, normalize_text


_TIME_EXPRESSIONS = (
    re.compile(
        r"(?:最近|近|过去|未来|前|后)?"
        r"(?:\d+|[一二三四五六七八九十百两半]+)(?:个)?"
        r"(?:分钟|小时|天|日|周|星期|月|季度|季|年)"
    ),
    re.compile(
        r"(?:今天|今日|昨天|昨日|前天|明天|本周|这周|上周|下周|"
        r"本月|当月|这个月|上月|下月|本季度|这季度|上季度|下季度|"
        r"今年|本年|去年|明年|年初|年末|月初|月末|截至目前|当前)"
    ),
    re.compile(r"(?:19|20)\d{2}(?:年)?(?:\d{1,2}(?:月)?)?(?:\d{1,2}(?:日|号)?)?"),
    re.compile(
        r"(?:last|past|previous|next)(?:\d+|one|two|three|four|five|six|"
        r"seven|eight|nine|ten)?(?:day|week|month|quarter|year)s?",
        re.I,
    ),
    re.compile(r"(?:按|每)(?:分钟|小时|天|日|周|星期|月|季度|季|年)"),
    re.compile(
        r"(?:前|后)(?:\d+|[一二三四五六七八九十百]+)(?:名|个|条)?"
    ),
    re.compile(r"(?:top|bottom)\d+", re.I),
)

# These are query intent, analysis and presentation words, rather than business
# entities. Keep the list deliberately phrase-based: broad domain nouns (for
# example, customer/store/color) must remain visible to the fail-closed check.
_UNEXPLAINED_STOP_PHRASES = tuple(sorted({
    "请帮我深入分析", "请帮我分析", "麻烦帮我", "能否帮我", "可以帮我",
    "我想知道", "我想了解", "请帮我", "帮我看看", "帮我看下", "帮忙看下",
    "深入分析", "进一步分析", "分析一下", "查询一下", "查看一下", "看一下",
    "查一下", "统计一下", "计算一下", "展示一下", "给我看看", "给我看下",
    "持续下降", "持续下滑", "持续上涨", "持续增长", "业务原因", "可能的原因",
    "主要原因", "驱动因素", "主要因素", "后续改进建议", "改进建议",
    "为什么", "怎么回事", "有何变化", "是否异常", "有没有异常",
    "是多少", "有多少", "怎么样", "如何", "多少", "什么", "哪些",
    "查询", "查看", "查找", "搜索", "展示", "显示", "统计", "计算", "获取",
    "列出", "输出", "返回", "分析", "了解", "想知道", "想了解",
    "请问", "麻烦", "帮忙", "帮我", "给我", "看看", "看下",
    "同比环比", "同比", "环比", "同环比", "对比", "比较", "占比",
    "变化趋势", "发展趋势", "趋势", "走势", "变化", "变动", "波动",
    "持续", "下降", "下滑", "上涨", "增长", "提高", "降低", "异常",
    "原因", "影响", "表现", "情况", "现状", "结果", "数据",
    "可能的", "可能", "相关的", "相关", "业务", "后续", "改进", "建议",
    "按照", "基于", "分组", "分布", "分别", "汇总", "聚合", "明细",
    "详情", "列表", "排名", "排行", "最高", "最低", "最大", "最小",
    "平均值", "均值", "总和", "合计", "求和", "计数", "去重数", "中位数",
    "前十", "前五", "后十", "后五",
    "groupby", "breakdown", "top", "bottom", "show", "find", "query",
    "please", "help", "analyze", "analysis", "why", "what", "how", "much",
    "trend", "compare", "average", "avg", "sum", "total", "count",
    "business", "metric", "metrics", "data", "result", "results", "report",
    "不要分析", "只需要看", "继续看", "也看", "再看", "只需要", "只看",
    "不要", "以及", "并且", "然后",
    "排除", "不含", "不包括", "非属于", "以外", "之外", "除外",
    "without", "except", "exclude", "instead",
}, key=len, reverse=True))


def _ambiguous(candidates: list[RecallCandidate], *, gap: float = 0.06) -> bool:
    if len(candidates) < 2:
        return False
    top, second = candidates[0], candidates[1]
    exact_types = {"explicit_code", "exact_name", "exact_alias"}
    if top.match_type == "explicit_code" and second.match_type != "explicit_code":
        return False
    if top.match_type in exact_types and second.match_type not in exact_types:
        return False
    return second.score >= top.score - gap


def _shared_term_ambiguous(
    candidates: list[RecallCandidate], *, gap: float = 0.06
) -> bool:
    """Return true only when the same input span maps to different targets."""
    for index, candidate in enumerate(candidates):
        terms = {
            normalize_text(evidence.matched_text)
            for evidence in candidate.evidence
            if evidence.match_type != "vector" and normalize_text(evidence.matched_text)
        }
        if not terms:
            continue
        for other in candidates[index + 1:]:
            if other.score < candidate.score - gap:
                continue
            other_terms = {
                normalize_text(evidence.matched_text)
                for evidence in other.evidence
                if evidence.match_type != "vector" and normalize_text(evidence.matched_text)
            }
            if terms & other_terms and candidate.code != other.code:
                return True
    return False


def _normalized_spans(question_norm: str, term: str) -> list[tuple[int, int]]:
    normalized = normalize_text(term)
    spans: list[tuple[int, int]] = []
    if not normalized:
        return spans
    start = 0
    while True:
        index = question_norm.find(normalized, start)
        if index < 0:
            return spans
        spans.append((index, index + len(normalized)))
        start = index + 1


def _candidate_spans(
    candidate: RecallCandidate,
    question_norm: str,
) -> list[tuple[int, int]]:
    return [
        span
        for evidence in candidate.evidence
        if evidence.match_type != "vector"
        for span in _normalized_spans(question_norm, evidence.matched_text)
    ]


def _has_local_unsupported_value_operator(
    question: str,
    candidates: list[RecallCandidate],
) -> bool:
    """Fail closed only when an operator is local to a recalled value span."""
    question_norm = normalize_text(question)
    prefix_pattern = re.compile(
        r"(?:不要|别看|排除(?:掉)?|不是|不含|不包括|并非|"
        r"非(?:属于|是)?|不(?:是)?属于|除(?:了|开)?|"
        r"改(?:成|为)?|换(?:成|为)?|更正(?:成|为)?|纠正(?:成|为)?|"
        r"大于|小于|不少于|不多于|高于|低于|超过|至少|至多|"
        r"above|below|over|under|greaterthan|lessthan|"
        r"而是|not|without|except|exclude)$",
        re.I,
    )
    suffix_pattern = re.compile(
        r"^(?:以外|之外|除外|外|不对|而是|改(?:成|为)?|换(?:成|为)?|"
        r"更正(?:成|为)?|纠正(?:成|为)?|"
        r"及?以上|及?以下|大于|小于|不少于|不多于|高于|低于|"
        r"above|below|over|under|greaterthan|lessthan|"
        r"不要|别看|排除|不含|不包括|except|exclude|instead)",
        re.I,
    )
    for candidate in candidates:
        for start, end in _candidate_spans(candidate, question_norm):
            prefix = question_norm[max(0, start - 16):start]
            suffix = question_norm[end:end + 16]
            if prefix_pattern.search(prefix) or suffix_pattern.search(suffix):
                return True

    terms = list(dict.fromkeys(
        str(evidence.matched_text).strip()
        for candidate in candidates
        for evidence in candidate.evidence
        if str(evidence.matched_text).strip()
    ))
    for index, left in enumerate(terms):
        for right in terms[index + 1:]:
            escaped_left = re.escape(left)
            escaped_right = re.escape(right)
            range_separator = r"(?:到|至|[-—–~～])"
            if (
                re.search(
                    rf"{escaped_left}\s*{range_separator}\s*{escaped_right}",
                    question,
                    re.I,
                )
                or re.search(
                    rf"{escaped_right}\s*{range_separator}\s*{escaped_left}",
                    question,
                    re.I,
                )
                or re.search(
                    rf"\bbetween\s+{escaped_left}\s+and\s+{escaped_right}\b",
                    question,
                    re.I,
                )
                or re.search(
                    rf"\bbetween\s+{escaped_right}\s+and\s+{escaped_left}\b",
                    question,
                    re.I,
                )
            ):
                return True
    return False


def _has_cross_dimension_or(
    question: str,
    candidates: list[RecallCandidate],
) -> bool:
    question_norm = normalize_text(question)
    positioned = [
        (start, end, candidate.dimension_code)
        for candidate in candidates
        if candidate.confidence == "high"
        for start, end in _candidate_spans(candidate, question_norm)
    ]
    positioned.sort()
    for index, (left_start, left_end, left_dimension) in enumerate(positioned):
        for right_start, right_end, right_dimension in positioned[index + 1:]:
            if left_dimension == right_dimension:
                continue
            if left_end <= right_start and re.search(
                r"(?:或者|或|or)", question_norm[left_end:right_start], re.I
            ):
                return True
    return False


def _has_governed_value_dimension(
    snapshot: CatalogSnapshot,
    primary: RecallCandidate | None,
) -> bool:
    if primary is None:
        return False
    for value in snapshot.values:
        if value.sensitive:
            continue
        dimension = snapshot.items.get(value.dimension_code)
        if dimension is None or dimension.semantic_type != "dimension" or dimension.is_time:
            continue
        value_tables = set(value.tables) or set(dimension.tables)
        if value_tables & set(primary.tables) & set(dimension.tables):
            return True
    return False


def _unexplained_value_tokens(
    question: str,
    candidates: Iterable[RecallCandidate],
) -> list[str]:
    """Return business-like text not covered by trusted semantic evidence."""
    residual = _residual_after_trusted_evidence(question, candidates)

    tokens: list[str] = []
    seen: set[str] = set()
    for token in re.findall(
        r"[a-z][a-z0-9_-]+|[\u4e00-\u9fff]{2,}", residual, re.I
    ):
        normalized = normalize_text(token)
        if normalized and normalized not in seen:
            seen.add(normalized)
            tokens.append(normalized)
    return tokens


def _residual_after_trusted_evidence(
    text: str,
    candidates: Iterable[RecallCandidate],
) -> str:
    question_norm = normalize_text(text)
    if not question_norm:
        return ""

    visible = [True] * len(question_norm)
    for candidate in candidates:
        # Medium/low matches are not sufficiently trustworthy to explain away
        # text. Their own uncertainty may clarify the query, but they must not
        # let an otherwise unknown value silently auto-run.
        if candidate.confidence != "high":
            continue
        for start, end in _candidate_spans(candidate, question_norm):
            for index in range(start, min(end, len(visible))):
                visible[index] = False

    residual = "".join(
        character if visible[index] else " "
        for index, character in enumerate(question_norm)
    )
    for pattern in _TIME_EXPRESSIONS:
        residual = pattern.sub(" ", residual)
    for phrase in _UNEXPLAINED_STOP_PHRASES:
        residual = residual.replace(phrase, " ")
    return residual


def _explicit_unknown_value_slots(
    question: str,
    dimensions: list[RecallCandidate],
    evidence_candidates: Iterable[RecallCandidate],
) -> list[str]:
    """Find ungoverned values in quotes or an explicit dimension assignment."""
    slots = [
        match.group(1)
        for match in re.finditer(
            r"[\"'“‘]\s*([^\"'”’]{1,80}?)\s*[\"'”’]",
            question,
        )
    ]

    dimension_terms = sorted({
        term
        for candidate in dimensions
        if candidate.confidence == "high"
        and candidate.match_type != "value_implied_dimension"
        for term in [
            candidate.code,
            candidate.name,
            *[
                str(evidence.matched_text)
                for evidence in candidate.evidence
                if evidence.match_type != "vector"
            ],
        ]
        if str(term).strip()
    }, key=len, reverse=True)
    for term in dimension_terms:
        match = re.search(
            rf"(?<![A-Za-z0-9_]){re.escape(term)}(?![A-Za-z0-9_])\s*"
            rf"(?:==|=|等于|属于|为|是)\s*"
            rf"(?:[\"'“‘]\s*)?"
            rf"([A-Za-z0-9][A-Za-z0-9_-]*|[\u4e00-\u9fff][\u4e00-\u9fffA-Za-z0-9_-]*)",
            question,
            re.I,
        )
        if match:
            slots.append(match.group(1))

    unresolved: list[str] = []
    seen: set[str] = set()
    trusted_candidates = list(evidence_candidates)
    for slot in slots:
        residual = _residual_after_trusted_evidence(slot, trusted_candidates)
        residual = re.sub(r"[的地得和与及或并且中内里]+", " ", residual)
        fragment = normalize_text(residual)
        if fragment and fragment not in seen:
            seen.add(fragment)
            unresolved.append(fragment)
    return unresolved


class SemanticMapper:
    def __init__(self, snapshot: CatalogSnapshot, retriever: SemanticRetriever) -> None:
        self.snapshot = snapshot
        self.retriever = retriever

    def map(
        self,
        question: str,
        *,
        allowed_measure_codes: Iterable[str] | None = None,
        allowed_dimension_codes: Iterable[str] | None = None,
        preferred_tables: Iterable[str] | None = None,
        assumed_measure_code: str | None = None,
        top_k: int = 10,
        include_vector: bool = True,
    ) -> SemanticMatchResult:
        started = time.perf_counter()
        preferred = set(preferred_tables or ())
        allowed_measure_set = (
            set(allowed_measure_codes) if allowed_measure_codes is not None else None
        )
        allowed_dimension_set = (
            set(allowed_dimension_codes) if allowed_dimension_codes is not None else None
        )
        requested_top_k = max(1, int(top_k))
        safety_top_k = max(50, requested_top_k)
        measure_response = self.retriever.search(
            question,
            semantic_types={"measure"},
            allowed_codes=allowed_measure_set,
            allowed_tables=preferred,
            strict_tables=False,
            top_k=safety_top_k,
            include_vector=include_vector,
        )
        measures = list(measure_response.candidates)
        assumed_primary_applied = False
        assumed_code_upper = str(assumed_measure_code or "").strip().upper()
        if assumed_code_upper:
            assumed_item = next((
                item for code, item in self.snapshot.items.items()
                if code.upper() == assumed_code_upper and item.semantic_type == "measure"
            ), None)
            allowed_upper = (
                {code.upper() for code in allowed_measure_set}
                if allowed_measure_set is not None else None
            )
            assumed_tables = set(assumed_item.tables) if assumed_item is not None else set()
            if preferred:
                assumed_tables &= preferred
            if (
                assumed_item is not None
                and assumed_tables
                and (allowed_upper is None or assumed_code_upper in allowed_upper)
            ):
                measures = [
                    RecallCandidate(
                        semantic_type="measure",
                        code=assumed_item.code,
                        name=assumed_item.cn_name,
                        score=1.0,
                        match_type="inherited_context",
                        tables=assumed_tables,
                        evidence=[MatchEvidence(
                            match_type="inherited_context",
                            matched_text="",
                            source="inherited_context",
                            detail=f"沿用上一轮指标 {assumed_item.code}",
                            score=1.0,
                        )],
                        confidence="high",
                    ),
                    *[
                        candidate for candidate in measures
                        if candidate.code.upper() != assumed_code_upper
                    ],
                ]
                assumed_primary_applied = True
        primary = measures[0] if measures else None
        compatible_tables = set(primary.tables) if primary else preferred

        dimension_codes = set(allowed_dimension_set) if allowed_dimension_set is not None else None
        if primary:
            structurally_compatible = {
                item.code
                for item in self.snapshot.items.values()
                if item.semantic_type == "dimension" and item.tables & primary.tables
            }
            dimension_codes = (
                structurally_compatible
                if dimension_codes is None
                else dimension_codes & structurally_compatible
            )
        dimension_response = self.retriever.search(
            question,
            semantic_types={"dimension"},
            allowed_codes=dimension_codes,
            allowed_tables=compatible_tables,
            strict_tables=bool(primary),
            top_k=safety_top_k,
            include_vector=include_vector,
        )
        dimensions = list(dimension_response.candidates)
        dimension_requested = bool(
            re.search(r"按|分组|分布|维度|各|每|group\s*by", question, re.I)
        )
        suppressed_dimension_overlap = 0
        suppressed_dimension_codes: list[str] = []
        if primary is not None and not dimension_requested:
            q_norm = normalize_text(question)
            primary_spans = _candidate_spans(primary, q_norm)
            retained_dimensions: list[RecallCandidate] = []
            for candidate in dimensions:
                dimension_spans = _candidate_spans(candidate, q_norm)
                fully_covered_by_measure = bool(dimension_spans) and all(
                    any(
                        measure_start <= dimension_start
                        and dimension_end <= measure_end
                        for measure_start, measure_end in primary_spans
                    )
                    for dimension_start, dimension_end in dimension_spans
                )
                if fully_covered_by_measure:
                    suppressed_dimension_overlap += 1
                    suppressed_dimension_codes.append(candidate.code)
                    continue
                retained_dimensions.append(candidate)
            dimensions = retained_dimensions

        value_response = self.retriever.search_values(
            question,
            allowed_dimension_codes=dimension_codes,
            allowed_tables=compatible_tables,
            strict_tables=bool(primary),
            top_k=safety_top_k,
        )
        value_candidates = list(value_response.candidates)
        suppressed_measure_overlap = 0
        if value_candidates:
            q_norm = normalize_text(question)

            entity_spans: list[tuple[int, int]] = []
            for entity_candidate in [*(measures[:1]), *dimensions]:
                for evidence in entity_candidate.evidence:
                    if evidence.match_type == "vector":
                        continue
                    entity_spans.extend(_normalized_spans(q_norm, evidence.matched_text))

            filtered_values: list[RecallCandidate] = []
            for candidate in value_candidates:
                explicitly_quoted = any(
                    re.search(
                        rf"[\"'“‘]\s*{re.escape(str(evidence.matched_text))}\s*[\"'”’]",
                        question,
                    )
                    for evidence in candidate.evidence
                )
                value_spans = [
                    span
                    for evidence in candidate.evidence
                    for span in _normalized_spans(q_norm, evidence.matched_text)
                ]
                fully_covered_by_entity = bool(value_spans) and all(
                    any(
                        entity_start <= value_start and value_end <= entity_end
                        for entity_start, entity_end in entity_spans
                    )
                    for value_start, value_end in value_spans
                )
                if fully_covered_by_entity and not explicitly_quoted:
                    suppressed_measure_overlap += 1
                    continue
                filtered_values.append(candidate)
            value_candidates = filtered_values
        value_bindings = [
            ValueBinding(
                dimension_code=candidate.dimension_code,
                input_text=(candidate.evidence[0].matched_text if candidate.evidence else candidate.name),
                canonical_value=candidate.canonical_value,
                score=candidate.score,
                confidence=candidate.confidence,
                source=(candidate.evidence[0].source if candidate.evidence else "catalog"),
                filter_safe=bool(candidate.metadata.get("filterSafe") and candidate.tables),
                applicable_tables=set(candidate.tables),
                evidence=list(candidate.evidence),
            )
            for candidate in value_candidates
        ]

        dimension_by_code = {candidate.code: candidate for candidate in dimensions}
        for value_candidate in value_candidates:
            if value_candidate.dimension_code in dimension_by_code:
                existing = dimension_by_code[value_candidate.dimension_code]
                implied_evidence = MatchEvidence(
                    match_type="value_implied_dimension",
                    matched_text=(
                        value_candidate.evidence[0].matched_text
                        if value_candidate.evidence else value_candidate.canonical_value
                    ),
                    source="value_index",
                    detail=f"维值属于 {existing.code}",
                    score=max(0.0, value_candidate.score - 0.01),
                )
                if existing.match_type == "vector" or existing.confidence != "high":
                    existing.match_type = "value_implied_dimension"
                    existing.confidence = value_candidate.confidence
                    existing.evidence = [
                        implied_evidence,
                        *existing.evidence,
                        *value_candidate.evidence,
                    ]
                else:
                    existing.evidence.extend(value_candidate.evidence)
                existing.tables = set(value_candidate.tables)
                existing.score = max(existing.score, min(0.96, value_candidate.score - 0.01))
                continue
            item = self.snapshot.items.get(value_candidate.dimension_code)
            if item is None:
                continue
            candidate = RecallCandidate(
                semantic_type="dimension",
                code=item.code,
                name=item.cn_name,
                score=max(0.0, min(0.96, value_candidate.score - 0.01)),
                match_type="value_implied_dimension",
                tables=set(value_candidate.tables),
                evidence=[
                    MatchEvidence(
                        match_type="value_implied_dimension",
                        matched_text=value_candidate.canonical_value,
                        source="value_index",
                        detail=f"维值属于 {item.code}",
                        score=max(0.0, value_candidate.score - 0.01),
                    ),
                    *value_candidate.evidence,
                ],
                confidence=value_candidate.confidence,
            )
            dimensions.append(candidate)
            dimension_by_code[candidate.code] = candidate
        high_value_implied = any(
            candidate.match_type == "value_implied_dimension"
            and candidate.confidence == "high"
            for candidate in dimensions
        )
        trusted_nonvector_dimensions = [
            candidate for candidate in dimensions
            if candidate.match_type != "vector"
        ]
        uncovered_dimension_tokens = _unexplained_value_tokens(
            question,
            [*measures, *trusted_nonvector_dimensions, *value_candidates],
        )
        suppressed_vector_dimension_codes: list[str] = []
        if (
            high_value_implied
            and not uncovered_dimension_tokens
            and not bool(
                dimension_response.diagnostics.get("uncoveredSpanVectorFallback")
            )
        ):
            retained_dimensions = []
            for candidate in dimensions:
                pure_vector = bool(candidate.evidence) and all(
                    evidence.match_type == "vector"
                    for evidence in candidate.evidence
                )
                if candidate.match_type == "vector" and pure_vector:
                    suppressed_vector_dimension_codes.append(candidate.code)
                    continue
                retained_dimensions.append(candidate)
            dimensions = retained_dimensions
        dimensions.sort(key=lambda candidate: (-candidate.score, candidate.code))

        q_norm = normalize_text(question)
        catalog_codes_upper = {code.upper() for code in self.snapshot.items}
        allowed_measure_codes_upper = (
            {code.upper() for code in allowed_measure_set}
            if allowed_measure_set is not None else None
        )
        allowed_dimension_codes_upper = (
            {code.upper() for code in allowed_dimension_set}
            if allowed_dimension_set is not None else None
        )
        recalled_measure_codes = {candidate.code.upper() for candidate in measures}
        recalled_dimension_codes = {candidate.code.upper() for candidate in dimensions}
        unknown_explicit_codes = sorted(
            code
            for code in (
                explicit_codes(question, "MEAS")
                | explicit_codes(question, "DIM")
            )
            if (
                code not in catalog_codes_upper
                or (
                    code.startswith("MEAS_")
                    and (
                        (
                            allowed_measure_codes_upper is not None
                            and code not in allowed_measure_codes_upper
                        )
                        or code not in recalled_measure_codes
                    )
                )
                or (
                    code.startswith("DIM_")
                    and (
                        (
                            allowed_dimension_codes_upper is not None
                            and code not in allowed_dimension_codes_upper
                        )
                        or code not in recalled_dimension_codes
                    )
                )
            )
        )
        measure_multi_entity = False
        potential_additional_measure = False
        if measures:
            primary_spans = _candidate_spans(measures[0], q_norm)
            for candidate in measures[1:]:
                if candidate.confidence not in {"high", "medium"}:
                    continue
                candidate_spans = _candidate_spans(candidate, q_norm)
                vector_only = bool(candidate.evidence) and all(
                    evidence.match_type == "vector"
                    for evidence in candidate.evidence
                )
                if (
                    vector_only
                    and candidate.code != measures[0].code
                    and candidate.confidence == "medium"
                    and bool(
                        measure_response.diagnostics.get(
                            "uncoveredSpanVectorFallback"
                        )
                    )
                ):
                    potential_additional_measure = True
                    break
                if any(
                    not any(
                        primary_start <= start and end <= primary_end
                        for primary_start, primary_end in primary_spans
                    )
                    for start, end in candidate_spans
                ):
                    measure_multi_entity = True
                    break
        measure_ambiguous = (
            _ambiguous(measures)
            or measure_multi_entity
            or potential_additional_measure
        )
        dimension_ambiguous = _shared_term_ambiguous(dimensions)
        high_dimension_terms = {
            normalize_text(evidence.matched_text)
            for candidate in dimensions
            if candidate.confidence == "high"
            for evidence in candidate.evidence
            if evidence.match_type != "vector" and normalize_text(evidence.matched_text)
        }
        uncovered_medium_dimension = any(
            candidate.confidence == "medium"
            and (
                candidate.match_type != "vector" or not high_dimension_terms
            )
            and any(
                normalize_text(evidence.matched_text)
                and not any(
                    normalize_text(evidence.matched_text) in high_term
                    for high_term in high_dimension_terms
                )
                for evidence in candidate.evidence
                if evidence.match_type != "vector"
            )
            for candidate in dimensions
        )
        dimension_uncertain = bool(
            any(candidate.confidence == "medium" for candidate in dimensions)
            or (
                dimension_requested
                and dimensions
                and not any(candidate.confidence == "high" for candidate in dimensions)
            )
            or uncovered_medium_dimension
        )
        evidence_candidates = [*measures, *dimensions, *value_candidates]
        unexplained_tokens = _unexplained_value_tokens(question, evidence_candidates)
        unresolved_value_slots = _explicit_unknown_value_slots(
            question,
            dimensions,
            evidence_candidates,
        )
        unresolved_value_intent = bool(
            (unexplained_tokens or unresolved_value_slots)
            and _has_governed_value_dimension(self.snapshot, primary)
        )
        value_ambiguous = False
        values_by_input: dict[str, list[RecallCandidate]] = {}
        for candidate in value_candidates:
            matched_text = candidate.evidence[0].matched_text if candidate.evidence else candidate.name
            values_by_input.setdefault(normalize_text(matched_text), []).append(candidate)
        for same_input in values_by_input.values():
            if len(same_input) < 2:
                continue
            same_input.sort(key=lambda candidate: (-candidate.score, candidate.dimension_code))
            top_value = same_input[0]
            if any(
                (
                    candidate.dimension_code != top_value.dimension_code
                    or normalize_text(candidate.canonical_value)
                    != normalize_text(top_value.canonical_value)
                )
                and candidate.score >= top_value.score - 0.05
                for candidate in same_input[1:]
            ):
                value_ambiguous = True
                break
        if not value_ambiguous and len(value_candidates) > 1:
            q_norm = normalize_text(question)

            def _spans(candidate: RecallCandidate) -> list[tuple[int, int]]:
                spans: list[tuple[int, int]] = []
                for evidence in candidate.evidence:
                    term = normalize_text(evidence.matched_text)
                    if not term:
                        continue
                    start = 0
                    while True:
                        index = q_norm.find(term, start)
                        if index < 0:
                            break
                        spans.append((index, index + len(term)))
                        start = index + 1
                return spans

            for index, candidate in enumerate(value_candidates):
                candidate_spans = _spans(candidate)
                for other in value_candidates[index + 1:]:
                    if other.score < candidate.score - 0.05:
                        continue
                    different_target = (
                        candidate.dimension_code != other.dimension_code
                        or normalize_text(candidate.canonical_value)
                        != normalize_text(other.canonical_value)
                    )
                    if not different_target:
                        continue
                    if any(
                        left_start < right_end and right_start < left_end
                        for left_start, left_end in candidate_spans
                        for right_start, right_end in _spans(other)
                    ):
                        value_ambiguous = True
                        break
                if value_ambiguous:
                    break
        value_uncertain = any(candidate.confidence != "high" for candidate in value_candidates)
        value_unsafe = any(not binding.filter_safe for binding in value_bindings)
        execution_tables = set(primary.tables) if primary is not None else set()
        high_dimensions = [
            candidate for candidate in dimensions if candidate.confidence == "high"
        ]
        for candidate in high_dimensions:
            execution_tables &= set(candidate.tables)
        dimension_table_incompatible = bool(
            primary is not None and high_dimensions and not execution_tables
        )
        value_table_incompatible = bool(
            value_bindings
            and (
                not execution_tables
                or any(
                    not execution_tables.issubset(binding.applicable_tables)
                    for binding in value_bindings
                    if binding.confidence == "high"
                )
            )
        )
        unsupported_value_operator = bool(
            value_candidates
            and _has_local_unsupported_value_operator(question, value_candidates)
        )
        cross_dimension_or = _has_cross_dimension_or(question, value_candidates)

        if primary is None:
            confidence = "low"
            decision = "reject"
            needs_clarification = False
        elif (
            measure_ambiguous
            or dimension_ambiguous
            or dimension_uncertain
            or value_ambiguous
            or value_uncertain
            or value_unsafe
            or dimension_table_incompatible
            or value_table_incompatible
            or unsupported_value_operator
            or cross_dimension_or
            or unresolved_value_intent
            or unknown_explicit_codes
        ):
            confidence = "medium"
            decision = "clarify"
            needs_clarification = True
        elif primary.confidence == "high":
            confidence = "high"
            decision = "auto"
            needs_clarification = False
        elif primary.confidence == "medium":
            confidence = "medium"
            decision = "clarify"
            needs_clarification = True
        else:
            confidence = "low"
            decision = "reject"
            needs_clarification = False

        diagnostics = {
            "measureAmbiguous": measure_ambiguous,
            "multipleMeasureEntities": measure_multi_entity,
            "potentialAdditionalMeasure": potential_additional_measure,
            "dimensionAmbiguous": dimension_ambiguous,
            "dimensionUncertain": dimension_uncertain,
            "valueAmbiguous": value_ambiguous,
            "valueUncertain": value_uncertain,
            "valueUnsafe": value_unsafe,
            "dimensionTableIncompatible": dimension_table_incompatible,
            "valueTableIncompatible": value_table_incompatible,
            "unsupportedValueOperator": unsupported_value_operator,
            "crossDimensionOrUnsupported": cross_dimension_or,
            "unresolvedValueIntent": unresolved_value_intent,
            "unexplainedTokens": unexplained_tokens,
            "unresolvedValueSlots": unresolved_value_slots,
            "unknownExplicitCodes": unknown_explicit_codes,
            "assumedPrimary": (
                {"code": primary.code, "source": "inherited_context"}
                if assumed_primary_applied and primary is not None else None
            ),
            "suppressedMeasureOverlapValues": suppressed_measure_overlap,
            "suppressedEntityOverlapValues": suppressed_measure_overlap,
            "suppressedMeasureOverlapDimensions": suppressed_dimension_overlap,
            "suppressedMeasureOverlapDimensionCodes": sorted(
                set(suppressed_dimension_codes)
            ),
            "suppressedVectorDimensionCodes": sorted(
                set(suppressed_vector_dimension_codes)
            ),
            "preferredTables": sorted(preferred),
            "compatibleTables": sorted(
                execution_tables if primary is not None else compatible_tables
            ),
            "vectorUsed": bool(
                measure_response.vector_used or dimension_response.vector_used
            ),
            "vectorDisabledReason": (
                measure_response.vector_disabled_reason
                or dimension_response.vector_disabled_reason
            ),
            "measureRecall": measure_response.diagnostics,
            "dimensionRecall": dimension_response.diagnostics,
        }
        return SemanticMatchResult(
            question=question,
            measure_candidates=measures[:requested_top_k],
            dimension_candidates=dimensions[:requested_top_k],
            value_bindings=value_bindings,
            confidence=confidence,
            decision=decision,
            needs_clarification=needs_clarification,
            snapshot_key=self.snapshot.snapshot_key,
            elapsed_ms=int((time.perf_counter() - started) * 1000),
            diagnostics=diagnostics,
        )
