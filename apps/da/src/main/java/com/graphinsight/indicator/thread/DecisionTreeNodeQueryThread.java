package com.graphinsight.indicator.thread;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DecisionTreeDetail;
import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.enums.DecisionTreeNodeType;
import com.graphinsight.indicator.manager.DimensionAnalysisManager;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.model.vo.DecisionTreeContributionInfo;
import com.graphinsight.indicator.model.vo.DecisionTreeNode;
import com.graphinsight.indicator.model.vo.DecisionTreeNodeData;
import com.graphinsight.indicator.model.vo.DecisionTreeQueryVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisDetailVO;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.util.NumberFormatUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Author: lixiaolong
 * Date: 2022/9/20
 * Desc:
 */
@Slf4j
public class DecisionTreeNodeQueryThread implements Callable<DecisionTreeNode> {

    private DecisionTreeQueryVO decisionTreeQueryVO;

    private DecisionTreeDetail detail;

    private ChartQueryService chartQueryService;

    private IMeasureService measureService;

    private MeasureManager measureManager;

    private Set<String> authMeasureCodes;

    private String username;

    private Long spaceId;

    private DimensionAnalysisManager dimensionAnalysisManager;

    public DecisionTreeNodeQueryThread(DecisionTreeQueryVO decisionTreeQueryVO, DecisionTreeDetail detail, ChartQueryService chartQueryService, IMeasureService measureService, MeasureManager measureManager, Set<String> authMeasureCodes, String username, Long spaceId, DimensionAnalysisManager dimensionAnalysisManager) {
        this.decisionTreeQueryVO = decisionTreeQueryVO;
        this.detail = detail;
        this.chartQueryService = chartQueryService;
        this.measureService = measureService;
        this.measureManager = measureManager;
        this.authMeasureCodes = authMeasureCodes;
        this.username = username;
        this.spaceId = spaceId;
        this.dimensionAnalysisManager = dimensionAnalysisManager;
    }

    @Override
    public DecisionTreeNode call() throws Exception {
        DecisionTreeNode treeNode = new DecisionTreeNode();
        treeNode.setNodeType(DecisionTreeNodeType.convert(DecisionTreeNodeType.getType(detail.getNodeType())).getCode());
        if (!DecisionTreeNodeType.isOperator(DecisionTreeNodeType.getType(detail.getNodeType()))){
            Measure measure = getMeasure(detail);
            treeNode.setRatio(measure == null ? false : "%".equals(measure.getUnit()));
            treeNode.setNodeData(createNodeData(measure));
        } else {
            treeNode.setNodeData(createNodeData(null));
        }
        treeNode.setParentCode(detail.getParentCode());
        treeNode.setTreeLevelSeq(detail.getTreeLevelSeq());
        return treeNode;
    }

    private DecisionTreeNodeData createNodeData(Measure measure){
        DecisionTreeNodeData nodeData = new DecisionTreeNodeData();
        nodeData.setCurrentDate(decisionTreeQueryVO.getCurrentDate());
        nodeData.setBaseDate(decisionTreeQueryVO.getBaseDate());
        nodeData.setDimCode(decisionTreeQueryVO.getDimCode());
        Boolean flag = Objects.nonNull(decisionTreeQueryVO.getBaseDate())
                && Objects.nonNull(decisionTreeQueryVO.getCurrentDate())
                && Objects.nonNull(decisionTreeQueryVO.getDimCode());
        if (measure != null && flag){
            Set<String> dimCodes = new HashSet<>();
            dimCodes.add(decisionTreeQueryVO.getDimCode());
            nodeData.setDrillDown(measureManager.canDrillDown(measure.getCode(),dimCodes));
            nodeData.setHasAuth(authMeasureCodes.contains(measure.getCode()));
            BigDecimal currentValue = getValueFromDoris(measure.getCode(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getCurrentDate(), username, spaceId);
            BigDecimal baseValue = getValueFromDoris(measure.getCode(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getBaseDate(), username, spaceId);
            nodeData.setPreviousPeriodValue(baseValue);
            nodeData.setCurrentPeriodValue(currentValue);
            DecisionTreeContributionInfo decisionTreeContributionInfo = new DecisionTreeContributionInfo();
            fillDimensionAnalysisInfo(decisionTreeContributionInfo,decisionTreeQueryVO,measure.getCode());
            nodeData.setContributionInfo(decisionTreeContributionInfo);
        } else {
            nodeData.setDrillDown(false);
            nodeData.setHasAuth(false);
        }
        nodeData.setNodeName(measure == null ? detail.getNodeValue() : measure.getCnName());
        nodeData.setNodeCode(detail.getNodeValue());
        return nodeData;
    }

    private Measure getMeasure(DecisionTreeDetail detail){
        Measure measure = Optional.ofNullable(detail.getNodeValue())
                .map(code -> measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, code)))
                .orElse(null);
        return measure;
    }

    private void fillDimensionAnalysisInfo(DecisionTreeContributionInfo info, DecisionTreeQueryVO decisionTreeQueryVO, String measCode) {
        List<DimensionAnalysisTask> dimensionAnalysisTasks = listDimensionAnalysisTask(decisionTreeQueryVO, measCode);
        if (!CollectionUtils.isEmpty(dimensionAnalysisTasks)) {
            DimensionAnalysisTask dimensionAnalysisTask = dimensionAnalysisTasks.stream().sorted(Comparator.comparing(DimensionAnalysisTask::getId).reversed()).findFirst().orElse(null);
            if (dimensionAnalysisTask != null) {
                DimensionAnalysisDetailVO dimensionAnalysisDetailVO = dimensionAnalysisManager.detail(dimensionAnalysisTask.getId());
                info.setDimensionAnalysisDetail(dimensionAnalysisDetailVO);
            }
        }
    }

    private List<DimensionAnalysisTask> listDimensionAnalysisTask(DecisionTreeQueryVO decisionTreeQueryVO, String measCode) {
        return dimensionAnalysisManager.listExistedTask(measCode,
                decisionTreeQueryVO.getDimCode(),
                decisionTreeQueryVO.getBaseDate(),
                decisionTreeQueryVO.getCurrentDate(),
                decisionTreeQueryVO.getSpaceId(),
                UserThreadLocalUtil.getUserName());
    }


    private BigDecimal getValueFromDoris(String measCode, String dimCode, String dimValue, String username, Long spaceId) {
        try {
            String value = chartQueryService.execOnlySingleMeasure(measCode, dimCode, dimValue, new HashSet<>(), username, spaceId);
            return NumberFormatUtil.format(value);
        } catch (Exception e) {
            log.error("chartQueryService.execOnlySingleMeasure 执行异常,measCode: {},dimCode: {},dimValue: {},username: {},spaceId: {}", measCode, dimCode, dimValue, username, spaceId, e);
            return null;
        }
    }

}
