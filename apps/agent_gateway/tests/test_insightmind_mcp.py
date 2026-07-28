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
        with patch.object(gateway, "get_semantic_meta", return_value=meta):
            result = gateway.search_catalog("通话")
        self.assertEqual("MEAS_calls", result["measures"][0]["code"])
        self.assertEqual("质检通话数", result["measures"][0]["name"])

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


if __name__ == "__main__":
    unittest.main()
