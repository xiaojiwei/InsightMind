#!/usr/bin/env python3
"""Build the governed mall_robot after-sales marketing business KG."""
from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from pathlib import Path

import pymysql
from rdflib import Graph, Literal, Namespace, RDF, URIRef
from rdflib.namespace import XSD

BASE_DIR = Path(__file__).resolve().parents[1]
if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

from kg_builder.business_kg.llm_builder import _ONTOLOGY_PREAMBLE


IND = Namespace("http://indicator.insightmind.com/ontology#")
INST = Namespace("http://indicator.insightmind.com/instance/")

TABLES = (
    "dws_coupon_region_day",
    "dws_coupon_store_day",
    "dws_delivery_region_day",
    "dws_employee_day",
    "dws_rights_region_day",
    "dws_rights_store_day",
    "dws_vehicle_model_day",
    "dws_cost_roi_day",
)


@dataclass(frozen=True)
class App:
    table: str
    column: str = ""
    operator: str = "sum"
    operands: tuple[str, ...] = ()


@dataclass(frozen=True)
class Measure:
    code: str
    name: str
    definition: str
    caliber: str
    unit: str
    category: str
    apps: tuple[App, ...]
    north_star: bool = False


BASE_MEASURES = (
    Measure("MEAS_delivery_cnt", "交车量", "已完成交付的车辆数量", "SUM(delivery_cnt)，日粒度可加和", "辆", "funnel", (App("dws_delivery_region_day", "delivery_cnt"), App("dws_vehicle_model_day", "delivery_cnt"))),
    Measure("MEAS_coupon_send_cnt", "发券量", "营销优惠券发放数量", "SUM(send_cnt/coupon_send_cnt)，包含成功发放的券实例", "张", "coupon", (App("dws_coupon_region_day", "send_cnt"), App("dws_coupon_store_day", "send_cnt"), App("dws_delivery_region_day", "coupon_send_cnt"), App("dws_vehicle_model_day", "coupon_send_cnt"))),
    Measure("MEAS_coupon_received_cnt", "领券量", "已被用户领取的优惠券数量", "SUM(received_cnt)，领取后即使已使用或过期仍计入", "张", "coupon", (App("dws_coupon_region_day", "received_cnt"), App("dws_coupon_store_day", "received_cnt"))),
    Measure("MEAS_coupon_used_cnt", "核销量", "已使用优惠券数量", "SUM(used_cnt)，源口径 coupon.status=1", "张", "coupon", (App("dws_coupon_region_day", "used_cnt"), App("dws_coupon_store_day", "used_cnt"), App("dws_delivery_region_day", "coupon_used_cnt"), App("dws_vehicle_model_day", "coupon_used_cnt")), True),
    Measure("MEAS_coupon_expired_cnt", "优惠券过期量", "失效且未核销的优惠券数量", "SUM(expired_cnt)，源口径 coupon.status=2；当前源数据需复核状态3", "张", "coupon", (App("dws_coupon_region_day", "expired_cnt"),)),
    Measure("MEAS_coupon_send_amount", "发券面额", "发放优惠券的面额合计", "SUM(send_amount/coupon_send_amount)，金额单位为分", "分", "cost", (App("dws_coupon_region_day", "send_amount"), App("dws_coupon_store_day", "send_amount"), App("dws_cost_roi_day", "coupon_send_amount"))),
    Measure("MEAS_coupon_used_amount", "核销面额", "已核销优惠券的面额合计", "SUM(used_amount/coupon_used_amount)，金额单位为分", "分", "cost", (App("dws_coupon_region_day", "used_amount"), App("dws_coupon_store_day", "used_amount"), App("dws_cost_roi_day", "coupon_used_amount"))),
    Measure("MEAS_rights_open_cnt", "权益开通量", "售后服务权益包开通数量", "SUM(rights_open_cnt)，按权益实例去重后的日汇总", "个", "rights", (App("dws_delivery_region_day", "rights_open_cnt"), App("dws_rights_region_day", "rights_open_cnt"), App("dws_rights_store_day", "rights_open_cnt"), App("dws_vehicle_model_day", "rights_open_cnt"))),
    Measure("MEAS_rights_delivery_cnt", "交付场景权益开通量", "交付场景开通的权益包数量", "SUM(rights_delivery_cnt)", "个", "rights", (App("dws_rights_region_day", "rights_delivery_cnt"),)),
    Measure("MEAS_rights_service_cnt", "服务场景权益开通量", "服务场景开通的权益包数量", "SUM(rights_service_cnt)", "个", "rights", (App("dws_rights_region_day", "rights_service_cnt"),)),
    Measure("MEAS_service_order_cnt", "服务转化订单量", "产生销售额的售后服务订单数量", "SUM(order_cnt)，DWS 已限定 order_amount>0", "单", "sales", (App("dws_rights_region_day", "order_cnt"), App("dws_rights_store_day", "order_cnt"), App("dws_cost_roi_day", "order_cnt"))),
    Measure("MEAS_service_order_amount", "服务销售额", "售后服务及权益订单实付金额", "SUM(order_amount)，金额单位为分", "分", "sales", (App("dws_rights_region_day", "order_amount"), App("dws_rights_store_day", "order_amount"), App("dws_vehicle_model_day", "order_amount"), App("dws_cost_roi_day", "order_amount")), True),
    Measure("MEAS_rights_cost", "权益成本", "售出或开通权益包的成本合计", "SUM(rights_cost_total/rights_cost)，金额单位为分", "分", "cost", (App("dws_rights_region_day", "rights_cost_total"), App("dws_rights_store_day", "rights_cost_total"), App("dws_cost_roi_day", "rights_cost"))),
    Measure("MEAS_dist_order_cnt", "顾问分销订单量", "售后顾问分销转化订单数量", "SUM(dist_order_cnt)", "单", "employee", (App("dws_employee_day", "dist_order_cnt"),)),
    Measure("MEAS_dist_order_amount", "顾问分销销售额", "售后顾问分销订单金额", "SUM(dist_order_amount)，金额单位为分", "分", "employee", (App("dws_employee_day", "dist_order_amount"),)),
    Measure("MEAS_dist_rights_cnt", "顾问分销权益量", "售后顾问分销权益包数量", "SUM(dist_rights_cnt)", "个", "employee", (App("dws_employee_day", "dist_rights_cnt"),)),
)


