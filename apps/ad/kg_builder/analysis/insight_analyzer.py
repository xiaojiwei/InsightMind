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
from typing import Any, Callable, Generator, List, Optional

from kg_builder.analysis.time_parser import parse_question_time
from kg_builder.semantic_retrieval.normalizer import normalize_text
from kg_builder.utils.http_client import urlopen as _urlopen


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
        semantic_mapping_service: Any | None = None,
    ) -> None:
        self._data_agent_url = data_agent_url
        self._ttl_path = ttl_path
        self._llm_config = llm_config
        self._log = log_cb
        self._cancel_cb = cancel_cb or (lambda: False)
        self._context = context if isinstance(context, dict) else {}
        self._semantic_mapping_service = semantic_mapping_service

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
        anomaly_profile = self._infer_anomaly_profile(question, intent)
        intent["anomaly_profile"] = anomaly_profile
        intent["anomaly_type"] = anomaly_profile.get("type")
        intent["anomaly_direction"] = anomaly_profile.get("direction")
        self._log(
            f"  关键词: {intent.get('meas_keywords')}，类型: {intent.get('analysis_type')}，"
            f"异常: {anomaly_profile.get('label')}，来源: {anomaly_profile.get('source_label')}，"
            f"置信度: {anomaly_profile.get('confidence_label')}"
        )
        yield {"step": "intent", "result": intent}
        yield {"step": "anomaly_profile", "result": anomaly_profile}

        if self._cancel_cb():
            return

        # ── Step 3: KG 指标匹配 ─────────────────────────────────────────── #
        self._log("▶ Step 3: 知识图谱指标匹配...")
        meas_info = self._find_meas_in_kg(intent.get("meas_keywords", []), question)

        if meas_info and meas_info.get("needs_clarification"):
            candidates = meas_info.get("semantic_mapping", {}).get("measureCandidates", [])
            names = "、".join(
                str(item.get("name") or item.get("code") or "") for item in candidates[:3]
            )
            message = f"语义召回到候选指标：{names}。请确认指标后再执行分析。"
            self._log(f"  ⚠ {message}")
            yield {
                "step": "kg_clarify",
                "action": "clarify",
                "message": message,
                "result": meas_info,
            }
            return

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
            "semantic_mapping": meas_info.get("semantic_mapping") or {},
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
            if event.get("step") == "no_data":
                self._log("═══ Insight 分析因数据不足提前结束 ═══")
                return

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
          anomaly_type: metric_drop/metric_rise/metric_spike/trend_anomaly/
                        dimension_slice/document_trace/data_quality/unknown
          anomaly_direction: down/up/spike/volatile/flat/unknown
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
            "  anomaly_type: metric_drop（指标下降）/ metric_rise（指标上升或偏高）/ metric_spike（尖峰）/"
            "trend_anomaly（趋势异常）/ dimension_slice（某个维度切片异常）/ document_trace（单据级异常）/"
            "data_quality（疑似数据质量异常）/ unknown\n"
            "  anomaly_direction: down/up/spike/volatile/flat/unknown 之一\n"
            "重要：meas_keywords 必须是纯净的指标名称或英文缩写，不含时间、疑问词。\n"
            '返回示例: {"meas_keywords":["NSS","净服务评分"],"analysis_type":"root_cause",'
            '"anomaly_type":"metric_drop","anomaly_direction":"down"}'
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
                parsed.setdefault("anomaly_type", "unknown")
                parsed.setdefault("anomaly_direction", "unknown")
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
        return {
            **time_info,
            "meas_keywords": keywords,
            "analysis_type": atype,
            "anomaly_type": "unknown",
            "anomaly_direction": "unknown",
        }

    def _infer_anomaly_profile(self, question: str, intent: dict) -> dict[str, Any]:
        """Infer anomaly source, shape and evidence strength for downstream analysis."""
        q = question or ""
        cell = self._cell_context()
        anomaly = cell.get("anomaly") if isinstance(cell.get("anomaly"), dict) else {}
        diagnosis = cell.get("diagnosis") if isinstance(cell.get("diagnosis"), dict) else {}
        documents = cell.get("documents") if isinstance(cell.get("documents"), list) else []
        alerts = cell.get("alerts") if isinstance(cell.get("alerts"), list) else []
        filters = self._cell_context_filters()

        direction = str(intent.get("anomaly_direction") or "unknown").strip() or "unknown"
        anomaly_type = str(intent.get("anomaly_type") or "unknown").strip() or "unknown"
        source = "unknown"
        context_anomaly_type = self._normalize_context_anomaly_type(
            self._context.get("anomalyType") or anomaly.get("type") or anomaly.get("anomalyType")
        )
        if anomaly_type == "unknown" and context_anomaly_type:
            anomaly_type = context_anomaly_type
            source = str(diagnosis.get("source") or anomaly.get("source") or "cell_context")

        text_matched = False
        if re.search(r"下滑|下降|降低|变低|走低|减少|减少了|跌|掉|负增长", q):
            direction = "down"
            anomaly_type = "metric_drop"
            source = "question_intent"
            text_matched = True
        elif re.search(r"上涨|上升|升高|变高|走高|增加|增长|偏高|过高|飙升", q):
            direction = "up"
            anomaly_type = "metric_rise"
            source = "question_intent"
            text_matched = True
        elif re.search(r"突增|陡增|尖峰|峰值|突然|暴涨|暴跌|跳变", q):
            direction = "spike"
            anomaly_type = "metric_spike"
            source = "question_intent"
            text_matched = True
        elif re.search(r"波动|震荡|不稳定|忽高忽低", q):
            direction = "volatile"
            anomaly_type = "trend_anomaly"
            source = "question_intent"
            text_matched = True

        if re.search(r"趋势|走势|连续|周期|季节|拐点", q):
            anomaly_type = "trend_anomaly"
            source = "question_intent"
            text_matched = True
            if direction == "unknown":
                direction = "volatile"
        if re.search(r"数据质量|缺失|为空|null|重复|口径|同步|延迟|脏数据|异常值", q, re.I):
            anomaly_type = "data_quality"
            source = "question_intent"
            text_matched = True
        if documents:
            anomaly_type = "document_trace"
            source = "document_rule"
        elif alerts and source == "unknown":
            source = "metric_alert"
        elif anomaly_type == "unknown" and (cell or any(not self._is_time_context_filter(item) for item in filters)):
            anomaly_type = "dimension_slice"
            source = "cell_context"
        elif source == "unknown" and text_matched:
            source = "question_intent"

        labels = {
            "metric_drop": "指标下降异常",
            "metric_rise": "指标上升/偏高异常",
            "metric_spike": "指标突变异常",
            "trend_anomaly": "趋势异常",
            "dimension_slice": "维度切片异常",
            "document_trace": "单据级异常",
            "data_quality": "数据质量异常",
            "unknown": "未指定异常类型",
        }
        strategies = {
            "metric_drop": "优先解释下降贡献最大的指标算子、维度值和时间段。",
            "metric_rise": "优先解释拉升最大的指标算子、维度值和时间段，并判断是否属于业务预期。",
            "metric_spike": "优先定位突变发生的日期/周期，再找对应切片和明细证据。",
            "trend_anomaly": "优先做时间序列趋势、异常期和拐点解释，再补充结构贡献。",
            "dimension_slice": "优先围绕当前单元格/维度切片，比较同口径上下期并下钻相关维度。",
            "document_trace": "优先使用真实单据、命中规则和异常字段解释，不套用总体波动模板。",
            "data_quality": "优先检查缺失、重复、延迟、口径变化等数据质量证据，再判断业务含义。",
            "unknown": "按常规指标波动归因流程分析。",
        }

        focus_filters = []
        for item in filters:
            if self._is_time_context_filter(item):
                continue
            focus_filters.append({
                "code": str(item.get("code") or ""),
                "name": str(item.get("name") or item.get("code") or ""),
                "value": self._cell_filter_value(item),
            })

        normalized_type = anomaly_type if anomaly_type in labels else "unknown"
        shape = str(diagnosis.get("shape") or anomaly.get("shape") or self._profile_shape(normalized_type))
        evidence_items = self._profile_evidence_items(
            anomaly=anomaly,
            diagnosis=diagnosis,
            documents=documents,
            alerts=alerts,
            focus_filters=focus_filters,
        )
        evidence = []
        if anomaly:
            evidence.append(f"{anomaly.get('title') or ''} {anomaly.get('reason') or ''}".strip())
        if focus_filters:
            evidence.append("当前切片: " + " / ".join(
                f"{item['name']}={item['value']}" for item in focus_filters if item.get("value")
            ))
        if documents:
            evidence.append(f"命中异常单据 {len(documents)} 条")
        if alerts:
            evidence.append(f"命中指标预警 {len(alerts)} 个")

        evidence_strength = self._profile_evidence_strength(
            source=source,
            evidence_items=evidence_items,
            documents=documents,
            alerts=alerts,
            focus_filters=focus_filters,
        )
        confidence = self._profile_confidence(
            source=source,
            anomaly_type=normalized_type,
            evidence_strength=evidence_strength,
            text_matched=text_matched,
            has_context=bool(cell),
        )
        hypotheses = self._profile_hypotheses(normalized_type, source, diagnosis, documents, alerts)

        return {
            "type": normalized_type,
            "label": labels.get(normalized_type, labels["unknown"]),
            "source": source,
            "source_label": self._profile_source_label(source),
            "shape": shape,
            "shape_label": self._profile_shape_label(shape),
            "direction": direction if direction in {"down", "up", "spike", "volatile", "flat", "unknown"} else "unknown",
            "strategy": strategies.get(normalized_type, strategies["unknown"]),
            "confidence": confidence,
            "confidence_label": self._confidence_label(confidence),
            "evidence_strength": evidence_strength,
            "evidence_strength_label": self._strength_label(evidence_strength),
            "focus_filters": focus_filters,
            "evidence": [item for item in evidence if item],
            "evidence_items": evidence_items,
            "hypotheses": hypotheses,
        }

    @staticmethod
    def _profile_shape(anomaly_type: str) -> str:
        mapping = {
            "metric_drop": "drop",
            "metric_rise": "rise",
            "metric_spike": "spike",
            "trend_anomaly": "trend",
            "dimension_slice": "slice",
            "document_trace": "document",
            "data_quality": "quality",
        }
        return mapping.get(str(anomaly_type or ""), "unknown")

    @staticmethod
    def _profile_shape_label(shape: str) -> str:
        mapping = {
            "drop": "下降",
            "rise": "上升/偏高",
            "spike": "突变",
            "trend": "趋势",
            "slice": "维度切片",
            "document": "单据",
            "quality": "数据质量",
            "unknown": "未知",
        }
        return mapping.get(str(shape or ""), "未知")

    @staticmethod
    def _profile_source_label(source: str) -> str:
        mapping = {
            "document_rule": "单据规则",
            "metric_alert": "指标预警",
            "cell_context": "透视表单元格",
            "question_intent": "用户问题",
            "unknown": "未知",
        }
        return mapping.get(str(source or ""), "未知")

    @staticmethod
    def _confidence_label(score: float) -> str:
        if score >= 0.8:
            return "高"
        if score >= 0.6:
            return "中"
        return "低"

    @staticmethod
    def _strength_label(score: int) -> str:
        if score >= 80:
            return "强"
        if score >= 55:
            return "中"
        return "弱"

    def _profile_evidence_items(
        self,
        anomaly: dict[str, Any],
        diagnosis: dict[str, Any],
        documents: list[dict[str, Any]],
        alerts: list[dict[str, Any]],
        focus_filters: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        items = []
        for item in diagnosis.get("evidence") or []:
            if isinstance(item, dict):
                items.append({
                    "type": str(item.get("type") or "evidence"),
                    "label": str(item.get("label") or ""),
                    "detail": str(item.get("detail") or ""),
                    "weight": int(float(item.get("weight") or 0)),
                })
        if documents and not any(item.get("type") == "document" for item in items):
            first = documents[0] if isinstance(documents[0], dict) else {}
            items.append({
                "type": "document",
                "label": f"命中 {len(documents)} 条异常单据",
                "detail": f"{first.get('documentNo') or '-'} {first.get('fieldName') or first.get('field') or ''}={first.get('value')}",
                "weight": 95,
            })
        if alerts and not any(item.get("type") == "alert" for item in items):
            labels = "、".join(str(item.get("label") or item.get("type") or "指标预警") for item in alerts[:4])
            items.append({
                "type": "alert",
                "label": f"命中 {len(alerts)} 个指标预警",
                "detail": labels,
                "weight": 75,
            })
        if focus_filters and not any(item.get("type") == "context" for item in items):
            items.append({
                "type": "context",
                "label": "当前透视表切片",
                "detail": " / ".join(f"{item['name']}={item['value']}" for item in focus_filters if item.get("value")),
                "weight": 60,
            })
        if anomaly and not any(item.get("type") == "summary" for item in items):
            detail = f"{anomaly.get('title') or ''} {anomaly.get('reason') or ''}".strip()
            if detail:
                items.append({"type": "summary", "label": "业务解释摘要", "detail": detail, "weight": 65})
        return [item for item in items if item.get("label") or item.get("detail")][:8]

    @staticmethod
    def _profile_evidence_strength(
        source: str,
        evidence_items: list[dict[str, Any]],
        documents: list[dict[str, Any]],
        alerts: list[dict[str, Any]],
        focus_filters: list[dict[str, Any]],
    ) -> int:
        weights = [int(float(item.get("weight") or 0)) for item in evidence_items if isinstance(item, dict)]
        if documents:
            weights.append(min(98, 82 + min(len(documents), 8) * 2))
        if alerts:
            weights.append(min(90, 68 + min(len(alerts), 5) * 3))
        if focus_filters:
            weights.append(60)
        if not weights:
            return 35 if source != "unknown" else 25
        return max(25, min(98, int(round(max(weights) * 0.72 + (sum(weights) / len(weights)) * 0.28))))

    @staticmethod
    def _profile_confidence(
        source: str,
        anomaly_type: str,
        evidence_strength: int,
        text_matched: bool,
        has_context: bool,
    ) -> float:
        base = {
            "document_rule": 0.88,
            "metric_alert": 0.72,
            "cell_context": 0.54,
            "question_intent": 0.56,
            "unknown": 0.35,
        }.get(source, 0.35)
        if anomaly_type == "unknown":
            base -= 0.12
        if text_matched:
            base += 0.05
        if has_context:
            base += 0.04
        base += max(0, evidence_strength - 55) * 0.003
        return round(max(0.1, min(0.98, base)), 2)

    @staticmethod
    def _profile_hypotheses(
        anomaly_type: str,
        source: str,
        diagnosis: dict[str, Any],
        documents: list[dict[str, Any]],
        alerts: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        out = []
        for item in diagnosis.get("hypotheses") or []:
            if isinstance(item, dict):
                out.append({
                    "type": str(item.get("type") or source or anomaly_type),
                    "title": str(item.get("title") or ""),
                    "reason": str(item.get("reason") or ""),
                })
        if out:
            return out[:4]
        if documents:
            first = documents[0] if isinstance(documents[0], dict) else {}
            return [{
                "type": "document_rule",
                "title": "单据规则命中",
                "reason": f"优先解释单据 {first.get('documentNo') or '-'} 的规则、字段和值，不把它泛化成总体指标波动。",
            }]
        if alerts:
            return [{
                "type": "metric_alert",
                "title": "指标预警命中",
                "reason": "先确认预警规则指向表达式、阈值还是趋势路径，再选择对应归因路径。",
            }]
        mapping = {
            "metric_drop": ("下降贡献", "优先找拉低最大的维度值、算子和时间段。"),
            "metric_rise": ("上升贡献", "优先找拉升最大的维度值、算子和时间段，并判断是否为业务预期。"),
            "metric_spike": ("突变定位", "先定位突变时间点，再看同一切片的组成项和明细证据。"),
            "trend_anomaly": ("趋势拐点", "先分析时间序列异常期，再补充切片结构变化。"),
            "dimension_slice": ("切片归因", "围绕当前透视表切片做同口径对比和维度下钻。"),
            "data_quality": ("数据质量", "优先检查缺失、重复、延迟和口径变化。"),
        }
        title, reason = mapping.get(anomaly_type, ("常规归因", "按指标波动、维度贡献和异常点顺序分析。"))
        return [{"type": anomaly_type, "title": title, "reason": reason}]

    @staticmethod
    def _normalize_context_anomaly_type(value: Any) -> str:
        raw = str(value or "").strip().lower()
        mapping = {
            "document": "document_trace",
            "document_trace": "document_trace",
            "metric_alert": "metric_spike",
            "metric_spike": "metric_spike",
            "metric_rise": "metric_rise",
            "metric_drop": "metric_drop",
            "trend": "trend_anomaly",
            "trend_anomaly": "trend_anomaly",
            "path": "trend_anomaly",
            "expression": "metric_spike",
            "self": "metric_spike",
            "data_quality": "data_quality",
            "dimension": "dimension_slice",
            "dimension_slice": "dimension_slice",
        }
        return mapping.get(raw, "")

    def _rule_based_time(self, question: str, today: datetime.date, y: int, w: int) -> dict:
        """规则提取时间信息。"""
        parsed = parse_question_time(question, today)
        if parsed:
            return parsed

        context_time = self._cell_context_time_info(today)
        if context_time:
            self._log(f"  使用异常单元格时间上下文: {context_time.get('time_desc')}")
            return context_time

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

    def _cell_context(self) -> dict[str, Any]:
        ctx = self._context.get("cellInsight")
        return ctx if isinstance(ctx, dict) else {}

    def _cell_context_filters(self) -> list[dict[str, Any]]:
        cell = self._cell_context()
        context = cell.get("cellContext") if isinstance(cell.get("cellContext"), dict) else {}
        filters = context.get("filters") if isinstance(context, dict) else []
        if not isinstance(filters, list):
            return []
        return [item for item in filters if isinstance(item, dict)]

    @staticmethod
    def _cell_filter_value(item: dict[str, Any]) -> str:
        return str(item.get("filterValue") or item.get("value") or "").strip()

    @staticmethod
    def _is_time_context_filter(item: dict[str, Any]) -> bool:
        code = str(item.get("code") or "").lower()
        name = str(item.get("name") or "")
        try:
            view_type = int(float(str(item.get("viewType") or 0)))
        except (TypeError, ValueError):
            view_type = 0
        value = str(item.get("value") or item.get("filterValue") or "")
        if 1 <= view_type <= 6:
            return True
        if any(token in name for token in ("日期", "时间", "日", "周", "月", "季度", "年")):
            return True
        if any(token in code for token in ("date", "day", "week", "month", "quarter", "year")):
            return True
        return bool(re.match(r"^\d{4}[-/]?\d{2}([-/]?\d{2})?$", value) or re.match(r"^\d{4}-?Q[1-4]$", value, re.I))

    def _cell_context_time_info(self, today: datetime.date) -> Optional[dict]:
        for item in self._cell_context_filters():
            if not self._is_time_context_filter(item):
                continue
            raw = self._cell_filter_value(item)
            name = str(item.get("name") or item.get("code") or "时间")
            try:
                if re.match(r"^\d{4}[-/]?\d{2}[-/]?\d{2}$", raw):
                    compact = raw.replace("/", "-")
                    if "-" not in compact:
                        compact = f"{compact[:4]}-{compact[4:6]}-{compact[6:8]}"
                    target = datetime.date.fromisoformat(compact)
                    prev = target - datetime.timedelta(days=1)
                    return {
                        "time_start": target.isoformat(),
                        "time_end": target.isoformat(),
                        "prev_start": prev.isoformat(),
                        "prev_end": prev.isoformat(),
                        "gran": "day",
                        "time_desc": f"{name}={raw}",
                    }
                if "周" in name or "week" in str(item.get("code") or "").lower() or re.match(r"^\d{4}[-/]W\d{1,2}$", raw, re.I):
                    match = re.match(r"^(\d{4})[-/]?W?(\d{1,2})$", raw, re.I)
                    if match:
                        year, week = int(match.group(1)), int(match.group(2))
                        prev_week = week - 1 if week > 1 else 52
                        prev_year = year if week > 1 else year - 1
                        start, end = _week_bounds(year, week)
                        prev_start, prev_end = _week_bounds(prev_year, prev_week)
                        return {
                            "time_start": start,
                            "time_end": end,
                            "prev_start": prev_start,
                            "prev_end": prev_end,
                            "gran": "week",
                            "time_desc": f"{name}={raw}",
                        }
                if re.match(r"^\d{4}[-/]?\d{2}$", raw):
                    compact = raw.replace("/", "-")
                    year = int(compact[:4])
                    month = int(compact[-2:])
                    first = datetime.date(year, month, 1)
                    if month == 12:
                        last = datetime.date(year, 12, 31)
                    else:
                        last = datetime.date(year, month + 1, 1) - datetime.timedelta(days=1)
                    prev_last = first - datetime.timedelta(days=1)
                    prev_first = prev_last.replace(day=1)
                    return {
                        "time_start": first.isoformat(),
                        "time_end": last.isoformat(),
                        "prev_start": prev_first.isoformat(),
                        "prev_end": prev_last.isoformat(),
                        "gran": "month",
                        "time_desc": f"{name}={raw}",
                    }
                match_q = re.match(r"^(\d{4})-?Q([1-4])$", raw, re.I)
                if match_q:
                    year, quarter = int(match_q.group(1)), int(match_q.group(2))
                    first_month = (quarter - 1) * 3 + 1
                    first = datetime.date(year, first_month, 1)
                    last_month = first_month + 2
                    last = (
                        datetime.date(year + 1, 1, 1) - datetime.timedelta(days=1)
                        if last_month == 12
                        else datetime.date(year, last_month + 1, 1) - datetime.timedelta(days=1)
                    )
                    prev_last = first - datetime.timedelta(days=1)
                    prev_first_month = ((prev_last.month - 1) // 3) * 3 + 1
                    prev_first = datetime.date(prev_last.year, prev_first_month, 1)
                    return {
                        "time_start": first.isoformat(),
                        "time_end": last.isoformat(),
                        "prev_start": prev_first.isoformat(),
                        "prev_end": prev_last.isoformat(),
                        "gran": "month",
                        "time_desc": f"{name}={raw}",
                    }
            except Exception as exc:
                self._log(f"  ⚠ 单元格时间上下文解析失败: {raw} ({exc})")
        return None

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
        IND = Namespace("http://indicator.insightmind.com/ontology#")

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
        cell_ctx = self._cell_context()
        cell_measure = cell_ctx.get("measure") if isinstance(cell_ctx.get("measure"), dict) else {}
        bound_measure_code = str(
            self._context.get("activeMeasureCode") or cell_measure.get("code") or ""
        ).strip()
        if not cell_ctx or bound_measure_code not in cache:
            bound_measure_code = ""

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

        if clean_tokens:
            self._log(f"  匹配候选 tokens: {clean_tokens}")

        preferred_tables = set(self._context.get("factTables") or [])
        semantic_candidates: list[Any] = []
        semantic_scored: list[tuple[str, int, str, str]] = []
        semantic_clarification: dict[str, Any] = {}
        if self._semantic_mapping_service is not None and (question or keywords):
            try:
                semantic_recall = self._semantic_mapping_service.search(
                    question or " ".join(keywords),
                    semantic_types={"measure"},
                    allowed_codes=set(cache),
                    allowed_tables=preferred_tables,
                    top_k=10,
                    include_vector=True,
                )
                semantic_candidates = list(semantic_recall.candidates or [])
                if semantic_candidates:
                    top = semantic_candidates[0]
                    def _matched_terms(candidate: Any) -> set[str]:
                        return {
                            normalize_text(str(getattr(evidence, "matched_text", "")))
                            for evidence in list(getattr(candidate, "evidence", []) or [])
                            if str(getattr(evidence, "match_type", "")) != "vector"
                            and normalize_text(str(getattr(evidence, "matched_text", "")))
                        }

                    ambiguous = False
                    if len(semantic_candidates) > 1 and semantic_candidates[1].score >= top.score - 0.06:
                        top_terms = _matched_terms(top)
                        second_terms = _matched_terms(semantic_candidates[1])
                        ambiguous = bool(top_terms & second_terms) or any(
                            left in right or right in left
                            for left in top_terms
                            for right in second_terms
                        )
                    if top.confidence == "high" and not ambiguous:
                        for candidate in semantic_candidates:
                            if candidate.confidence != "high" or candidate.code not in cache:
                                continue
                            if not _matched_terms(candidate):
                                continue
                            tables = self._kg_meas_table_cache.get(candidate.code, []) or [""]
                            table = next(
                                (name for name in tables if not preferred_tables or name in preferred_tables),
                                tables[0],
                            )
                            semantic_scored.append((
                                candidate.code,
                                1000 + int(candidate.score * 100),
                                cache[candidate.code]["cn_name"],
                                table,
                            ))
                        self._log(
                            f"  语义目录命中: {top.name} ({top.code})，"
                            f"来源={top.match_type}，置信度={top.confidence}"
                        )
                    elif top.confidence == "high" and ambiguous and not bound_measure_code:
                        names = "、".join(candidate.name for candidate in semantic_candidates[:3])
                        self._log(f"  ⚠ 语义目录候选冲突，需明确指标: {names}")
                        return {
                            "needs_clarification": True,
                            "semantic_mapping": {
                                "decision": "clarify",
                                "confidence": "medium",
                                "measureCandidates": [
                                    candidate.to_dict() for candidate in semantic_candidates[:5]
                                ],
                            },
                        }
                    elif (
                        top.confidence == "medium"
                        and top.match_type == "vector"
                        and (
                            len(semantic_candidates) == 1
                            or semantic_candidates[1].score < top.score - 0.06
                        )
                    ):
                        semantic_clarification = {
                            "needs_clarification": True,
                            "semantic_mapping": {
                                "decision": "clarify",
                                "confidence": "medium",
                                "measureCandidates": [
                                    candidate.to_dict() for candidate in semantic_candidates[:5]
                                ],
                            },
                        }
            except Exception as exc:
                self._log(f"  语义召回不可用，使用原匹配规则: {exc}")

        if not clean_tokens and not bound_measure_code and not semantic_scored:
            return semantic_clarification or None

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

        if bound_measure_code:
            info = cache[bound_measure_code]
            tbl = (self._kg_meas_table_cache.get(bound_measure_code, []) or [""])[0]
            self._log(f"  使用单元格上下文绑定指标: {info['cn_name']} ({bound_measure_code})")
            scored = [(bound_measure_code, 10000, info["cn_name"], tbl)]
            keyword_scored = _score_all_indicators(restrict_tables=True) if clean_tokens else []
            if not keyword_scored and clean_tokens:
                keyword_scored = _score_all_indicators(restrict_tables=False)
            keyword_scored = [*semantic_scored, *keyword_scored]
            scored.extend(item for item in keyword_scored if item[0] != bound_measure_code)
        elif semantic_scored:
            scored = list(semantic_scored)
            legacy_scored = _score_all_indicators(restrict_tables=True)
            if not legacy_scored:
                legacy_scored = _score_all_indicators(restrict_tables=False)
            semantic_codes = {item[0] for item in scored}
            scored.extend(item for item in legacy_scored if item[0] not in semantic_codes)
        else:
            # 第一轮：用上轮对话的事实表范围约束匹配
            scored = _score_all_indicators(restrict_tables=True)
            # 第二轮：表约束下无匹配时放开到全图谱（避免上轮指标跨域污染本轮问题）
            if not scored:
                if preferred_tables:
                    self._log("  表约束下无匹配，全图谱放开搜索...")
                scored = _score_all_indicators(restrict_tables=False)

        if semantic_clarification and not bound_measure_code and (
            not scored or scored[0][1] < 50
        ):
            return semantic_clarification

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
            "semantic_mapping": {
                "measureCandidates": [
                    candidate.to_dict() for candidate in semantic_candidates[:5]
                ],
            } if semantic_candidates else {},
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
        reg_dims   = self._prioritized_regular_dims(meas_info.get("dim_codes", []), intent)
        gran       = intent.get("gran", "week")
        time_start = intent.get("time_start", "")
        time_end   = intent.get("time_end", "")
        prev_start = intent.get("prev_start", "")
        prev_end   = intent.get("prev_end", "")

        # 选择时间维度
        time_dim_code = time_dims.get(gran) or time_dims.get("week") or time_dims.get("day") or ""
        day_dim_code  = time_dims.get("day", "")

        # 若找不到对应粒度的时间维，自动降级。过滤值的编码必须与实际
        # 选中的维度一致：例如月维缺失时，不能把 YYYYMM 传给周维。
        if not time_dim_code and gran == "month":
            time_dim_code = time_dims.get("week", "")
        if not time_dim_code:
            # 取任意时间维
            time_dim_code = next(iter(time_dims.values()), "")
        effective_gran = next(
            (level for level, code in time_dims.items() if code == time_dim_code),
            gran,
        )

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
                time_dim_code, effective_gran, time_start, time_end, prev_start, prev_end
            )
            if time_filter:
                filter_list.append(time_filter)
        filter_list.extend(self._cell_context_dimension_filters(time_dim_code))

        params = {
            "configureList": configure_list,
            "filterList":    filter_list,
            "pageSize":      500,
            "pageNum":       1,
            # Keep the semantic range as internal metadata. IndicatorAnalyzer
            # must not infer dates from six-digit codes because YYYYMM and
            # YYYYWW are indistinguishable without the selected granularity.
            "_timeRange": {
                "time_start": time_start,
                "time_end": time_end,
                "prev_start": prev_start,
                "prev_end": prev_end,
                "gran": effective_gran,
            },
        }
        if day_dim_code and effective_gran in ("week", "month"):
            params["_p2DayDim"] = day_dim_code
        params["_gran"] = effective_gran
        if effective_gran != gran:
            params["_requestedGran"] = gran
            self._log(f"  时间维度降级: {gran} → {effective_gran} ({time_dim_code})")
        if intent.get("anomaly_profile"):
            params["_anomalyProfile"] = intent.get("anomaly_profile")

        return params

    def _prioritized_regular_dims(self, dim_codes: list[str], intent: dict) -> list[str]:
        """Prioritize dimensions that are part of the current anomaly context."""
        if not dim_codes:
            return []
        priority: list[str] = []
        profile = intent.get("anomaly_profile") if isinstance(intent.get("anomaly_profile"), dict) else {}
        for item in profile.get("focus_filters") or []:
            if isinstance(item, dict):
                code = str(item.get("code") or "").strip()
                if code:
                    priority.append(code)

        cell = self._cell_context()
        for item in cell.get("contributions") or []:
            if isinstance(item, dict):
                code = str(item.get("dimensionCode") or "").strip()
                if code:
                    priority.append(code)

        seen: set[str] = set()
        ordered: list[str] = []
        for code in priority + list(dim_codes):
            if code in dim_codes and code not in seen:
                ordered.append(code)
                seen.add(code)
        return ordered

    def _cell_context_dimension_filters(self, time_dim_code: str = "") -> list[dict[str, Any]]:
        """Convert selected pivot cell row/column paths into DA dimension filters."""
        filters: list[dict[str, Any]] = []
        seen: set[str] = set()
        for item in self._cell_context_filters():
            code = str(item.get("code") or "").strip()
            value = self._cell_filter_value(item)
            if not code or not value or code == time_dim_code or code in seen:
                continue
            if self._is_time_context_filter(item):
                continue
            filters.append({
                "code": code,
                "operatorList": [{
                    "sqlOprType": 0,
                    "dataList": [value],
                    "sqlLogicalType": 0,
                    "timeRange": 0,
                }],
                "internal": True,
            })
            seen.add(code)
        if filters:
            self._log("  单元格维度过滤: " + json.dumps(filters, ensure_ascii=False))
        return filters

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
        - 日粒度：使用 ISO 日期边界 dataList + internal=true
        - 周粒度：使用周码 YYYYWW dataList + internal=true
        - 月粒度：使用月码 YYYYMM dataList + internal=true
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
                    cur = d - datetime.timedelta(days=d.weekday())
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
                "viewType":     1,
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
                    "viewType":     3,
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
        cell_context_text = self._cell_insight_prompt_context()

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
        anomaly_profile = intent.get("anomaly_profile") if isinstance(intent.get("anomaly_profile"), dict) else {}
        anomaly_lines = [
            f"异常类型：{anomaly_profile.get('label') or '未指定'}",
            f"异常来源：{anomaly_profile.get('source_label') or anomaly_profile.get('source') or 'unknown'}",
            f"异常形态：{anomaly_profile.get('shape_label') or anomaly_profile.get('shape') or 'unknown'}",
            f"异常方向：{anomaly_profile.get('direction') or 'unknown'}",
            (
                f"证据强度：{anomaly_profile.get('evidence_strength_label') or '-'}"
                f"（{anomaly_profile.get('evidence_strength', '-')}）"
            ),
            (
                f"识别置信度：{anomaly_profile.get('confidence_label') or '-'}"
                f"（{anomaly_profile.get('confidence', '-')}）"
            ),
            f"分析策略：{anomaly_profile.get('strategy') or '按常规指标波动归因流程分析。'}",
        ]
        if anomaly_profile.get("evidence"):
            anomaly_lines.append("异常上下文：" + "；".join(str(x) for x in anomaly_profile.get("evidence") or []))
        if anomaly_profile.get("evidence_items"):
            evidence_text = []
            for item in (anomaly_profile.get("evidence_items") or [])[:5]:
                if isinstance(item, dict):
                    evidence_text.append(
                        f"{item.get('label') or item.get('type')}: {item.get('detail') or ''}"
                    )
            if evidence_text:
                anomaly_lines.append("结构化证据：" + "；".join(evidence_text))
        if anomaly_profile.get("hypotheses"):
            hypothesis_text = []
            for item in (anomaly_profile.get("hypotheses") or [])[:4]:
                if isinstance(item, dict):
                    hypothesis_text.append(
                        f"{item.get('title') or item.get('type')}: {item.get('reason') or ''}"
                    )
            if hypothesis_text:
                anomaly_lines.append("诊断假设：" + "；".join(hypothesis_text))
        anomaly_text = "\n".join(anomaly_lines)

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
            "本次分析已经识别了异常类型。不同异常要用不同解释方式：\n"
            "- 先依据异常来源、异常形态、证据强度和置信度选择解释口径；证据弱时必须说明不确定性。\n"
            "- 指标下降/上升：讲清楚主要拉低或拉高的维度、算子和幅度。\n"
            "- 趋势异常/突变：先讲异常发生在哪些时间点，再讲对应切片。\n"
            "- 维度切片异常：围绕当前切片解释，不要只讲全局指标。\n"
            "- 单据级异常：优先引用真实单据、命中规则和异常字段，不套用总体波动模板。\n"
            "- 数据质量异常：优先说明缺失、重复、延迟或口径变化证据。\n"
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
            f"===== 异常画像 =====\n"
            f"{anomaly_text}\n\n"
            f"===== 综合分析报告 =====\n"
            f"{report_text}\n"
            f"===== 补充关键数字 ====={supp_text}"
            f"{cell_context_text}"
        )

        self._log(f"  直答段 prompt 长度: {len(user)} 字符")

        if not self._llm_config.get("api_key") or not self._llm_config.get("base_url"):
            self._log("  ⚠ 直答段 LLM 未配置，使用综合报告生成本地回答")
            yield self._fallback_direct_answer(question, cn_name, report_text, anomaly_profile)
            return

        # 流式输出
        try:
            yield from self._llm_stream(system, user, max_tokens=1200)
        except Exception as e:
            self._log(f"  ⚠ 直答段生成失败，使用综合报告生成本地回答: {e}")
            yield self._fallback_direct_answer(question, cn_name, report_text, anomaly_profile, str(e))

    @staticmethod
    def _fallback_direct_answer(
        question: str,
        cn_name: str,
        report_text: str,
        anomaly_profile: dict,
        reason: str = "",
    ) -> str:
        lines = [line.strip() for line in str(report_text or "").splitlines() if line.strip()]
        bullets = [line for line in lines if line.startswith("- ")]
        conclusion = bullets[0][2:] if bullets else f"已完成「{cn_name or '目标指标'}」分析，但当前返回数据不足，需要结合明细继续确认。"
        evidence = bullets[1:5] if len(bullets) > 1 else bullets[:1]
        suggestions = [line for line in bullets if any(token in line for token in ("下钻", "明细", "Trace", "扩大", "补充"))][-3:]
        if not suggestions:
            suggestions = ["- 沿关键维度继续下钻，查看变化是否集中在少数对象。", "- 补充明细样本，确认是否存在单据规则、数据质量或真实业务变化。"]
        uncertainty = ""
        if reason:
            uncertainty = f"\n\n> AI 直答暂不可用，以下回答由已生成报告规则化整理。原因：{reason}"
        elif anomaly_profile:
            uncertainty = (
                f"\n\n> 异常画像：{anomaly_profile.get('label') or '未指定'}；"
                f"置信度：{anomaly_profile.get('confidence_label') or '-'}。"
            )
        return (
            "## 结论\n"
            f"{conclusion}{uncertainty}\n\n"
            "## 关键证据\n"
            + "\n".join(evidence or ["- 当前没有足够的可量化证据，建议扩大查询范围后复核。"])
            + "\n\n## 建议排查方向\n"
            + "\n".join(suggestions)
        )

    def _cell_insight_prompt_context(self) -> str:
        """Build a compact evidence packet from the selected abnormal cell."""
        cell = self._cell_context()
        if not cell:
            return ""
        measure = cell.get("measure") if isinstance(cell.get("measure"), dict) else {}
        context = cell.get("cellContext") if isinstance(cell.get("cellContext"), dict) else {}
        anomaly = cell.get("anomaly") if isinstance(cell.get("anomaly"), dict) else {}
        diagnosis = cell.get("diagnosis") if isinstance(cell.get("diagnosis"), dict) else {}
        documents = cell.get("documents") if isinstance(cell.get("documents"), list) else []
        contributions = cell.get("contributions") if isinstance(cell.get("contributions"), list) else []

        lines = [
            "\n\n===== 异常单元格上下文（来自Dashboard点击） =====",
            f"指标：{measure.get('name') or measure.get('code') or ''}",
            f"单元格切片：{context.get('label') or ''}",
            f"单元格值：{cell.get('cellValue')}",
            f"异常摘要：{anomaly.get('title') or ''}；{anomaly.get('reason') or ''}",
            (
                "异常识别："
                f"来源={diagnosis.get('source') or anomaly.get('source') or ''}，"
                f"形态={diagnosis.get('shape') or anomaly.get('shape') or ''}，"
                f"证据强度={diagnosis.get('evidenceStrength') or anomaly.get('evidenceStrength') or ''}"
                f"({diagnosis.get('evidenceStrengthLabel') or anomaly.get('evidenceStrengthLabel') or ''})，"
                f"置信度={diagnosis.get('confidence') or anomaly.get('confidence') or ''}"
                f"({diagnosis.get('confidenceLabel') or anomaly.get('confidenceLabel') or ''})"
            ),
        ]
        evidence_items = diagnosis.get("evidence") if isinstance(diagnosis.get("evidence"), list) else []
        if evidence_items:
            lines.append("结构化证据：")
            for item in evidence_items[:6]:
                if isinstance(item, dict):
                    lines.append(f"- {item.get('label') or item.get('type')}: {item.get('detail') or ''}")
        hypotheses = diagnosis.get("hypotheses") if isinstance(diagnosis.get("hypotheses"), list) else []
        if hypotheses:
            lines.append("诊断假设：")
            for item in hypotheses[:4]:
                if isinstance(item, dict):
                    lines.append(f"- {item.get('title') or item.get('type')}: {item.get('reason') or ''}")
        if contributions:
            lines.append("推荐/贡献维度：")
            for item in contributions[:5]:
                if not isinstance(item, dict):
                    continue
                label = item.get("dimensionName") or item.get("dimensionCode") or ""
                value = item.get("value") or ""
                score = item.get("score")
                reason = item.get("reason") or ""
                lines.append(f"- {label}{'=' + str(value) if value else ''}，推荐度/占比={score}，原因：{reason}")

        if documents:
            first = documents[0] if isinstance(documents[0], dict) else {}
            lines.append(f"命中异常单据数量：{len(documents)}")
            lines.append("代表性异常单据（真实明细记录，请优先引用）：")
            lines.append(f"- 规则：{first.get('ruleName') or ''}")
            lines.append(f"- 单据号：{first.get('documentNo') or ''}")
            lines.append(f"- 异常字段：{first.get('fieldName') or first.get('field') or ''}={first.get('value')}")
            record = first.get("record") if isinstance(first.get("record"), dict) else {}
            if record:
                compact_record = {
                    str(k): v
                    for idx, (k, v) in enumerate(record.items())
                    if idx < 60 and v not in (None, "")
                }
                record_text = json.dumps(compact_record, ensure_ascii=False, default=str)
                lines.append(f"- 真实单据字段：{record_text[:6000]}")
            if len(documents) > 1:
                other_docs = []
                for doc in documents[1:6]:
                    if isinstance(doc, dict):
                        other_docs.append(
                            f"{doc.get('documentNo') or '-'}: {doc.get('fieldName') or doc.get('field') or ''}={doc.get('value')}"
                        )
                if other_docs:
                    lines.append("其它命中单据：" + "；".join(other_docs))

        lines.append("请在最终回答中结合这份异常单元格上下文，不要只给全局宏观分析。")
        return "\n".join(lines)


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
            with _urlopen(req, timeout=15) as resp:
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
            with _urlopen(req, timeout=15) as resp:
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
                with _urlopen(req, timeout=45) as resp:
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
                with _urlopen(req, timeout=45) as resp:
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
