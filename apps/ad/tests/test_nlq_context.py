from kg_builder.nlq.service import MeasureMeta, NaturalLanguageQueryService
from kg_builder.analysis.insight_analyzer import InsightAnalyzer


class _FakeHttpResponse:
    def __init__(self, payload: dict):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self) -> bytes:
        import json

        return json.dumps(self.payload, ensure_ascii=False).encode("utf-8")


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


def test_confirmed_clarification_measure_becomes_governed_semantic_hint() -> None:
    service = _service()

    intent = service._apply_conversation_context(
        {},
        {"confirmedMeasureCode": "MEAS_web_sales_amount"},
        is_follow_up=True,
    )
    invalid = service._apply_conversation_context(
        {},
        {"confirmedMeasureCode": "MEAS_not_in_graph"},
        is_follow_up=True,
    )

    assert intent["measureCode"] == "MEAS_web_sales_amount"
    assert intent["clarificationConfirmed"] is True
    assert "measureCode" not in invalid


def test_confirmed_clarification_resolves_ambiguous_metric_question() -> None:
    service = _service()
    service._semantic_map = lambda *_args, **_kwargs: {
        "measureCandidates": [],
        "dimensionCandidates": [],
        "valueBindings": [],
        "decision": "clarify",
        "needsClarification": True,
        "diagnostics": {
            "unresolvedValueIntent": True,
            "unexplainedTokens": ["利润"],
        },
    }

    response = service.query(
        "查询利润",
        execute=False,
        context={"confirmedMeasureCode": "MEAS_web_net_profit"},
        is_follow_up=True,
    )

    assert response["ok"] is True
    assert response["matched"]["measureCode"] == "MEAS_web_net_profit"
    assert "confirmedMeasureCode" not in response["resolvedContext"]


def test_missing_metric_is_structured_reject_not_clarification() -> None:
    service = _service()
    response = service.query("查询退款率", execute=False)

    assert response["ok"] is False
    assert response["action"] == "reject"
    assert response["diagnosticCode"] == "METRIC_NOT_FOUND"
    assert response["recoverable"] is False
    assert response["needsClarification"] is False
    assert response["clarification"] == "没有在业务图谱中匹配到指标"


def test_query_trace_id_is_forwarded_to_data_agent() -> None:
    service = _service()
    captured = {}

    def fake_execute(payload):
        captured.update(payload)
        return {"code": "0", "data": {"cellList": []}}

    service._execute_da = fake_execute
    response = service.query(
        "查询网络销售金额", trace_id="trace-forward-1", execute=True
    )

    assert captured["traceId"] == "trace-forward-1"
    assert response["daPayload"]["traceId"] == "trace-forward-1"
    assert response["planStatus"] == "ready"
    assert response["analysisSpec"]["semantic"]["measureCodes"] == [
        "MEAS_web_sales_amount"
    ]
    assert "daPayload" not in response["analysisSpec"]


def test_data_agent_request_inherits_authorization(monkeypatch) -> None:
    service = NaturalLanguageQueryService(
        "unused.ttl",
        "http://unused",
        authorization="Bearer user-token",
    )
    captured = {}

    def fake_urlopen(request, timeout):
        captured["authorization"] = request.get_header("Authorization")
        captured["timeout"] = timeout
        return _FakeHttpResponse({"code": 200, "data": {"cellList": []}})

    monkeypatch.setattr("kg_builder.nlq.service.urlopen", fake_urlopen)
    result = service._execute_da({
        "configureList": [{"code": "MEAS_web_sales_amount"}],
        "filterList": [],
        "pageSize": 1,
        "pageNum": 1,
    })

    assert result["ok"] is True
    assert captured == {"authorization": "Bearer user-token", "timeout": 30}


