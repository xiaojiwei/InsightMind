import datetime

from kg_builder.analysis.cell_insight import CellInsightService
from kg_builder.analysis.insight_analyzer import InsightAnalyzer


def _analyzer(context=None):
    return InsightAnalyzer(
        data_agent_url="http://127.0.0.1:8091",
        ttl_path="/tmp/missing-indicator-data.ttl",
        llm_config={},
        log_cb=lambda _msg: None,
        context=context or {},
    )


def test_infer_metric_drop_anomaly_from_question():
    analyzer = _analyzer()

    profile = analyzer._infer_anomaly_profile(
        "为什么3月15号NSS下滑",
        {"anomaly_type": "unknown", "anomaly_direction": "unknown"},
    )

    assert profile["type"] == "metric_drop"
    assert profile["direction"] == "down"
    assert "下降" in profile["label"]
    assert profile["source"] == "question_intent"
    assert profile["shape"] == "drop"
    assert profile["confidence"] >= 0.6


def test_infer_common_anomaly_shapes_from_question():
    analyzer = _analyzer()

    cases = [
        ("投诉率为什么偏高", "metric_rise", "up"),
        ("订单量突然突增是什么原因", "metric_spike", "spike"),
        ("NPS趋势异常，最近走势不稳定", "trend_anomaly", "volatile"),
        ("销售额是不是有数据质量问题，出现null和重复", "data_quality", "unknown"),
    ]
    for question, expected_type, expected_direction in cases:
        profile = analyzer._infer_anomaly_profile(
            question,
            {"anomaly_type": "unknown", "anomaly_direction": "unknown"},
        )
        assert profile["type"] == expected_type
        assert profile["direction"] == expected_direction


def test_cell_context_turns_into_dimension_slice_and_prioritizes_dims():
    context = {
        "cellInsight": {
            "cellContext": {
                "filters": [
                    {"code": "DIM_date_day", "name": "日期", "value": "2026-03-15"},
                    {"code": "DIM_store", "name": "门店", "value": "上海店"},
                ]
            },
            "contributions": [
                {"dimensionCode": "DIM_city", "dimensionName": "城市", "score": 80}
            ],
        }
    }
    analyzer = _analyzer(context)

    profile = analyzer._infer_anomaly_profile(
        "这个单元格为什么异常",
        {"anomaly_type": "unknown", "anomaly_direction": "unknown"},
    )
    ordered = analyzer._prioritized_regular_dims(
        ["DIM_category", "DIM_city", "DIM_store"],
        {"anomaly_profile": profile},
    )
    filters = analyzer._cell_context_dimension_filters("DIM_date_day")

    assert profile["type"] == "dimension_slice"
    assert profile["source"] == "cell_context"
    assert profile["shape"] == "slice"
    assert profile["evidence_strength"] >= 55
    assert ordered[:2] == ["DIM_store", "DIM_city"]
    assert filters == [{
        "code": "DIM_store",
        "operatorList": [{
            "sqlOprType": 0,
            "dataList": ["上海店"],
            "sqlLogicalType": 0,
            "timeRange": 0,
        }],
        "internal": True,
    }]


def test_explicit_december_time_range_stays_in_same_year():
    analyzer = _analyzer()

    time_info = analyzer._rule_based_time(
        "分析12月销售额",
        datetime.date(2026, 6, 19),
        2026,
        25,
    )

    assert time_info["time_start"] == "2026-12-01"
    assert time_info["time_end"] == "2026-12-31"
    assert time_info["prev_start"] == "2026-11-01"
    assert time_info["prev_end"] == "2026-11-30"


def test_explicit_iso_date_range_uses_requested_daily_granularity():
    analyzer = _analyzer()

    time_info = analyzer._rule_based_time(
        "分析 2026-02-02 至 2026-02-15 平均电话质量分的按日趋势",
        datetime.date(2026, 7, 24),
        2026,
        30,
    )

    assert time_info == {
        "time_start": "2026-02-02",
        "time_end": "2026-02-15",
        "prev_start": "2026-01-19",
        "prev_end": "2026-02-01",
        "gran": "day",
        "time_desc": "2026-02-02 至 2026-02-15",
    }


def test_explicit_chinese_date_range_can_omit_second_year():
    analyzer = _analyzer()

    time_info = analyzer._rule_based_time(
        "分析2026年2月2日至2月15日的质量分趋势",
        datetime.date(2026, 7, 24),
        2026,
        30,
    )

    assert time_info["time_start"] == "2026-02-02"
    assert time_info["time_end"] == "2026-02-15"
    assert time_info["gran"] == "day"


