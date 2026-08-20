import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parents[1] / "insightmind_mcp.py"
SPEC = importlib.util.spec_from_file_location("insightmind_mcp", MODULE_PATH)
gateway = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(gateway)


class CatalogTests(unittest.TestCase):
    def test_catalog_flattens_models_and_time_dimensions(self):
        meta = {
            "models": [
                {
                    "measures": [{"code": "MEAS_calls", "title": "通话数"}],
                    "dimensions": [{"code": "DIM_store", "title": "门店"}],
                    "timeDimensions": [{"code": "DIM_date", "title": "日期"}],
                }
            ]
        }
        measures, dimensions = gateway._semantic_catalog(meta)
        self.assertEqual(["MEAS_calls"], [item["code"] for item in measures])
        self.assertEqual(["DIM_store", "DIM_date"], [item["code"] for item in dimensions])

    def test_search_catalog_matches_title(self):
        meta = {
            "models": [
                {
                    "measures": [{"code": "MEAS_calls", "title": "质检通话数"}],
                    "dimensions": [{"code": "DIM_store", "title": "门店"}],
                    "timeDimensions": [],
                }
            ]
        }
        with patch.object(gateway, "_request", return_value={"ok": False}), patch.object(
            gateway, "get_semantic_meta", return_value=meta
        ):
            result = gateway.search_catalog("通话")
        self.assertEqual("MEAS_calls", result["measures"][0]["code"])
        self.assertEqual("质检通话数", result["measures"][0]["name"])

    def test_search_catalog_prefers_shared_semantic_retrieval(self):
        semantic = {
            "ok": True,
            "vectorUsed": False,
            "items": [{
                "semanticType": "measure",
                "code": "MEAS_calls",
                "name": "质检通话数",
                "score": 0.98,
                "matchType": "exact_alias",
                "confidence": "high",
                "evidence": [{"source": "manual"}],
            }],
        }
        with patch.object(gateway, "_request", return_value=semantic), patch.object(
            gateway, "get_semantic_meta"
        ) as legacy:
            result = gateway.search_catalog("通话")

        self.assertEqual("semantic_retrieval", result["source"])
        self.assertEqual("MEAS_calls", result["measures"][0]["code"])
        self.assertEqual("exact_alias", result["measures"][0]["matchType"])
        legacy.assert_not_called()

    def test_compatible_dimensions_falls_back_to_ad_graph(self):
        meta = {
            "models": [
                {
                    "measures": [
                        {
                            "code": "MEAS_calls",
                            "title": "通话数",
                            "dimensionCodes": ["DIM_store"],
                        }
                    ],
                    "dimensions": [{"code": "DIM_store", "title": "门店"}],
                    "timeDimensions": [],
                }
            ]
        }
        with patch.object(gateway, "_request", return_value={"ok": True, "data": []}), patch.object(
            gateway, "get_semantic_meta", return_value=meta
        ):
            result = gateway.compatible_dimensions("MEAS_calls")
        self.assertEqual("ad_business_kg", result["source"])
        self.assertEqual("DIM_store", result["dimensions"][0]["code"])


class SafetyTests(unittest.TestCase):
    def test_page_size_is_capped(self):
        self.assertEqual(gateway.MAX_PAGE_SIZE, gateway._cap_page_size(gateway.MAX_PAGE_SIZE + 100))
        self.assertEqual(1, gateway._cap_page_size(-10))

    def test_raw_sparql_rejects_update(self):
        with patch.object(gateway, "ALLOW_RAW_SPARQL", True):
            result = gateway.raw_sparql_select("DELETE WHERE { ?s ?p ?o }")
        self.assertFalse(result["ok"])

    def test_governed_value_no_match_does_not_fall_back_to_da(self):
        with patch.object(
            gateway,
            "_request",
            return_value={"ok": True, "items": []},
        ) as request:
            result = gateway.find_dimensions_by_value("13800138000")

        self.assertTrue(result["ok"])
        self.assertTrue(result["governedNoMatch"])
        self.assertEqual([], result["dimensions"])
        request.assert_called_once()

    def test_unavailable_governed_value_index_fails_closed_by_default(self):
        with patch.object(
            gateway,
            "_request",
            return_value={"ok": False, "error": "unavailable"},
        ) as request, patch.object(gateway, "ALLOW_LEGACY_VALUE_LOOKUP", False):
            result = gateway.find_dimensions_by_value("东区")

        self.assertFalse(result["ok"])
        self.assertTrue(result["degraded"])
        request.assert_called_once()

    def test_legacy_value_lookup_requires_explicit_compatibility_switch(self):
        with patch.object(
            gateway,
            "_request",
            side_effect=[
                {"ok": False, "error": "unavailable"},
                {"ok": True, "data": [{"code": "DIM_region"}]},
            ],
        ) as request, patch.object(gateway, "ALLOW_LEGACY_VALUE_LOOKUP", True):
            result = gateway.find_dimensions_by_value("东区")

        self.assertTrue(result["ok"])
        self.assertEqual("da_value_lookup", result["source"])
        self.assertEqual(2, request.call_count)


if __name__ == "__main__":
    unittest.main()
