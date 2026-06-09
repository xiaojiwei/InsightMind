package com.graphinsight.indicator.lax.filter.function.impl;

import com.graphinsight.indicator.lax.filter.Node;
import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.service.ChartQueryService;
import org.springframework.util.CollectionUtils;

import java.util.List;

public class ConcatenateFun implements Function<List, Node> {

    private List paramList;

    @Override
    public Function build(List paramList, Tuple tuple, ChartQueryService chartQueryService) {
        this.paramList = paramList;
        return this;
    }

    @Override
    public Node apply() {

        Node node = new Node();
        if (!CollectionUtils.isEmpty(this.paramList)) {
            StringBuilder result = new StringBuilder();
            for (Object param : this.paramList) {

                if (param instanceof Node) {
                    param = ((Node) param).result;
                }
                String v = String.valueOf(param).replaceAll("'", "");
                result.append(v);
            }
            node.setResult(result.toString());
        }

        return node;

    }

}
