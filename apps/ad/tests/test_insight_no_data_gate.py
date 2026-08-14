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
