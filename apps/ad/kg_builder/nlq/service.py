"""Natural-language query service for AD -> DA.

The service deliberately keeps LLM output away from the final executable query.
It uses the LLM only as a future optional intent helper; the current planner
derives all executable MEAS_/DIM_ codes from the business KG.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import datetime
from decimal import Decimal
import json
import math
import re
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Callable, Optional

from rdflib import Graph, Namespace, RDF
from rdflib.namespace import RDFS

from kg_builder.ontology.owl_schema import DB

IND = Namespace("http://indicator.lixiang.com/ontology#")


_NOISE_WORDS = {
    "查询", "看下", "看一下", "一下", "帮我", "我想", "我要", "请", "显示",
    "统计", "分析", "报表", "数据", "多少", "情况", "结果", "通过", "接口",
    "query", "api", "的", "了", "和", "与", "按", "分", "每", "各",
}

_TIME_LEVEL_PATTERNS = [
    ("day", re.compile(r"按日|按天|每日|每天|日粒度|日期|天")),
    ("week", re.compile(r"按周|每周|周粒度|星期|周")),
    ("month", re.compile(r"按月|每月|月度|月份|月")),
    ("quarter", re.compile(r"按季|季度|季")),
    ("year", re.compile(r"按年|每年|年度|年份|年")),
]


@dataclass
class MeasureMeta:
    code: str
    cn_name: str
    en_name: str = ""
    definition: str = ""
    description: str = ""
    tables: set[str] = field(default_factory=set)
    search_text: str = ""


@dataclass
class DimensionMeta:
    code: str
    cn_name: str
    en_name: str = ""
    definition: str = ""
    description: str = ""
    view_type: Optional[int] = None
    hierarchy_code: str = ""
    level_code: str = ""
    tables: set[str] = field(default_factory=set)
    row_nums: dict[str, int] = field(default_factory=dict)
    search_text: str = ""

    @property
    def is_time(self) -> bool:
        return self.hierarchy_code == "h_date" or self.level_code in {
            "day", "week", "month", "quarter", "year", "hour"
        } or (self.view_type is not None and 1 <= self.view_type <= 6)


class NaturalLanguageQueryService:
    """Plan and optionally execute a DA query from a natural-language question."""

    def __init__(
        self,
        ttl_path: str | Path,
        data_agent_url: str,
        source_ttl_path: str | Path | None = None,
        log_cb: Optional[Callable[[str], None]] = None,
    ) -> None:
        self.ttl_path = Path(ttl_path)
        self.data_agent_url = data_agent_url
        self.source_ttl_path = Path(source_ttl_path) if source_ttl_path else None
        self._log = log_cb or (lambda _msg: None)
        self._mtime: Optional[float] = None
        self._graph = Graph()
        self._measures: dict[str, MeasureMeta] = {}
        self._dimensions: dict[str, DimensionMeta] = {}
        self._columns_by_table: dict[str, list[dict[str, Any]]] = {}
        self._table_labels_by_name: dict[str, dict[str, str]] = {}
        self._source_mtime: Optional[float] = None
        self._source_graph = Graph()
        self._source_columns_by_name: dict[str, list[dict[str, Any]]] = {}
        self._source_columns_by_table_col: dict[tuple[str, str], list[dict[str, Any]]] = {}
        self._source_table_columns: dict[str, list[dict[str, Any]]] = {}
        self._source_column_by_uri: dict[str, dict[str, Any]] = {}
        self._source_rows_by_table_col_value: dict[tuple[str, str, str], list[dict[str, Any]]] = {}
        self._dynamic_column_labels: dict[str, str] = {}

    def query(
        self,
        question: str,
        *,
        execute: bool = True,
        page_size: int = 100,
        page_num: int = 1,
        max_dimensions: int = 3,
        query_mode: str = "auto",
        context: Optional[dict[str, Any]] = None,
        is_follow_up: bool = False,
    ) -> dict[str, Any]:
        question = (question or "").strip()
        if not question:
            return {"ok": False, "error": "question 不能为空"}

        started = time.time()
        conversation_context = self._normalize_conversation_context(context)
        self._load_if_needed()
        if not self._measures:
            return {"ok": False, "error": "业务图谱中没有可查询指标"}

        route_intent = self._resolve_question_intent(question, query_mode)
        route_intent = self._apply_conversation_context(
            route_intent,
            conversation_context,
            is_follow_up=is_follow_up,
        )
        if route_intent.get("mode") == "entity_lookup":
            return self._attach_resolved_context(self.entity_lookup(
                question,
                page_size=page_size,
                page_num=page_num,
                intent=route_intent,
            ), conversation_context, is_follow_up)
        if route_intent.get("mode") == "relationship_analysis":
            return self._attach_resolved_context(self.relationship_analysis(
                question,
                page_size=page_size,
                page_num=page_num,
                intent=route_intent,
                started=started,
            ), conversation_context, is_follow_up)
        if route_intent.get("mode") == "landing_advice":
            return self._attach_resolved_context(
                self._landing_advice_response(question, started),
                conversation_context,
                is_follow_up,
            )

        mode = route_intent.get("mode") or self._resolve_query_mode(question, query_mode)
        plan = self._plan(
            question,
            page_size=page_size,
            page_num=page_num,
            max_dimensions=max_dimensions,
            query_mode=mode,
            route_intent=route_intent,
        )
        graph_context = (
            self._build_graph_context(plan)
            if plan.get("ok") else {}
        )
        response: dict[str, Any] = {
            "ok": plan["ok"],
            "question": question,
            "queryMode": mode,
            "intent": plan["intent"],
            "matched": plan["matched"],
            "daPayload": plan.get("daPayload"),
            "graphContext": graph_context,
            "diagnostics": plan["diagnostics"],
            "elapsedMs": int((time.time() - started) * 1000),
        }
        self._attach_resolved_context(response, conversation_context, is_follow_up)
        if not plan["ok"] or not execute:
            response["needsClarification"] = plan.get("needsClarification", False)
            response["clarification"] = plan.get("clarification", "")
            if plan.get("ok") and mode == "explain":
                response["graphAnswer"] = self._build_graph_answer(plan, graph_context)
                response["ok"] = True
            response["suggestedNextQuestions"] = (
                plan.get("suggestedNextQuestions")
                or self._build_suggested_questions(mode, plan, response)
            )
            return response

        if mode == "explain":
            response["graphAnswer"] = self._build_graph_answer(plan, graph_context)
            response["ok"] = True
            response["elapsedMs"] = int((time.time() - started) * 1000)
            response["suggestedNextQuestions"] = (
                plan.get("suggestedNextQuestions")
                or self._build_suggested_questions(mode, plan, response)
            )
            return response

        da_result = self._execute_da(plan["daPayload"])
        response["result"] = da_result
        response["ok"] = da_result.get("ok", False)
        if not response["ok"]:
            response["error"] = da_result.get("error", "DA 查询失败")
        response["explain"] = self._explain(plan, da_result)
        if mode in {"detail", "analyze_detail"}:
            detail_data = self._normalize_detail_result(da_result)
            response["detailData"] = detail_data
            response["graphContext"] = self._build_graph_context(plan, detail_data)
            self._apply_detail_display_labels(detail_data, response["graphContext"], plan)
            if mode in {"detail", "analyze_detail"} and response["ok"]:
                response["secondaryAnalysis"] = self._analyze_detail_data(
                    plan,
                    response["graphContext"],
                    detail_data,
                )
                if mode == "detail":
                    mode = "analyze_detail"
                    response["queryMode"] = "analyze_detail"
        response["elapsedMs"] = int((time.time() - started) * 1000)
        response["suggestedNextQuestions"] = self._build_suggested_questions(mode, plan, response)
        self._attach_resolved_context(response, conversation_context, is_follow_up)
        return response

    @staticmethod
    def _normalize_conversation_context(context: Optional[dict[str, Any]]) -> dict[str, Any]:
        if not isinstance(context, dict):
            return {}
        normalized = dict(context)
        for key in ("factTables", "measureCodes", "dimensionCodes"):
            value = normalized.get(key)
            normalized[key] = [str(item) for item in value if item] if isinstance(value, list) else []
        filters = normalized.get("filters")
        normalized["filters"] = filters if isinstance(filters, list) else []
        return normalized

    def _apply_conversation_context(
        self,
        route_intent: dict[str, Any],
        context: dict[str, Any],
        *,
        is_follow_up: bool,
    ) -> dict[str, Any]:
        intent = dict(route_intent or {})
        if not is_follow_up or not context:
            return intent
        fact_tables = context.get("factTables") or []
        if fact_tables:
            intent.setdefault("preferredFactTables", fact_tables)
        active_measure = str(context.get("activeMeasureCode") or "").strip()
        if active_measure:
            intent.setdefault("inheritedMeasureCode", active_measure)
        if context.get("dimensionCodes") and not intent.get("dimensionCodes"):
            intent["dimensionCodes"] = context["dimensionCodes"]
        if context.get("filters"):
            intent.setdefault("inheritedFilters", context["filters"])
        # 从上轮 entity_lookup 传递实体上下文
        last_entity = context.get("lastEntity")
        if isinstance(last_entity, dict) and last_entity.get("value"):
            intent["inheritedEntity"] = last_entity
        intent["contextInherited"] = True
        return intent

    def _attach_resolved_context(
        self,
        response: dict[str, Any],
        previous: dict[str, Any],
        is_follow_up: bool,
    ) -> dict[str, Any]:
        resolved = dict(previous)
        matched = response.get("matched") or {}
        intent = response.get("intent") or {}
        measure_code = str(matched.get("measureCode") or "").strip()
        if measure_code:
            measure_codes = [
                code for code in resolved.get("measureCodes", [])
                if code and code != measure_code
            ]
            resolved["measureCodes"] = [measure_code, *measure_codes][:8]
            resolved["activeMeasureCode"] = measure_code
        if matched.get("factTables"):
            resolved["factTables"] = list(matched["factTables"])
        if matched.get("dimensionCodes") is not None:
            resolved["dimensionCodes"] = list(matched.get("dimensionCodes") or [])
        if intent.get("filters"):
            resolved["filters"] = list(intent["filters"])
        resolved["lastQuestion"] = response.get("question") or ""
        resolved["lastQueryMode"] = response.get("queryMode") or ""
        # 实体检索上下文：供后续"同类订单"/"同类XX"追问使用
        if response.get("queryMode") == "entity_lookup":
            entity = response.get("entity") or {}
            peer = response.get("peerAnalysis") or {}
            joined = response.get("joinedDimensions") or []
            if entity:
                resolved["lastEntity"] = {
                    "fieldText": str(entity.get("fieldText") or ""),
                    "value": str(entity.get("value") or ""),
                    "tableName": str(entity.get("tableName") or matched.get("factTables", [None])[0] or ""),
                }
            if peer.get("rules"):
                resolved["lastEntity"]["peerFilters"] = [
                    {"field": r["field"], "name": r.get("name", r["field"]), "value": r["value"], "op": r.get("op", "eq")}
                    for r in peer["rules"]
                ]
            if joined:
                resolved["lastEntity"]["joinedDimensions"] = [
                    {"dimCode": j.get("dimCode", ""), "dimName": j.get("dimName", ""), "value": j.get("value", "")}
                    for j in joined[:8] if j.get("value")
                ]
        resolved["contextApplied"] = bool(is_follow_up and previous)
        response["resolvedContext"] = resolved
        return response

    def entity_lookup(
        self,
        question: str,
        *,
        page_size: int = 500,
        page_num: int = 1,
        intent: Optional[dict[str, Any]] = None,
    ) -> dict[str, Any]:
        """Lookup details by a business/source graph attribute value and analyze peers."""
        question = (question or "").strip()
        if not question:
            return {"ok": False, "error": "question 不能为空"}
        started = time.time()
        self._load_if_needed()
        self._load_source_graph_if_needed()

        parsed = self._entity_lookup_payload_from_intent(intent or {})
        if not parsed:
            llm_intent = self._resolve_question_intent_with_llm(question)
            parsed = self._entity_lookup_payload_from_intent(llm_intent)
        if not parsed:
            parsed = self._parse_entity_lookup(question)
        if not parsed:
            field_hints = self._entity_field_candidates()[:15]
            hint_names = sorted(set(
                (f.get("label") or f.get("cnName") or f.get("columnName") or "")
                for f in field_hints
                if (f.get("label") or f.get("cnName") or f.get("columnName"))
            ))[:6]
            hint_str = "、".join(hint_names) if hint_names else "订单ID、仓库名称、促销名称等"
            return {
                "ok": False,
                "queryMode": "entity_lookup",
                "error": (
                    f"未识别到具体实体信息。请提供可检索的属性值，例如「{hint_str}」。"
                    "对话上下文中如有引用过的订单/仓库/促销，系统可自动关联。"
                ),
                "hintFields": [{"name": h} for h in hint_names],
                "elapsedMs": int((time.time() - started) * 1000),
            }

        candidate = self._candidate_from_entity_payload(parsed)
        if not candidate:
            candidate = self._match_entity_field(parsed.get("fieldText", ""), parsed["value"])
        if not candidate:
            return {
                "ok": False,
                "queryMode": "entity_lookup",
                "error": f"没有在图谱中找到「{parsed.get('fieldText') or parsed.get('fieldCode') or '属性'}」对应的可检索字段",
                "parsed": parsed,
                "elapsedMs": int((time.time() - started) * 1000),
            }

        rows, sql, sql_error = self._query_entity_rows(candidate, parsed["value"], page_size, page_num)
        if sql_error:
            return {
                "ok": False,
                "queryMode": "entity_lookup",
                "error": sql_error,
                "entity": self._entity_payload(candidate, parsed["value"]),
                "elapsedMs": int((time.time() - started) * 1000),
            }

        measure = self._first_measure_for_table(candidate["tableName"])
        plan = self._entity_plan(candidate, measure, page_size, page_num)
        detail_data = {
            "columns": [{"code": k, "name": k} for k in (rows[0].keys() if rows else [])],
            "records": [self._jsonable_row(r) for r in rows],
            "rowCount": len(rows),
            "rawRowCount": len(rows),
            "headerIncluded": False,
            "reviewSql": sql,
        }
        graph_context = self._build_graph_context(plan, detail_data) if plan.get("ok") else {}
        self._apply_detail_display_labels(detail_data, graph_context, plan)

        first_row = rows[0] if rows else {}
        joined = self._lookup_joined_dimensions(candidate["tableName"], first_row)
        peer = self._analyze_entity_peers(
            candidate,
            first_row,
            measure,
            page_size=5000,
            entity_value=parsed["value"],
            joined_dimensions=joined,
        ) if rows else {}

        return {
            "ok": True,
            "queryMode": "entity_lookup",
            "question": question,
            "entity": self._entity_payload(candidate, parsed["value"]),
            "pageSize": max(1, min(int(page_size or 500), 10000)),
            "pageNum": max(1, int(page_num or 1)),
            "detailData": detail_data,
            "joinedDimensions": joined,
            "peerAnalysis": peer,
            "graphContext": graph_context,
            "matched": plan.get("matched", {}),
            "daPayload": None,
            "explain": (
                f"已按「{self._entity_display_label(candidate)} = {parsed['value']}」检索，"
                f"命中 {len(rows)} 行，并基于图谱关系生成同类分析。"
            ),
            "elapsedMs": int((time.time() - started) * 1000),
            "suggestedNextQuestions": self._build_suggested_questions(
                "entity_lookup", plan,
                {"entity": self._entity_payload(candidate, parsed["value"]), "peerAnalysis": peer},
            ),
        }

    def relationship_analysis(
        self,
        question: str,
        *,
        page_size: int = 500,
        page_num: int = 1,
        intent: Optional[dict[str, Any]] = None,
        started: Optional[float] = None,
    ) -> dict[str, Any]:
        """Analyze graph-derived metric relationships and cross-dimension differences."""
        started = started or time.time()
        question = (question or "").strip()
        self._load_if_needed()
        self._load_source_graph_if_needed()

        context = self._relationship_context(question, intent or {})
        if not context:
            return {
                "ok": False,
                "queryMode": "relationship_analysis",
                "question": question,
                "error": "没有从业务图谱中匹配到可做相关关系分析的指标组合",
                "elapsedMs": int((time.time() - started) * 1000),
            }

        sample_limit = max(1000, min(int(page_size or 1000), 20000))
        rows, sql, error = self._query_relationship_rows(context, sample_limit)
        if error:
            return {
                "ok": False,
                "queryMode": "relationship_analysis",
                "question": question,
                "error": error,
                "matched": self._relationship_matched_payload(context),
                "elapsedMs": int((time.time() - started) * 1000),
            }
        analysis = self._analyze_relationship_rows(question, context, rows)
        return {
            "ok": True,
            "queryMode": "relationship_analysis",
            "question": question,
            "intent": {
                "rawQuestion": question,
                "queryMode": "relationship_analysis",
                "driverMeasure": context["driver"]["measure"].cn_name,
                "outcomeMeasures": [o["measure"].cn_name for o in context["outcomes"]],
                "dimensionTexts": [d["dimension"].cn_name for d in context["dimensions"]],
                "limit": sample_limit,
            },
            "matched": self._relationship_matched_payload(context),
            "relationshipAnalysis": analysis,
            "reviewSql": sql,
            "pageSize": sample_limit,
            "pageNum": max(1, int(page_num or 1)),
            "explain": (
                f"已基于业务图谱匹配「{context['driver']['measure'].cn_name}」与 "
                f"{len(context['outcomes'])} 个结果指标，"
                f"在「{self._table_business_label(context['table']) or context['table']}」上抽样 {len(rows)} 行做相关关系分析。"
            ),
            "elapsedMs": int((time.time() - started) * 1000),
            "suggestedNextQuestions": self._build_suggested_questions("relationship_analysis", {
                "matched": self._relationship_matched_payload(context),
            }, {"daPayload": {"configureList": []}}),
        }

    def _landing_advice_response(self, question: str, started: float) -> dict[str, Any]:
        fact_tables = sorted({t for m in self._measures.values() for t in m.tables if t})
        measure_names = [m.cn_name for m in self._measures.values() if m.cn_name][:8]
        dim_names = [d.cn_name for d in self._dimensions.values() if d.cn_name][:10]
        answer = {
            "summary": (
                "可以按“自动语义发现 + 图谱校验 + 问数反馈修正”的方式快速落地，"
                "不要把首版价值阻塞在业务专家手工建模上。"
            ),
            "currentGraph": {
                "measureCount": len(self._measures),
                "dimensionCount": len(self._dimensions),
                "factTableCount": len(fact_tables),
                "sampleMeasures": measure_names,
                "sampleDimensions": dim_names,
            },
            "recommendedPath": [
                "先从数据源图谱、字段注释、样例值和外键/包含关系自动生成初版业务图谱。",
                "指标、维度、字段含义优先读业务图谱；缺失时调用模型补全，再把补全结果写回可复用元数据。",
                "每个问数问题都反向沉淀命中指标、关联维度、失败原因和修复建议，形成半自动迭代闭环。",
                "业务专家只审核高频指标、关键口径和异常结论，不参与全量字段建模。"
            ],
            "industryGap": [
                "当前更接近快速可用的语义层和描述性分析，行业先进方案会继续加入指标血缘治理、权限口径审批和版本化发布。",
                "促销、定价这类策略问题，行业先进做法通常会叠加实验、因果推断、价格弹性和利润约束，而不只看相关性。",
                "跨维分析应逐步从样本相关升级为可解释模型，纳入商品、客户、库存、季节、活动成本等控制变量。"
            ],
        }
        return {
            "ok": True,
            "queryMode": "landing_advice",
            "question": question,
            "landingAdvice": answer,
            "matched": {
                "measureName": "快速落地方案",
                "factTables": fact_tables,
                "dimensions": [],
            },
            "diagnostics": {},
            "elapsedMs": int((time.time() - started) * 1000),
            "suggestedNextQuestions": self._build_suggested_questions("landing_advice", {
                "matched": {"measureName": "快速落地方案"},
            }, {"landingAdvice": answer}),
        }

    def _parse_entity_lookup(self, question: str) -> dict[str, str]:
        q = re.sub(r"\s+", " ", question or "").strip()
        patterns = [
            r"(?P<field>[\u4e00-\u9fa5A-Za-z_][\u4e00-\u9fa5A-Za-z0-9_]{0,24})\s*(?:为|是|=|:|：)\s*[\"'“”]?(?P<value>[A-Za-z0-9_.\-]+)[\"'“”]?",
            r"^(?:查|查询|查看|检索|搜索)?\s*(?P<field>[\u4e00-\u9fa5A-Za-z_][\u4e00-\u9fa5A-Za-z0-9_]{0,24})\s+[\"'“”]?(?P<value>[A-Za-z0-9_.\-]+)[\"'“”]?(?:\s*(?:的)?(?:信息|详情|明细|所有信息|全部信息|情况|分析|画像|同类.*)?)?$",
        ]
        for pat in patterns:
            m = re.search(pat, q, re.I)
            if not m:
                continue
            field = (m.group("field") or "").strip()
            value = (m.group("value") or "").strip().strip("'\"“”")
            if field and value and field not in {"查询", "查看", "分析", "输入"}:
                return {"fieldText": field, "value": value}
        return {}

    def _entity_lookup_payload_from_intent(self, intent: dict[str, Any]) -> dict[str, str]:
        if not intent or intent.get("mode") != "entity_lookup":
            return {}
        entity = intent.get("entity") or {}
        if not isinstance(entity, dict):
            entity = {}
        field_text = str(entity.get("fieldText") or intent.get("fieldText") or "").strip()
        field_code = str(entity.get("fieldCode") or intent.get("fieldCode") or "").strip()
        table_name = str(entity.get("tableName") or intent.get("tableName") or "").strip()
        value = str(entity.get("value") or intent.get("value") or "").strip().strip("'\"“”")
        payload = {
            "fieldText": field_text,
            "fieldCode": field_code,
            "tableName": table_name,
            "value": value,
        }
        return payload if value and (field_text or field_code) else {}

    def _candidate_from_entity_payload(self, payload: dict[str, str]) -> dict[str, Any]:
        field_code = (payload.get("fieldCode") or "").strip()
        table_name = (payload.get("tableName") or "").strip()
        if not field_code:
            return {}
        if "." in field_code and not table_name:
            table_name, field_code = field_code.rsplit(".", 1)
        candidates = self._entity_field_candidates()
        exact = []
        for c in candidates:
            if c["columnName"].lower() != field_code.lower():
                continue
            candidate_tables = {
                str(c.get("tableName") or "").lower(),
                str(c.get("sourceTableName") or "").lower(),
            }
            if table_name and table_name.lower() not in candidate_tables:
                continue
            exact.append(c)
        if not exact:
            return {}
        value = payload.get("value") or ""
        exact = sorted(
            exact,
            key=lambda c: (
                not self._entity_candidate_value_exists(c, value),
                c["tableName"],
                c["columnName"],
            ),
        )
        return dict(exact[0])

    def _resolve_question_intent(self, question: str, query_mode: str) -> dict[str, Any]:
        requested = (query_mode or "auto").strip().lower()
        aliases = {
            "stat": "aggregate",
            "stats": "aggregate",
            "agg": "aggregate",
            "明细": "detail",
            "解释": "explain",
            "analysis": "analyze_detail",
            "detail_analysis": "analyze_detail",
            "entity": "entity_lookup",
            "entity_lookup": "entity_lookup",
            "relationship": "relationship_analysis",
            "relation": "relationship_analysis",
            "correlation": "relationship_analysis",
            "相关": "relationship_analysis",
            "关系": "relationship_analysis",
            "落地": "landing_advice",
        }
        requested = aliases.get(requested, requested)
        if requested in {
            "aggregate", "detail", "analyze_detail", "explain",
            "entity_lookup", "relationship_analysis", "landing_advice",
        }:
            return {"mode": requested}

        heuristic_intent = self._heuristic_special_intent(question)
        if heuristic_intent:
            return heuristic_intent

        llm_intent = self._resolve_question_intent_with_llm(question)
        if llm_intent.get("mode") in {
            "aggregate", "detail", "analyze_detail", "explain",
            "entity_lookup", "relationship_analysis", "landing_advice",
        }:
            return llm_intent

        parsed = self._parse_entity_lookup(question)
        if parsed:
            return {"mode": "entity_lookup", "entity": parsed, "reason": "识别到通用属性值表达式"}

        return {"mode": self._resolve_query_mode(question, query_mode)}

    def _heuristic_special_intent(self, question: str) -> dict[str, Any]:
        q = question or ""
        # 实体指代检测优先：避免被"异常/促销"等词误路由到聚合或关系分析
        if re.search(
            r"该[订单笔个项条只次件张批种座家名位辆台部本套组类科属种块片根颗粒层段行列册份处所期届场届次回趟度]|"
            r"这个|此[订单笔个次项]|某个|哪一个|"
            r"哪[一个笔条次件张]|什么订单|什么促销",
            q
        ):
            return {"mode": "entity_lookup", "reason": "识别为实体检索与同类对比分析"}
        # 同类/同组 + 特征/较高/差异：应走关系分析做多维度特征画像
        if re.search(r"同类|同组|同批|同种|类似|相似|相近", q) and re.search(
            r"特征|较高|较低|偏高|偏低|更多|更少|更大|更小|明显|差异|区别|什么样|不同",
            q
        ):
            return {"mode": "relationship_analysis", "reason": "识别为同类对比与特征画像分析"}
        if re.search(r"落地|快速上线|快速接入|业务专家|数据建模|建模.*没戏|不用.*建模", q):
            return {"mode": "landing_advice", "reason": "识别为快速落地方法咨询"}
        if (
            re.search(r"相关|关系|显著|是否也|是不是.*好方法|好方法|降价|促销.*好|折扣类|折扣.*订单|不同.+下|差异|跨维度|图谱跳跃", q)
            and re.search(r"折扣|优惠|降价|促销|净利润|利润|销售数量|数量|仓库|运输方式", q)
        ):
            return {"mode": "relationship_analysis", "reason": "识别为指标相关关系或跨维差异分析"}
        return {}

    def _resolve_question_intent_with_llm(self, question: str) -> dict[str, Any]:
        try:
            from kg_builder.utils.llm_config import llm_config_from_env

            cfg = llm_config_from_env(Path.cwd())
            api_key = (cfg.get("api_key") or "").strip()
            base_url = (cfg.get("base_url") or "").strip().rstrip("/")
            model = (cfg.get("model") or "").strip()
            if not api_key or not base_url or not model:
                return {}

            field_candidates = self._entity_field_candidates_for_llm(question)
            measure_examples = [
                {"code": m.code, "name": m.cn_name, "definition": m.definition, "tables": sorted(m.tables)}
                for m in list(self._measures.values())[:40]
            ]
            dimension_examples = [
                {"code": d.code, "name": d.cn_name, "isTime": d.is_time, "tables": sorted(d.tables)}
                for d in list(self._dimensions.values())[:60]
            ]
            system = (
                "你是 AD 智能Insight 的统一意图解析器，只返回 JSON，不要返回 Markdown。"
                "mode 只能是 entity_lookup、aggregate、detail、analyze_detail、relationship_analysis、landing_advice、explain。"
                "entity_lookup=用户给出某个业务属性/字段及具体值，想查该对象/记录/属性值相关明细或同类分析；"
                "aggregate=指标汇总、按维度分组、趋势聚合；"
                "detail=指标相关原始明细；"
                "analyze_detail=明细基础上的异常、分布、原因、特征分析；"
                "relationship_analysis=用户询问指标之间是否相关、是否显著偏高、某种策略是否有效，或跨维度/图谱跳跃差异；"
                "landing_advice=用户询问如何快速落地、如何减少人工业务建模依赖；"
                "explain=只问图谱关系、口径、来源、可用维度。"
                "如果是 entity_lookup，必须从 candidateFields 中选择最匹配的 fieldCode/tableName，"
                "并抽取 value；不要臆造候选字段之外的 fieldCode。"
                "如果是指标查询，尽量从 availableMeasures/availableDimensions 选择 measureCode 和 dimensionCodes。"
            )
            user = json.dumps({
                "question": question,
                "candidateFields": field_candidates,
                "availableMeasures": measure_examples,
                "availableDimensions": dimension_examples,
                "outputSchema": {
                    "mode": "entity_lookup|aggregate|detail|analyze_detail|relationship_analysis|landing_advice|explain",
                    "reason": "简短中文原因",
                    "entity": {
                        "fieldText": "用户原话中的属性名",
                        "fieldCode": "候选字段 columnName",
                        "tableName": "候选字段 tableName",
                        "value": "用户输入的属性值"
                    },
                    "measureCode": "可选，指标 code",
                    "dimensionCodes": ["可选，维度 code"],
                },
            }, ensure_ascii=False)
            payload = {
                "model": model,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": user},
                ],
                "temperature": 0,
                "max_tokens": 320,
            }
            req = urllib.request.Request(
                f"{base_url}/chat/completions",
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {api_key}",
                },
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            content = (
                data.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "")
                .strip()
            )
            if content.startswith("```"):
                content = re.sub(r"^```(?:json)?\s*|\s*```$", "", content, flags=re.S).strip()
            parsed = json.loads(content)
            if not isinstance(parsed, dict):
                return {}
            mode = str(parsed.get("mode") or "").strip()
            if mode not in {
                "entity_lookup", "aggregate", "detail", "analyze_detail",
                "relationship_analysis", "landing_advice", "explain",
            }:
                return {}
            parsed["mode"] = mode
            return parsed
        except Exception as exc:
            self._log(f"[NLQ] LLM 统一意图解析不可用，使用规则兜底: {exc}")
            return {}

    def _entity_field_candidates_for_llm(self, question: str, limit: int = 120) -> list[dict[str, Any]]:
        candidates = self._entity_field_candidates()
        q_tokens = self._tokens(question)
        scored = []
        for c in candidates:
            text = " ".join([
                str(c.get("label") or ""),
                str(c.get("comment") or ""),
                str(c.get("columnName") or ""),
                str(c.get("tableName") or ""),
                " ".join(c.get("aliases") or []),
            ])
            score = self._token_score(q_tokens, text, weight=10)
            if self._norm(c.get("label") or "") and self._norm(c.get("label") or "") in self._norm(question):
                score += 50
            if str(c.get("columnName") or "").lower() in question.lower():
                score += 80
            scored.append((score, c))
        scored.sort(key=lambda x: (-x[0], x[1].get("tableName", ""), x[1].get("columnName", "")))
        top = [c for _score, c in scored[:limit]]
        return [
            {
                "fieldCode": c.get("columnName"),
                "fieldName": c.get("label"),
                "tableName": c.get("sourceTableName") or c.get("tableName"),
                "factTable": c.get("tableName"),
                "comment": c.get("comment"),
                "type": c.get("columnType"),
            }
            for c in top
        ]

    def _match_entity_field(self, field_text: str, value: str) -> dict[str, Any]:
        candidates = self._entity_field_candidates()
        if not candidates:
            return {}
        field_norm = self._norm(field_text)
        value_s = str(value)
        best: tuple[float, dict[str, Any]] | None = None
        for c in candidates:
            score = 0.0
            code_l = c["columnName"].lower()
            label_norm = self._norm(c.get("label") or "")
            comment_norm = self._norm(c.get("comment") or "")
            aliases = {a.lower() for a in c.get("aliases", []) if a}
            if field_norm and field_norm == label_norm:
                score += 120
            if field_norm and field_norm == comment_norm:
                score += 110
            if field_norm and field_norm in label_norm:
                score += 70
            if field_norm and field_norm in comment_norm:
                score += 60
            if field_text.lower() == code_l:
                score += 120
            if field_text.lower() in aliases:
                score += 95
            if field_norm and any(field_norm in self._norm(a) for a in aliases):
                score += 65
            if self._entity_candidate_value_exists(c, value_s):
                score += 20
            if score <= 0:
                continue
            if best is None or score > best[0]:
                best = (score, c)
        return dict(best[1]) if best else {}

    def _entity_field_candidates(self) -> list[dict[str, Any]]:
        fact_tables = sorted({t for m in self._measures.values() for t in m.tables if t})
        measure_cols: dict[tuple[str, str], str] = {}
        dim_cols: dict[tuple[str, str], str] = {}
        for m in self._measures.values():
            for app in self._measure_apps(m.code):
                table = ((app.get("table") or {}).get("tableName") or "").lower()
                col = (app.get("factColumn") or "").lower()
                if table and col:
                    measure_cols[(table, col)] = m.cn_name
        for d in self._dimensions.values():
            for app in self._dimension_apps(d.code):
                table = (app.get("factTable") or "").lower()
                for col_key in ("dimFactColumn", "masterPrimaryKey"):
                    col = (app.get(col_key) or "").lower()
                    if table and col:
                        dim_cols[(table, col)] = d.cn_name

        result: list[dict[str, Any]] = []
        for table in fact_tables:
            cols = self._source_table_columns.get(table.lower(), [])
            if not cols and self._columns_by_table.get(table):
                cols = self._columns_by_table.get(table, [])
            for col in cols:
                col_name = str(col.get("columnName") or col.get("code") or col.get("name") or "")
                if not col_name:
                    continue
                label = self._graph_column_label_from_ttl(table, col_name) or self._business_column_label(col_name)
                label = self._clean_business_label(label) or col_name
                label = self._entity_column_label(col_name, label)
                key = (table.lower(), col_name.lower())
                aliases = [
                    label,
                    col_name,
                    col_name.replace("_", ""),
                    col_name.replace("_sk", ""),
                ]
                if key in measure_cols:
                    aliases.append(measure_cols[key])
                if key in dim_cols:
                    aliases.append(dim_cols[key])
                result.append({
                    "tableName": table,
                    "sourceTableName": table,
                    "schema": self._schema_for_table(table),
                    "columnName": col_name,
                    "label": label,
                    "comment": col.get("comment") or col.get("name") or "",
                    "columnType": col.get("columnType") or col.get("type") or "",
                    "aliases": list(dict.fromkeys([str(a) for a in aliases if a])),
                    "filterType": "fact",
                })
        for dim in self._dimensions.values():
            for app in self._dimension_apps(dim.code):
                fact_table = app.get("factTable") or ""
                dim_table = app.get("dimTable") or ""
                dim_pk = app.get("dimPrimaryKey") or ""
                dim_col = app.get("dimColumn") or ""
                fact_col = app.get("dimFactColumn") or ""
                if not fact_table or not dim_table or not dim_pk or not dim_col or not fact_col:
                    continue
                source_col = self._source_column_meta(dim_table, dim_col)
                label = self._clean_business_label(dim.cn_name) or self._business_column_label(dim_col) or dim_col
                aliases = [
                    label,
                    dim_col,
                    dim_col.replace("_", ""),
                    source_col.get("comment") or "",
                ]
                result.append({
                    "tableName": fact_table,
                    "sourceTableName": dim_table,
                    "schema": app.get("factSchema") or self._schema_for_table(fact_table),
                    "sourceSchema": app.get("dimSchema") or "",
                    "columnName": dim_col,
                    "label": label,
                    "comment": source_col.get("comment") or "",
                    "columnType": source_col.get("columnType") or "",
                    "aliases": list(dict.fromkeys([str(a) for a in aliases if a])),
                    "filterType": "dimension",
                    "dimTable": dim_table,
                    "dimPrimaryKey": dim_pk,
                    "dimColumn": dim_col,
                    "dimFactColumn": fact_col,
                    "dimensionCode": dim.code,
                    "dimensionName": dim.cn_name,
                })
        return result

    def _entity_column_label(self, column_name: str, label: str) -> str:
        return label or column_name

    def _entity_display_label(self, candidate: dict[str, Any]) -> str:
        label = self._clean_business_label(candidate.get("label"))
        if label and label not in {"字段", "编号", "关联键"}:
            return label
        column_name = str(candidate.get("columnName") or "")
        translated = self._clean_business_label(
            self._translate_missing_column_labels([column_name]).get(column_name, "")
        )
        if translated and translated not in {"字段", "编号", "关联键"}:
            return translated
        return column_name or label or "属性"

    def _entity_value_exists(self, table_name: str, column_name: str, value: str) -> bool:
        return bool(self._source_rows_by_table_col_value.get((table_name.lower(), column_name.lower(), value)))

    def _entity_candidate_value_exists(self, candidate: dict[str, Any], value: str) -> bool:
        table = candidate.get("sourceTableName") or candidate.get("tableName") or ""
        col = candidate.get("columnName") or ""
        return self._entity_value_exists(str(table), str(col), str(value))

    def _query_entity_rows(
        self,
        candidate: dict[str, Any],
        value: str,
        page_size: int,
        page_num: int,
    ) -> tuple[list[dict[str, Any]], str, str]:
        conn = None
        try:
            import pymysql
            conn_cfg = self._db_connection_for_table(candidate["tableName"], candidate.get("schema") or "")
            conn = pymysql.connect(
                host=conn_cfg["host"],
                port=conn_cfg["port"],
                user=conn_cfg["user"],
                password=conn_cfg["password"],
                database=conn_cfg["database"],
                charset="utf8mb4",
            )
            table = self._sql_table(candidate["tableName"], "")
            limit = max(1, min(int(page_size or 500), 10000))
            offset = max(0, (max(1, int(page_num or 1)) - 1) * limit)
            if candidate.get("filterType") == "dimension":
                dim_table = self._sql_table(candidate["dimTable"], "")
                sql = (
                    f"SELECT * FROM {table} WHERE `{candidate['dimFactColumn']}` IN "
                    f"(SELECT `{candidate['dimPrimaryKey']}` FROM {dim_table} "
                    f"WHERE `{candidate['dimColumn']}` = %s) LIMIT %s OFFSET %s"
                )
            else:
                col = candidate["columnName"]
                sql = f"SELECT * FROM {table} WHERE `{col}` = %s LIMIT %s OFFSET %s"
            with conn.cursor(pymysql.cursors.DictCursor) as cur:
                cur.execute(sql, (value, limit, offset))
                rows = list(cur.fetchall())
            shown_sql = sql.replace("%s", "?", 1).replace("%s", str(limit), 1).replace("%s", str(offset), 1)
            shown_sql = shown_sql.replace("?", f"'{value}'", 1)
            return rows, shown_sql, ""
        except Exception as exc:
            return [], "", f"属性值明细查询失败: {exc}"
        finally:
            try:
                if conn:
                    conn.close()
            except Exception:
                pass

    def _lookup_joined_dimensions(self, table_name: str, row: dict[str, Any]) -> list[dict[str, Any]]:
        if not row:
            return []
        conn = None
        joined: list[dict[str, Any]] = []
        try:
            import pymysql
            conn_cfg = self._db_connection_for_table(table_name, self._schema_for_table(table_name))
            conn = pymysql.connect(
                host=conn_cfg["host"],
                port=conn_cfg["port"],
                user=conn_cfg["user"],
                password=conn_cfg["password"],
                database=conn_cfg["database"],
                charset="utf8mb4",
            )
            with conn.cursor(pymysql.cursors.DictCursor) as cur:
                for dim in self._sort_available_dims(
                    [d for d in self._dimensions.values() if table_name in d.tables],
                    {table_name},
                ):
                    for app in self._dimension_apps(dim.code, {table_name}):
                        fact_col = app.get("dimFactColumn") or app.get("masterPrimaryKey") or ""
                        if not fact_col or fact_col not in row:
                            continue
                        raw_val = row.get(fact_col)
                        value = raw_val
                        if app.get("dimTable") and app.get("dimPrimaryKey") and app.get("dimColumn"):
                            dim_table = self._sql_table(app["dimTable"], "")
                            cur.execute(
                                f"SELECT `{app['dimColumn']}` FROM {dim_table} WHERE `{app['dimPrimaryKey']}` = %s LIMIT 1",
                                (raw_val,),
                            )
                            found = cur.fetchone()
                            if found:
                                value = found.get(app["dimColumn"])
                        joined.append({
                            "name": dim.cn_name,
                            "value": self._jsonable_value(value),
                            "keyValue": self._jsonable_value(raw_val),
                            "source": "图谱关联",
                        })
                        break
        except Exception as exc:
            joined.append({"name": "关联维度补全", "value": f"查询失败: {exc}", "source": "系统"})
        finally:
            try:
                if conn:
                    conn.close()
            except Exception:
                pass
        seen = set()
        deduped = []
        for item in joined:
            key = (item.get("name"), str(item.get("value")))
            if key in seen:
                continue
            seen.add(key)
            deduped.append(item)
        return deduped[:20]

    def _analyze_entity_peers(
        self,
        candidate: dict[str, Any],
        row: dict[str, Any],
        measure: MeasureMeta | None,
        *,
        page_size: int,
        entity_value: Any = "",
        joined_dimensions: Optional[list[dict[str, Any]]] = None,
    ) -> dict[str, Any]:
        peer_filters, peer_rows = self._query_adaptive_peer_rows(
            candidate["tableName"],
            self._peer_filters(candidate["tableName"], row),
            page_size,
        )
        metrics = self._peer_metric_columns(candidate["tableName"], row, measure)
        comparisons = []
        for col in metrics:
            vals = [self._to_float(r.get(col)) for r in peer_rows if self._to_float(r.get(col)) is not None]
            current = self._to_float(row.get(col))
            if current is None or not vals:
                continue
            vals_sorted = sorted(vals)
            avg = sum(vals_sorted) / len(vals_sorted)
            median = vals_sorted[len(vals_sorted) // 2]
            percentile = sum(1 for v in vals_sorted if v <= current) / len(vals_sorted) * 100
            comparisons.append({
                "field": col,
                "name": self._display_label_for_column(candidate["tableName"], col) or col,
                "current": current,
                "peerAvg": avg,
                "peerMedian": median,
                "peerMin": vals_sorted[0],
                "peerMax": vals_sorted[-1],
                "percentile": percentile,
                "diffPct": ((current - avg) / abs(avg) * 100) if avg else None,
            })
        standout = sorted(
            comparisons,
            key=lambda x: abs(x["diffPct"] or 0),
            reverse=True,
        )[:3]
        summary = self._peer_summary(peer_filters, peer_rows, standout)
        result = {
            "rules": [
                {"field": f, "name": n, "value": self._jsonable_value(v), "op": _op}
                for f, n, v, _op in peer_filters
            ],
            "count": len(peer_rows),
            "comparisons": comparisons,
            "standout": standout,
            "summary": summary,
        }
        quick = self._interpret_entity_peer_analysis(
            candidate=candidate,
            row=row,
            measure=measure,
            entity_value=entity_value,
            joined_dimensions=joined_dimensions or [],
            peer_filters=peer_filters,
            peer_count=len(peer_rows),
            standout=standout,
            comparisons=comparisons,
        )
        if quick:
            result["quickInterpretation"] = quick
        return result

    def _peer_filters(self, table_name: str, row: dict[str, Any]) -> list[tuple[str, str, Any, str]]:
        table_set = {table_name}
        dims = [d for d in self._dimensions.values() if table_name in d.tables]
        candidates: list[tuple[tuple[int, int, int, str], str, str, Any, str]] = []
        seen: set[tuple[str, str, str]] = set()
        for dim in dims:
            row_num = self._min_row_num(dim, table_set)
            for app in self._dimension_apps(dim.code, table_set):
                col = app.get("dimFactColumn") or app.get("masterPrimaryKey") or ""
                if not col or col not in row or row.get(col) in (None, ""):
                    continue
                raw_val = row.get(col)
                name = self._clean_business_label(dim.cn_name) or self._display_label_for_column(table_name, col) or col
                value = raw_val
                op = "eq"
                if dim.is_time:
                    bucket = self._time_peer_bucket(table_name, col, raw_val, dims)
                    if bucket:
                        name, value, op = bucket
                key = (col, name, str(value))
                if key in seen:
                    continue
                seen.add(key)
                level_rank = {
                    "month": 0,
                    "week": 1,
                    "quarter": 2,
                    "year": 3,
                    "day": 4,
                    "hour": 5,
                }.get(dim.level_code or "", 6)
                cardinality = row_num if row_num is not None else 10**9
                candidates.append((
                    (
                        0 if dim.is_time else 1,
                        level_rank,
                        cardinality,
                        name,
                    ),
                    col,
                    name,
                    value,
                    op,
                ))
        candidates.sort(key=lambda item: item[0])
        return [(col, name, value, op) for _score, col, name, value, op in candidates[:3]]

    def _time_peer_bucket(
        self,
        table_name: str,
        fact_column: str,
        raw_value: Any,
        dims: list[DimensionMeta],
    ) -> tuple[str, Any, str] | None:
        raw_text = str(raw_value or "").strip()
        if not re.fullmatch(r"\d{8}", raw_text):
            return None
        for dim in dims:
            if dim.level_code != "month":
                continue
            for app in self._dimension_apps(dim.code, {table_name}):
                col = app.get("dimFactColumn") or app.get("masterPrimaryKey") or ""
                if col == fact_column:
                    return (self._clean_business_label(dim.cn_name) or dim.cn_name, raw_text[:6], "prefix")
        return None

    def _query_peer_rows(self, table_name: str, filters: list[tuple[str, str, Any, str]], limit: int) -> list[dict[str, Any]]:
        conn = None
        try:
            import pymysql
            conn_cfg = self._db_connection_for_table(table_name, self._schema_for_table(table_name))
            conn = pymysql.connect(
                host=conn_cfg["host"],
                port=conn_cfg["port"],
                user=conn_cfg["user"],
                password=conn_cfg["password"],
                database=conn_cfg["database"],
                charset="utf8mb4",
            )
            where = []
            params = []
            for col, _name, val, op in filters:
                if op == "prefix":
                    where.append(f"CAST(`{col}` AS CHAR) LIKE %s")
                    params.append(f"{val}%")
                else:
                    where.append(f"`{col}` = %s")
                    params.append(val)
            where_clause = ("WHERE " + " AND ".join(where)) if where else ""
            sql = f"SELECT * FROM {self._sql_table(table_name, '')} {where_clause} LIMIT %s"
            params.append(max(1, min(int(limit or 5000), 50000)))
            with conn.cursor(pymysql.cursors.DictCursor) as cur:
                cur.execute(sql, params)
                return list(cur.fetchall())
        except Exception:
            return []
        finally:
            try:
                if conn:
                    conn.close()
            except Exception:
                pass

    def _query_adaptive_peer_rows(
        self,
        table_name: str,
        filters: list[tuple[str, str, Any, str]],
        limit: int,
    ) -> tuple[list[tuple[str, str, Any, str]], list[dict[str, Any]]]:
        active = list(filters)
        rows = self._query_peer_rows(table_name, active, limit)
        while len(rows) < 20 and len(active) > 1:
            active = active[:-1]
            rows = self._query_peer_rows(table_name, active, limit)
        return active, rows

    def _peer_metric_columns(self, table_name: str, row: dict[str, Any], measure: MeasureMeta | None) -> list[str]:
        candidates: list[str] = []
        if measure:
            for app in self._measure_apps(measure.code):
                if ((app.get("table") or {}).get("tableName") or "") == table_name and app.get("factColumn"):
                    candidates.append(app["factColumn"])
        for m in self._measures.values():
            if table_name not in m.tables:
                continue
            for app in self._measure_apps(m.code):
                if ((app.get("table") or {}).get("tableName") or "") == table_name and app.get("factColumn"):
                    candidates.append(app["factColumn"])
        dimension_keys = self._dimension_key_columns_for_table(table_name)
        for col, val in row.items():
            if self._to_float(val) is None:
                continue
            if col in dimension_keys or self._looks_like_identifier_column(table_name, col):
                continue
            candidates.append(col)
        return list(dict.fromkeys([c for c in candidates if c in row and not self._looks_like_identifier_column(table_name, c)]))[:10]

    def _dimension_key_columns_for_table(self, table_name: str) -> set[str]:
        keys: set[str] = set()
        for dim in self._dimensions.values():
            for app in self._dimension_apps(dim.code, {table_name}):
                for field in ("dimFactColumn", "masterPrimaryKey"):
                    col = app.get(field) or ""
                    if col:
                        keys.add(col)
        return keys

    def _looks_like_identifier_column(self, table_name: str, column_name: str) -> bool:
        source_col = self._source_column_meta(table_name, column_name)
        business_col = self._business_column_meta(table_name, column_name)
        if source_col.get("isPrimaryKey") or business_col.get("isPrimaryKey"):
            return True
        lowered = column_name.lower()
        return bool(re.search(r"(^|_)(id|sk|key|code|no|number)$", lowered))

    def _display_label_for_column(self, table_name: str, column_name: str) -> str:
        if not column_name:
            return ""
        qualified = f"{table_name}.{column_name}" if table_name else column_name
        cached = self._dynamic_column_labels.get(qualified) or self._dynamic_column_labels.get(column_name)
        if cached:
            return cached
        graph_label = self._clean_business_label(self._graph_column_label_from_ttl(table_name, column_name))
        if graph_label:
            self._dynamic_column_labels[qualified] = graph_label
            return graph_label
        translated = self._translate_missing_column_labels([qualified]).get(qualified, "")
        if translated:
            self._dynamic_column_labels[qualified] = translated
            return translated
        return column_name

    def _peer_summary(
        self,
        filters: list[tuple[str, str, Any, str]],
        peer_rows: list[dict[str, Any]],
        standout: list[dict[str, Any]],
    ) -> str:
        if not peer_rows:
            return "同类条件下没有查询到可比较样本。"
        rule_text = "、".join(f"{name}={val}" for _field, name, val, _op in filters) or "同事实表"
        if not standout:
            return f"按 {rule_text} 定义同类记录，共 {len(peer_rows)} 条；暂无足够数值字段形成显著差异。"
        pieces = []
        for item in standout:
            diff = item.get("diffPct")
            if diff is None:
                continue
            direction = "高于" if diff >= 0 else "低于"
            pieces.append(f"{item['name']}{direction}同类均值 {abs(diff):.1f}%")
        return f"按 {rule_text} 定义同类记录，共 {len(peer_rows)} 条；" + "，".join(pieces) + "。"

    def _interpret_entity_peer_analysis(
        self,
        *,
        candidate: dict[str, Any],
        row: dict[str, Any],
        measure: MeasureMeta | None,
        entity_value: Any,
        joined_dimensions: list[dict[str, Any]],
        peer_filters: list[tuple[str, str, Any, str]],
        peer_count: int,
        standout: list[dict[str, Any]],
        comparisons: list[dict[str, Any]],
    ) -> dict[str, Any]:
        if not row or (not peer_count and not comparisons):
            return {}
        try:
            from kg_builder.utils.llm_config import llm_config_from_env

            cfg = llm_config_from_env(Path.cwd())
            api_key = (cfg.get("api_key") or "").strip()
            base_url = (cfg.get("base_url") or "").strip().rstrip("/")
            model = (cfg.get("model") or "").strip()
            if not api_key or not base_url or not model:
                return {}

            table_name = str(candidate.get("tableName") or "")
            row_items = []
            for col, value in list(row.items())[:40]:
                if value in (None, ""):
                    continue
                row_items.append({
                    "name": self._display_label_for_column(table_name, col),
                    "value": self._jsonable_value(value),
                })
                if len(row_items) >= 18:
                    break

            payload = {
                "object": {
                    "fieldName": self._entity_display_label(candidate),
                    "value": self._jsonable_value(entity_value),
                    "factTableName": self._table_business_label(table_name),
                },
                "measure": {
                    "name": measure.cn_name if measure else "",
                    "definition": measure.definition if measure else "",
                },
                "sameClassRules": [
                    {"name": name, "value": self._jsonable_value(value)}
                    for _field, name, value, _op in peer_filters
                ],
                "sameClassSampleCount": peer_count,
                "standoutComparisons": [
                    {
                        "name": item.get("name"),
                        "current": self._jsonable_value(item.get("current")),
                        "peerAvg": self._jsonable_value(item.get("peerAvg")),
                        "percentile": self._jsonable_value(item.get("percentile")),
                        "diffPct": self._jsonable_value(item.get("diffPct")),
                    }
                    for item in standout[:5]
                ],
                "allComparedFields": [
                    {
                        "name": item.get("name"),
                        "current": self._jsonable_value(item.get("current")),
                        "peerAvg": self._jsonable_value(item.get("peerAvg")),
                        "diffPct": self._jsonable_value(item.get("diffPct")),
                    }
                    for item in comparisons[:10]
                ],
                "joinedDimensions": [
                    {
                        "name": item.get("name"),
                        "value": self._jsonable_value(item.get("value")),
                    }
                    for item in joined_dimensions[:12]
                ],
                "currentRecordSample": row_items,
                "outputSchema": {
                    "summary": "一段不超过80字的中文总览",
                    "findings": ["2到4条中文要点"],
                    "suggestedNextQuestions": ["1到3个可继续追问的问题"],
                },
            }
            system = (
                "你是 AD 智能Insight 的同类对象快速解读器。"
                "只返回 JSON，不要返回 Markdown。"
                "所有展示文本必须是中文，不要输出 SQL、英文字段名、代码或技术字段。"
                "如果输入里有英文枚举值，输出时必须翻译成中文；无法确定含义时用“该取值”代替，不要原样复制英文。"
                "只能基于用户给定的图谱名称、同类条件、样本数、当前值和同类统计解读；"
                "不要编造未提供的指标、维度、字段含义或因果结论。"
                "如果样本量不足，要明确提醒结论仅供参考。"
            )
            body = {
                "model": model,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
                ],
                "temperature": 0.2,
                "max_tokens": 900,
            }
            req = urllib.request.Request(
                f"{base_url}/chat/completions",
                data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {api_key}",
                },
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=18) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            content = (
                data.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "")
                .strip()
            )
            parsed = self._parse_json_object(content)
            if not parsed:
                text = self._clean_llm_output_text(content)
                return {"source": "llm", "summary": text} if text else {}
            summary = self._clean_llm_output_text(parsed.get("summary"))
            findings = [
                self._clean_llm_output_text(item)
                for item in (parsed.get("findings") or [])
                if self._clean_llm_output_text(item)
            ][:4]
            next_questions = [
                self._clean_llm_output_text(item)
                for item in (parsed.get("suggestedNextQuestions") or parsed.get("nextQuestions") or [])
                if self._clean_llm_output_text(item)
            ][:3]
            result: dict[str, Any] = {"source": "llm"}
            if summary:
                result["summary"] = summary
            if findings:
                result["findings"] = findings
            if next_questions:
                result["suggestedNextQuestions"] = next_questions
            return result if len(result) > 1 else {}
        except Exception as exc:
            self._log(f"[NLQ] 同类对象 LLM 快速解读不可用: {exc}")
            return {}

    @staticmethod
    def _parse_json_object(text: Any) -> dict[str, Any]:
        content = str(text or "").strip()
        if not content:
            return {}
        if content.startswith("```"):
            content = re.sub(r"^```(?:json)?\s*|\s*```$", "", content, flags=re.S).strip()
        try:
            parsed = json.loads(content)
            return parsed if isinstance(parsed, dict) else {}
        except Exception:
            pass
        match = re.search(r"\{.*\}", content, flags=re.S)
        if not match:
            return {}
        try:
            parsed = json.loads(match.group(0))
            return parsed if isinstance(parsed, dict) else {}
        except Exception:
            return {}

    @staticmethod
    def _clean_llm_output_text(value: Any) -> str:
        text = str(value or "").strip()
        if not text:
            return ""
        text = re.sub(r"```(?:json)?|```", "", text).strip()
        text = re.sub(r"<think>.*?</think>", "", text, flags=re.S | re.I).strip()
        text = re.sub(r"\s+", " ", text).strip()
        text = re.sub(r"[A-Za-z][A-Za-z0-9_.-]*", "该取值", text)
        text = re.sub(r"该取值[（(]([^）)]*[\u4e00-\u9fff][^）)]*)[）)]", r"\1", text)
        text = re.sub(r"该取值(?:\s*该取值)+", "该取值", text).strip()
        return text

    def _relationship_context(self, question: str, intent: dict[str, Any]) -> dict[str, Any]:
        tokens = self._tokens(question)
        measure_hits = self._rank_measures(question, tokens)
        preferred_tables = {
            str(table) for table in (intent.get("preferredFactTables") or [])
            if table
        }
        if preferred_tables:
            compatible_hits = [hit for hit in measure_hits if hit[1].tables & preferred_tables]
            if compatible_hits:
                measure_hits = compatible_hits
        driver = self._choose_relationship_driver(question, measure_hits, intent)
        if not driver:
            return {}
        # 实体上下文：从上轮 entity_lookup 继承同类范围
        entity_ctx = self._resolve_entity_context(intent)
        has_peer_scope = entity_ctx is not None and bool(
            re.search(r"同类|同组|同月|同批|同种|类似|相似|相近|一样|相同|同类订单|同类记录", question)
        )
        table_candidates = []
        for table in sorted(driver.tables):
            if preferred_tables and table not in preferred_tables:
                continue
            driver_app = self._measure_app_for_table(driver, table)
            if not driver_app.get("factColumn"):
                continue
            outcomes = self._relationship_outcomes(question, table, driver, measure_hits, intent)
            outcomes = [
                item for item in outcomes
                if item.get("app", {}).get("factColumn")
                and item["app"]["factColumn"] != driver_app.get("factColumn")
            ]
            if not outcomes:
                continue
            dimensions = self._relationship_dimensions(question, table, entity_ctx)
            # 同类追问：纳入实体维度做特征对比
            if has_peer_scope and entity_ctx.get("tableName") == table:
                # 把实体的维度值加入分析维度列表，用于多维度特征比较
                entity_dims = entity_ctx.get("joinedDimensions") or []
                existing_dim_codes = {d["dimension"].code for d in dimensions}
                for ed in entity_dims:
                    dim_code = ed.get("dimCode", "")
                    if dim_code and dim_code not in existing_dim_codes:
                        dim = self._dimensions.get(dim_code)
                        if dim and table in dim.tables:
                            for app in self._dimension_apps(dim.code, {table}):
                                col = app.get("dimFactColumn") or app.get("masterPrimaryKey") or ""
                                if col:
                                    dimensions.append({"dimension": dim, "app": app, "col": col})
                                    existing_dim_codes.add(dim_code)
                                    break
            score = 100 + len(outcomes) * 20 + len(dimensions) * 8
            table_label = self._table_business_label(table)
            if table_label and self._norm(table_label) in self._norm(question):
                score += 40
            ctx = {
                "table": table,
                "driver": {"measure": driver, "app": driver_app},
                "outcomes": outcomes[:5],
                "dimensions": dimensions[:6],  # 同类追问放宽到6个维度
            }
            if has_peer_scope:
                ctx["entityContext"] = entity_ctx
            table_candidates.append((score, ctx))
        if not table_candidates:
            return {}
        table_candidates.sort(key=lambda item: (-item[0], item[1]["table"]))
        return table_candidates[0][1]

    def _resolve_entity_context(self, intent: dict[str, Any]) -> Optional[dict[str, Any]]:
        """从上轮对话的实体上下文中提取同类对比所需的信息。"""
        # 优先从 intent 的 inheritedEntity 取
        entity = intent.get("inheritedEntity") or intent.get("contextEntity") or {}
        if isinstance(entity, dict) and entity.get("value"):
            return entity
        # 从 preferredFactTables + 问题中推断
        return None

    def _choose_relationship_driver(
        self,
        question: str,
        measure_hits: list[tuple[float, MeasureMeta]],
        intent: dict[str, Any],
    ) -> MeasureMeta | None:
        q_norm = self._norm(question)
        # 问题中明确提到的指标优先于继承的上下文指标
        # 计算"驱动相关度"：指标的业务词与问题的重叠程度（给非通用词更高权重）
        q_tokens = set(self._tokens(q_norm))
        # 通用词：在多数指标名中都出现的词，不应作为区分信号
        common_words = {"订单", "销售", "目录", "网络", "数量", "金额", "总额", "数据", "统计"}
        def _driver_relevance(m: MeasureMeta) -> tuple[int, float]:
            if not m.cn_name:
                return (0, 0)
            m_norm = self._norm(m.cn_name)
            if m_norm in q_norm:
                return (2, 100)  # 完整匹配最高优先
            m_tokens = [t for t in self._tokens(m_norm) if len(t) >= 2]
            specific_tokens = [t for t in m_tokens if t not in common_words]
            specific_overlap = [t for t in specific_tokens if t in q_tokens]
            all_overlap = [t for t in m_tokens if t in q_tokens]
            return (
                len(specific_overlap),  # 优先看特定词命中数
                len(all_overlap) * 0.1 + (measure_hits_score.get(m.code, 0) * 0.01),  # 其次看总重叠+rank分
            )
        measure_hits_score = {m.code: score for score, m in measure_hits}
        driver_candidates = [
            (_driver_relevance(m) + (m,), m)
            for _score, m in measure_hits
            if _driver_relevance(m)[0] > 0  # 至少要有一个特定业务词命中
        ]
        if driver_candidates:
            driver_candidates.sort(key=lambda x: (x[0][0], x[0][1]), reverse=True)
            best = driver_candidates[0][1]
            return best
        # 无特定业务词命中时，检查继承指标
        hinted_code = str(
            intent.get("driverMeasureCode") or intent.get("measureCode")
            or intent.get("inheritedMeasureCode") or ""
        ).strip()
        if hinted_code and hinted_code in self._measures:
            return self._measures[hinted_code]
        # 兜底：用最高 rank 分的指标
        if measure_hits:
            return measure_hits[0][1]
        return None
        scored = []
        hit_scores = {m.code: score for score, m in measure_hits}
        for measure in self._measures.values():
            name_text = self._norm(measure.cn_name)
            semantic_text = self._norm(" ".join([measure.cn_name, measure.definition, measure.description, measure.en_name]))
            if (asks_driver and any(term in name_text for term in driver_terms)) or (
                not asks_driver and any(term in semantic_text for term in driver_terms)
            ):
                exact_boost = 80 if self._norm(measure.cn_name) and self._norm(measure.cn_name) in q_norm else 0
                scored.append((hit_scores.get(measure.code, 0.0) + exact_boost + 80, measure))
        if not scored and measure_hits:
            scored.append(measure_hits[0])
        scored.sort(key=lambda item: (-item[0], item[1].code))
        return scored[0][1] if scored else None

    def _relationship_outcomes(
        self,
        question: str,
        table: str,
        driver: MeasureMeta,
        measure_hits: list[tuple[float, MeasureMeta]],
        intent: dict[str, Any],
    ) -> list[dict[str, Any]]:
        hinted_codes = [
            str(c).strip()
            for c in (intent.get("outcomeMeasureCodes") or intent.get("measureCodes") or [])
            if str(c).strip()
        ]
        selected: list[MeasureMeta] = []
        for code in hinted_codes:
            m = self._measures.get(code)
            if m and m.code != driver.code and table in m.tables:
                selected.append(m)
        if not selected:
            for _score, m in measure_hits:
                if m.code != driver.code and table in m.tables:
                    selected.append(m)
        if not selected or re.search(r"表现|效果|好方法|差异|跨维度|图谱跳跃", question):
            preferred_terms = ("净利润", "利润", "销售数量", "数量", "销售额", "销售金额", "含税", "付款", "订单数")
            for m in self._measures.values():
                if m.code == driver.code or table not in m.tables:
                    continue
                text = self._norm(" ".join([m.cn_name, m.definition, m.description]))
                if any(term in text for term in preferred_terms):
                    selected.append(m)
        result = []
        seen = set()
        for m in selected:
            if m.code in seen:
                continue
            seen.add(m.code)
            app = self._measure_app_for_table(m, table)
            col = app.get("factColumn") or ""
            if not col or self._looks_like_identifier_column(table, col):
                continue
            result.append({"measure": m, "app": app})
        return result[:6]

    def _relationship_dimensions(self, question: str, table: str, entity_ctx: Optional[dict[str, Any]] = None) -> list[dict[str, Any]]:
        dims = [d for d in self._dimensions.values() if table in d.tables]
        hits = self._rank_dimensions(question, self._tokens(question), dims)
        q_norm = self._norm(question)
        wants_dimensions = bool(re.search(r"不同|差异|跨维度|图谱跳跃|分组|维度|按|各|每|特征|同类|画像|什么样|哪些特征|什么样", question))
        # 实体同类追问：即使问题没显式提维度，也需要维度做特征对比
        if not wants_dimensions and entity_ctx and entity_ctx.get("value"):
            wants_dimensions = True
        if not wants_dimensions:
            return []
        selected = [
            d for _score, d in hits
            if not d.is_time and self._norm(d.cn_name) and self._norm(d.cn_name) in q_norm
        ]
        if not selected:
            selected = [d for score, d in hits if score >= 40 and not d.is_time]
        if not selected:
            selected = [d for d in self._sort_available_dims(dims, {table}) if not d.is_time]
        result = []
        seen_cols = set()
        for dim in selected:
            for app in self._dimension_apps(dim.code, {table}):
                col = app.get("dimFactColumn") or app.get("masterPrimaryKey") or ""
                if not col or col in seen_cols:
                    continue
                seen_cols.add(col)
                result.append({"dimension": dim, "app": app})
                break
        return result

    def _measure_app_for_table(self, measure: MeasureMeta, table: str) -> dict[str, Any]:
        for app in self._measure_apps(measure.code):
            app_table = ((app.get("table") or {}).get("tableName") or "")
            if app_table == table:
                return app
        return {}

    def _relationship_matched_payload(self, context: dict[str, Any]) -> dict[str, Any]:
        return {
            "measureCode": context["driver"]["measure"].code,
            "measureName": context["driver"]["measure"].cn_name,
            "factTables": [context["table"]],
            "relatedMeasures": [
                {"code": item["measure"].code, "name": item["measure"].cn_name}
                for item in context.get("outcomes", [])
            ],
            "dimensionCodes": [item["dimension"].code for item in context.get("dimensions", [])],
            "dimensions": [
                {
                    "code": item["dimension"].code,
                    "name": item["dimension"].cn_name,
                    "level": item["dimension"].level_code,
                    "isTime": item["dimension"].is_time,
                    "tables": sorted(item["dimension"].tables),
                }
                for item in context.get("dimensions", [])
            ],
        }

    def _query_relationship_rows(
        self,
        context: dict[str, Any],
        limit: int,
    ) -> tuple[list[dict[str, Any]], str, str]:
        table = context["table"]
        columns = [context["driver"]["app"].get("factColumn")]
        columns.extend(item["app"].get("factColumn") for item in context.get("outcomes", []))
        columns.extend(
            item["app"].get("dimFactColumn") or item["app"].get("masterPrimaryKey")
            for item in context.get("dimensions", [])
        )
        # 实体同类上下文：纳入实体的维度列，用于后续多维度特征对比
        entity_ctx = context.get("entityContext") or {}
        peer_filters = entity_ctx.get("peerFilters") or []
        for pf in peer_filters:
            field = pf.get("field", "")
            if field and field not in columns:
                columns.append(field)
        columns = list(dict.fromkeys([c for c in columns if c]))
        if not columns:
            return [], "", "没有可查询字段"
        conn = None
        try:
            import pymysql
            conn_cfg = self._db_connection_for_table(table, self._schema_for_table(table))
            conn = pymysql.connect(
                host=conn_cfg["host"],
                port=conn_cfg["port"],
                user=conn_cfg["user"],
                password=conn_cfg["password"],
                database=conn_cfg["database"],
                charset="utf8mb4",
            )
            select_cols = ", ".join(f"`{c}`" for c in columns)
            driver_col = context["driver"]["app"].get("factColumn")
            # 同类范围过滤：基于实体对等维度值限制比较范围
            where_parts = [f"`{driver_col}` IS NOT NULL"]
            where_params: list = []
            if peer_filters:
                for pf in peer_filters:
                    field = pf.get("field", "")
                    val = pf.get("value")
                    op = pf.get("op", "eq")
                    if field and val is not None:
                        if op == "prefix":
                            where_parts.append(f"CAST(`{field}` AS CHAR) LIKE %s")
                            where_params.append(f"{val}%")
                        else:
                            where_parts.append(f"`{field}` = %s")
                            where_params.append(val)
            where_clause = "WHERE " + " AND ".join(where_parts)
            sql = (
                f"SELECT {select_cols} FROM {self._sql_table(table, '')} "
                f"{where_clause} LIMIT %s"
            )
            params = where_params + [max(1, min(int(limit or 500), 50000))]
            with conn.cursor(pymysql.cursors.DictCursor) as cur:
                cur.execute(sql, params)
                rows = list(cur.fetchall())
            shown_sql = re.sub(r"%s", lambda m: str(params.pop(0)) if params else "?", sql, count=len(params))
            return rows, shown_sql, ""
        except Exception as exc:
            return [], "", f"相关关系分析取数失败: {exc}"
        finally:
            try:
                if conn:
                    conn.close()
            except Exception:
                pass

    def _analyze_relationship_rows(
        self,
        question: str,
        context: dict[str, Any],
        rows: list[dict[str, Any]],
    ) -> dict[str, Any]:
        table = context["table"]
        driver_measure = context["driver"]["measure"]
        driver_col = context["driver"]["app"].get("factColumn")
        pairs = [(row, self._to_float(row.get(driver_col))) for row in rows]
        valid_rows = [row for row, value in pairs if value is not None]
        driver_values = [value for _row, value in pairs if value is not None]
        if not driver_values:
            return {
                "summary": "样本中没有可用的折扣类数值，无法判断相关关系。",
                "sample": {"rowCount": len(rows), "validRowCount": 0},
                "industryComparison": self._relationship_industry_comparison(),
            }
        threshold = self._percentile(driver_values, 0.75)
        high_rows = [row for row in valid_rows if (self._to_float(row.get(driver_col)) or 0.0) >= threshold]
        base_rows = [row for row in valid_rows if (self._to_float(row.get(driver_col)) or 0.0) < threshold]
        positive_rows = [row for row in valid_rows if (self._to_float(row.get(driver_col)) or 0.0) > 0]

        comparisons = []
        correlations = []
        for item in context.get("outcomes", []):
            measure = item["measure"]
            col = item["app"].get("factColumn")
            high_vals = self._numeric_values(high_rows, col)
            base_vals = self._numeric_values(base_rows, col)
            all_pairs = [
                (self._to_float(row.get(driver_col)), self._to_float(row.get(col)))
                for row in valid_rows
            ]
            all_pairs = [(x, y) for x, y in all_pairs if x is not None and y is not None]
            high_avg = self._mean(high_vals)
            base_avg = self._mean(base_vals)
            lift = self._lift_pct(high_avg, base_avg)
            effect = self._cohen_d(high_vals, base_vals)
            corr = self._pearson([x for x, _y in all_pairs], [y for _x, y in all_pairs])
            verdict = self._relationship_verdict(
                measure.cn_name, lift, effect, corr, len(all_pairs),
                driver_label=self._short_driver_label(driver_measure.cn_name),
            )
            comparisons.append({
                "measure": measure.cn_name,
                "columnLabel": self._display_label_for_column(table, col),
                "highAvg": high_avg,
                "baseAvg": base_avg,
                "liftPct": lift,
                "effectSize": effect,
                "highCount": len(high_vals),
                "baseCount": len(base_vals),
                "verdict": verdict,
            })
            correlations.append({
                "measure": measure.cn_name,
                "driver": driver_measure.cn_name,
                "correlation": corr,
                "sampleCount": len(all_pairs),
                "strength": self._correlation_strength(corr),
            })

        dimension_breakdowns = [
            self._relationship_dimension_breakdown(context, dim_item, positive_rows, threshold)
            for dim_item in context.get("dimensions", [])
        ]
        dimension_breakdowns = [item for item in dimension_breakdowns if item.get("groups")]

        # 多维度特征画像：比较高/低组在各业务维度上的分布差异
        feature_profile = self._build_feature_profile(
            context, high_rows, base_rows, threshold, driver_measure.cn_name,
        )

        answer = self._relationship_answer(
            question, driver_measure, threshold, comparisons, correlations,
            dimension_breakdowns, feature_profile,
        )
        return {
            "summary": answer["summary"],
            "conclusion": answer["conclusion"],
            "driver": {
                "measure": driver_measure.cn_name,
                "highThreshold": threshold,
                "thresholdLabel": "排名前25%的门槛值",
            },
            "sample": {
                "rowCount": len(rows),
                "validRowCount": len(valid_rows),
                "discountPositiveRowCount": len(positive_rows),
                "highDiscountRowCount": len(high_rows),
                "isSample": True,
            },
            "highDiscountComparisons": comparisons,
            "correlations": correlations,
            "dimensionBreakdowns": dimension_breakdowns,
            "featureProfile": feature_profile,
            "industryComparison": self._relationship_industry_comparison(),
            "suggestedNextQuestions": answer["suggestedNextQuestions"],
        }

    def _relationship_dimension_breakdown(
        self,
        context: dict[str, Any],
        dim_item: dict[str, Any],
        rows: list[dict[str, Any]],
        high_threshold: float,
    ) -> dict[str, Any]:
        table = context["table"]
        dim = dim_item["dimension"]
        app = dim_item["app"]
        key_col = app.get("dimFactColumn") or app.get("masterPrimaryKey") or ""
        if not key_col:
            return {}
        value_map = self._dimension_value_map(table, app, [row.get(key_col) for row in rows])
        driver_col = context["driver"]["app"].get("factColumn")
        outcome_cols = [
            (item["measure"].cn_name, item["app"].get("factColumn"))
            for item in context.get("outcomes", [])
            if item["app"].get("factColumn")
        ][:3]
        grouped: dict[str, list[dict[str, Any]]] = {}
        for row in rows:
            raw = row.get(key_col)
            label = value_map.get(str(raw), self._display_data_value(raw))
            grouped.setdefault(label, []).append(row)
        groups = []
        for label, group_rows in grouped.items():
            if len(group_rows) < 2:
                continue
            driver_vals = self._numeric_values(group_rows, driver_col)
            item = {
                "value": label,
                "rowCount": len(group_rows),
                "avgDriver": self._mean(driver_vals),
                "highDriverRatePct": (
                    sum(1 for row in group_rows if (self._to_float(row.get(driver_col)) or 0.0) >= high_threshold)
                    / len(group_rows) * 100
                ) if group_rows else 0,
                "outcomes": [],
            }
            for measure_name, col in outcome_cols:
                item["outcomes"].append({
                    "measure": measure_name,
                    "avg": self._mean(self._numeric_values(group_rows, col)),
                })
            groups.append(item)
        groups.sort(key=lambda item: (-item["highDriverRatePct"], -item["rowCount"], item["value"]))
        insight = ""
        if groups:
            top = groups[0]
            bottom = groups[-1]
            insight = (
                f"{dim.cn_name}下高折扣占比最高的是「{top['value']}」"
                f"（{top['highDriverRatePct']:.1f}%），最低的是「{bottom['value']}」"
                f"（{bottom['highDriverRatePct']:.1f}%）。"
            )
        return {
            "dimension": dim.cn_name,
            "joinPath": self._join_description(dim.cn_name, app),
            "groups": groups[:8],
            "insight": insight,
        }

    def _dimension_value_map(self, fact_table: str, app: dict[str, Any], values: list[Any]) -> dict[str, str]:
        unique = [v for v in dict.fromkeys([v for v in values if v not in (None, "")])]
        if not unique or not app.get("dimTable") or not app.get("dimPrimaryKey") or not app.get("dimColumn"):
            return {str(v): self._display_data_value(v) for v in unique}
        conn = None
        try:
            import pymysql
            conn_cfg = self._db_connection_for_table(fact_table, self._schema_for_table(fact_table))
            conn = pymysql.connect(
                host=conn_cfg["host"],
                port=conn_cfg["port"],
                user=conn_cfg["user"],
                password=conn_cfg["password"],
                database=conn_cfg["database"],
                charset="utf8mb4",
            )
            placeholders = ", ".join(["%s"] * len(unique[:500]))
            sql = (
                f"SELECT `{app['dimPrimaryKey']}` AS k, `{app['dimColumn']}` AS v "
                f"FROM {self._sql_table(app['dimTable'], '')} "
                f"WHERE `{app['dimPrimaryKey']}` IN ({placeholders})"
            )
            with conn.cursor(pymysql.cursors.DictCursor) as cur:
                cur.execute(sql, unique[:500])
                found = list(cur.fetchall())
            mapping = {str(row.get("k")): self._display_data_value(row.get("v")) for row in found}
            for value in unique:
                mapping.setdefault(str(value), self._display_data_value(value))
            return mapping
        except Exception:
            return {str(v): self._display_data_value(v) for v in unique}
        finally:
            try:
                if conn:
                    conn.close()
            except Exception:
                pass

    @staticmethod
    def _short_driver_label(driver_name: str) -> str:
        """从指标全名中提取简短业务标签，如 '目录折扣总额'→'折扣金额', '目录销售数量'→'销售数量'"""
        name = driver_name or ""
        for prefix in ("目录", "网络"):
            if name.startswith(prefix):
                name = name[len(prefix):]
                break
        return name or driver_name

    def _build_feature_profile(
        self,
        context: dict[str, Any],
        high_rows: list[dict[str, Any]],
        base_rows: list[dict[str, Any]],
        threshold: float,
        driver_name: str,
    ) -> dict[str, Any]:
        """多维度特征画像：比较 high/base 组在各业务维度上的分布差异。"""
        table = context["table"]
        dims = context.get("dimensions", [])
        short_label = self._short_driver_label(driver_name)
        if not dims or len(high_rows) < 5 or len(base_rows) < 5:
            return {"dimensions": [], "summary": "样本量不足，无法生成特征画像"}
        high_total = len(high_rows)
        base_total = len(base_rows)
        all_total = high_total + base_total
        overall_high_pct = high_total / all_total * 100

        dim_profiles: list[dict[str, Any]] = []
        for dim_item in dims:
            dim = dim_item["dimension"]
            col = dim_item.get("col") or dim_item["app"].get("dimFactColumn") or dim_item["app"].get("masterPrimaryKey") or ""
            if not col:
                continue
            # 统计每个维度值在 high/base 组中的分布
            high_counts: dict[str, int] = {}
            base_counts: dict[str, int] = {}
            for row in high_rows:
                val = str(row.get(col, "") or "").strip()
                if val:
                    high_counts[val] = high_counts.get(val, 0) + 1
            for row in base_rows:
                val = str(row.get(col, "") or "").strip()
                if val:
                    base_counts[val] = base_counts.get(val, 0) + 1
            all_vals = set(high_counts.keys()) | set(base_counts.keys())
            if len(all_vals) < 2 or len(all_vals) > 100:
                continue  # 跳过无区分度或基数过高的维度
            # 计算每个值在高组中的占比 vs 基准组中的占比
            value_diffs: list[dict[str, Any]] = []
            for val in all_vals:
                hc = high_counts.get(val, 0)
                bc = base_counts.get(val, 0)
                high_pct = hc / high_total * 100 if high_total else 0
                base_pct = bc / base_total * 100 if base_total else 0
                diff = high_pct - base_pct  # 正值=高组过表达
                total = hc + bc
                if total >= 3:  # 过滤噪声
                    value_diffs.append({
                        "value": val,
                        "label": self._display_label_for_column(table, col) or val,
                        "highCount": hc, "baseCount": bc,
                        "highPct": round(high_pct, 1), "basePct": round(base_pct, 1),
                        "diffPct": round(diff, 1),
                    })
            if not value_diffs:
                continue
            value_diffs.sort(key=lambda x: abs(x["diffPct"]), reverse=True)
            top_over = [v for v in value_diffs if v["diffPct"] > 0][:3]
            top_under = [v for v in value_diffs if v["diffPct"] < 0][:3]
            # 维度级汇总：用最高 |diffPct| 作为该维度的区分度
            max_diff = max(abs(v["diffPct"]) for v in value_diffs)
            dist_label = "差异明显" if max_diff >= 15 else ("有一定差异" if max_diff >= 8 else "略有差异")
            dim_profiles.append({
                "dimension": dim.cn_name or col,
                "column": col,
                "distinctiveness": dist_label,
                "maxDiffPct": round(max_diff, 1),
                "overrepresented": top_over,
                "underrepresented": top_under,
            })
        dim_profiles.sort(key=lambda x: -x["maxDiffPct"])

        # 生成文字摘要
        summary_parts: list[str] = []
        if dim_profiles:
            top_dim = dim_profiles[0]
            if top_dim["overrepresented"]:
                vals = "、".join(v["value"] for v in top_dim["overrepresented"][:2])
                summary_parts.append(
                    f"{short_label}较高的订单，更常见于「{top_dim['dimension']}」为 {vals} 的情况"
                )
            if len(dim_profiles) >= 2 and dim_profiles[1]["overrepresented"]:
                dv = dim_profiles[1]
                vals = "、".join(v["value"] for v in dv["overrepresented"][:2])
                summary_parts.append(f"其次在「{dv['dimension']}」上也更多出现在 {vals}")
        if not summary_parts:
            summary_parts.append(f"{short_label}较高的订单在各维度上和普通订单没有明显差别")
        return {
            "dimensions": dim_profiles[:8],
            "summary": "；".join(summary_parts) + "。",
            "overallHighPct": round(overall_high_pct, 1),
            "driverName": driver_name,
        }

    def _relationship_answer(
        self,
        question: str,
        driver: MeasureMeta,
        threshold: float,
        comparisons: list[dict[str, Any]],
        correlations: list[dict[str, Any]],
        dimension_breakdowns: list[dict[str, Any]],
        feature_profile: Optional[dict[str, Any]] = None,
    ) -> dict[str, Any]:
        pieces = [
            f"将「{driver.cn_name}」排名前25%的订单作为分析组（门槛值 {self._fmt_number(threshold)}），其余为对比组。"
        ]
        positive = []
        negative = []
        neutral = []
        for comp in comparisons:
            lift = comp.get("liftPct")
            corr = next((c.get("correlation") for c in correlations if c.get("measure") == comp.get("measure")), None)
            if lift is None:
                neutral.append(comp["measure"])
                continue
            phrase = (
                f"「{comp['measure']}」分析组平均 {self._fmt_number(comp.get('highAvg', 0))}，"
                f"对比组平均 {self._fmt_number(comp.get('baseAvg', 0))}，"
                f"{'高出' if lift >= 0 else '低于'}约 {abs(lift):.0f}%"
            )
            pieces.append(phrase + "。")
            if lift >= 10 and (corr is None or corr >= 0.15):
                positive.append(comp["measure"])
            elif lift <= -5 or (corr is not None and corr <= -0.15):
                negative.append(comp["measure"])
            else:
                neutral.append(comp["measure"])
        # 检测问题中提到的业务概念是否被当前指标覆盖
        outcome_names = [comp["measure"] for comp in comparisons]
        driver_short = self._short_driver_label(driver.cn_name)
        # 只检查看起来像业务指标的概念词（含指标相关字眼）
        biz_indicator_chars = re.compile(r"[量额率价费成本利润收入折扣优惠促销退货付款运费亏损盈利订单]")
        q_concepts = set()
        for term in re.split(r"[，。？?、；;：:！!\s]+", question):
            term = term.strip()
            # 只保留含业务指标特征字且长度 2-8 的词
            if 2 <= len(term) <= 8 and biz_indicator_chars.search(term):
                q_concepts.add(term)
        # 检查哪些概念没被指标覆盖（子串匹配）
        covered_text = " ".join([driver.cn_name] + outcome_names)
        uncovered = [c for c in q_concepts if c not in covered_text]

        # 拼结论：先回答核心问题，再说明局限
        conclusion_parts: list[str] = []
        if comparisons:
            pos_items = [c["measure"] for c in comparisons if (c.get("liftPct") or 0) >= 10]
            neg_items = [c["measure"] for c in comparisons if (c.get("liftPct") or 0) <= -5]
            if pos_items and not neg_items:
                conclusion_parts.append(
                    f"在同类订单中，{driver_short}较高的订单，{'、'.join(pos_items)}也明显更高。"
                )
            elif neg_items:
                conclusion_parts.append(
                    f"{driver_short}较高的订单，{'、'.join(neg_items)}反而更低。"
                )
            else:
                conclusion_parts.append(
                    f"{driver_short}较高的订单，其他指标和普通订单差别不大。"
                )
        # 明确告知未覆盖的概念（只对确实在问但图谱没有的业务概念）
        if uncovered:
            conclusion_parts.append(
                f"提醒：当前图谱中没有收录「{'」「'.join(uncovered[:3])}」相关指标，"
                f"以上分析仅基于现有可用指标（{'、'.join(outcome_names[:3])}）。"
                "如需准确回答，建议先补充相关指标数据。"
            )
        elif feature_profile and feature_profile.get("summary"):
            conclusion_parts.append(feature_profile["summary"])
        conclusion = "".join(conclusion_parts)

        if dimension_breakdowns:
            pieces.extend(item["insight"] for item in dimension_breakdowns[:3] if item.get("insight"))
        if feature_profile and feature_profile.get("summary"):
            pieces.append(feature_profile["summary"])
        return {
            "summary": "".join(pieces),
            "conclusion": conclusion,
            "suggestedNextQuestions": [
                "把高折扣订单按商品、客户或促销目的继续拆解，利润差异是否仍然成立？",
                "用相邻周期或相似订单做对照，高折扣是否仍能带来稳定增量？",
            ],
        }

    @staticmethod
    def _relationship_industry_comparison() -> dict[str, list[str]]:
        return {
            "current": [
                "当前实现可以快速自动落地：从业务图谱读取指标、维度和关联路径，直接做样本相关、分层对比和跨维差异。",
                "它适合快速发现问题和生成追问，不依赖业务专家先完成全量语义建模。",
            ],
            "gap": [
                "它还不是因果结论，无法单独证明降价促销带来了利润或销量提升。",
                "行业更优做法会加入实验分组、倾向得分/双重差分、价格弹性、活动成本、商品和客户控制变量。",
                "生产级促销决策还需要利润约束、库存约束、长期复购和渠道蚕食等指标共同评估。",
            ],
        }

    @staticmethod
    def _numeric_values(rows: list[dict[str, Any]], column: str) -> list[float]:
        values = []
        for row in rows:
            value = NaturalLanguageQueryService._to_float(row.get(column))
            if value is not None:
                values.append(value)
        return values

    @staticmethod
    def _mean(values: list[float]) -> Optional[float]:
        return sum(values) / len(values) if values else None

    @staticmethod
    def _std(values: list[float]) -> Optional[float]:
        if len(values) < 2:
            return None
        avg = sum(values) / len(values)
        return math.sqrt(sum((v - avg) ** 2 for v in values) / (len(values) - 1))

    @staticmethod
    def _percentile(values: list[float], q: float) -> float:
        if not values:
            return 0.0
        vals = sorted(values)
        pos = (len(vals) - 1) * max(0.0, min(q, 1.0))
        low = int(math.floor(pos))
        high = int(math.ceil(pos))
        if low == high:
            return vals[low]
        return vals[low] + (vals[high] - vals[low]) * (pos - low)

    @staticmethod
    def _lift_pct(current: Optional[float], base: Optional[float]) -> Optional[float]:
        if current is None or base in (None, 0):
            return None
        return (current - base) / abs(base) * 100

    @staticmethod
    def _cohen_d(left: list[float], right: list[float]) -> Optional[float]:
        if len(left) < 2 or len(right) < 2:
            return None
        left_std = NaturalLanguageQueryService._std(left) or 0.0
        right_std = NaturalLanguageQueryService._std(right) or 0.0
        pooled = math.sqrt((left_std ** 2 + right_std ** 2) / 2)
        if not pooled:
            return None
        return ((NaturalLanguageQueryService._mean(left) or 0.0) - (NaturalLanguageQueryService._mean(right) or 0.0)) / pooled

    @staticmethod
    def _pearson(xs: list[float], ys: list[float]) -> Optional[float]:
        if len(xs) != len(ys) or len(xs) < 3:
            return None
        mx = sum(xs) / len(xs)
        my = sum(ys) / len(ys)
        num = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
        den_x = math.sqrt(sum((x - mx) ** 2 for x in xs))
        den_y = math.sqrt(sum((y - my) ** 2 for y in ys))
        if not den_x or not den_y:
            return None
        return max(-1.0, min(1.0, num / (den_x * den_y)))

    @staticmethod
    def _correlation_strength(value: Optional[float]) -> str:
        if value is None:
            return "样本不足"
        abs_v = abs(value)
        if abs_v >= 0.6:
            return "关联紧密"
        if abs_v >= 0.35:
            return "有一定关联"
        if abs_v >= 0.15:
            return "关联较弱"
        return "基本无关"

    @staticmethod
    def _relationship_verdict(
        measure_name: str,
        lift: Optional[float],
        effect: Optional[float],
        corr: Optional[float],
        n: int,
        driver_label: str = "",
    ) -> str:
        label = driver_label or "该指标"
        if lift is None or n < 20:
            return "样本不足，结论仅供参考"
        effect_abs = abs(effect or 0.0)
        corr_abs = abs(corr or 0.0)
        if lift > 10 and (effect_abs >= 0.3 or corr_abs >= 0.25):
            return f"{label}较高的订单，{measure_name}明显更高"
        if lift < -10 and (effect_abs >= 0.3 or corr_abs >= 0.25):
            return f"{label}较高的订单，{measure_name}明显更低"
        if abs(lift) >= 5:
            return f"{label}较高的订单，{measure_name}略高，但差距不算大"
        return f"{label}较高的订单，{measure_name}和普通订单差不多"

    def _first_measure_for_table(self, table_name: str) -> MeasureMeta | None:
        for m in self._measures.values():
            if table_name in m.tables:
                return m
        return next(iter(self._measures.values()), None)

    def _entity_plan(
        self,
        candidate: dict[str, Any],
        measure: MeasureMeta | None,
        page_size: int,
        page_num: int,
    ) -> dict[str, Any]:
        measure = measure or self._first_measure_for_table(candidate["tableName"])
        if not measure:
            return {"ok": False, "intent": {}, "matched": {}, "diagnostics": {}}
        return {
            "ok": True,
            "intent": {
                "queryMode": "entity_lookup",
                "measureText": measure.cn_name,
                "dimensionTexts": [],
                "timeLevel": "",
                "filters": [{
                    "type": "entityValue",
                    "label": candidate["label"],
                    "value": "",
                }],
                "limit": page_size,
            },
            "matched": {
                "measureCode": measure.code,
                "measureName": measure.cn_name,
                "factTables": [candidate["tableName"]],
                "dimensionCodes": [],
                "dimensions": [],
            },
            "daPayload": None,
            "diagnostics": {},
        }

    def _entity_payload(self, candidate: dict[str, Any], value: str) -> dict[str, Any]:
        return {
            "fieldCode": candidate.get("columnName", ""),
            "fieldName": self._entity_display_label(candidate),
            "value": value,
            "factTable": candidate.get("tableName", ""),
            "schema": candidate.get("schema", ""),
        }

    def _schema_for_table(self, table_name: str) -> str:
        for m in self._measures.values():
            for app in self._measure_apps(m.code):
                tbl = app.get("table") or {}
                if tbl.get("tableName") == table_name:
                    return tbl.get("schema") or ""
        return ""

    def _db_connection_for_table(self, table_name: str, schema: str) -> dict[str, Any]:
        for tbl in self._graph.subjects(RDF.type, IND.DwTable):
            if self._str(self._graph.value(tbl, IND.tableName)) != table_name:
                continue
            conn = self._graph.value(tbl, IND.hasConnection)
            if conn:
                return {
                    "host": self._str(self._graph.value(conn, IND.host)) or "127.0.0.1",
                    "port": self._int(self._graph.value(conn, IND.port)) or 3306,
                    "user": self._str(self._graph.value(conn, IND.dbUser)) or "root",
                    "password": self._str(self._graph.value(conn, IND.dbPassword)) or "",
                    "database": (
                        self._str(self._graph.value(conn, IND.dbName))
                        or self._str(self._graph.value(tbl, IND.schemaName))
                        or schema
                        or "tpcds"
                    ),
                }
        return {
            "host": "127.0.0.1",
            "port": 3306,
            "user": "root",
            "password": "root",
            "database": schema or "tpcds",
        }

    @staticmethod
    def _sql_table(table_name: str, _schema: str = "") -> str:
        return f"`{table_name.replace('`', '')}`"

    def _jsonable_row(self, row: dict[str, Any]) -> dict[str, Any]:
        return {k: self._jsonable_value(v) for k, v in row.items()}

    @staticmethod
    def _jsonable_value(value: Any) -> Any:
        if isinstance(value, Decimal):
            return float(value)
        if isinstance(value, (datetime.date, datetime.datetime)):
            return value.isoformat()
        return value

    @staticmethod
    def _to_float(value: Any) -> float | None:
        try:
            if value in (None, ""):
                return None
            num = float(value)
            if math.isnan(num) or math.isinf(num):
                return None
            return num
        except Exception:
            return None

    def _load_if_needed(self) -> None:
        if not self.ttl_path.exists():
            raise FileNotFoundError(f"业务图谱不存在: {self.ttl_path}")
        mtime = self.ttl_path.stat().st_mtime
        if self._mtime == mtime:
            self._load_source_graph_if_needed()
            return

        g = Graph()
        g.parse(str(self.ttl_path), format="turtle")
        self._graph = g
        self._mtime = mtime
        self._measures = self._load_measures(g)
        self._dimensions = self._load_dimensions(g)
        self._columns_by_table = self._load_table_columns(g)
        self._table_labels_by_name = self._load_table_labels(g)
        self._log(
            f"[NLQ] 图谱加载完成: {len(self._measures)} 个指标, "
            f"{len(self._dimensions)} 个维度, "
            f"{sum(len(v) for v in self._columns_by_table.values())} 个字段"
        )
        self._load_source_graph_if_needed()

    def _load_source_graph_if_needed(self) -> None:
        if not self.source_ttl_path or not self.source_ttl_path.exists():
            return
        mtime = self.source_ttl_path.stat().st_mtime
        if self._source_mtime == mtime:
            return
        g = Graph()
        g.parse(str(self.source_ttl_path), format="turtle")
        self._source_graph = g
        self._source_mtime = mtime
        self._index_source_graph(g)
        self._log(
            f"[NLQ] 数据源图谱加载完成: "
            f"{sum(len(v) for v in self._source_table_columns.values())} 个源字段"
        )

    def _load_measures(self, g: Graph) -> dict[str, MeasureMeta]:
        measures: dict[str, MeasureMeta] = {}
        for node in g.subjects(RDF.type, IND.Measure):
            code = self._str(g.value(node, IND.code))
            if not code.startswith("MEAS_"):
                continue
            meta = MeasureMeta(
                code=code,
                cn_name=self._str(g.value(node, IND.cnName)) or code,
                en_name=self._str(g.value(node, IND.enName)),
                definition=self._str(g.value(node, IND.definition)),
                description=self._str(g.value(node, IND.description)),
            )
            for app in g.objects(node, IND.hasMeasureApp):
                tbl = g.value(app, IND.appliesToTable) or g.value(app, IND.measFactTable)
                table_name = self._str(g.value(tbl, IND.tableName)) if tbl else ""
                if table_name:
                    meta.tables.add(table_name)
            if not meta.tables:
                continue
            meta.search_text = self._build_search_text(
                meta.code, meta.cn_name, meta.en_name, meta.definition, meta.description
            )
            measures[code] = meta
        return measures

    def _load_dimensions(self, g: Graph) -> dict[str, DimensionMeta]:
        dims: dict[str, DimensionMeta] = {}
        for node in g.subjects(RDF.type, IND.Dimension):
            code = self._str(g.value(node, IND.code))
            if not code.startswith("DIM_"):
                continue
            meta = DimensionMeta(
                code=code,
                cn_name=self._str(g.value(node, IND.cnName)) or code,
                en_name=self._str(g.value(node, IND.enName)),
                definition=self._str(g.value(node, IND.definition)),
                description=self._str(g.value(node, IND.description)),
                view_type=self._int(g.value(node, IND.viewTypeCode)),
                hierarchy_code=self._str(g.value(node, IND.hierarchyCode)),
                level_code=self._str(g.value(node, IND.levelCode)),
            )
            for app in g.objects(node, IND.hasDimApp):
                fact_tbl = g.value(app, IND.dimFactTable)
                table_name = self._str(g.value(fact_tbl, IND.tableName)) if fact_tbl else ""
                if table_name:
                    meta.tables.add(table_name)
            if not meta.tables:
                continue
            meta.search_text = self._build_search_text(
                meta.code, meta.cn_name, meta.en_name, meta.definition,
                meta.description, meta.level_code
            )
            dims[code] = meta

        for hist in g.subjects(RDF.type, IND.DimHistogram):
            dim_code = self._str(g.value(hist, IND.histDimCode))
            table_name = self._str(g.value(hist, IND.histTableName))
            row_num = self._int(g.value(hist, IND.dimensionRowNum))
            if dim_code in dims and table_name and row_num is not None:
                dims[dim_code].row_nums[table_name] = row_num
        return dims

    def _load_table_columns(self, g: Graph) -> dict[str, list[dict[str, Any]]]:
        columns_by_table: dict[str, list[dict[str, Any]]] = {}
        for tbl in g.subjects(RDF.type, IND.DwTable):
            table_name = self._str(g.value(tbl, IND.tableName))
            if not table_name:
                continue
            schema_name = self._str(g.value(tbl, IND.schemaName))
            cols = []
            for idx, col in enumerate(g.objects(tbl, IND.hasColumn), 1):
                name = self._str(g.value(col, IND.columnName))
                if not name:
                    continue
                ordinal = self._int(g.value(col, IND.ordinalPosition))
                cols.append({
                    "code": name,
                    "name": self._str(g.value(col, IND.cnName)) or name,
                    "columnName": name,
                    "columnType": self._str(g.value(col, IND.columnType)),
                    "comment": self._str(g.value(col, IND.columnComment)),
                    "isPrimaryKey": self._bool(g.value(col, IND.isPrimaryKey)),
                    "isNullable": self._bool(g.value(col, IND.isNullable)),
                    "ordinalPosition": ordinal if ordinal is not None else idx,
                    "sampleValues": [str(v) for v in g.objects(col, IND.sampleValue)],
                    "tableName": table_name,
                    "schemaName": schema_name,
                    "uri": str(col),
                })
            if cols:
                columns_by_table[table_name] = sorted(
                    cols,
                    key=lambda c: (
                        c.get("ordinalPosition") if c.get("ordinalPosition") is not None else 10**9,
                        c.get("columnName") or "",
                    ),
                )
        return columns_by_table

    def _load_table_labels(self, g: Graph) -> dict[str, dict[str, str]]:
        labels: dict[str, dict[str, str]] = {}
        for tbl in g.subjects(RDF.type, IND.DwTable):
            table_name = self._str(g.value(tbl, IND.tableName))
            if not table_name:
                continue
            labels[table_name.lower()] = {
                "tableName": table_name,
                "cnName": self._str(g.value(tbl, IND.cnName)),
                "description": self._str(g.value(tbl, IND.description)),
            }
        return labels

    def _index_source_graph(self, g: Graph) -> None:
        by_name: dict[str, list[dict[str, Any]]] = {}
        by_table_col: dict[tuple[str, str], list[dict[str, Any]]] = {}
        table_cols: dict[str, list[dict[str, Any]]] = {}
        by_uri: dict[str, dict[str, Any]] = {}

        for col in g.subjects(RDF.type, DB.Column):
            parent = g.value(col, DB.belongsToTable)
            if parent is None:
                parent = next(g.subjects(DB.containsColumn, col), None)
            col_name = self._str(g.value(col, DB.name))
            if not col_name:
                continue
            table_name = self._str(g.value(parent, DB.tableName) or g.value(parent, DB.name)) if parent else ""
            schema_uri = g.value(parent, DB.belongsToSchema) if parent else None
            schema_name = self._str(g.value(schema_uri, DB.name)) if schema_uri else ""
            payload = {
                "uri": str(col),
                "columnName": col_name,
                "tableName": table_name,
                "schemaName": schema_name,
                "columnType": self._str(g.value(col, DB.columnType)),
                "comment": self._str(g.value(col, DB.comment)),
                "isPrimaryKey": self._bool(g.value(col, DB.isPrimaryKey)),
                "isNullable": self._bool(g.value(col, DB.isNullable)),
                "cardinality": self._int(g.value(col, DB.cardinality)),
                "nullRate": self._float(g.value(col, DB.nullRate)),
                "topValues": [str(v) for v in g.objects(col, DB.topValue)][:5],
                "patterns": [str(v) for v in g.objects(col, DB.detectedPattern)][:5],
            }
            by_name.setdefault(col_name.lower(), []).append(payload)
            by_uri[str(col)] = payload
            if table_name:
                by_table_col.setdefault((table_name.lower(), col_name.lower()), []).append(payload)
                table_cols.setdefault(table_name.lower(), []).append(payload)

        for table_name, cols in table_cols.items():
            table_cols[table_name] = sorted(cols, key=lambda c: c.get("columnName") or "")
        self._source_columns_by_name = by_name
        self._source_columns_by_table_col = by_table_col
        self._source_table_columns = table_cols
        self._source_column_by_uri = by_uri
        self._source_rows_by_table_col_value = self._index_source_individual_rows(g, by_uri)

    def _index_source_individual_rows(
        self,
        g: Graph,
        columns_by_uri: dict[str, dict[str, Any]],
    ) -> dict[tuple[str, str, str], list[dict[str, Any]]]:
        rows_by_value: dict[tuple[str, str, str], list[dict[str, Any]]] = {}
        for tbl in g.subjects(RDF.type, DB.Table):
            table_name = self._str(g.value(tbl, DB.tableName) or g.value(tbl, DB.name))
            if not table_name:
                continue
            for ind in g.objects(tbl, DB.hasIndividual):
                row = self._source_individual_row(ind, table_name)
                if not row.get("values"):
                    continue
                for col_name, value in row["values"].items():
                    if value in (None, ""):
                        continue
                    key = (table_name.lower(), col_name.lower(), str(value))
                    rows_by_value.setdefault(key, []).append(row)
        return rows_by_value

    def _source_individual_row(self, ind: Any, table_name: str) -> dict[str, Any]:
        values: dict[str, Any] = {}
        for pred, obj in self._source_graph.predicate_objects(ind):
            col_meta = self._source_column_by_uri.get(str(pred))
            if not col_meta:
                continue
            values[col_meta["columnName"]] = self._literal_value(obj)
        return {
            "uri": str(ind),
            "tableName": table_name,
            "label": self._str(self._source_graph.value(ind, RDFS.label)),
            "values": values,
        }

    def _plan(
        self,
        question: str,
        *,
        page_size: int,
        page_num: int,
        max_dimensions: int,
        query_mode: str,
        route_intent: Optional[dict[str, Any]] = None,
    ) -> dict[str, Any]:
        q_tokens = self._tokens(question)
        route_intent = route_intent or {}
        hinted_measure_code = str(route_intent.get("measureCode") or "").strip()
        hinted_measure = self._measures.get(hinted_measure_code)
        measure_hits = [(999.0, hinted_measure)] if hinted_measure else self._rank_measures(question, q_tokens)
        preferred_tables = {
            str(table) for table in (route_intent.get("preferredFactTables") or [])
            if table
        }
        if preferred_tables and measure_hits:
            compatible_hits = [
                hit for hit in measure_hits
                if hit[1].tables & preferred_tables
            ]
            if compatible_hits:
                measure_hits = compatible_hits
        inherited_measure_code = str(route_intent.get("inheritedMeasureCode") or "").strip()
        inherited_measure = self._measures.get(inherited_measure_code)
        if not measure_hits and inherited_measure and (
            not preferred_tables or inherited_measure.tables & preferred_tables
        ):
            measure_hits = [(50.0, inherited_measure)]
        if not measure_hits:
            return self._failed_plan(
                question,
                "没有在业务图谱中匹配到指标",
                {"measureCandidates": self._top_measure_examples()},
            )

        measure_score, measure = measure_hits[0]
        # 低置信度检测：所有指标得分都很低（< 30），说明问题中的业务概念在 KG 中没有对应
        LOW_CONFIDENCE_THRESHOLD = 30
        if measure_score < LOW_CONFIDENCE_THRESHOLD:
            # 尝试 LLM 兜底：将问题中的领域词映射到 KG 实际指标
            llm_mapped = self._llm_map_to_kg_measures(question, measure_hits[:10])
            if llm_mapped:
                measure = llm_mapped
                measure_score = 50.0  # LLM 映射的给中等置信度
            else:
                available = self._top_measure_examples()
                names = "、".join(m["name"] for m in available[:8])
                # 用前5个指标生成改写建议
                rewritten = self._build_rewrite_suggestions(question, available[:5])
                plan = self._failed_plan(
                    question,
                    f"当前业务图谱中没有匹配到「{question}」对应的指标。"
                    f"图谱中收录的指标包括：{names} 等。"
                    "以下是用已有指标改写的问题，可直接点击分析：",
                    {
                        "measureCandidates": available,
                        "suggestion": "可以尝试的指标名：" + names,
                    },
                )
                if rewritten:
                    plan["suggestedNextQuestions"] = rewritten
                return plan
        if self._is_ambiguous(measure_hits):
            return self._clarify_plan(
                question,
                "指标匹配不唯一，请明确要查哪个指标",
                {"measureCandidates": [self._measure_hit_payload(h) for h in measure_hits[:5]]},
            )

        compatible_dims = [
            d for d in self._dimensions.values() if d.tables & measure.tables
        ]
        time_dim = self._choose_time_dim(question, compatible_dims)
        time_filter = self._parse_time_filter(question)
        if not time_filter:
            inherited_filters = route_intent.get("inheritedFilters") or []
            if inherited_filters and isinstance(inherited_filters[0], dict):
                inherited = inherited_filters[0]
                if inherited.get("start") and inherited.get("end"):
                    time_filter = {
                        "label": str(inherited.get("label") or "继承上一轮时间范围"),
                        "start": str(inherited["start"]),
                        "end": str(inherited["end"]),
                    }
        filter_time_dim = (
            self._choose_filter_time_dim(compatible_dims, measure.tables)
            if time_filter else None
        )
        dimension_hits = self._rank_dimensions(question, q_tokens, compatible_dims)
        selected_dims = self._select_dimensions_from_intent(
            route_intent,
            compatible_dims,
            max_dimensions,
        )
        if not selected_dims:
            selected_dims = self._select_dimensions(
                question, dimension_hits, time_dim, max_dimensions
            )

        configure_list = [{"code": measure.code}]
        for dim in selected_dims:
            configure_list.append({
                "code": dim.code,
                "order": {"sortType": 1 if dim.is_time else 0},
                "alias": "",
                "hasSubtotal": False,
            })

        intent = {
            "queryMode": query_mode,
            "measureText": self._best_label_for_question(question, measure),
            "dimensionTexts": [d.cn_name for d in selected_dims],
            "timeLevel": time_dim.level_code if time_dim else "",
            "filters": [
                {
                    "type": "timeRange",
                    "label": time_filter["label"],
                    "start": time_filter["start"],
                    "end": time_filter["end"],
                    "dimensionCode": filter_time_dim.code if filter_time_dim else "",
                    "dimensionName": filter_time_dim.cn_name if filter_time_dim else "",
                }
            ] if time_filter else [],
            "limit": page_size,
        }
        matched = {
            "measureCode": measure.code,
            "measureName": measure.cn_name,
            "factTables": sorted(measure.tables),
            "dimensionCodes": [d.code for d in selected_dims],
            "dimensions": [
                {
                    "code": d.code,
                    "name": d.cn_name,
                    "level": d.level_code,
                    "isTime": d.is_time,
                    "tables": sorted(d.tables),
                }
                for d in selected_dims
            ],
        }
        diagnostics = {
            "measureScore": measure_score,
            "contextInherited": bool(route_intent.get("contextInherited")),
            "preferredFactTables": sorted(preferred_tables),
            "measureCandidates": [self._measure_hit_payload(h) for h in measure_hits[:5]],
            "dimensionCandidates": [self._dimension_hit_payload(h) for h in dimension_hits[:8]],
            "availableDimensions": [
                {
                    "code": d.code,
                    "name": d.cn_name,
                    "level": d.level_code,
                    "isTime": d.is_time,
                    "rowNum": self._min_row_num(d, measure.tables),
                }
                for d in self._sort_available_dims(compatible_dims, measure.tables)[:30]
            ],
        }
        filter_list = []
        if time_filter and filter_time_dim:
            filter_list.append(self._build_da_time_filter(filter_time_dim, time_filter))

        da_payload = {
            "configureList": configure_list,
            "filterList": filter_list,
            "pageSize": page_size,
            "pageNum": page_num,
        }
        if query_mode in {"detail", "analyze_detail"}:
            da_payload["measureDetail"] = True

        return {
            "ok": True,
            "intent": intent,
            "matched": matched,
            "daPayload": da_payload if query_mode != "explain" else None,
            "diagnostics": diagnostics,
        }

    def _rank_measures(self, question: str, tokens: list[str]) -> list[tuple[float, MeasureMeta]]:
        hits: list[tuple[float, MeasureMeta]] = []
        q_norm = self._norm(question)
        explicit_codes = {c.upper() for c in re.findall(r"MEAS_[A-Za-z0-9_]+", question, re.I)}
        for m in self._measures.values():
            score = 0.0
            code_l = m.code.lower()
            cn_norm = self._norm(m.cn_name)
            if m.code.upper() in explicit_codes:
                score += 200
            if cn_norm and cn_norm in q_norm:
                score += 80 + min(len(cn_norm), 12)
            if code_l in question.lower():
                score += 100
            score += self._token_score(tokens, m.search_text, weight=8)
            if score > 0:
                hits.append((score, m))
        return sorted(hits, key=lambda x: (-x[0], x[1].code))

    def _rank_dimensions(
        self,
        question: str,
        tokens: list[str],
        dims: list[DimensionMeta],
    ) -> list[tuple[float, DimensionMeta]]:
        hits: list[tuple[float, DimensionMeta]] = []
        q_norm = self._norm(question)
        explicit_codes = {c.upper() for c in re.findall(r"DIM_[A-Za-z0-9_]+", question, re.I)}
        dimension_context = bool(re.search(r"按|分组|分布|维度|各|每|group|by", question, re.I))
        for d in dims:
            score = 0.0
            cn_norm = self._norm(d.cn_name)
            if d.code.upper() in explicit_codes:
                score += 200
            if cn_norm and cn_norm in q_norm:
                score += 70 + min(len(cn_norm), 12)
            if d.code.lower() in question.lower():
                score += 100
            score += self._token_score(tokens, d.search_text, weight=7)
            if d.is_time:
                if d.level_code and self._wanted_time_level(question) == d.level_code:
                    score += 90
                elif not dimension_context:
                    score -= 20
            if score > 0:
                hits.append((score, d))
        return sorted(hits, key=lambda x: (-x[0], x[1].code))

    def _select_dimensions(
        self,
        question: str,
        hits: list[tuple[float, DimensionMeta]],
        time_dim: Optional[DimensionMeta],
        max_dimensions: int,
    ) -> list[DimensionMeta]:
        selected: list[DimensionMeta] = []
        seen: set[str] = set()

        if time_dim is not None and self._wanted_time_level(question):
            selected.append(time_dim)
            seen.add(time_dim.code)

        dimension_context = bool(re.search(r"按|分组|分布|维度|各|每|group|by", question, re.I))
        for score, dim in hits:
            if dim.code in seen:
                continue
            if dim.is_time and time_dim is not None:
                continue
            if any(
                (not existing.is_time)
                and self._norm(dim.cn_name)
                and self._norm(dim.cn_name) in self._norm(existing.cn_name)
                and self._norm(dim.cn_name) != self._norm(existing.cn_name)
                for existing in selected
            ):
                continue
            if not dimension_context and score < 60:
                continue
            if score < 18:
                continue
            selected.append(dim)
            seen.add(dim.code)
            if len(selected) >= max_dimensions:
                break
        return selected

    def _select_dimensions_from_intent(
        self,
        route_intent: dict[str, Any],
        compatible_dims: list[DimensionMeta],
        max_dimensions: int,
    ) -> list[DimensionMeta]:
        wanted = route_intent.get("dimensionCodes") or []
        if not isinstance(wanted, list):
            return []
        compatible_by_code = {d.code: d for d in compatible_dims}
        selected: list[DimensionMeta] = []
        seen: set[str] = set()
        for code in wanted:
            code = str(code or "").strip()
            dim = compatible_by_code.get(code)
            if not dim or code in seen:
                continue
            selected.append(dim)
            seen.add(code)
            if len(selected) >= max_dimensions:
                break
        return selected

    def _choose_time_dim(
        self,
        question: str,
        dims: list[DimensionMeta],
    ) -> Optional[DimensionMeta]:
        level = self._wanted_time_level(question)
        if not level:
            return None
        time_dims = [d for d in dims if d.is_time]
        exact = [d for d in time_dims if d.level_code == level]
        if exact:
            return exact[0]
        fallback_order = ["day", "week", "month", "quarter", "year"]
        for lvl in fallback_order:
            for d in time_dims:
                if d.level_code == lvl:
                    return d
        return time_dims[0] if time_dims else None

    def _choose_filter_time_dim(
        self,
        dims: list[DimensionMeta],
        measure_tables: set[str],
    ) -> Optional[DimensionMeta]:
        time_dims = [d for d in dims if d.is_time and d.tables & measure_tables]
        if not time_dims:
            return None
        for level in ("day", "month", "week", "quarter", "year"):
            for d in time_dims:
                if d.level_code == level:
                    return d
        return time_dims[0]

    def _parse_time_filter(self, question: str) -> dict[str, str]:
        q = question or ""
        today = datetime.date.today()

        m = re.search(r"(最近|近)\s*(\d+)\s*个?月", q)
        if m:
            months = max(1, min(int(m.group(2)), 60))
            start = self._add_months(today, -months)
            return {
                "label": f"最近{months}个月",
                "start": start.isoformat(),
                "end": today.isoformat(),
            }

        m = re.search(r"(最近|近)\s*(\d+)\s*(天|日)", q)
        if m:
            days = max(1, min(int(m.group(2)), 3660))
            start = today - datetime.timedelta(days=days)
            return {
                "label": f"最近{days}天",
                "start": start.isoformat(),
                "end": today.isoformat(),
            }

        if re.search(r"最近三个月|近三个月", q):
            start = self._add_months(today, -3)
            return {"label": "最近3个月", "start": start.isoformat(), "end": today.isoformat()}
        if re.search(r"最近一个月|近一个月", q):
            start = self._add_months(today, -1)
            return {"label": "最近1个月", "start": start.isoformat(), "end": today.isoformat()}
        if re.search(r"最近半年|近半年", q):
            start = self._add_months(today, -6)
            return {"label": "最近6个月", "start": start.isoformat(), "end": today.isoformat()}
        if re.search(r"最近一年|近一年", q):
            start = self._add_months(today, -12)
            return {"label": "最近12个月", "start": start.isoformat(), "end": today.isoformat()}

        m = re.search(r"(\d{4}-\d{2}-\d{2})\s*(?:至|到|-|~)\s*(\d{4}-\d{2}-\d{2})", q)
        if m:
            start, end = m.group(1), m.group(2)
            if start > end:
                start, end = end, start
            return {"label": f"{start}至{end}", "start": start, "end": end}

        return {}

    @staticmethod
    def _add_months(day: datetime.date, months: int) -> datetime.date:
        year = day.year + (day.month - 1 + months) // 12
        month = (day.month - 1 + months) % 12 + 1
        last = [
            31,
            29 if year % 4 == 0 and (year % 100 != 0 or year % 400 == 0) else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31,
        ][month - 1]
        return datetime.date(year, month, min(day.day, last))

    @staticmethod
    def _build_da_time_filter(dim: DimensionMeta, time_filter: dict[str, str]) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "code": dim.code,
            "operatorList": [{
                "sqlOprType": 2,
                "dataList": [time_filter["start"], time_filter["end"]],
                "timeRange": 1,
            }],
            "internal": True,
        }
        if dim.level_code == "week":
            payload["viewType"] = 2
        return payload

    def _resolve_query_mode(self, question: str, query_mode: str) -> str:
        requested = (query_mode or "auto").strip().lower()
        aliases = {
            "stat": "aggregate",
            "stats": "aggregate",
            "agg": "aggregate",
            "明细": "detail",
            "解释": "explain",
            "analysis": "analyze_detail",
            "detail_analysis": "analyze_detail",
        }
        requested = aliases.get(requested, requested)
        if requested in {"aggregate", "detail", "explain", "analyze_detail"}:
            return requested

        q = question or ""
        # 实体指代检测：对具体实例的追问（该订单/此仓库/同类订单）应走 entity_lookup 而非聚合
        has_entity_ref = bool(re.search(
            r"该[订单笔个项条只次件张批种座家名位辆台部本套组类科属种块片根颗粒层段行列册份处所期届场届次回趟度]|"
            r"这个|此[订单笔个次项]|某个|哪一个|"
            r"哪[一个笔条次件张]|什么订单|什么促销|"
            r"同类|同组|同批|同种|类似|相似|相近",
            q
        ))
        if has_entity_ref:
            return "entity_lookup"

        has_detail = bool(re.search(r"明细|详情|列表|记录|样本|原始|订单列表|行级|下钻", q))
        has_analysis = bool(re.search(r"分析|二次分析|分析明细|从明细|字段分析|样本分析|异常|特征|分布|解释|说明|还能按哪些|推荐", q))
        has_graph = bool(re.search(r"图谱|关系|血缘|来自|来源|为什么|能不能|有哪些维度|按哪些维度|能按哪些|可用维度|解释|说明|join|关联", q, re.I))

        if has_detail:
            return "analyze_detail"
        if has_graph:
            return "explain"
        return "aggregate"

    def _resolve_query_mode_with_llm(self, question: str) -> str:
        try:
            from kg_builder.utils.llm_config import llm_config_from_env

            cfg = llm_config_from_env(Path.cwd())
            api_key = (cfg.get("api_key") or "").strip()
            base_url = (cfg.get("base_url") or "").strip().rstrip("/")
            model = (cfg.get("model") or "").strip()
            if not api_key or not base_url or not model:
                return ""

            measure_examples = [
                {"code": m.code, "name": m.cn_name, "definition": m.definition}
                for m in list(self._measures.values())[:20]
            ]
            dimension_examples = [
                {"code": d.code, "name": d.cn_name, "isTime": d.is_time}
                for d in list(self._dimensions.values())[:30]
            ]
            system = (
                "你是 AD 智能问数的意图路由器，只返回 JSON。"
                "mode 只能是 aggregate、detail、analyze_detail、explain。"
                "aggregate=指标汇总、趋势、按维度分组查询；"
                "detail=只要原始明细列表；"
                "analyze_detail=用户要求基于明细继续分析、解释差异、异常、特征、原因，"
                "需要查询明细并用源表图谱扩展关联维表后做业务分析；"
                "explain=只问指标/维度/图谱关系、可用维度、来源口径，不执行 DA 数据分析。"
                "不要返回 Markdown。"
            )
            user = json.dumps({
                "question": question,
                "availableMeasures": measure_examples,
                "availableDimensions": dimension_examples,
                "outputSchema": {"mode": "aggregate|detail|analyze_detail|explain", "reason": "简短原因"},
            }, ensure_ascii=False)
            payload = {
                "model": model,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": user},
                ],
                "temperature": 0,
                "max_tokens": 120,
            }
            req = urllib.request.Request(
                f"{base_url}/chat/completions",
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {api_key}",
                },
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            content = (
                data.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "")
                .strip()
            )
            if content.startswith("```"):
                content = re.sub(r"^```(?:json)?\s*|\s*```$", "", content, flags=re.S).strip()
            parsed = json.loads(content)
            mode = str(parsed.get("mode") or "").strip()
            return mode if mode in {"aggregate", "detail", "analyze_detail", "explain"} else ""
        except Exception as exc:
            self._log(f"[NLQ] LLM 路由不可用，使用规则兜底: {exc}")
            return ""

    def _execute_da(self, payload: dict[str, Any]) -> dict[str, Any]:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(
            self.data_agent_url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read().decode("utf-8")
            data = json.loads(raw)
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")[:800]
            return {"ok": False, "error": f"DA HTTP {e.code}: {raw}"}
        except Exception as e:
            return {"ok": False, "error": str(e)}

        ok = data.get("code") == 200
        result = {
            "ok": ok,
            "code": data.get("code"),
            "message": data.get("msg") or data.get("message") or "",
            "data": data.get("data"),
            "raw": data,
        }
        if not ok:
            result["error"] = (
                data.get("errorMessage") or data.get("message") or
                data.get("msg") or json.dumps(data, ensure_ascii=False)[:800]
            )
        return result

    def _normalize_detail_result(self, da_result: dict[str, Any]) -> dict[str, Any]:
        data = da_result.get("data") if isinstance(da_result, dict) else {}
        data = data if isinstance(data, dict) else {}
        rows = data.get("cellList") or data.get("list") or []
        if not isinstance(rows, list):
            rows = []

        columns: list[dict[str, Any]] = []
        records: list[dict[str, Any]] = []
        header_included = False

        if rows and isinstance(rows[0], list):
            first_row = rows[0]
            header_included = all(
                isinstance(c, dict)
                and str(c.get("data", "")) == str(c.get("code", c.get("data", "")))
                for c in first_row
            )
            if header_included:
                for idx, cell in enumerate(first_row):
                    code = str(cell.get("code") or cell.get("data") or f"col_{idx}")
                    columns.append({
                        "code": code,
                        "name": cell.get("name") or code,
                        "type": cell.get("type"),
                    })
                data_rows = rows[1:]
            else:
                max_len = max((len(r) for r in rows if isinstance(r, list)), default=0)
                columns = [{"code": f"col_{i}", "name": f"col_{i}"} for i in range(max_len)]
                data_rows = rows

            for row in data_rows:
                if not isinstance(row, list):
                    continue
                rec: dict[str, Any] = {}
                for idx, cell in enumerate(row):
                    if not isinstance(cell, dict):
                        continue
                    col = columns[idx]["code"] if idx < len(columns) else f"col_{idx}"
                    rec[col] = cell.get("data")
                records.append(rec)
        elif rows and isinstance(rows[0], dict):
            columns = [{"code": k, "name": k} for k in rows[0].keys()]
            records = rows

        return {
            "columns": columns,
            "records": records,
            "rowCount": len(records),
            "rawRowCount": len(rows),
            "headerIncluded": header_included,
            "reviewSql": data.get("reviewSql") or "",
        }

    def _build_graph_context(
        self,
        plan: dict[str, Any],
        detail_data: Optional[dict[str, Any]] = None,
    ) -> dict[str, Any]:
        matched = plan.get("matched") or {}
        measure_code = matched.get("measureCode") or ""
        measure = self._measures.get(measure_code)
        if not measure:
            return {}

        compatible_dims = [
            d for d in self._dimensions.values() if d.tables & measure.tables
        ]
        selected_dim_codes = set(matched.get("dimensionCodes") or [])
        measure_apps = self._measure_apps(measure_code)
        drill_dims = []
        join_paths = []
        for dim in self._sort_available_dims(compatible_dims, measure.tables):
            dim_apps = self._dimension_apps(dim.code, measure.tables)
            dim_payload = {
                "code": dim.code,
                "name": dim.cn_name,
                "level": dim.level_code,
                "hierarchy": dim.hierarchy_code,
                "isTime": dim.is_time,
                "selected": dim.code in selected_dim_codes,
                "tables": sorted(dim.tables),
                "apps": dim_apps,
            }
            drill_dims.append(dim_payload)
            for app in dim_apps:
                join_paths.append({
                    "dimensionCode": dim.code,
                    "dimensionName": dim.cn_name,
                    "factTable": app.get("factTable"),
                    "factColumn": app.get("dimFactColumn"),
                    "dimTable": app.get("dimTable"),
                    "dimPrimaryKey": app.get("dimPrimaryKey"),
                    "dimColumn": app.get("dimColumn"),
                    "description": self._join_description(dim.cn_name, app),
                })

        ttl_columns = self._columns_for_tables(measure.tables)
        raw_detail_columns = detail_data.get("columns", []) if detail_data else []
        detail_columns = self._annotate_detail_columns(
            raw_detail_columns or ttl_columns,
            measure_apps,
            drill_dims,
        )
        return {
            "measure": {
                "code": measure.code,
                "name": measure.cn_name,
                "enName": measure.en_name,
                "definition": measure.definition,
                "description": measure.description,
                "factTables": sorted(measure.tables),
                "apps": measure_apps,
            },
            "availableDrillDimensions": drill_dims,
            "joinPaths": join_paths,
            "selectedJoinPaths": [
                jp for jp in join_paths if jp["dimensionCode"] in selected_dim_codes
            ],
            "tableColumns": ttl_columns,
            "detailColumns": detail_columns,
            "metadataGaps": [
                "业务 TTL 当前没有 DwColumn/hasColumn 明细字段字典，字段清单来自 DA 明细结果。"
            ] if detail_data and raw_detail_columns and not ttl_columns else [],
        }

    def _measure_apps(self, measure_code: str) -> list[dict[str, Any]]:
        g = self._graph
        node = self._node_by_code(measure_code)
        if node is None:
            return []
        apps = []
        for app in g.objects(node, IND.hasMeasureApp):
            tbl = g.value(app, IND.appliesToTable) or g.value(app, IND.measFactTable)
            apps.append({
                "uri": str(app),
                "applyTypeCode": self._int(g.value(app, IND.applyTypeCode)),
                "expression": self._str(g.value(app, IND.expression)),
                "factColumn": self._str(g.value(app, IND.factColumn)),
                "whereCondition": self._str(g.value(app, IND.whereCondition)),
                "hasColumnDT": self._str(g.value(app, IND.hasColumnDT)),
                "table": self._table_payload(tbl),
            })
        return apps

    def _dimension_apps(
        self,
        dim_code: str,
        measure_tables: Optional[set[str]] = None,
    ) -> list[dict[str, Any]]:
        g = self._graph
        node = self._node_by_code(dim_code)
        if node is None:
            return []
        apps = []
        for app in g.objects(node, IND.hasDimApp):
            fact_tbl = g.value(app, IND.dimFactTable)
            dim_tbl = g.value(app, IND.dimTable)
            fact_table_name = self._str(g.value(fact_tbl, IND.tableName)) if fact_tbl else ""
            if measure_tables and fact_table_name not in measure_tables:
                continue
            apps.append({
                "uri": str(app),
                "factTable": fact_table_name,
                "factSchema": self._str(g.value(fact_tbl, IND.schemaName)) if fact_tbl else "",
                "dimFactColumn": self._str(g.value(app, IND.dimFactColumn)),
                "dimTable": self._str(g.value(dim_tbl, IND.tableName)) if dim_tbl else "",
                "dimSchema": self._str(g.value(dim_tbl, IND.schemaName)) if dim_tbl else "",
                "dimPrimaryKey": self._str(g.value(app, IND.dimPrimaryKey)),
                "dimColumn": self._str(g.value(app, IND.dimColumn)),
                "masterPrimaryKey": self._str(g.value(app, IND.masterPrimaryKey)),
                "whereCondition": self._str(g.value(app, IND.whereCondition)),
                "isRootJoin": self._str(g.value(app, IND.isRootJoin)),
            })
        return apps

    def _table_payload(self, tbl: Any) -> dict[str, Any]:
        if tbl is None:
            return {}
        g = self._graph
        return {
            "uri": str(tbl),
            "schema": self._str(g.value(tbl, IND.schemaName)),
            "tableName": self._str(g.value(tbl, IND.tableName)),
            "sourceTypeCode": self._int(g.value(tbl, IND.sourceTypeCode)),
            "columns": self._columns_by_table.get(self._str(g.value(tbl, IND.tableName)), []),
        }

    def _columns_for_tables(self, table_names: set[str]) -> list[dict[str, Any]]:
        cols = []
        for table_name in sorted(table_names):
            cols.extend(self._columns_by_table.get(table_name, []))
        return cols

    def _annotate_detail_columns(
        self,
        columns: list[dict[str, Any]],
        measure_apps: list[dict[str, Any]],
        drill_dims: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        if not columns:
            return []
        fact_columns = {
            app.get("factColumn"): app
            for app in measure_apps if app.get("factColumn")
        }
        dim_fact_columns: dict[str, list[dict[str, Any]]] = {}
        dim_value_columns: dict[str, list[dict[str, Any]]] = {}
        dim_key_columns: dict[str, list[dict[str, Any]]] = {}
        for dim in drill_dims:
            for app in dim.get("apps", []):
                info = {"dimensionCode": dim["code"], "dimensionName": dim["name"], **app}
                if app.get("dimFactColumn"):
                    dim_fact_columns.setdefault(app["dimFactColumn"], []).append(info)
                if app.get("dimColumn"):
                    dim_value_columns.setdefault(app["dimColumn"], []).append(info)
                for key in ("dimPrimaryKey", "masterPrimaryKey"):
                    if app.get(key):
                        dim_key_columns.setdefault(app[key], []).append(info)

        annotated = []
        for col in columns:
            code = str(col.get("code") or "")
            roles = []
            if code in fact_columns:
                roles.append({"role": "measureFactColumn", "measureApp": fact_columns[code]})
            for info in dim_fact_columns.get(code, []):
                roles.append({"role": "dimensionFactKey", **info})
            for info in dim_value_columns.get(code, []):
                roles.append({"role": "dimensionValueColumn", **info})
            for info in dim_key_columns.get(code, []):
                roles.append({"role": "dimensionKeyColumn", **info})
            annotated.append({**col, "roles": roles})
        return annotated

    def _apply_detail_display_labels(
        self,
        detail_data: dict[str, Any],
        graph_context: dict[str, Any],
        plan: dict[str, Any],
    ) -> None:
        columns = detail_data.get("columns") or []
        if not isinstance(columns, list):
            return
        detail_meta = {
            str(c.get("code") or ""): c
            for c in (graph_context.get("detailColumns") or [])
            if c.get("code")
        }
        translated = self._translate_missing_column_labels([
            str(c.get("code") or "")
            for c in columns
            if isinstance(c, dict) and c.get("code")
        ])
        used: dict[str, int] = {}
        for idx, col in enumerate(columns, start=1):
            if not isinstance(col, dict):
                continue
            code = str(col.get("code") or "")
            meta = detail_meta.get(code, {})
            label = self._detail_column_display_label(code, meta, plan, idx)
            if not label:
                label = translated.get(code) or f"字段{idx}"
            count = used.get(label, 0) + 1
            used[label] = count
            display_name = f"{label}{count}" if count > 1 else label
            col["originalName"] = col.get("name")
            col["displayName"] = display_name
            col["name"] = display_name

    def _detail_column_display_label(
        self,
        code: str,
        meta: dict[str, Any],
        plan: dict[str, Any],
        idx: int,
    ) -> str:
        matched = (plan or {}).get("matched") or {}
        measure_name = matched.get("measureName") or "指标值"
        for role in meta.get("roles") or []:
            if role.get("role") == "measureFactColumn":
                return measure_name
            dim_name = self._clean_business_label(role.get("dimensionName"))
            if dim_name:
                if role.get("role") == "dimensionValueColumn":
                    return dim_name
                return f"{dim_name}关联键"
        label = self._business_column_label(code)
        return label if label and label != "字段" else ""

    def _translate_missing_column_labels(self, columns: list[str]) -> dict[str, str]:
        missing = []
        hints: dict[str, str] = {}
        for col in columns:
            if not col or col in self._dynamic_column_labels:
                continue
            table_name, raw = self._split_qualified_column(col)
            graph_label = self._graph_column_label_from_ttl(table_name, raw)
            if graph_label:
                self._dynamic_column_labels[col] = graph_label
                continue
            name = raw or col
            missing.append(name)
            source_col = self._source_column_meta(table_name, raw)
            hint_parts = []
            if table_name:
                hint_parts.append(f"所属表: {table_name}")
            if source_col.get("comment"):
                hint_parts.append(f"字段注释: {source_col.get('comment')}")
            if source_col.get("columnType"):
                hint_parts.append(f"字段类型: {source_col.get('columnType')}")
            if hint_parts:
                hints[name] = "；".join(hint_parts)

        translations: dict[str, str] = {}
        unique_missing = sorted(set(missing))
        if unique_missing:
            try:
                from kg_builder.utils.translator import LLMTranslator
                translations = LLMTranslator().translate(unique_missing, hints=hints)
            except Exception as exc:
                self._log(f"[NLQ] 字段名 LLM 翻译不可用，使用字段序号兜底: {exc}")

        result: dict[str, str] = {}
        ordinal = 0
        for col in columns:
            if not col:
                continue
            if col in self._dynamic_column_labels:
                result[col] = self._dynamic_column_labels[col]
                continue
            _table_name, raw = self._split_qualified_column(col)
            zh = self._clean_business_label(translations.get(raw or col))
            if not zh:
                ordinal += 1
                zh = f"字段{ordinal}"
            self._dynamic_column_labels[col] = zh
            result[col] = zh
        return result

    def _graph_column_label_from_ttl(self, table_name: str, column_name: str) -> str:
        table_label = self._table_business_label(table_name)
        business_col = self._business_column_meta(table_name, column_name)
        if business_col:
            for key in ("name", "comment"):
                label = self._clean_business_label(business_col.get(key))
                if label:
                    return self._join_table_column_label(table_label, label)
        source_col = self._source_column_meta(table_name, column_name)
        if source_col:
            label = self._clean_business_label(source_col.get("comment"))
            if label:
                return self._join_table_column_label(table_label, label)
        return ""

    def _analyze_detail_data(
        self,
        plan: dict[str, Any],
        graph_context: dict[str, Any],
        detail_data: dict[str, Any],
    ) -> dict[str, Any]:
        records = detail_data.get("records") or []
        columns = detail_data.get("columns") or []
        if not records:
            return {
                "summary": "DA 明细查询没有返回记录，无法做样本层面的二次分析。",
                "rowCount": 0,
                "columnCount": len(columns),
                "suggestedNextQuestions": self._suggest_next_questions(plan, graph_context),
            }

        col_codes = [c.get("code") for c in columns if c.get("code")]
        null_stats = []
        cardinality = []
        numeric_stats = []
        for col in col_codes:
            values = [r.get(col) for r in records]
            null_count = sum(1 for v in values if v in (None, "", "-", "null", "None"))
            non_null = [v for v in values if v not in (None, "", "-", "null", "None")]
            distinct = sorted({str(v) for v in non_null})[:10]
            null_stats.append({"column": col, "nullCount": null_count})
            cardinality.append({
                "column": col,
                "distinctCount": len({str(v) for v in non_null}),
                "sampleValues": distinct[:5],
            })
            nums = []
            for v in non_null:
                try:
                    nums.append(float(v))
                except Exception:
                    pass
            if nums and len(nums) >= max(2, len(non_null) // 2):
                numeric_stats.append({
                    "column": col,
                    "min": min(nums),
                    "max": max(nums),
                    "avg": round(sum(nums) / len(nums), 4),
                })

        graph_expansion = self._build_graph_expansion(
            records=records,
            columns=columns,
            graph_context=graph_context,
        )
        integrated = self._integrate_detail_with_related_rows(
            records=records,
            graph_expansion=graph_expansion,
        )
        integrated["columnLabels"] = self._display_labels_for_integrated_rows(
            integrated.get("rows", []),
            columns,
        )
        low_cardinality = sorted(cardinality, key=lambda x: x["distinctCount"])[:10]
        summary = (
            f"本次明细样本返回 {len(records)} 行、{len(columns)} 个字段；"
            f"已将可命中的关联表数据合并为 {len(integrated.get('rows', []))} 行增强明细。"
        )
        return {
            "summary": summary,
            "rowCount": len(records),
            "columnCount": len(columns),
            "integratedDetailData": integrated,
            "integratedAnalysis": self._analyze_integrated_rows(
                integrated.get("rows", []),
                plan=plan,
                graph_context=graph_context,
            ),
            "lowCardinalityColumns": low_cardinality,
            "numericColumns": numeric_stats[:15],
            "nullColumns": [x for x in null_stats if x["nullCount"] > 0][:15],
            "suggestedNextQuestions": self._suggest_next_questions(plan, graph_context),
        }

    def _build_graph_expansion(
        self,
        *,
        records: list[dict[str, Any]],
        columns: list[dict[str, Any]],
        graph_context: dict[str, Any],
    ) -> dict[str, Any]:
        if not self._source_columns_by_name:
            return {
                "hop": 1,
                "available": False,
                "reason": "未配置或未加载数据源图谱 TTL，无法进行列级 1 跳扩展。",
                "columns": [],
                "rollup": {},
            }

        fact_tables = {
            str(t)
            for t in (graph_context.get("measure", {}) or {}).get("factTables", [])
            if t
        }
        column_codes = [str(c.get("code") or "") for c in columns if c.get("code")]
        expansions = []
        for col in column_codes:
            matches = self._match_source_columns(col, fact_tables)
            if not matches:
                continue
            sample_values = self._sample_values(records, col)
            candidate_expansions = [
                self._expand_source_column(match, sample_values)
                for match in matches[:2]
            ]
            expansions.append({
                "column": col,
                "sampleValues": sample_values,
                "matches": candidate_expansions,
                "relationCount": sum(len(m.get("relations", [])) for m in candidate_expansions),
            })

        rollup = self._rollup_graph_expansion(expansions)
        return {
            "hop": 1,
            "available": True,
            "sourceGraph": str(self.source_ttl_path) if self.source_ttl_path else "",
            "columns": expansions,
            "rollup": rollup,
        }

    def _integrate_detail_with_related_rows(
        self,
        *,
        records: list[dict[str, Any]],
        graph_expansion: dict[str, Any],
    ) -> dict[str, Any]:
        rows = []
        join_attempts = 0
        join_hits = 0
        joined_tables: dict[str, int] = {}

        expansion_by_column = {
            item.get("column"): item
            for item in graph_expansion.get("columns", [])
            if item.get("column")
        }

        for idx, record in enumerate(records):
            enriched = dict(record)
            related_payload: dict[str, Any] = {}
            for col_name, expansion in expansion_by_column.items():
                value = record.get(col_name)
                if value in (None, "", "-", "null", "None"):
                    continue
                for match in expansion.get("matches", []):
                    for rel in match.get("relations", []):
                        if rel.get("type") not in {"referencesTable", "potentialForeignKey"}:
                            continue
                        target = rel.get("target") or {}
                        if target.get("nodeType") != "table":
                            continue
                        table_name = target.get("tableName") or ""
                        if not table_name:
                            continue
                        join_attempts += 1
                        related_row = self._find_related_source_row(table_name, str(value), target)
                        if not related_row:
                            continue
                        join_hits += 1
                        joined_tables[table_name] = joined_tables.get(table_name, 0) + 1
                        values = related_row.get("values") or {}
                        related_payload[table_name] = values
                        for r_col, r_val in values.items():
                            out_key = f"{table_name}.{r_col}"
                            if out_key not in enriched:
                                enriched[out_key] = r_val
            rows.append({
                "rowIndex": idx,
                "data": enriched,
                "related": related_payload,
            })

        return {
            "rows": rows,
            "coverage": {
                "baseRowCount": len(records),
                "joinAttempts": join_attempts,
                "joinHits": join_hits,
                "joinedTables": joined_tables,
                "hitRate": round(join_hits / join_attempts, 4) if join_attempts else 0.0,
            },
        }

    def _display_labels_for_integrated_rows(
        self,
        rows: list[dict[str, Any]],
        base_columns: list[dict[str, Any]],
    ) -> dict[str, str]:
        labels = {
            str(c.get("code") or ""): str(c.get("displayName") or "")
            for c in base_columns
            if c.get("code") and c.get("displayName")
        }
        data_rows = [r.get("data") or {} for r in rows]
        keys = sorted({k for row in data_rows for k in row.keys()})
        translated = self._translate_missing_column_labels(keys)
        field_count = 0
        for key in keys:
            if labels.get(key):
                continue
            label = translated.get(key) or self._business_column_label(key)
            if not label or label == "字段":
                field_count += 1
                label = f"字段{field_count}"
            labels[key] = label
        return labels

    def _find_related_source_row(
        self,
        table_name: str,
        value: str,
        table_payload: dict[str, Any],
    ) -> Optional[dict[str, Any]]:
        candidate_columns = [
            c.get("columnName")
            for c in table_payload.get("coreColumns", [])
            if c.get("isPrimaryKey") and c.get("columnName")
        ]
        if not candidate_columns:
            candidate_columns = [
                c.get("columnName")
                for c in table_payload.get("coreColumns", [])
                if c.get("columnName") and self._looks_like_key_column(c.get("columnName") or "")
            ]
        if not candidate_columns:
            candidate_columns = [
                c.get("columnName")
                for c in table_payload.get("coreColumns", [])[:3]
                if c.get("columnName")
            ]
        for col_name in candidate_columns:
            rows = self._source_rows_by_table_col_value.get(
                (table_name.lower(), str(col_name).lower(), value)
            )
            if rows:
                return rows[0]
        return None

    @staticmethod
    def _looks_like_key_column(name: str) -> bool:
        low = name.lower()
        return low in {"id", "key"} or low.endswith("_id") or low.endswith("_key") or low.endswith("_sk")

    def _analyze_integrated_rows(
        self,
        integrated_rows: list[dict[str, Any]],
        *,
        plan: Optional[dict[str, Any]] = None,
        graph_context: Optional[dict[str, Any]] = None,
    ) -> dict[str, Any]:
        flat_rows = [r.get("data") or {} for r in integrated_rows]
        if not flat_rows:
            return {
                "summary": "没有可分析的增强明细行。",
                "columnCount": 0,
                "keyFindings": [],
            }

        columns = sorted({k for row in flat_rows for k in row.keys()})
        self._translate_missing_column_labels(columns)
        measure_name = ((plan or {}).get("matched") or {}).get("measureName") or "目标指标"
        measure_column = self._choose_business_measure_column(
            flat_rows,
            graph_context or {},
            measure_name,
        )
        profiles = []
        for col in columns:
            values = [row.get(col) for row in flat_rows]
            non_null = [v for v in values if v not in (None, "", "-", "null", "None")]
            distinct_values = sorted({str(v) for v in non_null})
            nums = []
            for value in non_null:
                try:
                    nums.append(float(value))
                except Exception:
                    pass
            profile = {
                "column": col,
                "nonNullCount": len(non_null),
                "distinctCount": len(distinct_values),
                "sampleValues": distinct_values[:5],
            }
            if nums and len(nums) >= max(2, len(non_null) // 2):
                profile["numeric"] = {
                    "min": min(nums),
                    "max": max(nums),
                    "avg": round(sum(nums) / len(nums), 4),
                }
            profiles.append(profile)

        business = self._build_business_integrated_analysis(
            flat_rows,
            columns,
            profiles,
            measure_name=measure_name,
            measure_column=measure_column,
        )
        low_cardinality = sorted(
            [p for p in profiles if p["nonNullCount"] > 0],
            key=lambda p: (p["distinctCount"], p["column"]),
        )[:12]
        numeric = [p for p in profiles if "numeric" in p][:12]
        joined_columns = [c for c in columns if "." in c]

        return {
            "summary": business["summary"],
            "riskLevel": business.get("riskLevel", "normal"),
            "columnCount": len(columns),
            "joinedColumnCount": len(joined_columns),
            "measureColumn": measure_column,
            "measureName": measure_name,
            "businessFindings": business["businessFindings"],
            "driverBreakdowns": business["driverBreakdowns"],
            "representativeRows": business["representativeRows"],
            "opportunities": business["opportunities"],
            "risks": business["risks"],
            "suggestedNextQuestions": business["suggestedNextQuestions"],
            "dataBasis": business["dataBasis"],
            "lowCardinalityColumns": low_cardinality,
            "numericColumns": numeric,
            "keyFindings": business["businessFindings"],
        }

    def _build_business_integrated_analysis(
        self,
        rows: list[dict[str, Any]],
        columns: list[str],
        profiles: list[dict[str, Any]],
        *,
        measure_name: str,
        measure_column: str,
    ) -> dict[str, Any]:
        row_count = len(rows)
        measure_values = [self._to_float(row.get(measure_column)) for row in rows]
        measure_values = [v for v in measure_values if v is not None]
        total = sum(measure_values) if measure_values else 0.0
        avg = total / len(measure_values) if measure_values else 0.0
        min_v = min(measure_values) if measure_values else 0.0
        max_v = max(measure_values) if measure_values else 0.0

        amount_label = measure_name
        related_labels = self._related_table_labels(columns)
        related_text = (
            "、".join(related_labels[:6]) + "等关联信息"
            if related_labels else "源表图谱可关联信息"
        )

        driver_columns = self._select_business_driver_columns(profiles, columns, row_count)
        breakdowns = [
            self._breakdown_by_column(rows, col, measure_column)
            for col in driver_columns
        ]
        breakdowns = [b for b in breakdowns if b.get("groups")]

        findings = []
        if breakdowns:
            first = breakdowns[0]
            top = first["groups"][0]
            top_value = top.get("displayValue") or self._display_data_value(top.get("value"), 1)
            top_share = top.get("sharePct", 0)
            direction = "集中" if top_share > 50 else ("偏高" if top_share > 30 else "")
            findings.append(
                f"从{first['label']}看，{top_value}贡献了 {top_share}% 的{measure_name}"
                + (f"，占比明显{direction}，可能存在依赖单一维度的风险" if direction else "")
                + "。"
            )
        profit_column = self._find_semantic_column(
            profiles,
            include=("利润", "profit"),
            exclude=(measure_column,),
        )
        discount_column = self._find_semantic_column(
            profiles,
            include=("折扣", "discount"),
            exclude=(measure_column,),
        )
        coupon_column = self._find_semantic_column(
            profiles,
            include=("优惠券", "coupon"),
            exclude=(measure_column,),
        )
        quantity_column = self._find_semantic_column(
            profiles,
            include=("数量", "quantity", "qty"),
            exclude=(measure_column,),
        )

        profit = self._sum_column(rows, profit_column) if profit_column else None
        if profit is not None and total:
            margin = round(profit / total * 100, 1)
            margin_label = "严重偏低" if margin < 5 else ("偏低" if margin < 10 else ("健康" if margin > 20 else "正常"))
            findings.append(
                f"样本净利润合计 {self._fmt_number(profit)}，净利率 {margin}%（{margin_label}）。"
            )
        discount = self._sum_column(rows, discount_column) if discount_column else None
        coupon = self._sum_column(rows, coupon_column) if coupon_column else None
        if discount is not None and total:
            discount_rate = round(discount / total * 100, 1)
            discount_note = "折扣占比偏高，可能侵蚀利润" if discount_rate > 20 else ""
            findings.append(
                f"折扣合计 {self._fmt_number(discount)}，占{amount_label}的 {discount_rate}%。"
                + (f" {discount_note}" if discount_note else "")
            )
        if coupon is not None and total and coupon > 0:
            findings.append(
                f"优惠券金额合计 {self._fmt_number(coupon)}，约为{amount_label}的 {round(coupon / total * 100, 1)}%。"
            )
        quantity = self._sum_column(rows, quantity_column) if quantity_column else None
        if quantity is not None and quantity:
            findings.append(
                f"样本销售数量合计 {self._fmt_number(quantity)}，按数量折算的平均销售额为 {self._fmt_number(total / quantity)}。"
            )

        representatives = self._representative_rows(rows, measure_column, driver_columns)
        opportunities = self._business_opportunities(breakdowns, profit, total, discount, coupon)
        risks = self._business_risks(rows, measure_column, profit_column, profit, total)
        suggested = self._business_next_questions(measure_name, breakdowns)
        data_basis = [
            f"以 {row_count} 条明细样本为基础。",
            f"核心分析指标采用 {measure_name}（{amount_label}）。",
            "分析中已把源表图谱可关联到的维表/源表信息并入同一张明细宽表。",
        ]

        # 整体风险评估（基于已计算的利润等指标）
        profit_margin = (profit / total * 100) if (profit is not None and total and total > 0) else None
        risk_level = "normal"
        risk_desc = ""
        if profit_margin is not None and profit_margin < 5:
            risk_level = "red"; risk_desc = f"利润率仅 {profit_margin:.1f}%，存在严重亏损风险"
        elif profit_margin is not None and profit_margin < 10:
            risk_level = "orange"; risk_desc = f"利润率 {profit_margin:.1f}% 偏低，需关注成本控制"
        elif discount is not None and total and discount / total > 0.3:
            risk_level = "orange"; risk_desc = f"折扣占比 {discount/total*100:.0f}% 过高，可能严重侵蚀利润"
        elif values and max(values) > 5 * (sum(values) / len(values)):
            risk_level = "yellow"; risk_desc = "存在极端大额订单，样本均值可能被拉高"
        summary = (
            f"本次分析覆盖 {row_count} 条明细记录，结合{related_text}进行综合研判。"
            f"{measure_name}合计 {self._fmt_number(total)}，单条范围 {self._fmt_number(min_v)} ~ {self._fmt_number(max_v)}。"
            + (f"【风险等级：{risk_desc}】" if risk_desc else "【整体表现正常】")
        )

        return {
            "summary": summary,
            "riskLevel": risk_level,
            "businessFindings": findings[:8],
            "driverBreakdowns": breakdowns[:6],
            "representativeRows": representatives,
            "opportunities": opportunities[:5],
            "risks": risks[:5],
            "suggestedNextQuestions": suggested[:6],
            "dataBasis": data_basis,
        }

    def _choose_business_measure_column(
        self,
        rows: list[dict[str, Any]],
        graph_context: dict[str, Any],
        measure_name: str,
    ) -> str:
        for app in ((graph_context.get("measure") or {}).get("apps") or []):
            fact_col = app.get("factColumn")
            if fact_col and any(fact_col in row for row in rows):
                return fact_col
        text = measure_name.lower()
        candidates = [c for c in rows[0].keys() if self._to_float(rows[0].get(c)) is not None]
        if "利润" in measure_name or "profit" in text:
            for col in candidates:
                if any(word in self._column_business_text(col).lower() for word in ("利润", "profit")):
                    return col
        for col in candidates:
            low = self._column_business_text(col).lower()
            if "sales" in low and ("price" in low or "amt" in low or "amount" in low):
                return col
            if "销售" in low and any(word in low for word in ("额", "金额", "价格")):
                return col
        return candidates[0] if candidates else ""

    def _select_business_driver_columns(
        self,
        profiles: list[dict[str, Any]],
        columns: list[str],
        row_count: int,
    ) -> list[str]:
        preferred_keywords = (
            "name", "type", "code", "carrier", "city", "country", "state",
            "department", "hours", "class", "category", "status", "channel",
            "region", "brand", "名称", "类型", "编码", "城市", "国家", "地区",
            "部门", "状态", "渠道", "品牌",
        )
        blocked = ("_sk", "_id", "date", "time", "zip", "street", "suite", "number")
        scored = []
        for p in profiles:
            col = p["column"]
            if col not in columns or p.get("nonNullCount", 0) == 0:
                continue
            distinct = p.get("distinctCount", 0)
            if distinct < 2 or distinct > max(8, row_count):
                continue
            low = col.lower()
            if "numeric" in p and not any(k in low for k in ("code", "type")):
                continue
            if any(k in low for k in blocked):
                continue
            score = 0
            if "." in col:
                score += 8
            for idx, keyword in enumerate(preferred_keywords):
                if keyword in low:
                    score += 20 - idx
            if score <= 0:
                continue
            scored.append((score, distinct, col))
        scored.sort(key=lambda x: (-x[0], x[1], x[2]))
        result = []
        seen_labels = set()
        for _, _, col in scored:
            label = self._business_column_label(col)
            if label in seen_labels:
                continue
            seen_labels.add(label)
            result.append(col)
            if len(result) >= 8:
                break
        return result

    def _breakdown_by_column(
        self,
        rows: list[dict[str, Any]],
        column: str,
        measure_column: str,
    ) -> dict[str, Any]:
        groups: dict[str, dict[str, Any]] = {}
        total_measure = 0.0
        for row in rows:
            value = row.get(column)
            if value in (None, "", "-", "null", "None"):
                value = "未填写"
            key = str(value)
            amount = self._to_float(row.get(measure_column)) or 0.0
            total_measure += amount
            item = groups.setdefault(key, {"value": key, "rowCount": 0, "measureTotal": 0.0})
            item["rowCount"] += 1
            item["measureTotal"] += amount
        ordered = sorted(groups.values(), key=lambda x: (-x["measureTotal"], x["value"]))
        for idx, item in enumerate(ordered, start=1):
            item["measureTotal"] = round(item["measureTotal"], 4)
            item["sharePct"] = round(item["measureTotal"] / total_measure * 100, 1) if total_measure else 0.0
            item["displayValue"] = self._display_data_value(item["value"], idx)
        return {
            "column": column,
            "label": self._business_column_label(column),
            "groups": ordered[:5],
            "insight": self._breakdown_insight(column, ordered, total_measure),
        }

    def _breakdown_insight(
        self,
        column: str,
        groups: list[dict[str, Any]],
        total_measure: float,
    ) -> str:
        if not groups:
            return ""
        top = groups[0]
        label = self._business_column_label(column)
        top_value = top.get("displayValue") or self._display_data_value(top.get("value"), 1)
        share = round((top["measureTotal"] / total_measure * 100), 1) if total_measure else 0.0
        if len(groups) == 1:
            return f"{label}全部集中在{top_value}。"
        return f"{label}中{top_value}贡献最高，占样本 {share}%。"

    def _representative_rows(
        self,
        rows: list[dict[str, Any]],
        measure_column: str,
        context_columns: list[str],
    ) -> list[dict[str, Any]]:
        scored = []
        for idx, row in enumerate(rows):
            amount = self._to_float(row.get(measure_column))
            if amount is None:
                continue
            scored.append((amount, idx, row))
        if not scored:
            return []
        scored.sort(key=lambda x: x[0], reverse=True)
        picks = [scored[0]]
        if len(scored) > 1:
            picks.append(scored[-1])
        result = []
        for amount, idx, row in picks:
            context = []
            for col in context_columns[:6]:
                value = row.get(col)
                if value not in (None, "", "-", "null", "None"):
                    context.append(f"{self._business_column_label(col)}={self._display_data_value(value)}")
            result.append({
                "rowIndex": idx,
                "measureValue": round(amount, 4),
                "context": "，".join(context[:6]),
            })
        return result

    def _business_opportunities(
        self,
        breakdowns: list[dict[str, Any]],
        profit: Optional[float],
        total: float,
        discount: Optional[float],
        coupon: Optional[float],
    ) -> list[str]:
        items = []
        for breakdown in breakdowns[:3]:
            groups = breakdown.get("groups") or []
            if groups:
                items.append(
                    f"优先复盘{breakdown['label']}中贡献最高的{groups[0].get('displayValue') or self._display_data_value(groups[0].get('value'), 1)}，确认其客群、商品或履约策略是否可复制。"
                )
        if discount is not None and total and discount / total > 0.2:
            items.append("折扣占比较高，建议结合商品和促销活动继续拆解，判断折扣是否带来足够利润。")
        if coupon is not None and total and coupon / total > 0.1:
            items.append("优惠券投入较明显，建议对比使用优惠券与未使用优惠券订单的利润表现。")
        if profit is not None and total and profit / total > 0:
            items.append("样本整体为正利润，可进一步寻找高利润订单的共同特征用于运营放大。")
        return items

    @staticmethod
    def _risk_item(level: str, text: str) -> dict[str, str]:
        """生成带等级的risk项。level: red/orange/yellow"""
        return {"level": level, "text": text}

    def _business_risks(
        self,
        rows: list[dict[str, Any]],
        measure_column: str,
        profit_column: str,
        profit: Optional[float],
        total: float,
    ) -> list[dict[str, str]]:
        risks = []
        if profit is not None and total and profit / total < 0.05:
            risks.append(self._risk_item("red", f"净利率仅 {(profit/total*100):.1f}%，利润严重偏低，销售额增长未转化为实际收益，需立即排查成本结构"))
        elif profit is not None and total and profit / total < 0.1:
            risks.append(self._risk_item("orange", f"净利率 {(profit/total*100):.1f}% 偏低，利润空间被压缩，建议关注折扣和履约成本"))
        if profit_column:
            negative_profit = [
                row for row in rows
                if (self._to_float(row.get(profit_column)) or 0.0) < 0
            ]
            neg_pct = len(negative_profit) / max(len(rows), 1) * 100
            if neg_pct > 20:
                risks.append(self._risk_item("red", f"{len(negative_profit)} 条明细（占比 {neg_pct:.0f}%）出现亏损，非个别现象，需排查对应商品/渠道"))
            elif negative_profit:
                risks.append(self._risk_item("yellow", f"存在 {len(negative_profit)} 条亏损明细（占比 {neg_pct:.0f}%），建议定位对应商品或渠道"))
        values = [self._to_float(row.get(measure_column)) for row in rows]
        values = [v for v in values if v is not None]
        if values:
            mean_v = sum(values) / len(values)
            if max(values) > 5 * mean_v:
                risks.append(self._risk_item("orange", f"存在极端大额订单（最高 {self._fmt_number(max(values))}，是均值的 {max(values)/mean_v:.0f} 倍），整体结论可能被少数订单左右"))
            elif max(values) > 3 * mean_v:
                risks.append(self._risk_item("yellow", f"样本中存在明显大额订单，汇总结果可能受少数订单影响较大"))
        return risks

    def _business_next_questions(
        self,
        measure_name: str,
        breakdowns: list[dict[str, Any]],
    ) -> list[dict[str, str]]:
        questions: list[dict[str, str]] = []
        for b in breakdowns[:3]:
            questions.append({"category": "维度下钻", "question": f"按{b['label']}继续拆解{measure_name}"})
        questions.append({"category": "指标归因", "question": f"找出{measure_name}最高和最低的明细差异"})
        questions.append({"category": "关联分析", "question": "对比高利润订单与低利润订单的共同特征"})
        return questions

    def _sum_column(self, rows: list[dict[str, Any]], column: str) -> Optional[float]:
        values = [self._to_float(row.get(column)) for row in rows if column in row]
        values = [v for v in values if v is not None]
        if not values:
            return None
        return sum(values)

    def _find_semantic_column(
        self,
        profiles: list[dict[str, Any]],
        *,
        include: tuple[str, ...],
        exclude: tuple[str, ...] = (),
    ) -> str:
        excluded = set(exclude)
        candidates = []
        for profile in profiles:
            col = profile.get("column") or ""
            if not col or col in excluded or "numeric" not in profile:
                continue
            text = self._column_business_text(col).lower()
            if any(word.lower() in text for word in include):
                candidates.append((profile.get("nonNullCount", 0), col))
        if not candidates:
            return ""
        candidates.sort(key=lambda item: (-item[0], item[1]))
        return candidates[0][1]

    def _business_column_label(self, column: str) -> str:
        if column in self._dynamic_column_labels:
            return self._dynamic_column_labels[column]
        table_name, raw = self._split_qualified_column(column)
        ttl_label = self._graph_column_label_from_ttl(table_name, raw)
        if ttl_label:
            self._dynamic_column_labels[column] = ttl_label
            return ttl_label
        return self._dynamic_column_labels.get(column) or "字段"

    def _column_business_text(self, column: str) -> str:
        table_name, raw = self._split_qualified_column(column)
        parts = [column, raw, self._table_business_label(table_name)]
        business_col = self._business_column_meta(table_name, raw)
        if business_col:
            parts.extend(str(business_col.get(k) or "") for k in ("name", "comment", "columnName"))
        source_col = self._source_column_meta(table_name, raw)
        if source_col:
            parts.extend(str(source_col.get(k) or "") for k in ("comment", "columnName"))
        return " ".join(p for p in parts if p)

    @staticmethod
    def _split_qualified_column(column: str) -> tuple[str, str]:
        if "." in column:
            table_name, raw = column.split(".", 1)
            return table_name, raw
        return "", column

    def _business_column_meta(self, table_name: str, column_name: str) -> dict[str, Any]:
        if table_name:
            for col in self._columns_by_table.get(table_name, []):
                if (col.get("columnName") or "").lower() == column_name.lower():
                    return col
        matches = self._source_columns_by_name.get(column_name.lower(), [])
        for match in matches:
            tbl = match.get("tableName") or ""
            for col in self._columns_by_table.get(tbl, []):
                if (col.get("columnName") or "").lower() == column_name.lower():
                    return col
        return {}

    def _source_column_meta(self, table_name: str, column_name: str) -> dict[str, Any]:
        if table_name:
            matches = self._source_columns_by_table_col.get(
                (table_name.lower(), column_name.lower()),
                [],
            )
            if matches:
                return matches[0]
        matches = self._source_columns_by_name.get(column_name.lower(), [])
        return matches[0] if matches else {}

    def _table_business_label(self, table_name: str) -> str:
        if not table_name:
            return ""
        meta = self._table_labels_by_name.get(table_name.lower(), {})
        label = self._clean_business_label(meta.get("cnName"))
        if label:
            return label
        return ""

    def _related_table_labels(self, columns: list[str]) -> list[str]:
        labels = []
        seen = set()
        for col in columns:
            table_name, _raw = self._split_qualified_column(col)
            if not table_name:
                continue
            label = self._table_business_label(table_name)
            if not label or label in seen:
                continue
            seen.add(label)
            labels.append(label)
        return labels

    @staticmethod
    def _join_table_column_label(table_label: str, column_label: str) -> str:
        if not table_label:
            return column_label
        if column_label in table_label or table_label in column_label:
            return column_label
        return f"{table_label}-{column_label}"

    @staticmethod
    def _clean_business_label(value: Any) -> str:
        text = str(value or "").strip()
        if not text:
            return ""
        text = re.sub(r"[_\s]+", " ", text).strip()
        text = re.sub(r"\b(sk|id)$", "", text, flags=re.I).strip()
        if not text:
            return ""
        if re.search(r"[\u4e00-\u9fff]", text):
            return text
        return ""

    @staticmethod
    def _display_data_value(value: Any, index: int = 0) -> str:
        text = str(value or "").strip()
        if not text:
            return "未填写"
        if re.search(r"[\u4e00-\u9fff]", text):
            return text
        if re.search(r"[A-Za-z]", text):
            return f"类别{index}" if index else "类别"
        return text

    @staticmethod
    def _to_float(value: Any) -> Optional[float]:
        if value in (None, "", "-", "null", "None"):
            return None
        try:
            return float(value)
        except Exception:
            return None

    @staticmethod
    def _fmt_number(value: float) -> str:
        if abs(value) >= 1000:
            return f"{value:,.2f}"
        return f"{value:.2f}"

    def _match_source_columns(
        self,
        column_name: str,
        fact_tables: set[str],
    ) -> list[dict[str, Any]]:
        key = column_name.lower()
        preferred = []
        for table_name in fact_tables:
            preferred.extend(
                self._source_columns_by_table_col.get((table_name.lower(), key), [])
            )
        fallback = self._source_columns_by_name.get(key, [])
        seen = set()
        result = []
        for item in preferred + fallback:
            uri = item.get("uri")
            if uri in seen:
                continue
            seen.add(uri)
            result.append(item)
        return result

    def _expand_source_column(
        self,
        col_meta: dict[str, Any],
        sample_values: list[str],
    ) -> dict[str, Any]:
        from rdflib import URIRef

        g = self._source_graph
        col_uri = URIRef(col_meta["uri"])
        relations = []

        def add_relation(kind: str, direction: str, target, predicate) -> None:
            target_payload = self._source_node_payload(target)
            if not target_payload:
                return
            relations.append({
                "type": kind,
                "direction": direction,
                "predicate": self._uri_tail(predicate),
                "target": target_payload,
            })

        relation_names = {
            str(DB.references): "referencesTable",
            str(DB.potentialFK): "potentialForeignKey",
            str(DB.similarTo): "similarColumn",
            str(DB.coOccursWith): "coOccursWithColumn",
            str(DB.sharedEnum): "sharedEnumColumn",
        }
        for pred, obj in g.predicate_objects(col_uri):
            kind = relation_names.get(str(pred))
            if kind:
                add_relation(kind, "out", obj, pred)
        for subj, pred, _ in g.triples((None, None, col_uri)):
            kind = relation_names.get(str(pred))
            if kind:
                add_relation(kind, "in", subj, pred)

        parent_table = self._source_parent_table(col_uri)
        parent_payload = self._source_node_payload(parent_table) if parent_table else {}
        return {
            "sourceColumn": col_meta,
            "parentTable": parent_payload,
            "sampleValueOverlap": self._sample_overlap(sample_values, col_meta.get("topValues") or []),
            "relations": self._dedupe_relations(relations)[:12],
        }

    def _source_parent_table(self, col_uri: Any) -> Any:
        g = self._source_graph
        parent = g.value(col_uri, DB.belongsToTable)
        if parent is None:
            parent = next(g.subjects(DB.containsColumn, col_uri), None)
        return parent

    def _source_node_payload(self, node: Any) -> dict[str, Any]:
        if node is None:
            return {}
        g = self._source_graph
        if (node, RDF.type, DB.Table) in g:
            table_name = self._str(g.value(node, DB.tableName) or g.value(node, DB.name))
            schema_uri = g.value(node, DB.belongsToSchema)
            schema_name = self._str(g.value(schema_uri, DB.name)) if schema_uri else ""
            return {
                "nodeType": "table",
                "uri": str(node),
                "tableName": table_name,
                "schemaName": schema_name,
                "comment": self._str(g.value(node, DB.comment)),
                "tableCategory": self._str(g.value(node, DB.tableCategory)),
                "rowCount": self._int(g.value(node, DB.rowCount)),
                "coreColumns": self._core_columns_for_table(table_name),
            }
        if (node, RDF.type, DB.Column) in g:
            parent = self._source_parent_table(node)
            table_name = self._str(g.value(parent, DB.tableName) or g.value(parent, DB.name)) if parent else ""
            return {
                "nodeType": "column",
                "uri": str(node),
                "columnName": self._str(g.value(node, DB.name)),
                "tableName": table_name,
                "columnType": self._str(g.value(node, DB.columnType)),
                "comment": self._str(g.value(node, DB.comment)),
                "isPrimaryKey": self._bool(g.value(node, DB.isPrimaryKey)),
                "cardinality": self._int(g.value(node, DB.cardinality)),
                "topValues": [str(v) for v in g.objects(node, DB.topValue)][:5],
            }
        return {
            "nodeType": "node",
            "uri": str(node),
            "label": self._uri_tail(node),
        }

    def _core_columns_for_table(self, table_name: str) -> list[dict[str, Any]]:
        cols = self._source_table_columns.get((table_name or "").lower(), [])
        if not cols:
            return []
        scored = sorted(
            cols,
            key=lambda c: (
                not bool(c.get("isPrimaryKey")),
                not self._looks_like_label_column(c.get("columnName") or "", c.get("comment") or ""),
                c.get("columnName") or "",
            ),
        )
        return [
            {
                "columnName": c.get("columnName"),
                "columnType": c.get("columnType"),
                "comment": c.get("comment"),
                "isPrimaryKey": c.get("isPrimaryKey"),
                "topValues": c.get("topValues", [])[:3],
            }
            for c in scored[:8]
        ]

    @staticmethod
    def _looks_like_label_column(name: str, comment: str) -> bool:
        text = f"{name} {comment}".lower()
        return any(k in text for k in ("name", "title", "label", "desc", "comment", "名称", "姓名", "标题", "描述"))

    @staticmethod
    def _sample_values(records: list[dict[str, Any]], column_name: str, limit: int = 8) -> list[str]:
        vals = []
        seen = set()
        for row in records:
            value = row.get(column_name)
            if value in (None, "", "-", "null", "None"):
                continue
            text = str(value)
            if text in seen:
                continue
            seen.add(text)
            vals.append(text)
            if len(vals) >= limit:
                break
        return vals

    @staticmethod
    def _sample_overlap(sample_values: list[str], top_values: list[str]) -> list[str]:
        top = {str(v) for v in top_values}
        return [v for v in sample_values if v in top]

    @staticmethod
    def _dedupe_relations(relations: list[dict[str, Any]]) -> list[dict[str, Any]]:
        seen = set()
        result = []
        for rel in relations:
            target = rel.get("target") or {}
            key = (rel.get("type"), rel.get("direction"), target.get("uri"))
            if key in seen:
                continue
            seen.add(key)
            result.append(rel)
        return result

    def _rollup_graph_expansion(self, expansions: list[dict[str, Any]]) -> dict[str, Any]:
        relation_counts: dict[str, int] = {}
        related_tables: dict[str, dict[str, Any]] = {}
        related_columns: dict[str, dict[str, Any]] = {}
        for item in expansions:
            for match in item.get("matches", []):
                parent = match.get("parentTable") or {}
                if parent.get("tableName"):
                    related_tables[parent["tableName"]] = parent
                for rel in match.get("relations", []):
                    rel_type = rel.get("type") or "relation"
                    relation_counts[rel_type] = relation_counts.get(rel_type, 0) + 1
                    target = rel.get("target") or {}
                    if target.get("nodeType") == "table" and target.get("tableName"):
                        related_tables[target["tableName"]] = target
                    elif target.get("nodeType") == "column" and target.get("uri"):
                        related_columns[target["uri"]] = target
        return {
            "relationCounts": relation_counts,
            "relatedTables": list(related_tables.values())[:12],
            "relatedColumns": list(related_columns.values())[:20],
            "analysisHints": self._graph_expansion_hints(relation_counts, related_tables),
        }

    @staticmethod
    def _graph_expansion_hints(
        relation_counts: dict[str, int],
        related_tables: dict[str, dict[str, Any]],
    ) -> list[str]:
        hints = []
        if relation_counts.get("referencesTable"):
            hints.append("样本字段包含可外键关联的列，可结合关联表核心字段补充实体画像或维度解释。")
        if relation_counts.get("sharedEnumColumn"):
            hints.append("样本字段存在共享枚举关系，可对比相同枚举域字段的一致性和口径差异。")
        if relation_counts.get("potentialForeignKey"):
            hints.append("样本字段存在潜在外键关系，适合进一步验证 join 可用性和缺失匹配。")
        dim_tables = [
            t.get("tableName") for t in related_tables.values()
            if t.get("tableCategory") in {"dimension", "lookup"}
        ]
        if dim_tables:
            hints.append(f"可优先扩展维度/查找表：{', '.join(dim_tables[:5])}。")
        return hints

    def _build_graph_answer(self, plan: dict[str, Any], graph_context: dict[str, Any]) -> dict[str, Any]:
        matched = plan.get("matched") or {}
        dims = graph_context.get("availableDrillDimensions") or []
        time_dims = [d for d in dims if d.get("isTime")]
        biz_dims = [d for d in dims if not d.get("isTime")]
        return {
            "summary": (
                f"「{matched.get('measureName')}」({matched.get('measureCode')}) "
                f"来自事实表 {', '.join(matched.get('factTables') or [])}，"
                f"当前业务 TTL 中可关联 {len(dims)} 个维度。"
            ),
            "businessDimensions": [
                {"code": d["code"], "name": d["name"]} for d in biz_dims
            ],
            "timeDimensions": [
                {"code": d["code"], "name": d["name"], "level": d.get("level")}
                for d in time_dims
            ],
            "joinPaths": graph_context.get("joinPaths") or [],
            "suggestedNextQuestions": self._suggest_next_questions(plan, graph_context),
        }

    def _suggest_next_questions(self, plan: dict[str, Any], graph_context: dict[str, Any]) -> list[dict[str, str]]:
        """生成带分类标签的推荐追问（统一入口）。"""
        matched = plan.get("matched") or {}
        measure_name = matched.get("measureName") or "该指标"
        dims = graph_context.get("availableDrillDimensions") or []
        biz_dims = [d for d in dims if not d.get("isTime")]
        time_dims = [d for d in dims if d.get("isTime")]
        suggestions: list[dict[str, str]] = []
        for d in biz_dims[:2]:
            suggestions.append({"category": "维度下钻", "question": f"按{d['name']}分析{measure_name}"})
        for d in time_dims[:1]:
            suggestions.append({"category": "趋势分析", "question": f"按{d['name']}看{measure_name}趋势"})
        suggestions.append({"category": "图谱解释", "question": f"解释{measure_name}的图谱关系"})
        return suggestions[:5]

    def _build_suggested_questions(
        self,
        mode: str,
        plan: dict[str, Any],
        result: dict[str, Any],
    ) -> list[dict[str, str]]:
        """根据查询模式和结果上下文，生成带分类标签的推荐追问。"""
        matched = plan.get("matched") or {}
        measure_name = matched.get("measureName") or ""
        dim_names = [d.get("name", "") for d in matched.get("dimensions", []) if d.get("name")]
        meas_code = matched.get("measureCode") or ""
        # 从配置列表中提取所有指标
        cfg_list = (result.get("daPayload") or {}).get("configureList") or []
        all_meas = [c["code"] for c in cfg_list if str(c.get("code", "")).startswith("MEAS_")]
        suggestions: list[dict[str, str]] = []

        # 根据模式生成不同方向的推荐问题
        if mode == "aggregate":
            if dim_names:
                for dn in dim_names[:2]:
                    suggestions.append({"category": "维度下钻", "question": f"按{dn}拆解{measure_name}"})
            if len(all_meas) >= 2:
                suggestions.append({"category": "指标对比", "question": f"对比{'和'.join(all_meas[:2])}的变化趋势"})
            if measure_name:
                suggestions.append({"category": "指标归因", "question": f"为什么{measure_name}发生了变化？"})
                suggestions.append({"category": "明细查询", "question": f"查询{measure_name}的原始明细"})
                suggestions.append({"category": "图谱解释", "question": f"{measure_name}有哪些可分析维度？"})

        elif mode == "detail":
            if measure_name:
                suggestions.append({"category": "指标归因", "question": f"为什么{measure_name}发生了变化？"})
                suggestions.append({"category": "明细增强", "question": f"分析{measure_name}的明细数据特征"})
                suggestions.append({"category": "图谱解释", "question": f"{measure_name}的口径是什么？"})
            if dim_names:
                suggestions.append({"category": "维度下钻", "question": f"按{dim_names[0]}分组统计{measure_name}"})

        elif mode == "analyze_detail":
            if measure_name:
                suggestions.append({"category": "指标归因", "question": f"为什么{measure_name}发生了变化？"})
                suggestions.append({"category": "关联分析", "question": f"哪些因素与{measure_name}显著相关？"})
            for dn in dim_names[:1]:
                suggestions.append({"category": "维度下钻", "question": f"按{dn}拆解差异最大的TOP项"})
            peer = result.get("peerAnalysis") or {}
            if peer.get("standout"):
                suggestions.append({"category": "实体检索", "question": f"查看异常{measure_name}对应的具体订单/记录"})

        elif mode == "relationship_analysis":
            suggestions.append({"category": "指标归因", "question": f"为什么{'和'.join(all_meas[:2]) if len(all_meas) >= 2 else measure_name}存在关联？"})
            suggestions.append({"category": "维度下钻", "question": f"按不同维度拆解关联关系是否一致？"})
            suggestions.append({"category": "趋势分析", "question": f"查看关联关系在不同时间段的变化趋势"})

        elif mode == "entity_lookup":
            entity = result.get("entity") or {}
            entity_val = entity.get("value") or entity.get("fieldText") or "该实体"
            suggestions.append({"category": "实体检索", "question": f"查看与{entity_val}同类的其他记录"})
            suggestions.append({"category": "关联分析", "question": f"{entity_val}关联了哪些维度和指标？"})
            suggestions.append({"category": "指标归因", "question": f"同类记录中哪些因素导致了差异？"})

        elif mode == "explain":
            if measure_name:
                suggestions.append({"category": "指标归因", "question": f"为什么{measure_name}发生了变化？"})
                suggestions.append({"category": "趋势分析", "question": f"{measure_name}最近趋势如何？"})
                suggestions.append({"category": "明细查询", "question": f"查询{measure_name}的明细数据"})

        elif mode == "landing_advice":
            sample_meas = (result.get("landingAdvice") or {}).get("currentGraph") or {}
            sm = (sample_meas.get("sampleMeasures") or [])[:3]
            for m in sm:
                suggestions.append({"category": "指标归因", "question": f"为什么{m}发生了变化？"})
            suggestions.append({"category": "图谱解释", "question": f"可用维度和指标概览"})

        # 兜底：至少返回2个通用追问
        if not suggestions and measure_name:
            suggestions.append({"category": "指标归因", "question": f"为什么{measure_name}发生了变化？"})
            suggestions.append({"category": "图谱解释", "question": f"{measure_name}有哪些可分析维度？"})

        return suggestions[:5]

    def _join_description(self, dim_name: str, app: dict[str, Any]) -> str:
        fact = app.get("factTable") or "事实表"
        fact_col = app.get("dimFactColumn") or "事实表字段"
        dim_table = app.get("dimTable") or "维表"
        dim_pk = app.get("dimPrimaryKey") or app.get("masterPrimaryKey") or "维表主键"
        dim_col = app.get("dimColumn") or "维度值字段"
        return f"{fact}.{fact_col} -> {dim_table}.{dim_pk}，取 {dim_col} 作为「{dim_name}」"

    def _node_by_code(self, code: str):
        for node in self._graph.subjects(IND.code, None):
            if self._str(self._graph.value(node, IND.code)) == code:
                return node
        return None

    def _explain(self, plan: dict[str, Any], da_result: dict[str, Any]) -> str:
        matched = plan.get("matched") or {}
        dims = matched.get("dimensions") or []
        dim_s = "、".join(d["name"] for d in dims) if dims else "不分组"
        query_mode = ((plan.get("intent") or {}).get("queryMode") or "").lower()
        row_count = 0
        data = da_result.get("data") or {}
        if isinstance(data, dict):
            rows = data.get("list") or data.get("cellList") or []
            row_count = len(rows) if isinstance(rows, list) else 0
            if query_mode in {"detail", "analyze_detail"} and row_count > 0:
                row_count -= 1
        return (
            f"已匹配指标「{matched.get('measureName')}」"
            f"，按「{dim_s}」查询，"
            f"返回 {max(row_count, 0)} 行。"
        )

    def _failed_plan(self, question: str, error: str, diagnostics: dict[str, Any]) -> dict[str, Any]:
        return {
            "ok": False,
            "needsClarification": True,
            "clarification": error,
            "intent": {"rawQuestion": question},
            "matched": {},
            "diagnostics": diagnostics,
        }

    def _clarify_plan(self, question: str, message: str, diagnostics: dict[str, Any]) -> dict[str, Any]:
        plan = self._failed_plan(question, message, diagnostics)
        plan["clarification"] = message
        return plan

    def _is_ambiguous(self, hits: list[tuple[float, MeasureMeta]]) -> bool:
        if len(hits) < 2:
            return False
        top, second = hits[0][0], hits[1][0]
        return top < 90 and second >= top * 0.85

    def _sort_available_dims(
        self,
        dims: list[DimensionMeta],
        measure_tables: set[str],
    ) -> list[DimensionMeta]:
        return sorted(
            dims,
            key=lambda d: (
                d.is_time,
                self._min_row_num(d, measure_tables) if self._min_row_num(d, measure_tables) is not None else 10**9,
                d.cn_name,
            ),
        )

    def _min_row_num(self, dim: DimensionMeta, measure_tables: set[str]) -> Optional[int]:
        vals = [v for t, v in dim.row_nums.items() if t in measure_tables]
        return min(vals) if vals else None

    def _measure_hit_payload(self, hit: tuple[float, MeasureMeta]) -> dict[str, Any]:
        score, m = hit
        return {
            "code": m.code,
            "name": m.cn_name,
            "score": round(score, 2),
            "tables": sorted(m.tables),
        }

    def _dimension_hit_payload(self, hit: tuple[float, DimensionMeta]) -> dict[str, Any]:
        score, d = hit
        return {
            "code": d.code,
            "name": d.cn_name,
            "score": round(score, 2),
            "level": d.level_code,
            "isTime": d.is_time,
            "tables": sorted(d.tables),
        }

    def _llm_map_to_kg_measures(
        self,
        question: str,
        measure_hits: list[tuple[float, MeasureMeta]],
    ) -> Optional[MeasureMeta]:
        """LLM 兜底：将问题中的领域术语映射到 KG 中实际存在的指标。"""
        try:
            from kg_builder.utils.llm_config import llm_config_from_env
            cfg = llm_config_from_env(Path.cwd())
            api_key = (cfg.get("api_key") or "").strip()
            base_url = (cfg.get("base_url") or "").strip().rstrip("/")
            model = (cfg.get("model") or "").strip()
            if not api_key or not base_url or not model:
                return None
            candidates = [
                {"code": m.code, "name": m.cn_name, "tables": sorted(m.tables)}
                for _score, m in measure_hits
            ]
            system = (
                "你是一个业务指标匹配器。用户提出一个问题，其中可能包含不在候选列表中的业务术语。"
                "请从候选指标中选择最接近用户意图的一个。"
                "只返回 JSON：{\"code\": \"指标code\", \"reason\": \"简短理由\"}。"
                "如果所有候选指标都与用户意图无关，返回 {\"code\": null, \"reason\": \"无匹配\"}。"
                "不要编造候选列表之外的指标。"
            )
            payload = {
                "model": model,
                "messages": [{"role": "user", "content": json.dumps({
                    "question": question,
                    "candidates": candidates,
                }, ensure_ascii=False)}],
                "temperature": 0,
                "max_tokens": 128,
            }
            req = urllib.request.Request(
                f"{base_url}/chat/completions",
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {api_key}",
                },
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            content = (data.get("choices", [{}])[0].get("message", {}).get("content", "")).strip()
            parsed = json.loads(content) if content.startswith("{") else {}
            code = str(parsed.get("code") or "").strip()
            if code and code in self._measures:
                self._log(f"[NLQ] LLM 兜底匹配: {code} ({self._measures[code].cn_name}) reason={parsed.get('reason','')}")
                return self._measures[code]
        except Exception as e:
            self._log(f"[NLQ] LLM 兜底匹配失败: {e}")
        return None

    def _build_rewrite_suggestions(
        self,
        question: str,
        available: list[dict[str, Any]],
    ) -> list[dict[str, str]]:
        """用已有指标生成改写建议，帮助用户快速找到可分析的问题。"""
        suggestions: list[dict[str, str]] = []
        patterns = [
            ("{name}的最近趋势如何？", "趋势分析"),
            ("查询{name}的明细数据", "明细查询"),
            ("为什么{name}发生了变化？", "指标归因"),
        ]
        for m in available[:4]:
            for tmpl, cat in patterns:
                q = tmpl.format(name=m["name"])
                if q not in {s["question"] for s in suggestions}:
                    suggestions.append({"category": cat, "question": q})
        # 去重后取前6个
        seen: set[str] = set()
        deduped: list[dict[str, str]] = []
        for s in suggestions:
            if s["question"] not in seen:
                seen.add(s["question"])
                deduped.append(s)
        return deduped[:6]

    def _top_measure_examples(self) -> list[dict[str, Any]]:
        return [
            {"code": m.code, "name": m.cn_name, "tables": sorted(m.tables)}
            for m in sorted(self._measures.values(), key=lambda x: x.cn_name)[:15]
        ]

    def _wanted_time_level(self, question: str) -> str:
        for level, pat in _TIME_LEVEL_PATTERNS:
            if pat.search(question):
                return level
        return ""

    def _token_score(self, tokens: list[str], search_text: str, weight: int) -> float:
        if not tokens or not search_text:
            return 0.0
        score = 0.0
        for tok in tokens:
            if tok in search_text:
                score += weight + min(len(tok), 8)
        return score

    def _tokens(self, text: str) -> list[str]:
        raw = re.findall(r"MEAS_[A-Za-z0-9_]+|DIM_[A-Za-z0-9_]+|[A-Za-z0-9_]+|[\u4e00-\u9fa5]{2,}", text)
        tokens: list[str] = []
        seen: set[str] = set()
        for item in raw:
            for tok in self._split_token(item):
                norm = self._norm(tok)
                if len(norm) < 2 or norm in _NOISE_WORDS or norm in seen:
                    continue
                seen.add(norm)
                tokens.append(norm)
        return tokens

    def _split_token(self, token: str) -> list[str]:
        parts = [token]
        if re.search(r"[\u4e00-\u9fa5]", token) and len(token) > 4:
            for n in (2, 3, 4, 5, 6):
                for i in range(0, len(token) - n + 1):
                    parts.append(token[i:i + n])
        if "_" in token:
            parts.extend(p for p in token.split("_") if p)
        return parts

    def _build_search_text(self, *parts: str) -> str:
        joined = " ".join(p for p in parts if p)
        tokenized = " ".join(self._tokens(joined))
        return f"{self._norm(joined)} {tokenized}".strip()

    def _best_label_for_question(self, question: str, measure: MeasureMeta) -> str:
        return measure.cn_name if measure.cn_name and self._norm(measure.cn_name) in self._norm(question) else measure.code

    @staticmethod
    def _norm(text: str) -> str:
        return re.sub(r"[\s_\-:：,，。./\\()（）\[\]【】]+", "", (text or "").lower())

    @staticmethod
    def _str(value: Any) -> str:
        return "" if value is None else str(value)

    @staticmethod
    def _int(value: Any) -> Optional[int]:
        if value is None:
            return None
        try:
            return int(float(str(value)))
        except Exception:
            return None

    @staticmethod
    def _float(value: Any) -> Optional[float]:
        if value is None:
            return None
        try:
            return float(str(value))
        except Exception:
            return None

    @staticmethod
    def _literal_value(value: Any) -> Any:
        py_value = getattr(value, "toPython", lambda: value)()
        return py_value

    @staticmethod
    def _bool(value: Any) -> Optional[bool]:
        if value is None:
            return None
        low = str(value).strip().lower()
        if low in {"true", "1", "yes", "y"}:
            return True
        if low in {"false", "0", "no", "n"}:
            return False
        return None

    @staticmethod
    def _uri_tail(value: Any) -> str:
        text = str(value or "")
        if "#" in text:
            return text.rsplit("#", 1)[-1]
        return text.rstrip("/").rsplit("/", 1)[-1]
