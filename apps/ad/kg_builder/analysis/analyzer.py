"""
analyzer.py — IndicatorAnalyzer

分析指标数据，包含四个部分：
  Part 1: 波动分析（当期 vs 上期）
  Part 2: KG 图谱关系分析
  Part 3: 统计学分析（描述性 + 趋势 + 异常 + 相关性）
  Part 4: LLM 综合报告
"""
from __future__ import annotations

import decimal
import json
import math
import urllib.request as _ureq
from pathlib import Path
from typing import Callable, Generator, Optional


class _SafeEncoder(json.JSONEncoder):
    """JSON encoder that handles Decimal, date, numpy types."""
    def default(self, obj):
        import datetime
        try:
            import numpy as np
            if isinstance(obj, (np.integer,)):
                return int(obj)
            if isinstance(obj, (np.floating,)):
                return float(obj)
            if isinstance(obj, np.ndarray):
                return obj.tolist()
        except ImportError:
            pass
        if isinstance(obj, decimal.Decimal):
            return float(obj)
        if isinstance(obj, (datetime.date, datetime.datetime)):
            return str(obj)
        return super().default(obj)


# ── helpers ─────────────────────────────────────────────────────────────── #

def _safe_float(v) -> Optional[float]:
    try:
        f = float(v)
        return None if (math.isnan(f) or math.isinf(f)) else f
    except (TypeError, ValueError):
        return None


def _pct_change(curr, prev) -> Optional[float]:
    if prev is None or curr is None:
        return None
    if prev == 0:
        return None
    return round((curr - prev) / abs(prev) * 100, 2)


def _log_mean(a: float, b: float) -> float:
    """对数均值 L(a,b)=(a-b)/(ln a - ln b)，用于 LMDI 分解。a=b 时返回 a。"""
    if a == b:
        return a
    if a <= 0 or b <= 0:
        return (a + b) / 2.0          # 含负值时退化为算术均值
    return (a - b) / (math.log(a) - math.log(b))


def _lmdi_additive(v_curr: dict, v_prev: dict) -> dict:
    """
    LMDI 加法分解：将总量变化按各子项贡献度分配。
    v_curr / v_prev: {key: float}，值为各子项对总指标的加权贡献（= 指标值 × 份额）。
    返回 {key: contribution}，Σcontribution ≈ Σv_curr - Σv_prev。
    """
    all_keys = set(v_curr) | set(v_prev)
    result = {}
    for k in all_keys:
        a = v_curr.get(k, 0.0) or 0.0
        b = v_prev.get(k, 0.0) or 0.0
        if a == 0 and b == 0:
            result[k] = 0.0
        elif a > 0 and b > 0:
            result[k] = _log_mean(a, b) * (math.log(a) - math.log(b))
        else:
            # 含零或负值：直接差值
            result[k] = a - b
    return result


