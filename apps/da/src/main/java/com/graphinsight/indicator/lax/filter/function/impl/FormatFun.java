package com.graphinsight.indicator.lax.filter.function.impl;

import com.graphinsight.indicator.lax.filter.Node;
import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.filter.function.mode.CalculateParam;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.service.ChartQueryService;

import java.text.DecimalFormat;
import java.util.List;

public class FormatFun implements Function<List, Node> {

    private Integer _IDX_VALUE = Integer.valueOf(0);

    private Integer _FORMAT = Integer.valueOf(1);

    private List paramList;

    @Override
    public Function build(List paramList, Tuple tuple, ChartQueryService chartQueryService) {
        this.paramList = paramList;
        return this;
    }

    @Override
    public Node apply() {

        Object value = paramList.get(_IDX_VALUE);
        if (value instanceof Node) {
            value = ((Node)value).result;
        }

        Object formatObj = paramList.get(_FORMAT);

        if (formatObj instanceof Node) {
            formatObj = ((Node)formatObj).result;
        }
        String format = String.valueOf(formatObj);
        format = format.replaceAll("'", "");
        String result = String.format(format, value);
        Node node = new Node();
        node.setResult(result);

        return node;

    }

}
