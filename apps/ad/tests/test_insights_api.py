from fastapi import FastAPI
from fastapi.testclient import TestClient

import kg_builder.insights.router as insight_router
from kg_builder.insights.router import router


def _points(count=36):
    year, month = 2023, 1
    result = []
    for index in range(count):
        result.append({"period": f"{year:04d}-{month:02d}-01", "value": 100 + index * 2})
        month += 1
        if month == 13:
            year += 1
            month = 1
    return result


def test_forecast_api_accepts_supplied_series(monkeypatch):
    monkeypatch.setattr(insight_router, "_safe_persist_forecast", lambda *_args: None)
    monkeypatch.setattr(insight_router, "_safe_persist_fact", lambda *_args: None)
    app = FastAPI()
    app.include_router(router)
    client = TestClient(app)

    response = client.post("/api/insights/forecast", json={
        "metricCode": "MEAS_sales",
        "timeDimension": "DIM_month",
        "grain": "month",
        "horizon": 3,
        "series": _points(),
    })

    assert response.status_code == 200
    payload = response.json()
    assert payload["forecast"]["status"] in {"QUALIFIED", "LOW_CONFIDENCE"}
    assert len(payload["forecast"]["forecast"]) == 3


def test_cross_metric_api_uses_supplied_series(monkeypatch):
    monkeypatch.setattr(insight_router, "_safe_persist_fact", lambda *_args: None)
    app = FastAPI()
    app.include_router(router)
    client = TestClient(app)
    left = _points(48)
    right = [{**point, "value": point["value"] * 2 + (index % 3)} for index, point in enumerate(left)]

    response = client.post("/api/insights/cross-metric/discover", json={
        "metricCodes": ["A", "B"],
        "timeDimension": "DIM_month",
        "grain": "month",
        "start": left[0]["period"],
        "end": left[-1]["period"],
        "series": {"A": left, "B": right},
        "contributionVectors": {
            "DIM_region": {
                "A": {"east": 0.7, "west": -0.3},
                "B": {"east": 0.6, "west": -0.4},
            }
        },
        "minPoints": 30,
    })

    assert response.status_code == 200
    payload = response.json()
    assert payload["candidateCount"] == 1
    assert payload["commonContributors"]["A|B"][0]["relation"] in {"common_driver", "common_detractor"}
    assert payload["facts"][0]["payload"]["sharedContributors"]


def test_job_runner_dispatches_forecast(monkeypatch):
    monkeypatch.setattr(insight_router, "_safe_persist_forecast", lambda *_args: None)
    monkeypatch.setattr(insight_router, "_safe_persist_fact", lambda *_args: None)
    app = FastAPI()
    app.include_router(router)
    client = TestClient(app)

    response = client.post("/api/insights/jobs/run", json={
        "jobType": "forecast",
        "payload": {
            "metricCode": "MEAS_sales",
            "timeDimension": "DIM_month",
            "grain": "month",
            "horizon": 2,
            "series": _points(),
        },
    })

    assert response.status_code == 200
    assert len(response.json()["forecast"]["forecast"]) == 2
