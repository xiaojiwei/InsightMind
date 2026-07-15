import json

from demo_call_sop_data import DEMO_STORE, build_demo_records
from kg_builder.call_sop import SOP_CATALOG


def test_demo_call_sop_dataset_is_complete_and_sanitized():
    rows = build_demo_records()
    assert len(rows) == 54
    assert sum(1 for row in rows if row["connected"]) == 51
    assert len({row["expert_name"] for row in rows}) == 7
    assert {row["rule_name"] for row in rows} == {item.name for item in SOP_CATALOG}
    assert {row["grade"] for row in rows} == {"高质量达成", "标准达成", "基础达成", "未达成"}
    assert all(row["store_name"] == DEMO_STORE for row in rows)
    assert all(row["customer_account_id"].startswith("demo-user-") for row in rows)
    assert len({row["quality_id"] for row in rows}) == len(rows)

    payload = json.dumps(rows, ensure_ascii=False, default=str)
    assert "小鹏汽车" in payload
    assert "理" + "想" not in payload
    assert "Li" + " Auto" not in payload


def test_demo_call_sop_dataset_triggers_monitoring_scenarios():
    rows = build_demo_records()
    assert sum(row["low_quality_call_count"] if "low_quality_call_count" in row else int(row["score"] <= 50) for row in rows) >= 2
    assert sum(int(row["coverage"] < 0.55) for row in rows) >= 3
    assert sum(row["missing_count"] for row in rows) >= 15
    assert any(sum(1 for row in rows if row["expert_name"] == name) <= 4 for name in {row["expert_name"] for row in rows})
