import time

import numpy as np

from kg_builder.semantic_retrieval.embedding import SemanticVectorIndex
from kg_builder.semantic_retrieval.models import (
    AliasRecord,
    CatalogItem,
    CatalogSnapshot,
    CatalogValue,
)
from kg_builder.semantic_retrieval.retriever import SemanticRetriever


def _snapshot(
    *,
    items: dict[str, CatalogItem],
    aliases: list[AliasRecord],
    values: list[CatalogValue] | None = None,
) -> CatalogSnapshot:
    return CatalogSnapshot(
        version="edges",
        graph_hash="graph",
        source_graph_hash="source",
        dictionary_hash="dictionary",
        items=items,
        values=values or [],
        aliases=aliases,
        generated_at="now",
    )


def test_latin_entity_aliases_require_original_word_boundaries() -> None:
    items = {
        "MEAS_app": CatalogItem("MEAS_app", "measure", "app"),
        "MEAS_sales": CatalogItem("MEAS_sales", "measure", "sales"),
        "DIM_us": CatalogItem("DIM_us", "dimension", "US"),
    }
    aliases = [
        AliasRecord("app", "app", "measure", "MEAS_app", "kg_name", 0.98),
        AliasRecord(
            "sales", "sales", "measure", "MEAS_sales", "kg_name", 0.98
        ),
        AliasRecord("US", "us", "dimension", "DIM_us", "kg_name", 0.98),
    ]
    retriever = SemanticRetriever(_snapshot(items=items, aliases=aliases))

    assert retriever.search(
        "application business wholesales",
        include_vector=False,
    ).candidates == []
    positive = retriever.search("use app for US sales", include_vector=False)
    assert {candidate.code for candidate in positive.candidates} == {
        "MEAS_app",
        "MEAS_sales",
        "DIM_us",
    }


def test_four_character_adjacent_transposition_reaches_medium_fuzzy_match() -> None:
    item = CatalogItem(
        "DIM_segment",
        "dimension",
        "客户分层",
        tables=frozenset({"fact_sales"}),
    )
    alias = AliasRecord(
        "客户分层",
        "客户分层",
        "dimension",
        "DIM_segment",
        "kg_name",
        0.98,
    )
    retriever = SemanticRetriever(
        _snapshot(items={item.code: item}, aliases=[alias])
    )

    result = retriever.search(
        "客分户层",
        semantic_types={"dimension"},
        include_vector=False,
    )

    assert result.candidates[0].code == "DIM_segment"
    assert result.candidates[0].match_type == "fuzzy"
    assert result.candidates[0].confidence == "medium"
    assert "0.750" in result.candidates[0].evidence[0].detail


