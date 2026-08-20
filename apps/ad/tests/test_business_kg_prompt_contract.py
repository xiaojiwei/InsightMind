from pathlib import Path

from kg_builder.business_kg.llm_builder import BusinessKGBuilder, _ONTOLOGY_PREAMBLE, _SYSTEM_PROMPT


def test_ontology_documents_attribute_dimension_expr_contract():
    assert "dimTypeCode=2" in _ONTOLOGY_PREAMBLE
    assert "dimColumn 与 dimPrimaryKey 不同" in _ONTOLOGY_PREAMBLE
    assert "{d}.<dimColumn>" in _ONTOLOGY_PREAMBLE
    assert "{d}.w_city" in _ONTOLOGY_PREAMBLE


def test_system_prompt_requires_dim_column_expr_for_attribute_dimensions():
    assert "dimColumn != dimPrimaryKey" in _SYSTEM_PROMPT
    assert 'ind:dimColumnExpr "{d}.<属性列>"' in _SYSTEM_PROMPT
    assert "仓库名/仓库城市" in _SYSTEM_PROMPT
    assert "Unknown column" in _SYSTEM_PROMPT


def test_business_scenario_context_is_labeled_as_modeling_constraint():
    section = BusinessKGBuilder._build_scenario_section("@prefix ex: <urn:example:> .")

    assert "默认业务场景本体" in section
    assert "不得虚构不存在的表或列" in section
    assert "```turtle" in section
    assert "@prefix ex:" in section


def test_default_business_scenario_asset_is_packaged():
    scenario_path = (
        Path(__file__).parents[1]
        / "kg_builder"
        / "business_kg"
        / "default-business-scenario.ttl"
    )

    content = scenario_path.read_text(encoding="utf-8")
    assert "Li Auto CELN Customer Follow-up Scenario Ontology" in content
    assert ":Li-HasStage" in content


def test_accuracy_validation_uses_business_graph_selector():
    template_path = (
        Path(__file__).parents[1]
        / "kg_builder"
        / "web"
        / "templates"
        / "index.html"
    )
    template = template_path.read_text(encoding="utf-8")
    function_body = template.split("async function runBkgValidate()", 1)[1].split(
        "let _bkvResults", 1
    )[0]

    assert "getElementById('bz-file-select')" in function_body
    assert "getElementById('bkg-file-select')" not in function_body
    assert "'/api/business-kg/validate'" in function_body
    assert "method: 'POST'" in function_body
    assert "new EventSource" not in function_body


def test_cached_source_graph_validation_request_falls_back_to_business_graph(tmp_path, monkeypatch):
    import web_app

    output_dir = tmp_path / "output"
    business_dir = output_dir / "business_kg"
    business_dir.mkdir(parents=True)
    source_graph = output_dir / "kg_tpcds.ttl"
    business_graph = business_dir / "indicator-data.ttl"
    source_graph.write_text("source", encoding="utf-8")
    business_graph.write_text("business", encoding="utf-8")

    monkeypatch.setattr(web_app, "OUTPUT_DIR", output_dir)
    monkeypatch.setattr(web_app, "BKG_DIR", business_dir)
    monkeypatch.setattr(web_app, "_current_bkg_path", None)

    assert web_app._resolve_bkg_path("kg_tpcds.ttl") == business_graph
    assert web_app._resolve_bkg_path("missing.ttl") is None
