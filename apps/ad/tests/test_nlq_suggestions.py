from web_app import NLQ_DEMO_INSIGHT_QUESTIONS, _select_nlq_suggestion_candidates


def test_suggestion_candidates_ignore_metrics_from_missing_demo_tables():
    items = [
        {"code": "MEAS_celn", "name": "CELN漏斗计数", "tables": {"im_celn_store_funnel_fact"}},
        {"code": "MEAS_avg_call_quality_score", "name": "平均电话质量分", "tables": {"im_call_quality_fact"}},
        {"code": "MEAS_quality_record_count", "name": "质检通话数", "tables": {"im_call_quality_fact"}},
    ]

    selected = _select_nlq_suggestion_candidates(
        items,
        {"im_call_quality_fact"},
        ("MEAS_quality_record_count", "MEAS_avg_call_quality_score"),
    )

    assert [item["code"] for item in selected] == [
        "MEAS_quality_record_count",
        "MEAS_avg_call_quality_score",
    ]


def test_suggestion_candidates_fall_back_when_table_check_is_unavailable():
    items = [
        {"code": "MEAS_b", "name": "B指标", "tables": {"table_b"}},
        {"code": "MEAS_a", "name": "A指标", "tables": {"table_a"}},
    ]

    selected = _select_nlq_suggestion_candidates(items, set(), ())

    assert [item["code"] for item in selected] == ["MEAS_a", "MEAS_b"]


def test_demo_insight_question_contains_proven_historical_attribution_prompt():
    question = NLQ_DEMO_INSIGHT_QUESTIONS[0]
    assert question == "分析6月8日至7月2日平均电话质量分下降原因"
    assert len(question) <= 24
