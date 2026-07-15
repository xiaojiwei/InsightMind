"""Seed deterministic, fully synthetic data for the three call-quality dashboards.

The generated rows do not come from a production system.  Customer identifiers,
ASR text, employees, store names and quality results are all fabricated so a
fresh InsightMind checkout can demonstrate diagnosis, workbench drill-down and
pivot alerting without shipping customer data.
"""

from __future__ import annotations

import argparse
import json
import os
import re
from datetime import datetime, timedelta
from typing import Any

import pymysql

from kg_builder.call_quality_workspace import WORKSPACE_VERSION, build_workspace_payload
from kg_builder.call_sop import SOP_CATALOG, SOP_VERSION, analyze_call_sop_record, json_dumps


DEMO_DAY = "2026-07-02"
DEMO_STORE = "小鹏汽车杭州演示体验中心"
DEMO_CITY = "杭州"
DEMO_MANAGER = "演示店长"

_EXPERTS: tuple[tuple[str, int, int], ...] = (
    ("顾晨", 9, 44),
    ("林悦", 12, 58),
    ("周航", 8, 66),
    ("许安", 14, 52),
    ("蒋宁", 4, 74),
    ("宋妍", 5, 62),
    ("韩宇", 2, 81),
)

_DIALOGS = {
    "high": (
        "专家:您好，我是小鹏汽车杭州演示体验中心的产品专家小顾。看您在官网留资关注了G9，"
        "上次提到家里有孩子、日常通勤也会跑长途，比较关心空间、智驾和充电。G9的空间和智驾"
        "更适合到店实际体验。您是明天下午三点还是周六上午十点方便来店试驾？客户:周六上午吧，"
        "预算大概三十万，也想对比一下续航。专家:好的，我给您预约周六上午十点，门店在文一西路"
        "西溪附近，稍后加微信发定位，周五再提前联系确认。客户:可以，没问题。"
    ),
    "standard": (
        "专家:您好，我是小鹏汽车门店产品专家小林。看您官网关注了G6，邀请您到店试驾。"
        "客户:最近忙，想了解智驾。专家:试驾可以体验智驾，明天下午方便吗？"
        "客户:行。专家:那我加微信发定位。"
    ),
    "basic": (
        "专家:您好，我是小鹏汽车门店顾问。看您关注过P7i，想邀请您到店试驾。"
        "客户:最近没时间。专家:好的，之后再联系。"
    ),
    "miss": (
        "专家:您好，这边想问下您看的车型还考虑吗？客户:最近没时间，之后再说。专家:好的，那不打扰了。"
    ),
    "unconnected": "专家:您好，这里是小鹏汽车门店，本次电话未接通，已进入语音留言。",
}


def _style_for(index: int, expert_index: int) -> str:
    # Stable mixture: enough good calls for contrast and enough weak calls to
    # trigger the checked-in alert rules on every dashboard demonstration.
    sequence = (
        ("miss", "basic", "standard", "miss", "high", "basic"),
        ("basic", "standard", "miss", "standard", "high", "basic"),
        ("standard", "high", "basic", "standard", "miss", "high"),
        ("miss", "basic", "miss", "standard", "basic", "high"),
        ("high", "standard", "high", "basic", "standard", "high"),
        ("standard", "basic", "standard", "high", "miss", "standard"),
        ("high", "standard", "high", "high", "standard", "high"),
    )
    return sequence[expert_index][index % len(sequence[expert_index])]


def _issue_category(index: int, connected: bool, score: int, coverage: float) -> str:
    if not connected or index % 7 == 0:
        return "下一步动作未完成"
    if score <= 55 or index % 5 == 0:
        return "低质量通话"
    if coverage < 0.60 or index % 3 == 0:
        return "关键信息覆盖不足"
    return "表达一致性待提升"


