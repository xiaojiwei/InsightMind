import os
from pathlib import Path
import time

import numpy as np
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from kg_builder.analysis.insight_analyzer import InsightAnalyzer
from kg_builder.nlq.service import NaturalLanguageQueryService
from kg_builder.semantic_retrieval import SemanticMappingConfig, SemanticMappingService
from kg_builder.semantic_retrieval.dictionary import load_dictionary
from kg_builder.semantic_retrieval.models import AliasRecord, CatalogItem, CatalogSnapshot
from kg_builder.semantic_retrieval.retriever import SemanticRetriever
from kg_builder.semantic_retrieval.router import create_semantic_retrieval_router


TTL = """
@prefix ind: <http://indicator.insightmind.com/ontology#> .
@prefix inst: <http://indicator.insightmind.com/instance/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

inst:m_gmv a ind:Measure ;
  ind:code "MEAS_gmv" ; ind:cnName "商品交易总额" ;
  ind:enName "gross_merchandise_value" ; ind:definition "成交商品的总金额" ;
  ind:hasMeasureApp inst:ma_gmv .
inst:ma_gmv a ind:MeasureApp ; ind:appliesToTable inst:fact_sales .

inst:m_net a ind:Measure ;
  ind:code "MEAS_net_sales" ; ind:cnName "净销售额" ;
  ind:definition "扣除退款后的销售金额" ; ind:hasMeasureApp inst:ma_net .
inst:ma_net a ind:MeasureApp ; ind:appliesToTable inst:fact_sales .

inst:m_offline a ind:Measure ;
  ind:code "MEAS_offline" ; ind:cnName "下线指标" ; ind:online 0 ;
  ind:hasMeasureApp inst:ma_offline .
inst:ma_offline a ind:MeasureApp ; ind:appliesToTable inst:fact_sales .

inst:d_region a ind:Dimension ;
  ind:code "DIM_region" ; ind:cnName "区域" ; ind:viewTypeCode 0 ;
  ind:hasDimApp inst:da_region .
inst:da_region a ind:DimensionApp ; ind:dimFactTable inst:fact_sales ;
  ind:dimFactColumn "region_name" .

inst:d_channel a ind:Dimension ;
  ind:code "DIM_channel" ; ind:cnName "渠道" ; ind:viewTypeCode 0 ;
  ind:hasDimApp inst:da_channel .
inst:da_channel a ind:DimensionApp ; ind:dimFactTable inst:fact_sales ;
  ind:dimFactColumn "channel_name" .

inst:d_phone a ind:Dimension ;
  ind:code "DIM_phone" ; ind:cnName "手机号" ; ind:viewTypeCode 0 ;
  ind:hasDimApp inst:da_phone .
inst:da_phone a ind:DimensionApp ; ind:dimFactTable inst:fact_sales ;
  ind:dimFactColumn "mobile" .

inst:d_segment a ind:Dimension ;
  ind:code "DIM_segment" ; ind:cnName "客户分层" ; ind:dimTypeCode 2 ;
  ind:viewTypeCode 0 ; ind:hasDimApp inst:da_segment .
inst:da_segment a ind:DimensionApp ; ind:dimFactTable inst:fact_sales ;
  ind:dimFactColumn "segment_id" ; ind:dimTable inst:dim_segment ;
  ind:dimPrimaryKey "segment_id" ; ind:dimColumn "segment_name" .
inst:dim_segment a ind:DwTable ; ind:tableName "dim_segment" ;
  ind:hasColumn inst:c_segment_name .
inst:c_segment_name a ind:DwColumn ; ind:columnName "segment_name" ;
  ind:sampleValue "VIP" .

inst:fact_sales a ind:DwTable ; ind:tableName "fact_sales" ;
  ind:hasColumn inst:c_region, inst:c_channel, inst:c_phone ;
  ind:hasConnection inst:secret_connection .
inst:c_region a ind:DwColumn ; ind:columnName "region_name" ;
  ind:sampleValue "华东", "华南" .
inst:c_channel a ind:DwColumn ; ind:columnName "channel_name" ;
  ind:sampleValue "直营", "经销" .
inst:c_phone a ind:DwColumn ; ind:columnName "mobile" ;
  ind:sampleValue "13800138000" .
inst:secret_connection a ind:DataConnection ; ind:dbPassword "do-not-leak-secret" .
"""


DICTIONARY = """
version: "1"
entries:
  - term: 成交额
    semanticType: measure
    canonicalCode: MEAS_gmv
    status: APPROVED
  - term: 大区
    semanticType: dimension
    canonicalCode: DIM_region
    status: ENABLED
  - term: 流水
    semanticType: measure
    canonicalCode: MEAS_gmv
    status: PENDING
values:
  - term: 东区
    dimensionCode: DIM_region
    canonicalValue: 华东
    status: APPROVED
  - term: 直营网点
    dimensionCode: DIM_channel
    canonicalValue: 直营
    status: APPROVED
  - term: 直营
    dimensionCode: DIM_region
    canonicalValue: 华东
    status: APPROVED
  - term: 13800138000
    dimensionCode: DIM_phone
    canonicalValue: 13800138000
    status: APPROVED
  - term: 大客户
    dimensionCode: DIM_segment
    canonicalValue: VIP
    status: APPROVED
valuePolicies:
  DIM_region: PUBLIC_ENUM
  DIM_channel: PUBLIC_ENUM
  DIM_segment: PUBLIC_ENUM
  DIM_phone: PUBLIC_ENUM
"""


def _paths(tmp_path: Path) -> tuple[Path, Path]:
    ttl = tmp_path / "catalog.ttl"
    dictionary = tmp_path / "semantic_dictionary.yaml"
    ttl.write_text(TTL, encoding="utf-8")
    dictionary.write_text(DICTIONARY, encoding="utf-8")
    return ttl, dictionary


def _config(tmp_path: Path, dictionary: Path, *, vector: bool = False) -> SemanticMappingConfig:
    return SemanticMappingConfig(
        dictionary_paths=(dictionary,),
        vector_enabled=vector,
        cache_dir=tmp_path / "cache",
    )


