"""Install a deterministic, fully synthetic CELN operating demo dataset.

The business knowledge graph publishes CELN measures backed by
``da_tms.im_celn_store_funnel_fact``. A fresh demo checkout must therefore
create that fact table together with the small operational tables used by the
Smart Insight drill-down views. Every customer, employee, store and event in
this module is fabricated for product demonstration.
"""

from __future__ import annotations

import argparse
import os
import re
from datetime import date, datetime, timedelta
from typing import Any

import pymysql


DEMO_DAY = date(2026, 7, 2)
DEMO_STORE = "理想汽车杭州演示体验中心"
DEMO_CITY = "杭州"
DEMO_MANAGER = "演示店长"

_STAGES: tuple[tuple[str, str, str, int], ...] = (
    ("C", "C Why Car", "01_C", 1),
    ("E", "E Why Energy", "02_E", 2),
    ("L", "L 品牌意向", "03_L", 3),
    ("N", "N Why Now", "04_N", 4),
)

_LATEST_STAGE_COUNTS = {"C": 42, "E": 32, "L": 20, "N": 11}
_EXPERTS = ("顾晨", "林悦", "周航", "许安", "蒋宁", "宋妍", "韩宇")
_MODELS = ("G6", "G9", "X9", "P7i")


def build_funnel_rows() -> list[dict[str, Any]]:
    """Return seven daily snapshots covering all published CELN groups."""
    rows: list[dict[str, Any]] = []
    for day_index in range(7):
        snapshot_day = DEMO_DAY - timedelta(days=6 - day_index)
        stage_counts = {
            "C": 48 - day_index,
            "E": 26 + day_index,
            "L": 17 + (day_index // 2),
            "N": 8 + (day_index // 2),
        }
        for stage, stage_name, stage_code, stage_order in _STAGES:
            rows.append(
                {
                    "activity_date": snapshot_day,
                    "activity_month": snapshot_day.strftime("%Y-%m"),
                    "activity_week": int(snapshot_day.strftime("%V")),
                    "store_id": 1,
                    "store_name": DEMO_STORE,
                    "store_city": DEMO_CITY,
                    "region_id": 101,
                    "manager_id": 1001,
                    "manager_name": DEMO_MANAGER,
                    "funnel_group_code": "stage",
                    "celn_funnel_group": "CELN阶段推进",
                    "celn_funnel_stage_code": stage_code,
                    "celn_funnel_stage_name": stage_name,
                    "celn_stage_order": stage_order,
                    "funnel_count": stage_counts[stage],
                }
            )

        total_users = sum(stage_counts.values())
        lifecycle_nodes = (
            ("10_POOL", "用户池总量", 10, total_users),
            ("11_FOLLOW", "当日跟进闭环", 11, 20 + day_index),
            ("12_FC_CANDIDATE", "FC候选", 12, stage_counts["L"] + stage_counts["N"]),
            ("13_FC_IDENTIFIED", "当日FC识别", 13, 5 + (day_index % 3)),
            ("14_FC_CONFIRMED", "FC确认/转化", 14, 2 + (day_index % 2)),
        )
        conversion_nodes = (
            ("20_LOCK", "锁单", 20, 7 + (day_index // 2)),
            ("21_ORDER", "大定", 21, 4 + (day_index // 3)),
            ("22_DELIVERY", "交付", 22, 2 + (day_index // 3)),
        )
        for group_code, group_name, nodes in (
            ("lifecycle", "经营闭环承接", lifecycle_nodes),
            ("conversion", "成交结果承接", conversion_nodes),
        ):
            for node_code, node_name, node_order, node_count in nodes:
                rows.append(
                    {
                        "activity_date": snapshot_day,
                        "activity_month": snapshot_day.strftime("%Y-%m"),
                        "activity_week": int(snapshot_day.strftime("%V")),
                        "store_id": 1,
                        "store_name": DEMO_STORE,
                        "store_city": DEMO_CITY,
                        "region_id": 101,
                        "manager_id": 1001,
                        "manager_name": DEMO_MANAGER,
                        "funnel_group_code": group_code,
                        "celn_funnel_group": group_name,
                        "celn_funnel_stage_code": node_code,
                        "celn_funnel_stage_name": node_name,
                        "celn_stage_order": node_order,
                        "funnel_count": node_count,
                    }
                )
    return rows


def build_customers() -> list[dict[str, Any]]:
    """Return synthetic customers matching the latest C/E/L/N snapshot."""
    rows: list[dict[str, Any]] = []
    serial = 0
    for stage, _stage_name, _stage_code, _stage_order in _STAGES:
        for stage_index in range(_LATEST_STAGE_COUNTS[stage]):
            serial += 1
            rows.append(
                {
                    "id": 100000 + serial,
                    "customer_code": f"demo-celn-{serial:04d}",
                    "customer_name": f"演示客户{serial:04d}",
                    "phone": f"DEMO-{serial:04d}",
                    "source_type": ("官网留资", "门店活动", "老客推荐")[serial % 3],
                    "current_stage_code": stage,
                    "city": DEMO_CITY,
                    "intended_model": _MODELS[serial % len(_MODELS)],
                    "is_fc_candidate": int(stage in {"L", "N"} and stage_index % 3 != 0),
                    "first_visit_time": datetime(2026, 6, 1, 9, 0) + timedelta(hours=serial),
                    "updated_at": datetime(2026, 7, 2, 8, 0) + timedelta(minutes=serial),
                }
            )
    return rows


_SCHEMA = """
DROP TABLE IF EXISTS celn_determination_evidence;
DROP TABLE IF EXISTS celn_stage_determination;
DROP TABLE IF EXISTS celn_customer_tag;
DROP TABLE IF EXISTS celn_follow_up_record;
DROP TABLE IF EXISTS celn_follow_up_task;
DROP TABLE IF EXISTS celn_conversion_event;
DROP TABLE IF EXISTS celn_fc_identification;
DROP TABLE IF EXISTS celn_fc_rule;
DROP TABLE IF EXISTS celn_evidence;
DROP TABLE IF EXISTS celn_tag;
DROP TABLE IF EXISTS celn_product_expert;
DROP TABLE IF EXISTS celn_store_manager;
DROP TABLE IF EXISTS celn_daily_review;
DROP TABLE IF EXISTS celn_customer;
DROP TABLE IF EXISTS celn_stage;
DROP TABLE IF EXISTS celn_store;
DROP TABLE IF EXISTS im_celn_store_funnel_fact;

CREATE TABLE celn_store (
  id BIGINT PRIMARY KEY, store_name VARCHAR(255) NOT NULL, city VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_store_manager (
  id BIGINT PRIMARY KEY, store_id BIGINT NOT NULL, manager_name VARCHAR(64) NOT NULL, is_active TINYINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_stage (
  stage_code VARCHAR(16) PRIMARY KEY, stage_name_zh VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_product_expert (
  id BIGINT PRIMARY KEY, expert_name VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_customer (
  id BIGINT PRIMARY KEY, customer_code VARCHAR(64) NOT NULL, customer_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32), source_type VARCHAR(32), current_stage_code VARCHAR(16), current_store_id BIGINT NOT NULL,
  city VARCHAR(64), intended_model VARCHAR(64), is_fc_candidate TINYINT NOT NULL DEFAULT 0,
  is_active TINYINT NOT NULL DEFAULT 1, first_visit_time DATETIME, updated_at DATETIME,
  KEY idx_customer_store_stage (current_store_id, current_stage_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_follow_up_record (
  id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, expert_id BIGINT,
  follow_up_method VARCHAR(32), stage_before VARCHAR(16), stage_after VARCHAR(16),
  follow_up_result VARCHAR(255), started_at DATETIME, ended_at DATETIME,
  KEY idx_follow_customer (customer_id), KEY idx_follow_day (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_follow_up_task (
  id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, assigned_expert_id BIGINT,
  task_status VARCHAR(32), next_plan_time DATETIME, priority VARCHAR(16), source_type VARCHAR(32), updated_at DATETIME,
  KEY idx_task_customer (customer_id), KEY idx_task_plan (next_plan_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_evidence (
  id BIGINT PRIMARY KEY, evidence_name VARCHAR(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_stage_determination (
  id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, determined_stage_code VARCHAR(16) NOT NULL,
  confidence_score DECIMAL(8,4), judged_by_type VARCHAR(32), is_reviewable TINYINT,
  determination_time DATETIME, remark VARCHAR(255), KEY idx_determination_day (determination_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_determination_evidence (
  determination_id BIGINT NOT NULL, evidence_id BIGINT NOT NULL, evidence_value VARCHAR(255),
  PRIMARY KEY (determination_id, evidence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_tag (
  id BIGINT PRIMARY KEY, tag_code VARCHAR(32), tag_name VARCHAR(64), tag_type VARCHAR(32)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_customer_tag (
  customer_id BIGINT NOT NULL, tag_id BIGINT NOT NULL, is_active TINYINT NOT NULL,
  tagged_at DATETIME, PRIMARY KEY (customer_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_conversion_event (
  id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
  conversion_type VARCHAR(32), conversion_time DATETIME, KEY idx_conversion_day (conversion_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_fc_rule (
  id BIGINT PRIMARY KEY, rule_name VARCHAR(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_fc_identification (
  id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, fc_rule_id BIGINT,
  fc_status VARCHAR(32), identification_time DATETIME, remark VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE celn_daily_review (
  id BIGINT PRIMARY KEY, store_id BIGINT NOT NULL, review_date DATE NOT NULL,
  review_conclusion VARCHAR(255), strategy_adjustment VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE im_celn_store_funnel_fact (
  activity_date DATE NOT NULL, activity_month VARCHAR(7), activity_week INT,
  store_id BIGINT, store_name VARCHAR(255), store_city VARCHAR(64), region_id BIGINT,
  manager_id BIGINT, manager_name VARCHAR(64), funnel_group_code VARCHAR(16),
  celn_funnel_group VARCHAR(32), celn_funnel_stage_code VARCHAR(32),
  celn_funnel_stage_name VARCHAR(80), celn_stage_order INT, funnel_count DECIMAL(32,0),
  PRIMARY KEY (activity_date, store_id, celn_funnel_stage_code),
  KEY idx_celn_scope (store_name, activity_date, celn_funnel_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"""


def _validate_database_name(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_]+", value or ""):
        raise ValueError("database name may contain only letters, numbers and underscores")
    return value


def seed_database(connection: pymysql.Connection, database: str) -> dict[str, int]:
    database = _validate_database_name(database)
    funnel_rows = build_funnel_rows()
    customers = build_customers()
    with connection.cursor() as cursor:
        cursor.execute(f"USE `{database}`")
        for statement in _SCHEMA.split(";"):
            if statement.strip():
                cursor.execute(statement)

        cursor.execute("INSERT INTO celn_store(id,store_name,city) VALUES(1,%s,%s)", (DEMO_STORE, DEMO_CITY))
        cursor.execute(
            "INSERT INTO celn_store_manager(id,store_id,manager_name,is_active) VALUES(1001,1,%s,1)",
            (DEMO_MANAGER,),
        )
        cursor.executemany(
            "INSERT INTO celn_stage(stage_code,stage_name_zh) VALUES(%s,%s)",
            [(stage, stage_name) for stage, stage_name, _code, _order in _STAGES],
        )
        cursor.executemany(
            "INSERT INTO celn_product_expert(id,expert_name) VALUES(%s,%s)",
            [(index + 1, name) for index, name in enumerate(_EXPERTS)],
        )
        cursor.executemany(
            """INSERT INTO celn_customer(
                id,customer_code,customer_name,phone,source_type,current_stage_code,current_store_id,
                city,intended_model,is_fc_candidate,is_active,first_visit_time,updated_at
            ) VALUES(%s,%s,%s,%s,%s,%s,1,%s,%s,%s,1,%s,%s)""",
            [
                (
                    row["id"], row["customer_code"], row["customer_name"], row["phone"], row["source_type"],
                    row["current_stage_code"], row["city"], row["intended_model"], row["is_fc_candidate"],
                    row["first_visit_time"], row["updated_at"],
                )
                for row in customers
            ],
        )
        cursor.executemany(
            """INSERT INTO im_celn_store_funnel_fact(
                activity_date,activity_month,activity_week,store_id,store_name,store_city,region_id,
                manager_id,manager_name,funnel_group_code,celn_funnel_group,celn_funnel_stage_code,
                celn_funnel_stage_name,celn_stage_order,funnel_count
            ) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
            [tuple(row[key] for key in (
                "activity_date", "activity_month", "activity_week", "store_id", "store_name", "store_city",
                "region_id", "manager_id", "manager_name", "funnel_group_code", "celn_funnel_group",
                "celn_funnel_stage_code", "celn_funnel_stage_name", "celn_stage_order", "funnel_count",
            )) for row in funnel_rows],
        )

        followups: list[tuple[Any, ...]] = []
        follow_id = 1
        transitions = (("C", "E", 4), ("E", "L", 3), ("L", "N", 2), ("E", "C", 1))
        for day_offset in range(7):
            event_day = DEMO_DAY - timedelta(days=6 - day_offset)
            for before, after, count in transitions:
                for transition_index in range(max(1, count - (day_offset % 2))):
                    customer = customers[(follow_id * 7) % len(customers)]
                    started = datetime.combine(event_day, datetime.min.time()) + timedelta(hours=9 + transition_index)
                    followups.append((
                        follow_id, customer["id"], (follow_id % len(_EXPERTS)) + 1, "电话",
                        before, after, "已完成合成跟进并记录阶段变化", started, started + timedelta(minutes=18),
                    ))
                    follow_id += 1
        cursor.executemany(
            """INSERT INTO celn_follow_up_record(
                id,customer_id,expert_id,follow_up_method,stage_before,stage_after,follow_up_result,started_at,ended_at
            ) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
            followups,
        )

        tasks = []
        for index, customer in enumerate(customers[:36], start=1):
            plan_time = datetime(2026, 7, 2, 10, 0) + timedelta(minutes=index * 12)
            tasks.append((index, customer["id"], (index % len(_EXPERTS)) + 1, "待跟进", plan_time, "高" if index % 4 == 0 else "普通", "系统推荐", plan_time - timedelta(hours=2)))
        cursor.executemany(
            """INSERT INTO celn_follow_up_task(
                id,customer_id,assigned_expert_id,task_status,next_plan_time,priority,source_type,updated_at
            ) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)""",
            tasks,
        )

        cursor.executemany(
            "INSERT INTO celn_evidence(id,evidence_name) VALUES(%s,%s)",
            ((1, "车型关注"), (2, "预算范围"), (3, "购车时点"), (4, "试驾意愿")),
        )
        determinations = []
        determination_evidence = []
        for index, customer in enumerate(customers[::4], start=1):
            determined_at = datetime(2026, 7, 2, 9, 0) + timedelta(minutes=index * 7)
            determinations.append((index, customer["id"], customer["current_stage_code"], 0.82 + (index % 10) / 100, "规则+人工复核", 1, determined_at, "完全合成的阶段判断"))
            determination_evidence.append((index, (index % 4) + 1, f"演示证据{index:03d}"))
        cursor.executemany(
            """INSERT INTO celn_stage_determination(
                id,customer_id,determined_stage_code,confidence_score,judged_by_type,is_reviewable,determination_time,remark
            ) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)""",
            determinations,
        )
        cursor.executemany(
            "INSERT INTO celn_determination_evidence(determination_id,evidence_id,evidence_value) VALUES(%s,%s,%s)",
            determination_evidence,
        )

        tags = ((1, "DEMO_FAMILY", "家庭用户", "用户画像"), (2, "DEMO_SMART", "关注智驾", "产品偏好"), (3, "DEMO_RANGE", "关注续航", "产品偏好"), (4, "DEMO_RECENT", "近期购车", "购车时点"))
        cursor.executemany("INSERT INTO celn_tag(id,tag_code,tag_name,tag_type) VALUES(%s,%s,%s,%s)", tags)
        cursor.executemany(
            "INSERT INTO celn_customer_tag(customer_id,tag_id,is_active,tagged_at) VALUES(%s,%s,1,%s)",
            [(customer["id"], (index % 4) + 1, datetime(2026, 7, 2, 8, 30) + timedelta(minutes=index)) for index, customer in enumerate(customers[:48])],
        )

        conversion_customers = [row for row in customers if row["current_stage_code"] in {"L", "N"}][:8]
        cursor.executemany(
            "INSERT INTO celn_conversion_event(id,customer_id,store_id,conversion_type,conversion_time) VALUES(%s,%s,1,%s,%s)",
            [(index, customer["id"], ("锁单", "大定", "交付")[index % 3], datetime(2026, 7, 2, 13, 0) + timedelta(minutes=index * 20)) for index, customer in enumerate(conversion_customers, start=1)],
        )
        cursor.executemany(
            "INSERT INTO celn_fc_rule(id,rule_name) VALUES(%s,%s)",
            ((1, "近期购车意愿识别"), (2, "试驾与预算完整度识别")),
        )
        cursor.executemany(
            "INSERT INTO celn_fc_identification(id,customer_id,fc_rule_id,fc_status,identification_time,remark) VALUES(%s,%s,%s,%s,%s,%s)",
            [(index, customer["id"], (index % 2) + 1, "已确认", datetime(2026, 7, 2, 11, 0) + timedelta(minutes=index * 9), "合成演示识别结果") for index, customer in enumerate(conversion_customers, start=1)],
        )
        cursor.executemany(
            "INSERT INTO celn_daily_review(id,store_id,review_date,review_conclusion,strategy_adjustment) VALUES(%s,1,%s,%s,%s)",
            [
                (index, DEMO_DAY - timedelta(days=3 - index), f"演示复盘{index}：L/N用户持续增长", "优先跟进N阶段并补齐试驾邀约")
                for index in range(1, 4)
            ],
        )
    connection.commit()
    return {
        "funnel_rows": len(funnel_rows),
        "customers": len(customers),
        "followups": len(followups),
        "tasks": len(tasks),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Install the sanitized CELN Smart Insight demo dataset")
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--password", default=os.getenv("MYSQL_PASSWORD", "root"))
    parser.add_argument("--database", default=os.getenv("DA_TMS_DB", "da_tms"))
    args = parser.parse_args()
    connection = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        counts = seed_database(connection, args.database)
    finally:
        connection.close()
    print(
        "Installed synthetic CELN demo in "
        f"{args.database}: {counts['funnel_rows']} snapshots, "
        f"{counts['customers']} customers, {counts['followups']} follow-ups."
    )


if __name__ == "__main__":
    main()
