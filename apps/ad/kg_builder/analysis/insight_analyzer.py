"""
insight_analyzer.py — InsightAnalyzer

自然语言数据洞察分析器：
  1. LLM 意图解析（指标关键词、时间范围、粒度）
  2. 从业务图谱（TTL）匹配指标及可用维度
  3. 构造 DA API query_params
  4. 委托 IndicatorAnalyzer 完整执行 6-Part 分析
  5. 流式输出 Insight 直接回答段
"""
from __future__ import annotations

import datetime
import json
import re
import urllib.request as _ureq
from pathlib import Path
from typing import Callable, Generator, List, Optional


# ── 工具函数 ──────────────────────────────────────────────────────────────── #

def _week_bounds(year: int, week: int) -> tuple[str, str]:
    """返回 ISO 周的周一和周日日期字符串。"""
    mon = datetime.date.fromisocalendar(year, week, 1)
    sun = datetime.date.fromisocalendar(year, week, 7)
    return mon.isoformat(), sun.isoformat()


def _week_code(year: int, week: int) -> str:
    return f"{year}{week:02d}"


def _current_year_week() -> tuple[int, int]:
    iso = datetime.date.today().isocalendar()
    return iso[0], iso[1]


# ── 主类 ─────────────────────────────────────────────────────────────────── #

