#!/usr/bin/env python3
"""
分阶段生成 TPC-DS 业务图谱
Phase 1: Measure + MeasureApp（销售指标、退货指标、跨渠道衍生指标）
Phase 2: Dimension + DimensionApp + DwTable + Category
"""
from pathlib import Path
from rdflib import Graph, RDF, Namespace
from kg_builder.business_kg.llm_builder import BusinessKGBuilder, _ONTOLOGY_PREAMBLE

BASE_DIR = Path(__file__).parent
TTL_PATH = BASE_DIR / "output/kg_tpcds.ttl"
OUT_PATH_PHASE1 = BASE_DIR / "output/business_kg/phase1_measures.ttl"
OUT_PATH_PHASE2 = BASE_DIR / "output/business_kg/phase2_dimensions.ttl"
OUT_FINAL = BASE_DIR / "output/business_kg/indicator-data.ttl"


# ── Phase 1: Measure + MeasureApp 提示词（只生成指标，禁止生成维度） ──────── #
PHASE1_HINT = """\
你是一位资深零售数据分析建模专家。基于 TPC-DS 数据库，为一个零售企业生成**指标和指标应用**实例。

## 业务场景
TPC-DS 是零售决策支持数据库，三大销售渠道：
- store_sales / store_returns — 门店销售/退货
- catalog_sales / catalog_returns — 目录销售/退货（列名前缀 cs_）
- web_sales / web_returns — 网站销售/退货（列名前缀 ws_）

## 指标设计任务

### 第一类：销售原子指标（applyTypeCode=0）
每个渠道（store_sales / catalog_sales / web_sales）各生成一套，每个指标必须同时生成对应的 MeasureApp。

注意：catalog_sales 列名以 cs_ 开头，web_sales 以 ws_ 开头。
例如 catalog_sales 的销售额列是 cs_sales_price，web_sales 的是 ws_sales_price。

每个指标生成两个实例：
1. ind:Measure（指标定义）
2. ind:MeasureApp（指标在某张事实表上的具体应用）

**store_sales 渠道指标：**
1. 销售额 (store_sales_amount) — factColumn="ss_ext_sales_price", expression=[{"operatingType":"operator","operator":"sum"}]
2. 销售成本 (store_sales_cost) — factColumn="ss_ext_wholesale_cost", expression=[{"operatingType":"operator","operator":"sum"}]
3. 销售利润 (store_sales_profit) — factColumn="ss_net_profit", expression=[{"operatingType":"operator","operator":"sum"}]
4. 销售数量 (store_sales_quantity) — factColumn="ss_quantity", expression=[{"operatingType":"operator","operator":"sum"}]
5. 折扣金额 (store_discount_amt) — factColumn="ss_ext_discount_amt", expression=[{"operatingType":"operator","operator":"sum"}]
6. 税额 (store_tax_amt) — factColumn="ss_ext_tax", expression=[{"operatingType":"operator","operator":"sum"}]
7. 优惠券金额 (store_coupon_amt) — factColumn="ss_coupon_amt", expression=[{"operatingType":"operator","operator":"sum"}]
8. 净支付金额 (store_net_paid) — factColumn="ss_net_paid", expression=[{"operatingType":"operator","operator":"sum"}]

**catalog_sales 渠道指标：**
1. 销售额 (catalog_sales_amount) — factColumn="cs_sales_price", expression=[{"operatingType":"operator","operator":"sum"}]
2. 销售成本 (catalog_sales_cost) — factColumn="cs_wholesale_cost", expression=[{"operatingType":"operator","operator":"sum"}]
3. 销售利润 (catalog_sales_profit) — factColumn="cs_net_profit", expression=[{"operatingType":"operator","operator":"sum"}]
4. 销售数量 (catalog_sales_quantity) — factColumn="cs_quantity", expression=[{"operatingType":"operator","operator":"sum"}]
5. 折扣金额 (catalog_discount_amt) — factColumn="cs_discount_amt", expression=[{"operatingType":"operator","operator":"sum"}]
6. 税额 (catalog_tax_amt) — factColumn="cs_tax", expression=[{"operatingType":"operator","operator":"sum"}]
7. 优惠券金额 (catalog_coupon_amt) — factColumn="cs_coupon_amt", expression=[{"operatingType":"operator","operator":"sum"}]

**web_sales 渠道指标：**
1. 销售额 (web_sales_amount) — factColumn="ws_sales_price", expression=[{"operatingType":"operator","operator":"sum"}]
2. 销售成本 (web_sales_cost) — factColumn="ws_wholesale_cost", expression=[{"operatingType":"operator","operator":"sum"}]
3. 销售利润 (web_sales_profit) — factColumn="ws_net_profit", expression=[{"operatingType":"operator","operator":"sum"}]
4. 销售数量 (web_sales_quantity) — factColumn="ws_quantity", expression=[{"operatingType":"operator","operator":"sum"}]
5. 折扣金额 (web_discount_amt) — factColumn="ws_discount_amt", expression=[{"operatingType":"operator","operator":"sum"}]
6. 税额 (web_tax_amt) — factColumn="ws_tax", expression=[{"operatingType":"operator","operator":"sum"}]
7. 优惠券金额 (web_coupon_amt) — factColumn="ws_coupon_amt", expression=[{"operatingType":"operator","operator":"sum"}]

### 第二类：退货原子指标（applyTypeCode=0）
每个退货渠道各生成一套，每个指标同时生成 MeasureApp。

**store_returns 渠道：**
1. 退货笔数 (store_return_cnt) — factColumn="sr_ticket_number", expression=[{"operatingType":"operator","operator":"distinct_count"}]
2. 退货金额 (store_return_amt) — factColumn="sr_return_amt", expression=[{"operatingType":"operator","operator":"sum"}]
3. 退货数量 (store_return_qty) — factColumn="sr_return_qty", expression=[{"operatingType":"operator","operator":"sum"}]

**catalog_returns 渠道：**
1. 退货笔数 (catalog_return_cnt) — factColumn="cr_ticket_number", expression=[{"operatingType":"operator","operator":"distinct_count"}]
2. 退货金额 (catalog_return_amt) — factColumn="cr_return_amt", expression=[{"operatingType":"operator","operator":"sum"}]
3. 退货数量 (catalog_return_qty) — factColumn="cr_return_qty", expression=[{"operatingType":"operator","operator":"sum"}]

**web_returns 渠道：**
1. 退货笔数 (web_return_cnt) — factColumn="wr_ticket_number", expression=[{"operatingType":"operator","operator":"distinct_count"}]
2. 退货金额 (web_return_amt) — factColumn="wr_return_amt", expression=[{"operatingType":"operator","operator":"sum"}]
3. 退货数量 (web_return_qty) — factColumn="wr_return_qty", expression=[{"operatingType":"operator","operator":"sum"}]

### 第三类：跨渠道衍生指标（applyTypeCode=1）
衍生指标没有 factColumn，expression 中 referenceMetricCode 引用原子指标 code。

1. **总销售额** (total_sales_amount) — expression=[{"referenceMetricCode":"store_sales_amount","operatingType":"metric"},{"referenceMetricCode":"catalog_sales_amount","operatingType":"metric"},{"referenceMetricCode":"web_sales_amount","operatingType":"metric"},{"operatingType":"operator","operator":"sum"}]
2. **总退货金额** (total_return_amt) — expression=[{"referenceMetricCode":"store_return_amt","operatingType":"metric"},{"referenceMetricCode":"catalog_return_amt","operatingType":"metric"},{"referenceMetricCode":"web_return_amt","operatingType":"metric"},{"operatingType":"operator","operator":"sum"}]
3. **整体退货率** (overall_return_rate) — expression=[{"referenceMetricCode":"total_return_amt","operatingType":"metric"},{"referenceMetricCode":"total_sales_amount","operatingType":"metric"},{"operatingType":"operator","operator":"division"}]

## 输出格式要求

每个 Measure 实例必须包含：
- ind:code（英文标识符，如 "store_sales_amount"）
- ind:enName（英文名称，如 "Store Sales Amount"）
- ind:cnName（中文名称，如 "门店销售额"）
- ind:applyTypeCode（0=原子，1=衍生）
- ind:expression（JSON 数组）
- ind:available 1

每个 MeasureApp 实例必须包含：
- ind:code（如 "ma_store_sales_amount_store_sales"）
- ind:enName（如 "Store Sales Amount on store_sales"）
- ind:cnName（如 "门店销售额 / 门店销售表"）
- ind:factTable — 填 inst:tbl_tpcds__<table_name>（如 inst:tbl_tpcds__store_sales）
- ind:factColumn — 填真实列名（原子指标必填，衍生指标不填）
- ind:applyTypeCode — 必须与对应的 Measure 一致
- ind:available 1

## 重要约束
1. **只生成 Measure 和 MeasureApp 实例，不要生成任何 Dimension / DimensionApp / DwTable / Category 实例**
2. **expression 必须是合法 JSON 数组字符串**
3. **factTable URI 格式：inst:tbl_tpcds__<物理表名>**
4. **MeasureApp 的 factColumn 必须来自对应物理表的真实列名**
5. **sourceTypeCode 全部填 0**
"""