RATIO_MEASURES = (
    Measure("MEAS_coupon_receive_rate", "领券率", "发券后被用户领取的比例", "领券量/发券量；分母为0时返回空", "%", "funnel", (App("dws_coupon_region_day", operands=("MEAS_coupon_received_cnt", "MEAS_coupon_send_cnt")), App("dws_coupon_store_day", operands=("MEAS_coupon_received_cnt", "MEAS_coupon_send_cnt"))),),
    Measure("MEAS_coupon_conversion_rate", "优惠券转化率", "发券后完成核销的比例", "核销量/发券量；分母为0时返回空", "%", "funnel", (App("dws_coupon_region_day", operands=("MEAS_coupon_used_cnt", "MEAS_coupon_send_cnt")), App("dws_coupon_store_day", operands=("MEAS_coupon_used_cnt", "MEAS_coupon_send_cnt")), App("dws_delivery_region_day", operands=("MEAS_coupon_used_cnt", "MEAS_coupon_send_cnt")), App("dws_vehicle_model_day", operands=("MEAS_coupon_used_cnt", "MEAS_coupon_send_cnt"))), True),
    Measure("MEAS_coupon_redemption_rate", "领券核销率", "已领取优惠券完成核销的比例", "核销量/领券量；分母为0时返回空", "%", "funnel", (App("dws_coupon_region_day", operands=("MEAS_coupon_used_cnt", "MEAS_coupon_received_cnt")), App("dws_coupon_store_day", operands=("MEAS_coupon_used_cnt", "MEAS_coupon_received_cnt"))),),
    Measure("MEAS_delivery_send_rate", "交车发券率", "交付车辆中触达发券的比例", "交付场景发券量/交车量", "%", "funnel", (App("dws_delivery_region_day", operands=("MEAS_coupon_send_cnt", "MEAS_delivery_cnt")), App("dws_vehicle_model_day", operands=("MEAS_coupon_send_cnt", "MEAS_delivery_cnt"))),),
    Measure("MEAS_delivery_penetration_rate", "售后营销渗透率", "交付车辆最终完成优惠券核销的比例", "核销量/交车量", "%", "funnel", (App("dws_delivery_region_day", operands=("MEAS_coupon_used_cnt", "MEAS_delivery_cnt")), App("dws_vehicle_model_day", operands=("MEAS_coupon_used_cnt", "MEAS_delivery_cnt"))), True),
    Measure("MEAS_rights_attach_rate", "权益附加率", "交付车辆开通权益包的比例", "权益开通量/交车量", "%", "rights", (App("dws_delivery_region_day", operands=("MEAS_rights_open_cnt", "MEAS_delivery_cnt")), App("dws_vehicle_model_day", operands=("MEAS_rights_open_cnt", "MEAS_delivery_cnt"))),),
    Measure("MEAS_avg_service_order_amount", "服务客单价", "每笔转化服务订单的平均实付金额", "服务销售额/服务转化订单量，结果单位为分/单", "分/单", "sales", (App("dws_rights_region_day", operands=("MEAS_service_order_amount", "MEAS_service_order_cnt")), App("dws_rights_store_day", operands=("MEAS_service_order_amount", "MEAS_service_order_cnt")), App("dws_cost_roi_day", operands=("MEAS_service_order_amount", "MEAS_service_order_cnt"))),),
)


