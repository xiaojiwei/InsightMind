from kg_builder.call_sop import (
    SOP_CATALOG,
    SOP_VERSION,
    _ratio_level,
    analyze_call_sop_record,
    overall_grade_label,
    overall_grade_level,
)


def test_sop_catalog_follows_rule_and_business_sequence():
    assert [category.code for category in SOP_CATALOG] == [
        "RULE_000",
        "RULE_001",
        "RULE_002",
        "RULE_003",
        "RULE_004",
        "RULE_005",
        "RULE_006",
        "RULE_007",
    ]


def test_three_checkpoint_levels_use_exact_thirds():
    assert _ratio_level(1 / 3) == "basic"
    assert _ratio_level(2 / 3) == "standard"
    assert _ratio_level(1) == "high"


def test_small_program_text_is_not_self_introduction():
    analysis = analyze_call_sop_record({
        "aggregated_content": "专家: 您可以在小程序里查看车型配置。｜客户: 现在不方便。",
    })
    category = next(item for item in analysis["categories"] if item["code"] == "RULE_000")
    checkpoint = next(item for item in category["checkpoints"] if item["code"] == "RULE_000_01")

    assert SOP_VERSION == "call_sop_v1.1"
    assert checkpoint["hit"] is False


def test_overall_grade_contract_is_shared_by_label_and_level():
    assert overall_grade_label(0.75) == "高质量达成"
    assert overall_grade_label(0.55) == "标准达成"
    assert overall_grade_label(0.35) == "基础达成"
    assert overall_grade_label(1, connected=False) == "未达成"

    assert overall_grade_level("高质量达成") == "high"
    assert overall_grade_level("", 0.6) == "standard"
    assert overall_grade_level("未知等级", 0.2) == "miss"