def build_demo_records() -> list[dict[str, Any]]:
    """Return 54 deterministic call records without touching a database."""
    rows: list[dict[str, Any]] = []
    serial = 0
    start_at = datetime.strptime(f"{DEMO_DAY} 09:05:00", "%Y-%m-%d %H:%M:%S")
    disconnected_serials = {11, 31, 49}

    for expert_index, (expert_name, count, score_base) in enumerate(_EXPERTS):
        for local_index in range(count):
            serial += 1
            style = "unconnected" if serial in disconnected_serials else _style_for(local_index, expert_index)
            connected = style != "unconnected"
            actual_next_action = {
                "high": "已预约试驾；添加微信并发送门店定位；到店前再次确认",
                "standard": "无",
                "basic": "无",
                "miss": "无",
                "unconnected": "再次外呼",
            }[style]
            base_record = {
                "customer_account_id": f"demo-user-{serial:04d}",
                "aggregated_content": _DIALOGS[style],
                "actual_next_action": actual_next_action,
                "validation_notes": "完全合成的产品演示数据",
                "intent_name": "试驾邀约",
            }
            analysis = analyze_call_sop_record(base_record)
            workspace = build_workspace_payload({**base_record, "sop_analysis": analysis})
            coverage = float(analysis.get("coverage_rate") or 0)
            score_adjustment = {"high": 15, "standard": 7, "basic": -1, "miss": -10, "unconnected": -18}[style]
            score = max(22, min(96, score_base + score_adjustment + ((local_index % 3) - 1) * 3))
            hit_count = int(analysis.get("hit_checkpoint_count") or 0)
            total_count = int(analysis.get("total_checkpoint_count") or 24)
            missing_count = max(0, total_count - hit_count)
            grade = str(workspace["detail"].get("sopGradeLabel") or "未达成")
            issue_category = _issue_category(serial, connected, score, coverage)
            rule_name = SOP_CATALOG[(serial - 1) % len(SOP_CATALOG)].name
            conversation_time = start_at + timedelta(minutes=serial * 11)
            next_action_done = int(actual_next_action not in {"", "无", "再次外呼"})
            quality_pass = int(score > 50 and connected)
            required_names = [checkpoint.name for category in SOP_CATALOG for checkpoint in category.checkpoints]
            hit_codes = {
                checkpoint["code"]
                for category in analysis.get("categories") or []
                for checkpoint in category.get("checkpoints") or []
                if checkpoint.get("hit")
            }
            covered_names = [
                checkpoint.name
                for category in SOP_CATALOG
                for checkpoint in category.checkpoints
                if checkpoint.code in hit_codes
            ]
            missing_names = [name for name in required_names if name not in covered_names]
            rows.append(
                {
                    "quality_id": 910000 + serial,
                    "customer_account_id": base_record["customer_account_id"],
                    "activity_date": DEMO_DAY,
                    "activity_month": "2026-07",
                    "activity_week": "2026-W27",
                    "latest_conversation_time": conversation_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "expert_id": f"demo-expert-{expert_index + 1:02d}",
                    "expert_name": expert_name,
                    "store_name": DEMO_STORE,
                    "rule_name": rule_name,
                    "aggregated_content": base_record["aggregated_content"],
                    "actual_next_action": actual_next_action,
                    "validation_notes": base_record["validation_notes"],
                    "intent_name": base_record["intent_name"],
                    "analysis": analysis,
                    "workspace": workspace,
                    "connected": connected,
                    "grade": grade,
                    "invite_result": workspace["detail"].get("inviteResultLabel") or "待跟进",
                    "primary_sop_category": workspace["detail"].get("primarySopCategory") or rule_name,
                    "word_count": int(workspace["detail"].get("wordCount") or 0),
                    "duration_seconds": int(workspace["detail"].get("durationSeconds") or 0),
                    "score": score,
                    "coverage": coverage,
                    "hit_count": hit_count,
                    "total_count": total_count,
                    "missing_count": missing_count,
                    "required_names": required_names,
                    "covered_names": covered_names,
                    "missing_names": missing_names,
                    "issue_category": issue_category,
                    "next_action_done": next_action_done,
                    "quality_pass": quality_pass,
                }
            )
    return rows


