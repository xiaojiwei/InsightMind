import json

from demo_call_sop_data import DEMO_STORE, build_demo_records
from kg_builder.call_sop import SOP_CATALOG


def test_demo_call_sop_dataset_is_complete_and_sanitized():
    rows = build_demo_records()
    assert len(rows) == 175
    assert sum(1 for row in rows if row["connected"]) >= 160
    assert len({row["expert_name"] for row in rows}) == 7
    assert {row["rule_name"] for row in rows} == {item.name for item in SOP_CATALOG}
    assert {row["grade"] for row in rows} == {"高质量达成", "标准达成", "基础达成", "未达成"}
    assert all(row["store_name"] == DEMO_STORE for row in rows)
    assert all(row["customer_account_id"].startswith("demo-user-") for row in rows)
    assert len({row["quality_id"] for row in rows}) == len(rows)

    payload = json.dumps(rows, ensure_ascii=False, default=str)
    assert "特斯拉汽车" in payload
    assert "小" + "鹏汽车" not in payload
    assert "理" + "想" not in payload
    assert "Li" + " Auto" not in payload


def test_demo_call_sop_dataset_triggers_monitoring_scenarios():
    rows = build_demo_records()
    assert sum(row["low_quality_call_count"] if "low_quality_call_count" in row else int(row["score"] <= 50) for row in rows) >= 2
    assert sum(int(row["coverage"] < 0.55) for row in rows) >= 3
    assert sum(row["missing_count"] for row in rows) >= 15
    dates = sorted({row["activity_date"] for row in rows})
    assert dates == [f"2026-06-{day:02d}" for day in range(8, 31)] + ["2026-07-01", "2026-07-02"]
    early = [row["score"] for row in rows if row["activity_date"] <= "2026-06-15"]
    late = [row["score"] for row in rows if row["activity_date"] >= "2026-06-25"]
    assert sum(early) / len(early) - sum(late) / len(late) >= 20

    contributor_late = [
        row["score"] for row in rows
        if row["activity_date"] >= "2026-06-25" and row["expert_name"] in {"顾晨", "林悦"}
    ]
    stable_late = [
        row["score"] for row in rows
        if row["activity_date"] >= "2026-06-25" and row["expert_name"] not in {"顾晨", "林悦"}
    ]
    assert sum(stable_late) / len(stable_late) - sum(contributor_late) / len(contributor_late) >= 10
