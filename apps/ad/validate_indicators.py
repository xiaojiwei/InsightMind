#!/usr/bin/env python3
"""
验证指标查询有效性
测试每个指标与日期维度(DIM_dim_date_day)的组合查询，失败时交由 LLM 分析并修复 TTL。
"""
from __future__ import annotations

import json
import re
import sys
import time
import urllib.request

from kg_builder.utils.http_client import urlopen as _urlopen
import urllib.error
from pathlib import Path
from typing import Optional

from rdflib import Graph, Namespace
from kg_builder.utils.llm_config import llm_config_from_env

BASE_DIR = Path(__file__).parent
IND = Namespace("http://indicator.insightmind.com/ontology#")
DA_API = "http://127.0.0.1:8091/bi/v1/datasource/query"


_LLM_CONFIG = llm_config_from_env(BASE_DIR)
LLM_API_KEY = _LLM_CONFIG["api_key"]
LLM_BASE_URL = _LLM_CONFIG["base_url"]
LLM_MODEL = _LLM_CONFIG["model"]


# ── TTL Loading ─────────────────────────────────────────────────────────────── #

def load_measures(ttl_path: str) -> list[dict]:
    """加载 TTL 中所有 MEAS_ 开头的指标及其完整 MeasureApp 配置。"""
    g = Graph()
    g.parse(ttl_path, format="turtle")

    seen_codes: set = set()
    measures = []
    for meas_inst in g.subjects(None, None):
        code = g.value(meas_inst, IND.code)
        if not code or not str(code).startswith("MEAS_"):
            continue
        code = str(code)
        if code in seen_codes:
            continue
        seen_codes.add(code)

        # 跳过衍生/复合指标（measTypeCode=1），它们的 SQL 生成逻辑不同
        mtype = g.value(meas_inst, IND.measTypeCode)
        if mtype and int(float(str(mtype))) == 1:
            continue

        # 收集该指标的完整三元组
        triples: list[str] = []
        for p, o in g.predicate_objects(meas_inst):
            triples.append(f"    ind:{_short(p)} {_fmt_obj(g, p, o)} ;")
        triples.append("    .")

        # 收集关联的 MeasureApp + NaturalDimMapping
        apps = []
        for mapp in g.objects(meas_inst, IND.hasMeasureApp):
            app_triples: list[str] = []
            for p, o in g.predicate_objects(mapp):
                app_triples.append(f"    ind:{_short(p)} {_fmt_obj(g, p, o)} ;")
            app_triples.append("    .")
            apps.append("\n".join(app_triples))

            # NaturalDimMapping
            for ndm in g.objects(mapp, IND.hasNaturalDimMapping):
                ndm_triples: list[str] = []
                for p, o in g.predicate_objects(ndm):
                    ndm_triples.append(f"    ind:{_short(p)} {_fmt_obj(g, p, o)} ;")
                ndm_triples.append("    .")
                apps.append("\n".join(ndm_triples))

        measures.append({
            "code": code,
            "cn_name": str(g.value(meas_inst, IND.cnName) or ""),
            "ttl_snippet": "\n".join(triples) + "\n\n" + "\n".join(apps),
        })

    return measures


def _short(uri) -> str:
    s = str(uri)
    return s.split("#")[-1] if "#" in s else s.rsplit("/", 1)[-1]


def _fmt_obj(g, p, o) -> str:
    p_str = str(p)
    if "expression" in p_str or "caliber" in p_str or "definition" in p_str or "description" in p_str:
        return f'"{str(o)}"'
    if "code" in p_str or "cnName" in p_str or "enName" in p_str or "unit" in p_str:
        return f'"{str(o)}"'
    if str(o).startswith("http"):
        return f"inst:{_short(o)}"
    return f'"{str(o)}"'


# ── DA API ─────────────────────────────────────────────────────────────────── #

def query_da(meas_code: str, dim_code: str = "DIM_dim_date_day") -> dict:
    """查询单个指标 × 日期维度，返回 {'ok': bool, 'rows': int, 'error': str|None}."""
    payload = json.dumps({
        "configureList": [
            {"code": meas_code, "order": {"sortType": 0}, "ratioList": [], "alias": "val"},
            {"code": dim_code, "order": {"sortType": 1}, "ratioList": [], "alias": "d"}
        ],
        "filterList": [],
        "pageSize": 1,
        "pageNum": 1,
    }).encode("utf-8")

    try:
        req = urllib.request.Request(DA_API, data=payload,
                                     headers={"Content-Type": "application/json"}, method="POST")
        with _urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return {"ok": False, "rows": 0, "error": str(e)}

    if data.get("code") != 200:
        return {"ok": False, "rows": 0, "error": data.get("errorMessage", data.get("message", "unknown"))}
    if not data.get("data") or not data["data"].get("cellList"):
        return {"ok": False, "rows": 0, "error": "cellList 为空"}

    rows = data["data"]["cellList"]
    return {"ok": True, "rows": len(rows), "error": None}


