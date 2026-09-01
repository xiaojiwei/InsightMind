from __future__ import annotations

from kg_builder.entities.models import ColumnEntity, EntityGraph
from kg_builder.relations import implicit


ImplicitRelationExtractor = implicit.ImplicitRelationExtractor
REL_POTENTIAL_FK = implicit.REL_POTENTIAL_FK
REL_SIMILAR_TO = implicit.REL_SIMILAR_TO


def _column(
    column_id: str,
    name: str,
    table_id: str,
    *,
    is_pk: bool = False,
    comment: str = "",
) -> ColumnEntity:
    return ColumnEntity(
        id=column_id,
        name=name,
        table_id=table_id,
        data_type="VARCHAR(64)",
        is_nullable=not is_pk,
        is_pk=is_pk,
        comment=comment,
        normalized_name=name.lower(),
    )


def test_llm_relations_are_validated_before_becoming_symmetric_graph_edges() -> None:
    columns = [
        _column("col::customers::customer_name", "customer_name", "table::customers"),
        _column("col::orders::client_label", "client_label", "table::orders"),
        _column("col::customers::nickname", "nickname", "table::customers"),
    ]
    response = """```json
    {"relations": [
      {"source": "col::customers::customer_name", "target": "col::orders::client_label", "confidence": 0.93, "reason": "同一客户名称"},
      {"source": "col::customers::customer_name", "target": "col::customers::nickname", "confidence": 0.99, "reason": "同表关系应忽略"},
      {"source": "col::missing::field", "target": "col::orders::client_label", "confidence": 0.99},
      {"source": "col::customers::customer_name", "target": "col::orders::client_label", "confidence": 0.40}
    ]}
    ```"""
    discoverer = implicit.LLMImplicitRelationDiscoverer(completion=lambda _prompt: response)

    relations = ImplicitRelationExtractor(
        semantic_discoverer=discoverer,
        similarity_threshold=0.85,
    ).extract(EntityGraph(columns=columns))

    semantic = [relation for relation in relations if relation.predicate == REL_SIMILAR_TO]
    assert [(relation.subject_id, relation.object_id) for relation in semantic] == [
        ("col::customers::customer_name", "col::orders::client_label"),
        ("col::orders::client_label", "col::customers::customer_name"),
    ]
    assert all(relation.confidence == 0.93 for relation in semantic)
    assert all(relation.properties == {"source": "llm", "reason": "同一客户名称"} for relation in semantic)


def test_llm_failure_keeps_lightweight_rule_based_relations_available() -> None:
    columns = [
        _column("col::customers::customer_id", "customer_id", "table::customers", is_pk=True),
        _column("col::orders::customer_id", "customer_id", "table::orders"),
    ]

    def unavailable(_prompt: str) -> str:
        raise RuntimeError("LLM is not configured")

    relations = ImplicitRelationExtractor(
        semantic_discoverer=implicit.LLMImplicitRelationDiscoverer(completion=unavailable),
    ).extract(EntityGraph(columns=columns))

    assert any(
        relation.predicate == REL_POTENTIAL_FK
        and relation.subject_id == "col::orders::customer_id"
        and relation.object_id == "col::customers::customer_id"
        for relation in relations
    )
    assert not any(relation.predicate == REL_SIMILAR_TO for relation in relations)


def test_disabling_llm_keeps_deterministic_relations_without_remote_call() -> None:
    columns = [
        _column("col::customers::customer_id", "customer_id", "table::customers", is_pk=True),
        _column("col::orders::customer_id", "customer_id", "table::orders"),
    ]

    class ForbiddenDiscoverer:
        def discover(self, *_args, **_kwargs):
            raise AssertionError("LLM discovery must remain disabled")

    relations = ImplicitRelationExtractor(
        enable_llm_semantics=False,
        semantic_discoverer=ForbiddenDiscoverer(),
    ).extract(EntityGraph(columns=columns))

    assert any(
        relation.predicate == REL_POTENTIAL_FK
        and relation.subject_id == "col::orders::customer_id"
        and relation.object_id == "col::customers::customer_id"
        for relation in relations
    )


def test_similarity_threshold_must_be_finite_and_bounded() -> None:
    import pytest

    for invalid in (float("nan"), float("inf"), -0.01, 1.01):
        with pytest.raises(ValueError, match="similarity_threshold"):
            ImplicitRelationExtractor(similarity_threshold=invalid)


def test_missing_llm_configuration_skips_remote_semantic_discovery() -> None:
    calls = 0

    def config_loader() -> dict[str, str]:
        nonlocal calls
        calls += 1
        return {"api_key": "", "base_url": "https://example.invalid", "model": "demo"}

    columns = [
        _column("col::customers::customer_name", "customer_name", "table::customers"),
        _column("col::orders::client_label", "client_label", "table::orders"),
    ]
    discoverer = implicit.LLMImplicitRelationDiscoverer(config_loader=config_loader)

    assert discoverer.discover(columns, similarity_threshold=0.85) == []
    assert calls == 1


def test_configured_llm_gateway_drives_semantic_relation_discovery(monkeypatch) -> None:
    import httpx

    request: dict = {}

    class Response:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict:
            return {
                "choices": [{
                    "message": {
                        "content": (
                            '{"relations":[{"source":"col::customers::customer_name",'
                            '"target":"col::orders::client_label","confidence":0.91}]}'
                        )
                    }
                }]
            }

    def post(url, *, headers, json, timeout):
        request.update({"url": url, "headers": headers, "json": json, "timeout": timeout})
        return Response()

    monkeypatch.setattr(httpx, "post", post)
    columns = [
        _column("col::customers::customer_name", "customer_name", "table::customers"),
        _column("col::orders::client_label", "client_label", "table::orders"),
    ]
    discoverer = implicit.LLMImplicitRelationDiscoverer(config_loader=lambda: {
        "api_key": "configured-key",
        "base_url": "https://llm.example/v1",
        "model": "configured-model",
    })

    relations = discoverer.discover(columns, similarity_threshold=0.85)

    assert len(relations) == 2
    assert request["url"] == "https://llm.example/v1/chat/completions"
    assert request["json"]["model"] == "configured-model"
