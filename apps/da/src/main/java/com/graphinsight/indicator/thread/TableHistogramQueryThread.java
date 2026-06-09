package com.graphinsight.indicator.thread;

import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.manager.HistogramManager;
import com.graphinsight.indicator.model.dto.HistogramQueryResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * Author: lixiaolong
 * Date: 2023/1/4
 * Desc:
 */
@Slf4j
public class TableHistogramQueryThread implements Callable<HistogramQueryResult> {

    private HistogramManager histogramManager;


    private DwTable table;

    public TableHistogramQueryThread(HistogramManager histogramManager, DwTable table) {
        this.histogramManager = histogramManager;
        this.table = table;
    }

    @Override
    public HistogramQueryResult call() throws Exception {
        HistogramQueryResult result = null;
        try {
            result = histogramManager.tableQuery(table);
            histogramManager.saveTableNum(result);
        } catch (Exception e) {
            log.error("事实表条数查询异常,sql:{} \n:", result.getSql(), e);
        }
        return result;
    }
}
