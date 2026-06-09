package com.graphinsight.indicator.lax.filter.function.impl;

import com.graphinsight.indicator.lax.filter.Node;
import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.service.ChartQueryService;

import java.util.LinkedList;
import java.util.List;

public class TtestFun implements Function<List<Node>, Node> {

    private List<Node> paramList = new LinkedList<>();

    private Tuple tuple;

    @Override
    public Function build(List<Node> paramList, Tuple tuple, ChartQueryService chartQueryService) {
        this.paramList = paramList;
        this.tuple = tuple;
        return this;
    }

    @Override
    public Node apply() {
        Double result = Double.valueOf(0);
        for (Node param : this.paramList) {
            result += (Double)param.result;
        }

        Node rNode = new Node();
        rNode.result = result;
        return rNode;
    }

}
