from rdflib import Namespace, RDF

from kg_builder.business_kg.reasoner import BusinessKGReasoner


def test_reasoner_materializes_dimension_compatibility_and_measure_dependencies():
    ttl = """
@prefix ind: <http://indicator.insightmind.com/ontology#> .
@prefix inst: <http://indicator.insightmind.com/instance/> .

inst:meas_order_cnt a ind:Measure ;
    ind:code "MEAS_order_cnt" ;
    ind:cnName "订单数" ;
    ind:hasMeasureApp inst:ma_order_cnt .

inst:meas_pay_rate a ind:Measure ;
    ind:code "MEAS_pay_rate" ;
    ind:cnName "支付率" ;
    ind:hasMeasureApp inst:ma_pay_rate .

inst:ma_order_cnt a ind:MeasureApp ;
    ind:appliesToTable inst:tbl_order .

inst:ma_pay_rate a ind:MeasureApp ;
    ind:appliesToTable inst:tbl_order ;
    ind:dependsOnMeasApp inst:ma_order_cnt .

inst:dim_city a ind:Dimension ;
    ind:code "DIM_city" ;
    ind:cnName "城市" ;
    ind:hasDimApp inst:da_city_order .

inst:da_city_order a ind:DimensionApp ;
    ind:dimFactTable inst:tbl_order .

inst:tbl_order a ind:DwTable ;
    ind:tableName "dwd_order" .
"""

    inferred = BusinessKGReasoner().infer_from_turtle(ttl)
    ind = Namespace("http://indicator.insightmind.com/ontology#")
    inst = Namespace("http://indicator.insightmind.com/instance/")

    assert (inst.meas_order_cnt, ind.compatibleDimension, inst.dim_city) in inferred
    assert (inst.meas_pay_rate, ind.compatibleDimension, inst.dim_city) in inferred
    assert (inst.meas_pay_rate, ind.upstreamMeasure, inst.meas_order_cnt) in inferred
    assert (inst.meas_order_cnt, ind.downstreamMeasure, inst.meas_pay_rate) in inferred

    evidence_nodes = list(inferred.subjects(RDF.type, ind.Inference))
    assert evidence_nodes
    assert any(
        str(inferred.value(node, ind.inferredByRule)) == "compatible_dimension.shared_fact_table"
        for node in evidence_nodes
    )