_SCHEMA = """
CREATE TABLE call_record_judgement_rules (
  rule_id VARCHAR(64) PRIMARY KEY,
  sop_category_code VARCHAR(64) NOT NULL,
  sop_category_name VARCHAR(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE call_record_judgement_results (
  id BIGINT PRIMARY KEY,
  customer_account_id VARCHAR(64) NOT NULL,
  latest_conversation_time DATETIME NOT NULL,
  aggregated_content TEXT NOT NULL,
  intent_name VARCHAR(128),
  intent_original_name VARCHAR(128),
  actual_next_action TEXT,
  validation_notes TEXT,
  sop_checkpoints_json LONGTEXT,
  sop_analysis_version VARCHAR(64),
  call_asr_segments_json LONGTEXT,
  call_sop_evidence_json LONGTEXT,
  call_quality_detail_json LONGTEXT,
  call_word_count INT,
  call_duration_seconds INT,
  invite_result_label VARCHAR(64),
  sop_grade_label VARCHAR(64),
  primary_sop_category VARCHAR(128),
  call_workspace_version VARCHAR(64),
  sop_connected_flag TINYINT,
  sop_hit_checkpoint_count INT,
  sop_total_checkpoint_count INT,
  KEY idx_call_time (latest_conversation_time),
  KEY idx_call_customer (customer_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE im_call_quality_fact (
  quality_id BIGINT PRIMARY KEY,
  activity_date DATE NOT NULL,
  activity_month VARCHAR(16), activity_week VARCHAR(16), quality_run_date DATE,
  customer_account_id VARCHAR(64), specialist_id VARCHAR(64), expert_id VARCHAR(64), expert_name VARCHAR(64),
  store_id VARCHAR(64), store_name VARCHAR(255), store_city VARCHAR(64), region_id VARCHAR(64),
  manager_id VARCHAR(64), manager_name VARCHAR(64), business_type_code VARCHAR(64), is_net_lock_order TINYINT,
  conversation_count INT, latest_conversation_time DATETIME, rule_id VARCHAR(64), intent_id VARCHAR(64),
  intent_name VARCHAR(128), canonical_question TEXT, intent_matched TINYINT, match_confidence DECIMAL(10,4),
  required_slots_json LONGTEXT, covered_slots_json LONGTEXT, missing_slots_json LONGTEXT,
  required_slot_count INT, covered_slot_count INT, missing_slot_count INT, slot_coverage_rate DECIMAL(10,4),
  expected_next_action TEXT, next_action_done_flag TINYINT, intent_score DECIMAL(10,2),
  slot_score DECIMAL(10,2), next_action_score DECIMAL(10,2), safety_penalty DECIMAL(10,2), total_score DECIMAL(10,2),
  pass_flag TINYINT, quality_pass_label VARCHAR(32), call_flow_total VARCHAR(32), quality_score_level VARCHAR(64),
  issue_category VARCHAR(128), min_pass_score DECIMAL(10,2), answer_framework_matched TINYINT,
  low_coverage_call_count INT, low_quality_call_count INT, processing_status VARCHAR(32),
  create_time DATETIME, update_time DATETIME,
  KEY idx_fact_scope (activity_date, store_name),
  KEY idx_fact_expert (expert_name),
  KEY idx_fact_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"""


def _validate_database_name(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_]+", value or ""):
        raise ValueError("database name may contain only letters, numbers and underscores")
    return value


