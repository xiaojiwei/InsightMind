"""Uniform backend request observation for query, analysis, and dashboard APIs."""

from __future__ import annotations

import json
import time
import uuid
from dataclasses import dataclass
from typing import Awaitable, Callable, Optional

from fastapi import Request, Response
from starlette.background import BackgroundTask, BackgroundTasks
from starlette.middleware.base import BaseHTTPMiddleware

from .service import record_backend_request


@dataclass(frozen=True)
class BackendOperation:
    category: str
    operation: str
    resource_id: str = ""


_QUERY_PATHS = {
    "/api/query/preset",
    "/api/query/sparql",
    "/api/join-path",
    "/api/impact",
    "/api/ad/v1/load",
    "/api/ad/v1/sql",
    "/api/ad/v1/chart",
    "/api/ad/v1/drilldown",
    "/api/ad/v1/drill-dimensions",
    "/api/nlq/interpret",
    "/api/pivot/dimension-values",
    "/api/pivot/query",
    "/api/pivot/drill",
}
_ANALYSIS_PATHS = {
    "/api/analysis/start",
    "/api/analysis/ack",
    "/api/insight/start",
    "/api/insight/explain-cell",
    "/api/stats/analyze",
    "/api/alerts/document-scan",
    "/api/dashboard/v1/ai-interpret",
}
_ALREADY_TRACED = {"/api/nlq/query", "/api/nlq/entity-lookup"}


def _member_code(value) -> str:
    if isinstance(value, dict):
        value = (
            value.get("code") or value.get("member") or value.get("measureCode")
            or value.get("dimensionCode") or value.get("name") or ""
        )
    text = str(value or "").strip()
    return text[:255] if text else ""


def _member_list(value) -> list[str]:
    values = value if isinstance(value, list) else [value]
    result = []
    for item in values:
        code = _member_code(item)
        if code and code not in result:
            result.append(code)
    return result[:50]


def extract_semantic_context(payload) -> dict[str, object]:
    """Extract member identifiers only; never retain filter values or arbitrary body data."""
    if not isinstance(payload, dict):
        return {"measureCodes": [], "dimensionCodes": [], "factTables": [], "filters": []}
    nested = payload.get("query") if isinstance(payload.get("query"), dict) else {}
    sources = [payload, nested]
    measures: list[str] = []
    dimensions: list[str] = []
    fact_tables: list[str] = []
    filters: list[dict[str, str]] = []

    def add(target: list[str], values) -> None:
        for code in _member_list(values):
            if code not in target:
                target.append(code)

    for source in sources:
        for key in ("measures", "measureCodes", "measure", "measureCode"):
            add(measures, source.get(key))
        for key in (
            "dimensions", "dimensionCodes", "dimension", "dimensionCode", "rows", "columns",
            "currentMember", "contextMembers", "selectedDimensions",
        ):
            add(dimensions, source.get(key))
        for key in ("factTables", "tables", "table"):
            add(fact_tables, source.get(key))
        for key in ("rowPath", "columnPath"):
            add(dimensions, source.get(key))
        for item in source.get("filters") or []:
            if not isinstance(item, dict):
                continue
            code = _member_code(item)
            if code and not code.upper().startswith("MEAS_"):
                add(dimensions, code)
                descriptor = {
                    "member": code,
                    "operator": str(item.get("operator") or "")[:40],
                    "scope": str(item.get("scope") or "both")[:40],
                }
                if descriptor not in filters:
                    filters.append(descriptor)
        for item in source.get("configureList") or []:
            code = _member_code(item)
            if code.upper().startswith("MEAS_"):
                add(measures, code)
            elif code.upper().startswith("DIM_"):
                add(dimensions, code)

    return {
        "measureCodes": measures[:50],
        "dimensionCodes": dimensions[:50],
        "factTables": fact_tables[:50],
        "filters": filters[:50],
    }


async def _request_semantic_context(request: Request) -> dict[str, object]:
    if request.method.upper() not in {"POST", "PUT", "PATCH"}:
        return extract_semantic_context({})
    if "application/json" not in str(request.headers.get("content-type") or "").lower():
        return extract_semantic_context({})
    try:
        content_length = int(request.headers.get("content-length") or 0)
    except ValueError:
        content_length = 0
    if content_length > 262_144:
        return extract_semantic_context({})
    try:
        return extract_semantic_context(json.loads(await request.body()))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return extract_semantic_context({})


def classify_backend_operation(method: str, path: str) -> Optional[BackendOperation]:
    """Return a low-noise operation classification or None when it should be ignored."""
    method = str(method or "").upper()
    path = str(path or "")
    if path.startswith("/api/feedback/") or path in _ALREADY_TRACED:
        return None
    if method == "GET" and path.startswith("/dashboard/view/"):
        return BackendOperation("dashboard", "DASHBOARD_VIEW", path.rsplit("/", 1)[-1])
    if method == "GET" and path.startswith("/api/dashboard/v1/"):
        resource_id = path.rsplit("/", 1)[-1]
        if resource_id != "list":
            return BackendOperation("dashboard", "DASHBOARD_DEFINITION_LOAD", resource_id)
    if path in _QUERY_PATHS:
        return BackendOperation("query", "QUERY_EXECUTION")
    if path in _ANALYSIS_PATHS:
        return BackendOperation("analysis", "ANALYSIS_EXECUTION")
    if path.startswith("/api/da-tms/"):
        return BackendOperation("analysis", "DOMAIN_ANALYSIS")
    if method == "POST" and path.startswith("/api/insight/action-plan/"):
        return BackendOperation("analysis", "INSIGHT_ACTION")
    return None


def _append_background_task(response: Response, callback: Callable, **kwargs) -> None:
    task = BackgroundTask(callback, **kwargs)
    if response.background is None:
        response.background = task
        return
    if isinstance(response.background, BackgroundTasks):
        response.background.tasks.append(task)
        return
    tasks = BackgroundTasks()
    tasks.tasks.append(response.background)
    tasks.tasks.append(task)
    response.background = tasks


class FeedbackObservationMiddleware(BaseHTTPMiddleware):
    """Persist selected backend executions after their response has been sent."""

    async def dispatch(
        self,
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        operation = classify_backend_operation(request.method, request.url.path)
        if operation is None:
            return await call_next(request)

        request_id = f"req_{uuid.uuid4().hex}"
        semantic_context = await _request_semantic_context(request)
        started = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            record_backend_request(
                request_id=request_id,
                method=request.method,
                path=request.url.path,
                category=operation.category,
                operation=operation.operation,
                resource_id=operation.resource_id,
                status_code=500,
                elapsed_ms=int((time.perf_counter() - started) * 1000),
                error_code="UNHANDLED_EXCEPTION",
                semantic_context=semantic_context,
            )
            raise

        status_code = int(response.status_code)
        _append_background_task(
            response,
            record_backend_request,
            request_id=request_id,
            method=request.method,
            path=request.url.path,
            category=operation.category,
            operation=operation.operation,
            resource_id=operation.resource_id,
            status_code=status_code,
            elapsed_ms=int((time.perf_counter() - started) * 1000),
            error_code=(f"HTTP_{status_code}" if status_code >= 400 else ""),
            semantic_context=semantic_context,
        )
        response.headers["X-Feedback-Request-Id"] = request_id
        return response
