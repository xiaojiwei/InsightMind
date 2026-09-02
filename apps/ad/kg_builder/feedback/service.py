"""Application service for recording usage feedback without changing the graph."""

from __future__ import annotations

import logging
import os
import uuid
from pathlib import Path
from typing import Any, Optional

from .classifier import classify_error
from .graph_version import graph_identity
from .sanitizer import sanitize_payload, sanitize_text, sha256_text
from .schema_snapshot import build_snapshot, diff_snapshots
from .semantic_contract import build_semantic_contract, compatible_memory_status
from .store import store, utc_now


logger = logging.getLogger(__name__)
_APP_DIR = Path(__file__).resolve().parents[2]
_BKG_PATH = _APP_DIR / "output" / "business_kg" / "indicator-data.ttl"


def enabled() -> bool:
    return os.getenv("FEEDBACK_ENABLED", "true").strip().lower() not in {"0", "false", "no", "off"}


def init_feedback_store() -> None:
    if enabled():
        store.init()


def begin_query_trace(
    trace_id: str, question: str, *, conversation_id: str = "", parent_trace_id: str = "",
    source: str = "nlq",
) -> None:
    if not enabled():
        return
    try:
        graph = graph_identity(_BKG_PATH)
        safe_question = sanitize_text(question, max_chars=4000)
        store.begin_trace({
            "trace_id": trace_id,
            "parent_trace_id": parent_trace_id,
            "conversation_id": conversation_id,
            "source": source,
            "question_text": safe_question,
            "question_hash": sha256_text(question),
            "business_kg_hash": graph["sha256"],
            "business_kg_file": graph["filename"],
            "ontology_version": graph["ontologyVersion"],
            "created_at": utc_now(),
        })
    except Exception:
        logger.exception("Feedback trace start failed")


def _result_row_count(result: dict[str, Any]) -> Optional[int]:
    detail = result.get("detailData") or {}
    if isinstance(detail.get("records"), list):
        return len(detail["records"])
    summary = result.get("summary") or {}
    for key in ("returnedRows", "rowCount", "matchedRows"):
        if summary.get(key) is not None:
            try:
                return int(summary[key])
            except (TypeError, ValueError):
                pass
    data = ((result.get("result") or {}).get("data") or {})
    rows = data.get("cellList") or data.get("list")
    return len(rows) if isinstance(rows, list) else None


def _persist_semantic_contract(
    *, trace_id: str, source: str, question: str, query_mode: str, status: str,
    semantic_context: Optional[dict[str, Any]] = None,
    result: Optional[dict[str, Any]] = None,
    graph: Optional[dict[str, Any]] = None,
    operation: str = "",
    domain_code: str = "default",
) -> Optional[dict[str, Any]]:
    """Persist the v1 plan beside the trace without affecting query execution."""
    if not hasattr(store, "save_semantic_plan"):
        return None
    graph = graph or graph_identity(_BKG_PATH)
    contract = build_semantic_contract(
        trace_id=trace_id,
        source=source,
        question=question,
        query_mode=query_mode,
        status=status,
        semantic_context=semantic_context,
        result=result,
        graph=graph,
        operation=operation,
    )
    plan_id = store.save_semantic_plan({
        "trace_id": trace_id,
        "source": source,
        "status": status,
        "plan_hash": contract["planHash"],
        "plan": contract["plan"],
        "explain_plan": contract["explainPlan"],
        "created_at": utc_now(),
    })
    if hasattr(store, "replace_correction_steps"):
        store.replace_correction_steps(trace_id, plan_id, contract["corrections"])
    if contract["eligibleForMemory"] and hasattr(store, "create_pending_memory"):
        versions = contract["plan"].get("versions") or {}
        store.create_pending_memory({
            "trace_id": trace_id,
            "plan_id": plan_id,
            "question_hash": sha256_text(question),
            "domain_code": domain_code,
            "plan_hash": contract["planHash"],
            "business_kg_hash": versions.get("businessKgHash") or "",
            "schema_snapshot_id": versions.get("schemaSnapshotId") or "",
            "permission_scope_hash": "",
            "quality_score": 1.0 if status == "succeeded" else 0.8,
            "created_at": utc_now(),
        })
    return contract


