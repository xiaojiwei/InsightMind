from rdflib import Graph, Namespace, RDF

from web_app import _ensure_public_date_dimensions, _ensure_public_shared_dimensions


def test_ensure_public_date_dimensions_adds_shared_date_dimension():
    ttl = """
@prefix ind: <http://indicator.xiaojw.com/ontology#> .
@prefix inst: <http://indicator.xiaojw.com/instance/> .

inst:dim_web_month a ind:Dimension ;
    ind:code "DIM_web_sales_date_month" ;
    ind:cnName "网络销售月" ;
    ind:hierarchyCode "h_date" ;
    ind:levelCode "month" ;
    ind:hasDimApp inst:da_web_month .

inst:dim_catalog_month a ind:Dimension ;
    ind:code "DIM_date_month_catalog_sales" ;
    ind:cnName "月" ;
    ind:hierarchyCode "h_date" ;
    ind:levelCode "month" ;
    ind:hasDimApp inst:da_catalog_month .

inst:da_web_month a ind:DimensionApp ;
    ind:dimFactTable inst:tbl_web_sales .

inst:da_catalog_month a ind:DimensionApp ;
    ind:dimFactTable inst:tbl_catalog_sales .
"""

    out = _ensure_public_date_dimensions(ttl, lambda _msg: None)
    graph = Graph()
    graph.parse(data=out, format="turtle")
    ind = Namespace("http://indicator.xiaojw.com/ontology#")
    inst = Namespace("http://indicator.xiaojw.com/instance/")

    public_dim = inst.dim_date_month
    assert (public_dim, RDF.type, ind.Dimension) in graph
    assert str(graph.value(public_dim, ind.code)) == "DIM_date_month"
    assert set(graph.objects(public_dim, ind.hasDimApp)) == {
        inst.da_web_month,
        inst.da_catalog_month,
    }


def test_ensure_public_shared_dimensions_links_same_name_apps():
    ttl = """
@prefix ind: <http://indicator.xiaojw.com/ontology#> .
@prefix inst: <http://indicator.xiaojw.com/instance/> .

inst:dim_warehouse a ind:Dimension ;
    ind:code "DIM_warehouse" ;
    ind:cnName "仓库" ;
    ind:viewTypeCode 0 ;
    ind:hasDimApp inst:da_web_warehouse .

inst:dim_catalog_warehouse a ind:Dimension ;
    ind:code "DIM_warehouse_catalog_sales" ;
    ind:cnName "仓库" ;
    ind:viewTypeCode 0 ;
    ind:hasDimApp inst:da_catalog_warehouse .

inst:da_web_warehouse a ind:DimensionApp ;
    ind:dimFactTable inst:tbl_web_sales .

inst:da_catalog_warehouse a ind:DimensionApp ;
    ind:dimFactTable inst:tbl_catalog_sales .

inst:tbl_web_sales ind:tableName "web_sales" .
inst:tbl_catalog_sales ind:tableName "catalog_sales" .
"""

    out = _ensure_public_shared_dimensions(ttl, lambda _msg: None)
    graph = Graph()
    graph.parse(data=out, format="turtle")
    ind = Namespace("http://indicator.xiaojw.com/ontology#")
    inst = Namespace("http://indicator.xiaojw.com/instance/")

    assert set(graph.objects(inst.dim_warehouse, ind.hasDimApp)) == {
        inst.da_web_warehouse,
        inst.da_catalog_warehouse,
    }
