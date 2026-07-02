from kg_builder.nlq.service import MeasureMeta, NaturalLanguageQueryService
from kg_builder.analysis.insight_analyzer import InsightAnalyzer


def _service() -> NaturalLanguageQueryService:
    service = NaturalLanguageQueryService("unused.ttl", "http://unused")
    service._load_if_needed = lambda: None
    service._resolve_question_intent = lambda _question, _mode: {}
    service._measures = {
        "MEAS_catalog_net_profit": MeasureMeta(
            code="MEAS_catalog_net_profit",
            cn_name="目录净利润",
            tables={"catalog_sales"},
            search_text="目录净利润 利润",
        ),
        "MEAS_web_net_profit": MeasureMeta(
            code="MEAS_web_net_profit",
            cn_name="网络净利润",
            tables={"web_sales"},
            search_text="网络净利润 利润",
        ),
        "MEAS_web_sales_amount": MeasureMeta(
            code="MEAS_web_sales_amount",
            cn_name="网络销售金额",
            tables={"web_sales"},
            search_text="网络销售金额 销售 金额",
        ),
    }
    return service


def test_follow_up_inherits_fact_table_and_time_filter() -> None:
    service = _service()
    first = service.query("查询最近一个月网络销售金额", execute=False)

    assert first["ok"] is True
    assert first["matched"]["measureCode"] == "MEAS_web_sales_amount"
    assert first["resolvedContext"]["factTables"] == ["web_sales"]

    follow_up = service.query(
        "标价金额高但净利润增幅较小，是否存在成本控制问题",
        execute=False,
        context=first["resolvedContext"],
        is_follow_up=True,
    )

    assert follow_up["ok"] is True
    assert follow_up["matched"]["measureCode"] == "MEAS_web_net_profit"
    assert follow_up["matched"]["factTables"] == ["web_sales"]
    assert follow_up["intent"]["filters"] == first["intent"]["filters"]
    assert follow_up["diagnostics"]["contextInherited"] is True


def test_same_follow_up_without_context_still_requires_clarification() -> None:
    service = _service()
    response = service.query(
        "标价金额高但净利润增幅较小，是否存在成本控制问题",
        execute=False,
    )

    assert response["ok"] is False
    assert response["action"] == "clarify"
    assert response["diagnosticCode"] == "METRIC_AMBIGUOUS"
    assert response["recoverable"] is True
    assert response["needsClarification"] is True
    assert response["clarification"] == "指标匹配不唯一，请明确要查哪个指标"


def test_missing_metric_is_structured_reject_not_clarification() -> None:
    service = _service()
    response = service.query("查询退款率", execute=False)

    assert response["ok"] is False
    assert response["action"] == "reject"
    assert response["diagnosticCode"] == "METRIC_NOT_FOUND"
    assert response["recoverable"] is False
    assert response["needsClarification"] is False
    assert response["clarification"] == "没有在业务图谱中匹配到指标"


def test_deep_insight_metric_match_honors_inherited_fact_table() -> None:
    analyzer = InsightAnalyzer(
        data_agent_url="http://unused",
        ttl_path="unused.ttl",
        llm_config={},
        log_cb=lambda _message: None,
        context={"factTables": ["web_sales"]},
    )
    analyzer._kg_meas_table_cache = {
        "MEAS_catalog_net_profit": ["catalog_sales"],
        "MEAS_web_net_profit": ["web_sales"],
    }
    analyzer._kg_table_dims_cache = {"web_sales": []}
    analyzer._kg_dim_detail_cache = {}
    analyzer._kg_dim_histogram_cache = {}
    analyzer._load_kg_cache = lambda: {
        "MEAS_catalog_net_profit": {"cn_name": "目录净利润"},
        "MEAS_web_net_profit": {"cn_name": "网络净利润"},
    }

    matched = analyzer._find_meas_in_kg(["净利润"], "净利润增幅较小")

    assert matched is not None
    assert matched["meas_code"] == "MEAS_web_net_profit"
    assert matched["table_name"] == "web_sales"
