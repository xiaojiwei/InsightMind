#!/usr/bin/env python3
"""
修复 MeasureApp 缺失的 hasNaturalDimMapping（指标→日期维度关联）

对于每个 MeasureApp：
1. 从 appliesToTable 推断物理日期列（如 ss_sold_date_sk）
2. 生成 inst:ndm_<meas_code_short>_<table>_date NaturalDimMapping 实例
3. 用 hasNaturalDimMapping 属性将 MeasureApp 关联到该 NaturalDimMapping

同时为衍生指标（applyTypeCode=1）补充缺失的 dependsOnMeasApp。
"""
from pathlib import Path
from rdflib import Graph, RDF, Namespace, Literal, URIRef

BASE_DIR = Path(__file__).parent
TTL = BASE_DIR / "output/business_kg/indicator-data.ttl"

IND = Namespace("http://indicator.lixiang.com/ontology#")
INST = Namespace("http://indicator.lixiang.com/instance/")

# 事实表 → 日期外键列 映射
DATE_COLUMN_MAP = {
    # 销售表：sold_date_sk
    "tbl_tpcds__store_sales":    "ss_sold_date_sk",
    "tbl_tpcds__catalog_sales":  "cs_sold_date_sk",
    "tbl_tpcds__web_sales":      "ws_sold_date_sk",
    # 退货表：return_date_sk
    "tbl_tpcds__store_returns":   "sr_return_date_sk",
    "tbl_tpcds__catalog_returns": "cr_return_date_sk",
    "tbl_tpcds__web_returns":     "wr_return_date_sk",
}


def meas_code_to_short(code: str) -> str:
    """从 Measure code 提取短名，如 store_sales_amount → store_sales"""
    # 去掉常见前缀后缀
    s = code
    for suffix in ["_amount", "_amt", "_quantity", "_qty", "_cnt", "_cost", "_profit", "_rate"]:
        if s.endswith(suffix):
            s = s[:-len(suffix)]
    return s


def build_ndm_uri(meas_app_uri: str) -> URIRef:
    """为 MeasureApp 生成唯一的 NaturalDimMapping URI"""
    # inst:ma_store_sales_amount_store_sales → inst:ndm_store_sales_amount_store_sales_date
    short = meas_app_uri.replace("http://indicator.lixiang.com/instance/", "")
    return INST[f"ndm_{short}_date"]


def get_date_column(table_uri_str: str) -> str:
    """从 appliesToTable 获取日期物理列名"""
    return DATE_COLUMN_MAP.get(table_uri_str.replace("http://indicator.lixiang.com/instance/", ""), "ss_sold_date_sk")


def add_ndm_to_graph(g: Graph, ma_uri: URIRef, meas_code: str, table_uri_str: str) -> URIRef:
    """为 MeasureApp 添加 NaturalDimMapping 实例和关联三元组"""
    ndm_uri = build_ndm_uri(str(ma_uri))
    date_col = get_date_column(table_uri_str)

    # NaturalDimMapping 实例三元组
    g.add((ndm_uri, RDF.type, IND.NaturalDimMapping))
    g.add((ndm_uri, IND.naturalHierarchyCode, Literal("h_date")))
    g.add((ndm_uri, IND.physicalColumn, Literal(date_col)))

    # MeasureApp → hasNaturalDimMapping → NDM
    g.add((ma_uri, IND.hasNaturalDimMapping, ndm_uri))

    return ndm_uri