def test_recent_seven_days_is_not_replaced_by_current_month():
    analyzer = _analyzer()

    time_info = analyzer._rule_based_time(
        "分析最近7天平均电话质量分的趋势",
        datetime.date(2026, 7, 24),
        2026,
        30,
    )

    assert time_info["time_start"] == "2026-07-18"
    assert time_info["time_end"] == "2026-07-24"
    assert time_info["prev_start"] == "2026-07-11"
    assert time_info["prev_end"] == "2026-07-17"
    assert time_info["gran"] == "day"


def test_explicit_question_time_overrides_cell_time_context():
    analyzer = _analyzer({
        "cellInsight": {
            "cellContext": {
                "filters": [{"code": "DIM_date_day", "name": "日期", "value": "2026-07-02"}]
            }
        }
    })

    time_info = analyzer._rule_based_time(
        "分析2026-02-02至2026-02-15的质量分趋势",
        datetime.date(2026, 7, 24),
        2026,
        30,
    )

    assert time_info["time_start"] == "2026-02-02"
    assert time_info["time_end"] == "2026-02-15"


def test_decimal_string_view_type_is_treated_as_time_context():
    analyzer = _analyzer()

    assert analyzer._is_time_context_filter({
        "code": "DIM_date_day",
        "name": "日期",
        "value": "2026-03-15",
        "viewType": "2.0",
    })


def test_query_params_keep_anomaly_profile_and_cell_filters():
    context = {
        "cellInsight": {
            "cellContext": {
                "filters": [
                    {"code": "DIM_date_day", "name": "日期", "value": "2026-03-15"},
                    {"code": "DIM_store", "name": "门店", "value": "上海店"},
                ]
            }
        }
    }
    analyzer = _analyzer(context)
    meas_info = {
        "primary": {"meas_code": "MEAS_sales", "cn_name": "销售额", "table_name": "fact_sales"},
        "secondary": [{"meas_code": "MEAS_order_count", "cn_name": "订单数", "table_name": "fact_sales"}],
        "time_dims": {"day": "DIM_date_day", "week": "DIM_date_week", "month": "DIM_date_month"},
        "dim_codes": ["DIM_city", "DIM_store"],
    }

    params = analyzer._build_query_params(meas_info, {
        "gran": "day",
        "time_start": "2026-03-15",
        "time_end": "2026-03-15",
        "prev_start": "2026-03-14",
        "prev_end": "2026-03-14",
        "anomaly_profile": {
            "type": "dimension_slice",
            "focus_filters": [{"code": "DIM_store"}],
        },
    })

    assert params["_anomalyProfile"]["type"] == "dimension_slice"
    assert params["configureList"][3]["code"] == "DIM_store"
    assert any(item["code"] == "DIM_store" for item in params["filterList"])


def test_month_request_falls_back_to_week_filter_encoding():
    analyzer = _analyzer()
    meas_info = {
        "primary": {"meas_code": "MEAS_sales", "cn_name": "销售额", "table_name": "fact_sales"},
        "secondary": [],
        "time_dims": {"week": "DIM_date_week"},
        "dim_codes": [],
    }

    params = analyzer._build_query_params(meas_info, {
        "gran": "month",
        "time_start": "2026-03-01",
        "time_end": "2026-03-31",
        "prev_start": "2026-02-01",
        "prev_end": "2026-02-28",
    })

    time_filter = params["filterList"][0]
    assert params["_gran"] == "week"
    assert params["_requestedGran"] == "month"
    assert time_filter["code"] == "DIM_date_week"
    assert all(len(value) == 6 for value in time_filter["operatorList"][0]["dataList"])


def test_daily_query_uses_day_dimension_and_preserves_semantic_range():
    analyzer = _analyzer()
    meas_info = {
        "primary": {"meas_code": "MEAS_quality", "cn_name": "平均电话质量分", "table_name": "fact_call"},
        "secondary": [],
        "time_dims": {
            "day": "DIM_date_day",
            "week": "DIM_date_week",
            "month": "DIM_date_month",
        },
        "dim_codes": ["DIM_sales_expert", "DIM_sop_stage"],
    }

    params = analyzer._build_query_params(meas_info, {
        "gran": "day",
        "time_start": "2026-02-02",
        "time_end": "2026-02-15",
        "prev_start": "2026-01-19",
        "prev_end": "2026-02-01",
    })

    time_filter = params["filterList"][0]
    assert params["_gran"] == "day"
    assert params["_timeRange"]["time_start"] == "2026-02-02"
    assert params["_timeRange"]["time_end"] == "2026-02-15"
    assert time_filter["code"] == "DIM_date_day"
    assert time_filter["viewType"] == 1
    assert time_filter["operatorList"][0]["dataList"] == ["2026-01-19", "2026-02-15"]


