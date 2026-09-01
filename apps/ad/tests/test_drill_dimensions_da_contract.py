from __future__ import annotations

import pytest

import web_app


class _SemanticService:
    def meta(self) -> dict:
        return {
            "models": [{
                "measures": [{
                    "name": "ad.order_count",
                    "code": "MEAS_order_count",
                    "title": "订单数",
                    "tables": ["fact_orders"],
                }],
                "timeDimensions": [],
                "dimensions": [{
                    "name": "ad.region",
                    "code": "DIM_region",
                    "title": "区域",
                    "tables": ["fact_orders"],
                }],
            }]
        }


def test_drill_dimensions_do_not_fallback_when_da_graph_has_no_relations(monkeypatch) -> None:
    monkeypatch.setattr(web_app, "_ad_semantic_service", lambda *_args, **_kwargs: _SemanticService())
    monkeypatch.setattr(web_app, "_da_compatible_dimension_relations", lambda _code: {})

    result = web_app._ad_drill_dimension_candidates({"measure": "ad.order_count"})

    assert result["source"] == "da_graph"
    assert result["items"] == []


def test_da_graph_request_failure_is_not_treated_as_an_empty_graph(monkeypatch) -> None:
    def unavailable(*_args, **_kwargs):
        raise OSError("DA is offline")

    monkeypatch.setattr(web_app, "_urlopen", unavailable)

    with pytest.raises(RuntimeError, match="DA.*业务图谱"):
        web_app._da_compatible_dimension_relations("MEAS_order_count")