def main():
    g = Graph()
    g.parse(str(TTL), format="turtle")
    print(f"加载: {len(g)} triples")

    # ── 1. 为每个 MeasureApp 补充 hasNaturalDimMapping ────────────────── #
    ndm_added = 0
    ma_without_ndm = []

    for ma_uri in list(g.subjects(RDF.type, IND.MeasureApp)):
        # 检查是否已有 hasNaturalDimMapping
        ndm_list = list(g.objects(ma_uri, IND.hasNaturalDimMapping))
        if ndm_list:
            continue

        ma_code = str(g.value(ma_uri, IND.code) or "")
        applies_to = list(g.objects(ma_uri, IND.appliesToTable))
        if not applies_to:
            ma_without_ndm.append((ma_code, "无 appliesToTable"))
            continue

        table_uri = applies_to[0]
        table_uri_str = str(table_uri)

        # 推断日期列
        date_col = get_date_column(table_uri_str)

        ndm_uri = add_ndm_to_graph(g, ma_uri, ma_code, table_uri_str)
        ndm_added += 1
        print(f"  + NDM: ndm_{ma_code}_date → {date_col}")

    print(f"\n补充了 {ndm_added} 个 NaturalDimMapping")
    if ma_without_ndm:
        print(f"无法补充 ({len(ma_without_ndm)}):")
        for code, reason in ma_without_ndm[:5]:
            print(f"  {code}: {reason}")

    # ── 2. 为衍生指标 MeasureApp 补充 dependsOnMeasApp ────────────────── #
    # 衍生指标：expression 中有 referenceMetricCode，引用原子指标 code
    # 如 total_sales_amount 依赖 store_sales_amount + catalog_sales_amount + web_sales_amount
    DERIVED_DEPENDS = {
        "total_sales_amount": ["store_sales_amount", "catalog_sales_amount", "web_sales_amount"],
        "total_return_amt":    ["store_return_amt", "catalog_return_amt", "web_return_amt"],
    }

    dep_fixed = 0
    for ma_uri in list(g.subjects(RDF.type, IND.MeasureApp)):
        ma_code = str(g.value(ma_uri, IND.code) or "")
        apply_type = list(g.objects(ma_uri, IND.applyTypeCode))
        if not apply_type:
            continue
        atc = str(apply_type[0])
        if atc != "1":  # 只处理衍生指标
            continue

        # 检查是否有 dependsOnMeasApp
        if list(g.objects(ma_uri, IND.dependsOnMeasApp)):
            continue

        # 从 ma_code 推断衍生指标名（如 ma_total_sales_amount_derived → total_sales_amount）
        derived_name = ma_code.replace("ma_", "").replace("_derived", "").replace("_store_sales", "").replace("_catalog_sales", "").replace("_web_sales", "")
        # 简化：直接从 ma_code 拆
        parts = ma_code.split("_")
        # ma_total_sales_amount_derived → ["ma","total","sales","amount","derived"]
        # 去掉 ma_ 前缀和 _derived 后缀
        meas_name = ma_code.replace("ma_", "").replace("_derived", "")
        for suffix in ["_store_sales", "_catalog_sales", "_web_sales", "_store_returns", "_catalog_returns", "_web_returns"]:
            meas_name = meas_name.replace(suffix, "")
        deps = DERIVED_DEPENDS.get(meas_name, [])

        for dep_meas in deps:
            # 找对应的原子 MeasureApp
            dep_ma_codes = [
                f"ma_{dep_meas}_{t}"
                for t in ["store_sales", "catalog_sales", "web_sales",
                          "store_returns", "catalog_returns", "web_returns"]
            ]
            for dep_ma_code in dep_ma_codes:
                dep_ma_uri = INST[dep_ma_code]
                if (dep_ma_uri, RDF.type, IND.MeasureApp) in g:
                    g.add((ma_uri, IND.dependsOnMeasApp, dep_ma_uri))
                    print(f"  + dependsOn: {ma_code} → {dep_ma_code}")
                    dep_fixed += 1
                    break

    print(f"\n补充了 {dep_fixed} 个 dependsOnMeasApp")

    # ── 验证 ────────────────────────────────────────────────────────── #
    print("\n=== 验证 ===")

    # NaturalDimMapping
    ndm_count = len(list(g.subjects(RDF.type, IND.NaturalDimMapping)))
    print(f"NaturalDimMapping: {ndm_count}")

    # 有 hasNaturalDimMapping 的 MeasureApp
    ma_with_ndm = len([ma for ma in g.subjects(RDF.type, IND.MeasureApp)
                        if list(g.objects(ma, IND.hasNaturalDimMapping))])
    ma_total = len(list(g.subjects(RDF.type, IND.MeasureApp)))
    print(f"MeasureApp 有日期维度: {ma_with_ndm}/{ma_total}")

    # 有 dependsOnMeasApp 的衍生 MeasureApp
    derived_mas = [ma for ma in g.subjects(RDF.type, IND.MeasureApp)
                   if list(g.objects(ma, IND.applyTypeCode)) and
                      str(list(g.objects(ma, IND.applyTypeCode))[0]) == "1"]
    if derived_mas:
        derived_with_deps = sum(1 for ma in derived_mas if list(g.objects(ma, IND.dependsOnMeasApp)))
        print(f"衍生指标有 dependsOn: {derived_with_deps}/{len(derived_mas)}")

    # ── 保存 ────────────────────────────────────────────────────────── #
    g.serialize(str(TTL), format="turtle")
    print(f"\n已保存: {TTL}")
    print(f"文件大小: {TTL.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