def complete_query_trace(
    trace_id: str, result: dict[str, Any], *, conversation_id: str = "", source: str = "nlq",
) -> None:
    if not enabled() or not trace_id:
        return
    try:
        matched = result.get("matched") or {}
        diagnostic = str(result.get("diagnosticCode") or "")
        error = result.get("error") or result.get("clarification") or ""
        error_code = "" if result.get("ok") else classify_error(error, diagnostic)
        row_count = _result_row_count(result)
        if result.get("ok") and row_count == 0:
            status, event_name = "empty", "QUERY_EMPTY"
        elif result.get("ok"):
            status, event_name = "succeeded", "QUERY_SUCCEEDED"
        elif result.get("needsClarification") or result.get("action") == "clarify":
            status, event_name = "needs_clarification", "QUERY_NEEDS_CLARIFICATION"
        elif result.get("action") == "reject":
            status, event_name = "rejected", "QUERY_REJECTED"
        else:
            status, event_name = "failed", "QUERY_FAILED"
        measure_codes = []
        if matched.get("measureCode"):
            measure_codes.append(str(matched["measureCode"]))
        dimension_codes = [str(code) for code in (matched.get("dimensionCodes") or []) if code]
        fact_tables = [str(value) for value in (matched.get("factTables") or []) if value]
        store.complete_trace(trace_id, {
            "conversation_id": conversation_id,
            "source": source,
            "query_mode": result.get("queryMode") or "",
            "status": status,
            "diagnostic_code": diagnostic,
            "measure_codes": measure_codes,
            "dimension_codes": dimension_codes,
            "fact_tables": fact_tables,
            "result_row_count": row_count,
            "elapsed_ms": result.get("elapsedMs"),
            "error_code": error_code,
            "error_message": sanitize_text(error, max_chars=1000),
            "completed_at": utc_now(),
        })
        store.add_event({
            "event_id": f"evt_{uuid.uuid4().hex}",
            "trace_id": trace_id,
            "event_type": "execution",
            "event_name": event_name,
            "source": source,
            "payload": {
                "status": status, "diagnosticCode": diagnostic, "errorCode": error_code,
                "rowCount": row_count, "elapsedMs": result.get("elapsedMs"),
                "validationStatus": (result.get("validation") or {}).get("status"),
                "analysisSpecHash": result.get("analysisSpecHash") or "",
                "planStatus": result.get("planStatus") or "",
            },
            "dedupe_key": f"execution:{trace_id}",
        })
        _persist_semantic_contract(
            trace_id=trace_id,
            source=source,
            question=str(result.get("question") or ""),
            query_mode=str(result.get("queryMode") or ""),
            status=status,
            semantic_context={
                "measureCodes": measure_codes,
                "dimensionCodes": dimension_codes,
                "factTables": fact_tables,
            },
            result={**result, "resultRowCount": row_count},
            domain_code="nlq",
        )
    except Exception:
        logger.exception("Feedback trace completion failed")


def record_client_event(
    *, event_type: str, event_name: str, trace_id: str = "", payload: Optional[dict[str, Any]] = None,
    source: str = "web", event_id: str = "", dedupe_key: str = "",
) -> dict[str, Any]:
    if not enabled():
        return {"ok": True, "disabled": True}
    if event_type not in {"explicit", "behavior"}:
        raise ValueError("浏览器只允许提交 explicit 或 behavior 反馈")
    item = store.add_event({
        "event_id": event_id or f"evt_{uuid.uuid4().hex}",
        "trace_id": trace_id,
        "event_type": event_type,
        "event_name": event_name[:80],
        "source": source[:40],
        "payload": sanitize_payload(payload or {}),
        "dedupe_key": dedupe_key or event_id or f"{event_type}:{event_name}:{trace_id}:{uuid.uuid4().hex}",
    })
    memory_status = compatible_memory_status(event_name)
    if memory_status and trace_id and hasattr(store, "review_memory_by_trace"):
        safe_payload = sanitize_payload(payload or {})
        store.review_memory_by_trace(
            trace_id,
            action=memory_status,
            reason_code=sanitize_text(safe_payload.get("reasonCode"), max_chars=100),
            comment=sanitize_text(safe_payload.get("comment"), max_chars=1000),
            actor="explicit_feedback",
            payload={"eventName": event_name},
        )
    return item


