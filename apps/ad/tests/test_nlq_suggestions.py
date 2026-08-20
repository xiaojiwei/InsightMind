from web_app import _build_nlq_attribution_questions, _select_nlq_suggestion_candidates


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


def test_attribution_questions_are_built_from_active_graph_members():
    questions = _build_nlq_attribution_questions(
        {"code": "MEAS_conversion", "name": "优惠券转化率"},
        {"code": "MEAS_used", "name": "核销量"},
        [{"code": "DIM_region", "name": "战区"}],
        [{"code": "DIM_month", "name": "统计月份"}],
    )

    assert questions == [
        "分析优惠券转化率变化原因",
        "分析不同战区的核销量差异原因",
    ]
    assert all("电话质量" not in question for question in questions)
