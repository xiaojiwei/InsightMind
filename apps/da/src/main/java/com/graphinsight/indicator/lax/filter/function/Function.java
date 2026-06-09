package com.graphinsight.indicator.lax.filter.function;

import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.service.ChartQueryService;

import java.util.List;

public interface Function<K, V> {

    Function build(K paramList, Tuple tuple, ChartQueryService chartQueryService);
    V apply();
}