def record_backend_request(
    *, request_id: str, method: str, path: str, category: str, operation: str,
    resource_id: str = "", status_code: int = 200, elapsed_ms: int = 0,
    error_code: str = "",
    semantic_context: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    """Record a backend execution without retaining request bodies or query parameters."""
    if not enabled():
        return {"ok": True, "disabled": True}
    try:
        graph = graph_identity(_BKG_PATH)
        succeeded = int(status_code) < 400
        event_name = f"{category.upper()}_API_{'SUCCEEDED' if succeeded else 'FAILED'}"
        semantic_context = semantic_context or {}
        measure_codes = [
            sanitize_text(code, max_chars=255)
            for code in (semantic_context.get("measureCodes") or [])[:50]
            if code
        ]
        dimension_codes = [
            sanitize_text(code, max_chars=255)
            for code in (semantic_context.get("dimensionCodes") or [])[:50]
            if code
        ]
        fact_tables = [
            sanitize_text(table, max_chars=255)
            for table in (semantic_context.get("factTables") or [])[:50]
            if table
        ]
        trace_label = f"{sanitize_text(operation, max_chars=80)} {sanitize_text(path, max_chars=500)}"
        store.begin_trace({
            "trace_id": request_id,
            "conversation_id": "",
            "source": "backend_middleware",
            "question_text": trace_label,
            "question_hash": sha256_text(trace_label),
            "business_kg_hash": graph["sha256"],
            "business_kg_file": graph["filename"],
            "ontology_version": graph["ontologyVersion"],
            "created_at": utc_now(),
        })
        store.complete_trace(request_id, {
            "source": "backend_middleware",
            "query_mode": category,
            "status": "succeeded" if succeeded else "failed",
            "diagnostic_code": "",
            "measure_codes": measure_codes,
            "dimension_codes": dimension_codes,
            "fact_tables": fact_tables,
            "result_row_count": None,
            "elapsed_ms": max(0, int(elapsed_ms)),
            "error_code": sanitize_text(error_code, max_chars=100),
            "error_message": "",
            "completed_at": utc_now(),
        })
        event = store.add_event({
            "event_id": f"evt_{uuid.uuid4().hex}",
            "trace_id": request_id,
            "event_type": "execution",
            "event_name": event_name,
            "source": "backend_middleware",
            "payload": {
                "requestId": sanitize_text(request_id, max_chars=100),
                "method": sanitize_text(method, max_chars=10).upper(),
                "path": sanitize_text(path, max_chars=500),
                "category": sanitize_text(category, max_chars=40),
                "operation": sanitize_text(operation, max_chars=80),
                "resourceId": sanitize_text(resource_id, max_chars=200),
                "status": "succeeded" if succeeded else "failed",
                "statusCode": int(status_code),
                "elapsedMs": max(0, int(elapsed_ms)),
                "errorCode": sanitize_text(error_code, max_chars=100),
                "businessKgHash": graph["sha256"],
                "businessKgFile": graph["filename"],
                "ontologyVersion": graph["ontologyVersion"],
                "measureCodes": measure_codes,
                "dimensionCodes": dimension_codes,
                "factTables": fact_tables,
            },
            "dedupe_key": f"backend:{request_id}",
        })
        _persist_semantic_contract(
            trace_id=request_id,
            source="backend_middleware",
            question=trace_label,
            query_mode=category,
            status="succeeded" if succeeded else "failed",
            semantic_context=semantic_context,
            graph=graph,
            operation=operation,
            domain_code=category,
        )
        return event
    except Exception:
        logger.exception("Backend feedback recording failed")
        return {"ok": False}


def record_schema_snapshot(schema_info: Any) -> dict[str, Any]:
    if not enabled():
        return {"ok": True, "disabled": True}
    try:
        datasource_key, snapshot = build_snapshot(schema_info)
        latest = store.latest_snapshot(datasource_key)
        if latest and latest.get("schema_hash") == snapshot["schemaHash"]:
            return {"ok": True, "unchanged": True, "snapshotId": latest["snapshot_id"]}
        previous = latest.get("snapshot") if latest else {}
        changes = diff_snapshots(previous, snapshot) if latest else []
        table_count = len(snapshot["tables"])
        column_count = sum(len(table.get("columns") or {}) for table in snapshot["tables"].values())
        snapshot_id = store.save_snapshot({
            "datasource_key": datasource_key,
            "schema_hash": snapshot["schemaHash"],
            "table_count": table_count,
            "column_count": column_count,
            "snapshot": snapshot,
        })
        if not latest:
            store.add_event({
                "event_type": "data", "event_name": "SCHEMA_SNAPSHOT_CREATED",
                "source": "metadata_build", "payload": {
                    "snapshotId": snapshot_id, "tableCount": table_count, "columnCount": column_count,
                }, "dedupe_key": f"schema:{snapshot['schemaHash']}",
            })
        else:
            for index, change in enumerate(changes[:2000]):
                event_name = change.pop("eventName")
                store.add_event({
                    "event_type": "data", "event_name": event_name, "source": "metadata_build",
                    "payload": {"snapshotId": snapshot_id, **change},
                    "dedupe_key": f"schema:{snapshot_id}:{index}:{event_name}",
                })
        return {"ok": True, "snapshotId": snapshot_id, "changeCount": len(changes)}
    except Exception:
        logger.exception("Schema feedback snapshot failed")
        return {"ok": False}