# ── Phase 2: Dimension + DimensionApp + DwTable + Category 提示词 ─────────── #
PHASE2_HINT = """\
你是一位资深零售数据分析建模专家。基于 TPC-DS 数据库，为一个零售企业生成**维度、维度应用、物理表、指标分类**实例。

## 业务背景
TPC-DS 零售数据库，6张核心业务表：
- store_sales / catalog_sales / web_sales — 销售事实表
- store_returns / catalog_returns / web_returns — 退货事实表
- item — 商品维度表（i_item_sk PK, i_category, i_class, i_brand...）
- store — 门店维度表（s_store_sk PK, s_store_name, s_state...）
- customer — 客户维度表（c_customer_sk PK, c_customer_id...）
- customer_address — 客户地址表（ca_address_sk PK, ca_state, ca_city...）
- customer_demographics — 客户人口统计表（cd_demo_sk PK, cd_gender, cd_credit_rating...）
- promotion — 促销表（p_promo_sk PK, p_promo_name...）
- date_dim — 日期维表（d_date_sk PK, d_year, d_quarter_name, d_month_name...）
- time_dim — 时间维表（t_time_sk PK, t_hour, t_shift...）
- reason — 退货原因表（r_reason_sk PK, r_reason_desc...）

## DwTable 任务（必须先完成）

为以下每张表生成 ind:DwTable 实例：

inst:tbl_tpcds__store_sales    — schemaName="tpcds", tableName="store_sales", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__catalog_sales  — schemaName="tpcds", tableName="catalog_sales", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__web_sales      — schemaName="tpcds", tableName="web_sales", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__store_returns  — schemaName="tpcds", tableName="store_returns", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__catalog_returns — schemaName="tpcds", tableName="catalog_returns", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__web_returns    — schemaName="tpcds", tableName="web_returns", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__item           — schemaName="tpcds", tableName="item", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__store          — schemaName="tpcds", tableName="store", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__customer       — schemaName="tpcds", tableName="customer", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__customer_address — schemaName="tpcds", tableName="customer_address", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__customer_demographics — schemaName="tpcds", tableName="customer_demographics", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__promotion     — schemaName="tpcds", tableName="promotion", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__date_dim       — schemaName="tpcds", tableName="date_dim", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__time_dim       — schemaName="tpcds", tableName="time_dim", ind:hasConnection inst:conn_mysql_localhost_tpcds
inst:tbl_tpcds__reason         — schemaName="tpcds", tableName="reason", ind:hasConnection inst:conn_mysql_localhost_tpcds

DataConnection（只需一个）：
inst:conn_mysql_localhost_tpcds — ind:dbType "mysql", ind:host "localhost", ind:port 3306, ind:dbUser "root", ind:dbPassword "root", ind:dbName "tpcds"

## Category 任务

生成一个顶层分类：
- inst:cat_tpcds_retail — ind:cnName "TPC-DS 零售指标", ind:enName "TPC-DS Retail Metrics", ind:categoryParent ""

## Dimension 任务

每个维度必须包含：ind:code, ind:enName, ind:cnName, ind:dimTypeCode（2=标准维有维表）, ind:dimTable（指向维表 DwTable）, ind:available 1

### 日期维度（5个粒度，共用一个 Hierarchy）

| 粒度 | code | cnName | enName | levelCode | viewTypeCode | levelSequence |
|------|------|--------|--------|-----------|--------------|---------------|
| 年 | dim_date_year | 日期-年 | Date Year | year | 5 | 1 |
| 季 | dim_date_quarter | 日期-季 | Date Quarter | quarter | 4 | 2 |
| 月 | dim_date_month | 日期-月 | Date Month | month | 3 | 3 |
| 周 | dim_date_week | 日期-周 | Date Week | week | 2 | 4 |
| 日 | dim_date_day | 日期-日 | Date Day | day | 1 | 5 |

所有日期粒度共享 hierarchyCode="HIER_sold_date"，物理列=事实表中的日期外键（store_sales 用 ss_sold_date_sk，catalog_sales 用 cs_sold_date_sk，等等）

### 商品维度
- inst:dim_item_category — code="dim_item_category", cnName="商品分类", enName="Item Category", dimTypeCode=2, dimTable=inst:tbl_tpcds__item, hierarchyCode="HIER_item", levelCode="category", levelSequence=1, available 1
- inst:dim_item_class — code="dim_item_class", cnName="商品品类", enName="Item Class", dimTypeCode=2, dimTable=inst:tbl_tpcds__item, hierarchyCode="HIER_item", levelCode="class", levelSequence=2, available 1

### 门店维度
- inst:dim_store — code="dim_store", cnName="门店", enName="Store", dimTypeCode=2, dimTable=inst:tbl_tpcds__store, hierarchyCode="HIER_store", levelCode="store", levelSequence=1, available 1
- inst:dim_store_state — code="dim_store_state", cnName="门店地区", enName="Store State", dimTypeCode=2, dimTable=inst:tbl_tpcds__store, hierarchyCode="HIER_store", levelCode="state", levelSequence=2, available 1

### 促销维度
- inst:dim_promotion — code="dim_promotion", cnName="促销活动", enName="Promotion", dimTypeCode=2, dimTable=inst:tbl_tpcds__promotion, hierarchyCode="HIER_promo", levelCode="promotion", levelSequence=1, available 1

### 客户维度
- inst:dim_customer — code="dim_customer", cnName="客户", enName="Customer", dimTypeCode=2, dimTable=inst:tbl_tpcds__customer, hierarchyCode="HIER_customer", levelCode="customer", levelSequence=1, available 1
- inst:dim_customer_demographics — code="dim_customer_demographics", cnName="客户人口统计", enName="Customer Demographics", dimTypeCode=2, dimTable=inst:tbl_tpcds__customer_demographics, hierarchyCode="HIER_cdemo", levelCode="demographics", levelSequence=1, available 1

### 退货原因维度
- inst:dim_reason — code="dim_reason", cnName="退货原因", enName="Return Reason", dimTypeCode=2, dimTable=inst:tbl_tpcds__reason, hierarchyCode="HIER_reason", levelCode="reason", levelSequence=1, available 1

## DimensionApp 任务

每个事实表 × 维度组合生成一个 DimensionApp。
DimensionApp 必须包含：ind:code, ind:enName, ind:cnName, ind:dimFactColumn（事实表外键列）, ind:masterPrimaryKey（维表主键）, ind:dimColumn（展示列）, ind:dimTable（指向维度DwTable）, ind:dimFactTable（指向事实表DwTable）, ind:hierarchyCode, ind:levelCode, ind:isMasterApp true, ind:isRootJoin false, ind:available 1
如果同一个维表外键派生出多个维度属性（例如 promotion 的名称、渠道、目的、折扣激活标识都通过 *_promo_sk 关联 p_promo_sk），
这些 DimensionApp 的 dimFactColumn 和 masterPrimaryKey 相同，但 dimColumn 必须不同；同时补充 ind:dimColumnExpr "{d}.<属性列>"，
让查询按该属性列分组，不能都按共享的维表主键分组。

### store_sales 的 DimensionApp（按日期、商品、门店、促销）
- 日期年粒度：dimFactColumn="ss_sold_date_sk", masterPrimaryKey="d_date_sk", dimColumn="d_year", dimTable=inst:tbl_tpcds__date_dim, dimFactTable=inst:tbl_tpcds__store_sales, hierarchyCode="HIER_sold_date", levelCode="year"
- 日期月粒度：dimFactColumn="ss_sold_date_sk", masterPrimaryKey="d_date_sk", dimColumn="d_month_name", dimTable=inst:tbl_tpcds__date_dim, dimFactTable=inst:tbl_tpcds__store_sales, hierarchyCode="HIER_sold_date", levelCode="month"
- 商品分类：dimFactColumn="ss_item_sk", masterPrimaryKey="i_item_sk", dimColumn="i_category", dimTable=inst:tbl_tpcds__item, dimFactTable=inst:tbl_tpcds__store_sales, hierarchyCode="HIER_item", levelCode="category"
- 门店：dimFactColumn="ss_store_sk", masterPrimaryKey="s_store_sk", dimColumn="s_store_name", dimTable=inst:tbl_tpcds__store, dimFactTable=inst:tbl_tpcds__store_sales, hierarchyCode="HIER_store", levelCode="store"
- 促销：dimFactColumn="ss_promo_sk", masterPrimaryKey="p_promo_sk", dimColumn="p_promo_name", dimTable=inst:tbl_tpcds__promotion, dimFactTable=inst:tbl_tpcds__store_sales, hierarchyCode="HIER_promo", levelCode="promotion"

### catalog_sales 的 DimensionApp
- 日期年粒度：dimFactColumn="cs_sold_date_sk"（或 cs_sold_date_sk，具体看列名）, masterPrimaryKey="d_date_sk", dimColumn="d_year", dimTable=inst:tbl_tpcds__date_dim, dimFactTable=inst:tbl_tpcds__catalog_sales, hierarchyCode="HIER_sold_date", levelCode="year"
- 商品分类：dimFactColumn="cs_item_sk", masterPrimaryKey="i_item_sk", dimColumn="i_category", dimTable=inst:tbl_tpcds__item, dimFactTable=inst:tbl_tpcds__catalog_sales, hierarchyCode="HIER_item", levelCode="category"

### web_sales 的 DimensionApp
- 日期年粒度：dimFactColumn="ws_sold_date_sk", masterPrimaryKey="d_date_sk", dimColumn="d_year", dimTable=inst:tbl_tpcds__date_dim, dimFactTable=inst:tbl_tpcds__web_sales, hierarchyCode="HIER_sold_date", levelCode="year"
- 商品分类：dimFactColumn="ws_item_sk", masterPrimaryKey="i_item_sk", dimColumn="i_category", dimTable=inst:tbl_tpcds__item, dimFactTable=inst:tbl_tpcds__web_sales, hierarchyCode="HIER_item", levelCode="category"

### 退货表的 DimensionApp（用 sr/cs/wr_return_date_sk 作为日期外键）
- store_returns 日期：dimFactColumn="sr_return_date_sk", masterPrimaryKey="d_date_sk", dimColumn="d_year", dimTable=inst:tbl_tpcds__date_dim, dimFactTable=inst:tbl_tpcds__store_returns, hierarchyCode="HIER_return_date", levelCode="year"
- 退货原因：dimFactColumn="sr_reason_sk", masterPrimaryKey="r_reason_sk", dimColumn="r_reason_desc", dimTable=inst:tbl_tpcds__reason, dimFactTable=inst:tbl_tpcds__store_returns, hierarchyCode="HIER_reason", levelCode="reason"

## 重要约束
1. **只生成 Dimension / DimensionApp / DwTable / Category / DataConnection 实例，不要生成任何 Measure / MeasureApp 实例**
2. **所有 ind:dimFactColumn / ind:dimColumn / ind:masterPrimaryKey 必须来自元数据中的真实列名；ind:dimColumnExpr 使用 "{d}.真实属性列名" 或合法 SQL 表达式**
3. **sourceTypeCode 全部填 0**
"""


