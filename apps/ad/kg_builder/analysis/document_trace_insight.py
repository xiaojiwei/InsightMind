"""Document-level anomaly insight for matched business records."""
from __future__ import annotations

import json
import urllib.request as _ureq
from typing import Any, Callable, Generator, Optional


class DocumentTraceInsightAnalyzer:
    """Explain document trace anomalies without running metric fluctuation analysis."""

    def __init__(
        self,
        llm_config: dict,
        log_cb: Callable[[str], None],
        cancel_cb: Optional[Callable[[], bool]] = None,
        context: Optional[dict[str, Any]] = None,
    ) -> None:
        self._llm_config = llm_config or {}
        self._log = log_cb
        self._cancel_cb = cancel_cb or (lambda: False)
        self._context = context if isinstance(context, dict) else {}

    def analyze(self, question: str) -> Generator[dict[str, Any], None, None]:
        self._log("═══ 单据追踪 Insight 启动 ═══")
        evidence = self._evidence()
        if not evidence.get("documents"):
            self._log("  ⚠ 未收到异常单据明细，使用上下文摘要兜底")
        else:
            first = evidence["documents"][0]
            self._log(
                f"  ✓ 收到真实异常单据: {first.get('documentNo') or '-'} "
                f"{first.get('fieldName') or first.get('field') or ''}={first.get('value')}"
            )
        yield {"step": "insight_start"}
        if self._cancel_cb():
            return
        try:
            yield from self._stream_answer(question, evidence)
        except Exception as exc:
            self._log(f"  ⚠ 单据追踪 LLM 分析失败: {exc}，使用本地解释")
            yield {"insight_text": self._local_answer(evidence)}
        yield {"step": "done"}
        self._log("═══ 单据追踪 Insight 完成 ═══")

    def _evidence(self) -> dict[str, Any]:
        cell = self._context.get("cellInsight")
        cell = cell if isinstance(cell, dict) else {}
        documents = cell.get("documents") if isinstance(cell.get("documents"), list) else []
        return {
            "analysisMode": "document_trace",
            "measure": cell.get("measure") if isinstance(cell.get("measure"), dict) else {},
            "cellContext": cell.get("cellContext") if isinstance(cell.get("cellContext"), dict) else {},
            "cellValue": cell.get("cellValue"),
            "anomaly": cell.get("anomaly") if isinstance(cell.get("anomaly"), dict) else {},
            "contributions": cell.get("contributions") if isinstance(cell.get("contributions"), list) else [],
            "documents": documents,
            "selectedDocument": self._context.get("selectedDocument") or (documents[0] if documents else {}),
            "summary": cell.get("summary") or "",
        }

    @staticmethod
    def _compact_record(record: dict[str, Any], limit: int = 60) -> dict[str, Any]:
        priority_keywords = (
            "订单", "order", "数量", "qty", "标价", "售价", "价格", "price", "金额", "amount",
            "折扣", "discount", "优惠", "coupon", "促销", "promo", "城市", "city", "仓库",
            "warehouse", "客户", "customer", "商品", "item", "状态", "status", "日期", "date",
        )
        out: dict[str, Any] = {}
        ranked = sorted(
            record.items(),
            key=lambda pair: (
                not any(token in str(pair[0]).lower() or token in str(pair[0]) for token in priority_keywords),
                str(pair[0]),
            ),
        )
        for idx, (key, value) in enumerate(ranked):
            if idx >= limit:
                break
            if value not in (None, ""):
                out[str(key)] = value
        return out

    def _prompt_payload(self, evidence: dict[str, Any]) -> dict[str, Any]:
        docs = []
        for item in (evidence.get("documents") or [])[:12]:
            if not isinstance(item, dict):
                continue
            record = item.get("record") if isinstance(item.get("record"), dict) else {}
            docs.append({
                "ruleName": item.get("ruleName"),
                "severity": item.get("severity"),
                "documentNo": item.get("documentNo"),
                "field": item.get("fieldName") or item.get("field"),
                "value": item.get("value"),
                "record": self._compact_record(record),
            })
        payload = dict(evidence)
        payload["documents"] = docs
        selected = payload.get("selectedDocument")
        if isinstance(selected, dict) and isinstance(selected.get("record"), dict):
            payload["selectedDocument"] = {
                **selected,
                "record": self._compact_record(selected["record"]),
            }
        return payload

    def _stream_answer(self, question: str, evidence: dict[str, Any]) -> Generator[dict[str, str], None, None]:
        system = (
            "你是一名资深业务数据诊断专家，当前任务是分析单据级异常，不是指标波动归因。\n"
            "必须严格基于输入的真实明细、命中规则、异常字段和单元格上下文判断，不要套用环比/同比波动模板。\n"
            "如果证据不足，要明确说明还缺哪些字段或样本。\n\n"
            "回答必须完整收尾，不要以半句、半个字段名或未闭合的代码格式结束；字段名尽量使用中文业务名称。\n\n"
            "输出 Markdown，结构固定为：\n"
            "## 异常结论\n"
            "- 直接指出命中的规则、异常字段、异常值、代表性单据。\n\n"
            "## 真实单据证据\n"
            "- 引用关键字段，例如订单号、数量、售价、折扣金额、销售金额、城市、仓库、促销/优惠字段。\n\n"
            "## 业务解释\n"
            "- 根据字段组合判断可能原因，例如正常无折扣、促销规则缺失、默认值、数据同步、规则配置过宽/过窄。\n"
            "- 不要把单据异常解释成总体指标涨跌，除非输入证据支持。\n\n"
            "## 建议下一步\n"
            "- 给出 3 条可执行动作：查订单链路、查促销/优惠、查同类单据、调整规则等。\n"
        )
        user = (
            f"用户问题：{question}\n\n"
            "===== 单据追踪异常证据 =====\n"
            f"{json.dumps(self._prompt_payload(evidence), ensure_ascii=False, default=str)[:30000]}"
        )
        self._log(f"  单据追踪 prompt 长度: {len(user)} 字符")
        yield from self._llm_stream(system, user, max_tokens=2600)

    def _local_answer(self, evidence: dict[str, Any]) -> str:
        measure = evidence.get("measure") or {}
        context = evidence.get("cellContext") or {}
        docs = evidence.get("documents") or []
        first = docs[0] if docs else {}
        record = first.get("record") if isinstance(first.get("record"), dict) else {}
        field = first.get("fieldName") or first.get("field") or "异常字段"
        value = first.get("value")
        lines = [
            "## 异常结论",
            f"- {measure.get('name') or measure.get('code') or '当前指标'} 在 {context.get('label') or '当前切片'} 命中单据级异常。",
        ]
        if first:
            lines.append(f"- 代表性单据：{first.get('documentNo') or '-'}，{field}={value}，规则：{first.get('ruleName') or '单据追踪'}。")
        lines.extend(["", "## 真实单据证据"])
        for key in ["订单编号", "数量", "标价", "售价", "折扣金额", "销售金额", "优惠券金额", "城市", "仓库标识", "促销编号"]:
            if key in record:
                lines.append(f"- {key}：{record.get(key)}")
        lines.extend([
            "",
            "## 业务解释",
            f"- 当前异常集中在字段「{field}」取值为「{value}」。需要结合规则含义判断：如果规则认为该字段不能为该值，则应继续核对订单、促销、优惠和数据同步链路。",
            "- 这类问题属于单据诊断，不应直接套用总体指标波动结论。",
            "",
            "## 建议下一步",
            "- 打开该订单完整明细，检查促销编号、优惠券金额、订单状态和价格字段是否一致。",
            "- 抽取同城市、同月份、同商品或同仓库的相邻订单，判断该值是否为常见业务场景。",
            "- 如果大量正常订单也满足该规则，需要回到规则管理调整单据追踪条件。",
        ])
        return "\n".join(lines)

    def _llm_stream(self, system: str, user: str, max_tokens: int = 1200) -> Generator[dict[str, str], None, None]:
        api_key = self._llm_config.get("api_key", "")
        base_url = self._llm_config.get("base_url", "").rstrip("/")
        model = self._llm_config.get("model", "GPT5.5")
        is_anthropic = "anthropic" in base_url.lower()
        if not api_key or not base_url:
            raise ValueError("LLM 配置缺失")

        if is_anthropic:
            body = json.dumps({
                "model": model,
                "max_tokens": max_tokens,
                "system": system,
                "messages": [{"role": "user", "content": user}],
            }).encode("utf-8")
            headers = {"Content-Type": "application/json", "x-api-key": api_key, "anthropic-version": "2023-06-01"}
            req = _ureq.Request(f"{base_url}/messages", data=body, headers=headers, method="POST")
            with _ureq.urlopen(req, timeout=90) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            text = data.get("content", [{}])[0].get("text", "") if isinstance(data.get("content"), list) else str(data)
            truncated = data.get("stop_reason") == "max_tokens"
            yield {"insight_text": self._finalize_text(text, truncated)}
            return

        body = json.dumps({
            "model": model,
            "max_tokens": max_tokens,
            "messages": [{"role": "user", "content": system + "\n\n" + user}],
        }).encode("utf-8")
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"}
        req = _ureq.Request(f"{base_url}/chat/completions", data=body, headers=headers, method="POST")
        with _ureq.urlopen(req, timeout=90) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        if "choices" in data:
            choice = data["choices"][0]
            truncated = choice.get("finish_reason") == "length"
            yield {"insight_text": self._finalize_text(choice["message"]["content"], truncated)}
        else:
            yield {"insight_text": self._finalize_text(str(data), False)}

    @staticmethod
    def _finalize_text(text: str, truncated: bool = False) -> str:
        text = (text or "").strip()
        if not text:
            return text
        sentence_end = max(text.rfind(mark) for mark in ("。", "！", "？", ".", "!", "?", "\n## "))
        tail = text[sentence_end + 1:].strip() if sentence_end >= 0 else text
        looks_incomplete = truncated or (
            len(tail) > 24 and not text.endswith(("。", "！", "？", ".", "!", "?", "）", "】"))
        )
        if looks_incomplete and sentence_end > 0:
            text = text[:sentence_end + 1].rstrip()
        if truncated:
            text += "\n\n> 注：原始回答达到长度上限，系统已裁掉未完成尾句。建议缩小样本或继续追问某一条单据。"
        return text
