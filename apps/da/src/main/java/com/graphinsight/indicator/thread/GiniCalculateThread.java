package com.graphinsight.indicator.thread;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.enums.DimensionAnalysisTaskDetailStatusType;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.DimensionAnalysisManagerV2;
import com.graphinsight.indicator.model.dto.DimensionAnalysisTaskDetailDorisQueryResult;
import com.graphinsight.indicator.model.dto.GiniCalculateParam;
import com.graphinsight.indicator.model.vo.DimensionAnalysisGiniQueryVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisTaskDetailVO;
import com.graphinsight.indicator.util.gini.GiniCalculator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Author: lixiaolong
 * Date: 2022/7/12
 * Desc:
 */
@Slf4j
public class GiniCalculateThread implements Callable<DimensionAnalysisTaskDetailVO> {

    private DimensionAnalysisTaskDetailVO detail;
    private CacheManager cacheManager;
    private DimensionAnalysisManagerV2 dimensionAnalysisManager;
    private DimensionAnalysisGiniQueryVO dimensionAnalysisTask;

    public GiniCalculateThread(DimensionAnalysisTaskDetailVO detail, CacheManager cacheManager, DimensionAnalysisManagerV2 dimensionAnalysisManager, DimensionAnalysisGiniQueryVO dimensionAnalysisTask) {
        this.detail = detail;
        this.cacheManager = cacheManager;
        this.dimensionAnalysisManager = dimensionAnalysisManager;
        this.dimensionAnalysisTask = dimensionAnalysisTask;
    }

    @Override
    public DimensionAnalysisTaskDetailVO call() throws Exception{
        log.info("基尼系数计算任务 start ... ");
        if (Objects.nonNull(detail)) {
            GiniCalculateParam param;
            try {
                Map<String, Dimension> dimensionCodeMap = cacheManager.getMetadataCache().getAllDimensionCodeMap();
                Dimension dimension = dimensionCodeMap.get(detail.getDimCode());
                DimensionAnalysisTaskDetailDorisQueryResult dorisQueryResult = dimensionAnalysisManager.queryFromDoris(detail, dimensionAnalysisTask);
                param = dimensionAnalysisManager.buildParam(dorisQueryResult.getCurrentPageData(), dorisQueryResult.getPreviousPageData(), dimension);
                double gini = GiniCalculator.calculateGini(param);
                detail.setGiniValue(BigDecimal.valueOf(gini));
                detail.setStatus(DimensionAnalysisTaskDetailStatusType.COMPLETED.getCode());
            } catch (Exception e) {
                log.error("报告生成异常:",e);
                detail.setErrorMessage(e.getMessage());
                detail.setStatus(DimensionAnalysisTaskDetailStatusType.FAILED.getCode());
                // 有一个失败即为部分成功
            }
        } else {
            log.info("无可执行任务");
        }
        return detail;
    }

}
