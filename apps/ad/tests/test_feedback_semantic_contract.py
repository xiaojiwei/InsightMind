from kg_builder.feedback.semantic_contract import (
    build_semantic_contract,
    compatible_memory_status,
)


def test_semantic_contract_records_actual_selection_without_filter_values() -> None:
    contract = build_semantic_contract(
        trace_id="trace-1",
        source="dashboard",
        question="售后营销驾驶舱组件查询",
        query_mode="query",
        status="succeeded",
        semantic_context={
            "measureCodes": ["ad.coupon_conversion_pct", "ad.coupon_conversion_pct"],
            "dimensionCodes": ["ad.region"],
            "factTables": ["dws_coupon_region_day"],
            "filters": [{
                "member": "ad.scene_type",
                "operator": "equals",
                "values": ["customer-secret"],
            }],
        },
        result={"validation": {"status": "passed"}, "elapsedMs": 21},
        graph={"sha256": "a" * 64, "filename": "indicator-data.ttl", "ontologyVersion": "1.1.0"},
    )

    assert contract["eligibleForMemory"] is True
    assert contract["plan"]["selection"] == {
        "measureCodes": ["ad.coupon_conversion_pct"],
        "dimensionCodes": ["ad.region"],
        "factTables": ["dws_coupon_region_day"],
    }
    assert contract["plan"]["filters"] == [{
        "member": "ad.scene_type", "operator": "equals", "scope": "both",
    }]
    assert "customer-secret" not in str(contract)
    assert contract["corrections"][0]["correctorCode"] == "CODE_NORMALIZER"


def test_semantic_contract_can_fall_back_to_executed_da_payload() -> None:
    contract = build_semantic_contract(
        trace_id="trace-2",
        source="nlq",
        status="succeeded",
        result={
            "ok": True,
            "queryMode": "aggregate",
            "daPayload": {"configureList": [
                {"code": "MEAS_coupon_used_cnt", "kind": "measure"},
                {"code": "DIM_region", "kind": "dimension"},
            ]},
        },
    )

    assert contract["plan"]["selection"]["measureCodes"] == ["MEAS_coupon_used_cnt"]
    assert contract["plan"]["selection"]["dimensionCodes"] == ["DIM_region"]
    assert contract["corrections"][0]["correctorCode"] == "TRACE_CONTEXT_FALLBACK"


def test_only_explicit_quality_feedback_changes_memory_state() -> None:
    assert compatible_memory_status("RESULT_HELPFUL") == "ENABLED"
    assert compatible_memory_status("RESULT_UNHELPFUL") == "DISABLED"
    assert compatible_memory_status("RESULT_CORRECTION_SUBMITTED") == "DISABLED"
    assert compatible_memory_status("DASHBOARD_VIEWED") == ""