def test_llm_measure_match_requires_confirmation_not_execution() -> None:
    service = _service()
    top = service._measures["MEAS_web_sales_amount"]
    service._rank_measures = lambda _question, _tokens: [(10.0, top)]
    service._llm_map_to_kg_measures = lambda _question, _hits: {
        "measure": top,
        "reason": "问题里的收入可对应销售金额",
        "matchedTerms": ["收入"],
    }

    response = service.query("查询网络收入", execute=True)

    assert response["ok"] is False
    assert response["action"] == "clarify"
    assert response["diagnosticCode"] == "LLM_MEDIUM_CONFIDENCE"
    assert response["daPayload"] is None
    assert response["matched"]["measureCode"] == "MEAS_web_sales_amount"
    assert response["diagnostics"]["llmMeasureMatch"]["confidenceLevel"] == "medium"
    assert response["diagnostics"]["llmMeasureMatch"]["matchedTerms"] == ["收入"]
    assert response["planStatus"] == "requires_input"
    assert response["clarificationSpec"]["requiredSlots"] == ["measure"]
    assert response["clarificationSpec"]["resumeToken"]
    assert "activeMeasureCode" not in response["resolvedContext"]
    assert "measureCodes" not in response["resolvedContext"]


def test_llm_measure_conflict_with_rule_candidate_requires_clarification() -> None:
    service = _service()
    rule_top = service._measures["MEAS_catalog_net_profit"]
    llm_pick = service._measures["MEAS_web_net_profit"]
    service._rank_measures = lambda _question, _tokens: [(10.0, rule_top), (9.0, llm_pick)]
    service._llm_map_to_kg_measures = lambda _question, _hits: {
        "measure": llm_pick,
        "reason": "问题提到了网络场景",
        "matchedTerms": ["网络"],
    }

    response = service.query("网络利润情况", execute=True)

    assert response["ok"] is False
    assert response["action"] == "clarify"
    assert response["diagnosticCode"] == "METRIC_AMBIGUOUS"
    assert response["daPayload"] is None
    assert "不一致" in response["clarification"]
    assert response["diagnostics"]["llmMeasureMatch"]["code"] == "MEAS_web_net_profit"


def test_llm_measure_mapper_rejects_non_candidate_code(monkeypatch) -> None:
    service = _service()
    hit = [(10.0, service._measures["MEAS_web_sales_amount"])]

    monkeypatch.setattr(
        "kg_builder.utils.llm_config.llm_config_from_env",
        lambda _cwd: {"api_key": "k", "base_url": "http://llm", "model": "m"},
    )
    monkeypatch.setattr(
        "urllib.request.urlopen",
        lambda *_args, **_kwargs: _FakeHttpResponse({
            "choices": [{
                "message": {
                    "content": (
                        '{"code":"MEAS_fabricated","reason":"看起来相关",'
                        '"matchedTerms":["收入"]}'
                    )
                }
            }]
        }),
    )

    assert service._llm_map_to_kg_measures("查询收入", hit) is None


def test_llm_measure_mapper_requires_reason_and_matched_terms(monkeypatch) -> None:
    service = _service()
    hit = [(10.0, service._measures["MEAS_web_sales_amount"])]

    monkeypatch.setattr(
        "kg_builder.utils.llm_config.llm_config_from_env",
        lambda _cwd: {"api_key": "k", "base_url": "http://llm", "model": "m"},
    )
    monkeypatch.setattr(
        "urllib.request.urlopen",
        lambda *_args, **_kwargs: _FakeHttpResponse({
            "choices": [{
                "message": {
                    "content": '{"code":"MEAS_web_sales_amount","reason":"相关","matchedTerms":[]}'
                }
            }]
        }),
    )

    assert service._llm_map_to_kg_measures("查询收入", hit) is None


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