class IndicatorAnalyzer:
    """Main analysis entry point."""

    def __init__(
        self,
        data_agent_url: str,
        ttl_path: str,
        llm_config: dict,
        log_cb: Callable[[str], None],
        cancel_cb: Optional[Callable[[], bool]] = None,
    ) -> None:
        self._data_agent_url = data_agent_url
        self._ttl_path = ttl_path
        self._llm_config = llm_config
        self._log = log_cb
        self._cancel_cb = cancel_cb          # 返回 True 时表示本次分析已被取消
        self._ttl_meta_cache: Optional[dict] = None   # 缓存 TTL 指标元数据
        self._ttl_dim_cache:  Optional[dict] = None   # 缓存 TTL 维度元数据 {code: cn_name}

    # ── TTL 元数据 ──────────────────────────────────────────────────────── #

    def _ttl_meta(self) -> dict:
        """
        懒加载并缓存所有指标的 TTL 元数据。
        返回 {meas_code: {type, operands, denominator, expr_tree}}
        - type: 0=原子, 1=衍生, 2=派生
        - operands: 直接引用的子指标 code 列表
        - denominator: 若表达式为 A/B 形式，返回 B 的 code
        - expr_tree: [{type:operand/operator, measCode, operator}]
        """
        if self._ttl_meta_cache is not None:
            return self._ttl_meta_cache

        from rdflib import Graph, Namespace, Literal
        from pathlib import Path as _Path

        meta: dict = {}
        p = _Path(self._ttl_path)
        if not p.exists():
            self._ttl_meta_cache = meta
            return meta

        g = Graph()
        g.parse(str(p), format="turtle")
        IND = Namespace("http://indicator.insightmind.com/ontology#")

        for inst in g.subjects(None, None):
            code = g.value(inst, IND.code)
            if not code or not str(code).startswith("MEAS_"):
                continue
            code = str(code)
            mtype = g.value(inst, IND.measTypeCode)
            mtype = int(float(str(mtype))) if mtype else 0

            # 取第一个 MeasureApp 的 expression
            mapps = list(g.objects(inst, IND.hasMeasureApp))
            expression = str(g.value(mapps[0], IND.expression)) if mapps else None

            operands, denominator, expr_tree = [], None, None
            if expression and expression.strip().startswith("["):
                try:
                    ops = json.loads(expression)
                    expr_tree = []
                    for op in ops:
                        ot = op.get("operatingType")
                        if ot == "operand":
                            mc = (op.get("operand") or {}).get("measCode")
                            if mc:
                                operands.append(mc)
                                expr_tree.append({"type": "operand", "measCode": mc})
                        elif ot == "operator":
                            expr_tree.append({"type": "operator", "operator": op.get("operator")})
                        elif ot == "constant":
                            expr_tree.append({"type": "constant", "value": op.get("constant")})

                    # 检测 A/B 形式：operand / operand（排除常数）
                    operand_nodes = [n for n in expr_tree if n["type"] == "operand"]
                    op_nodes = [n for n in expr_tree if n["type"] == "operator"]
                    if (len(operand_nodes) == 2
                            and len(op_nodes) == 1
                            and op_nodes[0]["operator"] == "/"):
                        denominator = operand_nodes[1]["measCode"]
                except Exception:
                    pass

            meta[code] = {
                "type":        mtype,
                "cn_name":     str(g.value(inst, IND.cnName)) if g.value(inst, IND.cnName) else code,
                "operands":    operands,
                "denominator": denominator,
                "expr_tree":   expr_tree,
            }

        self._ttl_meta_cache = meta
        return meta

    def _ttl_dims(self) -> dict:
        """
        懒加载并缓存所有维度的 TTL 元数据。
        返回 {dim_code: cn_name}，排除日期类维度（submit_date_year/quarter/month/week）。
        """
        if self._ttl_dim_cache is not None:
            return self._ttl_dim_cache

        from rdflib import Graph, Namespace
        from pathlib import Path as _Path

        dims: dict = {}
        p = _Path(self._ttl_path)
        if not p.exists():
            self._ttl_dim_cache = dims
            return dims

        g = Graph()
        g.parse(str(p), format="turtle")
        IND = Namespace("http://indicator.insightmind.com/ontology#")

        # 排除粗粒度日期维度（只保留日粒度和非日期维度）
        _exclude_suffix = ("_year", "_quarter", "_month", "_week")

        for inst in g.subjects(None, None):
            code = g.value(inst, IND.code)
            if not code or not str(code).startswith("DIM_"):
                continue
            code = str(code)
            if any(code.endswith(s) for s in _exclude_suffix):
                continue
            cn_name = g.value(inst, IND.cnName)
            dims[code] = str(cn_name) if cn_name else code

        self._ttl_dim_cache = dims
        return dims

    def _leaf_operands(self, meas_code: str, visited: Optional[set] = None) -> list:
        """递归获取复合指标的所有叶节点（原子指标）code 列表。"""
        if visited is None:
            visited = set()
        if meas_code in visited:
            return []
        visited.add(meas_code)

        meta = self._ttl_meta()
        info = meta.get(meas_code)
        if not info or info["type"] == 0 or not info["operands"]:
            return [meas_code]  # 原子指标或未知 → 本身即叶节点

        leaves = []
        for child in info["operands"]:
            leaves.extend(self._leaf_operands(child, visited))
        return leaves

    def _leaf_num_coefficients(self, meas_code: str, outer_sign: float = 1.0,
                               visited: Optional[set] = None) -> dict:
        """
        递归提取指标各叶节点在线性表达式中的系数。

        算法：将 expr_tree（中缀列表）按加减运算符分割为「项」，
        每项由操作数×常数构成，乘法系数累积，符号由前导 +/- 确定。

        返回 {leaf_code: coefficient}，coefficient 是该叶节点对本指标的边际权重。
        仅适用于线性分子（无叶节点相乘），对非线性项返回近似。
        """
        if visited is None:
            visited = set()
        if meas_code in visited:
            return {}
        visited.add(meas_code)

        meta = self._ttl_meta()
        info = meta.get(meas_code, {})

        # 原子指标：本身系数 = outer_sign
        if info.get("type", 0) == 0 or not info.get("expr_tree"):
            return {meas_code: outer_sign}

        expr_tree = info["expr_tree"]

        # 将中缀 expr_tree 按加减分割成「项」列表
        # 每项：(sign, [nodes...]) — sign 由前导 +/- 决定
        terms: list = []
        current_sign: float = 1.0
        current_nodes: list = []

        for node in expr_tree:
            if node["type"] == "operator" and node["operator"] in ("+", "-"):
                if current_nodes:
                    terms.append((current_sign, current_nodes))
                current_sign = 1.0 if node["operator"] == "+" else -1.0
                current_nodes = []
            else:
                current_nodes.append(node)
        if current_nodes:
            terms.append((current_sign, current_nodes))

        coeffs: dict = {}
        for term_sign, nodes in terms:
            # 一项可能是：operand、operand * constant、constant * operand 等
            num_coeff: float = term_sign * outer_sign
            operand_code: Optional[str] = None
            for node in nodes:
                if node["type"] == "operand":
                    operand_code = node["measCode"]
                elif node["type"] == "constant":
                    num_coeff *= float(node["value"])
                elif node["type"] == "operator":
                    pass  # * / 运算符本身不改变系数（常数节点已携带值）
            if operand_code is not None:
                # 递归展开子指标
                sub = self._leaf_num_coefficients(operand_code, num_coeff, visited)
                for leaf, c in sub.items():
                    coeffs[leaf] = coeffs.get(leaf, 0.0) + c

        return coeffs

    # ── public ─────────────────────────────────────────────────────────── #


    def analyze(self, query_params: dict) -> Generator:
        """主入口：按序 yield 各步骤结果字典或报告字符串。"""
        self._log("═══ 开始指标分析 ═══")

        # 摘取内部控制参数（不传给 DA API）
        query_params = dict(query_params)
        _p2_day_dim: Optional[str] = query_params.pop("_p2DayDim", None)
        _gran: str = query_params.pop("_gran", "week")

        # 提取 meas_codes / dim_codes
        cfg_list = query_params.get("configureList", [])
        meas_codes = [c["code"] for c in cfg_list if c.get("code", "").startswith("MEAS_")]
        dim_codes  = [c["code"] for c in cfg_list if c.get("code", "").startswith("DIM_")]

        if not meas_codes:
            self._log("⚠ 无指标列，终止分析")
            return

        # 仅从 code 名称识别时间维度（不需要预先拉数据）
        time_col = self._detect_time_dim(None, dim_codes)
        if time_col:
            self._log(f"  识别到时间维度列: {time_col}")

        # 检测时间粒度（viewType=1 为日粒度；dataList 格式说明是周/期次，不是日粒度）
        _time_view_type = 1
        _time_filter_uses_datalist = False
        for _f in query_params.get("filterList", []):
            if time_col and _f.get("code") == time_col:
                _time_view_type = _f.get("viewType", 1)
                if (_f.get("operatorList") or [{}])[0].get("dataList"):
                    _time_filter_uses_datalist = True
                break
        _is_daily = (_time_view_type == 1 and not _time_filter_uses_datalist)

        # ── 提取分析配置的实际日期范围（供 Part 2 使用）─────────────────── #
        import datetime as _dt
        _cfg_start_date: Optional[str] = None
        _cfg_end_date:   Optional[str] = None
        for _f in query_params.get("filterList", []):
            if time_col and _f.get("code") == time_col:
                _opl = (_f.get("operatorList") or [{}])[0]
                _dl  = _opl.get("dataList")
                if _dl and len(_dl) >= 2:
                    # 周代码格式 YYYYWW → 转换为实际日期（首周周一 ~ 末周周日）
                    try:
                        yr0, wk0 = int(_dl[0][:4]), int(_dl[0][4:])
                        yr1, wk1 = int(_dl[1][:4]), int(_dl[1][4:])
                        _cfg_start_date = _dt.date.fromisocalendar(yr0, wk0, 1).isoformat()
                        _cfg_end_date   = _dt.date.fromisocalendar(yr1, wk1, 7).isoformat()
                    except Exception:
                        pass
                elif _opl.get("begin") and _opl.get("end"):
                    _cfg_start_date = str(_opl["begin"])[:10]
                    _cfg_end_date   = str(_opl["end"])[:10]
                break
        _cfg_day_count = 0
        if _cfg_start_date and _cfg_end_date:
            try:
                _cfg_day_count = (
                    _dt.date.fromisoformat(_cfg_end_date)
                    - _dt.date.fromisoformat(_cfg_start_date)
                ).days + 1
            except Exception:
                pass
        self._log(f"  分析时间范围: {_cfg_start_date} ~ {_cfg_end_date}，共 {_cfg_day_count} 天")

        # 跨 Part 传递的重点关注方向（handoff chain）
        prev_focuses: dict = {}

        # ── Part 1 — 波动识别 ──────────────────────────────────────────── #
        self._log("▶ [1/6] Part 1 — 波动识别…")
        part1 = {}
        try:
            part1 = self._analyze_fluctuations(query_params, meas_codes, dim_codes, time_col, _p2_day_dim)
        except Exception as e:
            self._log(f"  ⚠ Part 1 出错: {e}")
            part1 = {"error": str(e)}
        yield {"part": 1, "result": part1}

        try:
            p1i = self._part_interp("p1", part1, prev_focuses)
            yield {"part": "1_interp", "text": p1i["text"], "focus": p1i["focus"]}
            if p1i.get("focus"):
                prev_focuses["Part1_波动识别"] = p1i["focus"]
        except Exception as e:
            self._log(f"  ⚠ Part 1 interp 出错: {e}")

        # ── Part 2 — 统计量化 ──────────────────────────────────────────── #
        self._log("▶ [2/6] Part 2 — 统计量化…")
        # 先生成本 Part 任务规划（基于 Part 1 的"下阶段重点"）
        try:
            self._log(f"  Part2 规划：prev_focuses={list(prev_focuses.keys())}，focus='{list(prev_focuses.values())[-1][:30] if prev_focuses else ''}'")
            p2_plan = self._generate_part_plan("p2", prev_focuses)
            self._log(f"  Part2 规划完成：tasks={len(p2_plan.get('tasks',[]))}，goal='{p2_plan.get('core_goal','')[:30]}'")
            yield {"part": "2_plan", "plan": p2_plan}
        except Exception as e:
            self._log(f"  ⚠ Part 2 规划生成失败: {e}")
        rows: list = []
        part2_stats = {}
        df = None
        meas_cols: list = []
        dim_cols: list = []
        # 接收 Part 1 "下阶段重点" 作为分析导向
        p1_focus = prev_focuses.get("Part1_波动识别", "")

        # ── 10 天检测 ── #
        if 0 < _cfg_day_count < 10:
            part2_stats = {
                "skip_reason": (
                    f"配置时间范围仅 {_cfg_day_count} 天（{_cfg_start_date} ~ {_cfg_end_date}），"
                    "统计量化分析需至少 10 天数据，已跳过"
                )
            }
            self._log(f"  ⚠ Part 2 跳过：时间范围 {_cfg_day_count} 天 < 10 天")
        else:
            # 构造严格日期范围的 query_params
            # 关键：DA API 对 DIM_submit_date_week（周码字段）的 begin/end ISO 日期过滤
            # 完全无效，始终返回全量历史数据。
            # 正确做法：保留原始 filterList（dataList+internal 周码过滤），
            # 只修改 configureList 加入 _p2_day_dim；获取数据后在 Python 层按日期过滤。
            query_params_p2 = query_params  # 保留原始 filterList，不替换

            if _cfg_start_date and _cfg_end_date:
                self._log(f"  Part2 日期范围: {_cfg_start_date} ~ {_cfg_end_date}（Python层过滤列: {_p2_day_dim or time_col}，粒度: {_gran}）")

            # 若前端提供了日粒度维度，替换 configureList 中的时间维度，并加降序排序
            # 同时把 filterList 也换成日维度 begin/end，避免 DIM_submit_date_week+dataList
            # 与 DIM_submit_date_day 混用（DA API 不支持该组合，会返回查询失败）
            _p2_time_col = time_col
            if not _p2_day_dim and _gran == "day":
                # 日粒度直查：只保留指标 + 时间维度，去掉业务维度，避免 DA API 多维拒绝
                _p2_cfg = [{"code": time_col, "order": {"sortType": 1}}] + [
                    _c for _c in query_params_p2.get("configureList", [])
                    if _c.get("code", "") in meas_codes
                ]
                query_params_p2 = {**query_params_p2, "configureList": _p2_cfg}
            if _p2_day_dim:
                # Part 2 是时序统计分析，只需「指标 + 时间维度」
                # 去掉业务维度（DIM_repair_store_* 等），避免 DA API 拒绝组合查询
                _p2_cfg = []
                _day_placed = False
                for _c in query_params_p2.get("configureList", []):
                    _code = _c.get("code", "")
                    if _code in (time_col, _p2_day_dim):
                        if not _day_placed:
                            _p2_cfg.append({"code": _p2_day_dim, "order": {"sortType": 1}})
                            _day_placed = True
                    elif _code in meas_codes:
                        # 保留指标列
                        _p2_cfg.append(_c)
                    # 其他业务维度一律跳过
                if not _day_placed:
                    _p2_cfg = [{"code": _p2_day_dim, "order": {"sortType": 1}}] + _p2_cfg
                # filterList：去掉周维度过滤，加入日维度 begin/end 过滤
                _p2_filter_start = _cfg_start_date or ""
                _p2_filter_end   = _cfg_end_date   or ""
                if _p2_filter_start and _p2_filter_end:
                    _p2_day_filter = {
                        "code":         _p2_day_dim,
                        # 注意：DA API 对日维度 begin/end 格式无效，必须用 dataList + internal:true
                        "operatorList": [{"sqlOprType": 2,
                                           "dataList":  [_p2_filter_start, _p2_filter_end],
                                           "timeRange": 1}],
                        "internal":     True,
                    }
                    _p2_filters = [_f for _f in query_params_p2.get("filterList", [])
                                   if _f.get("code") != time_col]
                    _p2_filters.append(_p2_day_filter)
                    query_params_p2 = {**query_params_p2, "configureList": _p2_cfg, "filterList": _p2_filters}
                else:
                    query_params_p2 = {**query_params_p2, "configureList": _p2_cfg}
                _p2_time_col = _p2_day_dim
                self._log(f"  Part2 日粒度维度: {_p2_day_dim}（降序），过滤: {_p2_filter_start} ~ {_p2_filter_end}")

            try:
                rows, meas_cols, dim_cols = self._fetch_data(query_params_p2)
                self._log(f"  共 {len(rows)} 行，指标列: {meas_cols}，维度列: {dim_cols}")
            except Exception as _p2_err:
                # 日粒度查询失败时，fallback：用原始 dataList 格式（DIM_submit_date_week）
                if _p2_day_dim and query_params_p2.get("configureList") != query_params.get("configureList"):
                    self._log(f"  ⚠ 日粒度查询失败({_p2_err})，fallback 到周粒度 dataList 格式")
                    query_params_p2 = query_params  # 恢复原始参数
                    _p2_time_col = time_col
                    try:
                        rows, meas_cols, dim_cols = self._fetch_data(query_params_p2)
                        self._log(f"  fallback 成功: {len(rows)} 行，指标列: {meas_cols}")
                    except Exception as _p2_err2:
                        self._log(f"  ⚠ Part 2 fallback 也失败: {_p2_err2}")
                        rows = []
                else:
                    self._log(f"  ⚠ Part 2 查询失败: {_p2_err}")
                    rows = []

            try:
                if rows:
                    import pandas as pd
                    df = pd.DataFrame(rows)

                    # ── Python 层日期过滤（DA API 对周码字段的 begin/end 过滤无效）── #
                    _date_col = _p2_day_dim or time_col
                    if _cfg_start_date and _cfg_end_date and _date_col and _date_col in df.columns:
                        _before = len(df)
                        try:
                            df = df[
                                (df[_date_col].astype(str) >= _cfg_start_date) &
                                (df[_date_col].astype(str) <= _cfg_end_date)
                            ]
                            self._log(
                                f"  Python日期过滤: {_date_col} in [{_cfg_start_date}, {_cfg_end_date}]"
                                f" → {len(df)}/{_before} 行保留"
                            )
                        except Exception as _fe:
                            self._log(f"  ⚠ Python日期过滤失败: {_fe}")

                    # ── 聚焦 Part1 异常数据：按 Top LMDI 贡献的 dim-value 对过滤 ── #
                    p1_problem_dims: dict = {}  # {dim_col: [val, ...]}
                    for m in part1.get("global_top20", [])[:10]:
                        dc  = m.get("dim_col")
                        val = m.get("value")
                        if dc and val:
                            p1_problem_dims.setdefault(dc, []).append(str(val))

                    if p1_problem_dims:
                        mask = pd.Series(False, index=df.index)
                        for dc, vals in p1_problem_dims.items():
                            if dc in df.columns:
                                mask = mask | df[dc].isin(vals)
                        df_focused = df[mask] if mask.any() else df
                        self._log(
                            f"  Part2 聚焦：{len(df_focused)}/{len(df)} 行"
                            f"（Part1 Top{len(p1_problem_dims)} 异常维度：{list(p1_problem_dims)[:3]}）"
                        )
                    else:
                        df_focused = df

                    part2_stats = self._analyze_statistics(
                        df_focused, meas_cols, dim_cols, _p2_time_col,
                        focus_hint=p1_focus,
                        focus_dim_codes=list(p1_problem_dims.keys()),
                    )
                    part2_stats["focus_dims"] = {
                        "filtered_rows": len(df_focused),
                        "total_rows":    len(df),
                        "dims":          {dc: vals[:3] for dc, vals in p1_problem_dims.items()},
                    }
                else:
                    part2_stats = {"skip_reason": "数据为空"}
            except Exception as e:
                self._log(f"  ⚠ Part 2 出错: {e}")
                part2_stats = {"error": str(e)}
        yield {"part": 2, "result": part2_stats}

        try:
            p2i = self._part_interp("p2", part2_stats, prev_focuses,
                                    suppress_focus=_is_daily)
            yield {"part": "2_interp", "text": p2i["text"], "focus": p2i["focus"]}
            if p2i.get("focus") and not _is_daily:
                prev_focuses["Part2_统计量化"] = p2i["focus"]
        except Exception as e:
            self._log(f"  ⚠ Part 2 interp 出错: {e}")

        # ── Part 3 — 结构贡献度分析 ────────────────────────────────────── #
        self._log("▶ [3/6] Part 3 — 结构贡献度分析…")
        # 先生成本 Part 任务规划
        try:
            p3_plan = self._generate_part_plan("p3", prev_focuses)
            yield {"part": "3_plan", "plan": p3_plan}
        except Exception as e:
            self._log(f"  ⚠ Part 3 规划生成失败: {e}")
        part3_contrib = {}
        try:
            part3_contrib = self._analyze_structure_contribution(part1)
        except Exception as e:
            self._log(f"  ⚠ Part 3 出错: {e}")
            part3_contrib = {"error": str(e)}
        yield {"part": 3, "result": part3_contrib}

        try:
            p3i = self._part_interp("p3", part3_contrib, prev_focuses)
            yield {"part": "3_interp", "text": p3i["text"], "focus": p3i["focus"]}
            if p3i.get("focus"):
                prev_focuses["Part3_结构贡献度"] = p3i["focus"]
        except Exception as e:
            self._log(f"  ⚠ Part 3 interp 出错: {e}")

        # ── Part 4 — KG 图谱关系分析 ───────────────────────────────────── #
        self._log("▶ [4/6] Part 4 — KG 图谱关系分析…")
        # 先生成本 Part 任务规划
        try:
            p4_plan = self._generate_part_plan("p4", prev_focuses)
            yield {"part": "4_plan", "plan": p4_plan}
        except Exception as e:
            self._log(f"  ⚠ Part 4 规划生成失败: {e}")

        # 收集 Parts 1/2/3 中有问题的维度，作为 KG 分析的聚焦范围
        focus_dim_codes: set = set()
        # Part 1：LMDI Top 贡献维度
        for m in part1.get("global_top20", [])[:10]:
            if m.get("dim_col"):
                focus_dim_codes.add(m["dim_col"])
        # Part 2：统计异常维度 + 显著趋势维度
        for col in (part2_stats.get("anomaly_detection") or {}).keys():
            focus_dim_codes.add(col)
        for col in (part2_stats.get("trend") or {}).keys():
            focus_dim_codes.add(col)
        # Part 3：Pareto Top5 维度
        for dr in (part3_contrib.get("dimension_rank") or [])[:5]:
            if dr.get("dim_col"):
                focus_dim_codes.add(dr["dim_col"])
        self._log(f"  KG 聚焦维度（Parts 1/2/3 问题）：{sorted(focus_dim_codes)}")

        part4_kg = {}
        try:
            part4_kg = self._analyze_kg(meas_codes, dim_codes, part1)
            # 记录聚焦维度，供 KG 报告使用
            part4_kg["focus_dim_codes"] = sorted(focus_dim_codes)
        except Exception as e:
            self._log(f"  ⚠ Part 4 出错: {e}")
            part4_kg = {"error": str(e)}
        yield {"part": 4, "result": part4_kg}

        # Part KG_ATTR — 图谱扩展归因（仅分析聚焦维度）
        self._log("▶ [4.5/6] 图谱扩展归因分析…")
        part_kg_attr: dict = {}
        try:
            part_kg_attr = self._analyze_kg_attribution(
                part1, part4_kg, focus_dim_codes=focus_dim_codes or None
            )
        except Exception as e:
            self._log(f"  ⚠ KG 扩展归因出错: {e}")
            part_kg_attr = {"error": str(e)}
        yield {"part": "kg_attr", "result": part_kg_attr}

        # Part 4 KG AI 图谱专项报告
        self._log("▶ [4.8/6] Part 4 — 图谱 AI 分析…")
        try:
            kg_report = self._generate_kg_report(
                part1, part4_kg, part_kg_attr,
                focus_dim_codes=focus_dim_codes,
                part2_stats=part2_stats,
                part3_contrib=part3_contrib,
            )
        except Exception as e:
            self._log(f"  ⚠ KG AI 分析出错: {e}")
            kg_report = f"**图谱 AI 分析失败**\n\n{e}"
        yield {"part": "4_kg", "report": kg_report}

        # Part 4 解读 + handoff（合并 part4_kg 和 part_kg_attr 数据）
        try:
            p4_data = {**part4_kg}
            if part_kg_attr and not part_kg_attr.get("error"):
                kg_dims = part_kg_attr.get("kg_dimensions", [])
                p4_data["kg_attr_dims_count"] = len(kg_dims)
                p4_data["kg_attr_top3_dims"] = [
                    {"dim": d["cn_name"], "change_pct": d.get("total_change_pct")}
                    for d in sorted(kg_dims, key=lambda x: abs(x.get("total_change_pct") or 0), reverse=True)[:3]
                ]
            p4i = self._part_interp("p4", p4_data, prev_focuses)
            yield {"part": "4_interp", "text": p4i["text"], "focus": p4i["focus"]}
            if p4i.get("focus"):
                prev_focuses["Part4_KG图谱"] = p4i["focus"]
        except Exception as e:
            self._log(f"  ⚠ Part 4 interp 出错: {e}")

        # ── Part 5 — 归因下钻分析 ──────────────────────────────────────── #
        self._log("▶ [5/6] Part 5 — 归因下钻分析…")
        # 先生成本 Part 任务规划
        try:
            p5_plan = self._generate_part_plan("p5", prev_focuses)
            yield {"part": "5_plan", "plan": p5_plan}
        except Exception as e:
            self._log(f"  ⚠ Part 5 规划生成失败: {e}")
        part5_drill = {}
        try:
            part5_drill = self._analyze_drill_down(part1, part4_kg, query_params=query_params)
        except Exception as e:
            self._log(f"  ⚠ Part 5 出错: {e}")
            part5_drill = {"error": str(e)}
        yield {"part": 5, "result": part5_drill}

        try:
            p5i = self._part_interp("p5", part5_drill, prev_focuses)
            yield {"part": "5_interp", "text": p5i["text"], "focus": p5i["focus"]}
            if p5i.get("focus"):
                prev_focuses["Part5_归因下钻"] = p5i["focus"]
        except Exception as e:
            self._log(f"  ⚠ Part 5 interp 出错: {e}")

        # ── Part 6 — 综合报告 ──────────────────────────────────────────── #
        self._log("▶ [6/6] Part 6 — 综合报告…")
        # 先生成本 Part 任务规划
        try:
            p6_plan = self._generate_part_plan("p6", prev_focuses)
            yield {"part": "6_plan", "plan": p6_plan}
        except Exception as e:
            self._log(f"  ⚠ Part 6 规划生成失败: {e}")
        meta = {
            "meas_codes": meas_codes,
            "dim_codes": dim_codes,
            "row_count": len(rows),
            "time_col": time_col,
        }
        try:
            report = self._generate_report(part1, part4_kg, part2_stats, meta, part5_drill,
                                            part_kg_attr, prev_focuses)
        except Exception as e:
            self._log(f"  ⚠ Part 6 出错: {e}")
            report = f"**报告生成失败**\n\n{e}"
        yield {"report": report}

        # Part 6 独立解读（基于全量）
        try:
            p6_data = {
                "handoff_chain": prev_focuses,
                "指标波动摘要": [
                    {"col": m["col"], "cn": m.get("cn_name", m["col"]),
                     "current": m.get("current"), "previous": m.get("previous"),
                     "change_pct": m.get("change_pct")}
                    for m in part1.get("measures", [])
                ],
                "LMDI_top5": part1.get("global_top20", [])[:5],
                "统计异常top3": sorted(
                    part2_stats.get("anomaly_detection", {}).values(),
                    key=lambda x: abs(x.get("z_score") or 0), reverse=True
                )[:3] if isinstance(part2_stats.get("anomaly_detection"), dict) else [],
            }
            p6i = self._part_interp("p6", p6_data, prev_focuses)
            yield {"part": "6_interp", "text": p6i["text"], "focus": ""}
        except Exception as e:
            self._log(f"  ⚠ Part 6 interp 出错: {e}")

        self._log("═══ 分析完成 ═══")

    # ── 数据获取 ────────────────────────────────────────────────────────── #

    def _fetch_data(self, query_params: dict, page_size: int = 500):
        """
        调用 dataAgent，将 cellList 转换为 [{col: val, ...}] 格式。
        cellList 每行是 cell 对象列表，每个 cell 有 code/name/data/type 字段。
        自动翻页直到取完所有数据（上限 10 页）。
        注意：DA API 的 hasNextPage 字段不可信（总是返回 False），
              因此忽略该字段，只在返回行数为 0 时停止翻页。
        返回 (rows, meas_cols, dim_cols)
        """
        meas_cols: list[str] = []
        dim_cols: list[str] = []
        rows = []
        seen_rows: set = set()   # 用于去重（DA API 多页可能返回重复行）
        max_pages = 10

        for page_num in range(1, max_pages + 1):
            # 直接使用 configureList（已包含 MEAS_/DIM_ 前缀）
            cfg_list = query_params.get("configureList", [])
            payload = {**query_params, "configureList": cfg_list, "pageSize": page_size, "pageNum": page_num}
            body = json.dumps(payload).encode("utf-8")
            req = _ureq.Request(
                self._data_agent_url,
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with _ureq.urlopen(req, timeout=30) as resp:
                resp_data = json.loads(resp.read().decode("utf-8"))

            if resp_data.get("code") != 200:
                raise RuntimeError(
                    f"dataAgent 返回错误: {resp_data.get('message', resp_data)}"
                    f"\n  请求configureList: {[c.get('code') for c in payload.get('configureList', [])]}"
                    f"\n  filterList: {json.dumps(payload.get('filterList', []), ensure_ascii=False)}"
                )

            data = resp_data.get("data", {})
            cell_list = data.get("cellList", [])

            if not cell_list:
                break

            new_rows = 0
            for cell_row in cell_list:
                row: dict = {}
                for cell in cell_row:
                    code = cell.get("code", "")
                    raw  = cell.get("data", "")
                    # 数值列去掉千位逗号再转 float
                    if cell.get("type") == "MEASURE":
                        try:
                            val = float(str(raw).replace(",", "")) if raw not in (None, "", "--", "-") else None
                        except ValueError:
                            val = None
                        col_key = code
                        if col_key not in meas_cols:
                            meas_cols.append(col_key)
                    else:
                        val = str(raw) if raw not in (None, "") else None
                        col_key = code
                        if col_key not in dim_cols:
                            dim_cols.append(col_key)
                    row[col_key] = val

                # 用 frozenset 去重：避免 DA API 多页返回相同行
                row_key = frozenset((k, str(v)) for k, v in row.items())
                if row_key not in seen_rows:
                    seen_rows.add(row_key)
                    rows.append(row)
                    new_rows += 1

            # 本页全部是重复行 → 说明 DA API 已无新数据，提前退出
            if new_rows == 0:
                break

        return rows, meas_cols, dim_cols

    def _detect_time_dim(self, df, dim_cols: list) -> Optional[str]:
        """识别时间维度列（列名含年/月/日/季/周等关键词）。df 参数保留兼容，不再使用。"""
        time_keywords = ["年", "月", "日", "季", "周", "year", "month", "day", "quarter", "week",
                         "date", "time", "period", "_date", "_year", "_month", "_week", "_season"]
        for col in dim_cols:
            col_lower = col.lower()
            for kw in time_keywords:
                if kw in col_lower:
                    return col
        return None

    # ── Part 1: 波动分析 ────────────────────────────────────────────────── #

    def _analyze_fluctuations(self, query_params: dict, meas_codes: list, dim_codes: list, time_col: Optional[str], p2_day_dim: Optional[str] = None) -> dict:
        """
        波动分析（环比），分三步精准查询：
          Step 1: 「时间 + 指标」→ 准确的当期/上期指标值
          Step 1b: 对复合指标，额外查各算子（叶节点原子指标）的当期/上期波动
          Step 2: 对每个非时间维度，「时间 + 该维度 + 指标 [+ 分母指标]」查询，
                  用 LMDI 对数迪氏指数分解各维度子值的贡献度
        """
        import pandas as pd

        result: dict = {}
        cfg_list  = query_params.get("configureList", [])
        cfg_by_code = {c["code"]: c for c in cfg_list}
        ttl_meta  = self._ttl_meta()

        # ── 无时间维度：降级为维度分布分析 ───────────────────────────────── #
        if not time_col:
            try:
                rows, meas_cols, dim_cols = self._fetch_data(query_params)
            except Exception as e:
                return {"error": f"数据获取失败: {e}"}
            if not rows:
                return {"skip_reason": "数据为空"}
            df = pd.DataFrame(rows)
            result["skip_reason"] = "无时间维度，改为维度分布分析"
            return self._dim_distribution(df, meas_cols, dim_cols, result)

        # ─────────────────────────────────────────────────────────────────── #
        # Step 1: 时间 + 指标 → 确定当期/上期及准确指标值
        # ─────────────────────────────────────────────────────────────────── #
        time_meas_cfg = (
            [cfg_by_code.get(time_col, {"code": time_col})]
            + [cfg_by_code.get(m, {"code": m}) for m in meas_codes]
        )
        try:
            rows, meas_cols, _ = self._fetch_data({**query_params, "configureList": time_meas_cfg})
        except Exception as e:
            return {"error": f"时间+指标查询失败: {e}"}

        if not rows:
            return {"skip_reason": "数据为空"}

        df_time   = pd.DataFrame(rows)
        # 日期列标准化：截取前10字符得到 YYYY-MM-DD
        if time_col in df_time.columns:
            df_time[time_col] = df_time[time_col].astype(str).str.strip().str[:10]
        df_sorted = df_time.sort_values(by=time_col, ascending=True)
        periods   = df_sorted[time_col].dropna().unique().tolist()

        # 排除今日（当日数据往往未完整入库）：若最新期等于今天则去掉
        import datetime as _dt
        _today_str = _dt.date.today().isoformat()          # e.g. "2026-04-29"
        periods = [p for p in periods if str(p) != _today_str]

        if len(periods) < 2:
            result["skip_reason"] = "排除今日后时间期数少于 2，无法做环比分析"
            return result

        current_period  = str(periods[-1])
        previous_period = str(periods[-2])
        result["time_dim"]        = time_col
        result["current_period"]  = current_period
        result["previous_period"] = previous_period
        self._log(f"  当期: {current_period}，上期: {previous_period}")

        # ── 保留原始时间 filter，不做窄替换 ──
        # 原始 filter 使用日期值（如 "2026-03-02"），而数据库维度值是整数代理键
        # （如 d_week_seq=107430），替换后 da 无法正确处理。
        # 前端用户选的时间范围通常已覆盖当期+上期，直接复用即可。
        query_params_narrow = query_params
        self._log(f"  当期: {current_period}，上期: {previous_period}")

        df_curr = df_sorted[df_sorted[time_col].astype(str) == current_period]
        df_prev = df_sorted[df_sorted[time_col].astype(str) == previous_period]

        measures_list = []
        for col in meas_cols:
            curr_val  = _safe_float(df_curr[col].sum(min_count=1))
            prev_val  = _safe_float(df_prev[col].sum(min_count=1))
            change    = (curr_val - prev_val) if (curr_val is not None and prev_val is not None) else None
            chg_pct   = _pct_change(curr_val, prev_val)
            direction = "up" if (change and change > 0) else ("down" if (change and change < 0) else "flat")
            measures_list.append({
                "col":        col,
                "cn_name":    ttl_meta.get(col, {}).get("cn_name", col),
                "current":    curr_val,
                "previous":   prev_val,
                "change":     round(change, 4) if change is not None else None,
                "change_pct": chg_pct,
                "direction":  direction,
            })
        result["measures"] = measures_list

        # ─────────────────────────────────────────────────────────────────── #
        # Step 1b: 复合指标 → 各算子（叶节点）波动
        # ─────────────────────────────────────────────────────────────────── #
        operand_fluctuations: dict = {}   # {meas_code: {col, current, previous, change, ...}}

        # 叶节点查询专用参数：统一使用 query_params_narrow（DIM_submit_date_week + dataList 双期）
        # 这与维度贡献查询使用相同的 filterList 格式，DA API 均支持。
        # 注意：不能用 DIM_submit_date_day begin/end 做 filter，因为 configureList 里是
        # DIM_submit_date_week，两列不匹配 DA API 会直接返回"查询失败"。
        query_params_leaf = query_params_narrow

        for meas_code in meas_codes:
            info = ttl_meta.get(meas_code, {})
            if info.get("type", 0) == 0:
                continue    # 原子指标，无算子

            leaves = self._leaf_operands(meas_code)
            if not leaves:
                continue

            # 只查尚未查过的叶节点
            new_leaves = [lc for lc in leaves if lc not in operand_fluctuations]
            if not new_leaves:
                continue

            # 构造叶节点查询：只含时间维度 + 叶节点指标
            # 注意：不含业务维度，因为 DA API 在「多业务维度 + 多原子指标」组合时会
            # 返回"查询失败"，必须简化 configureList 以保证算子查询成功。
            self._log(f"  算子列表 ({meas_code}): {new_leaves}")
            leaf_cfg = (
                [cfg_by_code.get(time_col, {"code": time_col})]
                + [{"code": lc} for lc in new_leaves]
            )
            self._log(f"  算子查询 configureList: {[c['code'] for c in leaf_cfg]}")
            try:
                leaf_rows, leaf_cols, _ = self._fetch_data({**query_params_leaf, "configureList": leaf_cfg})
            except Exception as e:
                self._log(f"  ⚠ 算子查询失败 ({meas_code}): {e}")
                continue

            if not leaf_rows:
                self._log(f"  ⚠ 算子查询返回空数据 ({meas_code})，算子列: {new_leaves}")
                continue

            df_leaf   = pd.DataFrame(leaf_rows)
            self._log(f"  算子查询返回 {len(df_leaf)} 行，列: {list(df_leaf.columns)}")

            # 日期列标准化：截取前10字符得到 YYYY-MM-DD，防止 API 返回时间戳格式
            if time_col in df_leaf.columns:
                df_leaf[time_col] = df_leaf[time_col].astype(str).str.strip().str[:10]
            else:
                self._log(f"  ⚠ 算子查询结果中无时间列 {time_col}，可用列: {list(df_leaf.columns)}")
                continue

            sample_dates = df_leaf[time_col].dropna().unique().tolist()
            self._log(f"  算子查询日期样本: {sample_dates[:5]}，当期={current_period} 上期={previous_period}")

            # 无论日粒度还是周粒度，API 返回的时间列值与 period 字符串一致
            # 周粒度：API 始终返回 YYYYWW 周码（即使用 begin/end 过滤），直接比对即可
            df_lc = df_leaf[df_leaf[time_col] == str(current_period)[:10]]
            df_lp = df_leaf[df_leaf[time_col] == str(previous_period)[:10]]
            self._log(f"  当期匹配行数: {len(df_lc)}，上期匹配行数: {len(df_lp)}")

            for lc in leaf_cols:
                cv = _safe_float(df_lc[lc].sum(min_count=1)) if lc in df_lc.columns else None
                pv = _safe_float(df_lp[lc].sum(min_count=1)) if lc in df_lp.columns else None
                ch = (cv - pv) if (cv is not None and pv is not None) else None
                operand_fluctuations[lc] = {
                    "col":        lc,
                    "cn_name":    ttl_meta.get(lc, {}).get("cn_name", lc),
                    "parent":     meas_code,
                    "parent_cn":  ttl_meta.get(meas_code, {}).get("cn_name", meas_code),
                    "current":    cv,
                    "previous":   pv,
                    "change":     round(ch, 4) if ch is not None else None,
                    "change_pct": _pct_change(cv, pv),
                    "direction":  "up" if (ch and ch > 0) else ("down" if (ch and ch < 0) else "flat"),
                }

        if operand_fluctuations:
            # ── Bennet 算术均值分解法计算各算子贡献度 ──────────────────────── #
            # 对比率型指标 NSS = Numerator(xi) / Denominator(N)：
            #   contribution(xi ∈ 分子) = wi × Δxi / N_avg
            #   contribution(N  ∈ 分母) = −NSS_avg × ΔN / N_avg
            # 归一化：scaled_i = raw_i / Σraw_i × |ΔNSS|（消除一阶近似误差）
            # 参考：Bennet(1920) 算术指数；Diewert(1995) TFP 分解
            _opd_vals = list(operand_fluctuations.values())

            # 收集所有不同的父指标
            _parent_codes = list(dict.fromkeys(o["parent"] for o in _opd_vals))
            _contrib_raw: dict = {}   # {leaf_code: raw_contribution}

            for _par_code in _parent_codes:
                _par_info = ttl_meta.get(_par_code, {})
                _denom_code = _par_info.get("denominator")   # 分母指标 code

                # 找到主指标（measures_list）中与此 parent 对应的当期/上期值
                _meas_entry = next((m for m in measures_list if m["col"] == _par_code), None)

                if _denom_code and _meas_entry:
                    # ── Bennet 分解（比率型指标）──────────────────────────── #
                    _NSS_curr = _meas_entry.get("current")
                    _NSS_prev = _meas_entry.get("previous")
                    _denom_opd = operand_fluctuations.get(_denom_code, {})
                    _N_curr = _denom_opd.get("current")
                    _N_prev = _denom_opd.get("previous")

                    if all(v is not None for v in [_NSS_curr, _NSS_prev, _N_curr, _N_prev]) \
                            and (_N_curr + _N_prev) > 0:
                        _NSS_avg = (_NSS_curr + _NSS_prev) / 2.0
                        _N_avg   = (_N_curr   + _N_prev)   / 2.0

                        # 分子叶节点系数：对分子部分的每个直接子指标递归提取
                        # （不对整个 ratio 调用，避免 A/B 中 B 覆盖 A 的问题）
                        _numerator_ops = [op for op in _par_info.get("operands", [])
                                          if op != _denom_code]
                        _num_coeffs: dict = {}
                        for _num_op in _numerator_ops:
                            for lf, cf in self._leaf_num_coefficients(_num_op).items():
                                _num_coeffs[lf] = _num_coeffs.get(lf, 0.0) + cf

                        for _opd in _opd_vals:
                            lc = _opd["col"]
                            dx = _opd.get("change")
                            if dx is None:
                                continue
                            if lc == _denom_code:
                                # 分母项：∂NSS/∂N = -NSS/N
                                _contrib_raw[lc] = -_NSS_avg / _N_avg * dx
                            elif lc in _num_coeffs:
                                # 分子项：∂NSS/∂xi = wi/N
                                _contrib_raw[lc] = _num_coeffs[lc] / _N_avg * dx
                        continue  # 已处理，跳过下面的兜底

                # ── 兜底：无法 Bennet 分解，用绝对变化量比例 ─────────────── #
                _par_leaves = [o for o in _opd_vals if o["parent"] == _par_code]
                _abs_sum = sum(abs(o.get("change") or 0) for o in _par_leaves) or 1.0
                _delta_par = (_meas_entry.get("change") if _meas_entry else None) or _abs_sum
                for _opd in _par_leaves:
                    dx = _opd.get("change")
                    if dx is not None:
                        _contrib_raw[_opd["col"]] = dx / _abs_sum * abs(_delta_par) * (1 if dx >= 0 else -1)

            # 归一化：按绝对贡献之和归一化，结果恒在 [-100, +100] 之间
            # 避免 ΔNSS ≈ 0 时除以极小数导致数值爆炸
            # contrib_pct 含义：该算子贡献占所有算子「总活动量」的百分比（带符号）
            _abs_sum_raw = sum(abs(v) for v in _contrib_raw.values()) or 1.0

            for _opd in _opd_vals:
                lc = _opd["col"]
                raw = _contrib_raw.get(lc)
                if raw is None:
                    _opd["contrib_pct"] = None
                    continue
                _opd["contrib_pct"] = round(raw / _abs_sum_raw * 100, 1)

            # 按贡献度升序（负向贡献最大的在前）
            _opd_vals.sort(key=lambda x: (x.get("contrib_pct") if x.get("contrib_pct") is not None else 0))
            # 更新 dict 保持引用
            operand_fluctuations = {o["col"]: o for o in _opd_vals}
            result["operand_fluctuations"] = _opd_vals

        # ─────────────────────────────────────────────────────────────────── #
        # Step 1c: 对变化较大的算子，逐维度拆解（找到变化发生在哪个维度值）
        # ─────────────────────────────────────────────────────────────────── #
        # 选取与主指标同方向、贡献度绝对值最大的前3个算子做维度展开
        ttl_all_dims_for_opd = self._ttl_dims()
        _main_direction = (measures_list[0]["direction"] if measures_list else "flat")
        _opd_with_contrib = [v for v in operand_fluctuations.values() if v.get("contrib_pct") is not None]

        if _opd_with_contrib:
            if _main_direction == "down":
                # 主指标下降：选负向贡献算子（contrib_pct < 0）
                _same_dir = [v for v in _opd_with_contrib if (v["contrib_pct"] or 0) < 0]
            elif _main_direction == "up":
                # 主指标上升：选正向贡献算子（contrib_pct > 0）
                _same_dir = [v for v in _opd_with_contrib if (v["contrib_pct"] or 0) > 0]
            else:
                _same_dir = _opd_with_contrib
            if not _same_dir:
                _same_dir = _opd_with_contrib   # 无同方向时回退到全部
            sig_operands = sorted(_same_dir, key=lambda x: abs(x["contrib_pct"] or 0), reverse=True)[:3]
        else:
            # 回退：按 |change_pct| 降序
            sig_operands = sorted(
                [v for v in operand_fluctuations.values() if v.get("change_pct") is not None],
                key=lambda x: abs(x["change_pct"] or 0), reverse=True
            )[:3]

        operand_dim_breakdown: dict = {}   # {operand_code: [{dim, top_movers}]}
        for opd_info in sig_operands:
            opd_code = opd_info["col"]
            opd_dims = []
            for dim in [d for d in ttl_all_dims_for_opd if d != time_col]:
                # 只含时间维度 + 当前分析维度 + 算子指标，不附加其他业务维度
                # 多维度组合会导致 DA API 返回"查询失败"
                opd_dim_cfg = (
                    [cfg_by_code.get(time_col, {"code": time_col}),
                     cfg_by_code.get(dim,      {"code": dim})]
                    + [{"code": opd_code}]
                )
                try:
                    opd_rows, opd_mcols, _ = self._fetch_data({**query_params_leaf, "configureList": opd_dim_cfg})
                except Exception as e:
                    self._log(f"  ⚠ 算子维度查询失败 {opd_code}/{dim}: {e}")
                    continue
                if not opd_rows or opd_code not in [c for c in pd.DataFrame(opd_rows).columns]:
                    continue

                df_od = pd.DataFrame(opd_rows)
                if time_col in df_od.columns:
                    df_od[time_col] = df_od[time_col].astype(str).str.strip().str[:10]
                if dim not in df_od.columns or opd_code not in df_od.columns:
                    continue

                df_odc = df_od[df_od[time_col] == str(current_period)[:10]]
                df_odp = df_od[df_od[time_col] == str(previous_period)[:10]]

                grp_c = {str(k): (_safe_float(v) or 0.0) for k, v in df_odc.groupby(dim)[opd_code].sum().items()}
                grp_p = {str(k): (_safe_float(v) or 0.0) for k, v in df_odp.groupby(dim)[opd_code].sum().items()}
                all_vals_opd = set(grp_c) | set(grp_p)

                total_c = sum(grp_c.values()) or 1.0
                total_p = sum(grp_p.values()) or 1.0

                movers_opd = []
                for val in all_vals_opd:
                    cv2 = grp_c.get(val, 0.0)
                    pv2 = grp_p.get(val, 0.0)
                    movers_opd.append({
                        "value":         val,
                        "current":       round(cv2, 2),
                        "previous":      round(pv2, 2),
                        "change":        round(cv2 - pv2, 2),
                        "change_pct":    _pct_change(cv2, pv2),
                        "current_share": round(cv2 / total_c, 4),
                        "prev_share":    round(pv2 / total_p, 4),
                        "share_change":  round(cv2 / total_c - pv2 / total_p, 4),
                    })
                movers_opd.sort(key=lambda x: abs(x["change"]), reverse=True)
                opd_dims.append({
                    "dim_col":   dim,
                    "dim_cn":    ttl_all_dims_for_opd.get(dim, dim),
                    "top_movers": movers_opd[:5],
                })
            if opd_dims:
                operand_dim_breakdown[opd_code] = opd_dims

        if operand_dim_breakdown:
            result["operand_dim_breakdown"] = operand_dim_breakdown

        # ─────────────────────────────────────────────────────────────────── #
        # Step 2: 维度贡献度（LMDI 对数迪氏指数）
        # ─────────────────────────────────────────────────────────────────── #
        # 对所有 TTL 维度（排除时间维度）做贡献度分析，最终取全局 top 3
        ttl_all_dims = self._ttl_dims()          # {code: cn_name}
        all_non_time_dims = [d for d in ttl_all_dims if d != time_col]
        contrib = []
        all_movers_flat = []   # 跨维度全量 movers，用于全局 top 3

        for dim in all_non_time_dims:
            self._log(f"  → 维度贡献查询: {dim}")

            for meas_code in meas_codes:
                info        = ttl_meta.get(meas_code, {})
                denom_code  = info.get("denominator")   # 分母指标 code（用于体量权重）

                # 基础配置：时间 + 维度 + 复合指标
                dim_cfg = (
                    [cfg_by_code.get(time_col, {"code": time_col}),
                     cfg_by_code.get(dim,      {"code": dim})]
                    + [cfg_by_code.get(meas_code, {"code": meas_code})]
                )
                # 若有分母指标，追加（用于计算体量权重 W_i）
                if denom_code and denom_code != meas_code:
                    dim_cfg.append({"code": denom_code})

                try:
                    # 维度贡献查询使用 query_params_narrow（dataList+internal 周码过滤），
                    # 而非 query_params_leaf（日维度过滤），因为 MEAS_nss 等复合指标
                    # 支持 dataList 格式，但不一定支持 DIM_submit_date_day 过滤。
                    dim_rows, dim_meas_cols, _ = self._fetch_data({**query_params_narrow, "configureList": dim_cfg})
                except Exception as e:
                    self._log(f"  ⚠ 维度 {dim}/{meas_code} 查询失败: {e}")
                    continue

                if not dim_rows:
                    self._log(f"  ⚠ 维度 {dim}/{meas_code} 查询返回空")
                    continue

                df_dim = pd.DataFrame(dim_rows)
                self._log(f"  dim={dim}: 共{len(df_dim)}行, cols={list(df_dim.columns)}, 时间样本={df_dim[time_col].astype(str).str[:10].unique()[:3].tolist() if time_col in df_dim.columns else '无时间列'}")
                if time_col not in df_dim.columns or dim not in df_dim.columns:
                    self._log(f"  ⚠ 缺少必要列: time_col={time_col in df_dim.columns}, dim={dim in df_dim.columns}")
                    continue

                # 日期列标准化
                df_dim[time_col] = df_dim[time_col].astype(str).str.strip().str[:10]

                df_dc = df_dim[df_dim[time_col] == str(current_period)[:10]]
                df_dp = df_dim[df_dim[time_col] == str(previous_period)[:10]]
                self._log(f"  dim={dim}: df_dc={len(df_dc)}行, df_dp={len(df_dp)}行, denom_col={denom_code}, denom_in_cols={denom_code in df_dim.columns if denom_code else 'N/A'}")

                if meas_code not in df_dim.columns:
                    self._log(f"  ⚠ meas_code {meas_code} 不在结果列中")
                    continue

                # 各维度子值的指标值
                y_curr_by_val = {str(k): (_safe_float(v) or 0.0)
                                 for k, v in df_dc.groupby(dim)[meas_code].sum().items()}
                y_prev_by_val = {str(k): (_safe_float(v) or 0.0)
                                 for k, v in df_dp.groupby(dim)[meas_code].sum().items()}

                all_vals = set(y_curr_by_val) | set(y_prev_by_val)

                # 体量权重 W_i（有分母指标时用分母，否则用比例 1/n 平分）
                def _volume_weights(df_period, dcode):
                    if dcode and dcode in df_period.columns:
                        vol = {str(k): (_safe_float(v) or 0.0)
                               for k, v in df_period.groupby(dim)[dcode].sum().items()}
                        total_vol = sum(vol.values())
                        self._log(f"    _volume_weights: dcode={dcode}, n_vals={len(vol)}, total_vol={total_vol:.2f}")
                        if total_vol > 0:   # 有效数据才用体量权重
                            return {k: v / total_vol for k, v in vol.items()}
                        # total_vol==0：说明分母指标未返回有效数据，降级为等权
                    n = len(all_vals) or 1
                    self._log(f"    _volume_weights: 降级等权 n={n}")
                    return {k: 1.0 / n for k in all_vals}

                w_curr = _volume_weights(df_dc, denom_code)
                w_prev = _volume_weights(df_dp, denom_code)

                # 加权贡献 v_i = Y_i × W_i
                v_curr = {k: y_curr_by_val.get(k, 0.0) * w_curr.get(k, 0.0) for k in all_vals}
                v_prev = {k: y_prev_by_val.get(k, 0.0) * w_prev.get(k, 0.0) for k in all_vals}

                # LMDI 加法分解
                lmdi_contribs = _lmdi_additive(v_curr, v_prev)
                total_change  = sum(lmdi_contribs.values())

                movers = []
                for val in all_vals:
                    yc = y_curr_by_val.get(val, 0.0)
                    yp = y_prev_by_val.get(val, 0.0)
                    wc = w_curr.get(val, 0.0)
                    wp = w_prev.get(val, 0.0)
                    lc = lmdi_contribs.get(val, 0.0)
                    mover = {
                        "dim_col":         dim,
                        "dim_cn":          ttl_all_dims.get(dim, dim),
                        "value":           val,
                        "meas_col":        meas_code,
                        "meas_cn":         ttl_meta.get(meas_code, {}).get("cn_name", meas_code),
                        "current_val":     round(yc, 4),
                        "prev_val":        round(yp, 4),
                        "current_weight":  round(wc, 4),
                        "prev_weight":     round(wp, 4),
                        "lmdi_contrib":    round(lc, 6),
                        "contrib_pct":     round(lc / total_change * 100, 2) if total_change else None,
                    }
                    movers.append(mover)
                    all_movers_flat.append(mover)
                movers.sort(key=lambda x: abs(x["lmdi_contrib"]), reverse=True)
                contrib.append({
                    "dim_col":      dim,
                    "meas_col":     meas_code,
                    "meas_cn":      ttl_meta.get(meas_code, {}).get("cn_name", meas_code),
                    "denom_col":    denom_code,
                    "total_change": round(total_change, 6),
                    "method":       "LMDI" if denom_code else "LMDI(等权)",
                    "top_movers":   movers,
                })

        # 全局 top 20：按 |lmdi_contrib| 降序，取前 20
        all_movers_flat.sort(key=lambda x: abs(x["lmdi_contrib"]), reverse=True)
        result["global_top20"]      = all_movers_flat[:20]
        result["dimension_contrib"] = contrib
        return result

    def _dim_distribution(self, df, meas_cols: list, dim_cols: list, result: dict) -> dict:
        """无时间维度时做维度分布分析。"""
        distribution = []
        for dim in dim_cols:
            if dim not in df.columns:
                continue
            for meas_col in meas_cols:
                if meas_col not in df.columns:
                    continue
                total = _safe_float(df[meas_col].sum()) or 0
                by_dim = df.groupby(dim)[meas_col].sum()
                top5 = by_dim.sort_values(ascending=False).head(5)
                distribution.append({
                    "dim_col": dim,
                    "meas_col": meas_col,
                    "top5": [
                        {"value": str(k), "amount": _safe_float(v), "share": round(_safe_float(v) / total, 4) if total else 0}
                        for k, v in top5.items()
                    ],
                })
        result["dimension_distribution"] = distribution
        return result

    # ── Part 2: 图谱关系分析 ────────────────────────────────────────────── #

    def _analyze_kg(self, meas_codes: list, dim_codes: list, part1: dict = None) -> dict:
        from rdflib import Graph, Namespace, RDF, Literal

        ttl_path = Path(self._ttl_path)
        if not ttl_path.exists():
            return {"error": f"TTL 文件不存在: {ttl_path}"}

        g = Graph()
        g.parse(str(ttl_path), format="turtle")

        IND = Namespace("http://indicator.insightmind.com/ontology#")

        def _val(uri, prop):
            v = g.value(uri, prop)
            return str(v) if v else None

        def _find_inst(code: str):
            for s, _, _ in g.triples((None, IND.code, Literal(code))):
                return s
            return None

        # ── 预构建：code → cnName 映射（用于子指标/兄弟指标名称查找）─── #
        code_to_name: dict[str, str] = {}
        for inst in g.subjects(RDF.type, IND.Measure):
            c = _val(inst, IND.code)
            n = _val(inst, IND.cnName)
            if c:
                code_to_name[c] = n or c

        # ── 预构建：业务分类 URI → name ──────────────────────────────── #
        cat_name: dict[str, str] = {}
        cat_parent: dict[str, str] = {}
        for cat in g.subjects(RDF.type, IND.Category):
            cat_name[str(cat)] = _val(cat, IND.name) or str(cat).rsplit("/", 1)[-1]
            p = g.value(cat, IND.categoryParent)
            if p:
                cat_parent[str(cat)] = str(p)

        def _cat_path(cat_uri: str) -> str:
            parts = []
            cur = cat_uri
            for _ in range(5):
                parts.insert(0, cat_name.get(cur, cur.rsplit("/", 1)[-1]))
                if cur in cat_parent:
                    cur = cat_parent[cur]
                else:
                    break
            return " / ".join(parts)

        # ── 预构建：viewTypeCode → 粒度描述 ─────────────────────────── #
        view_type_name = {"1": "日", "2": "周", "3": "月", "4": "季", "5": "年"}

        result: dict = {"measures": [], "dimensions": [], "siblings": [], "category_tree": {}}

        # 收集所有涉及的事实表 URI（用于兄弟指标）
        fact_tbl_uris: set = set()

        # ──────────────────── 指标分析 ───────────────────────────────── #
        for mc in meas_codes:
            inst = _find_inst(mc)
            if inst is None:
                result["measures"].append({"code": mc, "error": "未在 TTL 中找到"})
                continue

            cn_name    = _val(inst, IND.cnName)
            meas_type  = _val(inst, IND.measTypeCode)
            caliber    = _val(inst, IND.caliber)
            definition = _val(inst, IND.definition)
            unit       = _val(inst, IND.unit)

            # 业务分类
            cat_uri  = g.value(inst, IND.belongsToCategory)
            category = _cat_path(str(cat_uri)) if cat_uri else None

            # MeasureApp 详情（可能有多个）
            mapp_list = []
            for mapp in g.objects(inst, IND.hasMeasureApp):
                apply_type    = _val(mapp, IND.applyTypeCode)
                fact_col      = _val(mapp, IND.factColumn)
                expression    = _val(mapp, IND.expression)
                where_cond    = _val(mapp, IND.whereCondition)
                has_col_dt    = _val(mapp, IND.hasColumnDT)
                tbl           = g.value(mapp, IND.appliesToTable)
                tbl_info      = {}
                conn_info     = {}
                if tbl:
                    fact_tbl_uris.add(tbl)
                    tbl_info = {
                        "tableName": _val(tbl, IND.tableName),
                        "cnName":    _val(tbl, IND.cnName),
                        "description": _val(tbl, IND.description),
                        "schema":    _val(tbl, IND.schemaName),
                    }
                    conn = g.value(tbl, IND.hasConnection)
                    if conn:
                        conn_info = {
                            "dbType": _val(conn, IND.dbType),
                            "host":   _val(conn, IND.host),
                            "port":   _val(conn, IND.port),
                            "dbName": _val(conn, IND.dbName),
                        }
                # 自然维度映射（NaturalDimMapping）
                ndm_list = []
                for ndm in g.objects(mapp, IND.hasNaturalDimMapping):
                    ndm_list.append({
                        "hierarchyCode": _val(ndm, IND.naturalHierarchyCode),
                        "physicalColumn": _val(ndm, IND.physicalColumn),
                    })

                # 解析聚合算子
                agg_op = None
                if expression and expression.strip().startswith("["):
                    try:
                        ops = json.loads(expression)
                        for op in ops:
                            if op.get("operatingType") == "operator":
                                agg_op = op.get("operator")
                                break
                    except Exception:
                        pass

                mapp_list.append({
                    "applyType":    {"0": "原子", "1": "衍生", "2": "派生"}.get(str(apply_type) if apply_type else "", apply_type),
                    "factColumn":   fact_col,
                    "aggOperator":  agg_op,
                    "whereCondition": where_cond,
                    "hasDateColumn": has_col_dt in ("true", "1", "True"),
                    "table":         tbl_info,
                    "connection":    conn_info,
                    "naturalDims":   ndm_list,
                })

            # 表达式树（衍生指标：操作数带名称）
            expr_tree = None
            # 取第一个 mapp 的 expression
            raw_expr = _val(list(g.objects(inst, IND.hasMeasureApp))[0] if list(g.objects(inst, IND.hasMeasureApp)) else inst, IND.expression) if list(g.objects(inst, IND.hasMeasureApp)) else None
            if raw_expr and raw_expr.strip().startswith("["):
                try:
                    ops = json.loads(raw_expr)
                    expr_tree = []
                    for op in ops:
                        if op.get("operatingType") == "operand":
                            sub_code = op.get("operand", {}).get("measCode")
                            expr_tree.append({
                                "type":    "operand",
                                "measCode": sub_code,
                                "cnName":  code_to_name.get(sub_code, sub_code) if sub_code else None,
                            })
                        elif op.get("operatingType") == "operator":
                            expr_tree.append({"type": "operator", "operator": op.get("operator")})
                except Exception:
                    pass

            type_name = {"0": "原子指标", "1": "衍生指标", "2": "派生指标"}.get(
                str(int(float(meas_type))) if meas_type else "", "未知")

            result["measures"].append({
                "code":        mc,
                "cnName":      cn_name,
                "unit":        unit,
                "type":        type_name,
                "category":    category,
                "caliber":     caliber,
                "definition":  definition,
                "measureApps": mapp_list,
                "expressionTree": expr_tree,
            })

        # ──────────────────── 维度分析（所有 TTL 维度，不限于已选）──── #
        # 遍历 TTL 中所有 Dimension，而非仅 dim_codes，保证图谱关系分析覆盖全量维度
        dim_codes_set = set(dim_codes)
        all_dim_uris = list(g.subjects(RDF.type, IND.Dimension))
        for inst in all_dim_uris:
            dc = _val(inst, IND.code)
            if not dc:
                continue

            cn_name     = _val(inst, IND.cnName)
            dim_type    = _val(inst, IND.dimTypeCode)
            view_type   = _val(inst, IND.viewTypeCode)
            hier_code   = _val(inst, IND.hierarchyCode)
            level_code  = _val(inst, IND.levelCode)
            definition  = _val(inst, IND.definition)
            is_hyper    = _val(inst, IND.isHyper)

            type_name = {"0": "退化维（直接取事实表列）", "1": "层级维", "2": "有维表（需 JOIN）"}.get(
                str(int(float(dim_type))) if dim_type else "", "未知")
            view_name = view_type_name.get(str(view_type) if view_type else "", None)

            # DimensionApp 详情
            dapp_list = []
            for dapp in g.objects(inst, IND.hasDimApp):
                fact_tbl    = g.value(dapp, IND.dimFactTable)
                dim_tbl     = g.value(dapp, IND.dimTable)
                dapp_entry  = {
                    "dimFactColumn":    _val(dapp, IND.dimFactColumn),
                    "masterPrimaryKey": _val(dapp, IND.masterPrimaryKey),
                    "isMasterApp":      _val(dapp, IND.isMasterApp) in ("true", "True", "1"),
                    "factTable":        _val(fact_tbl, IND.tableName) if fact_tbl else None,
                    "dimTable":         _val(dim_tbl,  IND.tableName) if dim_tbl  else None,
                    "dimPrimaryKey":    _val(dapp, IND.dimPrimaryKey),
                    "dimColumn":        _val(dapp, IND.dimColumn),
                }
                dapp_list.append(dapp_entry)

            result["dimensions"].append({
                "code":       dc,
                "cnName":     cn_name,
                "dimType":    type_name,
                "timeGrain":  view_name,
                "hierarchy":  hier_code,
                "levelCode":  level_code,
                "definition": definition,
                "isHyper":    is_hyper in ("true", "True", "1"),
                "dimApps":    dapp_list,
                "inQuery":    dc in dim_codes_set,   # 是否为用户原始查询已选
            })

        # 保留用户原始选中维度列表，供 kg_attribution 使用
        result["selected_dim_codes"] = dim_codes

        # ──────────────────── 兄弟指标（同事实表）────────────────────── #
        siblings = []
        for tbl_uri in fact_tbl_uris:
            tbl_name = _val(tbl_uri, IND.tableName)
            for mapp_s in g.subjects(IND.appliesToTable, tbl_uri):
                for meas_inst in g.subjects(IND.hasMeasureApp, mapp_s):
                    sibling_code = _val(meas_inst, IND.code)
                    if sibling_code and sibling_code not in meas_codes:
                        cn = _val(meas_inst, IND.cnName)
                        siblings.append({
                            "code":   sibling_code,
                            "cnName": cn or sibling_code,
                            "table":  tbl_name,
                        })
        # 去重
        seen = set()
        siblings_dedup = []
        for s in siblings:
            if s["code"] not in seen:
                seen.add(s["code"])
                siblings_dedup.append(s)
        result["siblings"] = sorted(siblings_dedup, key=lambda x: x["code"])

        # ──────────────────── 可用维度分组 ───────────────────────────── #
        dim_groups: dict[str, list] = {}
        for dim_inst in g.subjects(RDF.type, IND.Dimension):
            c    = _val(dim_inst, IND.code)
            cn   = _val(dim_inst, IND.cnName)
            hier = _val(dim_inst, IND.hierarchyCode) or "其他"
            if c:
                dim_groups.setdefault(hier, []).append({
                    "code": c, "cnName": cn or c,
                    "queried": True,          # 图谱扩展归因全部覆盖
                    "inQuery": c in dim_codes, # 是否在原始查询中手动选中
                })
        result["dim_groups"] = {
            k: sorted(v, key=lambda x: x["code"])
            for k, v in sorted(dim_groups.items())
        }

        # ──────────────────── 合并 Part1 当期波动数据 ────────────────────── #
        if part1 and not part1.get("error"):
            wave_by_code = {m["col"]: m for m in part1.get("measures", [])}
            for m_info in result["measures"]:
                code = m_info.get("code")
                if code and code in wave_by_code:
                    m_info["wave"] = wave_by_code[code]
            result["current_period"]  = part1.get("current_period")
            result["previous_period"] = part1.get("previous_period")
            result["time_dim"]        = part1.get("time_dim")

        return result

    # ── Part 3: 统计学分析 ──────────────────────────────────────────────── #

    def _analyze_statistics(self, df, meas_cols: list, dim_cols: list,
                            time_col: Optional[str],
                            focus_hint: str = "",
                            focus_dim_codes: list = []) -> dict:
        import numpy as np

        result: dict = {
            "descriptive": {},
            "trend": {},
            "anomalies": {},
            "correlation": {"dim_meas": {}, "meas_meas": {}},
        }

        # Part 1 "下阶段重点" 分析导向：记录到结果中，供 LLM 解读参考
        if focus_hint:
            result["analysis_focus"] = focus_hint

        # 相关性分析优先覆盖聚焦维度，再补充其余维度（最多 8 个）
        _focus_set = set(focus_dim_codes)
        _ordered_dims = (
            [d for d in dim_cols if d in _focus_set]
            + [d for d in dim_cols if d not in _focus_set]
        )[:8]

        numeric_df = df[meas_cols].apply(lambda c: c.map(_safe_float))

        # 3.1 描述性统计
        for col in meas_cols:
            if col not in numeric_df.columns:
                continue
            s = numeric_df[col].dropna()
            if len(s) == 0:
                continue
            try:
                from scipy.stats import skew as _skew, kurtosis as _kurtosis
                sk = float(_skew(s))
                ku = float(_kurtosis(s))
            except Exception:
                sk = ku = None
            q1, q3 = float(np.percentile(s, 25)), float(np.percentile(s, 75))
            result["descriptive"][col] = {
                "count": int(len(s)),
                "mean": round(float(s.mean()), 4),
                "median": round(float(s.median()), 4),
                "std": round(float(s.std()), 4) if len(s) > 1 else 0,
                "q1": round(q1, 4),
                "q3": round(q3, 4),
                "min": round(float(s.min()), 4),
                "max": round(float(s.max()), 4),
                "skewness": round(sk, 4) if sk is not None else None,
                "kurtosis": round(ku, 4) if ku is not None else None,
            }

        # 3.2 趋势分析（需要时间维度）
        if time_col and time_col in df.columns:
            df_sorted = df.sort_values(by=time_col)
            periods = df_sorted[time_col].dropna().unique().tolist()

            for col in meas_cols:
                if col not in df.columns:
                    continue
                ts = df_sorted.groupby(time_col)[col].sum().reset_index()
                ts.columns = ["period", "value"]
                ts["value"] = ts["value"].map(_safe_float)
                ts = ts.dropna(subset=["value"])
                if len(ts) < 3:
                    continue

                x = np.arange(len(ts), dtype=float)
                y = ts["value"].values.astype(float)

                try:
                    from scipy.stats import linregress
                    slope, intercept, r, p, _ = linregress(x, y)
                    r_sq = round(r ** 2, 4)
                    p_val = round(p, 6)
                    slope_r = round(float(slope), 4)

                    # 外推 3 期
                    forecast = []
                    last_period = ts["period"].iloc[-1]
                    for i in range(1, 4):
                        pred = float(intercept + slope * (len(ts) - 1 + i))
                        forecast.append({"offset": f"+{i}期", "value": round(pred, 4)})

                    trend_interp = ""
                    if p_val < 0.001:
                        sig_label = "极显著"
                    elif p_val < 0.01:
                        sig_label = "高度显著"
                    elif p_val < 0.05:
                        sig_label = "显著"
                    else:
                        sig_label = "不显著"

                    if p_val < 0.05:
                        direction = "上升" if slope > 0 else "下降"
                        trend_interp = (
                            f"{sig_label}的{direction}趋势（p={p_val}），"
                            f"期均变化 {slope_r:+,.4f}，"
                            f"R²={r_sq}（趋势解释了 {r_sq*100:.1f}% 的方差）"
                        )
                    else:
                        trend_interp = (
                            f"趋势{sig_label}（p={p_val}，p≥0.05 说明序列无明显方向性，"
                            f"波动可能是随机的）"
                        )

                    result["trend"][col] = {
                        "slope": slope_r,
                        "r_squared": r_sq,
                        "p_value": p_val,
                        "interpretation": trend_interp,
                        "forecast": forecast,
                    }
                except Exception:
                    pass

        # 3.3 异常检测
        for col in meas_cols:
            if col not in numeric_df.columns:
                continue
            s = numeric_df[col].dropna()
            if len(s) < 3:
                continue

            arr = s.values.astype(float)
            anomaly_indices: set[int] = set()

            # IQR
            q1, q3 = np.percentile(arr, 25), np.percentile(arr, 75)
            iqr = q3 - q1
            iqr_low, iqr_high = q1 - 1.5 * iqr, q3 + 1.5 * iqr
            iqr_anom = set(np.where((arr < iqr_low) | (arr > iqr_high))[0].tolist())

            # Z-score
            mean, std = np.mean(arr), np.std(arr)
            if std > 0:
                zscores = (arr - mean) / std
                z_anom = set(np.where(np.abs(zscores) > 2.5)[0].tolist())
            else:
                zscores = np.zeros_like(arr)
                z_anom = set()

            # IsolationForest (skip if < 5)
            if_anom: set[int] = set()
            if len(arr) >= 5:
                try:
                    from sklearn.ensemble import IsolationForest
                    clf = IsolationForest(contamination=0.1, random_state=42)
                    preds = clf.fit_predict(arr.reshape(-1, 1))
                    if_anom = set(np.where(preds == -1)[0].tolist())
                except Exception:
                    pass

            anomaly_indices = iqr_anom | z_anom | if_anom

            anomalies = []
            for idx in sorted(anomaly_indices):
                orig_idx = s.index[idx]
                methods = []
                if idx in iqr_anom: methods.append("IQR")
                if idx in z_anom:   methods.append("Z-score")
                if idx in if_anom:  methods.append("IsolationForest")
                row = df.loc[orig_idx]
                period_val = str(row.get(time_col, "")) if time_col else f"行#{orig_idx}"
                # 记录所有维度值，供前端展示，方便区分同一日期不同维度组合
                dims = {
                    d: str(row[d]) if row.get(d) is not None else ""
                    for d in dim_cols
                    if d in df.columns and d != time_col
                }
                anomalies.append({
                    "period": period_val,
                    "value": round(float(arr[idx]), 4),
                    "methods": methods,
                    "z_score": round(float(zscores[idx]), 4) if std > 0 else None,
                    "dims": dims,
                })
            result["anomalies"][col] = anomalies
            result.setdefault("anomaly_dim_cols", list(dim_cols))

        # 3.4 相关性
        # 维度 vs 指标（Spearman，将维度编码为整数）
        # 优先分析 Part1 聚焦维度，最多 8 个
        import pandas as pd
        for dim in _ordered_dims:
            if dim not in df.columns:
                continue
            dim_encoded = pd.Categorical(df[dim]).codes.astype(float)
            for meas_col in meas_cols:
                if meas_col not in numeric_df.columns:
                    continue
                meas_s = numeric_df[meas_col]
                valid = ~(pd.isnull(dim_encoded) | pd.isnull(meas_s))
                if valid.sum() < 3:
                    continue
                try:
                    from scipy.stats import spearmanr
                    rho, pval = spearmanr(dim_encoded[valid], meas_s[valid])
                    rho_f, pval_f = round(float(rho), 4), round(float(pval), 6)
                    # Spearman 业务解读
                    abs_rho = abs(rho_f)
                    if abs_rho >= 0.7:
                        corr_str = "强" + ("正" if rho_f > 0 else "负") + "相关"
                    elif abs_rho >= 0.4:
                        corr_str = "中等" + ("正" if rho_f > 0 else "负") + "相关"
                    elif abs_rho >= 0.2:
                        corr_str = "弱" + ("正" if rho_f > 0 else "负") + "相关"
                    else:
                        corr_str = "几乎无线性关系"
                    if pval_f < 0.001:
                        sig_str = "极显著（p<0.001）"
                    elif pval_f < 0.01:
                        sig_str = "高度显著（p<0.01）"
                    elif pval_f < 0.05:
                        sig_str = "显著（p<0.05）"
                    else:
                        sig_str = "不显著（p≥0.05，可能是随机关系）"
                    key = f"{dim}|{meas_col}"
                    result["correlation"]["dim_meas"][key] = {
                        "spearman": rho_f,
                        "p_value": pval_f,
                        "interp": f"{corr_str}，{sig_str}",
                    }
                except Exception:
                    pass

        # 多指标间 Pearson
        if len(meas_cols) > 1:
            try:
                from scipy.stats import pearsonr
                for i, c1 in enumerate(meas_cols):
                    for c2 in meas_cols[i+1:]:
                        if c1 not in numeric_df.columns or c2 not in numeric_df.columns:
                            continue
                        valid = numeric_df[[c1, c2]].dropna()
                        if len(valid) < 3:
                            continue
                        r, pv = pearsonr(valid[c1], valid[c2])
                        r_f, pv_f = round(float(r), 4), round(float(pv), 6)
                        abs_r = abs(r_f)
                        if abs_r >= 0.7:
                            corr_str2 = "强" + ("正" if r_f > 0 else "负") + "相关"
                        elif abs_r >= 0.4:
                            corr_str2 = "中等" + ("正" if r_f > 0 else "负") + "相关"
                        elif abs_r >= 0.2:
                            corr_str2 = "弱" + ("正" if r_f > 0 else "负") + "相关"
                        else:
                            corr_str2 = "几乎无线性关系"
                        if pv_f < 0.001:
                            sig_str2 = "极显著（p<0.001）"
                        elif pv_f < 0.01:
                            sig_str2 = "高度显著（p<0.01）"
                        elif pv_f < 0.05:
                            sig_str2 = "显著（p<0.05）"
                        else:
                            sig_str2 = "不显著（p≥0.05）"
                        key = f"{c1}|{c2}"
                        result["correlation"]["meas_meas"][key] = {
                            "pearson": r_f,
                            "p_value": pv_f,
                            "interp": f"{corr_str2}，{sig_str2}",
                        }
            except Exception:
                pass

        # 3.5 异常时间点摘要（只记录 z-score 异常期，不输出全量日序列）
        if time_col and time_col in df.columns:
            anomaly_periods: dict = {}
            for col in meas_cols:
                if col not in df.columns:
                    continue
                ts = df.groupby(time_col)[col].sum().reset_index()
                ts.columns = ["period", "value"]
                ts["value"] = ts["value"].map(_safe_float)
                ts = ts.dropna(subset=["value"])
                if len(ts) < 3:
                    continue
                vals = ts["value"].values.astype(float)
                mean_v, std_v = float(np.mean(vals)), float(np.std(vals))
                if std_v == 0:
                    continue
                anom_rows = []
                for _, row in ts.iterrows():
                    v = float(row["value"])
                    z = round((v - mean_v) / std_v, 3)
                    if abs(z) >= 2.0:   # 只保留超出 2σ 的异常期
                        if abs(z) >= 3.0:
                            label = f"极端{'偏高' if z > 0 else '偏低'}（|z|={abs(z):.2f}）"
                        elif abs(z) >= 2.5:
                            label = f"严重{'偏高' if z > 0 else '偏低'}（|z|={abs(z):.2f}）"
                        else:
                            label = f"显著{'偏高' if z > 0 else '偏低'}（|z|={abs(z):.2f}）"
                        anom_rows.append({
                            "period":  str(row["period"]),
                            "value":   round(v, 4),
                            "z_score": z,
                            "label":   label,
                        })
                if anom_rows:
                    # 按 |z| 降序，只保留前 10 个最显著异常期
                    anom_rows.sort(key=lambda x: abs(x["z_score"]), reverse=True)
                    anomaly_periods[col] = anom_rows[:10]
            if anomaly_periods:
                result["anomaly_periods"] = anomaly_periods

        return result

    # ── Part 5: 明细下钻分析 ─────────────────────────────────────────────── #

    def _drill_query_by_dim_value(
        self,
        query_params: dict,
        dim_code: str,
        dim_value: str,
        current_period: str,
        previous_period: str,
        time_col: str,
    ) -> dict:
        """
        基于 Query API cellList 行数据结构发起下钻查询。

        cellList 结构理解：
          - DIMENSION cell: id/data 字段均含维度值（code 为维度标识符），
            用这些值作为等值过滤条件构建 filterList。
          - MEASURE cell: id 字段为 null（API 未填充），
            code 字段是指标标识符，data 字段是指标值。

        本方法在原始 query_params 基础上叠加：
          1. 时间范围收窄到 [previous_period, current_period]（当期 vs 上期）
          2. 对目标维度追加等值过滤（基于 DIMENSION cell 的 id/data 值）
        返回当期和上期两段的汇总对比数据。
        """
        if not dim_code or not dim_value:
            return {"dim_code": dim_code, "dim_value": dim_value,
                    "skip_reason": "维度代码或维度值为空"}

        # 保留原始非时间、非目标维度的过滤条件
        base_filters = [
            f for f in query_params.get("filterList", [])
            if f.get("code") not in (time_col, dim_code)
        ]

        # 时间窗口：覆盖当期和上期
        time_filter: dict = {}
        if time_col:
            orig_view = 1
            _uses_dl  = False
            for f in query_params.get("filterList", []):
                if f.get("code") == time_col:
                    orig_view = f.get("viewType", 1)
                    if (f.get("operatorList") or [{}])[0].get("dataList"):
                        _uses_dl = True
                    break
            if _uses_dl:
                time_filter = {
                    "code": time_col,
                    "operatorList": [{"sqlOprType": 2,
                                      "dataList":  [str(previous_period), str(current_period)],
                                      "timeRange": 1}],
                    "internal": True,
                }
            else:
                time_filter = {
                    "code": time_col,
                    "viewType": orig_view,
                    "operatorList": [{
                        "sqlOprType": 2,
                        "begin": str(previous_period)[:10],
                        "end":   str(current_period)[:10],
                    }],
                }

        # 维度等值过滤：sqlOprType=0 (IN) + dataList=[dim_value]
        # 注意：sqlOprType=1 是 NOT-IN，不是等值；Operator 模型无 value 字段，须用 dataList
        dim_filter = {
            "code": dim_code,
            "viewType": 1,
            "operatorList": [{"sqlOprType": 0, "dataList": [str(dim_value)]}],
        }

        drill_params = {
            **query_params,
            "filterList": base_filters
                          + ([time_filter] if time_filter else [])
                          + [dim_filter],
        }

        # configureList 只保留「时间维度 + 指标」，去掉所有业务维度
        # 原因：DA API 对「复合指标(如NSS) + 多业务维度」组合会返回"查询失败"
        # （NSS底层需要多表JOIN，叠加多个高基数维度后超出DA API的笛卡尔积限制）
        # 下钻目标维度已通过 filterList 的等值过滤传入，不需要出现在 configureList
        orig_cfg = query_params.get("configureList", [])
        meas_only_cfg = [c for c in orig_cfg if c.get("code", "").startswith("MEAS_")]
        time_cfg = [{"code": time_col, "order": {"sortType": 1}}] if time_col else []
        drill_params = {**drill_params, "configureList": time_cfg + meas_only_cfg}

        try:
            rows, meas_cols, dim_cols = self._fetch_data(drill_params, page_size=200)
        except Exception as e:
            return {"dim_code": dim_code, "dim_value": dim_value, "error": str(e)}

        if not rows:
            return {"dim_code": dim_code, "dim_value": dim_value,
                    "row_count": 0, "skip_reason": "下钻查询返回 0 行"}

        # 按当期/上期分组汇总指标均值
        curr_rows = [r for r in rows if time_col and str(r.get(time_col, ""))[:10] == str(current_period)[:10]]
        prev_rows = [r for r in rows if time_col and str(r.get(time_col, ""))[:10] == str(previous_period)[:10]]
        self._log(f"    下钻 {dim_code}={dim_value}：共 {len(rows)} 行，当期 {len(curr_rows)} 行，上期 {len(prev_rows)} 行"
                  + (f"，日期样本={[str(r.get(time_col,''))[:10] for r in rows[:3]]}" if rows else ""))

        def _avg(row_list, col):
            vals = [r[col] for r in row_list if r.get(col) is not None]
            return round(sum(vals) / len(vals), 4) if vals else None

        meas_summary = []
        for mc in meas_cols:
            curr_v = _avg(curr_rows, mc)
            prev_v = _avg(prev_rows, mc)
            chg_pct = _pct_change(curr_v, prev_v)
            meas_summary.append({
                "meas_code":  mc,
                "current":    curr_v,
                "previous":   prev_v,
                "change_pct": chg_pct,
            })

        return {
            "dim_code":     dim_code,
            "dim_value":    dim_value,
            "row_count":    len(rows),
            "curr_count":   len(curr_rows),
            "prev_count":   len(prev_rows),
            "meas_summary": meas_summary,
            "sample_rows":  rows[:5],   # 前5行明细供参考
        }

    def _analyze_drill_down(self, part1: dict, part2: dict,
                            query_params: Optional[dict] = None) -> dict:
        import pandas as pd
        import numpy as np
        from rdflib import Graph, Namespace, RDF, Literal

        result: dict = {}

        # ── Step 0: Query API 下钻（从 Part1 global_top20 提取问题维度值，当期 vs 上期对比） #
        # cellList 行数据结构：DIMENSION cell id/data = 维度值；MEASURE cell id=null，用 code 标识指标
        # 对 Top 5 贡献维度值各发起一次 Query API 下钻查询
        _period     = part1.get("current_period")
        _prev_p     = part1.get("previous_period")
        _time_dim   = part1.get("time_dim", "")
        if query_params and _period and _prev_p:
            top_drill_items = []
            seen_dc_val: set = set()
            for m in part1.get("global_top20", [])[:5]:
                dc  = m.get("dim_col")
                val = m.get("value")
                if dc and val and (dc, str(val)) not in seen_dc_val:
                    seen_dc_val.add((dc, str(val)))
                    top_drill_items.append((dc, str(val), m.get("lmdi_contrib", 0)))

            api_drill_results = []
            for dc, val, contrib in top_drill_items:
                self._log(f"  Query API 下钻：{dc}={val}（贡献度={contrib:.4f}）")
                dr = self._drill_query_by_dim_value(
                    query_params, dc, val,
                    current_period=str(_period),
                    previous_period=str(_prev_p),
                    time_col=_time_dim,
                )
                dr["lmdi_contrib"] = contrib
                api_drill_results.append(dr)
            result["api_drill_results"] = api_drill_results

        # ── Step 1: 从 Part1 找变化最大的维度值 ───────────────────────────── #
        top_mover, top_dc = None, None
        max_abs = 0.0
        for dc in part1.get("dimension_contrib", []):
            for mv in dc.get("top_movers", []):
                chg = abs(mv.get("share_change") or 0)
                if chg > max_abs:
                    max_abs = chg
                    top_mover = mv
                    top_dc = dc

        if not top_mover:
            # fallback 1: 取维度分布里占比最大的值
            for dd in part1.get("dimension_distribution", []):
                if dd.get("top5"):
                    top_mover = {"value": dd["top5"][0]["value"], "share_change": 1.0}
                    top_dc = {"dim_col": dd["dim_col"], "meas_col": dd.get("meas_col", "")}
                    break
        if not top_mover:
            # fallback 2: 直接从 global_top20 取贡献绝对值最大的项
            for g20 in sorted(part1.get("global_top20", []),
                              key=lambda x: abs(x.get("lmdi_contrib") or 0), reverse=True):
                dc  = g20.get("dim_col")
                val = g20.get("value")
                if dc and val:
                    top_mover = {"value": val, "share_change": g20.get("lmdi_contrib", 0)}
                    top_dc    = {"dim_col": dc, "meas_col": g20.get("meas_col", "")}
                    break
        if not top_mover:
            return {**result, "skip_reason": "Part1 无维度贡献数据，无法确定下钻目标"}

        dim_code   = top_dc["dim_col"]
        dim_val    = top_mover["value"]
        period     = part1.get("current_period")
        meas_col   = top_dc.get("meas_col", "")
        share_chg  = top_mover.get("share_change") or 0

        result["drill_target"] = {
            "dim_code":     dim_code,
            "dim_value":    dim_val,
            "period":       period,
            "share_change": share_chg,
            "direction":    "上升" if share_chg > 0 else "下降",
            "meas_col":     meas_col,
        }
        self._log(f"  下钻目标：{dim_code}={dim_val}，期间={period}")

        # ── Step 2: 从 Part2 获取事实表信息 ───────────────────────────────── #
        measures = part2.get("measures", [])
        if not measures or measures[0].get("error"):
            return {**result, "skip_reason": "Part2 无指标数据"}
        mapp_list = measures[0].get("measureApps", [])
        if not mapp_list:
            return {**result, "skip_reason": "指标无 MeasureApp"}

        mapp         = mapp_list[0]
        tbl_info     = mapp.get("table", {})
        conn_partial = mapp.get("connection", {})
        fact_table   = tbl_info.get("tableName")
        db_schema    = tbl_info.get("schema", "")
        if not fact_table:
            return {**result, "skip_reason": "无法获取事实表名"}

        # ── Step 3: Re-read TTL 获取凭证 + 列映射 ─────────────────────────── #
        g = Graph()
        g.parse(self._ttl_path, format="turtle")
        IND = Namespace("http://indicator.insightmind.com/ontology#")

        def _val(uri, prop):
            v = g.value(uri, prop)
            return str(v) if v else None

        def _find_inst(code):
            for s, _, _ in g.triples((None, IND.code, Literal(code))):
                return s
            return None

        # DB 凭证
        db_host = conn_partial.get("host", "127.0.0.1")
        db_port = int(conn_partial.get("port") or 3306)
        db_name = conn_partial.get("dbName", "")
        db_user, db_password = "root", "root"
        for conn_uri in g.subjects(RDF.type, IND.DataConnection):
            if _val(conn_uri, IND.host) == db_host:
                db_user     = _val(conn_uri, IND.dbUser) or "root"
                db_password = _val(conn_uri, IND.dbPassword) or "root"
                break

        # 时间物理列（来自 NaturalDimMapping）
        time_phys_col = None
        for ndm_info in mapp.get("naturalDims", []):
            time_phys_col = ndm_info.get("physicalColumn")
            break

        # 维度外键列 + 判断是否有维度表（dimTypeCode=2）
        dim_inst     = _find_inst(dim_code)
        dim_fact_col = None
        dim_cn_name  = dim_code
        dim_type_code = "0"
        dim_tbl_name   = None   # 维度表表名（dimTypeCode=2 时用）
        dim_tbl_schema = None   # 维度表所在 schema（库名）
        dim_pk         = None   # 维度表主键
        dim_display_col = None  # 维度展示列
        if dim_inst:
            dim_cn_name   = _val(dim_inst, IND.cnName) or dim_code
            dim_type_code = str(int(float(_val(dim_inst, IND.dimTypeCode) or 0)))
            for dapp in g.objects(dim_inst, IND.hasDimApp):
                col = _val(dapp, IND.dimFactColumn)
                if col:
                    dim_fact_col    = col
                    dim_pk          = _val(dapp, IND.dimPrimaryKey)
                    dim_display_col = _val(dapp, IND.dimColumn)
                    tbl_u = g.value(dapp, IND.dimTable)
                    if tbl_u:
                        dim_tbl_name   = _val(tbl_u, IND.tableName)
                        dim_tbl_schema = _val(tbl_u, IND.schemaName)
                    break
        if not dim_fact_col:
            return {**result, "skip_reason": f"无法找到维度 {dim_code} 的事实表外键列"}

        result["drill_target"]["dim_cn_name"]    = dim_cn_name
        result["drill_target"]["dim_fact_col"]   = dim_fact_col
        result["drill_target"]["dim_type_code"]  = dim_type_code

        # 全局列映射 fact_col → {dim_code, cnName, role}
        col_meta: dict = {}
        for dim_u in g.subjects(RDF.type, IND.Dimension):
            d_code = _val(dim_u, IND.code)
            d_cn   = _val(dim_u, IND.cnName) or d_code
            for da in g.objects(dim_u, IND.hasDimApp):
                fc = _val(da, IND.dimFactColumn)
                if fc:
                    col_meta.setdefault(fc, {"dim_code": d_code, "cnName": d_cn, "role": "维度"})
        for meas_u in g.subjects(RDF.type, IND.Measure):
            m_code = _val(meas_u, IND.code)
            m_cn   = _val(meas_u, IND.cnName) or m_code
            for ma in g.objects(meas_u, IND.hasMeasureApp):
                fc = _val(ma, IND.factColumn)
                if fc:
                    col_meta.setdefault(fc, {"dim_code": m_code, "cnName": m_cn, "role": "指标"})

        # ── Step 4: 查询 MySQL 明细 ────────────────────────────────────────── #
        try:
            import pymysql
            import datetime as _dt3
            conn = pymysql.connect(
                host=db_host, port=db_port, user=db_user,
                password=db_password, database=db_name, charset="utf8mb4",
            )
            full_tbl = f"`{db_schema}`.`{fact_table}`" if db_schema else f"`{fact_table}`"

            # 时间过滤（%%Y 转义，避免 pymysql 误解 %Y）
            # 周代码 6 位纯数字（如 202618）→ BETWEEN 周一 AND 周日
            time_filter = ""
            time_params: list = []
            if time_phys_col and period:
                s = str(period)
                if s.isdigit() and len(s) == 6:
                    yr, wk = int(s[:4]), int(s[4:])
                    _begin = _dt3.date.fromisocalendar(yr, wk, 1).isoformat()
                    _end   = _dt3.date.fromisocalendar(yr, wk, 7).isoformat()
                    time_filter = f"`{time_phys_col}` BETWEEN %s AND %s"
                    time_params = [_begin, _end]
                else:
                    parts = s.split("-")
                    if len(parts) == 2:
                        time_filter = f"DATE_FORMAT(`{time_phys_col}`, '%%Y-%%m') = %s"
                    elif len(parts) == 1:
                        time_filter = f"YEAR(`{time_phys_col}`) = %s"
                    elif len(parts) == 3:
                        time_filter = f"DATE(`{time_phys_col}`) = %s"
                    time_params = [s]

            # 维度过滤：退化维直接过滤外键列；有维表则用子查询
            if dim_type_code == "2" and dim_tbl_name and dim_pk and dim_display_col:
                _dim_tbl_full = (f"`{dim_tbl_schema}`.`{dim_tbl_name}`"
                                 if dim_tbl_schema else f"`{dim_tbl_name}`")
                dim_filter = (
                    f"`{dim_fact_col}` IN "
                    f"(SELECT `{dim_pk}` FROM {_dim_tbl_full} WHERE `{dim_display_col}` = %s)"
                )
            else:
                dim_filter = f"`{dim_fact_col}` = %s"

            where_parts = [p for p in [time_filter, dim_filter] if p]
            where_clause = ("WHERE " + " AND ".join(where_parts)) if where_parts else ""
            sql_params = time_params + [dim_val]

            sql = f"SELECT * FROM {full_tbl} {where_clause} LIMIT 200"
            # 生成可读 SQL（用于展示，%% → %）
            sql_display = sql.replace("%%", "%")
            for p in sql_params:
                sql_display = sql_display.replace("%s", f"'{p}'", 1)
            result["sql"] = sql_display

            with conn.cursor(pymysql.cursors.DictCursor) as cur:
                cur.execute(sql, sql_params)
                rows = list(cur.fetchall())
            conn.close()

            result["detail_count"] = len(rows)
            self._log(f"  明细查询返回 {len(rows)} 行")
            if not rows:
                return {**result, "skip_reason": f"明细查询返回 0 行（{dim_fact_col} → '{dim_val}'，期间={period}）"}

        except Exception as e:
            return {**result, "skip_reason": f"MySQL 查询失败: {e}"}

        # ── Step 5: 列元信息 + 类型标注 ───────────────────────────────────── #
        df = pd.DataFrame(rows)
        # 将 Decimal 等转为 float
        for col in df.columns:
            try:
                df[col] = pd.to_numeric(df[col], errors="ignore")
            except Exception:
                pass

        col_info = []
        for col in df.columns:
            meta = col_meta.get(str(col), {})
            col_info.append({
                "column": str(col),
                "cnName": meta.get("cnName", str(col)),
                "role":   meta.get("role", "其他"),
            })
        result["columns"] = col_info

        # ── Step 6: 异常行检测 ─────────────────────────────────────────────── #
        numeric_cols = [c for c in df.columns if pd.api.types.is_numeric_dtype(df[c])]
        anomaly_idx_set: set = set()
        col_anomaly: dict = {}

        for col in numeric_cols:
            s = df[col].dropna()
            if len(s) < 3:
                continue
            arr = s.values.astype(float)
            q1, q3 = np.percentile(arr, 25), np.percentile(arr, 75)
            iqr = q3 - q1
            if iqr == 0:
                continue
            low, high = q1 - 1.5 * iqr, q3 + 1.5 * iqr
            mean, std = np.mean(arr), np.std(arr)
            anom_pos = np.where((arr < low) | (arr > high))[0]
            if len(anom_pos):
                real_idx = s.index[anom_pos].tolist()
                anomaly_idx_set.update(real_idx)
                col_anomaly[str(col)] = {
                    "iqr_low":  round(float(low), 4),
                    "iqr_high": round(float(high), 4),
                    "mean":     round(float(mean), 4),
                    "std":      round(float(std), 4),
                    "anomaly_count": len(real_idx),
                }
        result["anomaly_cols"] = col_anomaly

        def _row_to_dict(row_series):
            d = {}
            for c, v in row_series.items():
                try:
                    import math
                    fv = float(v)
                    d[str(c)] = None if math.isnan(fv) or math.isinf(fv) else fv
                except (TypeError, ValueError):
                    d[str(c)] = str(v) if v is not None else None
            return d

        # 异常行（最多 30 行）
        anomaly_rows_out = []
        for idx in sorted(anomaly_idx_set)[:30]:
            row_d = _row_to_dict(df.loc[idx])
            row_d["__anomalous_cols__"] = [
                c for c, info in col_anomaly.items()
                if c in df.columns and df.loc[idx, c] is not None
                and (float(df.loc[idx, c]) < info["iqr_low"] or float(df.loc[idx, c]) > info["iqr_high"])
            ]
            anomaly_rows_out.append(row_d)
        result["anomaly_rows"] = anomaly_rows_out

        # 正常样本（前 10 行）
        normal_idx = [i for i in df.index if i not in anomaly_idx_set][:10]
        result["sample_rows"] = [_row_to_dict(df.loc[i]) for i in normal_idx]

        # ── Step 7: 关联维度表数据（针对异常行的外键值） ───────────────────── #
        related_data: dict = {}
        if anomaly_rows_out:
            try:
                import pymysql as _pym
                conn2 = _pym.connect(
                    host=db_host, port=db_port, user=db_user,
                    password=db_password, database=db_name, charset="utf8mb4",
                )
                seen_fk_tbl: set = set()   # (fk_col, dim_tbl_name) 去重，避免同外键重复出现
                for dim_u in g.subjects(RDF.type, IND.Dimension):
                    d_type = _val(dim_u, IND.dimTypeCode)
                    try:
                        if int(float(d_type or -1)) != 2:
                            continue
                    except Exception:
                        continue
                    d_code = _val(dim_u, IND.code)
                    d_cn   = _val(dim_u, IND.cnName) or d_code
                    for dapp in g.objects(dim_u, IND.hasDimApp):
                        fc          = _val(dapp, IND.dimFactColumn)
                        pk          = _val(dapp, IND.dimPrimaryKey)
                        dim_col     = _val(dapp, IND.dimColumn)
                        dim_tbl_uri = g.value(dapp, IND.dimTable)
                        if not fc or not pk or not dim_tbl_uri:
                            continue
                        dim_tbl_name   = _val(dim_tbl_uri, IND.tableName)
                        dim_tbl_schema2 = _val(dim_tbl_uri, IND.schemaName)
                        if not dim_tbl_name:
                            continue
                        dim_tbl_full2 = (f"`{dim_tbl_schema2}`.`{dim_tbl_name}`"
                                         if dim_tbl_schema2 else f"`{dim_tbl_name}`")
                        # 同一外键列 + 同一维度表，只处理一次
                        dedup_key = (fc, dim_tbl_name)
                        if dedup_key in seen_fk_tbl:
                            continue
                        seen_fk_tbl.add(dedup_key)
                        # 收集异常行外键值
                        fk_vals = list({r.get(fc) for r in anomaly_rows_out if r.get(fc) is not None})
                        if not fk_vals:
                            continue
                        ph = ",".join(["%s"] * len(fk_vals))
                        dim_sql = f"SELECT * FROM {dim_tbl_full2} WHERE `{pk}` IN ({ph}) LIMIT 50"
                        try:
                            with conn2.cursor(_pym.cursors.DictCursor) as cur:
                                cur.execute(dim_sql, fk_vals)
                                dim_rows = [dict(r) for r in cur.fetchall()]
                            if dim_rows:
                                related_data[d_code] = {
                                    "cnName":      d_cn,
                                    "table":       dim_tbl_name,
                                    "fk_col":      fc,
                                    "pk_col":      pk,
                                    "display_col": dim_col,
                                    "rows":        dim_rows,
                                }
                        except Exception:
                            pass
                conn2.close()
            except Exception as e:
                self._log(f"  关联数据查询失败: {e}")
        result["related_data"] = related_data
        self._log(f"  关联维度表: {list(related_data.keys()) or '无'}")

        return result

    # ── Part KG_ATTR: 图谱扩展归因 ───────────────────────────────────────── #

    def _analyze_kg_attribution(self, part1: dict, part2: dict,
                                focus_dim_codes: Optional[set] = None) -> dict:
        """
        通过图谱发现所有关联维度（不限于已选），直连事实表计算各维度在
        当期 vs 上期的贡献变化，返回结构化归因结果。
        """
        from rdflib import Graph, Namespace, RDF, Literal as _Lit
        import pymysql

        result: dict = {"kg_dimensions": []}

        current_period  = part1.get("current_period")
        previous_period = part1.get("previous_period")
        if not current_period or not previous_period:
            result["skip_reason"] = "无时间维度，无法做扩展归因"
            return result

        # ── 从 Part2 获取事实表 / 连接 / 指标列 ── #
        measures = part2.get("measures", [])
        if not measures or measures[0].get("error"):
            result["skip_reason"] = "Part2 无指标数据"
            return result

        mapp_list = measures[0].get("measureApps", [])
        if not mapp_list:
            result["skip_reason"] = "指标无 MeasureApp"
            return result

        mapp        = mapp_list[0]
        tbl_info    = mapp.get("table", {})
        conn_info   = mapp.get("connection", {})
        fact_table  = tbl_info.get("tableName")
        db_schema   = tbl_info.get("schema", "")
        fact_col    = mapp.get("factColumn")
        agg_op      = (mapp.get("aggOperator") or "SUM").upper()
        where_cond  = mapp.get("whereCondition") or ""

        if not fact_table:
            result["skip_reason"] = "无法获取事实表名"
            return result

        # ── 从 TTL 读取完整凭证 + 维度物理列映射 ── #
        g = Graph()
        g.parse(self._ttl_path, format="turtle")
        IND = Namespace("http://indicator.insightmind.com/ontology#")

        def _v(uri, prop):
            v = g.value(uri, prop)
            return str(v) if v else None

        db_host = conn_info.get("host", "127.0.0.1")
        db_port = int(conn_info.get("port") or 3306)
        db_name = conn_info.get("dbName", "")
        db_user, db_pwd = "root", "root"
        for conn_uri in g.subjects(RDF.type, IND.DataConnection):
            if _v(conn_uri, IND.host) == db_host:
                db_user = _v(conn_uri, IND.dbUser) or "root"
                db_pwd  = _v(conn_uri, IND.dbPassword) or "root"
                break

        # 时间物理列
        time_col = None
        for ndm in mapp.get("naturalDims", []):
            time_col = ndm.get("physicalColumn")
            break
        if not time_col:
            result["skip_reason"] = "无时间物理列映射"
            return result

        # 所有维度: dim_code -> {cn_name, dim_fact_col, ...}
        # 优先取与当前事实表匹配的 dimApp，其次取第一个有 dimFactColumn 的 app
        dim_map: dict = {}
        for dim_uri in g.subjects(RDF.type, IND.Dimension):
            d_code    = _v(dim_uri, IND.code)
            d_cn      = _v(dim_uri, IND.cnName) or d_code
            d_type    = _v(dim_uri, IND.dimTypeCode) or "0"
            view_type = _v(dim_uri, IND.viewTypeCode)

            best: dict = {}
            fallback: dict = {}
            for dapp in g.objects(dim_uri, IND.hasDimApp):
                fc = _v(dapp, IND.dimFactColumn)
                if not fc or not d_code:
                    continue
                dim_tbl_uri = g.value(dapp, IND.dimTable)
                _dtbl_name   = _v(dim_tbl_uri, IND.tableName)   if dim_tbl_uri else None
                _dtbl_schema = _v(dim_tbl_uri, IND.schemaName)  if dim_tbl_uri else None
                _dtbl_full   = (f"`{_dtbl_schema}`.`{_dtbl_name}`"
                                if _dtbl_schema and _dtbl_name else
                                (f"`{_dtbl_name}`" if _dtbl_name else None))
                app_entry = {
                    "cn_name":          d_cn,
                    "dim_fact_col":     fc,
                    "dim_type_code":    d_type,
                    "view_type_code":   view_type,
                    "dim_tbl_name":     _dtbl_name,
                    "dim_tbl_full":     _dtbl_full,   # 带 schema 的全限定名
                    "dim_pk":           _v(dapp, IND.dimPrimaryKey),
                    "dim_display_col":  _v(dapp, IND.dimColumn),
                }
                # 检查该 app 对应的事实表是否与当前事实表一致
                fact_tbl_uri = g.value(dapp, IND.dimFactTable)
                app_fact_tbl = _v(fact_tbl_uri, IND.tableName) if fact_tbl_uri else None
                if app_fact_tbl == fact_table:
                    best = app_entry
                    break          # 找到最优，无需继续
                if not fallback:
                    fallback = app_entry

            chosen = best or fallback
            if chosen:
                dim_map[d_code] = chosen

        if not dim_map:
            result["skip_reason"] = "图谱中无可用维度"
            return result

        # ── 已选维度 code 集合（用于标记 is_selected）── #
        selected_codes = set(part2.get("selected_dim_codes", []))

        # ── 时间过滤片段 ── #
        # 返回 (sql_fragment, [param, ...])，param 始终为列表以便直接拼接
        def _time_clause(period: str):
            import datetime as _dt2
            s = str(period)
            # 周代码：6 位纯数字，如 202618 → BETWEEN 周一 AND 周日
            if s.isdigit() and len(s) == 6:
                yr, wk = int(s[:4]), int(s[4:])
                begin = _dt2.date.fromisocalendar(yr, wk, 1).isoformat()
                end   = _dt2.date.fromisocalendar(yr, wk, 7).isoformat()
                return f"`{time_col}` BETWEEN %s AND %s", [begin, end]
            parts = s.split("-")
            if len(parts) == 2:
                return f"DATE_FORMAT(`{time_col}`, '%%Y-%%m') = %s", [s]
            elif len(parts) == 1:
                return f"YEAR(`{time_col}`) = %s", [s]
            else:
                return f"DATE(`{time_col}`) = %s", [s]

        # ── 时间维度 GROUP BY 表达式（viewTypeCode → 粒度级别, 标签, expr生成器）── #
        # 粒度级别: 0=日 < 1=周 < 2=月 < 3=季 < 4=年
        _VIEW_TYPE = {
            "1": (0, "日",  lambda c: f"DATE(`{c}`)"),
            "2": (1, "周",  lambda c: f"DATE_FORMAT(`{c}`, '%%Y-%%u周')"),
            "3": (2, "月",  lambda c: f"DATE_FORMAT(`{c}`, '%%Y-%%m')"),
            "4": (3, "季",  lambda c: f"CONCAT(YEAR(`{c}`), '-Q', QUARTER(`{c}`))"),
            "5": (4, "年",  lambda c: f"YEAR(`{c}`)"),
        }

        def _period_gran(period: str) -> int:
            """当前过滤周期的粒度：年=4，月=2，日=0"""
            parts = str(period).split("-")
            if len(parts) == 1:   return 4
            if len(parts) == 2:   return 2
            return 0

        period_gran = _period_gran(current_period)

        extra = f"AND ({where_cond})" if where_cond.strip() else ""
        full_tbl = f"`{db_schema}`.`{fact_table}`" if db_schema else f"`{fact_table}`"
        metric_expr = f"{agg_op}(`{fact_col}`)" if fact_col else "COUNT(*)"

        # ── 连接 DB ── #
        try:
            conn = pymysql.connect(
                host=db_host, port=db_port, user=db_user,
                password=db_pwd, database=db_name, charset="utf8mb4",
            )
        except Exception as e:
            result["skip_reason"] = f"DB 连接失败: {e}"
            return result

        tf_curr, tv_curr = _time_clause(current_period)
        tf_prev, tv_prev = _time_clause(previous_period)

        kg_dims: list = []
        seen_non_time_cols: set = set()
        seen_time_keys: set = set()
        top_dim_cand: dict = {}   # 波动最大的非时间维度，用于关联维度明细分析
        max_dim_chg: float = 0.0
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            for d_code, d_info in dim_map.items():
                # 若有聚焦维度集合，跳过不在其中的非时间维度
                vtc_check = d_info.get("view_type_code")
                if focus_dim_codes and not vtc_check and d_code not in focus_dim_codes:
                    continue
                dim_fc      = d_info["dim_fact_col"]
                cn          = d_info["cn_name"]
                vtc         = d_info.get("view_type_code")        # 非空 = 时间维度
                dim_type    = d_info.get("dim_type_code", "0")
                dim_tbl     = d_info.get("dim_tbl_full") or d_info.get("dim_tbl_name")
                dim_tbl_name = d_info.get("dim_tbl_name")   # 仅表名（用于日志）
                dim_pk      = d_info.get("dim_pk")
                dim_display = d_info.get("dim_display_col")

                if vtc:
                    # ── 时间维度：按 viewTypeCode 决定 GROUP BY 表达式 ── #
                    vt_info = _VIEW_TYPE.get(str(vtc))
                    if not vt_info:
                        continue
                    vt_gran, vt_label, vt_group = vt_info
                    # 只展示比当前过滤粒度更细的时间维度
                    if vt_gran >= period_gran:
                        continue
                    time_key = (dim_fc, vtc)
                    if time_key in seen_time_keys:
                        continue
                    seen_time_keys.add(time_key)
                    group_expr = vt_group(dim_fc)
                    cn = f"{cn}（按{vt_label}）"
                    use_dim_join = False
                else:
                    # ── 普通维度 ── #
                    # 有维度表时直接 JOIN 并按 display 列分组，避免多个维度共享同一 FK 列
                    # 导致 type/city/area 查询结果完全相同的 bug
                    if dim_tbl and dim_pk and dim_display:
                        use_dim_join = True
                        group_expr   = f"`d`.`{dim_display}`"
                        non_time_key = (dim_fc, dim_tbl, dim_display)
                    else:
                        use_dim_join = False
                        group_expr   = f"`{dim_fc}`"
                        non_time_key = (dim_fc, dim_tbl, dim_display)
                    if non_time_key in seen_non_time_cols:
                        continue
                    seen_non_time_cols.add(non_time_key)

                # 时间维度无需 lookup（GROUP BY 表达式已产生可读值）
                use_lookup = (not vtc and not use_dim_join and dim_type == "2"
                              and dim_tbl and dim_pk and dim_display)
                try:
                    if use_dim_join:
                        # JOIN 维度表：GROUP BY 显示列，直接得到可读维度值
                        mex = f"{agg_op}(`f`.`{fact_col}`)" if fact_col else "COUNT(*)"
                        sql = (
                            f"SELECT `d`.`{dim_display}` AS dim_val, {mex} AS meas_val "
                            f"FROM {full_tbl} f "
                            f"LEFT JOIN {dim_tbl} d ON `f`.`{dim_fc}` = `d`.`{dim_pk}` "
                            f"WHERE {{tf}} {extra} "
                            f"GROUP BY `d`.`{dim_display}` ORDER BY meas_val DESC LIMIT 30"
                        )
                    else:
                        sql = (
                            f"SELECT {group_expr} AS dim_val, {metric_expr} AS meas_val "
                            f"FROM {full_tbl} "
                            f"WHERE {{tf}} {extra} "
                            f"GROUP BY {group_expr} ORDER BY meas_val DESC LIMIT 30"
                        )
                    cur.execute(sql.format(tf=tf_curr), tv_curr)
                    curr_rows_raw = {str(r["dim_val"]): _safe_float(r["meas_val"])
                                     for r in cur.fetchall()}
                    cur.execute(sql.format(tf=tf_prev), tv_prev)
                    prev_rows_raw = {str(r["dim_val"]): _safe_float(r["meas_val"])
                                     for r in cur.fetchall()}
                except Exception as e:
                    self._log(f"  ⚠ 维度 {d_code} 查询失败: {e}")
                    continue

                # ── 用维度表 lookup 把 ID 转为显示名 ── #
                id_to_name: dict = {}
                if use_lookup:
                    all_ids = list(set(curr_rows_raw) | set(prev_rows_raw))
                    if all_ids:
                        ph = ",".join(["%s"] * len(all_ids))
                        try:
                            cur.execute(
                                f"SELECT `{dim_pk}`, `{dim_display}` "
                                f"FROM {dim_tbl} WHERE `{dim_pk}` IN ({ph})",
                                all_ids,
                            )
                            for row in cur.fetchall():
                                raw_pk  = row.get(dim_pk)
                                raw_dsp = row.get(dim_display)
                                if raw_pk is not None and raw_dsp is not None:
                                    id_to_name[str(raw_pk)] = str(raw_dsp)
                        except Exception as e:
                            self._log(f"  ⚠ 维度表 {dim_tbl_name} lookup 失败: {e}")

                def _name(val: str) -> str:
                    return id_to_name.get(val, val) if id_to_name else val

                # 以显示名为 key 合并（同名合并累加）
                curr_rows: dict = {}
                for k, v in curr_rows_raw.items():
                    curr_rows[_name(k)] = (curr_rows.get(_name(k)) or 0) + (v or 0)
                prev_rows: dict = {}
                for k, v in prev_rows_raw.items():
                    prev_rows[_name(k)] = (prev_rows.get(_name(k)) or 0) + (v or 0)

                curr_total = sum(v for v in curr_rows.values() if v is not None)
                prev_total = sum(v for v in prev_rows.values() if v is not None)
                total_chg_pct = _pct_change(curr_total, prev_total)

                movers = []
                for val in set(curr_rows) | set(prev_rows):
                    cv = curr_rows.get(val) or 0
                    pv = prev_rows.get(val) or 0
                    cs = round(cv / curr_total, 4) if curr_total else 0
                    ps = round(pv / prev_total, 4) if prev_total else 0
                    movers.append({
                        "value":         val,
                        "current":       round(cv, 4),
                        "previous":      round(pv, 4),
                        "change":        round(cv - pv, 4),
                        "current_share": cs,
                        "prev_share":    ps,
                        "share_change":  round(cs - ps, 4),
                    })
                movers.sort(key=lambda x: abs(x["share_change"]), reverse=True)

                kg_dims.append({
                    "dim_code":        d_code,
                    "cn_name":         cn,
                    "dim_fact_col":    dim_fc,
                    "is_selected":     d_code in selected_codes,
                    "total_current":   round(curr_total, 4),
                    "total_previous":  round(prev_total, 4),
                    "total_change_pct": total_chg_pct,
                    "top_movers":      movers[:5],
                })

                # 记录波动最大的非时间维度候选（后续做关联维度交叉分析）
                if not vtc and abs(total_chg_pct or 0) > max_dim_chg:
                    max_dim_chg = abs(total_chg_pct or 0)
                    top_dim_cand = {
                        "d_code": d_code, "dim_fc": dim_fc, "cn": cn,
                        "id_to_name":    dict(id_to_name),
                        "curr_rows_raw": dict(curr_rows_raw),
                        "prev_rows_raw": dict(prev_rows_raw),
                        "movers":        movers,
                        "dim_type": dim_type, "dim_tbl": dim_tbl,
                        "dim_pk":   dim_pk,   "dim_display": dim_display,
                        "total_chg_pct": total_chg_pct,
                    }

            # ── Section A: 同源兄弟指标实值查询 ──────────────────────────────── #
            sibling_data: list = []
            for sib in part2.get("siblings", [])[:12]:
                sib_code = sib.get("code")
                if not sib_code:
                    continue
                sib_inst = None
                for s, _, _ in g.triples((None, IND.code, _Lit(sib_code))):
                    sib_inst = s
                    break
                if not sib_inst:
                    continue
                sib_mapps = list(g.objects(sib_inst, IND.hasMeasureApp))
                if not sib_mapps:
                    continue
                sm        = sib_mapps[0]
                sib_fc    = _v(sm, IND.factColumn)
                sib_agg   = (_v(sm, IND.aggOperator) or "SUM").upper()
                sib_where = _v(sm, IND.whereCondition) or ""
                sib_tbl_u = g.value(sm, IND.appliesToTable)
                sib_tbl   = _v(sib_tbl_u, IND.tableName) if sib_tbl_u else fact_table
                if not sib_fc:
                    continue
                sib_extra = f"AND ({sib_where})" if sib_where.strip() else ""
                sib_ftbl  = f"`{db_schema}`.`{sib_tbl}`" if db_schema else f"`{sib_tbl}`"
                sib_mexpr = f"{sib_agg}(`{sib_fc}`)"
                try:
                    sql = f"SELECT {sib_mexpr} AS v FROM {sib_ftbl} WHERE {{tf}} {sib_extra}"
                    cur.execute(sql.format(tf=tf_curr), tv_curr)
                    r = cur.fetchone(); sc = _safe_float(r["v"]) if r else None
                    cur.execute(sql.format(tf=tf_prev), tv_prev)
                    r = cur.fetchone(); sp = _safe_float(r["v"]) if r else None
                    sibling_data.append({
                        "code":       sib_code,
                        "cnName":     sib.get("cnName", sib_code),
                        "table":      sib_tbl,
                        "current":    round(sc, 4) if sc is not None else None,
                        "previous":   round(sp, 4) if sp is not None else None,
                        "change_pct": _pct_change(sc, sp),
                    })
                except Exception as e:
                    self._log(f"  ⚠ 兄弟指标 {sib_code} 查询失败: {e}")

            # ── Section B: 波动最大维度的关联维度明细交叉分析 ─────────────────── #
            top_dim_drill: dict = {}
            if top_dim_cand:
                tdc        = top_dim_cand
                top_fc     = tdc["dim_fc"]
                top_i2n    = tdc["id_to_name"]    # {fk_str: display_name}
                top_movers = tdc["movers"]         # sorted by abs(share_change)

                # 反向映射：display_name → [fk_str, ...]
                name_to_ids: dict = {}
                for fk, nm in top_i2n.items():
                    name_to_ids.setdefault(nm, []).append(fk)
                # 无 lookup 时 FK 即展示值
                if not top_i2n:
                    for fk in set(tdc["curr_rows_raw"]) | set(tdc["prev_rows_raw"]):
                        name_to_ids[fk] = [fk]

                # 选取最多 3 个不同 dim_fc 的非时间维度做交叉分析
                cross_dims: list = []
                seen_cross_fc: set = set()
                for rc, ri in dim_map.items():
                    if ri["dim_fact_col"] == top_fc or ri.get("view_type_code"):
                        continue
                    rfc = ri["dim_fact_col"]
                    if rfc in seen_cross_fc:
                        continue
                    seen_cross_fc.add(rfc)
                    cross_dims.append((rc, ri))
                    if len(cross_dims) >= 3:
                        break

                mover_details: list = []
                for mv in top_movers[:3]:
                    mv_name = mv["value"]
                    fk_vals = name_to_ids.get(mv_name, [mv_name])
                    ph      = ",".join(["%s"] * len(fk_vals))
                    related_dims_data: list = []

                    for rc, ri in cross_dims:
                        rfc       = ri["dim_fact_col"]
                        r_tbl     = ri.get("dim_tbl_name")
                        r_pk      = ri.get("dim_pk")
                        r_dsp     = ri.get("dim_display_col")
                        r_dtype   = ri.get("dim_type_code", "0")
                        r_use_lkp = (r_dtype == "2" and r_tbl and r_pk and r_dsp)
                        r_grp     = f"`{rfc}`"
                        try:
                            sql = (
                                f"SELECT {r_grp} AS dim_val, {metric_expr} AS v "
                                f"FROM {full_tbl} "
                                f"WHERE {{tf}} AND `{top_fc}` IN ({ph}) {extra} "
                                f"GROUP BY {r_grp} ORDER BY v DESC LIMIT 5"
                            )
                            cur.execute(sql.format(tf=tf_curr), tv_curr + fk_vals)
                            cr = {str(r["dim_val"]): _safe_float(r["v"]) for r in cur.fetchall()}
                            cur.execute(sql.format(tf=tf_prev), tv_prev + fk_vals)
                            pr = {str(r["dim_val"]): _safe_float(r["v"]) for r in cur.fetchall()}
                        except Exception as e:
                            self._log(f"  ⚠ 交叉分析 {rc} 查询失败: {e}")
                            continue

                        # 维度表 lookup
                        r_i2n: dict = {}
                        if r_use_lkp:
                            all_rids = list(set(cr) | set(pr))
                            if all_rids:
                                ph2 = ",".join(["%s"] * len(all_rids))
                                try:
                                    cur.execute(
                                        f"SELECT `{r_pk}`, `{r_dsp}` "
                                        f"FROM `{r_tbl}` WHERE `{r_pk}` IN ({ph2})",
                                        all_rids,
                                    )
                                    for row in cur.fetchall():
                                        rk = row.get(r_pk); rv = row.get(r_dsp)
                                        if rk is not None and rv is not None:
                                            r_i2n[str(rk)] = str(rv)
                                except Exception:
                                    pass

                        def _rname(v: str, _m=r_i2n) -> str:
                            return _m.get(v, v) if _m else v

                        cr_n: dict = {}
                        for k, v in cr.items():
                            cr_n[_rname(k)] = (cr_n.get(_rname(k)) or 0) + (v or 0)
                        pr_n: dict = {}
                        for k, v in pr.items():
                            pr_n[_rname(k)] = (pr_n.get(_rname(k)) or 0) + (v or 0)

                        r_tc = sum(v for v in cr_n.values() if v)
                        r_tp = sum(v for v in pr_n.values() if v)
                        r_movers: list = []
                        for val in set(cr_n) | set(pr_n):
                            cv = cr_n.get(val) or 0
                            pv = pr_n.get(val) or 0
                            cs = round(cv / r_tc, 4) if r_tc else 0
                            ps = round(pv / r_tp, 4) if r_tp else 0
                            r_movers.append({
                                "value":        val,
                                "current":      round(cv, 4),
                                "previous":     round(pv, 4),
                                "change_pct":   _pct_change(cv, pv),
                                "share_change": round(cs - ps, 4),
                            })
                        r_movers.sort(key=lambda x: abs(x.get("share_change") or 0), reverse=True)
                        related_dims_data.append({
                            "dim_code": rc,
                            "cn_name":  ri["cn_name"],
                            "top_values": r_movers[:5],
                        })

                    mover_details.append({
                        "value":        mv_name,
                        "current":      mv["current"],
                        "previous":     mv["previous"],
                        "share_change": mv["share_change"],
                        "related_dims": related_dims_data,
                    })

                top_dim_drill = {
                    "dim_code":        tdc["d_code"],
                    "cn_name":         tdc["cn"],
                    "total_change_pct": tdc["total_chg_pct"],
                    "mover_details":   mover_details,
                }

        conn.close()

        # 按总体变化幅度降序
        kg_dims.sort(key=lambda x: abs(x.get("total_change_pct") or 0), reverse=True)

        result["kg_dimensions"]   = kg_dims
        result["sibling_data"]    = sibling_data
        result["top_dim_drill"]   = top_dim_drill
        result["current_period"]  = current_period
        result["previous_period"] = previous_period
        result["fact_table"]      = fact_table
        result["metric_col"]      = fact_col
        return result

    # ── LLM 公共调用 ────────────────────────────────────────────────────── #

    def _llm_call(self, system_prompt: str, user_content: str,
                  max_tokens: int = 1024) -> str:
        """调用 LLM（Anthropic/OpenAI 兼容格式）。失败返回空串。"""
        import urllib.request as _ur
        api_key  = self._llm_config.get("api_key", "")
        base_url = self._llm_config.get("base_url", "").rstrip("/")
        model    = self._llm_config.get("model", "GPT5.5")
        is_anthropic = "anthropic" in base_url.lower()
        if not api_key or not base_url:
            return "（LLM 未配置）"
        if is_anthropic:
            payload = json.dumps({
                "model": model, "max_tokens": max_tokens,
                "messages": [{"role": "user", "content": user_content}],
                "system": system_prompt,
            }).encode("utf-8")
            req = _ur.Request(
                f"{base_url}/messages", data=payload,
                headers={"Content-Type": "application/json",
                         "x-api-key": api_key, "anthropic-version": "2023-06-01"},
                method="POST",
            )
        else:
            combined = system_prompt + "\n\n" + user_content
            payload = json.dumps({
                "model": model, "max_tokens": max_tokens,
                "messages": [{"role": "user", "content": combined}],
            }).encode("utf-8")
            req = _ur.Request(
                f"{base_url}/chat/completions", data=payload,
                headers={"Content-Type": "application/json",
                         "Authorization": f"Bearer {api_key}"},
                method="POST",
            )
        if self._cancel_cb and self._cancel_cb():
            raise RuntimeError("分析已被新任务取消，跳过 LLM 调用")
        with _ur.urlopen(req, timeout=90) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        if is_anthropic:
            content = data.get("content", [])
            stop_reason = data.get("stop_reason") or data.get("finish_reason", "")
            if content and isinstance(content, list):
                text = content[0].get("text", "") or ""
            else:
                text = ""
        else:
            choices = data.get("choices", [])
            if choices:
                text = choices[0].get("message", {}).get("content", "") or ""
            else:
                text = ""
            stop_reason = data.get("finish_reason", "")
        if text:
            _text_warn = text
            if is_anthropic and stop_reason == "max_tokens":
                self._log(f"  ⚠ LLM 输出达到 token 上限（max_tokens={max_tokens}），解读可能不完整")
            if (not is_anthropic) and stop_reason == "length":
                self._log(f"  ⚠ LLM 输出达到 token 上限（max_tokens={max_tokens}），解读可能不完整")
            # Strip chain-of-thought / reasoning noise from models that output it
            text = self._strip_reasoning(text)
            return text
        return ""

    @staticmethod
    def _strip_reasoning(text: str) -> str:
        """Remove chain-of-thought / reasoning noise from LLM output.

        Some models (MiniMax, DeepSeek) output their internal reasoning as part
        of the content even when told not to. This strips common patterns:
        - Lines that look like self-talk ("I need to...", "Let me...",
          "The user says...", "Must output...", etc.)
        - Blanket instruction recitation
        - Rewrite iterations where the model tries multiple versions
        """
        lines = text.strip().split("\n")
        cleaned: list[str] = []
        in_reasoning = False
        for line in lines:
            stripped = line.strip()
            # Detect reasoning patterns (model talking to itself in English)
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
            # Also detect "The ... should ..." meta-instruction patterns
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
                in_reasoning = True
                continue
            in_reasoning = False
            cleaned.append(line)
        result = "\n".join(cleaned).strip()
        # If stripping removed everything, return original
        return result if result else text

    _PART_META = {
        "p1": ("Part 1 — 波动识别",         "Part 2 — 统计量化"),
        "p2": ("Part 2 — 统计量化",          "Part 3 — 结构贡献度分析"),
        "p3": ("Part 3 — 结构贡献度分析",    "Part 4 — KG 图谱关系分析"),
        "p4": ("Part 4 — KG 图谱关系分析",   "Part 5 — 归因下钻分析"),
        "p5": ("Part 5 — 归因下钻分析",      "Part 6 — 综合报告"),
        "p6": ("Part 6 — 综合报告",          ""),
    }

    # ── Part 3: 结构贡献度分析 ───────────────────────────────────────────── #

    def _analyze_structure_contribution(self, part1: dict) -> dict:
        """
        结构贡献度分析（Part 3）：基于 Part1 已计算的 LMDI 结果，进行：
        1. 帕累托分析：Top N 维度值贡献累计 80% 变化量
        2. 正/负贡献拆解：哪些维度值在拉升，哪些在拖累
        3. 维度层级排行：按维度汇总绝对贡献量
        4. 指标贡献汇总：各指标的贡献量占比
        """
        result: dict = {}

        global_top20 = part1.get("global_top20", [])
        if not global_top20:
            result["skip_reason"] = "Part1 无 LMDI 数据，结构贡献度分析跳过"
            return result

        result["current_period"]  = part1.get("current_period")
        result["previous_period"] = part1.get("previous_period")

        # ── 1. 帕累托分析 ──────────────────────────────────────────────── #
        total_abs = sum(abs(m.get("lmdi_contrib", 0) or 0) for m in global_top20)
        pareto_items = []
        cum = 0.0
        for i, m in enumerate(global_top20):
            lc = m.get("lmdi_contrib", 0) or 0
            cum += abs(lc)
            pareto_items.append({
                "rank":        i + 1,
                "dim_col":     m.get("dim_col", ""),
                "dim_cn":      m.get("dim_cn", m.get("dim_col", "")),
                "value":       m.get("value", ""),
                "meas_cn":     m.get("meas_cn", m.get("meas_col", "")),
                "lmdi_contrib": round(lc, 6),
                "contrib_pct": m.get("contrib_pct"),
                "cum_pct":     round(cum / total_abs * 100, 1) if total_abs else None,
            })

        pareto_80_idx = next(
            (p["rank"] for p in pareto_items if (p["cum_pct"] or 0) >= 80),
            len(pareto_items)
        )
        result["pareto"] = {
            "items":           pareto_items,
            "pareto_80_count": pareto_80_idx,
            "total_items":     len(global_top20),
            "total_abs_change": round(total_abs, 6),
        }

        # ── 2. 正/负贡献拆解 ──────────────────────────────────────────── #
        pos_items = [m for m in global_top20 if (m.get("lmdi_contrib") or 0) > 0]
        neg_items = [m for m in global_top20 if (m.get("lmdi_contrib") or 0) < 0]
        pos_sum = sum(m.get("lmdi_contrib", 0) or 0 for m in pos_items)
        neg_sum = sum(m.get("lmdi_contrib", 0) or 0 for m in neg_items)

        result["pos_neg_split"] = {
            "positive_count": len(pos_items),
            "negative_count": len(neg_items),
            "positive_sum":   round(pos_sum, 6),
            "negative_sum":   round(neg_sum, 6),
            "net_change":     round(pos_sum + neg_sum, 6),
            "top3_positive":  [
                {"dim_cn": m.get("dim_cn", m.get("dim_col", "")),
                 "value": m.get("value", ""),
                 "contrib": round(m.get("lmdi_contrib", 0), 6)}
                for m in sorted(pos_items, key=lambda x: x.get("lmdi_contrib", 0), reverse=True)[:3]
            ],
            "top3_negative":  [
                {"dim_cn": m.get("dim_cn", m.get("dim_col", "")),
                 "value": m.get("value", ""),
                 "contrib": round(m.get("lmdi_contrib", 0), 6)}
                for m in sorted(neg_items, key=lambda x: x.get("lmdi_contrib", 0))[:3]
            ],
        }

        # ── 3. 维度层级贡献排行 ────────────────────────────────────────── #
        dim_agg: dict = {}
        for m in global_top20:
            dc = m.get("dim_col", "")
            cn = m.get("dim_cn", dc)
            if dc not in dim_agg:
                dim_agg[dc] = {"dim_col": dc, "dim_cn": cn,
                               "total_abs": 0.0, "total_net": 0.0, "item_count": 0}
            lc = m.get("lmdi_contrib", 0) or 0
            dim_agg[dc]["total_abs"]  += abs(lc)
            dim_agg[dc]["total_net"]  += lc
            dim_agg[dc]["item_count"] += 1

        dim_rank = sorted(dim_agg.values(), key=lambda x: x["total_abs"], reverse=True)
        for d in dim_rank:
            d["total_abs"] = round(d["total_abs"], 6)
            d["total_net"] = round(d["total_net"], 6)
            d["abs_pct"]   = round(d["total_abs"] / total_abs * 100, 1) if total_abs else None
        result["dimension_rank"] = dim_rank

        # ── 4. 指标贡献汇总 ───────────────────────────────────────────── #
        meas_agg: dict = {}
        for m in global_top20:
            mc = m.get("meas_col", "")
            cn = m.get("meas_cn", mc)
            if mc not in meas_agg:
                meas_agg[mc] = {"meas_col": mc, "meas_cn": cn,
                                "total_abs": 0.0, "total_net": 0.0}
            lc = m.get("lmdi_contrib", 0) or 0
            meas_agg[mc]["total_abs"] += abs(lc)
            meas_agg[mc]["total_net"] += lc

        meas_rank = sorted(meas_agg.values(), key=lambda x: x["total_abs"], reverse=True)
        for d in meas_rank:
            d["total_abs"] = round(d["total_abs"], 6)
            d["total_net"] = round(d["total_net"], 6)
            d["abs_pct"]   = round(d["total_abs"] / total_abs * 100, 1) if total_abs else None
        result["measure_rank"] = meas_rank

        return result

    def _generate_part_plan(self, part_key: str, prev_focuses: dict) -> dict:
        """
        根据上一 Part 的"下阶段重点"，为当前 Part 生成结构化任务规划。
        返回 {"part_name": str, "prev_focus": str, "tasks": [str, ...], "core_goal": str}
        无论 prev_focus 是否为空，都调用 LLM 生成规划。
        """
        part_name, _ = self._PART_META.get(part_key, (part_key, ""))

        # 取最近一条"下阶段重点"作为主要导向（允许为空，此时用默认描述）
        prev_focus = list(prev_focuses.values())[-1] if prev_focuses else ""

        # 所有前序重点（含更早的）
        all_prev = "\n".join(f"- {k}：{v}" for k, v in prev_focuses.items()) if prev_focuses else ""

        system_prompt = (
            f"你是数据分析规划助手。为「{part_name}」制定具体分析任务规划。\n"
            "【重要】直接输出任务列表，不要写思考过程、推理步骤或任何解释。\n"
            "要求：\n"
            "① 若有上阶段重点，任务必须紧扣其中指出的异常方向、具体维度或指标\n"
            "② 若无明确上阶段重点，则根据本阶段的标准分析方法给出任务\n"
            "③ 每条任务描述具体的分析动作（验证/量化/追溯/下钻/对比等），而非泛泛而谈\n"
            "④ 输出 3-5 条任务，每条以「• 」开头，一行一条，不要编号\n"
            "⑤ 最后单独一行输出「本阶段核心目标：[一句话总结]」\n"
            "只输出任务列表和核心目标，不要其他说明。"
        )
        if prev_focus:
            user_msg = (
                f"【前序各阶段重点摘要】\n{all_prev}\n\n"
                f"【最新下阶段重点（直接导向本阶段）】\n{prev_focus}\n\n"
                f"请为「{part_name}」生成分析任务规划。"
            )
        else:
            user_msg = (
                f"当前没有明确的上阶段重点导向，请根据「{part_name}」的标准分析目标，"
                f"生成 3-5 条具体分析任务规划。"
            )

        try:
            self._log(f"  ✦ {part_name} 任务规划生成中…")
            raw = self._llm_call(system_prompt, user_msg, max_tokens=2048)
        except Exception as e:
            self._log(f"  ⚠ {part_name} 任务规划失败: {e}")
            raw = f"• 按上阶段重点方向展开分析：{prev_focus}"

        # 解析任务列表
        tasks = []
        core_goal = ""
        for line in raw.strip().split("\n"):
            line = line.strip()
            if line.startswith("• "):
                tasks.append(line[2:].strip())
            elif line.startswith("本阶段核心目标："):
                core_goal = line[len("本阶段核心目标："):].strip()
            elif line.startswith("本阶段核心目标:"):
                core_goal = line[len("本阶段核心目标:"):].strip()

        return {
            "part_name":  part_name,
            "prev_focus": prev_focus,
            "tasks":      tasks if tasks else [prev_focus],
            "core_goal":  core_goal,
        }

    def _part_interp(self, part_key: str, part_data: dict,
                     prev_focuses: dict, suppress_focus: bool = False) -> dict:
        """
        为指定 Part 生成 AI 解读 + 下阶段重点。
        suppress_focus=True 时不生成"下阶段重点"（例如日粒度下的 Part2）。
        返回 {"text": markdown_str, "focus": str}
        """
        part_name, next_part = self._PART_META.get(part_key, ("分析阶段", ""))
        if suppress_focus:
            next_part = ""

        # ── 精简数据（避免 token 过多）── #
        def _slim(d):
            if not isinstance(d, dict):
                return d
            out = {}
            for k, v in d.items():
                if k in ("sample_rows", "raw_rows", "time_series",
                          "expr_tree", "operand_dim_breakdown"):
                    continue
                if isinstance(v, list) and len(v) > 6:
                    out[k] = v[:6]
                elif isinstance(v, dict) and len(v) > 8:
                    out[k] = dict(list(v.items())[:8])
                else:
                    out[k] = v
            return out

        prev_str = ""
        if prev_focuses:
            lines = [f"- {k}：{v}" for k, v in prev_focuses.items()]
            prev_str = "【前序阶段重点摘要】\n" + "\n".join(lines) + "\n\n"

        if next_part:
            system_prompt = (
                f"你是一位专业数据分析师，当前正在完成「{part_name}」分析。\n"
                "请完成两项输出：\n"
                "① 用 3-5 句中文解读本阶段关键发现（重点突出异常、驱动因素、业务含义）\n"
                f"② 给出「{next_part}」阶段应重点关注的具体方向（1-2 句，紧扣本阶段异常）\n\n"
                "【强制要求】无论数据是否充分，必须在最后一行输出如下格式（不得省略）：\n"
                f"▶ 下阶段重点：[针对{next_part}的具体分析方向]\n\n"
                "严格按如下格式输出（不要多余标题、不要思考过程、不要推理步骤）：\n"
                "[正文解读段落]\n\n"
                f"▶ 下阶段重点：[针对{next_part}的具体分析方向]"
            )
        else:
            system_prompt = (
                f"你是一位经验丰富的业务运营负责人，正在给团队做总结汇报。\n"
                "请用大白话、口语化的方式，把这两天发生的事情讲清楚。\n"
                "不要用统计学术语（不要说'环比'、'LMDI'、'维度贡献'、'置信区间'等），"
                "用'昨天比前天少了多少'、'哪类用户反馈变差了'这样的表达方式。\n"
                "内容要包含：这两天核心指标怎么变的、问题出在哪里、为什么、"
                "接下来建议怎么做（3 条以内，具体落地）。全程中文，5-8 句话。\n"
                "【重要】直接输出内容，不要写思考过程、推理步骤、格式说明。"
            )

        data_str = json.dumps(_slim(part_data), ensure_ascii=False,
                              indent=2, cls=_SafeEncoder)
        user_content = (
            f"{prev_str}"
            f"本阶段分析数据（JSON 精简版）：\n```json\n{data_str}\n```\n\n"
            "请按格式输出解读。"
        )

        try:
            self._log(f"  ✦ {part_name} AI 解读中…")
            text = self._llm_call(system_prompt, user_content, max_tokens=4096)
        except Exception as e:
            self._log(f"  ⚠ {part_name} AI 解读失败: {e}")
            text = f"（AI 解读暂不可用）"

        # 提取 "▶ 下阶段重点：" 行（兼容多种格式）
        focus = ""
        for line in reversed(text.strip().split("\n")):
            s = line.strip()
            for prefix in ("▶ 下阶段重点：", "▶ 下阶段重点:", "下阶段重点：", "下阶段重点:"):
                if s.startswith(prefix):
                    focus = s[len(prefix):].strip()
                    break
            if focus:
                break

        # ── Fallback：LLM 未输出 focus 时，从数据中自动生成 ── #
        if not focus and next_part:
            focus = self._fallback_focus(part_key, part_data, next_part)

        return {"text": text, "focus": focus}

    def _fallback_focus(self, part_key: str, part_data: dict, next_part: str) -> str:
        """当 LLM 未输出"下阶段重点"时，从数据中自动生成一条兜底 focus。"""
        try:
            if part_key == "p2":
                anom = part_data.get("anomaly_periods") or {}
                corr = (part_data.get("correlation") or {}).get("dim_meas") or {}
                desc = part_data.get("descriptive") or {}
                hints = []
                for col, rows in list(anom.items())[:2]:
                    if rows:
                        r = rows[0]
                        hints.append(f"{col} 在 {r['period']} 出现{r['label']}")
                if not hints and corr:
                    k, v = next(iter(corr.items()))
                    hints.append(f"{k} 呈{v.get('interp','相关关系')}")
                if not hints and desc:
                    col = next(iter(desc))
                    d = desc[col]
                    hints.append(f"{col} 均值 {d.get('mean')}，标准差 {d.get('std')}")
                base = "、".join(hints) if hints else "关键指标波动情况"
                return f"针对{next_part}，重点分析 {base} 的结构性原因及各维度贡献占比"
            if part_key == "p3":
                top = (part_data.get("top_contributors") or [])[:2]
                if top:
                    vals = "、".join(
                        f"{t.get('dim_cn', t.get('dim_col'))}={t.get('value')}"
                        for t in top if t.get("value")
                    )
                    return f"针对{next_part}，重点追踪 {vals} 通过图谱关联的上游驱动因素"
                return f"针对{next_part}，重点通过图谱关联发现主要贡献维度的驱动逻辑"
            if part_key == "p4":
                return f"针对{next_part}，重点对图谱发现的高波动维度值进行下钻明细分析"
            if part_key == "p5":
                return f"针对{next_part}，综合以上各阶段发现形成完整归因链路和业务建议"
            return f"针对{next_part}，延续本阶段发现的关键问题深入分析"
        except Exception:
            return f"针对{next_part}，重点关注本阶段发现的关键异常点"

    # ── Part 2 KG AI: 图谱专项 AI 分析 ─────────────────────────────────── #

    def _generate_kg_report(self, part1: dict, part2: dict,
                            part_kg_attr: Optional[dict] = None,
                            focus_dim_codes: Optional[set] = None,
                            part2_stats: Optional[dict] = None,
                            part3_contrib: Optional[dict] = None) -> str:
        """结合当期波动数据、图谱关系和扩展归因，生成专项 AI 分析报告。"""
        import urllib.request as _ureq

        api_key  = self._llm_config.get("api_key", "")
        base_url = self._llm_config.get("base_url", "").rstrip("/")
        model    = self._llm_config.get("model", "GPT5.5")

        # ── 构建聚焦摘要（Parts 1/2/3 发现的问题）── #
        focus_lines = []
        if focus_dim_codes:
            focus_lines.append(f"【聚焦维度（来自 Parts 1/2/3 问题汇总）】：{', '.join(sorted(focus_dim_codes))}")
        # Part 1 Top3 问题
        p1_top3 = part1.get("global_top20", [])[:3]
        if p1_top3:
            top_str = "；".join(
                f"{m.get('dim_cn', m.get('dim_col'))}={m.get('value')}（贡献{m.get('lmdi_contrib', 0):+.4f}）"
                for m in p1_top3
            )
            focus_lines.append(f"Part1 Top3 LMDI贡献：{top_str}")
        # Part 2 统计异常
        if part2_stats:
            anom_keys = list((part2_stats.get("anomaly_detection") or {}).keys())
            if anom_keys:
                focus_lines.append(f"Part2 异常维度：{anom_keys}")
        # Part 3 Pareto Top3
        if part3_contrib:
            dr_top3 = (part3_contrib.get("dimension_rank") or [])[:3]
            if dr_top3:
                dr_str = "；".join(
                    f"{d.get('dim_cn', d.get('dim_col'))}（{d.get('abs_pct')}%）"
                    for d in dr_top3
                )
                focus_lines.append(f"Part3 Pareto Top3：{dr_str}")

        focus_prefix = ""
        if focus_lines:
            focus_prefix = (
                "【分析聚焦说明】\n"
                "本次 KG 分析已从 Parts 1/2/3 中提取有问题的数据，仅针对以下内容展开图谱分析：\n"
                + "\n".join(f"  - {l}" for l in focus_lines)
                + "\n请围绕上述问题展开，而非泛泛介绍全量数据。\n\n"
            )

        system_prompt = (
            "你是一位资深数据指标分析师，精通业务知识图谱。"
            "图谱扩展归因数据已聚焦于 Parts 1/2/3 识别的有问题维度，"
            "请直接使用提供的分析结果，不要说'建议分析'或'尚未分析'。"
            "请根据提供的数据，用中文撰写一份深入的图谱关联分析报告（Markdown 格式），包含：\n"
            "1. **聚焦问题概述**：明确指出从 Parts 1/2/3 承接了哪些核心问题，当期指标整体趋势\n"
            "2. **图谱关联解读**：结合指标来源（事实表、计算口径、衍生关系）分析问题原因\n"
            "3. **复合指标算子解析（重点）**：若指标是复合指标，逐一列出各算子当期/上期/变化%，"
            "对变化最大的算子展开维度分析，要有具体数字\n"
            "4. **聚焦维度 LMDI 贡献解析**：针对聚焦维度，说明其贡献量和业务含义\n"
            "5. **同源指标关联分析**：结合兄弟指标实际数值，分析协同/背离关系\n"
            "6. **针对性建议**：基于图谱结构和问题聚焦，给出 2~3 条可操作建议\n\n"
            "要求：语言简洁专业，数字保留两位小数，方向描述要有数据支撑，避免空泛描述，全程使用中文。\n"
            "【重要】直接输出报告内容，不要写思考过程或推理步骤。"
        )

        # ── 构建精简上下文 ── #
        current_period  = part1.get("current_period", "未知")
        previous_period = part1.get("previous_period", "未知")

        wave_lines = []
        for m in part1.get("measures", []):
            dir_str = "↑" if m.get("direction") == "up" else ("↓" if m.get("direction") == "down" else "→")
            wave_lines.append(
                f"- {m['col']}: 当期={m.get('current')}, 上期={m.get('previous')}, "
                f"变化={m.get('change')}, 变化%={m.get('change_pct')}% {dir_str}"
            )

        kg_lines = []
        for m_info in part2.get("measures", []):
            if m_info.get("error"):
                continue
            kg_lines.append(f"\n指标: {m_info.get('cnName', m_info['code'])} ({m_info['code']})")
            kg_lines.append(f"  类型: {m_info.get('type', '?')}, 分类: {m_info.get('category', '?')}")
            if m_info.get("caliber"):
                kg_lines.append(f"  口径: {m_info['caliber']}")
            if m_info.get("definition"):
                kg_lines.append(f"  业务定义: {m_info['definition']}")
            for ma in m_info.get("measureApps", [])[:2]:
                tbl = ma.get("table", {})
                kg_lines.append(
                    f"  事实表: {tbl.get('tableName', '?')}({tbl.get('cnName', '')}), "
                    f"聚合: {ma.get('aggOperator', '?')}({ma.get('factColumn', '?')})"
                )
                if ma.get("whereCondition"):
                    kg_lines.append(f"  过滤条件: {ma['whereCondition']}")
            if m_info.get("expressionTree"):
                parts_expr = []
                for op in m_info["expressionTree"]:
                    if op["type"] == "operand":
                        parts_expr.append(op.get("cnName") or op.get("measCode", "?"))
                    elif op["type"] == "operator":
                        parts_expr.append(op.get("operator", "?"))
                kg_lines.append(f"  计算公式: {' '.join(parts_expr)}")

        for d_info in part2.get("dimensions", [])[:10]:  # 只取前10个维度避免上下文过长
            if d_info.get("error"):
                continue
            kg_lines.append(f"\n维度: {d_info.get('cnName', d_info['code'])} ({d_info['code']})")
            kg_lines.append(f"  类型: {d_info.get('dimType', '?')}, 时间粒度: {d_info.get('timeGrain', '无')}")
            for da in d_info.get("dimApps", [])[:1]:
                if da.get("factTable"):
                    kg_lines.append(
                        f"  关联事实表: {da['factTable']}, 外键列: {da.get('dimFactColumn', '?')}"
                    )

        dim_contrib_lines = []
        for mv in part1.get("global_top20", [])[:10]:   # LLM只取top10避免上下文过长
            lc = mv.get("lmdi_contrib", 0) or 0
            dim_contrib_lines.append(
                f"  {mv.get('dim_cn', mv.get('dim_col', '?'))}={mv['value']} "
                f"[{mv.get('meas_cn', mv.get('meas_col', '?'))}]: "
                f"当期={mv.get('current_val')}, 上期={mv.get('prev_val')}, "
                f"LMDI贡献={lc:+.6f}, 贡献占比={mv.get('contrib_pct') or 0:+.1f}%"
            )

        # ── 所有维度的图谱归因分析结果（来自 part_kg_attr）── #
        kg_attr_dim_lines = []
        if part_kg_attr and not part_kg_attr.get("error"):
            for kd in part_kg_attr.get("kg_dimensions", []):
                sel_tag = "（已选）" if kd.get("is_selected") else "（扩展）"
                total_chg = kd.get("total_change_pct")
                chg_str = f"{total_chg:+.2f}%" if total_chg is not None else "N/A"
                kg_attr_dim_lines.append(
                    f"- {kd['cn_name']}({kd['dim_code']}){sel_tag}: "
                    f"当期={kd.get('total_current')}, 上期={kd.get('total_previous')}, 变化={chg_str}"
                )
                for mv in kd.get("top_movers", [])[:3]:
                    sc = mv.get("share_change", 0) or 0
                    kg_attr_dim_lines.append(
                        f"    [{mv['value']}] 当期={mv['current']}, 上期={mv['previous']}, "
                        f"占比变化={sc*100:+.1f}%"
                    )

        # ── 算子波动（复合指标才有）── #
        operand_lines = []
        opd_dim_lines = []
        for opd in part1.get("operand_fluctuations", []):
            chg_pct = opd.get("change_pct")
            chg_str = f"{chg_pct:+.2f}%" if chg_pct is not None else "N/A"
            dir_str = "↑" if opd.get("direction") == "up" else ("↓" if opd.get("direction") == "down" else "→")
            operand_lines.append(
                f"- {opd.get('cn_name', opd['col'])}({opd['col']}): "
                f"当期={opd.get('current')}, 上期={opd.get('previous')}, "
                f"变化%={chg_str} {dir_str}"
            )

        opd_dim_bd = part1.get("operand_dim_breakdown", {})
        for opd_code, dim_list in opd_dim_bd.items():
            ttl = self._ttl_meta() if hasattr(self, '_ttl_meta') else {}
            opd_cn = part1.get("operand_fluctuations") and next(
                (o.get("cn_name", opd_code) for o in part1["operand_fluctuations"] if o["col"] == opd_code), opd_code
            ) or opd_code
            opd_dim_lines.append(f"\n【{opd_cn}({opd_code}) 维度拆解】")
            for dim_info in dim_list:
                opd_dim_lines.append(f"  维度: {dim_info.get('dim_cn', dim_info['dim_col'])}")
                for mv in dim_info.get("top_movers", [])[:3]:
                    sc = mv.get("share_change", 0) or 0
                    opd_dim_lines.append(
                        f"    [{mv['value']}] 当期={mv['current']}, 上期={mv['previous']}, "
                        f"变化={mv['change']:+.2f}, 占比变化={sc*100:+.1f}%"
                    )
        sibling_lines = []
        if part_kg_attr and not part_kg_attr.get("error"):
            for s in part_kg_attr.get("sibling_data", []):
                chg = s.get("change_pct")
                chg_str = f"{chg:+.2f}%" if chg is not None else "N/A"
                dir_str = ("↑" if (chg or 0) > 0 else ("↓" if (chg or 0) < 0 else "→"))
                sibling_lines.append(
                    f"- {s.get('cnName', s['code'])}({s['code']}): "
                    f"当期={s.get('current')}, 上期={s.get('previous')}, "
                    f"变化%={chg_str} {dir_str}"
                )

        # ── 波动最大维度的关联明细（来自 part_kg_attr）── #
        top_drill_lines = []
        if part_kg_attr and not part_kg_attr.get("error"):
            tdd = part_kg_attr.get("top_dim_drill", {})
            if tdd:
                top_drill_lines.append(
                    f"波动最大维度: {tdd.get('cn_name')}，"
                    f"总体变化 {tdd.get('total_change_pct', 0):+.2f}%"
                )
                for md in tdd.get("mover_details", []):
                    top_drill_lines.append(
                        f"\n  [{md['value']}] 当期={md['current']}, 上期={md['previous']}, "
                        f"占比变化={md['share_change']*100:+.1f}%"
                    )
                    for rd in md.get("related_dims", []):
                        top_drill_lines.append(f"    关联维度: {rd['cn_name']}")
                        for rv in rd.get("top_values", [])[:3]:
                            top_drill_lines.append(
                                f"      {rv['value']}: 当期={rv['current']}, "
                                f"上期={rv['previous']}, "
                                f"变化%={rv.get('change_pct') or 'N/A'}"
                            )

        user_msg = (
            focus_prefix
            + "【当期波动数据】\n"
            f"时间：{previous_period} → {current_period}\n"
            + ("\n".join(wave_lines) if wave_lines else "无波动数据")
            + "\n\n【复合指标算子波动（逐算子）】\n"
            + ("\n".join(operand_lines) if operand_lines else "无算子数据（非复合指标）")
            + "\n\n【变化最大算子的维度拆解（每个算子 × 所有维度，找变化根因）】\n"
            + ("\n".join(opd_dim_lines) if opd_dim_lines else "无算子维度拆解数据")
            + "\n\n【图谱关系结构】\n"
            + ("\n".join(kg_lines) if kg_lines else "无图谱数据")
            + "\n\n【LMDI 全维度贡献 Top 10（已覆盖图谱所有维度）】\n"
            + ("\n".join(dim_contrib_lines) if dim_contrib_lines else "无维度贡献数据")
            + "\n\n【所有维度图谱归因明细（全量，含扩展维度）】\n"
            + ("\n".join(kg_attr_dim_lines) if kg_attr_dim_lines else "无归因数据")
            + "\n\n【同源兄弟指标（含实际数值）】\n"
            + ("\n".join(sibling_lines) if sibling_lines else
               "、".join(f"{s.get('cnName',s.get('code','?'))}({s.get('code','')})"
                         for s in part2.get("siblings", [])[:10]) or "无")
            + "\n\n【波动最大维度分类明细及关联维度分析】\n"
            + ("\n".join(top_drill_lines) if top_drill_lines else "无")
            + "\n\n请基于以上信息生成图谱关联分析报告。"
        )

        is_anth = "anthropic" in base_url.lower()
        if is_anth:
            payload = {
                "model": model, "max_tokens": 8192,
                "messages": [{"role": "user", "content": user_msg}],
                "system": system_prompt,
            }
            body = json.dumps(payload).encode("utf-8")
            req = _ureq.Request(
                f"{base_url}/messages", data=body,
                headers={"Content-Type": "application/json",
                         "x-api-key": api_key, "anthropic-version": "2023-06-01"},
                method="POST",
            )
        else:
            combined = system_prompt + "\n\n" + user_msg
            payload = {
                "model": model, "max_tokens": 8192,
                "messages": [{"role": "user", "content": combined}],
            }
            body = json.dumps(payload).encode("utf-8")
            req = _ureq.Request(
                f"{base_url}/chat/completions", data=body,
                headers={"Content-Type": "application/json",
                         "Authorization": f"Bearer {api_key}"},
                method="POST",
            )

        self._log("  正在调用 LLM 生成图谱 AI 分析…")
        with _ureq.urlopen(req, timeout=120) as resp:
            resp_data = json.loads(resp.read().decode("utf-8"))

        if is_anth:
            content = resp_data.get("content", [])
            if content and isinstance(content, list):
                return content[0].get("text", "")
        choices = resp_data.get("choices", [])
        if choices:
            return choices[0].get("message", {}).get("content", "")

        return f"LLM 响应异常: {json.dumps(resp_data, ensure_ascii=False)}"

    # ── Part 6: LLM 综合报告 ────────────────────────────────────────────── #

    def _generate_report(self, part1: dict, part2: dict, part3: dict, meta: dict,
                         part5: Optional[dict] = None, part_kg_attr: Optional[dict] = None,
                         prev_focuses: Optional[dict] = None) -> str:
        import urllib.request as _ureq
        import threading

        api_key  = self._llm_config.get("api_key", "")
        base_url = self._llm_config.get("base_url", "").rstrip("/")
        model    = self._llm_config.get("model", "GPT5.5")

        system_prompt = (
            "你是一位经验丰富的业务运营负责人，正在给团队讲解这两天的数据情况。"
            "说话要像和同事聊天一样自然，用大白话，不要用'LMDI分解'、'环比'、'置信区间'、'维度贡献度'这类统计术语。"
            "数字要具体（比如'从100降到95，少了5个'），原因要讲清楚（是哪里出了问题、哪个群体变了、什么情况变多了变少了）。"
            "所有维度已完成分析，数据在 PartKG_扩展归因 中，直接用里面的数字说话，不要说'建议分析'或'需要进一步分析'。"
            "请用中文，输出 Markdown 格式报告，包含以下 6 个部分：\n"
            "1. **一句话总结**（用一两句话说清楚：这两天发生了什么、严不严重）\n"
            "2. **数字怎么变的**（具体说哪个指标涨了多少、跌了多少，用'从X变成Y'的方式描述）\n"
            "3. **问题出在哪里**（是哪个渠道、哪类用户、哪个环节出了问题，要引用具体数字）\n"
            "4. **为什么会这样**（结合业务场景解释原因，比如'周末门店人少导致…'、'某类用户反馈变差因为…'）\n"
            "5. **有没有异常情况**（是否有某天数据特别突出，或者某个细分群体异常，说清楚哪里异常）\n"
            "6. **接下来怎么办**（3-5 条具体可行的建议，写清楚做什么、在哪里做、针对谁）\n\n"
            "要求：全程不用统计术语，用普通人能看懂的语言；数字加千位分隔符；建议要落地可执行；全程中文。\n"
            "【重要】直接输出报告，不要写思考过程、推理步骤、格式说明或任何解释性文字。"
        )

        # ── 精简 Part1：只保留 measures + top10 LMDI ──────────────────── #
        p1_slim = {
            "current_period":  part1.get("current_period"),
            "previous_period": part1.get("previous_period"),
            "measures":        part1.get("measures", []),
            "global_top10":    part1.get("global_top20", [])[:10],
            "operand_fluctuations": part1.get("operand_fluctuations", [])[:6],
        }
        # ── 精简 Part2 统计量化：只保留异常检测 + 相关性 top5 ─────────── #
        p3_slim = {}
        if isinstance(part3, dict):
            p3_slim["analysis_focus"]  = part3.get("analysis_focus", "")
            p3_slim["anomaly_periods"] = part3.get("anomaly_periods", {})
            corr = part3.get("correlations", {})
            # 只保留相关系数绝对值最大的 top5
            dim_meas = sorted(
                corr.get("dim_meas", {}).items(),
                key=lambda x: abs(x[1].get("pearson", 0) or 0), reverse=True
            )[:5]
            p3_slim["top_correlations"] = {k: v for k, v in dim_meas}

        analysis_summary = {
            "元信息":       meta,
            "Part1_波动":   p1_slim,
            "Part2_统计异常": p3_slim,
        }
        if part5 and not part5.get("skip_reason") and not part5.get("error"):
            p5_slim = {k: v for k, v in part5.items()
                       if k not in ("sample_rows", "related_data")}
            if "anomaly_rows" in p5_slim:
                p5_slim["anomaly_rows"] = p5_slim["anomaly_rows"][:3]
            if "api_drill_results" in p5_slim:
                p5_slim["api_drill_results"] = p5_slim["api_drill_results"][:3]
            analysis_summary["Part5_下钻"] = p5_slim
        if part_kg_attr and not part_kg_attr.get("skip_reason") and not part_kg_attr.get("error"):
            slim_dims = []
            for d in part_kg_attr.get("kg_dimensions", [])[:8]:
                slim_dims.append({
                    "维度":     d.get("cn_name", d["dim_code"]),
                    "变化幅度": d.get("total_change_pct"),
                    "主要驱动": [
                        {"值": m.get("value"), "贡献": m.get("lmdi_contrib")}
                        for m in d.get("top_movers", [])[:2]
                    ],
                })
            analysis_summary["KG扩展归因"] = {
                "当期": part_kg_attr.get("current_period"),
                "上期": part_kg_attr.get("previous_period"),
                "各维度": slim_dims,
            }

        # 各阶段重点追踪链
        handoff_str = ""
        if prev_focuses:
            lines = [f"- {k}：{v}" for k, v in prev_focuses.items()]
            handoff_str = "【各阶段分析重点】\n" + "\n".join(lines) + "\n\n"

        user_msg = (
            f"{handoff_str}"
            "以下是精简后的分析数据，请生成综合报告：\n\n"
            "```json\n"
            + json.dumps(analysis_summary, ensure_ascii=False, indent=2, cls=_SafeEncoder)
            + "\n```"
        )

        is_anth = "anthropic" in base_url.lower()
        if is_anth:
            payload = {
                "model": model, "max_tokens": 4096,
                "messages": [{"role": "user", "content": user_msg}],
                "system": system_prompt,
            }
            body = json.dumps(payload).encode("utf-8")
            req = _ureq.Request(
                f"{base_url}/messages", data=body,
                headers={"Content-Type": "application/json",
                         "x-api-key": api_key, "anthropic-version": "2023-06-01"},
                method="POST",
            )
        else:
            combined = system_prompt + "\n\n" + user_msg
            payload = {
                "model": model, "max_tokens": 4096,
                "messages": [{"role": "user", "content": combined}],
            }
            body = json.dumps(payload).encode("utf-8")
            req = _ureq.Request(
                f"{base_url}/chat/completions", data=body,
                headers={"Content-Type": "application/json",
                         "Authorization": f"Bearer {api_key}"},
                method="POST",
            )

        self._log("  正在调用 LLM 生成报告（预计 20-40 秒）…")

        # 进度心跳：每 8 秒往日志发一条消息，让页面不显示卡死
        _done_flag = threading.Event()
        def _heartbeat():
            elapsed = 0
            while not _done_flag.wait(8):
                elapsed += 8
                self._log(f"  综合报告生成中… 已等待 {elapsed} 秒")
        _hb = threading.Thread(target=_heartbeat, daemon=True)
        _hb.start()

        try:
            with _ureq.urlopen(req, timeout=120) as resp:
                resp_data = json.loads(resp.read().decode("utf-8"))
        finally:
            _done_flag.set()

        # Anthropic API response format
        content = resp_data.get("content", [])
        if content and isinstance(content, list):
            return content[0].get("text", "")

        # OpenAI-compatible format
        choices = resp_data.get("choices", [])
        if choices:
            return choices[0].get("message", {}).get("content", "")

        return f"LLM 响应异常: {json.dumps(resp_data, ensure_ascii=False)}"