def seed_database(connection: pymysql.Connection, database: str, records: list[dict[str, Any]]) -> None:
    database = _validate_database_name(database)
    with connection.cursor() as cursor:
        cursor.execute(f"DROP DATABASE IF EXISTS `{database}`")
        cursor.execute(
            f"CREATE DATABASE `{database}` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        )
        cursor.execute(f"USE `{database}`")
        for statement in _SCHEMA.split(";"):
            if statement.strip():
                cursor.execute(statement)

        cursor.executemany(
            "INSERT INTO call_record_judgement_rules(rule_id,sop_category_code,sop_category_name) VALUES(%s,%s,%s)",
            [(category.name, category.code, category.name) for category in SOP_CATALOG],
        )

        judgement_sql = """INSERT INTO call_record_judgement_results(
            id,customer_account_id,latest_conversation_time,aggregated_content,intent_name,intent_original_name,
            actual_next_action,validation_notes,sop_checkpoints_json,sop_analysis_version,call_asr_segments_json,
            call_sop_evidence_json,call_quality_detail_json,call_word_count,call_duration_seconds,invite_result_label,
            sop_grade_label,primary_sop_category,call_workspace_version,sop_connected_flag,
            sop_hit_checkpoint_count,sop_total_checkpoint_count
        ) VALUES (""" + ",".join(["%s"] * 22) + ")"
        cursor.executemany(
            judgement_sql,
            [
                (
                    row["quality_id"], row["customer_account_id"], row["latest_conversation_time"],
                    row["aggregated_content"], row["intent_name"], "试驾邀约", row["actual_next_action"],
                    row["validation_notes"], json_dumps(row["analysis"]), SOP_VERSION,
                    json_dumps(row["workspace"]["segments"]), json_dumps(row["workspace"]["sopEvidence"]),
                    json_dumps(row["workspace"]["detail"]), row["word_count"], row["duration_seconds"],
                    row["invite_result"], row["grade"], row["primary_sop_category"], WORKSPACE_VERSION,
                    int(row["connected"]), row["hit_count"], row["total_count"],
                )
                for row in records
            ],
        )

        fact_columns = (
            "quality_id,activity_date,activity_month,activity_week,quality_run_date,customer_account_id,"
            "specialist_id,expert_id,expert_name,store_id,store_name,store_city,region_id,manager_id,manager_name,"
            "business_type_code,is_net_lock_order,conversation_count,latest_conversation_time,rule_id,intent_id,"
            "intent_name,canonical_question,intent_matched,match_confidence,required_slots_json,covered_slots_json,"
            "missing_slots_json,required_slot_count,covered_slot_count,missing_slot_count,slot_coverage_rate,"
            "expected_next_action,next_action_done_flag,intent_score,slot_score,next_action_score,safety_penalty,"
            "total_score,pass_flag,quality_pass_label,call_flow_total,quality_score_level,issue_category,min_pass_score,"
            "answer_framework_matched,low_coverage_call_count,low_quality_call_count,processing_status,create_time,update_time"
        )
        fact_sql = f"INSERT INTO im_call_quality_fact({fact_columns}) VALUES (" + ",".join(["%s"] * 51) + ")"
        cursor.executemany(
            fact_sql,
            [
                (
                    row["quality_id"], row["activity_date"], row["activity_month"], row["activity_week"],
                    row["activity_date"], row["customer_account_id"], row["expert_id"], row["expert_id"],
                    row["expert_name"], "demo-store-hz-01", row["store_name"], DEMO_CITY, "demo-region-east",
                    "demo-manager-01", DEMO_MANAGER, "TEST_DRIVE_INVITE", 0, 1, row["latest_conversation_time"],
                    row["rule_name"], "demo-intent-01", row["intent_name"], "邀请客户到店试驾", 1, 0.96,
                    json.dumps(row["required_names"], ensure_ascii=False),
                    json.dumps(row["covered_names"], ensure_ascii=False),
                    json.dumps(row["missing_names"], ensure_ascii=False), row["total_count"], row["hit_count"],
                    row["missing_count"], row["coverage"], row["actual_next_action"], row["next_action_done"],
                    min(100, row["score"] + 3), round(row["coverage"] * 100, 2),
                    100 if row["next_action_done"] else 35, 0, row["score"], row["quality_pass"],
                    "通过" if row["quality_pass"] else "未通过", "有效接通" if row["connected"] else "未接通",
                    row["grade"], row["issue_category"], 50, int(row["coverage"] >= 0.55),
                    int(row["coverage"] < 0.55), int(row["score"] <= 50), "DONE",
                    row["latest_conversation_time"], row["latest_conversation_time"],
                )
                for row in records
            ],
        )
    connection.commit()


def main() -> None:
    parser = argparse.ArgumentParser(description="Install the sanitized call-quality dashboard demo dataset")
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--password", default=os.getenv("MYSQL_PASSWORD", "root"))
    parser.add_argument("--database", default=os.getenv("DA_TMS_DB", "da_tms"))
    args = parser.parse_args()
    records = build_demo_records()
    connection = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        seed_database(connection, args.database, records)
    finally:
        connection.close()
    print(f"Installed {len(records)} synthetic calls in {args.database}.{DEMO_STORE}")


if __name__ == "__main__":
    main()