def test_value_indexes_preserve_alias_latin_and_table_scope_rules() -> None:
    items = {
        "DIM_country": CatalogItem(
            "DIM_country",
            "dimension",
            "国家",
            tables=frozenset({"fact_sales"}),
        ),
        "DIM_region": CatalogItem(
            "DIM_region",
            "dimension",
            "区域",
            tables=frozenset({"fact_one", "fact_two"}),
        ),
    }
    aliases = [
        AliasRecord("国家", "国家", "dimension", "DIM_country", "kg_name", 0.98),
        AliasRecord("区域", "区域", "dimension", "DIM_region", "kg_name", 0.98),
    ]
    values = [
        CatalogValue(
            dimension_code="DIM_country",
            canonical_value="US",
            aliases=("America",),
            tables=frozenset({"fact_sales"}),
            metadata={
                "safeTables": ["fact_sales"],
                "aliasSafeTables": {"america": ["fact_sales"]},
            },
        ),
        CatalogValue(
            dimension_code="DIM_region",
            canonical_value="EAST",
            aliases=("东区", "单表东区", "销售区", "库存区"),
            tables=frozenset({"fact_one", "fact_two"}),
            metadata={
                "aliasTables": {
                    "东区": ["fact_one"],
                    "单表东区": ["fact_two"],
                    "销售区": ["fact_one"],
                    "库存区": ["fact_two"],
                },
                "aliasSafeTables": {
                    "东区": ["fact_one"],
                    "单表东区": ["fact_two"],
                    "销售区": ["fact_one"],
                    "库存区": ["fact_two"],
                },
            },
        ),
    ]
    retriever = SemanticRetriever(
        _snapshot(items=items, aliases=aliases, values=values)
    )

    assert retriever.search_values("US").candidates[0].canonical_value == "US"
    assert retriever.search_values("国家 America").candidates[0].canonical_value == "US"
    assert retriever.search_values("business application").candidates == []
    assert retriever.search_values(
        "国家 US", allowed_tables={"fact_two"}
    ).candidates == []

    maximal = retriever.search_values(
        "区域 单表东区", allowed_tables={"fact_one", "fact_two"}
    ).candidates[0]
    assert maximal.tables == {"fact_two"}
    assert [item.matched_text for item in maximal.evidence] == ["单表东区"]
    assert maximal.metadata["filterSafe"] is True

    independent = retriever.search_values(
        "区域 单表东区和东区", allowed_tables={"fact_one", "fact_two"}
    ).candidates[0]
    assert independent.tables == set()
    assert {item.matched_text for item in independent.evidence} == {"东区", "单表东区"}
    assert independent.metadata["filterSafe"] is False

    disjoint = retriever.search_values(
        "区域 销售区和库存区", allowed_tables={"fact_one", "fact_two"}
    ).candidates[0]
    assert disjoint.tables == set()
    assert disjoint.metadata["filterSafe"] is False


def test_search_values_5000_value_no_match_is_under_one_second() -> None:
    item = CatalogItem(
        "DIM_perf",
        "dimension",
        "性能维度",
        tables=frozenset({"fact_perf"}),
    )
    alias = AliasRecord(
        "性能维度",
        "性能维度",
        "dimension",
        "DIM_perf",
        "kg_name",
        0.98,
    )
    values = [
        CatalogValue(
            dimension_code="DIM_perf",
            canonical_value=f"枚举值{index:04d}",
            tables=frozenset({"fact_perf"}),
        )
        for index in range(5000)
    ]
    retriever = SemanticRetriever(
        _snapshot(items={item.code: item}, aliases=[alias], values=values)
    )
    question = (
        "这是一个完全不同且不会命中任何枚举内容的超长分析问题"
        "需要讨论趋势原因风险以及未来行动建议"
    ) * 12

    started = time.perf_counter()
    result = retriever.search_values(question)
    elapsed = time.perf_counter() - started

    assert len(question) >= 500
    assert result.candidates == []
    assert elapsed < 1.0


def test_chinese_value_substrings_require_standalone_or_catalog_context() -> None:
    items = {
        "MEAS_gmv": CatalogItem("MEAS_gmv", "measure", "成交额"),
        "MEAS_coupon": CatalogItem(
            "MEAS_coupon", "measure", "优惠券转化率"
        ),
        "DIM_region": CatalogItem("DIM_region", "dimension", "区域"),
    }
    aliases = [
        AliasRecord("成交额", "成交额", "measure", "MEAS_gmv", "kg_name", 0.98),
        AliasRecord(
            "优惠券转化率", "优惠券转化率", "measure", "MEAS_coupon", "kg_name", 0.98
        ),
        AliasRecord("区域", "区域", "dimension", "DIM_region", "kg_name", 0.98),
    ]
    values = [
        CatalogValue(
            dimension_code="DIM_region",
            canonical_value="华东",
            aliases=("东区",),
            tables=frozenset({"fact_sales"}),
            metadata={"safeTables": ["fact_sales"]},
        ),
        CatalogValue(
            dimension_code="DIM_region",
            canonical_value="华南",
            aliases=("南区",),
            tables=frozenset({"fact_sales"}),
            metadata={"safeTables": ["fact_sales"]},
        ),
    ]
    retriever = SemanticRetriever(
        _snapshot(items=items, aliases=aliases, values=values)
    )

    assert retriever.search_values("东区").candidates[0].confidence == "high"
    assert retriever.search_values("查询成交额东区").candidates[0].canonical_value == "华东"
    assert retriever.search_values("东区优惠券转化率").candidates[0].confidence == "high"
    for conversational in ("南区呢", "那南区呢"):
        assert retriever.search_values(conversational).candidates[0].confidence == "high"
    for compound in ("华东区", "大东区", "东区部优惠券转化率"):
        embedded = retriever.search_values(compound).candidates[0]
        assert embedded.match_type == "fuzzy_value", compound
        assert embedded.confidence != "high", compound


