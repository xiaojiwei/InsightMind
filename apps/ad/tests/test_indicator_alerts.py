from pathlib import Path

from kg_builder.alerts import annotate_semantic_result


def test_semantic_result_marks_self_and_path_alerts(tmp_path: Path):
    result = {
        "data": [
            {"ad.city": "全国", "ad.sales": 100},
            {"ad.city": "北京", "ad.sales": 900},
            {"ad.city": "上海", "ad.sales": 0},
            {"ad.city": "广州", "ad.sales": 110},
            {"ad.city": "深圳", "ad.sales": 105},
        ],
        "diagnostics": {},
    }
    query = {"measures": ["ad.sales"], "dimensions": ["ad.city"]}
    catalog = {"measures": [{"code": "MEAS_sales"}]}

    annotated = annotate_semantic_result(result, query, catalog, tmp_path / "missing.ttl")

    alerts = annotated["alerts"]
    assert alerts["count"] >= 2
    assert alerts["maxLevel"] == 3
    assert any(a["type"] == "self" for row in annotated["data"] for a in row.get("__alerts", []))
    assert any(a["type"] == "path" for row in annotated["data"] for a in row.get("__alerts", []))


def test_semantic_result_marks_expression_internal_alert(tmp_path: Path):
    from kg_builder.alerts import indicator_alerts

    ttl = tmp_path / "indicator-data.ttl"
    ttl.write_text("", encoding="utf-8")
    result = {
        "data": [
            {"ad.week": "202601", "ad.c": 100},
            {"ad.week": "202602", "ad.c": 102},
            {"ad.week": "202603", "ad.c": 101},
            {"ad.week": "202604", "ad.c": 100},
        ],
        "diagnostics": {},
    }
    query = {"measures": ["ad.c"], "dimensions": ["ad.week"]}
    catalog = {
        "measures": [
            {"code": "MEAS_c"},
            {"code": "MEAS_a"},
            {"code": "MEAS_b"},
        ]
    }

    def load_fn(child_query):
        assert set(child_query["measures"]) == {"ad.a", "ad.b"}
        return {
            "data": [
                {"ad.week": "202601", "ad.a": 50, "ad.b": 50},
                {"ad.week": "202602", "ad.a": 51, "ad.b": 51},
                {"ad.week": "202603", "ad.a": 0, "ad.b": 101},
                {"ad.week": "202604", "ad.a": 49, "ad.b": 51},
            ],
            "diagnostics": {},
        }

    original = indicator_alerts._expression_meta
    indicator_alerts._expression_meta = lambda _path: {"MEAS_c": {"operands": ["MEAS_a", "MEAS_b"]}}
    try:
        annotated = annotate_semantic_result(result, query, catalog, ttl, load_fn)
    finally:
        indicator_alerts._expression_meta = original

    row_alerts = [a for row in annotated["data"] for a in row.get("__alerts", [])]
    assert any(a["type"] == "expression" for a in row_alerts)
    assert annotated["alerts"]["maxLevel"] == 3