def test_legacy_sentence_transformer_env_cannot_enable_local_model(
    tmp_path: Path,
    monkeypatch,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    monkeypatch.setenv("INSIGHTMIND_SEMANTIC_EMBEDDING_PROVIDER", "sentence_transformer")
    monkeypatch.setenv(
        "INSIGHTMIND_SEMANTIC_EMBEDDING_MODEL",
        "paraphrase-multilingual-MiniLM-L12-v2",
    )
    config = SemanticMappingConfig.from_env(tmp_path)
    config = SemanticMappingConfig(
        **{
            **config.__dict__,
            "dictionary_paths": (dictionary,),
            "cache_dir": tmp_path / "cache",
        }
    )

    service = SemanticMappingService(ttl, config=config)

    assert service.status()["embeddingProvider"].startswith("hashing-char-word-ngram-v1:")


def test_catalog_alias_value_and_privacy_guards(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    snapshot = service.snapshot
    assert set(snapshot.items) == {
        "MEAS_gmv", "MEAS_net_sales", "DIM_region", "DIM_channel", "DIM_phone",
        "DIM_segment",
    }
    assert "MEAS_offline" not in snapshot.items
    assert "do-not-leak-secret" not in str(snapshot.to_dict(include_values=True))
    assert all(value.dimension_code != "DIM_phone" for value in snapshot.values)

    alias = service.search("查询成交额", semantic_types={"measure"}, include_vector=False)
    assert alias.candidates[0].code == "MEAS_gmv"
    assert alias.candidates[0].confidence == "high"
    assert alias.candidates[0].match_type == "alias_phrase"

    pending = service.search("流水", semantic_types={"measure"}, include_vector=False)
    assert not pending.candidates

    mapped = service.map("查询成交额，按大区看东区", include_vector=False)
    assert mapped.decision == "auto"
    assert mapped.measure_candidates[0].code == "MEAS_gmv"
    assert mapped.dimension_candidates[0].code == "DIM_region"
    assert mapped.value_bindings[0].dimension_code == "DIM_region"
    assert mapped.value_bindings[0].canonical_value == "华东"


def test_same_value_in_multiple_dimensions_requires_clarification(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("查询成交额直营", include_vector=False)

    assert result.decision == "clarify"
    assert result.needs_clarification is True
    assert result.diagnostics["valueAmbiguous"] is True

    top_one = service.map("查询成交额直营", include_vector=False, top_k=1)
    assert top_one.decision == "clarify"
    assert top_one.diagnostics["valueAmbiguous"] is True


class _FakeEmbeddingProvider:
    provider_id = "fake-semantic-v1"

    def __init__(self) -> None:
        self.calls = 0

    def encode(self, texts):
        self.calls += 1
        rows = []
        for text in texts:
            if "商品交易总额" in text or "买卖表现" in text:
                rows.append([1.0, 0.0, 0.0])
            elif "净销售额" in text:
                rows.append([0.0, 1.0, 0.0])
            else:
                rows.append([0.0, 0.0, 1.0])
        return np.asarray(rows, dtype=np.float32)


def test_vector_is_lazy_and_only_used_after_dictionary_miss(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    provider = _FakeEmbeddingProvider()
    service = SemanticMappingService(
        ttl,
        config=_config(tmp_path, dictionary, vector=True),
        embedding_provider=provider,
    )

    exact = service.search("成交额", semantic_types={"measure"})
    assert exact.candidates[0].code == "MEAS_gmv"
    assert provider.calls == 0

    vector = service.search("买卖表现", semantic_types={"measure"})
    assert vector.vector_used is True
    assert vector.candidates[0].code == "MEAS_gmv"
    assert vector.candidates[0].match_type == "vector"
    assert provider.calls == 2  # catalog matrix + one query vector


def test_transient_vector_query_failure_does_not_disable_index(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)

    class FailOnceProvider(_FakeEmbeddingProvider):
        provider_id = "fake-fail-once-v1"

        def encode(self, texts):
            if self.calls == 1:  # catalog build is call 0; first query is call 1
                self.calls += 1
                raise RuntimeError("transient")
            return super().encode(texts)

    provider = FailOnceProvider()
    service = SemanticMappingService(
        ttl,
        config=_config(tmp_path, dictionary, vector=True),
        embedding_provider=provider,
    )

    first = service.search("买卖表现", semantic_types={"measure"})
    second = service.search("买卖表现", semantic_types={"measure"})

    assert first.candidates == []
    assert second.candidates[0].code == "MEAS_gmv"
    assert second.vector_used is True


def test_dictionary_change_invalidates_snapshot(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    before = service.snapshot.dictionary_hash
    assert not service.search("流水", semantic_types={"measure"}, include_vector=False).candidates

    dictionary.write_text(DICTIONARY.replace("status: PENDING", "status: APPROVED"), encoding="utf-8")
    after = service.refresh().dictionary_hash

    assert after != before
    assert service.search("流水", semantic_types={"measure"}, include_vector=False).candidates[0].code == "MEAS_gmv"


def test_reviewed_feedback_revision_is_polled_and_reloaded(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    rows: list[dict] = []
    config = _config(tmp_path, dictionary)
    config = SemanticMappingConfig(
        **{
            **config.__dict__,
            "feedback_dictionary_enabled": True,
            "feedback_refresh_seconds": 1,
        }
    )
    service = SemanticMappingService(
        ttl,
        config=config,
        feedback_loader=lambda _graph_hash: list(rows),
    )
    before = service.snapshot.dictionary_hash

    rows.append({
        "semanticType": "measure",
        "term": "交易流水额",
        "canonicalCode": "MEAS_gmv",
        "status": "ENABLED",
        "metadata": {"reviewId": "review-1"},
    })
    service._feedback_checked_at = 0.0
    after = service.refresh().dictionary_hash

    assert after != before
    assert service.search(
        "交易流水额", semantic_types={"measure"}, include_vector=False
    ).candidates[0].code == "MEAS_gmv"


def test_legacy_indicator_namespace_and_application_predicates(tmp_path: Path) -> None:
    ttl = tmp_path / "legacy.ttl"
    dictionary = tmp_path / "dictionary.yaml"
    ttl.write_text(
        """
@prefix ind: <http://indicator.lixiang.com/ontology#> .
@prefix inst: <http://indicator.lixiang.com/instance/> .
inst:m a ind:Measure ; ind:code "MEAS_legacy_sales" ; ind:cnName "历史销售额" ;
  ind:hasApplication inst:ma .
inst:ma a ind:MeasureApplication ; ind:onFactTable inst:t .
inst:d a ind:Dimension ; ind:code "DIM_legacy_region" ; ind:cnName "历史区域" ;
  ind:hasApplication inst:da .
inst:da a ind:DimensionApplication ; ind:onFactTable inst:t .
inst:t a ind:DwTable ; ind:tableName "legacy_sales" .
""",
        encoding="utf-8",
    )
    dictionary.write_text('version: "1"\nentries: []\nvalues: []\n', encoding="utf-8")
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("按历史区域查询历史销售额", include_vector=False)

    assert result.measure_candidates[0].code == "MEAS_legacy_sales"
    assert result.dimension_candidates[0].code == "DIM_legacy_region"
    assert result.decision == "auto"


def test_nlq_applies_unambiguous_reviewed_value_filter(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    service = NaturalLanguageQueryService(
        ttl,
        "http://unused",
        semantic_mapping_service=mapping,
    )
    service._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}

    response = service.query("查询成交额，按大区看东区", execute=False)

    assert response["ok"] is True
    assert response["matched"]["measureCode"] == "MEAS_gmv"
    assert response["matched"]["valueBindings"][0]["canonicalValue"] == "华东"
    value_filter = next(
        item for item in response["daPayload"]["filterList"] if item["code"] == "DIM_region"
    )
    assert value_filter["operatorList"][0]["dataList"] == ["华东"]
    assert response["semanticMapping"]["confidence"] == "high"


def test_nlq_refuses_unverified_type2_display_value_filter(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    service = NaturalLanguageQueryService(
        ttl,
        "http://unused",
        semantic_mapping_service=mapping,
    )
    service._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}

    response = service.query("查询成交额大客户", execute=False)

    assert response["ok"] is False
    assert response["action"] == "clarify"
    assert response["diagnosticCode"] == "DIMENSION_VALUE_FILTER_UNVERIFIED"
    assert response["daPayload"] is None
    assert response["matched"]["valueBindings"][0]["canonicalValue"] == "VIP"


def test_insight_analysis_reuses_governed_alias(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    analyzer = InsightAnalyzer(
        data_agent_url="http://unused",
        ttl_path=str(ttl),
        llm_config={},
        log_cb=lambda _message: None,
        semantic_mapping_service=mapping,
    )

    matched = analyzer._find_meas_in_kg(["成交额"], "成交额为什么下降")

    assert matched is not None
    assert matched["meas_code"] == "MEAS_gmv"
    assert matched["semantic_mapping"]["measureCandidates"][0]["matchType"] == "alias_phrase"


def test_insight_surfaces_unique_vector_candidate_for_confirmation(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(
        ttl,
        config=_config(tmp_path, dictionary, vector=True),
        embedding_provider=_FakeEmbeddingProvider(),
    )
    analyzer = InsightAnalyzer(
        data_agent_url="http://unused",
        ttl_path=str(ttl),
        llm_config={},
        log_cb=lambda _message: None,
        semantic_mapping_service=mapping,
    )

    matched = analyzer._find_meas_in_kg(["买卖表现"], "买卖表现为什么下降")

    assert matched is not None
    assert matched["needs_clarification"] is True
    candidate = matched["semantic_mapping"]["measureCandidates"][0]
    assert candidate["code"] == "MEAS_gmv"
    assert candidate["matchType"] == "vector"


def test_insight_surfaces_ambiguous_aliases_as_clarification(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "entries:\n",
            "entries:\n"
            "  - term: 销售指标\n"
            "    semanticType: measure\n"
            "    canonicalCode: MEAS_gmv\n"
            "    status: APPROVED\n"
            "  - term: 销售指标\n"
            "    semanticType: measure\n"
            "    canonicalCode: MEAS_net_sales\n"
            "    status: APPROVED\n",
        ),
        encoding="utf-8",
    )
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    analyzer = InsightAnalyzer(
        data_agent_url="http://unused",
        ttl_path=str(ttl),
        llm_config={},
        log_cb=lambda _message: None,
        semantic_mapping_service=mapping,
    )

    matched = analyzer._find_meas_in_kg(["销售指标"], "销售指标为什么下降")

    assert matched is not None
    assert matched["needs_clarification"] is True
    assert len(matched["semantic_mapping"]["measureCandidates"]) >= 2


def test_semantic_retrieval_http_contract(tmp_path: Path, monkeypatch) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    app = FastAPI()
    app.include_router(create_semantic_retrieval_router(lambda: service))
    monkeypatch.setenv("INSIGHTMIND_SEMANTIC_API_TOKEN", "semantic-test-token")
    client = TestClient(
        app, headers={"X-InsightMind-Semantic-Token": "semantic-test-token"}
    )

    search = client.get(
        "/api/semantic-retrieval/search",
        params={"keyword": "成交额", "types": "measure", "includeVector": "false"},
    )
    assert search.status_code == 200
    assert search.json()["items"][0]["code"] == "MEAS_gmv"
    assert search.json()["items"][0]["evidence"]

    mapped = client.post(
        "/api/semantic-retrieval/map",
        json={"question": "查询成交额，按大区看东区", "includeVector": False},
    )
    assert mapped.status_code == 200
    assert mapped.json()["decision"] == "auto"
    assert mapped.json()["valueBindings"][0]["canonicalValue"] == "华东"

    deny_all = client.post(
        "/api/semantic-retrieval/map",
        json={
            "question": "查询成交额",
            "allowedMeasureCodes": [],
            "allowedDimensionCodes": [],
            "includeVector": False,
        },
    )
    assert deny_all.status_code == 200
    assert deny_all.json()["decision"] == "reject"
    assert deny_all.json()["measureCandidates"] == []


def test_semantic_api_is_fail_closed_without_token(tmp_path: Path, monkeypatch) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    monkeypatch.delenv("INSIGHTMIND_SEMANTIC_API_TOKEN", raising=False)
    app = FastAPI()
    app.include_router(create_semantic_retrieval_router(lambda: service))
    client = TestClient(app)

    assert client.get(
        "/api/semantic-retrieval/search", params={"keyword": "成交额"}
    ).status_code == 503
    status = client.get("/api/semantic-retrieval/status")
    assert status.status_code == 200
    assert "graphHash" not in status.json()


def test_short_and_numeric_values_do_not_silently_filter(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 高\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 高\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "  - term: '10'\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: '10'\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "valuePolicies:\n",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    short = service.map("提高成交额", include_vector=False)
    numeric = service.map("查询最近10天成交额", include_vector=False)

    assert short.value_bindings == []
    assert numeric.value_bindings == []
    assert short.decision == "auto"
    assert numeric.decision == "auto"


def test_value_text_inside_measure_name_is_not_treated_as_filter(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace('ind:cnName "商品交易总额"', 'ind:cnName "服务销售额"'),
        encoding="utf-8",
    )
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 服务\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 服务\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "valuePolicies:\n",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("查询服务销售额", include_vector=False)

    assert result.measure_candidates[0].code == "MEAS_gmv"
    assert result.value_bindings == []
    assert result.decision == "auto"
    assert result.diagnostics["suppressedMeasureOverlapValues"] == 1


def test_value_text_inside_dimension_name_is_not_treated_as_filter(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace('ind:code "DIM_region" ; ind:cnName "区域"',
                    'ind:code "DIM_region" ; ind:cnName "服务类型"'),
        encoding="utf-8",
    )
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 服务\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 服务\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "valuePolicies:\n",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("按服务类型查询成交额", include_vector=False)

    assert result.dimension_candidates[0].code == "DIM_region"
    assert result.value_bindings == []
    assert result.decision == "auto"
    assert result.diagnostics["suppressedEntityOverlapValues"] == 1


def test_overlapping_value_spans_require_clarification(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 东区\n"
            "    dimensionCode: DIM_channel\n"
            "    canonicalValue: 直营\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "valuePolicies:\n",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("查询成交额华东区", include_vector=False)

    assert result.diagnostics["valueAmbiguous"] is True
    assert result.decision == "clarify"


def test_value_alias_weights_are_preserved_per_alias(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "    status: APPROVED\n  - term: 直营网点",
            "    status: APPROVED\n"
            "    weight: 0.99\n"
            "  - term: 华东片\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 华东\n"
            "    status: APPROVED\n"
            "    weight: 0.50\n"
            "  - term: 直营网点",
            1,
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    strong = service.search("东区", semantic_types={"value"}, include_vector=False)
    weak = service.search("华东片", semantic_types={"value"}, include_vector=False)

    assert strong.candidates[0].confidence == "high"
    assert strong.candidates[0].score >= 0.99
    assert weak.candidates[0].match_type == "exact_value"
    assert weak.candidates[0].confidence == "low"
    assert weak.candidates[0].score < 0.60


def test_same_term_conflicts_within_one_dimension_and_between_dimensions(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 东区\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 东部\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "valuePolicies:\n",
        ).replace(
            "values:\n",
            "  - term: 大区\n"
            "    semanticType: dimension\n"
            "    canonicalCode: DIM_channel\n"
            "    status: APPROVED\n"
            "values:\n",
            1,
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    value_conflict = service.map("查询成交额东区", include_vector=False)
    dimension_conflict = service.map("查询成交额按大区", include_vector=False)

    assert value_conflict.diagnostics["valueAmbiguous"] is True
    assert value_conflict.decision == "clarify"
    assert dimension_conflict.diagnostics["dimensionAmbiguous"] is True
    assert dimension_conflict.decision == "clarify"


def test_exact_dimension_does_not_hide_a_second_fuzzy_dimension(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("按区域和客户分群查询成交额", include_vector=False)

    assert any(item.code == "DIM_region" for item in result.dimension_candidates)
    assert any(item.code == "DIM_segment" for item in result.dimension_candidates)
    assert result.diagnostics["dimensionUncertain"] is True
    assert result.decision == "clarify"


def test_nlq_follow_up_revalidates_and_keeps_value_filter(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    service = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    service._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}

    first = service.query("查询成交额，按大区看东区", execute=False)
    follow_up = service.query(
        "继续看",
        execute=False,
        context=first["resolvedContext"],
        is_follow_up=True,
    )

    assert first["ok"] is True
    assert follow_up["ok"] is True
    inherited = next(
        item for item in follow_up["daPayload"]["filterList"]
        if item["code"] == "DIM_region"
    )
    assert inherited["operatorList"][0]["dataList"] == ["华东"]
    assert follow_up["matched"]["valueBindings"][0]["inherited"] is True
    assert follow_up["matched"]["valueBindings"][0]["applicableTables"] == [
        "fact_sales"
    ]


def test_source_manifest_rejects_a_replaced_business_graph(tmp_path: Path, monkeypatch) -> None:
    import web_app

    output_dir = tmp_path / "output"
    bkg_dir = output_dir / "business_kg"
    bkg_dir.mkdir(parents=True)
    business = bkg_dir / "indicator-data.ttl"
    source = output_dir / "kg_source.ttl"
    manifest = bkg_dir / "indicator-data.source.json"
    business.write_text(TTL, encoding="utf-8")
    source.write_text(TTL, encoding="utf-8")
    monkeypatch.setattr(web_app, "OUTPUT_DIR", output_dir)
    monkeypatch.setattr(web_app, "BKG_DIR", bkg_dir)
    monkeypatch.setattr(web_app, "SEMANTIC_SOURCE_MANIFEST_PATH", manifest)
    monkeypatch.delenv("INSIGHTMIND_SEMANTIC_SOURCE_TTL", raising=False)

    web_app._write_semantic_source_manifest(source)
    assert web_app._semantic_source_path() == source.resolve()

    business.write_text(TTL + "\n# replaced by a different BKG\n", encoding="utf-8")
    assert web_app._semantic_source_path() is None


def test_dimension_values_keep_application_table_scope(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace(
            "ind:hasDimApp inst:da_region .",
            "ind:hasDimApp inst:da_region, inst:da_region_other .",
        )
        + """
inst:m_other a ind:Measure ; ind:code "MEAS_other" ; ind:cnName "库存金额" ;
  ind:hasMeasureApp inst:ma_other .
inst:ma_other a ind:MeasureApp ; ind:appliesToTable inst:fact_other .
inst:da_region_other a ind:DimensionApp ; ind:dimFactTable inst:fact_other ;
  ind:dimFactColumn "region_id" .
inst:fact_other a ind:DwTable ; ind:tableName "fact_other" .
""",
        encoding="utf-8",
    )
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 异域东区\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 华东\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "    tables: [fact_other]\n"
            "valuePolicies:\n",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    wrong_table = service.map("查询库存金额东区", include_vector=False)
    scoped_alias = service.map("查询库存金额异域东区", include_vector=False)

    assert wrong_table.value_bindings == []
    assert wrong_table.decision == "clarify"
    assert wrong_table.diagnostics["unresolvedValueIntent"] is True
    assert scoped_alias.value_bindings[0].canonical_value == "华东"
    assert scoped_alias.value_bindings[0].filter_safe is True


def test_source_cardinality_uses_configured_limit(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    source = tmp_path / "source.ttl"
    source.write_text(
        """
@prefix db: <http://kg.local/db#> .
@prefix inst: <http://kg.local/instance/> .
inst:t a db:Table ; db:name "fact_sales" ; db:containsColumn inst:c .
inst:c a db:Column ; db:name "region_name" ; db:belongsToTable inst:t ;
  db:cardinality 100 ; db:topValue "华北" .
""",
        encoding="utf-8",
    )
    base = _config(tmp_path, dictionary)
    strict = SemanticMappingConfig(
        **{**base.__dict__, "max_dimension_cardinality": 10}
    )
    relaxed = SemanticMappingConfig(
        **{**base.__dict__, "max_dimension_cardinality": 200}
    )

    strict_service = SemanticMappingService(
        ttl, source_ttl_path=source, config=strict
    )
    relaxed_service = SemanticMappingService(
        ttl, source_ttl_path=source, config=relaxed
    )

    assert all(value.canonical_value != "华北" for value in strict_service.snapshot.values)
    assert any(value.canonical_value == "华北" for value in relaxed_service.snapshot.values)


def test_time_dimension_values_are_never_indexed(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL
        + """
inst:d_month a ind:Dimension ; ind:code "DIM_month" ; ind:cnName "月份" ;
  ind:viewTypeCode 3 ; ind:sampleValue "2026-08" ; ind:hasDimApp inst:da_month .
inst:da_month a ind:DimensionApp ; ind:dimFactTable inst:fact_sales ;
  ind:dimFactColumn "stat_month" .
""",
        encoding="utf-8",
    )
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "valuePolicies:\n  DIM_month: PUBLIC_ENUM\n",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    assert service.snapshot.items["DIM_month"].is_time is True
    assert all(value.dimension_code != "DIM_month" for value in service.snapshot.values)


def test_structured_pending_value_policy_is_fail_closed(tmp_path: Path) -> None:
    dictionary = tmp_path / "dictionary.yaml"
    dictionary.write_text(
        """
version: "1"
valuePolicies:
  DIM_region:
    policy: PUBLIC_ENUM
    status: PENDING
""",
        encoding="utf-8",
    )

    bundle = load_dictionary((dictionary,))

    assert "DIM_region" not in bundle.value_policies


def test_scoped_demo_value_policy_does_not_cross_business_graphs(tmp_path: Path) -> None:
    ttl = tmp_path / "other_domain.ttl"
    ttl.write_text(
        """
@prefix ind: <http://indicator.insightmind.com/ontology#> .
@prefix inst: <http://indicator.insightmind.com/instance/> .
inst:d a ind:Dimension ; ind:code "DIM_region" ; ind:cnName "区域" ;
  ind:sampleValue "华东" ; ind:hasDimApp inst:da .
inst:da a ind:DimensionApp ; ind:dimFactTable inst:t ; ind:dimFactColumn "region" .
inst:t a ind:DwTable ; ind:tableName "other_domain_sales" .
""",
        encoding="utf-8",
    )
    dictionary = Path(__file__).resolve().parents[1] / "semantic_dictionary.yaml"
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    assert service.snapshot.values == []
    assert any("事实表范围不匹配" in warning for warning in service.snapshot.warnings)


def test_short_latin_enum_requires_word_boundary_and_dimension_context(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace(
            "ind:hasColumn inst:c_region, inst:c_channel, inst:c_phone",
            "ind:hasColumn inst:c_region, inst:c_channel, inst:c_phone, inst:c_country",
        )
        + """
inst:d_country a ind:Dimension ; ind:code "DIM_country" ; ind:cnName "国家" ;
  ind:hasDimApp inst:da_country .
inst:da_country a ind:DimensionApp ; ind:dimFactTable inst:fact_sales ;
  ind:dimFactColumn "country_code" .
inst:c_country a ind:DwColumn ; ind:columnName "country_code" ;
  ind:sampleValue "US", "APP" .
""",
        encoding="utf-8",
    )
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "valuePolicies:\n  DIM_country: PUBLIC_ENUM\n",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    assert service.map("business 成交额", include_vector=False).value_bindings == []
    assert service.map("application 成交额", include_vector=False).value_bindings == []
    positive = service.map("国家 US 成交额", include_vector=False)
    assert positive.value_bindings[0].canonical_value == "US"
    assert positive.decision == "auto"


def test_nlq_clarifies_value_negation_but_allows_positive_multi_value(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    service = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    service._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}

    negative = service.query("查询成交额，不要东区，只看华南", execute=False)
    positive = service.query("查询成交额，东区和华南", execute=False)

    assert negative["ok"] is False
    assert negative["action"] == "clarify"
    assert negative["diagnosticCode"] == "DIMENSION_VALUE_OPERATOR_UNSUPPORTED"
    assert negative["daPayload"] is None
    assert positive["ok"] is True
    value_filter = next(
        item for item in positive["daPayload"]["filterList"]
        if item["code"] == "DIM_region"
    )
    assert set(value_filter["operatorList"][0]["dataList"]) == {"华东", "华南"}


def test_graph_identity_cache_keeps_multiple_graphs_and_is_bounded(tmp_path: Path) -> None:
    from kg_builder.feedback import graph_version

    graph_version._cache.clear()
    first = tmp_path / "first.ttl"
    second = tmp_path / "second.ttl"
    first.write_text("# first", encoding="utf-8")
    second.write_text("# second", encoding="utf-8")

    assert graph_version.graph_identity(first)["sha256"]
    assert graph_version.graph_identity(second)["sha256"]
    assert len(graph_version._cache) == 2
    for index in range(graph_version._CACHE_LIMIT + 4):
        path = tmp_path / f"graph-{index}.ttl"
        path.write_text(str(index), encoding="utf-8")
        graph_version.graph_identity(path)
    assert len(graph_version._cache) == graph_version._CACHE_LIMIT


def test_unknown_value_like_phrase_does_not_execute_without_filter(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    service = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    service._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}

    response = service.query("查询东南偏远区成交额", execute=False)

    assert response["ok"] is False
    assert response["diagnosticCode"] == "DIMENSION_VALUE_UNRESOLVED"
    assert response["daPayload"] is None


def test_duplicate_semantic_code_is_rejected(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL
        + """
inst:m_duplicate a ind:Measure ; ind:code "MEAS_gmv" ; ind:cnName "重复成交额" ;
  ind:hasMeasureApp inst:ma_duplicate .
inst:ma_duplicate a ind:MeasureApp ; ind:appliesToTable inst:fact_sales .
""",
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    with pytest.raises(ValueError, match="重复语义编码 MEAS_gmv"):
        _ = service.snapshot


def test_all_alternate_english_names_remain_searchable(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace(
            'ind:enName "gross_merchandise_value"',
            'ind:enName "zzz_sales", "aaa_sales"',
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    item = service.snapshot.items["MEAS_gmv"]
    assert item.en_name == "aaa_sales"
    assert "zzz_sales" in item.aliases
    assert service.search(
        "zzz_sales", semantic_types={"measure"}, include_vector=False
    ).candidates[0].code == "MEAS_gmv"


def test_same_size_preserved_mtime_still_invalidates_snapshot(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    assert service.snapshot.items["MEAS_gmv"].cn_name == "商品交易总额"
    stat = ttl.stat()
    replacement = TTL.replace("商品交易总额", "服务交易总额")
    assert len(replacement.encode("utf-8")) == len(TTL.encode("utf-8"))
    ttl.write_text(replacement, encoding="utf-8")
    os.utime(ttl, ns=(stat.st_atime_ns, stat.st_mtime_ns))

    assert service.refresh().items["MEAS_gmv"].cn_name == "服务交易总额"


def test_fuzzy_prefilter_keeps_large_dictionary_latency_bounded() -> None:
    items = {}
    aliases = []
    for index in range(5000):
        code = f"MEAS_perf_{index}"
        term = f"业务指标{index:04d}"
        items[code] = CatalogItem(
            code=code,
            semantic_type="measure",
            cn_name=term,
            tables=frozenset({"fact_perf"}),
        )
        aliases.append(AliasRecord(
            term=term,
            normalized_term=term.lower(),
            semantic_type="measure",
            canonical_code=code,
            source="manual",
            weight=0.96,
        ))
    snapshot = CatalogSnapshot(
        version="perf",
        graph_hash="g",
        source_graph_hash="s",
        dictionary_hash="d",
        items=items,
        values=[],
        aliases=aliases,
        generated_at="now",
    )
    retriever = SemanticRetriever(snapshot)
    question = (
        "请帮我深入分析最近三个月所有业务经营活动中一个完全不同的长问题"
        "为何持续下降以及可能的业务原因和后续改进建议"
    ) * 5

    started = time.perf_counter()
    result = retriever.search(question, semantic_types={"measure"}, include_vector=False)
    elapsed = time.perf_counter() - started

    assert result.candidates == []
    assert elapsed < 1.0


def test_value_operators_are_scoped_to_the_recalled_value_span(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    for question in (
        "查询非东区成交额",
        "查询非属于东区的成交额",
        "查询东区以外的成交额",
        "查询东区外的成交额",
        "查询除东区外的成交额",
        "查询除了东区的成交额",
        "查询 not 东区成交额",
        "查询 without 东区成交额",
        "查询东区改南区成交额",
        "查询东区换南区成交额",
        "查询东区更正为南区成交额",
    ):
        result = service.map(question, include_vector=False)
        assert result.decision == "clarify", question
        assert result.diagnostics["unsupportedValueOperator"] is True, question

    positive = service.map("不要分析趋势，只看东区成交额", include_vector=False)
    assert positive.diagnostics["unsupportedValueOperator"] is False
    assert positive.decision == "auto"
    assert positive.value_bindings[0].canonical_value == "华东"


def test_unexplained_business_span_fails_closed_for_unknown_values(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace('ind:sampleValue "VIP" .', 'ind:sampleValue "Gold" .'),
        encoding="utf-8",
    )
    dictionary.write_text(
        DICTIONARY.replace(
            "  - term: 大客户\n"
            "    dimensionCode: DIM_segment\n"
            "    canonicalValue: VIP\n"
            "    status: APPROVED\n",
            "",
        ).replace(
            "  - term: 直营网点\n"
            "    dimensionCode: DIM_channel\n"
            "    canonicalValue: 直营\n"
            "    status: APPROVED\n",
            "",
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    for unknown_value in ("华北", "VIP", "直营网点", "红色", "北京"):
        result = service.map(f"查询{unknown_value}成交额", include_vector=False)

        assert result.measure_candidates[0].code == "MEAS_gmv", unknown_value
        assert result.diagnostics["unresolvedValueIntent"] is True, unknown_value
        assert result.diagnostics["unexplainedTokens"], unknown_value
        assert result.decision == "clarify", unknown_value


def test_unexplained_span_ignores_analysis_time_and_mapped_entities(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    for question in (
        "请帮我深入分析最近三个月成交额为什么持续下降以及可能的业务原因",
        "按大区查看成交额",
        "查询东区和华南成交额",
        "查询成交额同比环比趋势",
        "查看成交额整体情况",
        "解释成交额有哪些可分析维度",
        "解释成交额的口径",
        "please analyze business gmv trend",
    ):
        result = service.map(question, include_vector=False)

        assert result.diagnostics["unresolvedValueIntent"] is False, question
        assert result.diagnostics["unexplainedTokens"] == [], question
        assert result.decision == "auto", question


def test_medium_dimension_never_silently_autoruns(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("查询客户分群成交额", include_vector=False)

    segment = next(item for item in result.dimension_candidates if item.code == "DIM_segment")
    assert segment.confidence == "medium"
    assert result.diagnostics["dimensionUncertain"] is True
    assert result.decision == "clarify"


def test_distinct_second_measure_span_requires_clarification(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    result = service.map("查询成交额和净销额", include_vector=False)

    assert [item.code for item in result.measure_candidates[:2]] == [
        "MEAS_gmv", "MEAS_net_sales",
    ]
    assert result.measure_candidates[1].confidence == "medium"
    assert result.diagnostics["multipleMeasureEntities"] is True
    assert result.diagnostics["measureAmbiguous"] is True
    assert result.decision == "clarify"


def test_value_binding_scope_fails_closed_for_multi_table_measure(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace(
            "ind:hasMeasureApp inst:ma_gmv .",
            "ind:hasMeasureApp inst:ma_gmv, inst:ma_gmv_other .",
        ).replace(
            "ind:hasDimApp inst:da_region .",
            "ind:hasDimApp inst:da_region, inst:da_region_other .",
        )
        + """
inst:ma_gmv_other a ind:MeasureApp ; ind:appliesToTable inst:fact_other .
inst:da_region_other a ind:DimensionApp ; ind:dimFactTable inst:fact_other ;
  ind:dimFactColumn "region_name" .
inst:fact_other a ind:DwTable ; ind:tableName "fact_other" .
""",
        encoding="utf-8",
    )
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 专属片区\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 华东\n"
            "    status: APPROVED\n"
            "    filterSafe: true\n"
            "    tables: [fact_other]\n"
            "valuePolicies:\n",
            1,
        ),
        encoding="utf-8",
    )
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    mapped = mapping.map("查询成交额专属片区", include_vector=False)

    assert mapped.value_bindings[0].applicable_tables == {"fact_other"}
    assert mapped.value_bindings[0].to_dict()["applicableTables"] == ["fact_other"]
    assert mapped.diagnostics["compatibleTables"] == ["fact_other"]
    assert mapped.diagnostics["valueTableIncompatible"] is False
    assert mapped.decision == "auto"

    nlq = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    nlq._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}
    response = nlq.query("查询成交额专属片区", execute=False)
    assert response["ok"] is False
    assert response["diagnosticCode"] == "DIMENSION_VALUE_TABLE_SCOPE_AMBIGUOUS"
    assert response["daPayload"] is None


def test_common_grain_aggregation_and_english_queries_are_not_unknown_values(
    tmp_path: Path,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "entries:\n",
            "entries:\n"
            "  - term: revenue\n"
            "    semanticType: measure\n"
            "    canonicalCode: MEAS_gmv\n"
            "    status: APPROVED\n",
            1,
        ),
        encoding="utf-8",
    )
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    for question in (
        "按月查询成交额",
        "按周查询成交额",
        "按天查询成交额",
        "每月成交额",
        "今年成交额是多少",
        "成交额多少",
        "成交额怎么样",
        "成交额的均值",
        "成交额平均值",
        "成交额总和",
        "成交额合计",
        "成交额前十",
        "show business revenue",
        "show business gross merchandise value",
    ):
        result = service.map(question, include_vector=False)
        assert result.decision == "auto", question
        assert result.diagnostics["unresolvedValueIntent"] is False, question


def test_explicit_unknown_value_slots_and_free_alphanumeric_tokens_fail_closed(
    tmp_path: Path,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    for question in (
        "查询 DIM_region=P7 的成交额",
        "查询 DIM_region=123 的成交额",
        "查询大区为P7的成交额",
        "查询“X”成交额",
        "查询“红”成交额",
    ):
        result = service.map(question, include_vector=False)
        assert result.decision == "clarify", question
        assert result.diagnostics["unresolvedValueIntent"] is True, question
        assert result.diagnostics["unresolvedValueSlots"], question

    for question in ("查询P7成交额", "查询A1成交额", "查询P7/A1成交额"):
        result = service.map(question, include_vector=False)
        assert result.decision == "clarify", question
        assert result.diagnostics["unexplainedTokens"], question

    assert service.map("查询123成交额", include_vector=False).decision == "auto"
    assert service.map("查询“东区”成交额", include_vector=False).decision == "auto"


def test_value_or_and_range_operators_never_change_filter_semantics(
    tmp_path: Path,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 南区\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 华南\n"
            "    status: APPROVED\n"
            "valuePolicies:\n",
            1,
        ),
        encoding="utf-8",
    )
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    cross_dimension = mapping.map("查询东区或直营网点成交额", include_vector=False)
    assert cross_dimension.decision == "clarify"
    assert cross_dimension.diagnostics["crossDimensionOrUnsupported"] is True

    for question in (
        "查询东区到南区成交额",
        "查询东区至南区成交额",
        "查询东区-南区成交额",
        "between 东区 and 南区 成交额",
        "查询东区以上成交额",
        "查询小于东区成交额",
    ):
        result = mapping.map(question, include_vector=False)
        assert result.decision == "clarify", question
        assert result.diagnostics["unsupportedValueOperator"] is True, question

    same_dimension = mapping.map("查询东区或南区成交额", include_vector=False)
    assert same_dimension.decision == "auto"
    assert same_dimension.diagnostics["crossDimensionOrUnsupported"] is False
    assert mapping.map("查询东区和南区成交额", include_vector=False).decision == "auto"

    nlq = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    nlq._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}
    cross_response = nlq.query("查询东区或直营网点成交额", execute=False)
    range_response = nlq.query("查询东区到南区成交额", execute=False)
    assert cross_response["ok"] is False
    assert cross_response["daPayload"] is None
    assert cross_response["semanticMapping"]["diagnostics"][
        "crossDimensionOrUnsupported"
    ] is True
    assert range_response["diagnosticCode"] == "DIMENSION_VALUE_OPERATOR_UNSUPPORTED"
    assert range_response["daPayload"] is None


def test_dimension_name_inside_measure_does_not_become_an_implicit_group(
    tmp_path: Path,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL.replace('ind:cnName "商品交易总额"', 'ind:cnName "地区销售额"').replace(
            'ind:code "DIM_region" ; ind:cnName "区域"',
            'ind:code "DIM_region" ; ind:cnName "地区"',
        ),
        encoding="utf-8",
    )
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    mapped = mapping.map("查询地区销售额", include_vector=False)
    assert mapped.decision == "auto"
    assert all(item.code != "DIM_region" for item in mapped.dimension_candidates)
    assert mapped.diagnostics["suppressedMeasureOverlapDimensionCodes"] == [
        "DIM_region"
    ]
    grouped = mapping.map("按地区查询地区销售额", include_vector=False)
    assert any(item.code == "DIM_region" for item in grouped.dimension_candidates)

    nlq = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    nlq._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}
    response = nlq.query("查询地区销售额", execute=False)
    assert response["ok"] is True
    assert response["matched"]["dimensionCodes"] == []
    assert response["daPayload"]["configureList"] == [{"code": "MEAS_gmv"}]


def test_unknown_explicit_codes_cannot_be_ignored_by_mapper_or_nlq(
    tmp_path: Path,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))

    legal = mapping.map("查询 MEAS_gmv", include_vector=False)
    assert legal.diagnostics["unknownExplicitCodes"] == []
    for question, missing in (
        ("查询 MEAS_gmv 和 MEAS_missing", "MEAS_MISSING"),
        ("查询成交额和 MEAS_missing", "MEAS_MISSING"),
        ("查询成交额按 DIM_missing", "DIM_MISSING"),
    ):
        result = mapping.map(question, include_vector=False)
        assert result.decision == "clarify", question
        assert result.diagnostics["unknownExplicitCodes"] == [missing]

    nlq = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    nlq._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}
    response = nlq.query("查询成交额按 DIM_missing", execute=False)
    assert response["ok"] is False
    assert response["diagnosticCode"] == "SEMANTIC_EXPLICIT_CODE_UNKNOWN"
    assert response["daPayload"] is None


def test_vector_only_second_measure_is_not_silently_ignored(tmp_path: Path) -> None:
    class SecondMeasureProvider:
        provider_id = "fake-second-measure-v1"

        def encode(self, texts):
            rows = []
            for text in texts:
                if "再次购买表现" in text or "净销售额" in text:
                    rows.append([0.0, 1.0, 0.0])
                elif "商品交易总额" in text:
                    rows.append([1.0, 0.0, 0.0])
                else:
                    rows.append([0.0, 0.0, 1.0])
            return np.asarray(rows, dtype=np.float32)

    ttl, dictionary = _paths(tmp_path)
    service = SemanticMappingService(
        ttl,
        config=_config(tmp_path, dictionary, vector=True),
        embedding_provider=SecondMeasureProvider(),
    )

    result = service.map("查询成交额和再次购买表现")
    assert result.measure_candidates[0].code == "MEAS_gmv"
    assert result.measure_candidates[1].code == "MEAS_net_sales"
    assert result.measure_candidates[1].match_type == "vector"
    assert result.diagnostics["potentialAdditionalMeasure"] is True
    assert result.diagnostics["measureAmbiguous"] is True
    assert result.decision == "clarify"


def test_explicit_dimension_limit_clarifies_instead_of_truncating(tmp_path: Path) -> None:
    ttl, dictionary = _paths(tmp_path)
    ttl.write_text(
        TTL
        + """
inst:d_scene a ind:Dimension ; ind:code "DIM_scene" ; ind:cnName "营销场景" ;
  ind:viewTypeCode 0 ; ind:hasDimApp inst:da_scene .
inst:da_scene a ind:DimensionApp ; ind:dimFactTable inst:fact_sales ;
  ind:dimFactColumn "scene" .
""",
        encoding="utf-8",
    )
    mapping = SemanticMappingService(ttl, config=_config(tmp_path, dictionary))
    nlq = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    nlq._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}
    question = "按区域、渠道、客户分层、营销场景查询成交额"

    limited = nlq.query(question, execute=False)
    expanded = nlq.query(question, execute=False, max_dimensions=4)
    assert limited["ok"] is False
    assert limited["diagnosticCode"] == "SEMANTIC_DIMENSION_LIMIT_EXCEEDED"
    assert limited["daPayload"] is None
    assert expanded["ok"] is True
    assert set(expanded["matched"]["dimensionCodes"]) == {
        "DIM_region", "DIM_channel", "DIM_segment", "DIM_scene",
    }


def test_follow_up_value_uses_inherited_primary_for_scope_validation(
    tmp_path: Path,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    dictionary.write_text(
        DICTIONARY.replace(
            "valuePolicies:\n",
            "  - term: 南区\n"
            "    dimensionCode: DIM_region\n"
            "    canonicalValue: 华南\n"
            "    status: APPROVED\n"
            "valuePolicies:\n",
            1,
        ),
        encoding="utf-8",
    )
    mapping = SemanticMappingService(
        ttl,
        config=_config(tmp_path, dictionary, vector=True),
    )
    nlq = NaturalLanguageQueryService(
        ttl, "http://unused", semantic_mapping_service=mapping
    )
    nlq._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}
    first = nlq.query("查询东区成交额", execute=False)
    assert first["ok"] is True

    for question in ("看南区", "也看南区", "再看南区"):
        response = nlq.query(
            question,
            execute=False,
            context=first["resolvedContext"],
            is_follow_up=True,
        )
        assert response["ok"] is True, question
        assert response["semanticMapping"]["diagnostics"]["assumedPrimary"] == {
            "code": "MEAS_gmv",
            "source": "inherited_context",
        }
        region_filter = next(
            item for item in response["daPayload"]["filterList"]
            if item["code"] == "DIM_region"
        )
        assert region_filter["operatorList"][0]["dataList"] == ["华南"]


def test_value_binding_upgrades_vector_dimension_and_suppresses_vector_noise(
    tmp_path: Path,
) -> None:
    ttl, dictionary = _paths(tmp_path)
    mapping = SemanticMappingService(
        ttl,
        config=_config(tmp_path, dictionary, vector=True),
        embedding_provider=_FakeEmbeddingProvider(),
    )

    result = mapping.map(
        "看华南",
        allowed_measure_codes=["MEAS_gmv"],
        allowed_dimension_codes={
            item.code for item in mapping.snapshot.items.values()
            if item.semantic_type == "dimension"
        },
        preferred_tables={"fact_sales"},
        assumed_measure_code="MEAS_gmv",
        include_vector=True,
    )

    assert result.decision == "auto"
    assert result.diagnostics["dimensionUncertain"] is False
    assert result.diagnostics["valueTableIncompatible"] is False
    assert result.diagnostics["vectorUsed"] is True
    region = next(
        item for item in result.dimension_candidates if item.code == "DIM_region"
    )
    assert region.match_type == "value_implied_dimension"
    assert region.confidence == "high"
    assert region.tables == {"fact_sales"}
    assert result.diagnostics["suppressedVectorDimensionCodes"]
