from kg_builder.semantic import AdSemanticService, build_meta


CATALOG = {
    "measures": [{
        "code": "MEAS_total_sales_amount",
        "name": "总销售额",
        "unit": "元",
        "tables": ["sales"],
        "dimensionCodes": ["DIM_date_day", "DIM_date_month", "DIM_channel"],
    }],
    "dimensions": [
        {
            "code": "DIM_date_day",
            "name": "日期",
            "isTime": True,
            "viewType": 1,
            "hierarchyCode": "HIER_date",
            "tables": ["sales"],
        },
        {
            "code": "DIM_date_month",
            "name": "月份",
            "isTime": True,
            "viewType": 3,
            "hierarchyCode": "HIER_date",
            "tables": ["sales"],
        },
        {
            "code": "DIM_channel",
            "name": "渠道",
            "isTime": False,
            "viewType": 0,
            "tables": ["sales"],
        },
    ],
}


def passthrough_filters(filters):
    return filters


def fake_da_query(payload):
    assert payload["configureList"][0]["code"] == "MEAS_total_sales_amount"
    return {
        "code": 200,
        "data": {
            "cost": 12,
            "reviewSql": "select ...",
            "cellList": [[
                {"code": "DIM_date_month", "type": "DIMENSION", "data": "2026-01"},
                {"code": "DIM_channel", "type": "DIMENSION", "data": "web"},
                {"code": "MEAS_total_sales_amount", "type": "MEASURE", "data": 100},
            ]],
        },
    }


def test_build_meta_exposes_cube_like_members():
    meta = build_meta(CATALOG)
    model = meta["models"][0]

    assert model["measures"][0]["name"] == "ad.total_sales_amount"
    assert model["timeDimensions"][0]["name"] == "ad.date_day"
    assert model["dimensions"][0]["name"] == "ad.channel"


def test_translate_query_maps_cube_like_spec_to_da_payload():
    service = AdSemanticService(CATALOG, fake_da_query, passthrough_filters)

    payload = service.translate_query({
        "measures": ["ad.total_sales_amount"],
        "dimensions": ["ad.channel"],
        "timeDimensions": [{
            "dimension": "ad.date_day",
            "granularity": "month",
            "dateRange": ["2026-01-01", "2026-01-31"],
        }],
        "filters": [{
            "member": "ad.channel",
            "operator": "equals",
            "values": ["web"],
        }],
        "order": {"ad.date_month": "asc"},
        "limit": 500,
    })

    assert [item["code"] for item in payload["configureList"]] == [
        "MEAS_total_sales_amount",
        "DIM_channel",
        "DIM_date_month",
    ]
    assert payload["pageSize"] == 500
    assert payload["filterList"] == [
        {
            "code": "DIM_channel",
            "operator": "in",
            "values": ["web"],
            "viewType": 0,
            "filterMode": "enum",
        },
        {
            "code": "DIM_date_day",
            "operator": "between",
            "values": ["2026-01-01", "2026-01-31"],
            "viewType": 1,
            "filterMode": "date",
        },
    ]


def test_load_normalizes_da_cell_list_to_member_keys():
    service = AdSemanticService(CATALOG, fake_da_query, passthrough_filters)

    result = service.load({
        "measures": ["ad.total_sales_amount"],
        "dimensions": ["ad.channel"],
        "timeDimensions": [{"dimension": "ad.date_day", "granularity": "month"}],
    })

    assert result["data"] == [{
        "ad.date_month": "2026-01",
        "ad.channel": "web",
        "ad.total_sales_amount": 100,
    }]
    assert result["diagnostics"]["reviewSql"] == "select ..."


def test_public_dimensions_are_deduped_and_resolved_per_measure():
    catalog = {
        "measures": [
            {
                "code": "MEAS_catalog_sales_amount",
                "name": "目录销售额",
                "tables": ["catalog_sales"],
                "dimensionCodes": ["DIM_date_month_catalog_sales"],
            },
            {
                "code": "MEAS_web_sales_amount",
                "name": "网络销售额",
                "tables": ["web_sales"],
                "dimensionCodes": ["DIM_web_sales_date_month"],
            },
        ],
        "dimensions": [
            {
                "code": "DIM_date_month_catalog_sales",
                "name": "月",
                "isTime": True,
                "viewType": 3,
                "levelCode": "month",
                "hierarchyCode": "h_date",
                "tables": ["catalog_sales"],
            },
            {
                "code": "DIM_web_sales_date_month",
                "name": "月",
                "isTime": True,
                "viewType": 3,
                "levelCode": "month",
                "hierarchyCode": "h_date",
                "tables": ["web_sales"],
            },
        ],
    }
    service = AdSemanticService(catalog, fake_da_query, passthrough_filters)

    meta = service.meta()["models"][0]
    assert [item["title"] for item in meta["timeDimensions"]] == ["月"]
    assert meta["timeDimensions"][0]["name"] == "ad.date_month"
    assert sorted(meta["timeDimensions"][0]["sourceCodes"]) == [
        "DIM_date_month_catalog_sales",
        "DIM_web_sales_date_month",
    ]

    catalog_payload = service.translate_query({
        "measures": ["ad.catalog_sales_amount"],
        "dimensions": ["ad.date_month"],
    })
    web_payload = service.translate_query({
        "measures": ["ad.web_sales_amount"],
        "dimensions": ["ad.date_month"],
    })

    assert [item["code"] for item in catalog_payload["configureList"]] == [
        "MEAS_catalog_sales_amount",
        "DIM_date_month_catalog_sales",
    ]
    assert [item["code"] for item in web_payload["configureList"]] == [
        "MEAS_web_sales_amount",
        "DIM_web_sales_date_month",
    ]


