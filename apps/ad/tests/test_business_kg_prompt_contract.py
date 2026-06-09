from kg_builder.business_kg.llm_builder import _ONTOLOGY_PREAMBLE, _SYSTEM_PROMPT


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
