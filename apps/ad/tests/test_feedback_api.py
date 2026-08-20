import importlib
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from kg_builder.feedback import service as feedback_service
from kg_builder.feedback.router import router


feedback_router_module = importlib.import_module("kg_builder.feedback.router")


class MemoryFeedbackStore:
    def __init__(self):
        self.events = []
        self.memories = []
        self.reviews = []
        self.dictionary = []

    def add_event(self, values):
        event = {
            "eventId": values["event_id"],
            "traceId": values.get("trace_id", ""),
            "eventType": values["event_type"],
            "eventName": values["event_name"],
            "source": values.get("source", ""),
            "payload": values.get("payload", {}),
        }
        self.events.append(event)
        return event

    def list_events(self, **filters):
        items = [
            event for event in self.events
            if not filters.get("event_type") or event["eventType"] == filters["event_type"]
        ]
        return {"items": items, "total": len(items)}

    def summary(self):
        return {
            "totalTraces": 0,
            "totalEvents": len(self.events),
            "traceCounts": {},
            "eventTypeCounts": {"explicit": len(self.events)} if self.events else {},
            "negativeFeedback": sum(
                event["eventName"] == "RESULT_UNHELPFUL" for event in self.events
            ),
            "schemaChanges": 0,
            "semanticPlans": 0,
            "correctionSteps": 0,
            "memoryCounts": {},
            "evalCases": 0,
        }

    def review_memory_by_trace(self, trace_id, **values):
        self.reviews.append((trace_id, values))
        return values

    def list_memories(self, **filters):
        items = [
            item for item in self.memories
            if not filters.get("status") or item["status"] == filters["status"]
        ]
        return {"items": items, "total": len(items)}

    def review_memory(self, memory_id, **values):
        item = next((item for item in self.memories if item["memoryId"] == memory_id), None)
        if not item:
            return None
        item["status"] = values["action"].upper()
        return item

    def create_eval_case_from_trace(self, trace_id, **values):
        return {"caseId": "case-1", "traceId": trace_id, "priority": values["priority"]}

    def list_eval_cases(self, **_filters):
        return {"items": [], "total": 0}

    def create_dictionary_proposal(self, values):
        item = {
            "entryId": "dict-1",
            "semanticType": values["semantic_type"],
            "term": values["term_text"],
            "canonicalCode": values.get("canonical_code", ""),
            "dimensionCode": values.get("dimension_code", ""),
            "canonicalValue": values.get("canonical_value", ""),
            "businessKgHash": values.get("business_kg_hash", ""),
            "status": "PENDING",
        }
        self.dictionary.append(item)
        return item

    def get_dictionary_entry(self, entry_id):
        return next((item for item in self.dictionary if item["entryId"] == entry_id), None)

    def list_dictionary_entries(self, **filters):
        items = [
            item for item in self.dictionary
            if not filters.get("status") or item["status"] == filters["status"]
        ]
        return {"items": items, "total": len(items)}

    def review_dictionary_entry(self, entry_id, **values):
        item = self.get_dictionary_entry(entry_id)
        if item:
            item["status"] = values["action"].upper()
        return item


def _client(monkeypatch):
    feedback_store = MemoryFeedbackStore()
    monkeypatch.setattr(feedback_service, "store", feedback_store)
    monkeypatch.setattr(feedback_router_module, "store", feedback_store)
    app = FastAPI()
    app.include_router(router)
    return TestClient(app), feedback_store


def test_feedback_api_records_and_lists_client_event(monkeypatch) -> None:
    client, _feedback_store = _client(monkeypatch)

    response = client.post("/api/feedback/events", json={
        "eventId": "event-api-1",
        "traceId": "trace-api-1",
        "eventType": "explicit",
        "eventName": "RESULT_UNHELPFUL",
        "payload": {"reasonCode": "WRONG_DIMENSION", "password": "must-not-persist"},
    })
    listed = client.get("/api/feedback/events", params={"eventType": "explicit"})
    summary = client.get("/api/feedback/summary")

    assert response.status_code == 200
    assert listed.status_code == 200
    assert listed.json()["total"] == 1
    assert listed.json()["items"][0]["payload"] == {"reasonCode": "WRONG_DIMENSION"}
    assert summary.json()["negativeFeedback"] == 1
    assert _feedback_store.reviews[0][0] == "trace-api-1"
    assert _feedback_store.reviews[0][1]["action"] == "DISABLED"


def test_feedback_api_reviews_memory_and_promotes_trace_to_eval(monkeypatch) -> None:
    client, feedback_store = _client(monkeypatch)
    feedback_store.memories.append({
        "memoryId": "memory-1", "traceId": "trace-1", "status": "PENDING",
    })

    listed = client.get("/api/feedback/memories", params={"status": "PENDING"})
    reviewed = client.post("/api/feedback/memories/memory-1/review", json={
        "action": "ENABLED", "actor": "tester",
    })
    eval_case = client.post("/api/feedback/traces/trace-1/eval-cases", json={
        "category": "feedback_regression", "priority": "P0",
    })

    assert listed.status_code == 200
    assert listed.json()["total"] == 1
    assert reviewed.status_code == 200
    assert reviewed.json()["memory"]["status"] == "ENABLED"
    assert eval_case.status_code == 200
    assert eval_case.json()["case"]["priority"] == "P0"