def test_measure_filter_translates_to_measure_code():
    service = AdSemanticService(CATALOG, fake_da_query, passthrough_filters)

    payload = service.translate_query({
        "measures": ["ad.total_sales_amount"],
        "filters": [{
            "kind": "measure",
            "member": "ad.total_sales_amount",
            "operator": "lte",
            "values": ["400000"],
        }],
    })

    assert payload["filterList"] == [{
        "code": "MEAS_total_sales_amount",
        "operator": "less_than_or_equal",
        "values": ["400000"],
        "viewType": 0,
        "filterMode": "enum",
    }]


def test_public_time_filter_resolves_to_measure_fact_table():
    catalog = {
        "measures": [{
            "code": "MEAS_web_net_profit",
            "name": "网络净利润",
            "tables": ["web_sales"],
            "dimensionCodes": ["DIM_web_sales_date_quarter"],
        }],
        "dimensions": [
            {
                "code": "DIM_date_quarter_catalog_sales",
                "name": "季度",
                "isTime": True,
                "viewType": 4,
                "levelCode": "quarter",
                "hierarchyCode": "h_date",
                "tables": ["catalog_sales"],
            },
            {
                "code": "DIM_web_sales_date_quarter",
                "name": "季度",
                "isTime": True,
                "viewType": 4,
                "levelCode": "quarter",
                "hierarchyCode": "h_date",
                "tables": ["web_sales"],
            },
        ],
    }
    service = AdSemanticService(catalog, fake_da_query, passthrough_filters)
    measure = service._resolve_member("ad.web_net_profit", "measure")

    filters = service._convert_filters([{
        "member": "ad.date_quarter",
        "operator": "equals",
        "values": ["2025Q2"],
    }], [measure])

    assert filters == [{
        "code": "DIM_web_sales_date_quarter",
        "operator": "in",
        "values": ["2025Q2"],
        "viewType": 4,
        "filterMode": "time",
    }]


def test_public_date_dimensions_are_compatible_across_fact_tables():
    dimensions = []
    measures = []
    for table, measure_code in [
        ("catalog_sales", "MEAS_catalog_sales_amount"),
        ("web_sales", "MEAS_web_sales_amount"),
    ]:
        dim_codes = []
        for level, view_type in [
            ("day", 1),
            ("week", 2),
            ("month", 3),
            ("quarter", 4),
            ("year", 5),
        ]:
            code = f"DIM_{table}_date_{level}"
            dim_codes.append(code)
            dimensions.append({
                "code": code,
                "name": {"day": "日", "week": "周", "month": "月", "quarter": "季度", "year": "年"}[level],
                "isTime": True,
                "viewType": view_type,
                "levelCode": level,
                "hierarchyCode": "h_date",
                "tables": [table],
            })
        measures.append({
            "code": measure_code,
            "name": measure_code,
            "tables": [table],
            "dimensionCodes": dim_codes,
        })

    service = AdSemanticService({"measures": measures, "dimensions": dimensions}, fake_da_query, passthrough_filters)

    payload = service.translate_query({
        "measures": ["ad.catalog_sales_amount", "ad.web_sales_amount"],
        "dimensions": ["ad.date_day", "ad.date_week", "ad.date_month", "ad.date_quarter", "ad.date_year"],
    })

    assert [item["code"] for item in payload["configureList"]] == [
        "MEAS_catalog_sales_amount",
        "MEAS_web_sales_amount",
        "DIM_date_day",
        "DIM_date_week",
        "DIM_date_month",
        "DIM_date_quarter",
        "DIM_date_year",
    ]


def test_public_non_time_dimensions_are_compatible_across_fact_tables():
    catalog = {
        "measures": [
            {
                "code": "MEAS_catalog_net_profit",
                "name": "目录净利润",
                "tables": ["catalog_sales"],
                "dimensionCodes": ["DIM_warehouse_catalog_sales"],
            },
            {
                "code": "MEAS_web_net_profit",
                "name": "网络净利润",
                "tables": ["web_sales"],
                "dimensionCodes": ["DIM_warehouse"],
            },
        ],
        "dimensions": [
            {
                "code": "DIM_warehouse",
                "name": "仓库",
                "isTime": False,
                "viewType": 0,
                "tables": ["web_sales"],
            },
            {
                "code": "DIM_warehouse_catalog_sales",
                "name": "仓库",
                "isTime": False,
                "viewType": 0,
                "tables": ["catalog_sales"],
            },
        ],
    }
    service = AdSemanticService(catalog, fake_da_query, passthrough_filters)

    payload = service.translate_query({
        "measures": ["ad.catalog_net_profit", "ad.web_net_profit"],
        "dimensions": ["ad.warehouse"],
    })

    assert [item["code"] for item in payload["configureList"]] == [
        "MEAS_catalog_net_profit",
        "MEAS_web_net_profit",
        "DIM_warehouse",
    ]
