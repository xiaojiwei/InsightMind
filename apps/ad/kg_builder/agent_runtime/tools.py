"""Adapters that expose existing Insight analyzers as governed runtime tools."""

from __future__ import annotations

from typing import Any

from .contracts import ToolDefinition, ToolInvocation


def analysis_compile_tool(
    *,
    data_agent_url: str,
    ttl_path: str,
    semantic_mapping_service_factory,
) -> ToolDefinition:
    def handler(arguments: dict[str, Any], _invocation: ToolInvocation) -> dict[str, Any]:
        from kg_builder.nlq.service import NaturalLanguageQueryService

        question = str(arguments.get("question") or "").strip()
        if not question:
            raise ValueError("question 不能为空")
        service = NaturalLanguageQueryService(
            ttl_path=ttl_path,
            data_agent_url=data_agent_url,
            semantic_mapping_service=semantic_mapping_service_factory(),
        )
        compiled = service.compile(
            question,
            query_mode=str(arguments.get("queryMode") or "auto"),
            context=dict(arguments.get("context") or {}),
            is_follow_up=bool(arguments.get("isFollowUp")),
        )
        return compiled.result.to_dict()

    return ToolDefinition(
        name="analysis.compile",
        description="Compile a natural-language request into a governed AnalysisSpec without querying data.",
        handler=handler,
        timeout_seconds=60,
    )


def nlq_query_tool(
    *,
    data_agent_url: str,
    ttl_path: str,
    semantic_mapping_service_factory,
) -> ToolDefinition:
    def handler(arguments: dict[str, Any], invocation: ToolInvocation) -> dict[str, Any]:
        from kg_builder.nlq.service import NaturalLanguageQueryService

        question = str(arguments.get("question") or "").strip()
        if not question:
            raise ValueError("question 不能为空")
        service = NaturalLanguageQueryService(
            ttl_path=ttl_path,
            data_agent_url=data_agent_url,
            semantic_mapping_service=semantic_mapping_service_factory(),
        )
        return service.query(
            question,
            trace_id=invocation.request_context.trace_id,
            execute=True,
            page_size=min(max(int(arguments.get("pageSize") or 100), 1), 1000),
            page_num=max(int(arguments.get("pageNum") or 1), 1),
            max_dimensions=min(max(int(arguments.get("maxDimensions") or 3), 0), 5),
            query_mode=str(arguments.get("queryMode") or "auto"),
            context=dict(arguments.get("context") or {}),
            is_follow_up=bool(arguments.get("isFollowUp")),
        )

    return ToolDefinition(
        name="nlq.query",
        description="Run an NLQ request through the existing compiled and policy-gated semantic layer.",
        handler=handler,
        timeout_seconds=120,
    )


def metric_diagnosis_tool(
    *,
    data_agent_url: str,
    ttl_path: str,
    llm_config_factory,
    semantic_mapping_service_factory,
) -> ToolDefinition:
    def handler(arguments: dict[str, Any], invocation: ToolInvocation) -> dict[str, Any]:
        from kg_builder.analysis.insight_analyzer import InsightAnalyzer

        question = str(arguments.get("question") or "").strip()
        if not question:
            raise ValueError("question 不能为空")
        analyzer = InsightAnalyzer(
            data_agent_url=data_agent_url,
            ttl_path=ttl_path,
            llm_config=llm_config_factory(),
            log_cb=lambda message: invocation.emit_legacy({"log": message}),
            cancel_cb=invocation.cancelled,
            context=dict(arguments.get("context") or {}),
            semantic_mapping_service=semantic_mapping_service_factory(),
        )
        event_count = 0
        for event in analyzer.analyze(question):
            if invocation.cancelled():
                break
            invocation.emit_legacy(event)
            event_count += 1
        return {"eventCount": event_count, "cancelled": invocation.cancelled()}

    return ToolDefinition(
        name="insight.metric_diagnosis",
        description="Run the governed metric fluctuation and contribution diagnosis workflow.",
        handler=handler,
        timeout_seconds=300,
    )


def document_trace_tool(*, llm_config_factory) -> ToolDefinition:
    def handler(arguments: dict[str, Any], invocation: ToolInvocation) -> dict[str, Any]:
        from kg_builder.analysis.document_trace_insight import DocumentTraceInsightAnalyzer

        question = str(arguments.get("question") or "").strip()
        if not question:
            raise ValueError("question 不能为空")
        analyzer = DocumentTraceInsightAnalyzer(
            llm_config=llm_config_factory(),
            log_cb=lambda message: invocation.emit_legacy({"log": message}),
            cancel_cb=invocation.cancelled,
            context=dict(arguments.get("context") or {}),
        )
        event_count = 0
        for event in analyzer.analyze(question):
            if invocation.cancelled():
                break
            invocation.emit_legacy(event)
            event_count += 1
        return {"eventCount": event_count, "cancelled": invocation.cancelled()}

    return ToolDefinition(
        name="insight.document_trace",
        description="Run the governed document trace insight workflow.",
        handler=handler,
        timeout_seconds=300,
    )