class _SecondSpanEmbeddingProvider:
    provider_id = "fake-second-span-v1"

    def __init__(self) -> None:
        self.calls = 0

    def encode(self, texts):
        self.calls += 1
        rows = []
        for text in texts:
            if "复购率" in text or "增长动能" in text:
                rows.append([0.0, 1.0])
            else:
                rows.append([1.0, 0.0])
        return np.asarray(rows, dtype=np.float32)


def test_exact_entity_does_not_block_vector_for_uncovered_second_span(tmp_path) -> None:
    items = {
        "MEAS_gmv": CatalogItem("MEAS_gmv", "measure", "成交额"),
        "MEAS_retention": CatalogItem("MEAS_retention", "measure", "复购率"),
    }
    aliases = [
        AliasRecord("成交额", "成交额", "measure", "MEAS_gmv", "kg_name", 0.98),
        AliasRecord("复购率", "复购率", "measure", "MEAS_retention", "kg_name", 0.98),
    ]
    snapshot = _snapshot(items=items, aliases=aliases)
    provider = _SecondSpanEmbeddingProvider()
    retriever = SemanticRetriever(
        snapshot,
        vector_index=SemanticVectorIndex(snapshot, provider, tmp_path / "vectors"),
    )

    exact_only = retriever.search(
        "查询成交额为什么持续下降", semantic_types={"measure"}
    )
    assert exact_only.vector_used is False
    assert provider.calls == 0

    combined = retriever.search(
        "查询成交额和增长动能", semantic_types={"measure"}
    )
    assert combined.vector_used is True
    assert combined.diagnostics["uncoveredSpanVectorFallback"] is True
    assert {candidate.code for candidate in combined.candidates} == {
        "MEAS_gmv", "MEAS_retention",
    }
    assert next(
        candidate for candidate in combined.candidates
        if candidate.code == "MEAS_retention"
    ).match_type == "vector"


def test_entity_maximal_munch_suppresses_only_fully_covered_short_name() -> None:
    items = {
        "DIM_store": CatalogItem("DIM_store", "dimension", "门店"),
        "DIM_store_type": CatalogItem(
            "DIM_store_type", "dimension", "门店类型"
        ),
    }
    aliases = [
        AliasRecord("门店", "门店", "dimension", "DIM_store", "kg_name", 0.98),
        AliasRecord(
            "门店类型", "门店类型", "dimension", "DIM_store_type", "kg_name", 0.98
        ),
    ]
    retriever = SemanticRetriever(_snapshot(items=items, aliases=aliases))

    nested = retriever.search(
        "按门店类型查询", semantic_types={"dimension"}, include_vector=False
    )
    assert [candidate.code for candidate in nested.candidates] == ["DIM_store_type"]

    independent = retriever.search(
        "按门店和门店类型查询",
        semantic_types={"dimension"},
        include_vector=False,
    )
    assert {candidate.code for candidate in independent.candidates} == {
        "DIM_store", "DIM_store_type",
    }
    assert all(candidate.confidence == "high" for candidate in independent.candidates)
