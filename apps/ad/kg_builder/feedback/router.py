"""Read/query API and explicit feedback ingestion endpoints."""

from __future__ import annotations

import logging
import os
from pathlib import Path
import secrets
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Query
from pydantic import BaseModel, Field

from .service import record_client_event
from .store import store
from .sanitizer import sanitize_payload, sanitize_text
from kg_builder.semantic_retrieval import (
    get_semantic_mapping_service,
    reset_semantic_mapping_registry,
)
from kg_builder.semantic_retrieval.normalizer import (
    is_safe_dimension_value,
    is_sensitive_dimension,
    normalize_text,
)


logger = logging.getLogger(__name__)
_APP_DIR = Path(__file__).resolve().parents[2]
_BUSINESS_KG_PATH = _APP_DIR / "output" / "business_kg" / "indicator-data.ttl"


def _require_feedback_token(
    feedback_token: str = Header("", alias="X-InsightMind-Feedback-Token"),
    legacy_review_token: str = Header("", alias="X-InsightMind-Review-Token"),
) -> str:
    expected = (
        os.getenv("INSIGHTMIND_FEEDBACK_API_TOKEN", "").strip()
        or os.getenv("SEMANTIC_DICTIONARY_REVIEW_TOKEN", "").strip()
    )
    if not expected:
        raise HTTPException(status_code=503, detail="反馈管理 API 未配置访问凭证")
    presented = feedback_token or legacy_review_token
    if not presented or not secrets.compare_digest(presented, expected):
        raise HTTPException(status_code=403, detail="反馈管理凭证无效")
    return os.getenv("INSIGHTMIND_FEEDBACK_ACTOR", "feedback-token").strip() or "feedback-token"


def _feedback_actor() -> str:
    return os.getenv("INSIGHTMIND_FEEDBACK_ACTOR", "feedback-token").strip() or "feedback-token"


router = APIRouter(
    prefix="/api/feedback",
    tags=["feedback"],
    dependencies=[Depends(_require_feedback_token)],
)


def _mysql_unavailable(exc: Exception) -> HTTPException:
    logger.exception("Feedback MySQL request failed")
    return HTTPException(status_code=503, detail="反馈 MySQL 暂不可用")


class ClientFeedbackEvent(BaseModel):
    eventId: str = ""
    traceId: str = ""
    eventType: str
    eventName: str
    source: str = "web"
    dedupeKey: str = ""
    payload: dict[str, Any] = Field(default_factory=dict)


class MemoryReviewRequest(BaseModel):
    action: str
    reasonCode: str = ""
    comment: str = ""
    actor: str = "reviewer"


class EvalCaseRequest(BaseModel):
    name: str = ""
    category: str = "regression"
    priority: str = "P1"


class DictionaryProposalRequest(BaseModel):
    semanticType: str
    term: str
    canonicalCode: str = ""
    dimensionCode: str = ""
    canonicalValue: str = ""
    domainCode: str = "default"
    sourceTraceId: str = ""
    metadata: dict[str, Any] = Field(default_factory=dict)


class DictionaryReviewRequest(BaseModel):
    action: str
    actor: str = "reviewer"
    comment: str = ""


def _validated_dictionary_target(payload: dict[str, Any]) -> tuple[dict[str, Any], str]:
    service = get_semantic_mapping_service(_BUSINESS_KG_PATH)
    snapshot = service.snapshot
    semantic_type = str(payload.get("semanticType") or "").strip().lower()
    canonical_code = str(payload.get("canonicalCode") or "").strip()
    dimension_code = str(payload.get("dimensionCode") or "").strip()
    canonical_value = str(payload.get("canonicalValue") or "").strip()
    target_code = dimension_code if semantic_type == "value" else canonical_code
    item = snapshot.items.get(target_code)
    expected_type = "dimension" if semantic_type == "value" else semantic_type
    if semantic_type not in {"measure", "dimension", "value"}:
        raise ValueError("semanticType 仅支持 measure/dimension/value")
    if item is None or item.semantic_type != expected_type:
        raise ValueError("canonicalCode/dimensionCode 不存在或类型不匹配")
    if semantic_type == "value":
        if item.is_time:
            raise ValueError("时间维度不允许维护普通维值别名")
        if is_sensitive_dimension(item.code, item.cn_name, item.en_name):
            raise ValueError("敏感维度不允许维护在线维值别名")
        if not is_safe_dimension_value(payload.get("term")) or not is_safe_dimension_value(canonical_value):
            raise ValueError("维值不符合隐私或长度规则")
    return item.to_dict(), snapshot.graph_hash


@router.post("/events")
def create_event(req: ClientFeedbackEvent):
    try:
        item = record_client_event(
            event_type=req.eventType, event_name=req.eventName, trace_id=req.traceId,
            payload=req.payload, source=req.source, event_id=req.eventId, dedupe_key=req.dedupeKey,
        )
        return {"ok": True, "event": item}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc


@router.get("/events")
def list_events(
    eventType: str = "", eventName: str = "", errorCode: str = "", search: str = "",
    page: int = Query(1, ge=1), pageSize: int = Query(50, ge=1, le=200),
):
    try:
        result = store.list_events(
            event_type=eventType, event_name=eventName, error_code=errorCode, search=search,
            limit=pageSize, offset=(page - 1) * pageSize,
        )
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
    return {**result, "page": page, "pageSize": pageSize}