def test_deep_insight_preserves_explicit_requested_dimension() -> None:
    analyzer = InsightAnalyzer(
        data_agent_url="http://unused",
        ttl_path="unused.ttl",
        llm_config={},
        log_cb=lambda _message: None,
    )
    analyzer._kg_meas_table_cache = {"MEAS_web_net_profit": ["web_sales"]}
    analyzer._kg_table_dims_cache = {
        # Simulate a legacy graph whose table association omits a dimension
        # that is nevertheless present in DA's deterministic catalogue.
        "web_sales": ["DIM_store"],
    }
    analyzer._kg_dim_detail_cache = {
        "DIM_province": {"cn_name": "省份"},
        "DIM_store": {"cn_name": "门店"},
    }
    analyzer._kg_dim_histogram_cache = {}
    analyzer._load_kg_cache = lambda: {
        "MEAS_web_net_profit": {"cn_name": "网络净利润"},
    }

    matched = analyzer._find_meas_in_kg(
        ["网络净利润"],
        "查看网络净利润在省份的贡献结构",
    )

    assert matched is not None
    assert matched["requested_dim_codes"] == ["DIM_province"]
    params = analyzer._build_query_params(matched, {
        "gran": "month",
        "time_start": "2026-08-01",
        "time_end": "2026-08-31",
        "prev_start": "2026-07-01",
        "prev_end": "2026-07-31",
    })
    configured = [item["code"] for item in params["configureList"]]
    assert "DIM_province" in configured
    assert "DIM_store" not in configured


def test_fallback_direct_answer_promotes_requested_dimension_to_conclusion() -> None:
    report = """## 综合报告
- 核销量 从 152 变为 90，变化幅度 -40.79%。
- 核销量：152 -> 90。
- 省份 的贡献变化合计为 0.156。
  - 其中 上海_001 的贡献/变化约为 0.002。
  - 其中 上海_002 的贡献/变化约为 0.001。
- 战区 的整体变化幅度为 -40.79%。
- 先按营销场景下钻。
"""

    answer = InsightAnalyzer._fallback_direct_answer(
        "分析不同省份的核销量差异原因", "核销量", report, {}, "LLM 暂时不可用"
    )

    conclusion = answer.split("## 关键证据", 1)[0]
    assert "省份当前可确认的差异/归因线索" in conclusion
    assert "上海_001" in conclusion
    assert "继续沿「省份」下钻" in answer


def test_deep_insight_empty_report_falls_back_to_local_answer() -> None:
    analyzer = InsightAnalyzer(
        data_agent_url="http://unused",
        ttl_path="unused.ttl",
        llm_config={},
        log_cb=lambda _message: None,
    )

    chunks = list(analyzer._stream_insight_answer(
        "分析网络销售金额变化原因",
        {"anomaly_profile": {}},
        {"meas_code": "MEAS_web_sales_amount", "cn_name": "网络销售金额"},
        {
            1: {"measures": [{
                "col": "MEAS_web_sales_amount",
                "cn_name": "网络销售金额",
                "current": 120,
                "previous": 100,
                "change": 20,
                "change_pct": 20.0,
            }]},
        },
        "",
    ))

    answer = "".join(chunks)
    assert "分析报告尚未生成" not in answer
    assert "网络销售金额" in answer
    assert "## 结论" in answer


def test_attribute_expression_is_not_misread_as_specific_order_lookup() -> None:
    from web_app import _parse_specific_document_lookup

    attribute_question = (
        "DWS-战区日粒度权益销售聚合-有销售额的订单量"
        "（order amount>0的记录数） ： 1"
    )

    assert _parse_specific_document_lookup(attribute_question) == {}
    assert _parse_specific_document_lookup("订单编号 ： 8000000") == {
        "fieldText": "订单编号",
        "value": "8000000",
    }


def test_long_generated_attribute_example_is_parseable() -> None:
    service = _service()
    question = (
        "DWS-战区日粒度权益销售聚合-有销售额的订单量"
        "（order amount>0的记录数） ： 1"
    )

    parsed = service._parse_entity_lookup(question)

    assert parsed["fieldText"].endswith("（order amount>0的记录数）")
    assert parsed["value"] == "1"
