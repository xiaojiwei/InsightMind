from web_app import (
    _pivot_dimension_compatible_with_measure,
    _pivot_resolve_dimensions_for_measures,
    _pivot_resolve_filters_for_measures,
)


CATALOG = {
    "measures": [
        {
            "code": "MEAS_catalog_sales_amount",
            "name": "目录销售额",
            "tables": ["catalog_sales"],
            "dimensionCodes": ["DIM_date_week_catalog_sales"],
        },
        {
            "code": "MEAS_web_sales_amount",
            "name": "网络销售额",
            "tables": ["web_sales"],
            "dimensionCodes": ["DIM_web_sales_date_week"],
        },
    ],
    "dimensions": [
        {
            "code": "DIM_date_week_catalog_sales",
            "name": "周",
            "isTime": True,
            "viewType": 2,
            "levelCode": "week",
            "hierarchyCode": "h_date",
            "tables": ["catalog_sales"],
        },
        {
            "code": "DIM_web_sales_date_week",
            "name": "周",
            "isTime": True,
            "viewType": 2,
            "levelCode": "week",
            "hierarchyCode": "h_date",
            "tables": ["web_sales"],
        },
    ],
}


def test_pivot_resolves_public_dimension_for_selected_measure():
    measures = [CATALOG["measures"][1]]

    resolved = _pivot_resolve_dimensions_for_measures(
        [{"code": "DIM_date_week", "name": "周"}],
        measures,
        CATALOG,
    )

    assert resolved[0]["code"] == "DIM_web_sales_date_week"
    assert resolved[0]["name"] == "周"
    assert resolved[0]["isTime"] is True


def test_pivot_resolves_public_filter_for_selected_measure():
    measures = [CATALOG["measures"][1]]

    resolved = _pivot_resolve_filters_for_measures(
        [{"code": "DIM_date_week", "operator": "in", "values": ["202623"]}],
        measures,
        CATALOG,
    )

    assert resolved[0]["code"] == "DIM_web_sales_date_week"
    assert resolved[0]["viewType"] == 2
    assert resolved[0]["filterMode"] == "time"


def test_pivot_public_dimension_is_compatible_with_multiple_fact_tables():
    measures = CATALOG["measures"]

    resolved = _pivot_resolve_dimensions_for_measures(
        [{"code": "DIM_date_week", "name": "周"}],
        measures,
        CATALOG,
    )

    assert resolved[0]["code"] == "DIM_date_week"
    assert sorted(resolved[0]["sourceCodes"]) == [
        "DIM_date_week_catalog_sales",
        "DIM_web_sales_date_week",
    ]
    assert all(
        _pivot_dimension_compatible_with_measure(resolved[0], measure, CATALOG)
        for measure in measures
    )