def test_invalid_iso_week_uses_default_time_range():
    analyzer = _analyzer()

    time_info = analyzer._rule_based_time(
        "分析第53周销售额",
        datetime.date(2027, 6, 19),
        2027,
        25,
    )

    assert time_info["gran"] == "month"


def test_metric_alert_cell_is_not_classified_as_document_trace():
    service = CellInsightService({
        "measures": [{"code": "MEAS_sales", "dimensionCodes": ["DIM_city"]}],
        "dimensions": [{"code": "DIM_city", "name": "城市"}],
    })

    result = service.explain({
        "measureCode": "MEAS_sales",
        "measureName": "销售额",
        "cellValue": 16051,
        "rowPath": [{"code": "DIM_city", "name": "城市", "value": "Guangzhou"}],
        "alertResults": [
            {"type": "path", "label": "环比波动", "severity": "warning", "reason": "环比下滑"}
        ],
        "documentAlertResults": [],
    })

    assert result["documents"] == []
    assert result["alerts"][0]["type"] == "path"
    assert result["anomaly"]["type"] == "trend_anomaly"
    assert result["anomaly"]["source"] == "metric_alert"
    assert result["diagnosis"]["shape"] == "trend"
    assert result["diagnosis"]["confidence"] >= 0.7
    assert any(item["type"] == "alert" for item in result["diagnosis"]["evidence"])
    assert result["anomaly"]["title"] == "发现指标预警"


def test_context_anomaly_type_guides_insight_profile():
    analyzer = _analyzer({
        "anomalyType": "trend_anomaly",
        "cellInsight": {
            "cellContext": {
                "filters": [{"code": "DIM_city", "name": "城市", "value": "Guangzhou"}]
            },
            "anomaly": {"type": "trend_anomaly"},
            "documents": [],
        },
    })

    profile = analyzer._infer_anomaly_profile(
        "请分析这个单元格异常",
        {"anomaly_type": "unknown", "anomaly_direction": "unknown"},
    )

    assert profile["type"] == "trend_anomaly"
    assert profile["source"] == "cell_context"
    assert profile["shape"] == "trend"


def test_document_cell_insight_has_strong_document_evidence():
    service = CellInsightService({
        "measures": [{"code": "MEAS_sales", "dimensionCodes": ["DIM_city"]}],
        "dimensions": [{"code": "DIM_city", "name": "城市"}],
    })

    result = service.explain({
        "measureCode": "MEAS_sales",
        "measureName": "销售额",
        "cellValue": 16051,
        "rowPath": [{"code": "DIM_city", "name": "城市", "value": "Guangzhou"}],
        "documentAlertResults": [{
            "rule": {"name": "折扣异常", "severity": "critical"},
            "result": {
                "targetColumn": "discount_amt",
                "targetColumnName": "折扣金额",
                "summary": {"matchedRows": 1},
                "matches": [{
                    "orderNumber": "234",
                    "targetValue": 0,
                    "record": {"DIM_city": "Guangzhou", "折扣金额": 0},
                }],
            },
        }],
    })

    assert result["anomaly"]["type"] == "document_trace"
    assert result["diagnosis"]["source"] == "document_rule"
    assert result["diagnosis"]["shape"] == "document"
    assert result["diagnosis"]["evidenceStrength"] >= 80
    assert result["diagnosis"]["confidence"] >= 0.9
    assert any(item["type"] == "document" for item in result["diagnosis"]["evidence"])


def test_insight_profile_reuses_cell_diagnosis_evidence():
    analyzer = _analyzer({
        "anomalyType": "document_trace",
        "cellInsight": {
            "cellContext": {
                "filters": [{"code": "DIM_city", "name": "城市", "value": "Guangzhou"}]
            },
            "anomaly": {"type": "document_trace", "source": "document_rule", "shape": "document"},
            "diagnosis": {
                "source": "document_rule",
                "shape": "document",
                "evidence": [{"type": "document", "label": "命中 1 条异常单据", "detail": "234 折扣金额=0", "weight": 95}],
                "hypotheses": [{"type": "document_rule", "title": "单据规则命中", "reason": "核对折扣字段"}],
            },
            "documents": [{"documentNo": "234", "fieldName": "折扣金额", "value": 0}],
        },
    })

    profile = analyzer._infer_anomaly_profile(
        "请分析这个单元格异常",
        {"anomaly_type": "unknown", "anomaly_direction": "unknown"},
    )

    assert profile["type"] == "document_trace"
    assert profile["source"] == "document_rule"
    assert profile["shape"] == "document"
    assert profile["evidence_strength"] >= 80
    assert profile["evidence_strength_label"] == "强"
    assert profile["confidence"] >= 0.9
    assert profile["evidence_items"][0]["type"] == "document"
