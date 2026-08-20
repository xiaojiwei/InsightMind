import importlib

from fastapi import FastAPI, Request
from fastapi.testclient import TestClient

from kg_builder.feedback.middleware import (
    FeedbackObservationMiddleware,
    classify_backend_operation,
    extract_semantic_context,
)


middleware_module = importlib.import_module("kg_builder.feedback.middleware")


def test_backend_operation_classification_covers_workloads_without_noise() -> None:
    assert classify_backend_operation("GET", "/dashboard/view/dash-sales").operation == "DASHBOARD_VIEW"
    assert classify_backend_operation("POST", "/api/ad/v1/load").category == "query"
    assert classify_backend_operation("POST", "/api/stats/analyze").category == "analysis"
    assert classify_backend_operation("POST", "/api/da-tms/call-sop/diagnosis").category == "analysis"
    assert classify_backend_operation("GET", "/api/build/status") is None
    assert classify_backend_operation("GET", "/api/feedback/events") is None
    assert classify_backend_operation("POST", "/api/nlq/query") is None


def test_semantic_context_extracts_codes_but_not_filter_values() -> None:
    context = extract_semantic_context({
        "measures": ["ad.sales_amount"],
        "dimensions": ["ad.region"],
        "filters": [{"member": "ad.order_date", "values": ["customer-secret"]}],
        "configureList": [{"code": "MEAS_profit"}, {"code": "DIM_channel"}],
        "unrelated": {"password": "must-not-record"},
    })

    assert context == {
        "measureCodes": ["ad.sales_amount", "MEAS_profit"],
        "dimensionCodes": ["ad.region", "ad.order_date", "DIM_channel"],
        "factTables": [],
        "filters": [{"member": "ad.order_date", "operator": "", "scope": "both"}],
    }
    assert "customer-secret" not in str(context)
    assert "must-not-record" not in str(context)


def test_middleware_records_backend_execution_after_response(monkeypatch) -> None:
    captured = []
    monkeypatch.setattr(
        middleware_module,
        "record_backend_request",
        lambda **values: captured.append(values),
    )
    app = FastAPI()
    app.add_middleware(FeedbackObservationMiddleware)

    @app.post("/api/ad/v1/load")
    async def load(request: Request):
        body = await request.json()
        return {"measures": body.get("measures")}

    response = TestClient(app).post("/api/ad/v1/load", json={
        "measures": ["ad.sales_amount"],
        "dimensions": ["ad.region"],
        "filters": [{"member": "ad.order_date", "values": ["secret-value"]}],
        "secret": "not-recorded",
    })

    assert response.status_code == 200
    assert response.json() == {"measures": ["ad.sales_amount"]}
    assert response.headers["X-Feedback-Request-Id"].startswith("req_")
    assert len(captured) == 1
    assert captured[0]["category"] == "query"
    assert captured[0]["operation"] == "QUERY_EXECUTION"
    assert captured[0]["path"] == "/api/ad/v1/load"
    assert captured[0]["semantic_context"] == {
        "measureCodes": ["ad.sales_amount"],
        "dimensionCodes": ["ad.region", "ad.order_date"],
        "factTables": [],
        "filters": [{"member": "ad.order_date", "operator": "", "scope": "both"}],
    }
    assert "secret" not in str(captured[0])


def test_middleware_records_dashboard_resource_and_failure(monkeypatch) -> None:
    captured = []
    monkeypatch.setattr(
        middleware_module,
        "record_backend_request",
        lambda **values: captured.append(values),
    )
    app = FastAPI()
    app.add_middleware(FeedbackObservationMiddleware)

    @app.get("/dashboard/view/{item_id}")
    async def dashboard(item_id: str):
        from fastapi.responses import JSONResponse

        return JSONResponse({"itemId": item_id}, status_code=503)

    response = TestClient(app).get("/dashboard/view/dash-sales")

    assert response.status_code == 503
    assert captured[0]["category"] == "dashboard"
    assert captured[0]["resource_id"] == "dash-sales"
    assert captured[0]["error_code"] == "HTTP_503"