class InsightAnalyzer:
    """
    自然语言 → 数据洞察分析器。

    参数与 IndicatorAnalyzer 保持一致，便于 web_app.py 统一初始化。
    """

    def __init__(
        self,
        data_agent_url: str,
        ttl_path: str,
        llm_config: dict,
        log_cb: Callable[[str], None],
        cancel_cb: Optional[Callable[[], bool]] = None,
        context: Optional[dict] = None,
    ) -> None:
        self._data_agent_url = data_agent_url
        self._ttl_path = ttl_path
        self._llm_config = llm_config
        self._log = log_cb
        self._cancel_cb = cancel_cb or (lambda: False)
        self._context = context if isinstance(context, dict) else {}

        # 懒加载 KG 数据（通过内嵌 IndicatorAnalyzer 的缓存方法）
        self._kg_cache: Optional[dict] = None  # {meas_code: {...}}
        self._kg_dim_cache: Optional[dict] = None  # {dim_code: cn_name}
        self._kg_dim_detail_cache: Optional[dict] = None  # {dim_code: {cn_name, viewTypeCode, hierarchyCode, levelCode}}
        self._kg_meas_table_cache: Optional[dict] = None  # {meas_code: [table_uri_str, ...]}
        self._kg_table_dims_cache: Optional[dict] = None  # {table_name: [dim_code, ...]}
        self._kg_dim_histogram_cache: Optional[dict] = None  # {(dim_code, table_name): row_num}

    # ── 主入口 ───────────────────────────────────────────────────────────── #

    def analyze(self, question: str) -> Generator[dict, None, None]:
        """
        主入口：按序 yield 进度事件和分析结果。
        所有事件格式与 IndicatorAnalyzer.analyze() 兼容，前端可共用渲染逻辑。
        额外新增：{"step":"intent"|"kg_match"|"kg_error"|"query_built", ...}
                  {"insight_text": "..."} （最终直答段，流式）
        """
        self._log("═══ Insight 分析启动 ═══")

        # ── Step 1: 解析时间范围（优先，立即展示给用户）────────────────────── #
        self._log("▶ Step 1: 解析时间范围...")
        today = datetime.date.today()
        y, w = today.isocalendar()[:2]
        time_info = self._rule_based_time(question, today, y, w)
        self._log(f"  时间范围: {time_info.get('time_start')} ~ {time_info.get('time_end')} "
                  f"（上期: {time_info.get('prev_start')} ~ {time_info.get('prev_end')}，粒度: {time_info.get('gran')}）")
        yield {"step": "time_parsed", "result": time_info}

        if self._cancel_cb():
            return

        # ── Step 2: LLM 提取指标关键词 + 分析类型（不依赖时间解析）──────────── #
        self._log("▶ Step 2: LLM 提取指标关键词...")
        intent = self._parse_intent(question, time_info)
        self._log(f"  关键词: {intent.get('meas_keywords')}，类型: {intent.get('analysis_type')}")
        yield {"step": "intent", "result": intent}

        if self._cancel_cb():
            return

        # ── Step 3: KG 指标匹配 ─────────────────────────────────────────── #
        self._log("▶ Step 3: 知识图谱指标匹配...")
        meas_info = self._find_meas_in_kg(intent.get("meas_keywords", []), question)

        if not meas_info:
            all_cn = [v["cn_name"] for v in self._load_kg_cache().values()][:15]
            hint = "、".join(all_cn)
            err_msg = (
                f"在业务图谱中未找到与「{'、'.join(intent.get('meas_keywords', []))}」相关的指标，无法提供分析。\n"
                f"当前图谱已收录指标示例：{hint}……\n"
                "请尝试使用更准确的指标名称，或确认该指标已录入图谱。"
            )
            self._log(f"  ✗ {err_msg}")
            yield {"step": "kg_error", "error": err_msg}
            return

        primary = meas_info.get("primary", meas_info)  # 兼容旧格式
        secondary = meas_info.get("secondary", [])
        self._log(
            f"  ✓ 匹配到指标: {primary['cn_name']} ({primary['meas_code']})"
            f"，关联表: {primary.get('table_name', '未知')}"
            f"，可用维度: {len(meas_info.get('dim_codes', []))} 个"
        )
        if secondary:
            sec_names = ", ".join(f"{s['cn_name']}({s['meas_code']})" for s in secondary)
            self._log(f"    辅助指标: {sec_names}")
        yield {"step": "kg_match", "result": {
            "meas_code":  primary["meas_code"],
            "cn_name":    primary["cn_name"],
            "table_name": primary.get("table_name", ""),
            "dim_count":  len(meas_info.get("dim_codes", [])),
            "dim_list":   meas_info.get("dim_codes", [])[:20],
            "time_dims":  meas_info.get("time_dims", {}),
            "secondary":  [{"meas_code": s["meas_code"], "cn_name": s["cn_name"]} for s in secondary],
        }}

        if self._cancel_cb():
            return

        # ── Step 4: 构造 query_params ────────────────────────────────────── #
        self._log("▶ Step 4: 构造查询参数...")
        query_params = self._build_query_params(meas_info, intent)
        self._log(f"  configureList: {len(query_params.get('configureList', []))} 项")
        self._log(f"  filterList: {json.dumps(query_params.get('filterList', []), ensure_ascii=False)}")
        yield {"step": "query_built", "result": {
            "configure_count": len(query_params.get("configureList", [])),
            "filter_summary": _summarize_filters(query_params.get("filterList", [])),
            "gran": intent.get("gran", "week"),
        }}

        if self._cancel_cb():
            return

        # ── Step 5: 完整 6-Part 分析（委托 IndicatorAnalyzer）────────────── #
        self._log("▶ Step 5: 启动完整 6-Part 分析...")
        from kg_builder.analysis.analyzer import IndicatorAnalyzer

        all_parts: dict = {}
        report_text = ""

        analyzer = IndicatorAnalyzer(
            data_agent_url=self._data_agent_url,
            ttl_path=self._ttl_path,
            llm_config=self._llm_config,
            log_cb=self._log,
            cancel_cb=self._cancel_cb,
        )
        for event in analyzer.analyze(query_params):
            if self._cancel_cb():
                return
            part_key = event.get("part")
            if isinstance(part_key, int) and "result" in event:
                all_parts[part_key] = event["result"]
            if "report" in event and "part" not in event:
                report_text = event.get("report", "")
            yield event

        if self._cancel_cb():
            return

        # ── Step 6: Insight 直答段（流式）────────────────────────────────── #
        self._log("▶ Step 6: 生成 Insight 直接回答...")
        yield {"step": "insight_start"}
        for chunk in self._stream_insight_answer(question, intent, meas_info, all_parts, report_text):
            if self._cancel_cb():
                return
            yield {"insight_text": chunk}

        yield {"step": "done"}
        self._log("═══ Insight 分析完成 ═══")

    # ── Step 1: 意图解析 ─────────────────────────────────────────────────── #

    def _parse_intent(self, question: str, time_info: dict) -> dict:
        """
        LLM 解析用户问题，提取指标关键词和分析类型。
        时间已由 _rule_based_time 解析完毕，此处只提取：
          meas_keywords: 指标相关关键词列表
          analysis_type: root_cause/trend/compare/describe
        """
        system = (
            "你是一个数据分析助手。根据用户的自然语言问题，提取以下字段。\n"
            "请严格返回 JSON，不要输出任何其他内容。\n"
            "字段说明：\n"
            "  meas_keywords: 指标相关关键词列表（只提取指标名称/缩写/英文名，"
            "不要包含时间信息、疑问词、动词等噪声词）\n"
            "    示例：'为什么3月15号NSS下滑' → [\"NSS\", \"净服务评分\"]\n"
            "    示例：'NPS净推荐值趋势' → [\"NPS\", \"净推荐值\"]\n"
            "    示例：'用车投诉率为什么高' → [\"用车投诉率\", \"投诉率\"]\n"
            "  analysis_type: root_cause（为什么/原因）/ trend（趋势）/ compare（对比）/ describe（是什么/情况）之一\n"
            "重要：meas_keywords 必须是纯净的指标名称或英文缩写，不含时间、疑问词。\n"
            '返回示例: {"meas_keywords":["NSS","净服务评分"],"analysis_type":"root_cause"}'
        )
        try:
            raw = self._llm_call(system, question, max_tokens=256)
            # 用括号深度匹配第一个完整 JSON 对象，避免贪婪 .* 吃到多余内容
            parsed = None
            start = raw.find("{")
            if start != -1:
                depth = 0
                for i in range(start, len(raw)):
                    ch = raw[i]
                    if ch == "{":
                        depth += 1
                    elif ch == "}":
                        depth -= 1
                        if depth == 0:
                            try:
                                parsed = json.loads(raw[start:i + 1])
                            except Exception:
                                pass
                            break
            if parsed and isinstance(parsed, dict):
                parsed.setdefault("analysis_type", "root_cause")
                parsed.setdefault("meas_keywords", [])
                result = {**time_info, **parsed}
                return result
        except Exception as e:
            self._log(f"  ⚠ LLM意图解析失败: {e}，使用规则兜底")

        # 规则兜底（关键词提取）
        today = datetime.date.today()
        y, w = today.isocalendar()[:2]
        keywords = re.findall(r'[A-Z]{2,}|[\u4e00-\u9fa5]{2,8}', question)
        # 过滤噪声词
        noise = {'为什么', '下滑', '上涨', '原因', '分析', '趋势', '情况', '问题', '怎么', '如何'}
        keywords = [k for k in keywords if k not in noise][:5]
        atype = "root_cause"
        if re.search(r'趋势|走势|变化', question):
            atype = "trend"
        elif re.search(r'对比|比较', question):
            atype = "compare"
        elif re.search(r'是什么|情况|现状', question):
            atype = "describe"
        return {**time_info, "meas_keywords": keywords, "analysis_type": atype}

    def _rule_based_time(self, question: str, today: datetime.date, y: int, w: int) -> dict:
        """规则提取时间信息。"""

        # 匹配"N月M日/号" → 日粒度，对比前一天
        # 注意：DA API 对原子指标用周维度过滤时 SQL 有 bug（%Y%u 格式与日期字符串比对失效），
        # 必须用日维度（DIM_submit_date_day + dataList 格式）才能保证算子查询成功。
        m = re.search(r'(\d{1,2})月(\d{1,2})[日号]', question)
        if m:
            mo, dy = int(m.group(1)), int(m.group(2))
            try:
                target = datetime.date(today.year, mo, dy)
                prev   = target - datetime.timedelta(days=1)
                return {"time_start": target.isoformat(), "time_end": target.isoformat(),
                        "prev_start": prev.isoformat(),   "prev_end": prev.isoformat(),
                        "gran": "day", "time_desc": m.group(0)}
            except Exception:
                pass

        # 匹配"第N周 / WN / W-N"
        m2 = re.search(r'第(\d{1,2})周|W(\d{1,2})', question, re.IGNORECASE)
        if m2:
            tw = int(m2.group(1) or m2.group(2))
            pw = tw - 1 if tw > 1 else 52
            py = y if tw > 1 else y - 1
            ts, te = _week_bounds(y, tw)
            ps, pe = _week_bounds(py, pw)
            return {"time_start": ts, "time_end": te, "prev_start": ps, "prev_end": pe,
                    "gran": "week", "time_desc": m2.group(0)}

        # 匹配"N月"
        m3 = re.search(r'(\d{1,2})月(?!\d)', question)
        if m3:
            mo = int(m3.group(1))
            try:
                first = datetime.date(today.year, mo, 1)
                last_day = (datetime.date(today.year, mo % 12 + 1, 1) - datetime.timedelta(days=1))
                prev_last = first - datetime.timedelta(days=1)
                prev_first = prev_last.replace(day=1)
                return {"time_start": first.isoformat(), "time_end": last_day.isoformat(),
                        "prev_start": prev_first.isoformat(), "prev_end": prev_last.isoformat(),
                        "gran": "month", "time_desc": m3.group(0)}
            except Exception:
                pass

        # 默认：月粒度（比周粒度更稳定，避免月初/周一当期数据为空）
        # 当月前3天 → 分析上个月（当期数据不足，无分析意义）
        # 否则 → 分析当月 vs 上月
        if today.day <= 3:
            # 上个月 vs 上上个月
            prev_month_last = today.replace(day=1) - datetime.timedelta(days=1)
            prev_month_first = prev_month_last.replace(day=1)
            pprev_month_last = prev_month_first - datetime.timedelta(days=1)
            pprev_month_first = pprev_month_last.replace(day=1)
            return {"time_start": prev_month_first.isoformat(), "time_end": prev_month_last.isoformat(),
                    "prev_start": pprev_month_first.isoformat(), "prev_end": pprev_month_last.isoformat(),
                    "gran": "month", "time_desc": f"{prev_month_first.month}月 vs {pprev_month_first.month}月"}
        else:
            curr_first = today.replace(day=1)
            curr_last = (curr_first.replace(month=curr_first.month % 12 + 1, day=1) -
                         datetime.timedelta(days=1)) if curr_first.month < 12 else datetime.date(today.year, 12, 31)
            prev_last = curr_first - datetime.timedelta(days=1)
            prev_first = prev_last.replace(day=1)
            return {"time_start": curr_first.isoformat(), "time_end": curr_last.isoformat(),
                    "prev_start": prev_first.isoformat(), "prev_end": prev_last.isoformat(),
                    "gran": "month", "time_desc": f"{curr_first.month}月 vs {prev_first.month}月"}

    # ── Step 2: KG 指标匹配 ──────────────────────────────────────────────── #

    def _load_kg_cache(self) -> dict:
        """懒加载 KG 指标元数据（含 cn_name）。"""
        if self._kg_cache is not None:
            return self._kg_cache

        from rdflib import Graph, Namespace
        p = Path(self._ttl_path)
        if not p.exists():
            self._kg_cache = {}
            return {}

        self._log("  加载知识图谱元数据...")
        g = Graph()
        g.parse(str(p), format="turtle")
        IND = Namespace("http://indicator.lixiang.com/ontology#")

        cache: dict = {}
        for inst in g.subjects(None, None):
            code = g.value(inst, IND.code)
            if not code or not str(code).startswith("MEAS_"):
                continue
            code = str(code)
            cn_name = g.value(inst, IND.cnName)
            mtype = g.value(inst, IND.measTypeCode)
            cache[code] = {
                "cn_name": str(cn_name) if cn_name else code,
                "mtype": int(float(str(mtype))) if mtype else 0,
                "uri": inst,
            }

        # 同时缓存维度详情和事实表关联
        self._kg_dim_detail_cache = {}
        self._kg_meas_table_cache = {}
        self._kg_table_dims_cache = {}
        self._kg_dim_histogram_cache = {}

        # 维度详情
        for inst in g.subjects(None, None):
            code = g.value(inst, IND.code)
            if not code or not str(code).startswith("DIM_"):
                continue
            code = str(code)
            cn_name = g.value(inst, IND.cnName)
            view_type = g.value(inst, IND.viewTypeCode)
            hier = g.value(inst, IND.hierarchyCode)
            level_code = g.value(inst, IND.levelCode)
            self._kg_dim_detail_cache[code] = {
                "cn_name":    str(cn_name) if cn_name else code,
                "viewTypeCode": int(float(str(view_type))) if view_type else 0,
                "hierarchyCode": str(hier) if hier else "",
                "levelCode":  str(level_code) if level_code else "",
            }

        # 指标 → 事实表关联（通过 MeasureApp.appliesToTable）
        for inst in g.subjects(None, None):
            code = g.value(inst, IND.code)
            if not code or not str(code).startswith("MEAS_"):
                continue
            code = str(code)
            for mapp in g.objects(inst, IND.hasMeasureApp):
                tbl = g.value(mapp, IND.appliesToTable)
                if tbl:
                    tbl_name = str(g.value(tbl, IND.tableName) or "")
                    self._kg_meas_table_cache.setdefault(code, [])
                    if tbl_name and tbl_name not in self._kg_meas_table_cache[code]:
                        self._kg_meas_table_cache[code].append(tbl_name)

        # 事实表 → 维度关联（通过 DimensionApp.dimFactTable）
        for dim_inst in g.subjects(None, None):
            dim_code = g.value(dim_inst, IND.code)
            if not dim_code or not str(dim_code).startswith("DIM_"):
                continue
            dim_code = str(dim_code)
            for dapp in g.objects(dim_inst, IND.hasDimApp):
                fact_tbl = g.value(dapp, IND.dimFactTable)
                if fact_tbl:
                    tbl_name = str(g.value(fact_tbl, IND.tableName) or "")
                    if tbl_name:
                        self._kg_table_dims_cache.setdefault(tbl_name, [])
                        if dim_code not in self._kg_table_dims_cache[tbl_name]:
                            self._kg_table_dims_cache[tbl_name].append(dim_code)

        # 维度基数（dimensionRowNum）
        for hist_inst in g.subjects(None, None):
            dim_code_v = g.value(hist_inst, IND.histDimCode)
            tbl_name_v = g.value(hist_inst, IND.histTableName)
            row_num_v  = g.value(hist_inst, IND.dimensionRowNum)
            if dim_code_v and tbl_name_v and row_num_v:
                key = (str(dim_code_v), str(tbl_name_v))
                try:
                    self._kg_dim_histogram_cache[key] = int(float(str(row_num_v)))
                except Exception:
                    pass

        self._kg_cache = cache
        self._log(f"  KG 加载完成: {len(cache)} 个指标，{len(self._kg_dim_detail_cache)} 个维度")
        return cache

    def _find_meas_in_kg(self, keywords: List[str], question: str = "") -> Optional[dict]:
        """
        从 KG 中按关键词匹配最佳指标（支持多指标）。
        匹配策略（优先级）：
          1. code 精确匹配（MEAS_xxx）
          2. cn_name 完全包含关键词（整体）
          3. 从关键词中拆分出的 token（大写缩写/短词）匹配 cn_name
          4. 从原始问题中直接提取大写缩写或中文短词进行匹配
        返回 {
          primary: {meas_code, cn_name, table_name, ...},
          secondary: [{meas_code, cn_name, ...}, ...],  # 其他高分指标
          dim_codes, time_dims  # 基于 primary 的维度
        }
        """
        cache = self._load_kg_cache()
        if not cache:
            return None

        # 构建候选 token 列表：
        # 先从 keywords 中提取，再从原始问题中提取大写缩写
        candidate_tokens: List[str] = []
        for kw in keywords:
            kw = kw.strip()
            if not kw:
                continue
            candidate_tokens.append(kw)
            # 从 keyword 中拆出大写缩写（如 "为什么NSS下滑" → "NSS"）
            upper_abbrs = re.findall(r'[A-Z]{2,}', kw)
            candidate_tokens.extend(upper_abbrs)
            # 拆出中文短词：滑动窗口切 2~4 字子串（避免贪婪匹配遗漏关键子串）
            for cn_seq in re.findall(r'[\u4e00-\u9fa5]+', kw):
                for ll in range(2, min(len(cn_seq), 4) + 1):
                    for i in range(len(cn_seq) - ll + 1):
                        candidate_tokens.append(cn_seq[i:i + ll])
        # 从原始问题中直接提取大写缩写（兜底）
        if question:
            upper_abbrs_q = re.findall(r'[A-Z]{2,}', question)
            candidate_tokens.extend(upper_abbrs_q)
            for cn_seq in re.findall(r'[\u4e00-\u9fa5]+', question):
                for ll in range(2, min(len(cn_seq), 4) + 1):
                    for i in range(len(cn_seq) - ll + 1):
                        candidate_tokens.append(cn_seq[i:i + ll])

        # 去重并过滤噪声词
        noise_words = {'为什么', '下滑', '上涨', '原因', '分析', '趋势', '情况', '问题',
                       '怎么', '如何', '月份', '时间', '数据', '月号', '这个', '那个'}
        seen = set()
        clean_tokens: List[str] = []
        for t in candidate_tokens:
            tl = t.lower().strip()
            if tl and tl not in seen and tl not in noise_words and len(tl) >= 2:
                seen.add(tl)
                clean_tokens.append(tl)

        if not clean_tokens:
            return None

        self._log(f"  匹配候选 tokens: {clean_tokens}")

        preferred_tables = set(self._context.get("factTables") or [])

        def _score_all_indicators(restrict_tables: bool) -> list[tuple[str, int, str, str]]:
            """返回 [(code, score, cn_name, table_name), ...] 按score降序"""
            results: list[tuple[str, int, str, str]] = []
            for code, info in cache.items():
                if restrict_tables and preferred_tables and not preferred_tables.intersection(
                    self._kg_meas_table_cache.get(code, [])
                ):
                    continue
                cn = info["cn_name"].lower()
                code_l = code.lower()
                score = 0
                for tok in clean_tokens:
                    if tok == code_l:
                        score += 100  # code 精确匹配
                    elif tok == cn:
                        score += 50   # cn_name 完全匹配
                    elif tok in cn:
                        score += 15 if len(tok) >= 3 else 5
                    else:
                        cn_parts = re.split(r'[\s/（）()、,，·]', cn)
                        if any(tok in part for part in cn_parts):
                            score += 3
                if score > 0:
                    tbl = (self._kg_meas_table_cache.get(code, []) or [""])[0]
                    results.append((code, score, info["cn_name"], tbl))
            results.sort(key=lambda x: x[1], reverse=True)
            return results

        # 第一轮：用上轮对话的事实表范围约束匹配
        scored = _score_all_indicators(restrict_tables=True)
        # 第二轮：表约束下无匹配时放开到全图谱（避免上轮指标跨域污染本轮问题）
        if not scored:
            if preferred_tables:
                self._log("  表约束下无匹配，全图谱放开搜索...")
            scored = _score_all_indicators(restrict_tables=False)

        if not scored:
            inherited_code = str(self._context.get("activeMeasureCode") or "").strip()
            if inherited_code in cache:
                self._log(f"  关键词无匹配，沿用上轮指标: {inherited_code}")
                info = cache[inherited_code]
                tbl = (self._kg_meas_table_cache.get(inherited_code, []) or [""])[0]
                scored = [(inherited_code, 1, info["cn_name"], tbl)]
            else:
                return None

        # 主指标 = 最高分；辅助指标 = 其余不同指标（必须同表，否则 DA 无法 join），最多2个
        primary_code, primary_score, primary_cn, primary_table = scored[0]
        seen_codes = {primary_code}
        secondary: list[dict] = []
        for code, score, cn, tbl in scored[1:]:
            if code not in seen_codes and len(secondary) < 2:
                # 辅助指标必须与主指标共享至少一个事实表（不同表的指标无法用同一时间维度 join）
                if tbl != primary_table:
                    self._log(f"  跳过跨表辅助指标: {cn} ({code}) 表={tbl} ≠ 主表={primary_table}")
                    continue
                seen_codes.add(code)
                secondary.append({"meas_code": code, "cn_name": cn, "table_name": tbl, "score": score})

        if secondary:
            names = ", ".join(s["cn_name"] for s in secondary)
            self._log(f"  辅助指标({len(secondary)}): {names}")

        info = cache[primary_code]
        table_name = primary_table

        # 获取主表的所有维度
        all_dim_codes = self._kg_table_dims_cache.get(table_name, [])

        # 过滤：只保留基数 <= 100 的维度（防止高基数维度拖慢查询）
        # 以及时间维度单独处理
        dim_detail = self._kg_dim_detail_cache or {}
        time_dims: dict = {}  # {levelCode: dim_code}  day/week/month
        regular_dims: list = []

        for dc in all_dim_codes:
            detail = dim_detail.get(dc, {})
            hier = detail.get("hierarchyCode", "")
            level = detail.get("levelCode", "")
            if hier == "h_date" or level in ("day", "week", "month", "year", "quarter"):
                if level in ("day", "week", "month"):
                    time_dims[level] = dc
            else:
                row_num = self._kg_dim_histogram_cache.get((dc, table_name))
                # 无基数数据时不拦截（默认可用），有数据且 >100 时才过滤
                # 同时提高阈值到 500，避免中等基数维度（如仓库、城市）被误杀
                if row_num is None or row_num <= 500:
                    regular_dims.append(dc)
                else:
                    self._log(f"  过滤高基维度: {dc} (row_num={row_num})")

        return {
            # Keep the primary fields at top-level for older callers/tests while
            # preserving the newer multi-measure structure below.
            "meas_code":     primary_code,
            "cn_name":       primary_cn,
            "table_name":    table_name,
            "primary": {
                "meas_code":  primary_code,
                "cn_name":    primary_cn,
                "table_name": table_name,
            },
            "secondary":    secondary,  # [{meas_code, cn_name, table_name, score}, ...]
            "dim_codes":    regular_dims[:10],  # 最多10个业务维度
            "time_dims":    time_dims,           # {day/week/month: dim_code}
        }

    # ── Step 3: 构造 query_params ────────────────────────────────────────── #

    def _build_query_params(self, meas_info: dict, intent: dict) -> dict:
        """
        构造 IndicatorAnalyzer.analyze() 所需的 query_params。
        支持多指标：primary + secondary 全部进入 configureList。
        """
        primary    = meas_info.get("primary", meas_info)  # 兼容旧格式
        secondary  = meas_info.get("secondary", [])
        meas_code  = primary["meas_code"]
        time_dims  = meas_info.get("time_dims", {})
        reg_dims   = meas_info.get("dim_codes", [])
        gran       = intent.get("gran", "week")
        time_start = intent.get("time_start", "")
        time_end   = intent.get("time_end", "")
        prev_start = intent.get("prev_start", "")
        prev_end   = intent.get("prev_end", "")

        # 选择时间维度
        time_dim_code = time_dims.get(gran) or time_dims.get("week") or time_dims.get("day") or ""
        day_dim_code  = time_dims.get("day", "")

        # 若找不到对应粒度的时间维，自动降级
        if not time_dim_code and gran == "month":
            time_dim_code = time_dims.get("week", "")
        if not time_dim_code:
            # 取任意时间维
            time_dim_code = next(iter(time_dims.values()), "")

        # 构造 configureList：主指标 + 辅助指标 + 时间维度 + 业务维度
        configure_list = [{"code": meas_code, "order": {"sortType": 0}, "ratioList": [], "alias": ""}]
        for sec in secondary:
            configure_list.append({"code": sec["meas_code"], "order": {"sortType": 0}, "ratioList": [], "alias": ""})
        if time_dim_code:
            configure_list.append({"code": time_dim_code, "order": {"sortType": 1}, "ratioList": [], "alias": ""})
        for dc in reg_dims:
            configure_list.append({"code": dc, "order": {"sortType": 0}, "ratioList": [], "alias": ""})

        # 构造 filterList（当期 + 上期）
        filter_list = []
        if time_dim_code:
            time_filter = self._build_time_filter(
                time_dim_code, gran, time_start, time_end, prev_start, prev_end
            )
            if time_filter:
                filter_list.append(time_filter)

        params = {
            "configureList": configure_list,
            "filterList":    filter_list,
            "pageSize":      500,
            "pageNum":       1,
        }
        if day_dim_code and gran in ("week", "month"):
            params["_p2DayDim"] = day_dim_code
        params["_gran"] = gran

        return params

    def _build_time_filter(
        self,
        time_dim_code: str,
        gran: str,
        time_start: str,
        time_end: str,
        prev_start: str,
        prev_end: str,
    ) -> Optional[dict]:
        """
        构造时间过滤器。
        - 周粒度：使用 dataList（周码 YYYYWW）+ internal=true
        - 日/月粒度：使用 begin/end
        """
        if not time_start:
            return None

        if gran == "week":
            # 从日期推算周码
            weeks = set()
            for ds, de in [(time_start, time_end), (prev_start, prev_end)]:
                if not ds:
                    continue
                try:
                    d = datetime.date.fromisoformat(ds)
                    end_d = datetime.date.fromisoformat(de) if de else d + datetime.timedelta(days=6)
                    cur = d
                    while cur <= end_d:
                        iso = cur.isocalendar()
                        weeks.add(_week_code(iso[0], iso[1]))
                        cur += datetime.timedelta(days=7)
                except Exception:
                    pass
            if weeks:
                return {
                    "code":         time_dim_code,
                    "viewType":     2,
                    "operatorList": [{"sqlOprType": 2, "dataList": sorted(weeks), "timeRange": 1}],
                    "internal":     True,
                }
        elif gran == "day":
            start = min(filter(None, [time_start, prev_start])) if prev_start else time_start
            end   = max(filter(None, [time_end,   prev_end]))   if prev_end   else time_end
            # 注意：DA API 对日维度的 begin/end 格式完全无效（不生成 WHERE 过滤）
            # 必须使用 dataList + internal:true + timeRange:1 格式，与周维度保持一致
            return {
                "code":         time_dim_code,
                "operatorList": [{"sqlOprType": 2, "dataList": [start, end], "timeRange": 1}],
                "internal":     True,
            }
        elif gran == "month":
            # 月维度需要月码列表（YYYYMM），而非日期范围
            months: list[str] = []
            for ds in [time_start, prev_start]:
                if not ds:
                    continue
                try:
                    d = datetime.date.fromisoformat(ds)
                    months.append(f"{d.year}{d.month:02d}")
                except Exception:
                    pass
            for ds in [time_end, prev_end]:
                if not ds:
                    continue
                try:
                    d = datetime.date.fromisoformat(ds)
                    mc = f"{d.year}{d.month:02d}"
                    if mc not in months:
                        months.append(mc)
                except Exception:
                    pass
            # 填充中间月份（如 4月→5月 之间有 gap 时补齐）
            if len(months) >= 2:
                try:
                    # 按月码数值排序后取 min/max，避免顺序反转导致空 range
                    nums = sorted(int(m) for m in months)
                    lo, hi = nums[0], nums[-1]
                    y1, m1 = lo // 100, lo % 100
                    y2, m2 = hi // 100, hi % 100
                    full: set[str] = set()
                    for ym in range(y1 * 12 + m1, y2 * 12 + m2 + 1):
                        yr = ym // 12
                        mo = ym % 12
                        if mo == 0:
                            yr -= 1
                            mo = 12
                        full.add(f"{yr}{mo:02d}")
                    months = sorted(full)
                except Exception:
                    pass
            if months:
                return {
                    "code":         time_dim_code,
                    "viewType":     2,
                    "operatorList": [{"sqlOprType": 2, "dataList": months, "timeRange": 1}],
                    "internal":     True,
                }
        return None

    # ── Step 5: 流式直答段 ───────────────────────────────────────────────── #

    def _stream_insight_answer(
        self,
        question: str,
        intent: dict,
        meas_info: dict,
        all_parts: dict,
        report_text: str,
    ) -> Generator[str, None, None]:
        """
        基于 Part6 综合报告生成针对用户问题的流式直接回答。

        输入优先级：
          主体：Part6 综合报告（已整合全部分析结论）
          补充：Part1 Top3 LMDI 贡献因子 + Part3 Top3 异常点（原始数字，防止报告遗漏）
        """
        if not report_text:
            yield "（分析报告尚未生成，无法输出直接回答）"
            return

        # ── 补充摘要：Part1 Top3 贡献因子 ───────────────────────────────── #
        supp_lines: list[str] = []

        p1 = all_parts.get(1, {})
        top3 = p1.get("global_top20", [])[:3]
        if top3:
            items = []
            for t in top3:
                dim  = t.get("dim_cn_name") or t.get("dim_col", "")
                val  = t.get("value", "")
                cont = t.get("lmdi_contrib", 0)
                pct  = t.get("contrib_pct")
                pct_s = f"({pct:+.1f}%)" if pct is not None else ""
                items.append(f"  • {dim}={val}  贡献={cont:+.6f}{pct_s}")
            supp_lines.append("LMDI Top3贡献因子：\n" + "\n".join(items))

        # ── 补充摘要：Part3 Top3 异常点 ─────────────────────────────────── #
        p3 = all_parts.get(2, {})   # analyzer 内部 part key=2 对应统计量化
        anom_items = []
        for col, anoms in (p3.get("anomalies") or {}).items():
            for a in sorted(anoms, key=lambda x: abs(x.get("z_score") or 0), reverse=True)[:2]:
                dims_s = "  ".join(f"{k}={v}" for k, v in (a.get("dims") or {}).items() if v)
                anom_items.append(
                    f"  • {a.get('period','')}  {dims_s}  值={a.get('value','')}  z={a.get('z_score','?')}"
                )
        if anom_items:
            supp_lines.append("统计异常Top点：\n" + "\n".join(anom_items[:4]))

        supp_text = ("\n\n" + "\n\n".join(supp_lines)) if supp_lines else ""

        # ── LLM Prompt ──────────────────────────────────────────────────── #
        primary    = meas_info.get("primary", meas_info)  # 兼容旧格式
        secondary  = meas_info.get("secondary", [])
        cn_name    = primary.get("cn_name", "")
        meas_codes = [primary.get("meas_code", "")]
        if secondary:
            cn_name += " + " + "、".join(s["cn_name"] for s in secondary)
            meas_codes.extend(s["meas_code"] for s in secondary)
        time_desc = (
            f"{intent.get('prev_start','')}~{intent.get('prev_end','')}（上期）vs "
            f"{intent.get('time_start','')}~{intent.get('time_end','')}（当期）"
        )

        multi_indicator_hint = ""
        if secondary:
            multi_indicator_hint = (
                f"\n本次分析同时查询了以下指标：{cn_name}。"
                "请在分析时注意指标间的关联关系，例如对比变化幅度、识别成本传导链等。"
            )

        system = (
            "你是一位资深数据分析师。\n"
            "下方提供了一份针对特定指标的完整数据分析报告，以及少量关键数字补充。\n"
            "请严格基于报告内容，针对用户的具体问题给出直接、精准的回答。\n"
            + multi_indicator_hint + "\n\n"
            "输出格式（Markdown）：\n"
            "## 结论\n"
            "1-2句话直接回答问题，包含最核心的数字或维度。\n\n"
            "## 关键证据\n"
            "- 3-5条，每条引用报告中的具体数字/维度/百分比\n\n"
            "## 建议排查方向\n"
            "- 2-3条，给出可操作的后续分析或业务跟进建议\n\n"
            "要求：中文，简洁专业，直接引用数字，不要泛泛而谈。"
        )

        user = (
            f"用户问题：{question}\n\n"
            f"分析指标：{cn_name}\n"
            f"指标代码列表：{', '.join(meas_codes)}\n"
            f"分析时段：{time_desc}\n\n"
            f"===== 综合分析报告 =====\n"
            f"{report_text}\n"
            f"===== 补充关键数字 ====={supp_text}"
        )

        self._log(f"  直答段 prompt 长度: {len(user)} 字符")

        # 流式输出
        try:
            yield from self._llm_stream(system, user, max_tokens=1200)
        except Exception as e:
            self._log(f"  ⚠ 直答段生成失败: {e}")
            yield f"\n\n（直接回答生成失败：{e}）"


    # ── LLM 调用 ─────────────────────────────────────────────────────────── #

    def _llm_call(self, system: str, user: str, max_tokens: int = 1024) -> str:
        """同步 LLM 调用，自动适配 OpenAI/Anthropic 后端。"""
        api_key  = self._llm_config.get("api_key", "")
        base_url = self._llm_config.get("base_url", "").rstrip("/")
        model    = self._llm_config.get("model", "GPT5.5")
        is_anthropic = "anthropic" in base_url.lower()

        if is_anthropic:
            payload = {
                "model":      model,
                "max_tokens": max_tokens,
                "messages":   [{"role": "user", "content": user}],
                "system":     system,
            }
            body = json.dumps(payload).encode("utf-8")
            headers = {
                "Content-Type":      "application/json",
                "x-api-key":         api_key,
                "anthropic-version": "2023-06-01",
            }
            req = _ureq.Request(f"{base_url}/messages", data=body, headers=headers, method="POST")
            with _ureq.urlopen(req, timeout=60) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            if "content" in data:
                return self._strip_reasoning(data["content"][0]["text"])
            if "choices" in data:
                return self._strip_reasoning(data["choices"][0]["message"]["content"])
            return str(data)
        else:
            # OpenAI-compatible (MiniMax, etc.)
            combined = system + "\n\n" + user
            payload = {
                "model":      model,
                "max_tokens": max_tokens,
                "messages":   [{"role": "user", "content": combined}],
            }
            body = json.dumps(payload).encode("utf-8")
            headers = {
                "Content-Type":  "application/json",
                "Authorization": f"Bearer {api_key}",
            }
            req = _ureq.Request(f"{base_url}/chat/completions", data=body, headers=headers, method="POST")
            with _ureq.urlopen(req, timeout=60) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            if "choices" in data:
                return self._strip_reasoning(data["choices"][0]["message"]["content"])
            return str(data)

    @staticmethod
    def _strip_reasoning(text: str) -> str:
        """Remove chain-of-thought / reasoning noise from LLM output."""
        lines = text.strip().split("\n")
        cleaned = []
        for line in lines:
            stripped = line.strip()
            if not stripped:
                cleaned.append("")
                continue
            is_reasoning = any(
                stripped.startswith(p) for p in (
                    "I need", "I must", "I should", "I will", "I'll",
                    "Let me", "Let's", "First", "Then", "Now",
                    "The user", "They say", "They also", "They want",
                    "But I", "But the", "We need", "We must", "We should",
                    "Must output", "Should output", "So I", "So we",
                    "Thus", "Therefore I", "Make sure", "Note that",
                    "The required", "The output", "We'll", "We're",
                    "I'm going", "I am going", "I think", "I believe",
                    "Based on", "Given that", "Following the",
                    "This is", "That is", "It's important",
                    "In this case", "Here I", "Here we",
                    "Okay", "OK so", "Alright", "Right",
                    "The final", "Final answer", "My response",
                    "The answer", "The response",
                )
            )
            is_meta = (
                stripped.startswith("The ") and
                (" should " in stripped or " must " in stripped) and
                ("paragraph" in stripped.lower() or
                 "line" in stripped.lower() or
                 "output" in stripped.lower() or
                 "format" in stripped.lower() or
                 "direction" in stripped.lower())
            )
            if is_reasoning or is_meta:
                continue
            cleaned.append(line)
        result = "\n".join(cleaned).strip()
        return result if result else text

    def _llm_stream(self, system: str, user: str, max_tokens: int = 1024) -> Generator[str, None, None]:
        """
        流式 LLM 调用，自动适配 OpenAI/Anthropic 后端。
        若接口不支持流式，退化为一次性返回整段文本（每句话 yield 一次）。
        """
        api_key  = self._llm_config.get("api_key", "")
        base_url = self._llm_config.get("base_url", "").rstrip("/")
        model    = self._llm_config.get("model", "GPT5.5")
        is_anthropic = "anthropic" in base_url.lower()

        if is_anthropic:
            payload = {
                "model":      model,
                "max_tokens": max_tokens,
                "messages":   [{"role": "user", "content": user}],
                "system":     system,
                "stream":     True,
            }
            body = json.dumps(payload).encode("utf-8")
            headers = {
                "Content-Type":      "application/json",
                "x-api-key":         api_key,
                "anthropic-version": "2023-06-01",
            }
            req = _ureq.Request(f"{base_url}/messages", data=body, headers=headers, method="POST")
            try:
                with _ureq.urlopen(req, timeout=120) as resp:
                    for raw_line in resp:
                        line = raw_line.decode("utf-8").strip()
                        if not line or not line.startswith("data:"):
                            continue
                        data_str = line[5:].strip()
                        if data_str in ("[DONE]", "__DONE__"):
                            break
                        try:
                            chunk = json.loads(data_str)
                            if chunk.get("type") == "content_block_delta":
                                text = chunk.get("delta", {}).get("text", "")
                                if text:
                                    yield text
                            elif "choices" in chunk:
                                text = chunk["choices"][0].get("delta", {}).get("content", "")
                                if text:
                                    yield text
                        except Exception:
                            pass
            except Exception as e:
                self._log(f"  流式调用失败({e})，降级为非流式")
                payload.pop("stream", None)
                text = self._llm_call(system, user, max_tokens)
                for sentence in re.split(r'(?<=[。！？\n])', text):
                    if sentence:
                        yield sentence
        else:
            # OpenAI-compatible streaming (MiniMax, etc.)
            combined = system + "\n\n" + user
            payload = {
                "model":      model,
                "max_tokens": max_tokens,
                "messages":   [{"role": "user", "content": combined}],
                "stream":     True,
            }
            body = json.dumps(payload).encode("utf-8")
            headers = {
                "Content-Type":  "application/json",
                "Authorization": f"Bearer {api_key}",
            }
            req = _ureq.Request(f"{base_url}/chat/completions", data=body, headers=headers, method="POST")
            try:
                with _ureq.urlopen(req, timeout=120) as resp:
                    for raw_line in resp:
                        line = raw_line.decode("utf-8").strip()
                        if not line or not line.startswith("data:"):
                            continue
                        data_str = line[5:].strip()
                        if data_str == "[DONE]":
                            break
                        try:
                            chunk = json.loads(data_str)
                            if "choices" in chunk:
                                delta = chunk["choices"][0].get("delta", {})
                                text = delta.get("content", "")
                                if text:
                                    yield text
                        except Exception:
                            pass
            except Exception as e:
                self._log(f"  流式调用失败({e})，降级为非流式")
                payload.pop("stream", None)
                text = self._llm_call(system, user, max_tokens)
                for sentence in re.split(r'(?<=[。！？\n])', text):
                    if sentence:
                        yield sentence


# ── 工具 ─────────────────────────────────────────────────────────────────── #

def _summarize_filters(filter_list: list) -> str:
    parts = []
    for f in filter_list:
        code = f.get("code", "")
        for op in f.get("operatorList", []):
            dl = op.get("dataList")
            if dl:
                parts.append(f"{code} in {dl[:3]}{'...' if len(dl) > 3 else ''}")
            elif op.get("begin"):
                parts.append(f"{code} [{op['begin']} ~ {op['end']}]")
    return "；".join(parts) if parts else "（无）"
