from kg_builder.analysis.analyzer import IndicatorAnalyzer


def test_no_data_stops_before_later_parts(monkeypatch):
    analyzer = IndicatorAnalyzer(
        data_agent_url="http://unused",
        ttl_path="unused.ttl",
        llm_config={},
        log_cb=lambda _message: None,
    )
    monkeypatch.setattr(
        analyzer,
        "_analyze_fluctuations",
        lambda *_args, **_kwargs: {"skip_reason": "数据为空"},
    )

    events = list(analyzer.analyze({
        "configureList": [
            {"code": "MEAS_avg_call_quality_score"},
            {"code": "DIM_date_week"},
        ],
        "filterList": [],
        "_gran": "week",
        "_timeRange": {
            "time_start": "2026-07-02",
            "time_end": "2026-07-29",
        },
    }))

    assert events[0]["part"] == 1
    assert events[1]["step"] == "no_data"
    assert events[1]["result"]["reason"] == "数据为空"
    assert not any(event.get("part") in (2, 3, 4, 5) for event in events)


def test_empty_part2_stops_before_contribution_and_kg(monkeypatch):
    analyzer = IndicatorAnalyzer(
        data_agent_url="http://unused",
        ttl_path="unused.ttl",
        llm_config={},
        log_cb=lambda _message: None,
    )
    monkeypatch.setattr(
        analyzer,
        "_analyze_fluctuations",
        lambda *_args, **_kwargs: {
            "current_period": "202630",
            "previous_period": "202629",
            "global_top20": [],
        },
    )
    monkeypatch.setattr(
        analyzer,
        "_part_interp",
        lambda *_args, **_kwargs: {"text": "", "focus": ""},
    )
    monkeypatch.setattr(analyzer, "_generate_part_plan", lambda *_args: {})
    monkeypatch.setattr(analyzer, "_fetch_data", lambda *_args, **_kwargs: ([], [], []))

    events = list(analyzer.analyze({
        "configureList": [
            {"code": "MEAS_avg_call_quality_score"},
            {"code": "DIM_date_week"},
        ],
        "filterList": [],
        "_gran": "week",
        "_timeRange": {"time_start": "2026-07-02", "time_end": "2026-07-29"},
    }))

    assert any(event.get("part") == 2 for event in events)
    no_data = next(event for event in events if event.get("step") == "no_data")
    assert "统计归因" in no_data["result"]["message"]
    assert not any(event.get("part") in (3, 4, 5) for event in events)


def test_fallback_report_keeps_explicit_dimension_first():
    report = IndicatorAnalyzer._fallback_report(
        part1={"measures": [{
            "cn_name": "核销量", "previous": 10, "current": 12,
            "change": 2, "change_pct": 20,
        }], "dimension_contrib": [{
            "dim_col": "DIM_province",
            "total_change": 5,
            "top_movers": [{
                "dim_cn": "省份", "value": "浙江省", "lmdi_contrib": 3,
            }, {
                "dim_cn": "省份", "value": "江苏省", "lmdi_contrib": 2,
            }],
        }]},
        part2={},
        part3={},
        meta={"dim_codes": ["DIM_province"]},
        part_kg_attr={"kg_dimensions": [
            {
                "dim_code": "DIM_region", "cn_name": "战区",
                "is_selected": False, "total_change_pct": -80,
                "top_movers": [],
            },
        ]},
    )

    problem_section = report.split("### 3. 问题出在哪里", 1)[1]
    assert problem_section.index("省份") < problem_section.index("战区")
    assert "浙江省" in problem_section
    assert "江苏省" in problem_section
    assert "贡献变化合计为 5" in problem_section
