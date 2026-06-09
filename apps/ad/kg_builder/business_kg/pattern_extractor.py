"""
IndicatorPatternExtractor
查询指标平台参考数据库，提炼出指标/维度的定义规律，
以结构化文本形式返回，供 LLM 作为上下文参考。

不生成任何 Turtle 三元组，不写入图谱文件。
目的：让 LLM 理解"指标/维度应该怎么定义"，
     从而针对新接入的数据源数据库，生成质量更高的业务图谱。
"""
from __future__ import annotations

import json
import re
from typing import Any, Callable, Optional


_AGG_FUNC_MAP = {
    "distinct_count": "COUNT_DISTINCT",
    "count_distinct": "COUNT_DISTINCT",
    "count":          "COUNT",
    "sum":            "SUM",
    "avg":            "AVG",
    "average":        "AVG",
    "max":            "MAX",
    "min":            "MIN",
}


def _extract_agg_func(expression: Any) -> str:
    if not expression:
        return ""
    ops = re.findall(r'"operator"\s*:\s*"([^"]+)"', str(expression))
    if not ops:
        return ""
    raw = ops[0].lower()
    return _AGG_FUNC_MAP.get(raw, raw.upper())


class IndicatorPatternExtractor:
    """
    连接指标平台参考数据库，提炼指标/维度建模规律。
    返回纯文本，作为 LLM 的上下文 prompt 片段。
    """

    def __init__(
        self,
        host: str,
        port: int,
        database: str,
        username: str,
        password: str,
        log_cb: Optional[Callable[[str], None]] = None,
    ) -> None:
        self._host     = host
        self._port     = port
        self._database = database
        self._username = username
        self._password = password
        self._log      = log_cb or (lambda m: None)

    def extract(self) -> str:
        """
        返回规律摘要文本。失败时返回空字符串（不中断主流程）。
        """
        import pymysql
        try:
            conn = pymysql.connect(
                host=self._host,
                port=self._port,
                database=self._database,
                user=self._username,
                password=self._password,
                charset="utf8mb4",
                connect_timeout=8,
            )
        except Exception as e:
            self._log(f"[参考库] 连接失败: {e}")
            return ""

        try:
            cur = conn.cursor(pymysql.cursors.DictCursor)
            cur.execute("SHOW TABLES")
            tables = {list(r.values())[0].lower() for r in cur.fetchall()}

            sections: list[str] = []
            sections.append(self._pattern_dw_tables(cur, tables))
            sections.append(self._pattern_dimensions(cur, tables))
            sections.append(self._pattern_hierarchies(cur, tables))
            sections.append(self._pattern_measures(cur, tables))
            sections.append(self._pattern_measure_applications(cur, tables))
            sections.append(self._pattern_dimension_applications(cur, tables))

            result = "\n\n".join(s for s in sections if s.strip())
            self._log(f"[参考库] 规律提炼完成，{len(result)} 字符")
            return result
        except Exception as e:
            self._log(f"[参考库] 提炼失败: {e}")
            return ""
        finally:
            conn.close()

    # ------------------------------------------------------------------ #

    def _fetchall(self, cur, sql: str, args=None) -> list[dict]:
        cur.execute(sql, args or ())
        return cur.fetchall() or []

    def _table_exists(self, tables: set, name: str) -> bool:
        return name.lower() in tables

    # ------------------------------------------------------------------ #
    # 数仓表（事实表）规律
    # ------------------------------------------------------------------ #

    def _pattern_dw_tables(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "dw_table"):
            return ""
        rows = self._fetchall(cur, """
            SELECT id, schema_name, table_name, cn_name,
                   type AS table_type, fact_table_type, has_dt, source_type
            FROM dw_table ORDER BY id
        """)
        if not rows:
            return ""
        self._log(f"  [参考库] dw_table: {len(rows)} 条")
        lines = ["### 数仓物理表规律"]
        lines.append(f"参考库共有 {len(rows)} 张数仓表，命名和类型规律如下：")
        for r in rows:
            ttype = "事实表" if (r.get("table_type") or 0) == 0 else "维度表"
            ftype = "聚合表" if (r.get("fact_table_type") or 1) == 0 else "明细表"
            has_dt = "有dt分区" if r.get("has_dt") else "无dt分区"
            src = {100: "Doris", 101: "TiDB", 102: "MySQL"}.get(r.get("source_type") or 0, "未知")
            full = f"{r.get('schema_name','')}.{r.get('table_name','')}"
            lines.append(f"- {full}（{r.get('cn_name','')}）: {ttype}/{ftype}/{has_dt}/数据源={src}")
        return "\n".join(lines)

    # ------------------------------------------------------------------ #
    # 维度规律
    # ------------------------------------------------------------------ #

    def _pattern_dimensions(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "dimension"):
            return ""
        rows = self._fetchall(cur, """
            SELECT id, code, cn_name, en_name, view_type, dim_type, is_hyper, description
            FROM dimension
            WHERE COALESCE(is_delete, 0) = 0
            ORDER BY dim_type, view_type, id
        """)
        if not rows:
            return ""
        self._log(f"  [参考库] dimension: {len(rows)} 条")

        DIM_TYPE_LABEL = {0: "退化维（直接取事实表列值）",
                         1: "标准维（无独立维表）",
                         2: "标准维（有独立维表）",
                         3: "衍生维度"}
        VIEW_TYPE_LABEL = {0: "字符型", 1: "日", 2: "周", 3: "月", 4: "季", 5: "年", 6: "小时"}

        # 按 dim_type 分组
        grouped: dict[int, list] = {}
        for r in rows:
            dt = r.get("dim_type") or 0
            grouped.setdefault(dt, []).append(r)

        lines = [f"### 维度规律（共 {len(rows)} 个维度）"]

        for dt, grp in sorted(grouped.items()):
            lines.append(f"\n**{DIM_TYPE_LABEL.get(dt, f'dim_type={dt}')}** — {len(grp)} 个：")
            for r in grp[:20]:  # 最多展示20个
                vt = VIEW_TYPE_LABEL.get(r.get("view_type") or 0, "未知")
                hyper = "（超维）" if r.get("is_hyper") else ""
                lines.append(
                    f"  - {r.get('cn_name','')} / {r.get('en_name','')} "
                    f"[viewType={vt}{hyper}]"
                    + (f" — {r.get('description','')}" if r.get("description") else "")
                )
            if len(grp) > 20:
                lines.append(f"  ... 共 {len(grp)} 个（已截断）")

        # 总结规律
        lines.append("\n**维度命名规律：**")
        date_dims = [r for r in rows if (r.get("view_type") or 0) > 0]
        if date_dims:
            lines.append(
                "- 日期类维度通常按粒度分组，命名格式为「业务名称_D/W/M/Q/Y」，"
                "分别对应日/周/月/季/年粒度（view_type = 1/2/3/4/5）"
            )
        char_dims = [r for r in rows if (r.get("dim_type") or 0) == 0]
        if char_dims:
            lines.append(
                f"- 退化维（{len(char_dims)} 个）直接对应事实表列，无需 JOIN，"
                f"如：{', '.join(r.get('cn_name','') for r in char_dims[:5])}"
            )
        hyper_dims = [r for r in rows if r.get("is_hyper")]
        if hyper_dims:
            lines.append(
                f"- 超维（{len(hyper_dims)} 个）为自然日期虚拟维度，用于跨业务日期映射，"
                f"如：{', '.join(r.get('cn_name','') for r in hyper_dims)}"
            )
        return "\n".join(lines)

    # ------------------------------------------------------------------ #
    # 层次结构规律
    # ------------------------------------------------------------------ #

    def _pattern_hierarchies(self, cur, tables: set) -> str:
        if not (self._table_exists(tables, "hierarchy") and
                self._table_exists(tables, "level")):
            return ""

        hier_rows = self._fetchall(cur, "SELECT id, code, name FROM hierarchy ORDER BY id")
        level_rows = self._fetchall(cur, """
            SELECT l.hierarchy_id, l.sequence, d.cn_name, d.view_type
            FROM level l
            LEFT JOIN dimension d ON d.id = l.dim_id
            ORDER BY l.hierarchy_id, l.sequence
        """)
        if not hier_rows or not level_rows:
            return ""
        self._log(f"  [参考库] hierarchy: {len(hier_rows)} 条, level: {len(level_rows)} 条")

        # Group levels by hierarchy
        hier_levels: dict[int, list] = {}
        for r in level_rows:
            hid = r.get("hierarchy_id")
            if hid:
                hier_levels.setdefault(hid, []).append(r)

        VIEW_TYPE_LABEL = {1: "日", 2: "周", 3: "月", 4: "季", 5: "年"}
        lines = [f"### 维度层次规律（共 {len(hier_rows)} 个层次）"]
        lines.append("日期类维度通常组织成层次（Hierarchy），从粗到细排列：")
        for h in hier_rows:
            levels = hier_levels.get(h["id"], [])
            level_chain = " → ".join(
                VIEW_TYPE_LABEL.get(r.get("view_type") or 0, r.get("cn_name") or "?")
                for r in sorted(levels, key=lambda x: x.get("sequence") or 0)
            )
            lines.append(
                f"- {h.get('name') or h.get('code','')}: {level_chain or '（无级别）'}"
            )
        lines.append("**规律**: 每组日期维度（如发运时间）都应建立一个对应的 Hierarchy，"
                     "将日/周/月/季/年按 sequence 从小到大排列。")
        return "\n".join(lines)

    # ------------------------------------------------------------------ #
    # 指标规律
    # ------------------------------------------------------------------ #

    def _pattern_measures(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "measure"):
            return ""
        rows = self._fetchall(cur, """
            SELECT m.id, m.cn_name, m.en_name, m.unit, m.caliber, m.definition,
                   ma.apply_type, ma.fact_column, ma.expression, ma.dw_table_id,
                   t.table_name, t.cn_name AS tbl_cn
            FROM measure m
            LEFT JOIN measure_application ma ON ma.meas_id = m.id
                AND COALESCE(ma.available, 1) = 1
            LEFT JOIN dw_table t ON t.id = ma.dw_table_id
            WHERE COALESCE(m.is_delete, 0) = 0
            ORDER BY ma.apply_type, m.id
        """)
        if not rows:
            return ""
        self._log(f"  [参考库] measure (带应用): {len(rows)} 条")

        APPLY_TYPE = {0: "原子指标", 1: "衍生指标", 2: "派生指标"}

        lines = [f"### 指标规律（共 {len({r['id'] for r in rows})} 个指标）"]
        lines.append("\n**指标定义示例：**")
        for r in rows[:30]:  # 展示前30个
            atype = APPLY_TYPE.get(r.get("apply_type") or 0, "未知")
            agg   = _extract_agg_func(r.get("expression"))
            col   = r.get("fact_column") or ""
            tbl   = r.get("table_name") or ""
            tbl_cn = r.get("tbl_cn") or ""
            formula = f"{agg}({col})" if agg and col else (agg or col or "—")
            src    = f"{tbl_cn}({tbl})" if tbl else "（衍生，无直接来源表）"
            lines.append(
                f"- [{atype}] {r.get('cn_name','')}/{r.get('en_name','')}: "
                f"计算方式={formula}, 来源={src}"
                + (f", 单位={r.get('unit')}" if r.get("unit") else "")
                + (f", 口径={r.get('caliber')}" if r.get("caliber") else "")
            )
        if len(rows) > 30:
            lines.append(f"... 共 {len(rows)} 条（已截断）")

        lines.append("\n**指标命名规律：**")
        lines.append("- 原子指标（apply_type=0）：直接对事实表某列做聚合（COUNT_DISTINCT/SUM/AVG/MAX/MIN）")
        lines.append("- 衍生指标（apply_type=1）：通过计算表达式（+/-/*//）组合其他原子指标")
        lines.append("- 原子指标的 factColumn 对应事实表中实际存在的列名")
        lines.append("- 指标的 en_name 通常与事实表列名保持对应关系，便于理解数据血缘")
        return "\n".join(lines)

    # ------------------------------------------------------------------ #
    # 指标应用规律（聚合方式分布）
    # ------------------------------------------------------------------ #

    def _pattern_measure_applications(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "measure_application"):
            return ""
        rows = self._fetchall(cur, """
            SELECT apply_type, expression, COUNT(*) AS cnt
            FROM measure_application
            WHERE COALESCE(available, 1) = 1
            GROUP BY apply_type, expression
        """)
        if not rows:
            return ""

        # Count agg functions
        agg_counts: dict[str, int] = {}
        for r in rows:
            agg = _extract_agg_func(r.get("expression"))
            if agg:
                agg_counts[agg] = agg_counts.get(agg, 0) + (r.get("cnt") or 1)

        if not agg_counts:
            return ""
        lines = ["### 聚合函数使用分布"]
        lines.append("参考库中各聚合函数的使用频率：")
        for func, cnt in sorted(agg_counts.items(), key=lambda x: -x[1]):
            lines.append(f"- {func}: {cnt} 次")
        lines.append("**规律**: COUNT_DISTINCT 常用于计件（车次、订单数等）；"
                     "AVG/SUM 用于金额/时长/比率；MAX/MIN 用于极值统计。")
        return "\n".join(lines)

    # ------------------------------------------------------------------ #
    # 维度应用规律
    # ------------------------------------------------------------------ #

    def _pattern_dimension_applications(self, cur, tables: set) -> str:
        if not (self._table_exists(tables, "dimension_application") and
                self._table_exists(tables, "dimension")):
            return ""
        rows = self._fetchall(cur, """
            SELECT d.cn_name, d.en_name, d.dim_type, d.view_type,
                   da.fact_column, da.data_type,
                   t.table_name, t.cn_name AS tbl_cn
            FROM dimension_application da
            JOIN dimension d ON d.id = da.dim_id
            LEFT JOIN dw_table t ON t.id = da.dw_table_id
            WHERE COALESCE(da.available, 1) = 1
            ORDER BY d.dim_type, d.id
        """)
        if not rows:
            return ""
        self._log(f"  [参考库] dimension_application: {len(rows)} 条")

        DIM_TYPE_LABEL = {0: "退化维", 1: "标准维(无表)", 2: "标准维(有表)", 3: "衍生维"}
        lines = [f"### 维度与事实表关联规律（共 {len(rows)} 条应用）"]
        lines.append("\n**维度在事实表上的映射示例：**")
        for r in rows[:20]:
            dt = DIM_TYPE_LABEL.get(r.get("dim_type") or 0, "未知")
            tbl = r.get("table_name") or ""
            col = r.get("fact_column") or ""
            dtype = r.get("data_type") or ""
            lines.append(
                f"- {r.get('cn_name','')}（{dt}）→ {tbl}.{col}"
                + (f" [{dtype}]" if dtype else "")
            )
        lines.append("\n**规律**:")
        lines.append("- 退化维直接从事实表列读取值（factColumn = 事实表列名）")
        lines.append("- 日期维的 factColumn 通常是一个 date_format 或 date_key 类的表达式/列")
        lines.append("- 每个维度在不同事实表上可有不同的 factColumn（即 DimensionApplication 记录）")
        lines.append("- 维度的 en_name 通常与事实表的列名相关，便于推断 factColumn")
        return "\n".join(lines)
