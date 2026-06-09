#!/usr/bin/env python3
"""
修复 business KG 中缺失的必填属性：
- ind:Measure 缺少 measTypeCode / unit / caliber / definition
- ind:MeasureApp 缺少 applyTypeCode / hasColumnDT / appliesToTable / available
"""
from pathlib import Path
from rdflib import Graph, RDF, Namespace, Literal
from rdflib.plugins.serializers.turtle import TurtleSerializer

BASE_DIR = Path(__file__).parent
PHASE1 = BASE_DIR / "output/business_kg/phase1_measures.ttl"
PHASE2 = BASE_DIR / "output/business_kg/phase2_dimensions.ttl"
OUT    = BASE_DIR / "output/business_kg/indicator-data.ttl"

IND = Namespace("http://indicator.lixiang.com/ontology#")
INST = Namespace("http://indicator.lixiang.com/instance/")

# ── 原子/衍生指标分类 ──────────────────────────────────────────────── #
DERIVED_CODES = {"total_sales_amount", "total_return_amt", "overall_return_rate"}

# ── catalog/web 列名映射（store_sales 的列名到 catalog/web 的映射）────────── #
# 例如 store_sales 的 ss_ext_sales_price → catalog_sales 的 cs_sales_price
COLUMN_ALIASES = {
    # store_sales 内部同义
    "ss_sales_price": "ss_sales_price",
    # catalog_sales 内部同义
    "cs_sales_price": "cs_sales_price",
    "cs_wholesale_cost": "cs_wholesale_cost",
    "cs_net_profit": "cs_net_profit",
    "cs_quantity": "cs_quantity",
    "cs_discount_amt": "cs_discount_amt",
    "cs_tax": "cs_tax",
    "cs_coupon_amt": "cs_coupon_amt",
}

def get_meas_type(code: str) -> int:
    """0=原子, 1=衍生"""
    return 1 if code in DERIVED_CODES else 0

def get_unit(code: str) -> str:
    if "amount" in code or "profit" in code or "cost" in code or "discount" in code or "tax" in code or "paid" in code or "return_amt" in code:
        return "元"
    if "quantity" in code or "qty" in code or "cnt" in code:
        return "个"
    if "rate" in code or "pct" in code or "ratio" in code:
        return "%"
    return "个"

def get_caliber(code: str) -> str:
    if "amount" in code or "sales" in code:
        return f"对指定时间范围内所有交易的实收金额求和"
    if "cost" in code:
        return f"对指定时间范围内所有交易的批发成本求和"
    if "profit" in code:
        return f"对指定时间范围内所有交易的净收入求和"
    if "quantity" in code or "qty" in code:
        return f"对指定时间范围内所有交易的数量求和"
    if "discount" in code:
        return f"对指定时间范围内所有交易的折扣金额求和"
    if "tax" in code:
        return f"对指定时间范围内所有交易的税额求和"
    if "coupon" in code:
        return f"对指定时间范围内所有使用的优惠券金额求和"
    if "paid" in code:
        return f"对指定时间范围内所有交易的净支付金额求和"
    if "return_cnt" in code:
        return f"对指定时间范围内所有退货记录的笔数统计"
    if "return_amt" in code:
        return f"对指定时间范围内所有退货记录的金额求和"
    if "return_qty" in code:
        return f"对指定时间范围内所有退货记录的数量求和"
    if "total_sales" in code:
        return f"门店、目录、网站三大渠道销售额之和"
    if "total_return" in code:
        return f"门店、目录、网站三大渠道退货金额之和"
    if "overall_return_rate" in code:
        return f"总退货金额与总销售额之比"
    return f"对指定时间范围内业务指标进行聚合计算"

def get_definition(code: str) -> str:
    if "sales_amount" in code:
        return "统计指定时间范围内商品销售的实收总金额"
    if "sales_cost" in code:
        return "统计指定时间范围内商品销售的批发成本总额"
    if "sales_profit" in code:
        return "统计指定时间范围内商品销售的净收入总额"
    if "sales_quantity" in code:
        return "统计指定时间范围内商品销售的总数量"
    if "discount" in code:
        return "统计指定时间范围内商品折扣的总金额"
    if "tax" in code:
        return "统计指定时间范围内商品销售的税额总额"
    if "coupon" in code:
        return "统计指定时间范围内使用的优惠券总金额"
    if "net_paid" in code:
        return "统计指定时间范围内消费者实际支付的净金额"
    if "return_cnt" in code:
        return "统计指定时间范围内退货订单的总笔数"
    if "return_amt" in code:
        return "统计指定时间范围内退货商品的总金额"
    if "return_qty" in code:
        return "统计指定时间范围内退货商品的总数量"
    if "total_sales_amount" in code:
        return "跨渠道汇总门店、目录、网站三大渠道的销售总额"
    if "total_return_amt" in code:
        return "跨渠道汇总门店、目录、网站三大渠道的退货总额"
    if "overall_return_rate" in code:
        return "整体退货率，反映商品质量和服务水平"
    return "业务指标定义"


def fix_measure(g: Graph, meas_uri: str, code: str):
    """补充 Measure 缺失的必填属性"""
    meas_type = get_meas_type(code)
    g.set((meas_uri, IND.measTypeCode, Literal(meas_type)))
    g.set((meas_uri, IND.unit, Literal(get_unit(code))))
    g.set((meas_uri, IND.caliber, Literal(get_caliber(code))))
    g.set((meas_uri, IND.definition, Literal(get_definition(code))))
    # description 补一个
    en_name = g.value(meas_uri, IND.enName)
    if en_name:
        g.set((meas_uri, IND.description, Literal(str(en_name))))