# ── LLM call ───────────────────────────────────────────────────────────────── #

def llm_call(system: str, user: str, max_tokens: int = 2048) -> str:
    """OpenAI-compatible LLM 调用。"""
    payload = json.dumps({
        "model": LLM_MODEL,
        "max_tokens": max_tokens,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {LLM_API_KEY}",
    }
    req = urllib.request.Request(
        f"{LLM_BASE_URL}/chat/completions", data=payload, headers=headers, method="POST"
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return data["choices"][0]["message"]["content"]


# ── TTL patching ───────────────────────────────────────────────────────────── #

def apply_fix(ttl_path: str, measure_code: str, fix_ttl: str) -> bool:
    """将 LLM 返回的修正 TTL 片段写入文件。"""
    with open(ttl_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 找到该指标的 Measure 定义块
    marker = f'ind:code "{measure_code}" ;'
    idx = content.find(marker)
    if idx == -1:
        print(f"  ⚠ 找不到 {measure_code} 在 TTL 中的位置")
        return False

    # 找到该指标定义的开始行
    block_start = content.rfind("\ninst:", 0, idx)
    if block_start == -1:
        block_start = content.rfind("\n", 0, idx) - 100
    # 找到该指标块的结束
    # 下一个 inst: 的定义或文件结束
    block_end = content.find("\ninst:", idx)
    if block_end == -1:
        block_end = len(content)

    # 提取修复指令中以 `inst:meas_` 或 `inst:ma_` 开头的完整块
    fix_blocks = _extract_fix_blocks(fix_ttl)
    if not fix_blocks:
        print(f"  ⚠ LLM 未返回有效 TTL 修复块")
        return False

    # 替换整个指标块
    fixed_block = content[block_start:block_end] + "\n" + "\n".join(fix_blocks) + "\n"
    content = content[:block_start] + fixed_block + content[block_end:]
    # 去重连续的 fix_blocks（可能已存在）
    for fb in fix_blocks:
        content = content.replace(fb + "\n" + fb, fb)

    with open(ttl_path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"   ✓ TTL 已更新")
    return True


def _strip_thinking(text: str) -> str:
    """移除 LLM 的 thinking/chain-of-thought 输出。"""
    # MiniMax/DeepSeek: <think>...</think> or 思考...
    text = re.sub(r'<think>.*?</think>', '', text, flags=re.DOTALL)
    text = re.sub(r'思考[：:][\s\S]*?(?=\n\n|\n```|inst:)', '', text)
    return text.strip()


def _extract_fix_blocks(text: str) -> list[str]:
    """从 LLM 响应中提取 inst: 开头的 TTL 块。"""
    text = _strip_thinking(text)
    blocks: list[str] = []

    # 优先: ```turtle 或 ``` 代码块
    for m in re.finditer(r'```(?:turtle|ttl)?\s*\n(.*?)```', text, re.DOTALL):
        snippet = m.group(1).strip()
        # 按空行分割为独立实例块
        for part in re.split(r'\n\s*\n', snippet):
            part = part.strip()
            if part.startswith("inst:") and part.endswith("."):
                blocks.append(part)
        if blocks:
            return blocks

    # 次选: 直接在正文中的 inst: 块
    pattern = re.compile(r'(?:^|\n)(inst:\w+\s+a\s+ind:\w+\s*;.*?)(?=\n\s*\.)', re.DOTALL)
    for m in pattern.finditer(text):
        block = m.group(1).strip()
        if not block.endswith("."):
            block += "\n    ."
        blocks.append(block)

    return blocks


# ── Main ───────────────────────────────────────────────────────────────────── #

SYSTEM_PROMPT = """你是一个 TPC-DS 零售知识图谱修复专家。你必须严格按以下格式输出修复内容：
1. 不要输出思考过程或解释
2. 必须用 \`\`\`turtle 代码块包裹修正后的 TTL 实例
3. 只输出需要修改的 inst: 实例，不要输出已有正确配置

示例输出：
\`\`\`turtle
inst:ma_store_return_amt_store_returns a ind:MeasureApp ;
    ind:appliesToTable inst:tbl_tpcds__store_returns ;
    .
\`\`\`

当某个指标查询失败时，分析其 Measure / MeasureApp / NaturalDimMapping 配置，
找出根因并输出修正后的 TTL 片段。

常见 TPC-DS 表名和列名：
- store_sales: ss_sold_date_sk, ss_ext_sales_price, ss_net_profit, ...
- store_returns: sr_returned_date_sk, sr_return_amt, sr_return_quantity
- catalog_sales: cs_sold_date_sk, cs_ext_sales_price, ...
- catalog_returns: cr_returned_date_sk, cr_return_amount, ...
- web_sales: ws_sold_date_sk, ws_ext_sales_price, ...
- web_returns: wr_returned_date_sk, wr_return_amt, ...
- date_dim: d_date_sk, d_date

hierarchyCode: 销售日期用 "HIER_date" (FK: *_sold_date_sk),
退货日期用 "HIER_return_date" (FK: *_returned_date_sk).

appliesToTable: 销售指标→*_sales, 退货指标→*_returns."""

def main():
    ttl_path = sys.argv[1] if len(sys.argv) > 1 else str(BASE_DIR / "output/business_kg/indicator-data.ttl")
    max_retries = int(sys.argv[2]) if len(sys.argv) > 2 else 3

    print(f"📋 加载指标: {ttl_path}")
    measures = load_measures(ttl_path)
    print(f"   共 {len(measures)} 个指标\n")

    # 测试日期维度（日、周、月、季、年）
    date_dims = [
        "DIM_dim_date_day",
        "DIM_dim_date_week",
        "DIM_dim_date_month",
        "DIM_dim_date_quarter",
        "DIM_dim_date_year",
    ]
    # 优先用日维测试（最快定位问题），通过后再测其他
    primary_dim = "DIM_dim_date_day"

    results = {"passed": [], "fixed": [], "failed": []}

    for i, m in enumerate(measures):
        code = m["code"]
        cn = m["cn_name"]
        print(f"[{i+1}/{len(measures)}] {code} ({cn})")

        ok = _test_and_fix(ttl_path, m, primary_dim, max_retries)
        if ok:
            # 通过日维后，快速验证其他时间维
            all_dims_ok = True
            for dim in date_dims[1:]:
                result = query_da(code, dim)
                if not result["ok"]:
                    print(f"   ⚠ {dim} 失败: {result['error'][:100]}")
                    all_dims_ok = False
            if all_dims_ok:
                print(f"   ✅ 全部时间维通过")
                results["passed"].append(code)
            else:
                results["failed"].append(code)
        else:
            results["failed"].append(code)

    # ── 汇总 ──
    print(f"\n{'='*60}")
    print(f"验证完成: {len(results['passed'])} 通过, {len(results['fixed'])} 修复, {len(results['failed'])} 失败")
    if results["fixed"]:
        print(f"修复的指标: {', '.join(results['fixed'])}")
    if results["failed"]:
        print(f"失败的指标: {', '.join(results['failed'])}")


def _test_and_fix(ttl_path: str, measure: dict, dim_code: str, max_retries: int) -> bool:
    """测试单个指标，失败时 LLM 修复，最多重试 max_retries 次。"""
    code = measure["code"]
    cn = measure["cn_name"]

    for attempt in range(max_retries):
        result = query_da(code, dim_code)
        if result["ok"]:
            print(f"   ✓ {dim_code} 查询成功 ({result['rows']} rows)")
            return True

        error = result["error"]
        print(f"   ✗ 查询失败: {error[:150]}")

        if attempt < max_retries - 1:
            print(f"   🔧 LLM 分析修复 (attempt {attempt + 2}/{max_retries})...")
            user = f"""指标查询失败：
  - 指标代码: {code}
  - 中文名: {cn}
  - 测试维度: {dim_code}
  - 错误信息: {error}

该指标当前的 TTL 配置：
```
{measure['ttl_snippet']}
```

请分析失败原因，输出修正后的 TTL 配置。只输出与需要修改的 inst: 实例，
格式为标准 Turtle。"""

            try:
                fix_ttl = llm_call(SYSTEM_PROMPT, user)
                print(f"   LLM 响应: {fix_ttl[:200]}...")
                if apply_fix(ttl_path, code, fix_ttl):
                    # 重新加载该指标的配置
                    measures = load_measures(ttl_path)
                    for m in measures:
                        if m["code"] == code:
                            measure["ttl_snippet"] = m["ttl_snippet"]
                            break
                    time.sleep(3)  # 等 DA 热加载
            except Exception as e:
                print(f"   ⚠ LLM 修复异常: {e}")
                break

    return False


if __name__ == "__main__":
    main()