DIMENSIONS = {
    "DIM_stat_date": ("统计日期", 1, {table: "stat_date" for table in TABLES}),
    # DA applies DATE_FORMAT for viewTypeCode=3, so month must map to the
    # physical DATE column rather than the preformatted varchar stat_month.
    "DIM_stat_month": ("统计月份", 3, {table: "stat_date" for table in TABLES}),
    "DIM_scene_type": ("营销场景", 0, {table: "scene_type" for table in TABLES if table != "dws_delivery_region_day"}),
    "DIM_region": ("战区", 0, {table: "region_name" for table in TABLES if table != "dws_vehicle_model_day"}),
    "DIM_store": ("门店", 0, {table: "store_name" for table in ("dws_coupon_store_day", "dws_rights_store_day", "dws_employee_day")}),
    "DIM_store_type": ("门店类型", 0, {table: "store_type" for table in ("dws_coupon_store_day", "dws_rights_store_day")}),
    "DIM_province": ("省份", 0, {"dws_coupon_store_day": "province_name"}),
    "DIM_employee": ("售后顾问", 0, {"dws_employee_day": "employee_name"}),
    "DIM_vehicle_series": ("车系", 0, {"dws_vehicle_model_day": "series_name"}),
    "DIM_vehicle_model": ("车型", 0, {"dws_vehicle_model_day": "model_name"}),
}


def args_parser() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=os.getenv("MALL_ROBOT_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MALL_ROBOT_PORT", "3306")))
    parser.add_argument("--database", default=os.getenv("MALL_ROBOT_DATABASE", "mall_robot"))
    parser.add_argument("--username", default=os.getenv("MALL_ROBOT_USERNAME", "root"))
    parser.add_argument("--password", default=os.getenv("MALL_ROBOT_PASSWORD", ""))
    parser.add_argument("--output", type=Path, default=Path("output/mall_robot/mall_robot_business_kg.ttl"))
    return parser.parse_args()


def lit(value, datatype=None):
    return Literal(value, datatype=datatype)


def app_uri(measure_code: str, table: str) -> URIRef:
    return INST[f"ma_{measure_code.lower()}__{table}"]


