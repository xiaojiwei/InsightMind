#!/usr/bin/env python3
"""Rebuild mall_robot after-sales marketing DWS marts from local source tables."""
from __future__ import annotations

import argparse
import os

import pymysql


TARGETS = (
    "dws_cost_roi_day",
    "dws_vehicle_model_day",
    "dws_rights_store_day",
    "dws_rights_region_day",
    "dws_employee_day",
    "dws_delivery_region_day",
    "dws_coupon_store_day",
    "dws_coupon_region_day",
)

REGION_NAME_SQL = """CASE COALESCE(s.region, 0)
    WHEN 10 THEN '东区' WHEN 20 THEN '南区' WHEN 30 THEN '西区'
    WHEN 40 THEN '北区' ELSE '未分区' END"""

SCENE_SQL = """CASE
    WHEN COALESCE(rgr.scene_code, '') IN ('DELIVERY', 'SERVICE') THEN rgr.scene_code
    WHEN COALESCE(rr.recommend_scene, '') LIKE 'db_%' THEN 'DELIVERY'
    ELSE 'SERVICE' END"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=os.getenv("MALL_ROBOT_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MALL_ROBOT_PORT", "3306")))
    parser.add_argument("--database", default=os.getenv("MALL_ROBOT_DATABASE", "mall_robot"))
    parser.add_argument("--username", default=os.getenv("MALL_ROBOT_USERNAME", "root"))
    parser.add_argument("--password", default=os.getenv("MALL_ROBOT_PASSWORD", ""))
    parser.add_argument(
        "--demo-delivery",
        action="store_true",
        help="build deterministic, realistically varied demo marketing and delivery measures",
    )
    return parser.parse_args()


def execute(cur, sql: str) -> int:
    cur.execute(sql)
    return max(cur.rowcount, 0)


def main() -> None:
    args = parse_args()
    demo_delivery = bool(args.demo_delivery)
    coupon_scene = "'DELIVERY'" if demo_delivery else "'SERVICE'"
    if demo_delivery:
        # Keep the synthetic mart's latest business date aligned with the database
        # server's current day.  The supplied demo source is a frozen historical
        # snapshot; without this rolling offset the dashboard's default/current
        # date filter eventually excludes every row.
        coupon_timestamp = """DATE_ADD(
            c.send_at,
            INTERVAL DATEDIFF(
                CURDATE(),
                (SELECT DATE(MAX(c2.send_at))
                   FROM coupon c2
                  WHERE c2.deleted = 0 AND c2.send_at IS NOT NULL)
            ) DAY
        )"""
        coupon_send_threshold = """(895
            + CASE COALESCE(s.region, 0)
                WHEN 10 THEN 8 WHEN 20 THEN 32 WHEN 30 THEN -18 WHEN 40 THEN -7 ELSE -30 END
            + CASE MONTH(c.send_at)
                WHEN 1 THEN -35 WHEN 2 THEN -70 WHEN 3 THEN 12 WHEN 4 THEN 24
                WHEN 5 THEN 18 WHEN 6 THEN -5 WHEN 7 THEN -22 WHEN 8 THEN 8
                WHEN 9 THEN 15 WHEN 10 THEN 34 WHEN 11 THEN 20 WHEN 12 THEN 46 ELSE 0 END)"""
        coupon_receive_threshold = """(748
            + CASE COALESCE(s.region, 0)
                WHEN 10 THEN 18 WHEN 20 THEN 47 WHEN 30 THEN -12 WHEN 40 THEN 25 ELSE -30 END
            + CASE MOD(MONTH(c.send_at), 4)
                WHEN 0 THEN 22 WHEN 1 THEN -14 WHEN 2 THEN 8 ELSE 0 END)"""
        coupon_use_threshold = """(252
            + CASE COALESCE(s.region, 0)
                WHEN 10 THEN 24 WHEN 20 THEN -7 WHEN 30 THEN 61 WHEN 40 THEN -28 ELSE -35 END
            + CASE MOD(MONTH(c.send_at), 5)
                WHEN 0 THEN 31 WHEN 1 THEN -18 WHEN 2 THEN 12 WHEN 3 THEN -6 ELSE 0 END)"""
        coupon_send_flag = f"(MOD(CRC32(CONCAT('demo-send:', c.id)), 1000) < {coupon_send_threshold})"
        coupon_receive_flag = (
            f"({coupon_send_flag} AND MOD(CRC32(CONCAT('demo-receive:', c.id)), 1000) "
            f"< {coupon_receive_threshold})"
        )
        coupon_use_flag = (
            f"({coupon_receive_flag} AND MOD(CRC32(CONCAT('demo-use:', c.id)), 1000) "
            f"< {coupon_use_threshold})"
        )
        coupon_face_value = """CASE
            WHEN MOD(CRC32(CONCAT('demo-face:', c.id)), 10) < 4 THEN 3000
            WHEN MOD(CRC32(CONCAT('demo-face:', c.id)), 10) < 7 THEN 5000
            WHEN MOD(CRC32(CONCAT('demo-face:', c.id)), 10) < 9 THEN 8000
            ELSE 10000 END"""
        employee_timestamp = """DATE_ADD(
            COALESCE(r.order_pay_time, r.finish_time, r.created_at),
            INTERVAL DATEDIFF(
                CURDATE(),
                (SELECT DATE(MAX(COALESCE(r2.order_pay_time, r2.finish_time, r2.created_at)))
                   FROM sales_order_recommend_record r2
                  WHERE r2.deleted = 0)
            ) DAY
        )"""
        employee_order_flag = "(MOD(CRC32(CONCAT('demo-dist-order:', r.id)), 1000) < 318)"
        employee_rights_flag = (
            f"({employee_order_flag} AND MOD(CRC32(CONCAT('demo-dist-rights:', r.id)), 1000) < 386)"
        )
        employee_order_amount = (
            f"CASE WHEN {employee_order_flag} THEN "
            "16500 + MOD(CRC32(CONCAT('demo-dist-amount:', r.id)), 101700) ELSE 0 END"
        )
        rights_timestamp = """DATE_ADD(
            rgr.created_at,
            INTERVAL DATEDIFF(
                CURDATE(),
                (SELECT DATE(MAX(rgr2.created_at))
                   FROM t_rights_group_record rgr2
                  WHERE rgr2.created_at IS NOT NULL)
            ) DAY
        )"""
        rights_open_threshold = """(611
            + CASE COALESCE(s.region, 0)
                WHEN 10 THEN 14 WHEN 20 THEN -19 WHEN 30 THEN 29 WHEN 40 THEN -27 ELSE -25 END
            + CASE WHEN """ + SCENE_SQL + " = 'SERVICE' THEN 23 ELSE -11 END)"
        rights_open_flag = (
            "(MOD(CRC32(CONCAT('demo-right-open:', rgr.code)), 1000) "
            f"< {rights_open_threshold})"
        )
        rights_open_count = f"SUM({rights_open_flag})"
        rights_order_threshold = """(354
            + CASE COALESCE(s.region, 0)
                WHEN 10 THEN 18 WHEN 20 THEN -13 WHEN 30 THEN 36 WHEN 40 THEN -22 ELSE -30 END
            + CASE WHEN """ + SCENE_SQL + " = 'SERVICE' THEN 27 ELSE -9 END)"
        rights_order_flag = (
            "(MOD(CRC32(CONCAT('demo-service-order:', o.sales_order_id)), 1000) "
            f"< {rights_order_threshold})"
        )
        rights_order_amount = (
            f"CASE WHEN {rights_order_flag} THEN "
            "28500 + MOD(CRC32(CONCAT('demo-service-amount:', o.sales_order_id)), 132500) ELSE 0 END"
        )
        rights_cost = (
            f"CASE WHEN {rights_open_flag} THEN "
            "4500 + MOD(CRC32(CONCAT('demo-right-cost:', rgr.code)), 14500) ELSE 0 END"
        )
    else:
        coupon_timestamp = "c.send_at"
        coupon_send_flag = "1"
        coupon_receive_flag = "(c.coupon_receive_record_id > 0)"
        coupon_use_flag = "(c.status = 1)"
        coupon_face_value = "COALESCE(c.discount_value, 0)"
        employee_timestamp = "COALESCE(r.order_pay_time, r.finish_time, r.created_at)"
        employee_order_flag = "(r.status = 1)"
        employee_rights_flag = "(r.status = 1 AND r.recommend_scene LIKE '%rights%')"
        employee_order_amount = "CASE WHEN r.status = 1 THEN COALESCE(r.order_amount, 0) ELSE 0 END"
        rights_timestamp = "rgr.created_at"
        rights_open_flag = "1"
        rights_open_count = "COUNT(DISTINCT rgr.code)"
        rights_order_flag = "(o.paid_amount > 0)"
        rights_order_amount = "COALESCE(o.paid_amount, 0)"
        rights_cost = "COALESCE(rg.cost_price, 0)"
    demo_model_delivery = (
        "COUNT(*) + SUM(MOD(CRC32(CONCAT('demo-delivery:', c.id, ':', v.model_id)), 1000) "
        "< (462 + CASE COALESCE(s.region, 0) WHEN 10 THEN 24 WHEN 20 THEN -17 "
        "WHEN 30 THEN 39 WHEN 40 THEN -31 ELSE 0 END))"
        if demo_delivery else "0"
    )
    demo_model_rights = (
        "SUM(MOD(CRC32(CONCAT('demo-delivery-rights:', c.id)), 1000) "
        "< (521 + CASE COALESCE(s.region, 0) WHEN 10 THEN 17 WHEN 20 THEN -23 "
        "WHEN 30 THEN 34 WHEN 40 THEN -16 ELSE 0 END))"
        if demo_delivery else "0"
    )
    model_actual_rights = "0" if demo_delivery else "COUNT(DISTINCT rgr.code)"
    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.username,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            cur.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            for table in TARGETS:
                cur.execute(f"DELETE FROM `{table}`")

            loaded: dict[str, int] = {}
            loaded["dws_coupon_store_day"] = execute(cur, f"""
                INSERT INTO dws_coupon_store_day (
                    stat_date, stat_month, scene_type, store_code, store_name,
                    store_type, region_id, region_name, province_name,
                    send_cnt, received_cnt, used_cnt, send_amount, used_amount
                )
                SELECT DATE({coupon_timestamp}), DATE_FORMAT({coupon_timestamp}, '%Y-%m'), 'SERVICE',
                       s.store_code, s.store_name, s.store_type,
                       CAST(COALESCE(s.region, 0) AS CHAR), {REGION_NAME_SQL},
                       COALESCE(s.store_province_name, '未知'),
                       SUM({coupon_send_flag}), SUM({coupon_receive_flag}), SUM({coupon_use_flag}),
                       SUM(CASE WHEN {coupon_send_flag} THEN {coupon_face_value} ELSE 0 END),
                       SUM(CASE WHEN {coupon_use_flag} THEN {coupon_face_value} ELSE 0 END)
                FROM coupon c
                JOIN tur_store s ON s.store_code = c.received_store_id
                WHERE c.deleted = 0 AND c.send_at IS NOT NULL
                GROUP BY DATE({coupon_timestamp}), DATE_FORMAT({coupon_timestamp}, '%Y-%m'),
                         s.store_code, s.store_name, s.store_type, s.region,
                         s.store_province_name
            """)

            loaded["dws_coupon_region_day"] = execute(cur, """
                INSERT INTO dws_coupon_region_day (
                    stat_date, stat_month, scene_type, region_id, region_name,
                    send_cnt, received_cnt, used_cnt, expired_cnt, send_amount, used_amount
                )
                SELECT stat_date, stat_month, scene_type, region_id, MAX(region_name),
                       SUM(send_cnt), SUM(received_cnt), SUM(used_cnt),
                       SUM(send_cnt - used_cnt), SUM(send_amount), SUM(used_amount)
                FROM dws_coupon_store_day
                GROUP BY stat_date, stat_month, scene_type, region_id
            """)

            loaded["dws_employee_day"] = execute(cur, f"""
                INSERT INTO dws_employee_day (
                    stat_date, stat_month, scene_type, employee_account_id, employee_name,
                    store_code, store_name, region_id, region_name,
                    dist_order_cnt, dist_order_amount, dist_rights_cnt
                )
                SELECT DATE({employee_timestamp}),
                       DATE_FORMAT({employee_timestamp}, '%Y-%m'),
                       CASE WHEN r.recommend_scene LIKE 'db_%' THEN 'DELIVERY' ELSE 'SERVICE' END,
                       r.sales_person_id, r.sales_person_id,
                       s.store_code, s.store_name, CAST(COALESCE(s.region, 0) AS CHAR),
                       {REGION_NAME_SQL},
                       SUM({employee_order_flag}),
                       SUM({employee_order_amount}),
                       SUM({employee_rights_flag})
                FROM sales_order_recommend_record r
                JOIN tur_store s ON s.store_code = r.sales_store_id
                WHERE r.deleted = 0
                  AND COALESCE(r.order_pay_time, r.finish_time, r.created_at) IS NOT NULL
                GROUP BY DATE({employee_timestamp}),
                         DATE_FORMAT({employee_timestamp}, '%Y-%m'),
                         CASE WHEN r.recommend_scene LIKE 'db_%' THEN 'DELIVERY' ELSE 'SERVICE' END,
                         r.sales_person_id, s.store_code, s.store_name, s.region
            """)

            loaded["dws_rights_store_day"] = execute(cur, f"""
                INSERT INTO dws_rights_store_day (
                    stat_date, stat_month, scene_type, store_code, store_name,
                    store_type, region_id, region_name, rights_open_cnt,
                    order_cnt, order_amount, rights_cost_total
                )
                SELECT DATE({rights_timestamp}), DATE_FORMAT({rights_timestamp}, '%Y-%m'),
                       {SCENE_SQL}, s.store_code, s.store_name, s.store_type,
                       CAST(COALESCE(s.region, 0) AS CHAR), {REGION_NAME_SQL},
                       {rights_open_count},
                       COUNT(DISTINCT CASE WHEN {rights_order_flag} THEN o.sales_order_id END),
                       SUM({rights_order_amount}), SUM({rights_cost})
                FROM t_rights_group_record rgr
                JOIN sales_order o ON o.sales_order_id = rgr.order_number
                JOIN sales_order_recommend_record rr ON rr.sales_order_id = o.sales_order_id
                JOIN tur_store s ON s.store_code = rr.sales_store_id
                LEFT JOIN t_rights_group rg ON rg.code = rgr.group_code
                WHERE rgr.created_at IS NOT NULL
                GROUP BY DATE({rights_timestamp}), DATE_FORMAT({rights_timestamp}, '%Y-%m'),
                         {SCENE_SQL}, s.store_code, s.store_name, s.store_type, s.region
            """)

            loaded["dws_rights_region_day"] = execute(cur, """
                INSERT INTO dws_rights_region_day (
                    stat_date, stat_month, scene_type, region_id, region_name,
                    rights_open_cnt, rights_delivery_cnt, rights_service_cnt,
                    order_cnt, order_amount, rights_cost_total
                )
                SELECT stat_date, stat_month, scene_type, region_id, MAX(region_name),
                       SUM(rights_open_cnt),
                       SUM(CASE WHEN scene_type = 'DELIVERY' THEN rights_open_cnt ELSE 0 END),
                       SUM(CASE WHEN scene_type = 'SERVICE' THEN rights_open_cnt ELSE 0 END),
                       SUM(order_cnt), SUM(order_amount), SUM(rights_cost_total)
                FROM dws_rights_store_day
                GROUP BY stat_date, stat_month, scene_type, region_id
            """)

            loaded["dws_vehicle_model_day"] = execute(cur, f"""
                INSERT INTO dws_vehicle_model_day (
                    stat_date, stat_month, scene_type, series_id, series_name,
                    model_id, model_name, delivery_cnt, coupon_send_cnt,
                    coupon_used_cnt, rights_open_cnt, order_amount
                )
                SELECT stat_date, DATE_FORMAT(stat_date, '%Y-%m'), scene_type,
                       series_id, MAX(series_name), model_id, MAX(model_name),
                       SUM(delivery_cnt), SUM(coupon_send_cnt), SUM(coupon_used_cnt),
                       SUM(rights_open_cnt), SUM(order_amount)
                FROM (
                    SELECT DATE({coupon_timestamp}) stat_date, {coupon_scene} scene_type,
                           vm.vehicle_series_id series_id, vs.name series_name,
                           v.model_id, v.model_name, {demo_model_delivery} delivery_cnt,
                           SUM({coupon_send_flag}) coupon_send_cnt, SUM({coupon_use_flag}) coupon_used_cnt,
                           {demo_model_rights} rights_open_cnt, 0 order_amount
                    FROM coupon c
                    JOIN vehicle_order v ON v.vin = c.vin
                    JOIN vehicle_model vm ON vm.vehicle_model_id = v.model_id
                    JOIN vehicle_series vs ON vs.vehicle_series_id = vm.vehicle_series_id
                    JOIN tur_store s ON s.store_code = c.received_store_id
                    WHERE c.deleted = 0 AND c.send_at IS NOT NULL
                    GROUP BY DATE({coupon_timestamp}), vm.vehicle_series_id, vs.name, v.model_id, v.model_name
                    UNION ALL
                    SELECT DATE({rights_timestamp}), {SCENE_SQL},
                           vm.vehicle_series_id, vs.name, v.model_id, v.model_name,
                           0, 0, 0, {model_actual_rights}, SUM({rights_order_amount})
                    FROM t_rights_group_record rgr
                    JOIN vehicle_order v ON v.vin = rgr.vin
                    JOIN vehicle_model vm ON vm.vehicle_model_id = v.model_id
                    JOIN vehicle_series vs ON vs.vehicle_series_id = vm.vehicle_series_id
                    JOIN sales_order o ON o.sales_order_id = rgr.order_number
                    JOIN sales_order_recommend_record rr ON rr.sales_order_id = o.sales_order_id
                    JOIN tur_store s ON s.store_code = rr.sales_store_id
                    WHERE rgr.created_at IS NOT NULL
                    GROUP BY DATE({rights_timestamp}), {SCENE_SQL},
                             vm.vehicle_series_id, vs.name, v.model_id, v.model_name
                ) x
                GROUP BY stat_date, scene_type, series_id, model_id
            """)

            if demo_delivery:
                loaded["dws_delivery_region_day"] = execute(cur, f"""
                    INSERT INTO dws_delivery_region_day (
                        stat_date, stat_month, region_id, region_name,
                        delivery_cnt, coupon_send_cnt, coupon_used_cnt, rights_open_cnt
                    )
                    SELECT DATE({coupon_timestamp}), DATE_FORMAT({coupon_timestamp}, '%Y-%m'),
                           CAST(COALESCE(s.region, 0) AS CHAR), {REGION_NAME_SQL},
                           {demo_model_delivery},
                           SUM({coupon_send_flag}), SUM({coupon_use_flag}),
                           {demo_model_rights}
                    FROM coupon c
                    JOIN vehicle_order v ON v.vin = c.vin
                    JOIN tur_store s ON s.store_code = c.received_store_id
                    WHERE c.deleted = 0 AND c.send_at IS NOT NULL
                    GROUP BY DATE({coupon_timestamp}), DATE_FORMAT({coupon_timestamp}, '%Y-%m'), s.region
                """)
            else:
                # The supplied vehicle orders contain no valid completed rows.
                loaded["dws_delivery_region_day"] = 0

            loaded["dws_cost_roi_day"] = execute(cur, """
                INSERT INTO dws_cost_roi_day (
                    stat_date, stat_month, scene_type, region_id, region_name,
                    coupon_send_amount, coupon_used_amount, rights_cost,
                    order_cnt, order_amount
                )
                SELECT stat_date, DATE_FORMAT(stat_date, '%Y-%m'), scene_type,
                       region_id, MAX(region_name), SUM(coupon_send_amount),
                       SUM(coupon_used_amount), SUM(rights_cost),
                       SUM(order_cnt), SUM(order_amount)
                FROM (
                    SELECT stat_date, scene_type, region_id, region_name,
                           send_amount coupon_send_amount, used_amount coupon_used_amount,
                           0 rights_cost, 0 order_cnt, 0 order_amount
                    FROM dws_coupon_region_day
                    UNION ALL
                    SELECT stat_date, scene_type, region_id, region_name,
                           0, 0, rights_cost_total, order_cnt, order_amount
                    FROM dws_rights_region_day
                ) x
                GROUP BY stat_date, scene_type, region_id
            """)

            conn.commit()
            print(f"delivery_mode: {'DEMO_SYNTHETIC' if demo_delivery else 'SOURCE_ONLY'}")
            for table in TARGETS:
                cur.execute(f"SELECT COUNT(*) FROM `{table}`")
                print(f"{table}: inserted={loaded.get(table, 0)} rows={cur.fetchone()[0]}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
