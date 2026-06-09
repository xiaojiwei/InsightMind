from kg_builder.semantic import AdSemanticService
from kg_builder.semantic.sql_api import AdSqlEngine


CATALOG = {
    "measures": [{
        "code": "MEAS_total_sales_amount",
        "name": "总销售额",
        "tables": ["sales"],
        "dimensionCodes": ["DIM_channel", "DIM_date_day"],
    }],
    "dimensions": [
        {
            "code": "DIM_channel",
            "name": "渠道",
            "isTime": False,
            "viewType": 0,
            "tables": ["sales"],
        },
        {
            "code": "DIM_date_day",
            "name": "日期",
            "isTime": True,
            "viewType": 1,
            "tables": ["sales"],
        },
    ],
}


class RecordingService(AdSemanticService):
    def __init__(self):
        super().__init__(
            CATALOG,
            da_query=lambda _payload: {"code": 200, "data": {"cellList": []}},
            da_filter_builder=lambda filters: filters,
        )
        self.last_query = None

    def load(self, query):
        self.last_query = query
        return {
            "data": [{
                "ad.channel": "web",
                "ad.total_sales_amount": 100,
            }],
        }


def test_sql_engine_exposes_semantic_schema():
    engine = AdSqlEngine(service_factory=lambda: RecordingService())

    assert engine.schema()["ad"]["semantic"]["channel"] == "TEXT"
    assert engine.schema()["ad"]["semantic"]["total_sales_amount"] == "DOUBLE"


def test_sql_engine_maps_select_to_semantic_query():
    service = RecordingService()
    engine = AdSqlEngine(service_factory=lambda: service)

    rows, columns = engine.query("""
        SELECT channel, SUM(total_sales_amount) AS sales
        FROM semantic
        WHERE channel = 'web' AND date_day BETWEEN '2026-01-01' AND '2026-01-31'
        GROUP BY channel
        ORDER BY sales DESC
        LIMIT 10
    """)

    assert columns == ["channel", "sales"]
    assert rows == [("web", 100)]
    assert service.last_query == {
        "measures": ["ad.total_sales_amount"],
        "dimensions": ["ad.channel"],
        "filters": [
            {"member": "ad.channel", "operator": "equals", "values": ["web"]},
            {"member": "ad.date_day", "operator": "between", "values": ["2026-01-01", "2026-01-31"]},
        ],
        "order": {"ad.total_sales_amount": "desc"},
        "limit": 10,
    }