@router.get("/summary")
def summary():
    try:
        return store.summary()
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc


@router.get("/traces/{trace_id}")
def trace_detail(trace_id: str):
    try:
        trace = store.get_trace(trace_id)
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
    if not trace:
        raise HTTPException(status_code=404, detail="Trace 不存在")
    return trace


@router.get("/memories")
def list_memories(
    status: str = "", search: str = "",
    page: int = Query(1, ge=1), pageSize: int = Query(30, ge=1, le=200),
):
    try:
        result = store.list_memories(
            status=status, search=search, limit=pageSize, offset=(page - 1) * pageSize,
        )
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
    return {**result, "page": page, "pageSize": pageSize}


@router.post("/memories/{memory_id}/review")
def review_memory(memory_id: str, req: MemoryReviewRequest):
    try:
        item = store.review_memory(
            memory_id,
            action=req.action,
            reason_code=req.reasonCode,
            comment=req.comment,
            actor=_feedback_actor(),
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
    if not item:
        raise HTTPException(status_code=404, detail="样本不存在")
    return {"ok": True, "memory": item}


@router.post("/traces/{trace_id}/eval-cases")
def create_eval_case(trace_id: str, req: EvalCaseRequest):
    priority = req.priority.upper()
    if priority not in {"P0", "P1", "P2", "P3"}:
        raise HTTPException(status_code=400, detail="优先级仅支持 P0/P1/P2/P3")
    try:
        item = store.create_eval_case_from_trace(
            trace_id,
            case_name=req.name,
            category=req.category,
            priority=priority,
        )
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
    if not item:
        raise HTTPException(status_code=404, detail="Trace 或语义计划不存在")
    return {"ok": True, "case": item}


@router.get("/eval-cases")
def list_eval_cases(
    category: str = "", priority: str = "",
    page: int = Query(1, ge=1), pageSize: int = Query(50, ge=1, le=200),
):
    try:
        result = store.list_eval_cases(
            category=category, priority=priority,
            limit=pageSize, offset=(page - 1) * pageSize,
        )
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
    return {**result, "page": page, "pageSize": pageSize}


@router.post("/dictionary/proposals")
def create_dictionary_proposal(req: DictionaryProposalRequest):
    payload = req.model_dump() if hasattr(req, "model_dump") else req.dict()
    try:
        target, graph_hash = _validated_dictionary_target(payload)
        term = sanitize_text(req.term, max_chars=500).strip()
        normalized = normalize_text(term)
        if len(normalized) < 2:
            raise ValueError("term 规范化后至少需要 2 个字符")
        proposal_metadata = sanitize_payload(req.metadata)
        for unsafe_key in (
            "filterSafe", "filter_safe", "executable", "internal",
            "permission", "permissionScope", "permission_scope",
        ):
            proposal_metadata.pop(unsafe_key, None)
        item = store.create_dictionary_proposal({
            "semantic_type": req.semanticType.lower(),
            "term_text": term,
            "normalized_term": normalized,
            "canonical_code": sanitize_text(req.canonicalCode, max_chars=191),
            "dimension_code": sanitize_text(req.dimensionCode, max_chars=191),
            "canonical_value": sanitize_text(req.canonicalValue, max_chars=500),
            "domain_code": sanitize_text(req.domainCode or "default", max_chars=100),
            "source": "feedback_proposal",
            "source_trace_id": sanitize_text(req.sourceTraceId, max_chars=100),
            "business_kg_hash": graph_hash,
            "metadata": {
                **proposal_metadata,
                "validatedTarget": {
                    "code": target.get("code"),
                    "name": target.get("cnName"),
                    "semanticType": target.get("semanticType"),
                },
            },
        })
        return {"ok": True, "proposal": item}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc


@router.get("/dictionary")
def list_dictionary_entries(
    status: str = "", semanticType: str = "", search: str = "",
    page: int = Query(1, ge=1), pageSize: int = Query(50, ge=1, le=200),
):
    try:
        result = store.list_dictionary_entries(
            status=status,
            semantic_type=semanticType,
            search=search,
            limit=pageSize,
            offset=(page - 1) * pageSize,
        )
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
    return {**result, "page": page, "pageSize": pageSize}


@router.post("/dictionary/{entry_id}/review")
def review_dictionary_entry(
    entry_id: str,
    req: DictionaryReviewRequest,
):
    try:
        current = store.get_dictionary_entry(entry_id)
        if not current:
            raise HTTPException(status_code=404, detail="字典提案不存在")
        if req.action.upper() == "ENABLED":
            _target, graph_hash = _validated_dictionary_target(current)
            if current.get("businessKgHash") != graph_hash:
                store.review_dictionary_entry(
                    entry_id,
                    action="STALE",
                    actor=_feedback_actor(),
                    comment="业务知识图谱版本已变化，请重新创建并审核提案",
                )
                raise HTTPException(status_code=409, detail="业务知识图谱版本已变化，提案已标记 STALE")
        item = store.review_dictionary_entry(
            entry_id,
            action=req.action,
            actor=_feedback_actor(),
            comment=sanitize_text(req.comment, max_chars=1000),
        )
        reset_semantic_mapping_registry()
        return {"ok": True, "entry": item}
    except HTTPException:
        raise
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise _mysql_unavailable(exc) from exc