def fix_measureapp(g: Graph, ma_uri: str, code: str, meas_code: str):
    """补充 MeasureApp 缺失的必填属性"""
    meas_type = get_meas_type(meas_code)

    # applyTypeCode
    g.set((ma_uri, IND.applyTypeCode, Literal(meas_type)))

    # hasColumnDT - 统一填 false（TPC-DS 没有 dt 分区）
    g.set((ma_uri, IND.hasColumnDT, Literal("false")))

    # available
    g.set((ma_uri, IND.available, Literal(1)))

    # appliesToTable - 从 code 推断，如 ma_store_sales_amount_store_sales → inst:tbl_tpcds__store_sales
    parts = code.split("_")
    tbl_name = None
    if "store" in meas_code and "catalog" not in meas_code and "web" not in meas_code:
        tbl_name = "store_sales"
    elif "catalog" in meas_code:
        tbl_name = "catalog_sales"
    elif "web" in meas_code:
        tbl_name = "web_sales"
    elif "return" in meas_code:
        if "catalog" in meas_code:
            tbl_name = "catalog_returns"
        elif "web" in meas_code:
            tbl_name = "web_returns"
        else:
            tbl_name = "store_returns"

    if tbl_name:
        applies = INST[f"tbl_tpcds__{tbl_name}"]
        g.set((ma_uri, IND.appliesToTable, applies))


def main():
    g = Graph()
    g.parse(str(PHASE1), format="turtle")
    g.parse(str(PHASE2), format="turtle")
    print(f"加载后共 {len(g)} triples")

    # ── 修复 Measure ────────────────────────────────────────────────── #
    meas_fixed = 0
    for meas_uri in g.subjects(RDF.type, IND.Measure):
        code = str(g.value(meas_uri, IND.code) or "")
        if not code:
            continue

        # 检查缺失属性
        missing = []
        if not g.value(meas_uri, IND.measTypeCode):
            missing.append("measTypeCode")
        if not g.value(meas_uri, IND.unit):
            missing.append("unit")
        if not g.value(meas_uri, IND.caliber):
            missing.append("caliber")
        if not g.value(meas_uri, IND.definition):
            missing.append("definition")

        if missing:
            print(f"  Measure {code}: 缺少 {missing}，正在修复…")
            fix_measure(g, meas_uri, code)
            meas_fixed += 1

    print(f"修复了 {meas_fixed} 个 Measure")

    # ── 修复 MeasureApp ──────────────────────────────────────────────── #
    ma_fixed = 0
    for ma_uri in g.subjects(RDF.type, IND.MeasureApp):
        code = str(g.value(ma_uri, IND.code) or "")
        if not code:
            continue

        # 从 code 反推 meas_code（去掉 ma_ 前缀和 _store_sales 后缀）
        meas_code = code.replace("ma_", "")
        # code 格式如 ma_store_sales_amount_store_sales → store_sales_amount
        # 去掉最后的 _store_sales 等表名后缀
        for suffix in ["_store_sales", "_catalog_sales", "_web_sales",
                       "_store_returns", "_catalog_returns", "_web_returns"]:
            if meas_code.endswith(suffix):
                meas_code = meas_code[:-len(suffix)]
                break

        missing = []
        if not g.value(ma_uri, IND.applyTypeCode):
            missing.append("applyTypeCode")
        if not g.value(ma_uri, IND.hasColumnDT):
            missing.append("hasColumnDT")
        if not g.value(ma_uri, IND.available):
            missing.append("available")
        if not g.value(ma_uri, IND.appliesToTable):
            missing.append("appliesToTable")

        if missing:
            print(f"  MeasureApp {code}: 缺少 {missing}，正在修复…")
            fix_measureapp(g, ma_uri, code, meas_code)
            ma_fixed += 1

    print(f"修复了 {ma_fixed} 个 MeasureApp")

    # ── 验证 ────────────────────────────────────────────────────────── #
    print("\n验证结果：")
    for label, cls in [("Measure", IND.Measure), ("MeasureApp", IND.MeasureApp),
                        ("Dimension", IND.Dimension), ("DimensionApp", IND.DimensionApp),
                        ("DwTable", IND.DwTable), ("Category", IND.Category)]:
        cnt = len(list(g.subjects(RDF.type, cls)))
        if cnt:
            # 检查缺失属性
            issues = 0
            for inst_uri in g.subjects(RDF.type, cls):
                if cls == IND.Measure:
                    for prop in [IND.measTypeCode, IND.unit, IND.caliber, IND.definition]:
                        if not g.value(inst_uri, prop):
                            issues += 1
                            break
                elif cls == IND.MeasureApp:
                    for prop in [IND.applyTypeCode, IND.hasColumnDT, IND.available, IND.appliesToTable]:
                        if not g.value(inst_uri, prop):
                            issues += 1
                            break
            status = f"⚠️ {issues}个缺失" if issues else "✅ 完整"
            print(f"  {label}: {cnt} {status}")

    # ── 保存 ────────────────────────────────────────────────────────── #
    g.serialize(str(OUT), format="turtle")
    print(f"\n已保存: {OUT}")


if __name__ == "__main__":
    main()
