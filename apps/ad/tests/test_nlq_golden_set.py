import json
from pathlib import Path


def test_golden_nlq_set_has_core_coverage() -> None:
    path = Path(__file__).with_name("golden_nlq_cases.json")
    cases = json.loads(path.read_text(encoding="utf-8"))

    assert len(cases) >= 50
    ids = [case["id"] for case in cases]
    assert len(ids) == len(set(ids))

    categories = {case["category"] for case in cases}
    assert {
        "aggregate",
        "dimension",
        "detail",
        "entity_lookup",
        "problem_orders",
        "explain",
        "relationship_analysis",
        "clarify",
        "reject",
        "validation",
        "trace",
    }.issubset(categories)

    for case in cases:
        assert case.get("question")
        assert isinstance(case.get("expected"), dict)
