"""Deterministic routing shared by Smart Insight and Agent runs.

The first version deliberately mirrors the established Smart Insight routing
rules.  Keeping the classification on the server makes Web, MCP, and future
clients agree without giving a model authority to choose unsafe execution.
"""

from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any


@dataclass(frozen=True)
class RouteDecision:
    mode: str
    route: str
    flow_node: str
    reason: str
    reason_code: str
    force_clear_context: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "mode": self.mode,
            "route": self.route,
            "flowNode": self.flow_node,
            "reason": self.reason,
            "reasonCode": self.reason_code,
            "forceClearContext": self.force_clear_context,
        }


class SmartIntentRouter:
    @staticmethod
    def _specific_document_lookup(question: str) -> bool:
        value = r"[A-Za-z0-9_.\-]{2,}"
        field = r"[\u4e00-\u9fa5A-Za-z_][\u4e00-\u9fa5A-Za-z0-9_\-]{0,40}"
        document_field = re.compile(
            r"(订单编号|订单号|订单ID|单据编号|单据号|退货单号|销售单号|order\s*(?:number|no|id)?|bill|document)",
            re.IGNORECASE,
        )
        table_field = re.compile(rf"{field}\s*[-－—]\s*{field}\s*(?:[:：=]|为|是)\s*[\"'“”]?{value}[\"'“”]?", re.IGNORECASE)
        field_value = re.compile(rf"^(?:查|查询|查看|检索|搜索)?\s*{field}\s*(?:[:：=]|为|是)?\s*[\"'“”]?{value}[\"'“”]?$", re.IGNORECASE)
        label = (re.split(r"[:：=]|为|是", question, maxsplit=1)[0] or "").strip()
        return bool(document_field.search(label) and (table_field.search(question) or field_value.search(question)))

    @staticmethod
    def _exact_attribute_lookup(question: str) -> bool:
        value = r"[^,，;；。！？\n\r]{1,80}"
        field = r"[\u4e00-\u9fa5A-Za-z_][\u4e00-\u9fa5A-Za-z0-9_\-]{0,40}"
        table_field = re.compile(rf"{field}\s*[-－—]\s*{field}\s*(?:[:：=]|为|是)\s*[\"'“”]?{value}[\"'“”]?", re.IGNORECASE)
        field_value = re.compile(rf"^(?:查|查询|查看|检索|搜索)?\s*{field}\s*(?:[:：=]|为|是)\s*[\"'“”]?{value}[\"'“”]?$", re.IGNORECASE)
        return bool(table_field.search(question) or field_value.search(question))

    def decide(self, question: str, context: dict[str, Any] | None = None) -> RouteDecision:
        query = str(question or "").strip()
        context = context or {}
        analysis_mode = str(context.get("analysisMode") or "").strip()
        if self._specific_document_lookup(query):
            return RouteDecision("query", "nlq_query", "E", "识别为订单/单据编号精确检索，走单据追踪链路", "DOCUMENT_LOOKUP", True)
        if self._exact_attribute_lookup(query):
            return RouteDecision("query", "nlq_query", "D", "识别为表字段精确属性检索，走 NLQ/entity_lookup 明细查询", "ATTRIBUTE_LOOKUP", True)
        if analysis_mode == "document_trace":
            return RouteDecision("insight", "document_trace", "E", "由异常上下文选择单据追踪分析", "DOCUMENT_TRACE_CONTEXT")

        explicit_light = bool(re.search(r"只查|只要|不用分析|无需分析|不做分析|仅查询|仅明细|返回明细", query))
        problem_orders = bool(re.search(r"(有问题|问题|异常|风险|预警|告警).*(订单|单据)|(订单|单据).*(有问题|问题|异常|风险|预警|告警)|单据追踪|问题订单|异常订单", query))
        asks_specific_entity = bool(re.search(r"该[订单笔个项条只次件张批种座家名位辆台部本套组类科属种块片根颗粒层段行列册份处所期届场届次回趟度]|这个|此[订单笔个次项]|某个|哪一个|哪[一个笔条次件张]|什么订单|什么促销|同类|同组|同批|同种|类似|相似", query))
        has_detail = bool(re.search(r"明细|详情|列表|记录|样本|原始|订单|行级", query))
        asks_deep_why = bool(re.search(r"为什么|为何|原因|归因|根因|影响因素|驱动|诊断|定位|异常|波动|变化|变动|下滑|下降|降低|上涨|上升|提升|增长|暴涨|暴跌|偏高|偏低|是否|有问题|问题|隐患|风险|瓶颈|制约|较小|较大|过高|过低|不足|过多|偏少|偏多|太少|太多|增幅|降幅|增速|减速|放缓|加快", query))
        asks_contribution = bool(re.search(r"贡献|贡献度|结构|拆解|分解|下钻|关键因素|主要因素|TOP|排行|最大|最小|最高|最低", query, re.IGNORECASE))
        asks_compare_trend = bool(re.search(r"趋势|走势|对比|比较|环比|同比|上期|本期|同期|差异|差距|连续|最近.*变化|近.*变化|增幅|降幅", query))
        asks_metric_dimension_analysis = bool(re.search(r"指标分析|维度分析|按.+分析|从.+维度|哪个维度|哪些维度|维度.*影响|维度.*贡献|指标.*变化|指标.*波动", query))
        asks_relationship = bool(re.search(r"相关|关系|显著|是否也|好方法|降价|促销.*好|折扣类|折扣.*订单|跨维度|图谱跳跃", query))
        asks_graph_only = bool(re.search(r"图谱|血缘|关系|来源|口径|定义|有哪些维度|有哪些.*维度|哪些.*维度|可用维度|可分析维度|分析维度|能按哪些", query))
        simple_query = bool(re.search(r"^(查|查询|查看|展示|列出|给我|看一下|统计|汇总|计算|多少|总额|平均|数量|明细)", query))
        light_dimension_query = bool(re.search(r"^(按|从).+(查看|查询|统计|汇总|分析|对比)", query)) and not bool(re.search(r"为什么|为何|原因|归因|根因|影响最大|贡献|贡献度|异常|波动|变化|变动|下滑|下降|上涨|上升|环比|同比|趋势|走势|显著|是否", query))

        if explicit_light:
            return RouteDecision("query", "nlq_query", "D", "按问题要求仅执行查询", "EXPLICIT_LIGHT_QUERY")
        if problem_orders:
            return RouteDecision("query", "nlq_query", "E", "识别为监控预警单据追踪的问题订单查询", "PROBLEM_ORDER_QUERY")
        if light_dimension_query:
            return RouteDecision("query", "nlq_query", "D", "识别为指标按维度聚合查询", "DIMENSION_AGGREGATE_QUERY")
        if asks_specific_entity and not (asks_contribution or asks_compare_trend or asks_metric_dimension_analysis):
            return RouteDecision("query", "nlq_query", "D", "识别为实体检索与同类对比分析，不适用指标归因", "ENTITY_LOOKUP")
        if asks_relationship:
            return RouteDecision("query", "nlq_query", "D", "识别为相关关系或跨维差异分析", "RELATIONSHIP_QUERY")
        if has_detail and not (asks_deep_why or asks_contribution or asks_compare_trend or asks_metric_dimension_analysis):
            return RouteDecision("query", "nlq_query", "D", "识别为明细或样本查询", "DETAIL_QUERY")
        if asks_deep_why or asks_contribution or asks_compare_trend or asks_metric_dimension_analysis or analysis_mode:
            return RouteDecision("insight", "metric_diagnosis", "F", "识别为指标/维度分析、波动归因、趋势对比或结构贡献问题", "METRIC_DIAGNOSIS")
        if asks_graph_only:
            return RouteDecision("query", "nlq_query", "G", "识别为图谱解释或口径查询", "SEMANTIC_EXPLANATION")
        if simple_query:
            return RouteDecision("query", "nlq_query", "D", "识别为指标聚合或明细查询", "SIMPLE_QUERY")
        if re.search(r"分析|洞察|Insight", query, re.IGNORECASE):
            return RouteDecision("insight", "metric_diagnosis", "F", "识别为综合分析问题", "COMPREHENSIVE_INSIGHT")
        return RouteDecision("query", "nlq_query", "D", "默认先执行图谱问数", "DEFAULT_NLQ")