def test_feedback_api_rejects_spoofed_execution_event(monkeypatch) -> None:
    client, _feedback_store = _client(monkeypatch)

    response = client.post("/api/feedback/events", json={
        "eventType": "execution",
        "eventName": "QUERY_SUCCEEDED",
    })

    assert response.status_code == 400
    assert "只允许提交" in response.json()["detail"]


def test_feedback_dictionary_requires_review_before_enable(monkeypatch) -> None:
    client, feedback_store = _client(monkeypatch)
    monkeypatch.setattr(
        feedback_router_module,
        "_validated_dictionary_target",
        lambda _payload: ({"code": "MEAS_gmv", "cnName": "商品交易总额", "semanticType": "measure"}, "g" * 64),
    )

    proposed = client.post("/api/feedback/dictionary/proposals", json={
        "semanticType": "measure",
        "term": "成交额",
        "canonicalCode": "MEAS_gmv",
    })
    pending = client.get("/api/feedback/dictionary", params={"status": "PENDING"})
    enabled = client.post("/api/feedback/dictionary/dict-1/review", json={
        "action": "ENABLED", "actor": "reviewer",
    })

    assert proposed.status_code == 200
    assert proposed.json()["proposal"]["status"] == "PENDING"
    assert pending.json()["total"] == 1
    assert enabled.status_code == 200
    assert feedback_store.dictionary[0]["status"] == "ENABLED"


def test_feedback_api_allows_local_management_without_a_token(monkeypatch) -> None:
    feedback_store = MemoryFeedbackStore()
    monkeypatch.setattr(feedback_service, "store", feedback_store)
    monkeypatch.setattr(feedback_router_module, "store", feedback_store)
    monkeypatch.delenv("INSIGHTMIND_FEEDBACK_API_TOKEN", raising=False)
    monkeypatch.delenv("SEMANTIC_DICTIONARY_REVIEW_TOKEN", raising=False)
    app = FastAPI()
    app.include_router(router)
    client = TestClient(app)

    response = client.get("/api/feedback/summary")

    assert response.status_code == 200


def test_feedback_api_reports_mysql_unavailable_without_leaking_details(monkeypatch) -> None:
    client, feedback_store = _client(monkeypatch)

    def fail_summary():
        raise RuntimeError("password=must-not-leak")

    feedback_store.summary = fail_summary
    response = client.get("/api/feedback/summary")

    assert response.status_code == 503
    assert response.json() == {"detail": "反馈 MySQL 暂不可用"}
    assert "must-not-leak" not in response.text


def test_home_sidebar_contains_feedback_menu() -> None:
    template = (
        Path(__file__).parents[1]
        / "kg_builder"
        / "web"
        / "templates"
        / "index.html"
    ).read_text(encoding="utf-8")

    assert '<a class="nav-link" href="/feedback" title="反馈记录">' in template
    assert '<span>反馈记录</span>' in template


def test_feedback_page_loads_without_a_management_token() -> None:
    template = (
        Path(__file__).parents[1]
        / "kg_builder"
        / "web"
        / "templates"
        / "feedback.html"
    ).read_text(encoding="utf-8")

    assert "feedback-token" not in template
    assert "反馈管理 Token" not in template
    assert "function reloadAll(){Promise.all([loadSummary(),loadMemories(),loadEvents()]);}" in template


def test_backend_feedback_payload_excludes_request_content(monkeypatch) -> None:
    captured = []
    started = []
    completed = []

    class CapturingStore:
        def begin_trace(self, values):
            started.append(values)

        def complete_trace(self, trace_id, values):
            completed.append((trace_id, values))

        def add_event(self, values):
            captured.append(values)
            return values

    monkeypatch.setattr(feedback_service, "store", CapturingStore())
    monkeypatch.setattr(
        feedback_service,
        "graph_identity",
        lambda _path: {
            "sha256": "a" * 64,
            "filename": "indicator-data.ttl",
            "ontologyVersion": "1.1.0",
        },
    )

    feedback_service.record_backend_request(
        request_id="req-1",
        method="POST",
        path="/api/ad/v1/load",
        category="query",
        operation="QUERY_EXECUTION",
        status_code=200,
        elapsed_ms=15,
        semantic_context={
            "measureCodes": ["ad.sales_amount"],
            "dimensionCodes": ["ad.region"],
            "factTables": ["sales"],
        },
    )

    payload = captured[0]["payload"]
    assert started[0]["trace_id"] == "req-1"
    assert completed[0][0] == "req-1"
    assert completed[0][1]["measure_codes"] == ["ad.sales_amount"]
    assert completed[0][1]["dimension_codes"] == ["ad.region"]
    assert captured[0]["trace_id"] == "req-1"
    assert captured[0]["event_type"] == "execution"
    assert captured[0]["event_name"] == "QUERY_API_SUCCEEDED"
    assert payload["path"] == "/api/ad/v1/load"
    assert payload["status"] == "succeeded"
    assert payload["businessKgHash"] == "a" * 64
    assert set(payload) == {
        "requestId", "method", "path", "category", "operation", "resourceId",
        "status", "statusCode", "elapsedMs", "errorCode", "businessKgHash",
        "businessKgFile", "ontologyVersion",
        "measureCodes", "dimensionCodes", "factTables",
    }
