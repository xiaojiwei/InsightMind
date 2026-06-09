package com.graphinsight.indicator.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.DecisionTree;
import com.graphinsight.indicator.auto.entity.DecisionTreeDetail;
import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.service.IDecisionTreeDetailService;
import com.graphinsight.indicator.auto.service.IDecisionTreeService;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.enums.ContributionCalculationType;
import com.graphinsight.indicator.enums.DecisionTreeNodeType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.DecisionTreeContributionInfo;
import com.graphinsight.indicator.model.vo.DecisionTreeFrontNodeType;
import com.graphinsight.indicator.model.vo.DecisionTreeNode;
import com.graphinsight.indicator.model.vo.DecisionTreeNodeData;
import com.graphinsight.indicator.model.vo.DecisionTreeQueryVO;
import com.graphinsight.indicator.model.vo.DecisionTreeVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisDetailVO;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.thread.DecisionTreeNodeQueryThread;
import com.graphinsight.indicator.util.NumberFormatUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.graphinsight.indicator.util.contribution.ContributionStrategy;
import com.graphinsight.indicator.util.contribution.ContributionStrategyHolder;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationResult;
import com.graphinsight.indicator.util.contribution.bean.ContributionOriginQueryParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/6/20
 * Desc:
 */
@Service
@Slf4j
public class DecisionTreeManager {

