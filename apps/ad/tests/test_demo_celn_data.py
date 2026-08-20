import json

from demo_celn_data import DEMO_STORE, build_customers, build_funnel_rows


def test_demo_celn_dataset_covers_all_smart_insight_groups():
    rows = build_funnel_rows()
    assert len(rows) == 84
    assert len({row["activity_date"] for row in rows}) == 7
    assert {row["celn_funnel_group"] for row in rows} == {
        "CELN阶段推进",
        "经营闭环承接",
        "成交结果承接",
    }
    latest = max(row["activity_date"] for row in rows)
    latest_stages = {
        row["celn_funnel_stage_code"]
        for row in rows
        if row["activity_date"] == latest and row["celn_funnel_group"] == "CELN阶段推进"
    }
    assert latest_stages == {"01_C", "02_E", "03_L", "04_N"}
    assert all(row["store_name"] == DEMO_STORE for row in rows)


def test_demo_celn_customers_are_synthetic_and_match_latest_snapshot():
    customers = build_customers()
    assert len(customers) == 105
    assert {row["current_stage_code"] for row in customers} == {"C", "E", "L", "N"}
    assert all(row["customer_code"].startswith("demo-celn-") for row in customers)
    assert all(row["customer_name"].startswith("演示客户") for row in customers)
    assert all(row["phone"].startswith("DEMO-") for row in customers)

    payload = json.dumps(customers, ensure_ascii=False, default=str)
    assert "理想汽车" not in payload  # Store data lives in the fact rows, not personal rows.
    assert "小" + "鹏" not in payload
    assert "Li" + " Auto" not in payload