def log(msg):
    print(f"  [LLM] {msg}")


def run_phase(phase_num, hint, domain_hint_for_summary):
    print(f"\n▶ Phase {phase_num}: 加载源图谱...")
    g = Graph()
    g.parse(str(TTL_PATH), format="turtle")
    print(f"  加载了 {len(g)} triples")

    print(f"▶ Phase {phase_num}: 提取元数据摘要...")
    from kg_builder.business_kg.extractor import MetadataSummaryExtractor
    extractor = MetadataSummaryExtractor(g, domain_hint=domain_hint_for_summary)
    summary = extractor.extract()
    print(f"  摘要长度: {len(summary):,} 字符")

    print(f"▶ Phase {phase_num}: 调用 LLM...")
    builder = BusinessKGBuilder.from_env(base_dir=BASE_DIR, log_cb=log)
    ttl_content, success = builder.build(summary=summary, domain_hint=hint)
    if not success:
        print(f"❌ Phase {phase_num} LLM 生成失败")
        return None
    print(f"  LLM 返回 {len(ttl_content):,} 字符")
    return ttl_content


def merge_and_save(parts, out_path):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    full = _ONTOLOGY_PREAMBLE + "\n" + "\n".join(parts)
    out_path.write_text(full, encoding="utf-8")

    # 验证
    g = Graph()
    try:
        g.parse(str(out_path), format="turtle")
        print(f"\n✅ 合并保存成功: {out_path} ({len(g)} triples)")
    except Exception as e:
        print(f"\n❌ TTL 解析失败: {e}")
        return False

    IND = Namespace("http://indicator.xiaojw.com/ontology#")
    for label, cls in [("Measure", IND.Measure), ("MeasureApp", IND.MeasureApp),
                        ("Dimension", IND.Dimension), ("DimensionApp", IND.DimensionApp),
                        ("DwTable", IND.DwTable), ("Category", IND.Category)]:
        cnt = len(list(g.subjects(RDF.type, cls)))
        if cnt:
            print(f"  {label}: {cnt}")
    return True