    @Autowired
    IDecisionTreeService decisionTreeService;
    @Autowired
    IDecisionTreeDetailService decisionTreeDetailService;
    @Autowired
    IMeasureService measureService;
    @Autowired
    ChartQueryService chartQueryService;
    @Resource
    ThreadPoolTaskExecutor executor;

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        decisionTreeService.removeById(id);
        decisionTreeDetailService.remove(Wrappers.<DecisionTreeDetail>lambdaQuery().eq(DecisionTreeDetail::getTreeId, id));
    }

    public List<DecisionTreeVO> listByMeasCode(String measCode, Long spaceId) {
        List<DecisionTree> decisionTrees = decisionTreeService.list(Wrappers.<DecisionTree>lambdaQuery()
                .eq(DecisionTree::getMeasCode, measCode)
                .eq(DecisionTree::getSpaceId, spaceId));
        if (CollectionUtils.isEmpty(decisionTrees)) {
            return Collections.EMPTY_LIST;
        }
        //
        DecisionTree defaultTree = decisionTrees.stream().filter(decisionTree -> Objects.equals(decisionTree.getIsDefault(), YesNoType.YES.getCode())).findFirst().orElse(null);
        List<DecisionTree> decisionTreeList = decisionTrees.stream()
                .filter(decisionTree -> Objects.equals(decisionTree.getIsDefault(), YesNoType.NO.getCode()))
                .sorted(Comparator.comparing(DecisionTree::getUpdateTime).reversed())
                .collect(Collectors.toList());
        List<DecisionTree> sortedTrees = new LinkedList<>();
        if (Objects.nonNull(defaultTree)) {
            sortedTrees.add(defaultTree);
        }
        sortedTrees.addAll(decisionTreeList);

        List<DecisionTreeVO> decisionTreeVOS = sortedTrees.stream()
                .map(dt -> {
                    DecisionTreeQueryVO queryVO = new DecisionTreeQueryVO();
                    queryVO.setId(dt.getId());
                    queryVO.setSpaceId(spaceId);
                    return detail(queryVO);
                }).collect(Collectors.toList());
        return decisionTreeVOS;
    }

    @CheckCacheVersion
    public DecisionTreeVO detail(DecisionTreeQueryVO decisionTreeQueryVO) {
        DecisionTreeVO decisionTreeVO = new DecisionTreeVO();

        // 获取所有节点
        DecisionTree decisionTree = decisionTreeService.getById(decisionTreeQueryVO.getId());
        if (Objects.isNull(decisionTree)) {
            throw IndicatorParamNotValidException.error("决策树不存在,ID: " + decisionTreeQueryVO.getId());
        }
        decisionTreeVO.setTreeName(decisionTree.getName());
        decisionTreeVO.setId(decisionTree.getId());
        decisionTreeVO.setIsDefault(Objects.equals(YesNoType.YES.getCode(), decisionTree.getIsDefault()));
        List<DecisionTreeDetail> decisionTreeDetails = decisionTreeDetailService.list(Wrappers.<DecisionTreeDetail>lambdaQuery()
                .eq(DecisionTreeDetail::getTreeId, decisionTreeQueryVO.getId()));

        // 并发查询doris 获取节点的本期值、基期值
        if (!CollectionUtils.isEmpty(decisionTreeDetails)) {
            // 用户拥有权限的指标集合
            UserContext userContext = userManager.getUserContext(decisionTreeQueryVO.getSpaceId(), UserThreadLocalUtil.getUserName());
            List<Measure> authMeasures = userContext.getAuthMeasures();
            Set<String> authMeasCodes = authMeasures.stream().map(Measure::getCode).collect(Collectors.toSet());

            String userName = UserThreadLocalUtil.getUserName();
            List<DecisionTreeNodeQueryThread> tasks = decisionTreeDetails.stream().map(detail -> {
                DecisionTreeNodeQueryThread thread = new DecisionTreeNodeQueryThread(decisionTreeQueryVO, detail, chartQueryService, measureService, measureManager, authMeasCodes, userName, decisionTree.getSpaceId(), dimensionAnalysisManager);
                return thread;
            }).collect(Collectors.toList());
            try {
                List<Future<DecisionTreeNode>> futures = executor.getThreadPoolExecutor().invokeAll(tasks);
                List<DecisionTreeNode> treeNodes = new ArrayList<>();
                // 收集结果
                futures.forEach(f -> {
                    DecisionTreeNode decisionTreeNode = null;
                    try {
                        decisionTreeNode = f.get();
                        treeNodes.add(decisionTreeNode);
                    } catch (InterruptedException e) {
                        log.error("获取执行结果异常:", e);
                        throw IndicatorParamNotValidException.error("查询异常:" + e);
                    } catch (ExecutionException e) {
                        log.error("获取执行结果异常:", e);
                        throw IndicatorParamNotValidException.error("查询异常:" + e);
                    }
                });
                // 拼装树状结构
                DecisionTreeNode rootNode = treeNodes.stream().filter(tree -> Objects.isNull(tree.getParentCode()))
                        .findFirst()
                        .orElseThrow(() -> IndicatorParamNotValidException.error("指标决策树的跟节点不存在,树ID:" + decisionTreeQueryVO.getId()));
                calContribution(rootNode,decisionTreeQueryVO,null,null);
                findChildren(decisionTreeQueryVO, rootNode, treeNodes);
                decisionTreeVO.setDecisionTreeNode(rootNode);
            } catch (InterruptedException e) {
                log.error("获取执行结果异常:", e);
                throw IndicatorParamNotValidException.error("查询异常:" + e);
            }


        }
        return decisionTreeVO;
    }

    private void findChildren(DecisionTreeQueryVO decisionTreeQueryVO, DecisionTreeNode parentDecisionTreeNode, List<DecisionTreeNode> decisionTreeNodes) {
        List<DecisionTreeNode> children = decisionTreeNodes.stream()
                .filter(tree -> Objects.equals(tree.getParentCode(), parentDecisionTreeNode.getNodeData().getNodeCode()))
                .sorted(Comparator.comparing(DecisionTreeNode::getTreeLevelSeq))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(children)) {
            if (Objects.nonNull(decisionTreeQueryVO.getBaseDate())
                    && Objects.nonNull(decisionTreeQueryVO.getCurrentDate())
                    && Objects.nonNull(decisionTreeQueryVO.getDimCode())) {
                // 计算各个子节点的贡献度
                children.forEach(c -> calContribution(c,decisionTreeQueryVO,parentDecisionTreeNode,children));
            }
            // 设置下一层级的节点
            children.forEach(c -> {
                if (! Objects.equals(c.getNodeData().getNodeCode(),parentDecisionTreeNode.getNodeData().getNodeCode())){
                    findChildren(decisionTreeQueryVO, c, decisionTreeNodes);
                }
            });
            parentDecisionTreeNode.setChildren(children);
        }
    }

    private void calContribution(DecisionTreeNode node,DecisionTreeQueryVO decisionTreeQueryVO,DecisionTreeNode parentNode, List<DecisionTreeNode> brother){
        ContributionCalculationParam param = createParam(decisionTreeQueryVO, node, parentNode, brother);
        ContributionStrategy strategy = ContributionStrategyHolder.getStrategy(param.getContributionCalculationType());
        ContributionCalculationResult calculationResult;
        try {
            calculationResult = strategy.calculate(param);
            DecisionTreeContributionInfo contributionInfo = node.getNodeData().getContributionInfo();
            if (contributionInfo != null){
                DecisionTreeContributionInfo info = convert(calculationResult);
                BeanUtils.copyProperties(info, contributionInfo, "dimensionAnalysisDetail");
            }
        } catch (Exception e) {
            log.error("贡献度计算异常,参数param:{},e:", param, e);
        }
    }

    /**
     * @param decisionTreeQueryVO
     * @return
     */
    public ContributionCalculationParam createParam(DecisionTreeQueryVO decisionTreeQueryVO, DecisionTreeNode node,
                                                    DecisionTreeNode parent, List<DecisionTreeNode> levelNodes) {
        ContributionCalculationParam.ContributionCalculationParamBuilder builder = ContributionCalculationParam.builder();
        ContributionCalculationType contributionCalculationType = ContributionCalculationType.DEFAULT;
        ContributionOriginQueryParam param = new ContributionOriginQueryParam();
        param.setMeasCode(node.getNodeData().getNodeCode());
        param.setBaseDate(decisionTreeQueryVO.getBaseDate());
        param.setCurrentDate(decisionTreeQueryVO.getCurrentDate());
        param.setUpperLayerMeasCode(node.getParentCode());
        param.setDimCode(decisionTreeQueryVO.getDimCode());

        if (parent != null){
            builder.upperLayerPreviousPeriodValue(parent.getNodeData().getPreviousPeriodValue());
            builder.upperLayerCurrentPeriodValue(parent.getNodeData().getCurrentPeriodValue());
        } else {
            builder.upperLayerPreviousPeriodValue(BigDecimal.ZERO);
            builder.upperLayerCurrentPeriodValue(BigDecimal.ZERO);
        }
        builder.previousPeriodValue(node.getNodeData().getPreviousPeriodValue());
        builder.currentPeriodValue(node.getNodeData().getCurrentPeriodValue());

        if (!CollectionUtils.isEmpty(levelNodes)) {
            DecisionTreeNode operatorTreeNode = levelNodes.stream()
                    .filter(td -> DecisionTreeNodeType.isOperator(DecisionTreeNodeType.getType(td.getNodeType())))
                    .findFirst()
                    .orElseThrow(() -> IndicatorParamNotValidException.error("该决策树有非法子节点：不存在算术运算符,请查看配置"));
            DecisionTreeNodeType treeNodeType = DecisionTreeFrontNodeType.convert(DecisionTreeFrontNodeType.getType(operatorTreeNode.getNodeType()),operatorTreeNode.getNodeData().getNodeCode());
            switch (treeNodeType) {
                case SUBTRACTION:
                    //减法，且指标在第一个位置 该指标就是被减数
                    builder.minuend(Objects.equals(levelNodes.get(0).getNodeData().getNodeCode(), node.getNodeData().getNodeCode()));
                    contributionCalculationType = ContributionCalculationType.SUBTRACTION;
                    break;
                case DIVISION:
                    //除法，且指标在第一个位置 该指标就是被除数
                    if (Objects.equals(levelNodes.get(0).getNodeData().getNodeCode(), node.getNodeData().getNodeCode())) {
                        builder.dividend(true);
                        DecisionTreeNode last = levelNodes.get(levelNodes.size() - 1);
                        builder.currentPeriodOppositeValue(last.getNodeData().getCurrentPeriodValue());
                        builder.previousPeriodOppositeValue(last.getNodeData().getPreviousPeriodValue());
                    } else {
                        builder.dividend(false);
                        DecisionTreeNode first = levelNodes.get(0);
                        builder.currentPeriodOppositeValue(first.getNodeData().getCurrentPeriodValue());
                        builder.previousPeriodOppositeValue(first.getNodeData().getPreviousPeriodValue());
                    }
                    contributionCalculationType = ContributionCalculationType.DIVISION;
                    break;
                case ADDITION:
                    contributionCalculationType = ContributionCalculationType.ADDITION;
                    break;
                case MULTIPLICATION:
                    contributionCalculationType = ContributionCalculationType.MULTIPLICATION;
                    break;
            }
        }
        builder.isRatio(node.isRatio());
        builder.contributionCalculationType(contributionCalculationType);
        builder.originQueryParam(param);
        return builder.build();
    }


    /**
     * 贡献度结果转换
     * 服务端对象 -> 前端对象
     *
     * @param calculationResult
     * @return
     */
    private DecisionTreeContributionInfo convert(ContributionCalculationResult calculationResult) {
        DecisionTreeContributionInfo result = new DecisionTreeContributionInfo();
        result.setContributionValue(calculationResult.getContributionValue() == null ? null : calculationResult.getContributionValue().setScale(4, BigDecimal.ROUND_DOWN).toString());
        result.setCurrentPeriodValue(calculationResult.getCurrentPeriodValue() == null ? null : calculationResult.getCurrentPeriodValue().setScale(2, BigDecimal.ROUND_DOWN).toString());
        result.setDeltaValue(calculationResult.getDeltaValue() == null ? null : calculationResult.getDeltaValue().setScale(2, BigDecimal.ROUND_DOWN).toString());
        result.setDeltaValueRate(calculationResult.getDeltaValueRate() == null ? null : calculationResult.getDeltaValueRate().setScale(4, BigDecimal.ROUND_DOWN).toString());
        result.setPreviousPeriodValue(calculationResult.getPreviousPeriodValue() == null ? null : calculationResult.getPreviousPeriodValue().setScale(2, BigDecimal.ROUND_DOWN).toString());
        return result;
    }

    @Autowired
    UserManager userManager;
    @Autowired
    MeasureManager measureManager;


    /**
     * 判断目标节点是不是比率类型
     *
     * @param allDecisionTreeDetail
     * @param targetTreeNode
     * @return
     */
    private boolean isRatio(List<DecisionTreeDetail> allDecisionTreeDetail, DecisionTreeDetail targetTreeNode) {
        Measure one = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, targetTreeNode.getNodeValue()));
        if (Objects.nonNull(one) && Objects.nonNull(one.getUnit())) {
            if (Objects.equals(one.getUnit(), "%")) {
                return true;
            } else {
                return false;
            }
        } else {
            List<DecisionTreeDetail> subDetails = allDecisionTreeDetail.stream()
                    .filter(detail -> Objects.equals(detail.getParentCode(), targetTreeNode.getNodeValue()))
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(subDetails)) {
                DecisionTreeDetail operatorTreeNode = subDetails.stream()
                        .filter(td -> DecisionTreeNodeType.isOperator(DecisionTreeNodeType.getType(td.getNodeType())))
                        .findFirst()
                        .orElseThrow(() -> IndicatorParamNotValidException.error("该决策树有非法子节点：不存在算术运算符,请查看配置"));
                DecisionTreeNodeType treeNodeType = DecisionTreeNodeType.getType(operatorTreeNode.getNodeType());
                switch (treeNodeType) {
                    case SUBTRACTION:
                    case ADDITION:
                    case MULTIPLICATION:
                        return false;
                    case DIVISION:
                        return true;
                }
            }
            return false;
        }

    }

    /**
     * @param decisionTreeQueryVO
     * @param measTreeNode          当前节点
     * @param upperLayerMeasCode
     * @param treeLevel             与当前节点平级的所有节点
     * @param allDecisionTreeDetail 当前树的所有节点
     * @return
     */
    public ContributionCalculationParam buildParam(DecisionTreeQueryVO decisionTreeQueryVO, DecisionTreeDetail measTreeNode,
                                                   String upperLayerMeasCode, List<DecisionTreeDetail> treeLevel,
                                                   List<DecisionTreeDetail> allDecisionTreeDetail) {
        ContributionCalculationParam.ContributionCalculationParamBuilder builder = ContributionCalculationParam.builder();
        userManager.getUserContext(decisionTreeQueryVO.getSpaceId(), UserThreadLocalUtil.getUserName());
        ContributionCalculationType contributionCalculationType = ContributionCalculationType.DEFAULT;
        ContributionOriginQueryParam param = new ContributionOriginQueryParam();
        param.setMeasCode(measTreeNode.getNodeValue());
        param.setBaseDate(decisionTreeQueryVO.getBaseDate());
        param.setCurrentDate(decisionTreeQueryVO.getCurrentDate());
        param.setUpperLayerMeasCode(upperLayerMeasCode);
        param.setDimCode(decisionTreeQueryVO.getDimCode());

        if (Objects.isNull(upperLayerMeasCode)) {
            // 没有上层节点，说明是根节点，上层节点基础数据按0处理
            builder.upperLayerCurrentPeriodValue(BigDecimal.ZERO)
                    .upperLayerPreviousPeriodValue(BigDecimal.ZERO);

        } else {
            // 有上层节点，获取上层节点的基础值
            BigDecimal upperLayerPreviousPeriodValue = getValueFromDoris(upperLayerMeasCode, decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getBaseDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
            BigDecimal upperLayerCurrentPeriodValue = getValueFromDoris(upperLayerMeasCode, decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getCurrentDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
            builder.upperLayerPreviousPeriodValue(upperLayerPreviousPeriodValue);
            builder.upperLayerCurrentPeriodValue(upperLayerCurrentPeriodValue);
        }
        BigDecimal previousPeriodValue = getValueFromDoris(measTreeNode.getNodeValue(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getBaseDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
        BigDecimal currentPeriodValue = getValueFromDoris(measTreeNode.getNodeValue(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getCurrentDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
        builder.previousPeriodValue(previousPeriodValue);
        builder.currentPeriodValue(currentPeriodValue);
        if (!CollectionUtils.isEmpty(treeLevel)) {
            DecisionTreeDetail operatorTreeNode = treeLevel.stream()
                    .filter(td -> DecisionTreeNodeType.isOperator(DecisionTreeNodeType.getType(td.getNodeType())))
                    .findFirst()
                    .orElseThrow(() -> IndicatorParamNotValidException.error("该决策树有非法子节点：不存在算术运算符,请查看配置"));
            DecisionTreeNodeType treeNodeType = DecisionTreeFrontNodeType.convert(DecisionTreeFrontNodeType.getType(operatorTreeNode.getNodeType()),operatorTreeNode.getNodeValue());

            switch (treeNodeType) {
                case SUBTRACTION:
                    //减法，且指标在第一个位置 该指标就是被减数
                    builder.minuend(Objects.equals(treeLevel.get(0).getId(), measTreeNode.getId()));
                    contributionCalculationType = ContributionCalculationType.SUBTRACTION;
                    break;
                case DIVISION:
                    //除法，且指标在第一个位置 该指标就是被除数
                    if (Objects.equals(treeLevel.get(0).getId(), measTreeNode.getId())) {
                        builder.dividend(true);
                        DecisionTreeDetail last = treeLevel.get(treeLevel.size() - 1);
                        BigDecimal currentPeriodOppositeValue = getValueFromDoris(last.getNodeValue(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getCurrentDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
                        BigDecimal previousPeriodOppositeValue = getValueFromDoris(last.getNodeValue(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getBaseDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
                        builder.currentPeriodOppositeValue(currentPeriodOppositeValue);
                        builder.previousPeriodOppositeValue(previousPeriodOppositeValue);
                    } else {
                        builder.dividend(false);
                        DecisionTreeDetail first = treeLevel.get(0);
                        BigDecimal currentPeriodOppositeValue = getValueFromDoris(first.getNodeValue(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getCurrentDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
                        BigDecimal previousPeriodOppositeValue = getValueFromDoris(first.getNodeValue(), decisionTreeQueryVO.getDimCode(), decisionTreeQueryVO.getBaseDate(), UserThreadLocalUtil.getUserName(), decisionTreeQueryVO.getSpaceId());
                        builder.currentPeriodOppositeValue(currentPeriodOppositeValue);
                        builder.previousPeriodOppositeValue(previousPeriodOppositeValue);
                    }
                    contributionCalculationType = ContributionCalculationType.DIVISION;
                    break;
                case ADDITION:
                    contributionCalculationType = ContributionCalculationType.ADDITION;
                    break;
                case MULTIPLICATION:
                    contributionCalculationType = ContributionCalculationType.MULTIPLICATION;
                    break;
            }
        }
        builder.isRatio(isRatio(allDecisionTreeDetail, measTreeNode));
        builder.contributionCalculationType(contributionCalculationType);
        builder.originQueryParam(param);
        return builder.build();
    }

    /**
     * 从doris查询数据
     *
     * @param measCode
     * @param dimCode
     * @param dimValue
     * @param username
     * @param spaceId
     * @return
     */
    public BigDecimal getValueFromDoris(String measCode, String dimCode, String dimValue, String username, Long spaceId) {
        try {
            String value = chartQueryService.execOnlySingleMeasure(measCode, dimCode, dimValue, new HashSet<>(), username, spaceId);
            return NumberFormatUtil.format(value);
        } catch (Exception e) {
            log.error("chartQueryService.execOnlySingleMeasure 执行异常,measCode: {},dimCode: {},dimValue: {},username: {},spaceId: {}", measCode, dimCode, dimValue, username, spaceId, e);
            return null;
        }
    }


    @Autowired
    IDimensionAnalysisTaskService dimensionAnalysisTaskService;
    @Autowired
    DimensionAnalysisManager dimensionAnalysisManager;

    private List<DimensionAnalysisTask> listDimensionAnalysisTask(DecisionTreeQueryVO decisionTreeQueryVO, String measCode) {
        return dimensionAnalysisManager.listExistedTask(measCode,
                decisionTreeQueryVO.getDimCode(),
                decisionTreeQueryVO.getBaseDate(),
                decisionTreeQueryVO.getCurrentDate(),
                decisionTreeQueryVO.getSpaceId(),
                UserThreadLocalUtil.getUserName());
    }


    /**
     * 填充节点数据
     *
     * @param decisionTreeQueryVO
     * @param decisionTreeNode
     * @param decisionTreeDetail
     * @param needContribution
     * @param authMeasCodes
     * @param allDecisionTreeDetail
     */
    private void fillTreeNodeData(DecisionTreeQueryVO decisionTreeQueryVO, DecisionTreeNode decisionTreeNode,
                                  DecisionTreeDetail decisionTreeDetail, boolean needContribution,
                                  Set<String> authMeasCodes, String upperLayerMeasCode,
                                  List<DecisionTreeDetail> treeLevel,
                                  List<DecisionTreeDetail> allDecisionTreeDetail) {
        decisionTreeNode.setNodeType(DecisionTreeNodeType.convert(DecisionTreeNodeType.getType(decisionTreeDetail.getNodeType())).getCode());
        DecisionTreeNodeData nodeData = new DecisionTreeNodeData();
        nodeData.setNodeCode(decisionTreeDetail.getNodeValue());
        nodeData.setNodeName(getNodeName(decisionTreeDetail));
        nodeData.setDimCode(decisionTreeQueryVO.getDimCode());
        nodeData.setBaseDate(decisionTreeQueryVO.getBaseDate());
        nodeData.setCurrentDate(decisionTreeQueryVO.getCurrentDate());
        decisionTreeNode.setNodeData(nodeData);
        if (!authMeasCodes.contains(decisionTreeDetail.getNodeValue())) {
            // 无指标权限
            nodeData.setHasAuth(false);
        } else if (needContribution) {
            // 有指标权限,且需要计算贡献度
            ContributionCalculationParam calculationParam = buildParam(decisionTreeQueryVO, decisionTreeDetail, upperLayerMeasCode, treeLevel, allDecisionTreeDetail);
            ContributionStrategy strategy = ContributionStrategyHolder.getStrategy(calculationParam.getContributionCalculationType());
            ContributionCalculationResult calculationResult = new ContributionCalculationResult();
            try {
                calculationResult = strategy.calculate(calculationParam);
            } catch (Exception e) {
                log.error("贡献度计算异常,参数param:{},e:", calculationParam, e);
            }
            DecisionTreeContributionInfo info = convert(calculationResult);
            fillDimensionAnalysisInfo(info, decisionTreeQueryVO, decisionTreeDetail.getNodeValue());
            nodeData.setContributionInfo(info);
            Set<String> dimCodes = new HashSet<>();
            dimCodes.add(decisionTreeQueryVO.getDimCode());
            boolean drillDown = measureManager.canDrillDown(decisionTreeDetail.getNodeValue(), dimCodes);
            nodeData.setDrillDown(drillDown);
        }
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

    private void findChildren(DecisionTreeQueryVO decisionTreeQueryVO, DecisionTreeNode parentDecisionTreeNode, List<DecisionTreeDetail> decisionTreeDetails, boolean needContribution, Set<String> authMeasCodes) {
        List<DecisionTreeDetail> subDetails = decisionTreeDetails.stream()
                .filter(tree -> Objects.equals(tree.getParentCode(), parentDecisionTreeNode.getNodeData().getNodeCode()))
                .sorted(Comparator.comparing(DecisionTreeDetail::getTreeLevelSeq))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(subDetails)) {
            // 设置当前层级节点
            List<DecisionTreeNode> children = new LinkedList<>();
            subDetails.forEach(treeNode -> {
                DecisionTreeNode child = new DecisionTreeNode();
                fillTreeNodeData(decisionTreeQueryVO, child, treeNode, needContribution, authMeasCodes, parentDecisionTreeNode.getNodeData().getNodeCode(), subDetails, decisionTreeDetails);
                children.add(child);
            });
            parentDecisionTreeNode.setChildren(children);
            // 设置下一层级的节点
            children.forEach(c -> findChildren(decisionTreeQueryVO, c, decisionTreeDetails, needContribution, authMeasCodes));
        }
    }


    private String getNodeName(DecisionTreeDetail detail) {
        if (Objects.equals(DecisionTreeNodeType.MEASURE.getCode(), detail.getNodeType())) {
            // 指标，获取中文名
            Measure measure = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, detail.getNodeValue()));
            if (Objects.isNull(measure)) {
                throw IndicatorParamNotValidException.error("指标Code:" + detail.getNodeValue() + " 被不存在或已被删除");
            }
            return measure.getCnName();
        } else {
            return detail.getNodeValue();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(DecisionTreeVO decisionTreeVO) {
        DecisionTree decisionTree = convert(decisionTreeVO);
        if (Objects.nonNull(decisionTreeVO.getId())) {
            // 更新树
            decisionTree.initUpdate();
        } else {
            // 保存树
            decisionTree.initCreate();
        }
        if (decisionTreeVO.getIsDefault()) {
            // 设置默认树了，需要把之前的默认树取消默认
            List<DecisionTree> originalDefaultTree = decisionTreeService.list(Wrappers.<DecisionTree>lambdaQuery()
                    .eq(DecisionTree::getMeasCode, decisionTree.getMeasCode())
                    .eq(DecisionTree::getIsDefault, YesNoType.YES.getCode()));
            if (!CollectionUtils.isEmpty(originalDefaultTree)) {
                for (DecisionTree tree : originalDefaultTree) {
                    tree.setIsDefault(YesNoType.NO.getCode());
                }
                decisionTreeService.updateBatchById(originalDefaultTree);
            }
        }
        decisionTreeService.saveOrUpdate(decisionTree);
        // 先删除详情，再保存新的详情
        decisionTreeDetailService.remove(Wrappers.<DecisionTreeDetail>lambdaQuery().eq(DecisionTreeDetail::getTreeId, decisionTree.getId()));
        List<DecisionTreeDetail> decisionTreeDetails = convert2Detail(decisionTreeVO, decisionTree.getId());
        decisionTreeDetailService.saveBatch(decisionTreeDetails);
    }

    private DecisionTree convert(DecisionTreeVO decisionTreeVO) {
        DecisionTree decisionTree = new DecisionTree();
        decisionTree.setName(decisionTreeVO.getTreeName());
        decisionTree.setId(decisionTreeVO.getId());
        decisionTree.setSpaceId(decisionTreeVO.getSpaceId());
        decisionTree.setIsDefault(decisionTreeVO.getIsDefault() ? YesNoType.YES.getCode() : YesNoType.NO.getCode());
        decisionTree.setMeasCode(decisionTreeVO.getDecisionTreeNode().getNodeData().getNodeCode());
        return decisionTree;
    }

    private DecisionTreeVO convert(DecisionTree decisionTree) {
        DecisionTreeVO decisionTreeVO = new DecisionTreeVO();
        decisionTreeVO.setTreeName(decisionTreeVO.getTreeName());
        decisionTreeVO.setId(decisionTreeVO.getId());
        decisionTreeVO.setSpaceId(decisionTree.getSpaceId());
        decisionTreeVO.setIsDefault(Objects.equals(decisionTree.getIsDefault(), YesNoType.YES.getCode()));
        return decisionTreeVO;
    }

    private List<DecisionTreeDetail> convert2Detail(DecisionTreeVO decisionTreeVO, Long treeId) {
        List<DecisionTreeDetail> result = new ArrayList<>();
        DecisionTreeNode decisionTreeNode = decisionTreeVO.getDecisionTreeNode();
        if (Objects.isNull(treeId)) {
            throw IndicatorParamNotValidException.error("决策树主键为空,操作失败");
        }
        DecisionTreeDetail decisionTreeDetail = new DecisionTreeDetail();
        decisionTreeDetail.setTreeId(treeId);
        DecisionTreeNodeType decisionTreeNodeType = DecisionTreeFrontNodeType.convert(DecisionTreeFrontNodeType.getType(decisionTreeNode.getNodeType()), decisionTreeNode.getNodeData().getNodeCode());
        decisionTreeDetail.setNodeType(decisionTreeNodeType.getCode());
        decisionTreeDetail.setNodeValue(decisionTreeNode.getNodeData().getNodeCode());
        decisionTreeDetail.setTreeLevelSeq(0);
        result.add(decisionTreeDetail);
        findChildren(result, decisionTreeNode, treeId);
        return result;
    }

    private void findChildren(List<DecisionTreeDetail> result, DecisionTreeNode parentNode, Long treeId) {
        List<DecisionTreeNode> children = parentNode.getChildren();
        if (!CollectionUtils.isEmpty(children)) {
            int seq = 0;
            for (DecisionTreeNode child : children) {
                DecisionTreeDetail childDetail = new DecisionTreeDetail();
                childDetail.setTreeId(treeId);
                childDetail.setParentCode(parentNode.getNodeData().getNodeCode());
                DecisionTreeNodeType decisionTreeNodeType = DecisionTreeFrontNodeType.convert(DecisionTreeFrontNodeType.getType(child.getNodeType()), child.getNodeData().getNodeCode());
                childDetail.setNodeType(decisionTreeNodeType.getCode());
                childDetail.setNodeValue(child.getNodeData().getNodeCode());
                childDetail.setTreeLevelSeq(seq);
                seq++;
                result.add(childDetail);
                findChildren(result, child, treeId);
            }
        }
    }
}
