package com.graphinsight.indicator.lax.filter;

import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.filter.function.impl.*;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.service.ChartQueryService;

import java.util.List;

/**
 * fun类厂
 */
public class FunctionFactory {

    public static Function buildFunction(String funName, List<Node> values, Tuple tuple, ChartQueryService chartQueryService) {

        Function fun = null;
        if ("Format".equalsIgnoreCase(funName)) {
            fun = new FormatFun();
            fun.build(values, tuple, chartQueryService);
        } else if ("Concatenate".equalsIgnoreCase(funName)) {
            fun = new ConcatenateFun();
            fun.build(values, tuple, chartQueryService);
        } else  if ("Workday".equalsIgnoreCase(funName)) {
            fun = new WorkdayFun();
            fun.build(values, tuple, chartQueryService);
        } else if ("SelectColumns".equalsIgnoreCase(funName)) {
            fun = new SelectColumnsFun();
            fun.build(values, tuple, chartQueryService);
        } else if ("CDP".equalsIgnoreCase(funName)) {
            fun = new CDPFun();
            fun.build(values, tuple, chartQueryService);
        } else if ("ER".equalsIgnoreCase(funName)) {
            fun = new ERFun();
            fun.build(values, tuple, chartQueryService);
        } else {
            fun = new TtestFun();
            fun.build(values, tuple, chartQueryService);
        }

        return fun;


    }

}