def main() -> None:
    args = args_parser()
    conn = pymysql.connect(host=args.host, port=args.port, user=args.username, password=args.password, database=args.database, charset="utf8mb4")
    try:
        with conn.cursor() as cur:
            placeholders = ",".join(["%s"] * len(TABLES))
            cur.execute(
                f"SELECT table_name, table_comment FROM information_schema.tables WHERE table_schema=%s AND table_name IN ({placeholders})",
                (args.database, *TABLES),
            )
            table_comments = dict(cur.fetchall())
            cur.execute(
                f"SELECT table_name,column_name,column_type,is_nullable,column_key,ordinal_position,column_comment FROM information_schema.columns WHERE table_schema=%s AND table_name IN ({placeholders}) ORDER BY table_name,ordinal_position",
                (args.database, *TABLES),
            )
            columns = cur.fetchall()
            row_counts = {}
            for table in TABLES:
                cur.execute(f"SELECT COUNT(*) FROM `{table}`")
                row_counts[table] = int(cur.fetchone()[0])
    finally:
        conn.close()

    graph = Graph()
    graph.parse(data=_ONTOLOGY_PREAMBLE, format="turtle")
    graph.bind("ind", IND)
    graph.bind("inst", INST)

    connection = INST[f"conn_mysql_{args.host.replace('.', '_')}_{args.database}"]
    graph.add((connection, RDF.type, IND.DataConnection))
    for predicate, value, datatype in (
        (IND.dbType, "mysql", None), (IND.host, args.host, None), (IND.port, args.port, XSD.integer),
        (IND.dbUser, args.username, None), (IND.dbPassword, args.password, None), (IND.dbName, args.database, None),
    ):
        graph.add((connection, predicate, lit(value, datatype)))

    categories = {
        "funnel": "营销漏斗", "coupon": "优惠券运营", "rights": "权益运营",
        "sales": "服务销售", "cost": "成本与收益", "employee": "顾问绩效",
    }
    root = INST.cat_after_sales_marketing
    graph.add((root, RDF.type, IND.Category))
    graph.add((root, IND.code, lit("CAT_AFTER_SALES_MARKETING")))
    graph.add((root, IND.name, lit("售后服务营销")))
    for index, (key, name) in enumerate(categories.items(), 1):
        uri = INST[f"cat_{key}"]
        graph.add((uri, RDF.type, IND.Category))
        graph.add((uri, IND.code, lit(f"CAT_{key.upper()}")))
        graph.add((uri, IND.id, lit(9100 + index, XSD.long)))
        graph.add((uri, IND.name, lit(name)))
        graph.add((uri, IND.categoryParent, root))

    table_uris = {}
    for table in TABLES:
        uri = INST[f"tbl_{args.database}__{table}"]
        table_uris[table] = uri
        graph.add((uri, RDF.type, IND.DwTable))
        graph.add((uri, IND.schemaName, lit(args.database)))
        graph.add((uri, IND.tableName, lit(table)))
        graph.add((uri, IND.cnName, lit(table_comments.get(table) or table)))
        graph.add((uri, IND.sourceTypeCode, lit(0, XSD.integer)))
        graph.add((uri, IND.hasConnection, connection))
        hist = INST[f"hist_tbl_{table}"]
        graph.add((hist, RDF.type, IND.TableHistogram))
        graph.add((hist, IND.tableRowNum, lit(row_counts[table], XSD.long)))
        graph.add((hist, IND.maxScanNum, lit(max(row_counts[table], 100000), XSD.long)))
        graph.add((uri, IND.hasTableHistogram, hist))

    for table, name, col_type, nullable, key, position, comment in columns:
        table_uri = table_uris[table]
        uri = INST[f"col_{args.database}__{table}__{name}"]
        graph.add((uri, RDF.type, IND.DwColumn))
        graph.add((uri, IND.columnName, lit(name)))
        graph.add((uri, IND.columnType, lit(col_type)))
        graph.add((uri, IND.columnComment, lit(comment or name)))
        graph.add((uri, IND.isPrimaryKey, lit(key == "PRI", XSD.boolean)))
        graph.add((uri, IND.isNullable, lit(nullable == "YES", XSD.boolean)))
        graph.add((uri, IND.ordinalPosition, lit(position, XSD.integer)))
        graph.add((table_uri, IND.hasColumn, uri))

    for code, (name, view_type, mappings) in DIMENSIONS.items():
        dim = INST[f"dim_{code.lower()}"]
        graph.add((dim, RDF.type, IND.Dimension))
        graph.add((dim, IND.code, lit(code)))
        graph.add((dim, IND.cnName, lit(name)))
        graph.add((dim, IND.enName, lit(code)))
        graph.add((dim, IND.definition, lit(f"售后服务营销分析公共{name}维度")))
        graph.add((dim, IND.dimTypeCode, lit(0, XSD.integer)))
        graph.add((dim, IND.viewTypeCode, lit(view_type, XSD.integer)))
        graph.add((dim, IND.isHyper, lit(code in {"DIM_stat_date", "DIM_stat_month"}, XSD.boolean)))
        if code in {"DIM_stat_date", "DIM_stat_month"}:
            graph.add((dim, IND.hierarchyCode, lit("HIER_STAT_TIME")))
            graph.add((dim, IND.levelSequence, lit(2 if code.endswith("month") else 3, XSD.integer)))
            graph.add((dim, IND.levelCode, lit("month" if code.endswith("month") else "day")))
        for table, column in mappings.items():
            dim_app = INST[f"da_{code.lower()}__{table}"]
            graph.add((dim_app, RDF.type, IND.DimensionApp))
            graph.add((dim_app, IND.dimTypeCode, lit(0, XSD.integer)))
            graph.add((dim_app, IND.dimFactColumn, lit(column)))
            graph.add((dim_app, IND.masterPrimaryKey, lit(column)))
            graph.add((dim_app, IND.isMasterApp, lit(True, XSD.boolean)))
            graph.add((dim_app, IND.isRootJoin, lit(True, XSD.boolean)))
            graph.add((dim_app, IND.dimFactTable, table_uris[table]))
            graph.add((dim, IND.hasDimApp, dim_app))

    all_measures = BASE_MEASURES + RATIO_MEASURES
    base_app_lookup = {(measure.code, app.table): app_uri(measure.code, app.table) for measure in BASE_MEASURES for app in measure.apps}
    for measure in all_measures:
        measure_uri = INST[f"meas_{measure.code.lower()}"]
        graph.add((measure_uri, RDF.type, IND.Measure))
        graph.add((measure_uri, IND.code, lit(measure.code)))
        graph.add((measure_uri, IND.cnName, lit(measure.name)))
        graph.add((measure_uri, IND.enName, lit(measure.code)))
        graph.add((measure_uri, IND.definition, lit(measure.definition)))
        graph.add((measure_uri, IND.caliber, lit(measure.caliber)))
        graph.add((measure_uri, IND.unit, lit(measure.unit)))
        graph.add((measure_uri, IND.measTypeCode, lit(1 if measure.apps[0].operands else 0, XSD.integer)))
        graph.add((measure_uri, IND.northStar, lit(1 if measure.north_star else 0, XSD.integer)))
        graph.add((measure_uri, IND.online, lit(1, XSD.integer)))
        graph.add((measure_uri, IND.belongsToCategory, INST[f"cat_{measure.category}"]))
        for app in measure.apps:
            uri = app_uri(measure.code, app.table)
            graph.add((uri, RDF.type, IND.MeasureApp))
            graph.add((uri, IND.applyTypeCode, lit(1 if app.operands else 0, XSD.integer)))
            if app.operands:
                expression = []
                for idx, operand in enumerate(app.operands):
                    if idx:
                        expression.append({"operatingType": "operator", "operator": "/"})
                    expression.append({"operatingType": "operand", "operand": {"measCode": operand}})
                    dependency = base_app_lookup.get((operand, app.table))
                    if dependency:
                        graph.add((uri, IND.dependsOnMeasApp, dependency))
            else:
                expression = [{"operatingType": "operator", "operator": app.operator}]
                graph.add((uri, IND.factColumn, lit(app.column)))
            graph.add((uri, IND.expression, lit(json.dumps(expression, ensure_ascii=False, separators=(",", ":")))))
            graph.add((uri, IND.hasColumnDT, lit(False, XSD.boolean)))
            graph.add((uri, IND.available, lit(1, XSD.integer)))
            graph.add((uri, IND.appliesToTable, table_uris[app.table]))
            ndm = INST[f"ndm_{measure.code.lower()}__{app.table}__date"]
            graph.add((ndm, RDF.type, IND.NaturalDimMapping))
            graph.add((ndm, IND.naturalHierarchyCode, lit("HIER_STAT_TIME")))
            graph.add((ndm, IND.physicalColumn, lit("stat_date")))
            graph.add((uri, IND.hasNaturalDimMapping, ndm))
            graph.add((measure_uri, IND.hasMeasureApp, uri))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(graph.serialize(format="turtle"), encoding="utf-8")
    print(f"business_kg={args.output.resolve()}")
    print(f"tables={len(TABLES)} measures={len(all_measures)} dimensions={len(DIMENSIONS)} triples={len(graph)}")
    print("row_counts=" + ",".join(f"{table}:{row_counts[table]}" for table in TABLES))


if __name__ == "__main__":
    main()