def main():
    results = []

    # Phase 1: Measure + MeasureApp
    out1 = run_phase(1, PHASE1_HINT, "retail sales store catalog web")
    if not out1:
        sys.exit(1)
    results.append(out1)

    # Phase 2: Dimension + DimensionApp + DwTable + Category
    out2 = run_phase(2, PHASE2_HINT, "retail store sales item date")
    if not out2:
        sys.exit(1)
    results.append(out2)

    # 合并保存
    print("\n▶ 合并保存最终图谱...")
    ok = merge_and_save(results, OUT_FINAL)
    if not ok:
        sys.exit(1)

    # 也保存分阶段文件
    OUT_PATH_PHASE1.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH_PHASE1.write_text(_ONTOLOGY_PREAMBLE + "\n" + out1, encoding="utf-8")
    OUT_PATH_PHASE2.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH_PHASE2.write_text(_ONTOLOGY_PREAMBLE + "\n" + out2, encoding="utf-8")
    print(f"  Phase1 保存至: {OUT_PATH_PHASE1}")
    print(f"  Phase2 保存至: {OUT_PATH_PHASE2}")

    # ── 验证指标查询有效性 ──
    print("\n▶ 验证指标查询有效性...")
    validate_path = BASE_DIR / "validate_indicators.py"
    if validate_path.exists():
        import subprocess
        subprocess.run([sys.executable, str(validate_path), str(OUT_FINAL)], check=False)
    else:
        print("  ⚠ validate_indicators.py 未找到，跳过验证")


if __name__ == "__main__":
    import sys
    main()
