package com.graphinsight.indicator.thread;

import com.graphinsight.indicator.manager.HistogramManager;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.Table;
import com.graphinsight.indicator.model.dto.HistogramQueryResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * Date: 2023/1/4
 * Desc:
 */
@Slf4j
public class DimensionHistogramQueryThread implements Callable<HistogramQueryResult> {

    private HistogramManager histogramManager;

    private Dimension dimension;

    private Table table;

    public DimensionHistogramQueryThread(HistogramManager histogramManager, Dimension dimension, Table table) {
        this.histogramManager = histogramManager;
        this.dimension = dimension;
        this.table = table;
    }

    @Override
    public HistogramQueryResult call() throws Exception {
        HistogramQueryResult result = null;
        try {
            result = histogramManager.dimensionQuery(dimension, table);
            histogramManager.saveDimensionNum(result);
        } catch (Exception e) {
            log.error("维度数量查询异常,sql:{} \n:", result.getSql(), e);
        }
        return result;
    }
}
