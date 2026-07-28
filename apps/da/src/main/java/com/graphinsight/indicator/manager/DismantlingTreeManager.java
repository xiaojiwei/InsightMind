package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphinsight.indicator.enums.ResultCode;
import com.graphinsight.indicator.exception.BusinessWarnException;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DismantlingTree;
import com.graphinsight.indicator.auto.entity.DismantlingTreeQuote;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.service.IDismantlingTreeQuoteService;
import com.graphinsight.indicator.auto.service.IDismantlingTreeService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.*;
import com.graphinsight.indicator.model.dto.defaultTree.DismantlingConfigTreeFEDefault;
import com.graphinsight.indicator.model.dto.defaultTree.DismantlingConfigTreeFEDefaultDimension;
import com.graphinsight.indicator.model.dto.defaultTree.DismantlingConfigTreeFEDefaultFirst;
import com.graphinsight.indicator.model.dto.defaultTree.DismantlingConfigTreeFEDefaultSecond;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.DimensionQueryService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.thread.DismantlingFloorQueryThread;
import com.graphinsight.indicator.thread.DismantlingRegionQueryThread;
import com.graphinsight.indicator.util.IndicatorAssert;
import com.graphinsight.indicator.util.NumberFormatUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.graphinsight.indicator.util.contribution.ContributionStrategy;
import com.graphinsight.indicator.util.contribution.ContributionStrategyHolder;
import com.graphinsight.indicator.util.contribution.InfixParseUtils;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationResult;
import com.graphinsight.indicator.util.contribution.bean.RatioMeasureCalculationParam;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.SerializationUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.graphinsight.indicator.auto.entity.User;

/**
 * Date: 2022/11/5
 * Desc:
 * 绝对贡献度和相对贡献度
 * 绝对贡献度是指节点对根节点的贡献度
 * 相对贡献度是指节点对上层节点的贡献度
 * 注意，上层节点并不一定等于树中的上层节点，如果一个节点的拆解结果里，是加减乘除的混合运算，那么对于这个节点拆分成的各个自己节点，都会有一个对应的虚拟节点。这个虚拟节点才是真正的上层节点。而对上层节点的贡献度，其实是对虚拟节点的贡献度。
 * A
 * A1(100)  +  A2(1)
 * a1(50) x a2(50) +  b1(100000) x b2(-99999)
 */
@Component
@Slf4j
public class DismantlingTreeManager {

    @Resource
    ChartQueryService chartQueryService;
    @Resource
    DorisQueryManager dorisQueryManager;
    @Resource
    IDismantlingTreeService dismantlingTreeService;
    @Resource
    IDismantlingTreeQuoteService iDismantlingTreeQuoteService;
    @Resource
    RedisCacheService redisCacheService;
    @Autowired
    BloodManager bloodManager;
    @Autowired
    UserManager userManager;
    @Autowired
    private DimensionManager dimensionManager;
    @Autowired
    private DimensionQueryService dimensionQueryService;

    private static final String DIM_MEAS_DELIMITER = "#MEASCODE#";
    private static final String DIM_CODE_DELIMITER = "#DIMCODE#";
    private static final String NODE_TYPE_DELIMITER = "#NODETYPE#";

    public DismantlingConfigTree detail(Long id) {
        DismantlingTree dismantlingTree = dismantlingTreeService.getById(id);
        IndicatorAssert.indicatorAssert(dismantlingTree == null, "决策树不存在");
        DismantlingConfigTree convert = convert(dismantlingTree);
        return convert;
    }

    public DismantlingConfigTreeFEDefault detailDefault(Long id) {
        try {
            DismantlingConfigTree tree = this.detail(id);
            // 默认拆解树（前端渲染依据）
            DismantlingConfigTreeFEDefault feDefault = new DismantlingConfigTreeFEDefault();
            // 第一层
            DismantlingConfigTreeFEDefaultFirst first = new DismantlingConfigTreeFEDefaultFirst();
            first.setCode(tree.getRootMeasCode());
            first.setCnName(tree.getName());
            // 第二层
            DismantlingConfigTreeFEDefaultSecond second = new DismantlingConfigTreeFEDefaultSecond();
            DismantlingConfigTreeFEDefaultDimension dimension = new DismantlingConfigTreeFEDefaultDimension();
            DismantlingConfigTreeRegion region = tree.getFloors().get(1).getRegions().get(0);
            dimension.setCode(region.getDrillDownDimensionCodes().get(0));
            dimension.setValues(region.getDisplayDimensionValues());
            String dimensionName = cacheManager.getMetadataCache().getAllDimensionCodeMap().get(dimension.getCode()).getCnName();
            dimension.setCnName(dimensionName);
            second.setType(0);
            second.setDimension(dimension);

            feDefault.setFirst(first);
            feDefault.setSecond(second);

            return feDefault;
        } catch (Exception e) {
            log.error("DismantlingTreeManager detailDefault error {}", e.getMessage(), e);
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 删除树
        dismantlingTreeService.removeById(id);

        // 删除引用
        removeQuote(id);
    }

    private void removeQuote(Long treeId) {
        iDismantlingTreeQuoteService.remove(Wrappers.<DismantlingTreeQuote>lambdaQuery().eq(DismantlingTreeQuote::getTreeId, treeId));
    }

    @Transactional(rollbackFor = Exception.class)
    public DismantlingConfigTree save(DismantlingConfigTree dismantlingConfigTree) {
        DismantlingTree dismantlingTree;
        if (dismantlingConfigTree.getId() != null) {
            // 更新逻辑
            dismantlingTree = dismantlingTreeService.getById(dismantlingConfigTree.getId());
            IndicatorAssert.indicatorAssert(dismantlingTree == null, "决策树不存在");
            dismantlingTree.initUpdate();
        } else {
            // 新增逻辑
            dismantlingTree = new DismantlingTree();
            dismantlingTree.initCreate();
        }
        BeanUtils.copyProperties(dismantlingConfigTree, dismantlingTree);
        ObjectMapper objectMapper = new ObjectMapper();
        String beConfig = null;
        try {
            beConfig = objectMapper.writeValueAsString(dismantlingConfigTree.getFloors());
        } catch (JsonProcessingException e) {
            throw IndicatorParamNotValidException.error("json序列化异常");
        }
        dismantlingTree.setBeConfig(beConfig);
        dismantlingTreeService.saveOrUpdate(dismantlingTree);

        // 更新引用的指标和维度
        updateQuote(dismantlingConfigTree, dismantlingTree.getId());
        DismantlingConfigTree detail = convert(dismantlingTree);
        return detail;
    }

    private void updateQuote(DismantlingConfigTree dismantlingConfigTree, Long treeId) {
        // 先删除旧的
        iDismantlingTreeQuoteService.remove(Wrappers.<DismantlingTreeQuote>lambdaQuery().eq(DismantlingTreeQuote::getTreeId, treeId));
        // 再插入新的
        List<DismantlingTreeQuote> dimQuotes = dismantlingConfigTree.getDimCodes().stream().map(code -> {
            DismantlingTreeQuote quote = new DismantlingTreeQuote();
            quote.setTreeId(treeId);
            quote.setCode(code);
            quote.setType(FieldType.DIMENSION.getCode());
            return quote;
        }).collect(Collectors.toList());

        List<DismantlingTreeQuote> measQuotes = dismantlingConfigTree.getMeasCodes().stream().map(code -> {
            DismantlingTreeQuote quote = new DismantlingTreeQuote();
            quote.setTreeId(treeId);
            quote.setCode(code);
            quote.setType(FieldType.MEASURE.getCode());
            return quote;
        }).collect(Collectors.toList());
        iDismantlingTreeQuoteService.saveBatch(dimQuotes);
        iDismantlingTreeQuoteService.saveBatch(measQuotes);
    }

    public DismantlingTreeNode query(DismantlingConfigTree configTree, DismantlingTreeQuery query) {
        List<DismantlingConfigTreeFloor> floors = configTree.getFloors();
        final DismantlingTreeQuery queryParm = new DismantlingTreeQuery();
        BeanUtils.copyProperties(query, queryParm);
        query.setUserName("system");
        List<DismantlingFloorQueryThread> tasks = new ArrayList<>();
        for (int i = 0; i < floors.size(); i++) {
            DismantlingConfigTreeFloor floor = floors.get(i);
            DismantlingFloorQueryThread queryThread = new DismantlingFloorQueryThread(i, this, floor, query);
            tasks.add(queryThread);
        }
        List<List<List<DismantlingTreeNode>>> lists = new ArrayList<>();
        try {
            List<Future<DismantlingThreadQueryResult>> futures = floorExecutor.getThreadPoolExecutor().invokeAll(tasks);
            List<DismantlingThreadQueryResult> results = new ArrayList<>();

            for (Future<DismantlingThreadQueryResult> future : futures) {
                results.add(future.get());
            }
            List<DismantlingThreadQueryResult> sortedList = results.stream().sorted(Comparator.comparing(DismantlingThreadQueryResult::getIndex)).collect(Collectors.toList());
            sortedList.forEach(r -> lists.add(r.getNodes()));

        } catch (InterruptedException e) {
            log.error("DismantlingTreeManager query InterruptedException {}", e.getMessage(), e);
        } catch (ExecutionException e) {
            log.error("DismantlingTreeManager query ExecutionException {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("DismantlingTreeManager query Exception {}", e.getMessage(), e);
        }
        // 转换成树结构
        DismantlingTreeNode rootNode = convertTree(lists);
        return rootNode;
    }

    private DismantlingTreeNode convertTree(List<List<List<DismantlingTreeNode>>> lists) {
        List<DismantlingTreeNode> allNodes = lists.stream().flatMap(list -> list.stream()).flatMap(list -> list.stream()).collect(Collectors.toList());
        DismantlingTreeNode root = allNodes.stream().filter(node -> !StringUtils.hasLength(node.getParentfingerPrints())).findFirst().orElseThrow(() -> IndicatorParamNotValidException.error("没有找到根节点"));
        BigDecimal baseValue = root.getNodeData().getBasePeriodValue();
        BigDecimal currentValue = root.getNodeData().getCurrentPeriodValue();
        if (BigDecimal.ZERO.compareTo(baseValue) != 0) {
            if (root.getIsRatio()) {
                BigDecimal deltaValue = currentValue.subtract(baseValue);
                root.getNodeData().setAbsoluteContributionValue(deltaValue);
                root.getNodeData().setRelativelyContributionValue(deltaValue);
            } else {
                BigDecimal deltaValue = currentValue.subtract(baseValue);
                BigDecimal deltaValueRate = deltaValue.divide(baseValue, 16, BigDecimal.ROUND_DOWN);
                root.getNodeData().setAbsoluteContributionValue(deltaValueRate);
                root.getNodeData().setRelativelyContributionValue(deltaValueRate);
            }
        }
        findChildren(root, root, allNodes);
        return root;
    }

    public List<DismantlingTreeVO> listTree(DismantlingTreeQuery query) {
        List<DismantlingTree> dismantlingTrees = dismantlingTreeService.list(Wrappers.<DismantlingTree>lambdaQuery().eq(DismantlingTree::getRootMeasCode, query.getMeasCode()).eq(DismantlingTree::getSpaceId, query.getSpaceId()));
        if (CollectionUtils.isEmpty(dismantlingTrees)) {
            return Collections.EMPTY_LIST;
        }
        Set<Long> treeIds = dismantlingTrees.stream().map(DismantlingTree::getId).collect(Collectors.toSet());
        List<DismantlingTreeQuote> treeQuotes = iDismantlingTreeQuoteService.list(Wrappers.<DismantlingTreeQuote>lambdaQuery().in(DismantlingTreeQuote::getTreeId, treeIds));
        Map<Long, List<DismantlingTreeQuote>> map = treeQuotes.stream().collect(Collectors.groupingBy(DismantlingTreeQuote::getTreeId));
        List<DismantlingTreeVO> vos = dismantlingTrees.stream().map(tree -> {
            DismantlingTreeVO vo = new DismantlingTreeVO();
            BeanUtils.copyProperties(tree, vo);
            vo.setTreeName(tree.getName());
            List<DismantlingTreeQuote> dismantlingTreeQuotes = map.get(tree.getId());
            if (!CollectionUtils.isEmpty(dismantlingTreeQuotes)) {
                Set<String> measCodes = dismantlingTreeQuotes.stream().filter(quote -> Objects.equals(quote.getType(), FieldType.MEASURE.getCode())).map(DismantlingTreeQuote::getCode).collect(Collectors.toSet());
                Set<String> dimCodes = dismantlingTreeQuotes.stream().filter(quote -> Objects.equals(quote.getType(), FieldType.DIMENSION.getCode())).map(DismantlingTreeQuote::getCode).collect(Collectors.toSet());
                vo.getMeasCodes().addAll(measCodes);
                vo.getDimCodes().addAll(dimCodes);
            }
            return vo;
        }).collect(Collectors.toList());
        return vos;
    }

    public List<Measure> listMeasure(Long spaceId) {
        if (spaceId == null) {
            throw IndicatorParamNotValidException.error("参数不完整");
        }
        List<DismantlingTree> dismantlingTrees = dismantlingTreeService.list(Wrappers.<DismantlingTree>lambdaQuery().eq(DismantlingTree::getSpaceId, spaceId));
        if (CollectionUtils.isEmpty(dismantlingTrees)) {
            return Collections.EMPTY_LIST;
        }
        Set<String> codes = dismantlingTrees.stream().collect(Collectors.groupingBy(DismantlingTree::getRootMeasCode)).keySet();
        Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();
        List<Measure> measures = codes.stream().map(code -> allMeasureCodeMap.get(code)).filter(measure -> Objects.nonNull(measure)).collect(Collectors.toList());
        return measures;
    }

    @Data
    class TotalData {
        private BigDecimal baseTotal = BigDecimal.ZERO;
        private BigDecimal currentTotal = BigDecimal.ZERO;
    }

    /**
     * TODO 递归深度限制
     *
     * @param root
     * @param parent
     * @param allNodes
     */
    private void findChildren(DismantlingTreeNode root, DismantlingTreeNode parent, List<DismantlingTreeNode> allNodes) {
        /**
         * 满足children的节点条件是 1.具有父节点的指纹 2.包含父节点的完整的维值链条。比如 父节点是400 过滤完条件1之后，得到的集合可能是
         * WECHAT_呼入 WECHAT_呼出 400_呼入 400_呼出.此时需要把Wechat开头的元素排除掉
         */
        List<DismantlingTreeNode> children = allNodes.stream()
                // 条件1 过滤
                .filter(node -> Objects.equals(node.getParentfingerPrints(), parent.getFingerprints()))
                // 条件2 过滤
                .filter(node -> node.getDimensionValues().containsAll(parent.getDimensionValues()) || CollectionUtils.isEmpty(parent.getDimensionValues())).collect(Collectors.toList());
        // 计算children 的节点数据总和
        Map<String, List<DismantlingTreeNode>> measCodeMap = children.stream().filter(node -> !Objects.equals(node.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR)).collect(Collectors.groupingBy(DismantlingTreeNode::getCode));
        Map<String, TotalData> totalMap = new HashMap<>();
        measCodeMap.forEach((measCode, nodes) -> {
            TotalData totalData = new TotalData();
            BigDecimal base = nodes.stream().map(node -> node.getNodeData().getBasePeriodValue()).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
            BigDecimal current = nodes.stream().map(node -> node.getNodeData().getCurrentPeriodValue()).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
            totalData.setBaseTotal(base);
            totalData.setCurrentTotal(current);
            totalMap.put(measCode, totalData);
        });
        children.forEach(node -> {
            if (!Objects.equals(node.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
                // 计算节点占比
                TotalData totalData = totalMap.get(node.getCode());
                BigDecimal currentPeriodValue = node.getNodeData().getCurrentPeriodValue();
                BigDecimal basePeriodValue = node.getNodeData().getBasePeriodValue();
                BigDecimal baseProportion = BigDecimal.ZERO;
                BigDecimal currentProportion = BigDecimal.ZERO;
                if (BigDecimal.ZERO.compareTo(totalData.getBaseTotal()) != 0) {
                    baseProportion = basePeriodValue.divide(totalData.getBaseTotal(), 16, BigDecimal.ROUND_DOWN);
                }

                if (BigDecimal.ZERO.compareTo(totalData.getCurrentTotal()) != 0) {
                    currentProportion = currentPeriodValue.divide(totalData.getCurrentTotal(), 16, BigDecimal.ROUND_DOWN);
                }
                node.getNodeData().setBaseTotal(totalData.getBaseTotal());
                node.getNodeData().setCurrentTotal(totalData.getCurrentTotal());
                node.getNodeData().setBaseProportion(baseProportion);
                node.getNodeData().setCurrentProportion(currentProportion);

                // 设置上层节点值
                node.getNodeData().setUpperLayerBasePeriodValue(parent.getNodeData().getBasePeriodValue());
                node.getNodeData().setUpperLayerCurrentPeriodValue(parent.getNodeData().getCurrentPeriodValue());
                node.setParentRatio(parent.getIsRatio());
            }
        });
        // 计算子节点贡献度
        calChildrenContribution(children, parent);
        // 转换成对根节点的贡献度
        children.forEach(node -> {
            // 子节点对上层指标的贡献占比
            BigDecimal rate = node.getNodeData().getRelativelyContributionValueRate();
            // 父节点对根节点的贡献度
            BigDecimal absoluteTotal = parent.getNodeData().getAbsoluteContributionValue();
            // 根据占比计算子节点对根节点的贡献度
            BigDecimal absoulteContributionValue = rate.multiply(absoluteTotal);
            // 子节点对根节点的贡献度
            node.getNodeData().setAbsoluteContributionValue(absoulteContributionValue);
            BigDecimal rootContributionValue = root.getNodeData().getAbsoluteContributionValue();
            if (BigDecimal.ZERO.compareTo(rootContributionValue) == 0) {
                node.getNodeData().setAbsoluteContributionValueRate(BigDecimal.ZERO);
            } else {
                node.getNodeData().setAbsoluteContributionValueRate(absoulteContributionValue.divide(rootContributionValue, 16, BigDecimal.ROUND_DOWN));
            }
            calDelta(node);
        });
        children.forEach(node -> findChildren(root, node, allNodes));
        calDelta(parent);
        List<DismantlingTreeNode> displayChildren = children.stream().filter(node -> node.getDisplay()).collect(Collectors.toList());
        parent.setChildren(displayChildren);
    }

    /**
     * @param lists
     */
    private void calContribution(List<List<List<DismantlingTreeNode>>> lists) {
        // 找出根节点
        Map<String, List<DismantlingTreeNode>> fingerprintMap = lists.stream().flatMap(s -> s.stream().flatMap(sub -> sub.stream())).collect(Collectors.groupingBy(DismantlingTreeNode::getFingerprints));
        // 逐层计算
        lists.forEach(floor -> calFloorContribution(floor, fingerprintMap));
    }

    private void calFloorContribution(List<List<DismantlingTreeNode>> floor, Map<String, List<DismantlingTreeNode>> fingerprintMap) {
        floor.forEach(region -> calRegionContribution(region, fingerprintMap));
    }

    private void calChildrenContribution(List<DismantlingTreeNode> children, DismantlingTreeNode parentNode) {
        // 中缀表达式解析，并计算贡献度
        ContributionCalculationType calculationType = getType(children);
        List<DismantlingTreeNode> treeNodes = InfixParseUtils.infixParse(children);
        treeNodes.forEach(node -> calSingleNode(node, parentNode, calculationType));
        // 计算各个节点的贡献度占比
        calRegionContributionRate(children);
    }

    /**
     * 计算Region中各个节点的贡献度
     *
     * @param region
     * @param fingerprintMap
     */
    private void calRegionContribution(List<DismantlingTreeNode> region, Map<String, List<DismantlingTreeNode>> fingerprintMap) {
        String parentfingerPrints = region.get(0).getParentfingerPrints();
        List<DismantlingTreeNode> dismantlingTreeNodes = fingerprintMap.get(parentfingerPrints);
        // 中缀表达式解析，并计算贡献度
        ContributionCalculationType calculationType = getType(region);
        if (!CollectionUtils.isEmpty(dismantlingTreeNodes) && dismantlingTreeNodes.get(0) != null) {
            DismantlingTreeNode parentNode = dismantlingTreeNodes.get(0);
            List<DismantlingTreeNode> treeNodes = InfixParseUtils.infixParse(region);
            treeNodes.forEach(node -> calSingleNode(node, parentNode, calculationType));
            // 计算各个节点的贡献度占比
            calRegionContributionRate(region);
        }
    }

    private ContributionCalculationType getType(List<DismantlingTreeNode> regionNodes) {
        List<DismantlingTreeNode> emptyNode = regionNodes.stream().filter(node -> Objects.equals(OperatorType.EMPTY, node.getOperatorType())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(emptyNode)) {
            return ContributionCalculationType.ADDITION;
        }
        return ContributionCalculationType.TWO_FACTOR;
    }

    private ContributionCalculationType getComboType(List<DismantlingTreeNode> regionNodes) {
        DismantlingTreeNode dismantlingTreeNode = regionNodes.stream().filter(node -> !node.isOperand()).sorted(Comparator.comparing(DismantlingTreeNode::priority)).findFirst().orElse(null);
        if (dismantlingTreeNode == null) {
            throw IndicatorParamNotValidException.error("运算类型不合法");
        }
        OperatorType type = dismantlingTreeNode.getOperatorType();
        switch (type) {
        case ADDITION:
        case SUBTRACTION:
            return ContributionCalculationType.ADDITION;
        case DIVISION:
        case MULTIPLICATION:
            return ContributionCalculationType.MULTIPLICATION;
        case EMPTY:
            return ContributionCalculationType.TWO_FACTOR;
        default:
            throw IndicatorParamNotValidException.error("运算类型不合法");
        }
    }

    /**
     * region中各个节点的贡献度相对占比
     *
     * @param region
     */
    private void calRegionContributionRate(List<DismantlingTreeNode> region) {
        BigDecimal contributionTotal = region.stream().filter(node -> !Objects.equals(node.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR)).map(node -> node.getNodeData().getRelativelyContributionValue()).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
        if (BigDecimal.ZERO.compareTo(contributionTotal) != 0) {
            region.forEach(node -> {
                if (!Objects.equals(node.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
                    BigDecimal value = node.getNodeData().getRelativelyContributionValue();
                    if (contributionTotal.compareTo(BigDecimal.ZERO) == 0) {
                        node.getNodeData().setRelativelyContributionTotal(contributionTotal);
                        node.getNodeData().setRelativelyContributionValueRate(BigDecimal.ZERO);
                    } else {
                        BigDecimal rate = value.divide(contributionTotal, 16, BigDecimal.ROUND_DOWN);
                        node.getNodeData().setRelativelyContributionTotal(contributionTotal);
                        node.getNodeData().setRelativelyContributionValueRate(rate);
                    }

                }
            });
        }
    }

    private void calSingleNode(DismantlingTreeNode node, DismantlingTreeNode parentNode, ContributionCalculationType type) {
        node.setParentRatio(parentNode.getIsRatio());
        if (node.getIsCombo()) {
            calCombo(node, type);
        } else {
            if (!DismantlingConfigTreeCalUnitType.OPERATOR.equals(node.getNodeType())) {
                ContributionCalculationParam param = createParam(node, parentNode);
                ContributionStrategy strategy = ContributionStrategyHolder.getStrategy(type);
                if (ContributionCalculationType.TWO_FACTOR.equals(type)) {
                    RatioMeasureCalculationParam ratioParam = getRatioParam(node);
                    param.setRatioParam(ratioParam);
                }
                ContributionCalculationResult result = strategy.calculate(param);
                convertResult(node, result);
            }
        }
    }

    /**
     * 计算combo的贡献度
     *
     * @param node
     */
    private void calCombo(DismantlingTreeNode node, ContributionCalculationType type) {
        // 先计算combo的贡献度
        DismantlingTreeNodeCombo combo = (DismantlingTreeNodeCombo) node;
        ContributionCalculationParam param = createComboParam(combo);
        // 如果上层节点是比率型指标，那么combo就按照比率型指标去计算贡献度
        combo.setIsRatio(combo.getParentRatio());
        ContributionStrategy strategy = ContributionStrategyHolder.getStrategy(type);
        ContributionCalculationResult result = strategy.calculate(param);
        BigDecimal contributionValue = result.getContributionValue();
        convertResult(combo, result);
        List<DismantlingTreeNode> convertItems = combo.getConvertItems();
        List<DismantlingTreeNode> items = combo.getItems();
        for (int i = 0; i < convertItems.size(); i++) {
            // 再计算combo内item的贡献度
            DismantlingTreeNode convertItem = convertItems.get(i);
            ContributionStrategy itemStrategy = ContributionStrategyHolder.getStrategy(getComboType(convertItems));
            if (!DismantlingConfigTreeCalUnitType.OPERATOR.equals(convertItem.getNodeType())) {
                ContributionCalculationParam itemParam = createParam(convertItem, combo);
                ContributionCalculationResult itemResult = itemStrategy.calculate(itemParam);
                // 把item对combo的贡献度转换为对上层节点的贡献度。这里对combo的贡献度其实就是对上层指标的贡献度。不需要额外计算。可以举例证明
                BigDecimal contributionValue2Combo = itemResult.getContributionValue();
                itemResult.setContributionValue(contributionValue2Combo);
                DismantlingTreeNode item = items.get(i);
                item.getNodeData().setRelativelyContributionValue(contributionValue2Combo == null ? BigDecimal.ZERO : contributionValue2Combo.setScale(16, BigDecimal.ROUND_DOWN));
            }
        }
    }

    private void convertResult(DismantlingTreeNode node, ContributionCalculationResult result) {
        node.getNodeData().setRelativelyContributionValue(result.getContributionValue() == null ? BigDecimal.ZERO : result.getContributionValue().setScale(16, BigDecimal.ROUND_DOWN));
        node.getNodeData().setDeltaValue(result.getDeltaValue() == null ? BigDecimal.ZERO : result.getDeltaValue().setScale(16, BigDecimal.ROUND_DOWN));
        node.getNodeData().setDeltaValueRate(result.getDeltaValueRate() == null ? BigDecimal.ZERO : result.getDeltaValueRate().setScale(16, BigDecimal.ROUND_DOWN));
    }

    /**
     * @return
     */
    public ContributionCalculationParam createComboParam(DismantlingTreeNode combo) {
        ContributionCalculationParam.ContributionCalculationParamBuilder builder = ContributionCalculationParam.builder();
        builder.upperLayerPreviousPeriodValue(combo.getNodeData().getUpperLayerBasePeriodValue());
        builder.upperLayerCurrentPeriodValue(combo.getNodeData().getUpperLayerCurrentPeriodValue());
        builder.previousPeriodValue(combo.getNodeData().getBasePeriodValue());
        builder.currentPeriodValue(combo.getNodeData().getCurrentPeriodValue());
        builder.parentPatio(combo.getParentRatio());
        return builder.build();
    }

    /**
     * @return
     */
    public ContributionCalculationParam createParam(DismantlingTreeNode node, DismantlingTreeNode parent) {
        ContributionCalculationParam.ContributionCalculationParamBuilder builder = ContributionCalculationParam.builder();
        if (parent != null) {
            builder.upperLayerPreviousPeriodValue(parent.getNodeData().getBasePeriodValue());
            builder.upperLayerCurrentPeriodValue(parent.getNodeData().getCurrentPeriodValue());
        } else {
            builder.upperLayerPreviousPeriodValue(BigDecimal.ZERO);
            builder.upperLayerCurrentPeriodValue(BigDecimal.ZERO);
        }
        builder.previousPeriodValue(node.getNodeData().getBasePeriodValue());
        builder.currentPeriodValue(node.getNodeData().getCurrentPeriodValue());
        builder.parentPatio(parent.getIsRatio());
        return builder.build();
    }

    private RatioMeasureCalculationParam getRatioParam(DismantlingTreeNode node) {
        RatioMeasureCalculationParam param = new RatioMeasureCalculationParam();
        param.setA_baseValue(node.getNodeData().getBaseMolecularValue());
        param.setB_baseValue(node.getNodeData().getBaseDenominatorValue());
        param.setA_currentValue(node.getNodeData().getCurrentMolecularValue());
        param.setB_currentValue(node.getNodeData().getCurrentDenominatorValue());

        param.setY0(node.getNodeData().getUpperLayerBasePeriodValue());
        param.setA_base_total(node.getNodeData().getBaseMolecularValueTotal());
        param.setA_current_total(node.getNodeData().getCurrentMolecularValueTotal());
        param.setB_base_total(node.getNodeData().getBaseDenominatorValueTotal());
        param.setB_current_total(node.getNodeData().getCurrentDenominatorValueTotal());
        return param;
    }

    // private DismantlingTreeNode
    // findRootNode(List<List<List<DismantlingTreeNode>>> lists) {
    // DismantlingTreeNode rootNode = Optional.ofNullable(lists)
    // .map(list -> list.get(0))
    // .map(list -> list.get(0))
    // .map(list -> list.get(0))
    // .orElse(null);
    // return rootNode;
    // }

    /**
     * @param treeNodes
     */
    private void calProportion(List<List<List<DismantlingTreeNode>>> treeNodes) {
        /**
         * 这里指纹可能会有重复的，原因是同一个region下面的多个配置节点,如果选择了相同的指标作为查询节点，那么得到的数据节点就会完全一样 比如 选择 订单量
         * * 订单量 作为两个计算节点，虽然不合理，但合法
         */
        Map<String, List<DismantlingTreeNode>> fingerprintMap = treeNodes.stream().flatMap(s -> s.stream().flatMap(sub -> sub.stream())).filter(node -> !Objects.equals(DismantlingConfigTreeCalUnitType.OPERATOR, node.getNodeType())).collect(Collectors.groupingBy(DismantlingTreeNode::getFingerprints));
        treeNodes.forEach(floorNodes -> {
            // floorNodes是当前层次的所有node，是一个二位数组，每一个元素由一个region生成的node集合
            floorNodes.forEach(regionNodes -> {
                DismantlingTreeNode firstNode = regionNodes.stream().findFirst().orElse(emptyNode());
                List<DismantlingTreeNode> calNodes = regionNodes.stream().filter(rn -> !Objects.equals(DismantlingConfigTreeCalUnitType.OPERATOR, rn.getNodeType())).collect(Collectors.toList());
                String parentfingerPrints = firstNode.getParentfingerPrints();
                DismantlingTreeNode parentNode = fingerprintMap.get(parentfingerPrints) == null ? null : fingerprintMap.get(parentfingerPrints).get(0);
                if (parentNode != null) {
                    BigDecimal currentPeriodValue = parentNode.getNodeData().getCurrentPeriodValue();
                    BigDecimal previousPeriodValue = parentNode.getNodeData().getBasePeriodValue();
                    calNodes.forEach(rn -> {
                        if (!Objects.equals(DismantlingConfigTreeCalUnitType.OPERATOR, rn.getNodeType())) {
                            DismantlingTreeNodeData nodeData = rn.getNodeData();
                            nodeData.setUpperLayerCurrentPeriodValue(currentPeriodValue);
                            nodeData.setUpperLayerBasePeriodValue(previousPeriodValue);
                            BigDecimal currentValue = nodeData.getCurrentPeriodValue();
                            BigDecimal baseValue = nodeData.getBasePeriodValue();
                            nodeData.setCurrentProportion(proportion(currentValue, nodeData.getCurrentTotal()));
                            nodeData.setBaseProportion(proportion(baseValue, nodeData.getBaseTotal()));
                        }
                    });
                }
            });
        });
    }

    private BigDecimal proportion(BigDecimal target, BigDecimal total) {
        if (total == null || BigDecimal.ZERO.compareTo(total) == 0) {
            return null;
        }
        BigDecimal proportion = target.divide(total, 16, BigDecimal.ROUND_DOWN);
        return proportion;
    }

    private DismantlingTreeNode emptyNode() {
        DismantlingTreeNode node = new DismantlingTreeNode();
        return node;
    }

    private DismantlingConfigTree convert(DismantlingTree dismantlingTree) {
        DismantlingConfigTree dismantlingConfigTree = new DismantlingConfigTree();
        BeanUtils.copyProperties(dismantlingTree, dismantlingConfigTree);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            List<DismantlingConfigTreeFloor> dismantlingConfigTreeFloors = objectMapper.readValue(dismantlingTree.getBeConfig(), new TypeReference<List<DismantlingConfigTreeFloor>>() {
            });
            dismantlingConfigTree.setFloors(dismantlingConfigTreeFloors);
        } catch (JsonProcessingException e) {
            log.error("反序列化异常:", e);
            throw IndicatorParamNotValidException.error("反序列化异常");
        }
        return dismantlingConfigTree;
    }

    private RelatedSet convert(RelatedCodeSet relatedCodeSet) {
        RelatedSet relatedSet = new RelatedSet();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Map<String, List<Dimension>> dimensionMap = allDimensionMap.values().stream().collect(Collectors.groupingBy(Dimension::getCode));
        Map<String, List<Measure>> measureMap = allMeasureMap.values().stream().collect(Collectors.groupingBy(Measure::getCode));
        Set<String> dimensionSet = relatedCodeSet.getDimensionSet();
        Set<String> measureSet = relatedCodeSet.getMeasureSet();
        Set<Integer> dimIds = dimensionSet.stream().map(code -> dimensionMap.get(code).get(0).getId()).collect(Collectors.toSet());
        Set<Integer> measIds = measureSet.stream().map(code -> measureMap.get(code).get(0).getId()).collect(Collectors.toSet());

        relatedSet.setMeasureSet(measIds);
        relatedSet.setDimensionSet(dimIds);
        relatedSet.setFilterWithRelyDimensions(relatedCodeSet.isFilterWithRelyDimensions());
        return relatedSet;
    }

    private RelatedCodeSet convert(RelatedSet relatedSet) {
        RelatedCodeSet result = new RelatedCodeSet();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Set<Integer> dimensionSet = relatedSet.getDimensionSet();
        Set<Integer> measureSet = relatedSet.getMeasureSet();
        Set<String> dimCodes = dimensionSet.stream().map(id -> allDimensionMap.get(id)).filter(d -> d != null).map(d -> d.getCode()).collect(Collectors.toSet());
        Set<String> measCodes = measureSet.stream().map(id -> allMeasureMap.get(id)).filter(m -> m != null).map(m -> m.getCode()).collect(Collectors.toSet());

        result.setMeasureSet(measCodes);
        result.setDimensionSet(dimCodes);
        result.setFilterWithRelyDimensions(relatedSet.isFilterWithRelyDimensions());
        return result;
    }

    private Tuple2<String, String> getMonthPeriod() {
        LocalDate currentDate = LocalDate.now();
        LocalDate currentPeriod = currentDate.minusMonths(1);
        LocalDate basePeriod = currentDate.minusMonths(2);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        return Tuples.of(currentPeriod.format(formatter), basePeriod.format(formatter));
    }

    private DismantlingTreeQuery buildDismantlingTreeQuery(String measCode) {
        // 1 根据指标编码查询拆解树
        List<DismantlingTree> dismantlingTrees = dismantlingTreeService.getByMeasCode(measCode);
        if (dismantlingTrees != null && dismantlingTrees.size() > 0) {
            Optional<DismantlingTree> optional = dismantlingTrees.stream().filter(p -> p.getSpaceId() == 4).findFirst();
            DismantlingTree dismantlingTree = optional.orElseGet(() -> dismantlingTrees.get(0));

            // 2 根据拆解树查询关联的维度
            List<DismantlingTreeQuote> treeQuotes = iDismantlingTreeQuoteService.list(Wrappers.<DismantlingTreeQuote>lambdaQuery().eq(DismantlingTreeQuote::getTreeId, dismantlingTree.getId()).eq(DismantlingTreeQuote::getType, FieldType.DIMENSION.getCode()));
            if (treeQuotes != null && treeQuotes.size() > 0) {
                // 3 根据指标编码和维度编码查询可用的日期维度
                String dimensionCode = treeQuotes.get(0).getCode();
                RelatedCodeSet codeSet = new RelatedCodeSet();
                codeSet.setMeasureSet(new HashSet<String>() {
                    {
                        add(measCode);
                    }
                });
                codeSet.setDimensionSet(new HashSet<String>() {
                    {
                        add(dimensionCode);
                    }
                });
                codeSet.setSpaceId(dismantlingTree.getSpaceId());
                RelatedSet relatedSet = this.convert(codeSet);
                RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);
                RelatedCodeSet resultCodeSet = convert(resultRelatedSet);
                MetadataCache metadataCache = cacheManager.getMetadataCache();
                Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
                List<Dimension> dimensions = allDimensionMap.values().stream().filter(d -> resultCodeSet.getDimensionSet().contains(d.getCode()) && d.getIsDelete() == 0 && d.getViewType() == 3 // 按月
                        && d.getOnline() == 1) // 在线
                        .collect(Collectors.toList());
                if (dimensions.size() > 0) {
                    String dateDimensionCode = dimensions.get(0).getCode();
                    // T1:Current T2:Base
                    Tuple2<String, String> dateTuple2 = this.getMonthPeriod();
                    // SQL操作参数
                    Operator currentOperator = new Operator();
                    Operator baseOperator = new Operator();
                    currentOperator.setDataList(Collections.singletonList(dateTuple2.getT1()));
                    currentOperator.setSqlOprType(SqlOprType.IN);
                    currentOperator.setSqlLogicalType(SqlLogicalType.AND);
                    currentOperator.setTimeRange(TimeRange.DATE);
                    baseOperator.setDataList(Collections.singletonList(dateTuple2.getT2()));
                    baseOperator.setSqlOprType(SqlOprType.IN);
                    baseOperator.setSqlLogicalType(SqlLogicalType.AND);
                    baseOperator.setTimeRange(TimeRange.DATE);
                    List<Operator> currentOperators = new ArrayList<>();
                    currentOperators.add(currentOperator);
                    List<Operator> baseOperators = new ArrayList<>();
                    baseOperators.add(baseOperator);

                    // 周期参数（业务参数）
                    Filter currentPeriod = new Filter();
                    Filter basePeriod = new Filter();
                    currentPeriod.setInternal(false);
                    currentPeriod.setCode(dateDimensionCode);
                    currentPeriod.setOperatorList(currentOperators);
                    basePeriod.setInternal(false);
                    basePeriod.setCode(dateDimensionCode);
                    basePeriod.setOperatorList(baseOperators);
                    List<Filter> currentPeriods = new ArrayList<>();
                    List<Filter> basePeriods = new ArrayList<>();
                    currentPeriods.add(currentPeriod);
                    basePeriods.add(basePeriod);

                    // 构建查询参数
                    DismantlingTreeQuery query = new DismantlingTreeQuery();
                    query.setSpaceId(dismantlingTree.getSpaceId());
                    query.setMeasCode(measCode);
                    query.setTreeId(dismantlingTree.getId());
                    query.setDismantlingWay(DismantlingWay.DYNAMIC);
                    query.setComparedType(DismantlingTreeQueryComparedType.CYCLE);
                    query.setCurrentDateFilters(currentPeriods);
                    query.setBaseDateFilters(basePeriods);

                    return query;
                }
            }
        }

        return null;
    }

    public DismantlingTreeVO queryTree(String measCode) {
        DismantlingTreeQuery query = this.buildDismantlingTreeQuery(measCode);
        if (query == null) {
            log.info("queryTree query params null");
            return null;
        }

        log.info("queryTree query params {}", JSON.toJSONString(query));

        // 检查指标权限
        UserContext userContext = userManager.getUserContext(query.getSpaceId(), UserThreadLocalUtil.getUserName());
        List<Measure> authMeasures = userContext.getAuthMeasures();
        Set<String> authMeasCodes = authMeasures.stream().map(Measure::getCode).collect(Collectors.toSet());
        if (!authMeasCodes.contains(measCode)) {
            return null;
        }

        String cacheKey = String.format("queryTree_%s_%s", query.getSpaceId(), query.getTreeId());
        if (redisCacheService.hasKey(cacheKey)) {
            return JSON.parseObject(JSON.toJSONString(redisCacheService.get(cacheKey, Object.class)), DismantlingTreeVO.class);
        }
        DismantlingTreeVO dismantlingTreeVO = this.queryTree(query);
        redisCacheService.put(cacheKey, dismantlingTreeVO, 30, TimeUnit.DAYS);

        return dismantlingTreeVO;
    }

    public DismantlingTreeVO queryTree(DismantlingTreeQuery query) {
        DismantlingTree dismantlingTree = dismantlingTreeService.getById(query.getTreeId());
        DismantlingConfigTree configTree = convert(dismantlingTree);
        DismantlingTreeNode node = query(configTree, query);
        List<DismantlingTreeQuote> quotes = iDismantlingTreeQuoteService.list(Wrappers.<DismantlingTreeQuote>lambdaQuery().eq(DismantlingTreeQuote::getTreeId, query.getTreeId()));
        DismantlingTreeVO dismantlingTreeVO = new DismantlingTreeVO();
        if (!CollectionUtils.isEmpty(quotes)) {
            Set<String> measCodes = quotes.stream().filter(q -> Objects.equals(q.getType(), FieldType.MEASURE.getCode())).map(DismantlingTreeQuote::getCode).collect(Collectors.toSet());
            Set<String> dimCodes = quotes.stream().filter(q -> Objects.equals(q.getType(), FieldType.DIMENSION.getCode())).map(DismantlingTreeQuote::getCode).collect(Collectors.toSet());
            dismantlingTreeVO.getMeasCodes().addAll(measCodes);
            dismantlingTreeVO.getDimCodes().addAll(dimCodes);
        }
        dismantlingTreeVO.setRoot(node);
        dismantlingTreeVO.setId(dismantlingTree.getId());
        dismantlingTreeVO.setIsDefault(Objects.equals(dismantlingTree.getIsDefault(), YesNoType.YES.getCode()));
        dismantlingTreeVO.setTreeName(dismantlingTree.getName());
        return dismantlingTreeVO;
    }

    @Async("executor")
    public void queryTreeAsync(DismantlingTreeQuery query, String queryTaskId) {
        try {
            this.setProgress(queryTaskId, 10);

            DismantlingTree dismantlingTree = dismantlingTreeService.getById(query.getTreeId());
            this.setProgress(queryTaskId, 20);

            DismantlingConfigTree configTree = convert(dismantlingTree);
            this.setProgress(queryTaskId, 40);

            DismantlingTreeNode node = query(configTree, query);
            this.setProgress(queryTaskId, 60);

            List<DismantlingTreeQuote> quotes = iDismantlingTreeQuoteService.list(Wrappers.<DismantlingTreeQuote>lambdaQuery().eq(DismantlingTreeQuote::getTreeId, query.getTreeId()));
            this.setProgress(queryTaskId, 80);

            DismantlingTreeVO dismantlingTreeVO = new DismantlingTreeVO();
            if (!CollectionUtils.isEmpty(quotes)) {
                Set<String> measCodes = quotes.stream().filter(q -> Objects.equals(q.getType(), FieldType.MEASURE.getCode())).map(DismantlingTreeQuote::getCode).collect(Collectors.toSet());
                Set<String> dimCodes = quotes.stream().filter(q -> Objects.equals(q.getType(), FieldType.DIMENSION.getCode())).map(DismantlingTreeQuote::getCode).collect(Collectors.toSet());
                dismantlingTreeVO.getMeasCodes().addAll(measCodes);
                dismantlingTreeVO.getDimCodes().addAll(dimCodes);
            }
            dismantlingTreeVO.setRoot(node);
            dismantlingTreeVO.setId(dismantlingTree.getId());
            dismantlingTreeVO.setIsDefault(Objects.equals(dismantlingTree.getIsDefault(), YesNoType.YES.getCode()));
            dismantlingTreeVO.setTreeName(dismantlingTree.getName());
            this.setProgress(queryTaskId, 90);

            this.setDismantlingTreeCache(queryTaskId, dismantlingTreeVO);
            this.setProgress(queryTaskId, 100);
        } catch (Exception ex) {
            this.setProgress(queryTaskId, -1);
            log.error("DismantlingTreeManager queryTreeAsync Exception " + ex.getMessage(), ex);
        }
    }

    public DismantlingTreeVO queryTreeCache(String taskId) {
        if (Objects.nonNull(taskId) && redisCacheService.hasKey(IndicatorConstant.DISMANTLING_TREE_QUERY_RESULT_PREFIX + taskId)) {
            return this.getDismantlingTreeCache(taskId);
        }

        throw IndicatorParamNotValidException.error("查询任务不存在，请稍候重试或重新查询。");
    }

    @Resource
    ThreadPoolTaskExecutor executor;

    @Resource(name = "floorExecutor")
    ThreadPoolTaskExecutor floorExecutor;

    public List<List<DismantlingTreeNode>> parseFloor(DismantlingConfigTreeFloor floor, DismantlingTreeQuery query) {
        List<List<DismantlingTreeNode>> result = new ArrayList<>();
        List<DismantlingConfigTreeRegion> regions = floor.getRegions();
        List<DismantlingRegionQueryThread> tasks = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) {
            DismantlingConfigTreeRegion region = regions.get(i);
            DismantlingRegionQueryThread queryThread = new DismantlingRegionQueryThread(i, this, floor, region, query);
            tasks.add(queryThread);
        }
        try {
            List<Future<DismantlingThreadQueryResult>> futures = executor.getThreadPoolExecutor().invokeAll(tasks);
            List<DismantlingThreadQueryResult> results = new ArrayList<>();

            for (Future<DismantlingThreadQueryResult> future : futures) {
                results.add(future.get());
            }
            List<DismantlingThreadQueryResult> sortedList = results.stream().sorted(Comparator.comparing(DismantlingThreadQueryResult::getIndex)).collect(Collectors.toList());
            sortedList.forEach(r -> result.addAll(r.getNodes()));

        } catch (InterruptedException e) {
            log.error("查询任务异常:", e);
        } catch (ExecutionException e) {
            log.error("查询任务异常:", e);
        }
        return result;
    }

    /**
     * @param region 一个配置的最小单元
     * @return 根据这个region的规则和下钻配置 查询出所有的节点数据，并且每一个节点的数据需要生成一个全树唯一的指纹。
     *         <p>
     *         是查询Doris的最小单元，这里可以做并发查询优化
     */
    public List<List<DismantlingTreeNode>> parseRegion(DismantlingConfigTreeFloor floor, DismantlingConfigTreeRegion region, DismantlingTreeQuery query) {
        DismantlingConfigTreeRegionType regionType = region.getRegionType();
        List<String> drillDownDimensionCodes = region.getDrillDownDimensionCodes();
        Set<String> dimensionCodes = new HashSet<>();
        dimensionCodes.addAll(drillDownDimensionCodes);
        if (Objects.equals(DismantlingConfigTreeRegionType.DIMENSION_DRILL_DOWN, regionType)) {
            // 维度下钻
        } else if (Objects.equals(DismantlingConfigTreeRegionType.MEASURE_DISMANTLING, regionType)) {
            // 指标拆解
        }
        List<DismantlingTreeNode> treeNodes = queryNodeData(floor, region, query);
        List<List<DismantlingTreeNode>> assembleNodeData = assembleNodeData(treeNodes, region);
        return assembleNodeData;
    }

    private void calDelta(DismantlingTreeNode node) {
        if (node != null && !Objects.equals(node.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
            BigDecimal basePeriodValue = BigDecimal.ZERO;
            BigDecimal currentPeriodValue = BigDecimal.ZERO;
            BigDecimal delta;
            if (Objects.equals(node.getNodeType(), DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE)) {
                basePeriodValue = node.getNodeData().getBasePeriodValue();
                currentPeriodValue = node.getNodeData().getCurrentPeriodValue();
            } else if (Objects.equals(node.getNodeType(), DismantlingConfigTreeCalUnitType.PROPORTION)) {
                basePeriodValue = node.getNodeData().getBaseProportion();
                currentPeriodValue = node.getNodeData().getCurrentProportion();
            }
            delta = currentPeriodValue.subtract(basePeriodValue);
            node.getNodeData().setDeltaValue(delta);
            if (Objects.equals(node.getIsRatio(), true)) {
                node.getNodeData().setDeltaValueRate(delta);
            } else {
                if (BigDecimal.ZERO.compareTo(basePeriodValue) != 0) {
                    node.getNodeData().setDeltaValueRate(delta.divide(basePeriodValue, 16, BigDecimal.ROUND_DOWN));
                }
            }
        }
    }

    /**
     * treeNodes得到的是按照下钻规则查询出来的各个维度下钻之后的指标数据，这时候并没有节点之间的计算关系，需要根据region的配置规则，形成带有数学计算符号的节点集合
     * 比如，region的配置规则是上次节点 A = mn * an, an 是A通过某一维度下钻得到的，mn 是在该维度下的另一个计算指标。
     * 下钻查询之后，得到的是很n个 m指标 和a指标的组合，需要再这一步将多个组合拼装成 m1 * a1 + m2 * a2 + ... + mn *
     * an的形式 其中，m和a的配对规则是，维值要一样，也就是说指纹的前缀要一样.配对之后，再在 mn 和 an
     * 之间增加运算符。最后在每一组之间再增加一个组间的运算符
     * <p>
     * 此外，需要根据每一层
     */
    private List<List<DismantlingTreeNode>> assembleNodeData(List<DismantlingTreeNode> treeNodes, DismantlingConfigTreeRegion region) {
        List<List<DismantlingTreeNode>> result = new LinkedList<>();
        Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();

        Set<String> parentFingerprints = region.getParentFingerprints();
        Set<String> displayDimensionValues = region.getDisplayDimensionValues();
        if (CollectionUtils.isEmpty(parentFingerprints)) {
            // 如果父指纹为空,说明当前层级是第一次，也就是根节点，直接返回即可
            treeNodes.forEach(treeNode -> {
                treeNode.setDisplayName(Optional.ofNullable(allMeasureCodeMap.get(treeNode.getCode())).map(Measure::getCnName).orElse("结果指标"));
                treeNode.setNodeType(DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
                treeNode.setFingerprints(treeNode.getFingerprints() + NODE_TYPE_DELIMITER + treeNode.getNodeType().getCode());
            });
            result.add(treeNodes);
            return result;
        }

        parentFingerprints.forEach(parentFingerprint -> {
            String parentFingerprintsPrefix = parentFingerprint.split(DIM_MEAS_DELIMITER)[0];
            // String fingerprintsSuffix = parentFingerprint.split(DIM_MEAS_DELIMITER)[1];
            List<DismantlingTreeNode> children = new LinkedList<>();
            List<DismantlingConfigTreeNode> regionNodes = region.getNodes();
            /**
             * 指纹前缀的Map
             */
            Map<String, List<DismantlingTreeNode>> fingerprintsPrefixMap = treeNodes.stream().collect(Collectors.groupingBy(node -> node.getFingerprints().split(DIM_MEAS_DELIMITER)[0]));
            fingerprintsPrefixMap.forEach((figerprints, nodes) -> {
                if (figerprints.startsWith(parentFingerprintsPrefix)) {
                    String dimValue = getDimValue(figerprints);
                    boolean display = false;
                    if (Objects.equals(DismantlingConfigTreeRegionType.MEASURE_DISMANTLING, region.getRegionType())) {
                        display = true;
                    } else {
                        display = displayDimensionValues.contains(dimValue);
                    }
                    /**
                     * 维值列表
                     */
                    List<String> dimensionValues = new LinkedList<>();
                    for (DismantlingConfigTreeNode regionNode : regionNodes) {
                        Measure measure = allMeasureCodeMap.get(regionNode.getQueryMeasCode());
                        String displayName = "";
                        if (dimValue != null) {
                            displayName += dimValue;
                        }
                        if (measure != null) {
                            displayName += measure.getCnName();
                        }
                        DismantlingTreeNode node = nodes.stream().filter(n -> Objects.equals(n.getCode(), regionNode.getQueryMeasCode())).findFirst().orElse(new DismantlingTreeNode());
                        DismantlingTreeNode treeNode = (DismantlingTreeNode) SerializationUtils.clone(node);
                        if (Objects.equals(regionNode.getType(), DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE)) {
                            // treeNode.setNodeType(DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
                            if (CollectionUtils.isEmpty(dimensionValues)) {
                                dimensionValues.addAll(treeNode.getDimensionValues());
                            }
                        } else if (Objects.equals(regionNode.getType(), DismantlingConfigTreeCalUnitType.PROPORTION)) {
                            // treeNode.setNodeType(DismantlingConfigTreeCalUnitType.PROPORTION);
                            treeNode.setIsRatio(true);
                            if (CollectionUtils.isEmpty(dimensionValues)) {
                                dimensionValues.addAll(treeNode.getDimensionValues());
                            }
                            displayName += "占比";
                        } else if (Objects.equals(regionNode.getType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
                            treeNode = operateNode(regionNode.getOperatorType());
                        }

                        treeNode.setDisplay(display);
                        treeNode.setNodeType(regionNode.getType());
                        treeNode.setParentfingerPrints(parentFingerprint);
                        treeNode.setDisplayName(displayName);
                        // 指纹增加类型
                        treeNode.setFingerprints(treeNode.getFingerprints() + NODE_TYPE_DELIMITER + regionNode.getType().getCode());
                        children.add(treeNode);
                    }
                    // 增加组间计算节点
                    OperatorType operatorType = region.getOperatorType();
                    if (operatorType != null) {
                        DismantlingTreeNode operateNode = operateNode(operatorType);
                        operateNode.setParentfingerPrints(parentFingerprint);
                        operateNode.setNodeType(DismantlingConfigTreeCalUnitType.OPERATOR);
                        operateNode.setDisplay(display);
                        // 指纹增加类型
                        operateNode.setFingerprints(operateNode.getFingerprints() + NODE_TYPE_DELIMITER + operateNode.getNodeType().getCode());
                        children.add(operateNode);
                    }

                    /**
                     * 计算节点是没有维度集合的，因此最终结果要补充进来
                     */
                    children.forEach(c -> {
                        if (CollectionUtils.isEmpty(c.getDimensionValues())) {
                            c.setDimensionValues(dimensionValues);
                        }
                    });
                }

            });
            // 如果最后一个节点是运算符节点，则移除此节点
            if (children.size() > 0) {
                DismantlingTreeNode last = children.get(children.size() - 1);
                if (Objects.equals(last.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
                    children.remove(children.size() - 1);
                }
            }
            /**
             * 如果最后一个节点显示的节点是运算符节点，则设置成不显示
             */
            List<DismantlingTreeNode> collect = children.stream().filter(node -> node.getDisplay()).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(collect)) {
                DismantlingTreeNode last = collect.get(collect.size() - 1);
                if (Objects.equals(last.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
                    last.setDisplay(false);
                }

            }
            result.add(children);
        });

        return result;
    }

    private DismantlingTreeNode operateNode(OperatorType operator) {
        DismantlingTreeNode node = new DismantlingTreeNode();
        node.setDisplayName(operator == null ? "" : operator.getDesc());
        node.setOperatorType(operator);
        return node;
    }

    private List<DismantlingTreeNode> queryNodeData(DismantlingConfigTreeFloor floor, DismantlingConfigTreeRegion region, DismantlingTreeQuery query) {
        Set<String> dimensionCodes = new HashSet<>();
        dimensionCodes.addAll(region.getDrillDownDimensionCodes());
        // 获取所有的node节点，需要查询的指标code
        List<DismantlingConfigTreeNode> regionNodes = region.getNodes();
        Set<String> measureCodes = new HashSet<>();
        for (DismantlingConfigTreeNode node : regionNodes) {
            if (!Objects.equals(DismantlingConfigTreeCalUnitType.OPERATOR, node.getType())) {
                measureCodes.add(node.getQueryMeasCode());
            }
            // 如果是比率型指标，需要计算分子、分母的值
            if (Objects.equals(node.getIsRatio(), true) && Objects.equals(DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE, node.getType())) {
                RatioMeasureDismanling ratioMeasureDismanling = measureManager.getRatioMeasureDismanling(node.getQueryMeasCode());
                measureCodes.add(ratioMeasureDismanling.getDenominatorCode());
                measureCodes.add(ratioMeasureDismanling.getMolecularCode());
                node.setDenominatorCode(ratioMeasureDismanling.getDenominatorCode());
                node.setMolecularCode(ratioMeasureDismanling.getMolecularCode());
            }
        }

        if (Objects.equals(DismantlingWay.STATIC, query.getDismantlingWay())) {
            // 静态拆解
            List<Filter> filters = new ArrayList<>();
            filters.addAll(query.getCurrentDateFilters());
            filters.addAll(query.getFilters());
            DataSource dataSource = buildDataSource(query.getSpaceId(), dimensionCodes, measureCodes, filters);
            dataSource.setUsername(query.getUserName());
            PageData pageData = chartQueryService.execQuery(dataSource);
            return parsePageData(pageData, region.getDrillDownDimensionCodes(), region);
        } else if (Objects.equals(DismantlingWay.DYNAMIC, query.getDismantlingWay())) {
            // 动态拆解
            // 基期
            List<Filter> baseFilters = new ArrayList<>();
            baseFilters.addAll(query.getBaseDateFilters());
            baseFilters.addAll(query.getFilters());

            // 本期
            List<Filter> currentFilters = new ArrayList<>();
            currentFilters.addAll(query.getCurrentDateFilters());
            currentFilters.addAll(query.getFilters());

            DataSource baseDataSource = buildDataSource(query.getSpaceId(), dimensionCodes, measureCodes, baseFilters);
            DataSource currentDataSource = buildDataSource(query.getSpaceId(), dimensionCodes, measureCodes, currentFilters);
            baseDataSource.setUsername(query.getUserName());
            currentDataSource.setUsername(query.getUserName());
            log.info(JSON.toJSONString(currentDataSource));
            PageData currentPageData = chartQueryService.execQuery(currentDataSource);
            PageData basePageData = chartQueryService.execQuery(baseDataSource);
            return parsePageData(basePageData, currentPageData, region.getDrillDownDimensionCodes(), region);
        } else {
            throw IndicatorParamNotValidException.error("拆解方式不合法");
        }
    }

    private List<DismantlingTreeNode> parsePageData(PageData pageData, List<String> drillDownCodes, DismantlingConfigTreeRegion region) {
        List<DismantlingTreeNode> result = new ArrayList<>();
        Map<String, DimensionQueryResult> map = buildFingerprintsPrefixMap(pageData.getCellList(), drillDownCodes);
        Map<String, List<Cell>> cellsMap = map.values().stream().collect(Collectors.toMap(DimensionQueryResult::getFingerprintsPrefix, DimensionQueryResult::getCells));
        Set<String> all = new HashSet<>();
        all.addAll(cellsMap.keySet());

        // 获取当前region查询的指标集合
        Set<String> measureCodes = new HashSet<>();
        Map<String, DismantlingConfigTreeNode> ratopMap = new HashMap<>();
        for (DismantlingConfigTreeNode node : region.getNodes()) {
            if (!Objects.equals(node.getType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
                measureCodes.add(node.getQueryMeasCode());
            }
            if (Objects.equals(node.getIsRatio(), true)) {
                ratopMap.put(node.getQueryMeasCode(), node);
            }
        }

        all.forEach(key -> {
            List<Cell> cells = Optional.ofNullable(cellsMap.get(key)).orElse(Collections.EMPTY_LIST);

            List<String> dimKeys = new LinkedList<>();
            Map<String, String> dimensionValueMap = new LinkedHashMap<>();
            DimensionQueryResult queryResult = map.get(key);
            if (queryResult != null) {
                dimKeys.addAll(queryResult.getDimensionKeys());
                dimensionValueMap = queryResult.getDimensionValueMap();
            }
            for (String measureCode : measureCodes) {
                Cell cell = cells.stream().filter(c -> Objects.equals(c.getCode(), measureCode)).findFirst().orElse(fillInCell(measureCode));
                DismantlingTreeNode treeNode = new DismantlingTreeNode();
                treeNode.setFingerprints(key + DIM_MEAS_DELIMITER + measureCode);
                buildDismantlingTreeNode(treeNode, cell, cell);
                BigDecimal baseTotal = calTotal(cellsMap.values(), measureCode);
                treeNode.getNodeData().setBaseTotal(baseTotal);
                treeNode.setCode(measureCode);
                treeNode.setDimensionValueMap(dimensionValueMap);
                treeNode.setDimensionValues(dimKeys);
                DismantlingConfigTreeNode dismantlingConfigTreeNode = ratopMap.get(measureCode);
                if (Objects.nonNull(dismantlingConfigTreeNode)) {
                    String denominatorCode = dismantlingConfigTreeNode.getDenominatorCode();
                    String molecularCode = dismantlingConfigTreeNode.getMolecularCode();
                    Cell denoCell = cells.stream().filter(c -> Objects.equals(c.getCode(), denominatorCode)).findFirst().orElse(fillInCell(measureCode));
                    Cell moleCell = cells.stream().filter(c -> Objects.equals(c.getCode(), molecularCode)).findFirst().orElse(fillInCell(measureCode));
                    treeNode.getNodeData().setDenominatorValue(NumberFormatUtil.formatExceptionWithZero(denoCell.getData()));
                    treeNode.getNodeData().setMolecularValue(NumberFormatUtil.formatExceptionWithZero(moleCell.getData()));
                    treeNode.getNodeData().setDenominatorValueTotal(calTotal(cellsMap.values(), denominatorCode));
                    treeNode.getNodeData().setMolecularValueTotal(calTotal(cellsMap.values(), molecularCode));
                    treeNode.setIsRatio(true);
                }
                result.add(treeNode);
            }
        });
        return result;
    }

    @Resource
    private MeasureManager measureManager;
    @Resource
    private CacheManager cacheManager;

    private List<DismantlingTreeNode> parsePageData(PageData basePageData, PageData currentPageData, List<String> drillDownCodes, DismantlingConfigTreeRegion region) {
        List<DismantlingTreeNode> result = new ArrayList<>();
        Map<String, DimensionQueryResult> baseMap = buildFingerprintsPrefixMap(basePageData.getCellList(), drillDownCodes);
        Map<String, DimensionQueryResult> currentMap = buildFingerprintsPrefixMap(currentPageData.getCellList(), drillDownCodes);
        Map<String, List<Cell>> baseCellsMap = baseMap.values().stream().collect(Collectors.toMap(DimensionQueryResult::getFingerprintsPrefix, DimensionQueryResult::getCells));
        Map<String, List<Cell>> currentCellsMap = currentMap.values().stream().collect(Collectors.toMap(DimensionQueryResult::getFingerprintsPrefix, DimensionQueryResult::getCells));
        Set<String> all = new HashSet<>();
        all.addAll(baseCellsMap.keySet());
        all.addAll(currentCellsMap.keySet());

        // 获取当前region查询的指标集合
        Set<String> measureCodes = new HashSet<>();
        Map<String, DismantlingConfigTreeNode> ratopMap = new HashMap<>();
        for (DismantlingConfigTreeNode node : region.getNodes()) {
            if (!Objects.equals(node.getType(), DismantlingConfigTreeCalUnitType.OPERATOR)) {
                measureCodes.add(node.getQueryMeasCode());
            }
            if (Objects.equals(node.getIsRatio(), true)) {
                ratopMap.put(node.getQueryMeasCode(), node);
            }
        }
        all.forEach(key -> {
            List<Cell> baseCells = Optional.ofNullable(baseCellsMap.get(key)).orElse(Collections.EMPTY_LIST);
            List<Cell> currentCells = Optional.ofNullable(currentCellsMap.get(key)).orElse(Collections.EMPTY_LIST);

            List<String> dimKeys = new LinkedList<>();
            DimensionQueryResult baseQueryResult = baseMap.get(key);
            DimensionQueryResult currentQueryResult = currentMap.get(key);
            Map<String, String> dimensionValueMap = new LinkedHashMap<>();
            if (baseQueryResult != null) {
                dimKeys.addAll(baseQueryResult.getDimensionKeys());
                dimensionValueMap = baseQueryResult.getDimensionValueMap();

            } else if (currentQueryResult != null) {
                dimKeys.addAll(currentQueryResult.getDimensionKeys());
                dimensionValueMap = currentQueryResult.getDimensionValueMap();
            }

            for (String measureCode : measureCodes) {
                // 计算合计值
                Cell currentCell = currentCells.stream().filter(cell -> Objects.equals(cell.getCode(), measureCode)).findFirst().orElse(fillInCell(measureCode));
                Cell baseCell = baseCells.stream().filter(cell -> Objects.equals(cell.getCode(), measureCode)).findFirst().orElse(fillInCell(measureCode));
                BigDecimal baseTotal = calTotal(baseCellsMap.values(), measureCode);
                BigDecimal currentTotal = calTotal(currentCellsMap.values(), measureCode);
                DismantlingTreeNode treeNode = new DismantlingTreeNode();
                treeNode.setFingerprints(key + DIM_MEAS_DELIMITER + measureCode);
                buildDismantlingTreeNode(treeNode, currentCell, baseCell);
                treeNode.getNodeData().setBaseTotal(baseTotal);
                treeNode.getNodeData().setCurrentTotal(currentTotal);
                treeNode.setCode(measureCode);
                treeNode.setDimensionValues(dimKeys);
                treeNode.setDimensionValueMap(dimensionValueMap);
                DismantlingConfigTreeNode dismantlingConfigTreeNode = ratopMap.get(measureCode);
                if (Objects.nonNull(dismantlingConfigTreeNode)) {
                    String denominatorCode = dismantlingConfigTreeNode.getDenominatorCode();
                    String molecularCode = dismantlingConfigTreeNode.getMolecularCode();
                    Cell currentDenoCell = currentCells.stream().filter(c -> Objects.equals(c.getCode(), denominatorCode)).findFirst().orElse(fillInCell(measureCode));
                    Cell currentMoleCell = currentCells.stream().filter(c -> Objects.equals(c.getCode(), molecularCode)).findFirst().orElse(fillInCell(measureCode));
                    Cell baseDenoCell = baseCells.stream().filter(c -> Objects.equals(c.getCode(), denominatorCode)).findFirst().orElse(fillInCell(measureCode));
                    Cell baseMoleCell = baseCells.stream().filter(c -> Objects.equals(c.getCode(), molecularCode)).findFirst().orElse(fillInCell(measureCode));
                    treeNode.getNodeData().setCurrentDenominatorValue(NumberFormatUtil.formatExceptionWithZero(currentDenoCell.getData()));
                    treeNode.getNodeData().setCurrentMolecularValue(NumberFormatUtil.formatExceptionWithZero(currentMoleCell.getData()));
                    treeNode.getNodeData().setBaseDenominatorValue(NumberFormatUtil.formatExceptionWithZero(baseDenoCell.getData()));
                    treeNode.getNodeData().setBaseMolecularValue(NumberFormatUtil.formatExceptionWithZero(baseMoleCell.getData()));
                    treeNode.setIsRatio(true);
                    treeNode.getNodeData().setCurrentDenominatorValueTotal(calTotal(currentCellsMap.values(), denominatorCode));
                    treeNode.getNodeData().setCurrentMolecularValueTotal(calTotal(currentCellsMap.values(), molecularCode));
                    treeNode.getNodeData().setBaseDenominatorValueTotal(calTotal(baseCellsMap.values(), denominatorCode));
                    treeNode.getNodeData().setBaseMolecularValueTotal(calTotal(baseCellsMap.values(), molecularCode));
                }
                result.add(treeNode);
            }
        });
        return result;
    }

    private String getDimValue(String fingerprintPrefix) {
        String dimValue = Optional.of(fingerprintPrefix).filter(s -> s.contains(DIM_CODE_DELIMITER)).map(s -> s.split(DIM_CODE_DELIMITER)).filter(arr -> arr.length > 0).map(arr -> arr[arr.length - 1]).orElse("");
        return dimValue;

    }

    private BigDecimal calTotal(Collection<List<Cell>> cells, String measCode) {
        BigDecimal total = cells.stream().flatMap(c -> c.stream()).filter(cell -> Objects.equals(cell.getCode(), measCode)).map(Cell::getData).map(data -> NumberFormatUtil.formatExceptionWithZero(data)).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
        return total;
    }

    /**
     * k:fingerprintsPrefix,指纹的前缀 用于合并基期和本期的数据
     */
    private Map<String, DimensionQueryResult> buildFingerprintsPrefixMap(List<List<Cell>> cellList, List<String> drillDownDimensionCodes) {
        Map<String, DimensionQueryResult> map = new LinkedHashMap<>();
        for (List<Cell> cells : cellList) {
            DimensionQueryResult fingerprints = new DimensionQueryResult();
            /**
             * 指纹的生成逻辑： 按照维度下钻的顺序进行排列,每下钻一个维度，用维度code和对应的维值组成一个字符串，最后加上指标code
             */
            String fingerprintsPrefix = "FIN";
            /**
             * 维值集合
             */
            List<String> dimKeys = new LinkedList<>();
            for (String code : drillDownDimensionCodes) {
                Cell c = cells.stream().filter(cell -> Objects.equals(cell.getCode(), code)).findFirst().orElse(fillInCell(code));
                fingerprintsPrefix += c.getCode() + DIM_CODE_DELIMITER + c.getId();
                dimKeys.add(c.getId());
                fingerprints.getDimensionValueMap().put(code, c.getId());
            }
            fingerprints.setCells(cells);
            fingerprints.setDimensionKeys(dimKeys);
            fingerprints.setFingerprintsPrefix(fingerprintsPrefix);
            map.put(fingerprintsPrefix, fingerprints);
        }
        return map;
    }

    private void buildDismantlingTreeNode(DismantlingTreeNode node, Cell currentCell, Cell preiviousCell) {
        DismantlingTreeNodeData data = new DismantlingTreeNodeData();
        data.setCurrentPeriodValue(NumberFormatUtil.formatExceptionWithZero(currentCell.getData()));
        data.setBasePeriodValue(NumberFormatUtil.formatExceptionWithZero(preiviousCell.getData()));
        node.setNodeData(data);
    }

    /**
     * 补位的cell，基期和本期值查询出的结果条数不一样时，就会导致节点配对失败，用补位的cell解决
     *
     * @param code
     * @return
     */
    private Cell fillInCell(String code) {
        Cell cell = new Cell();
        cell.setCode(code);
        return cell;
    }

    private DataSource buildDataSource(Long spaceId, Set<String> dimensionCodes, Set<String> measureCodes, List<Filter> filters) {
        DataSource dataSource = dorisQueryManager.buildDataSource(spaceId, measureCodes, dimensionCodes, null, filters);
        return dataSource;
    }

    public DismantlingTreeVO getDismantlingTreeCache(String taskId) {
        return redisCacheService.get(IndicatorConstant.DISMANTLING_TREE_QUERY_RESULT_PREFIX + taskId, DismantlingTreeVO.class);
    }

    public void setDismantlingTreeCache(String taskId, DismantlingTreeVO treeVO) {
        redisCacheService.put(IndicatorConstant.DISMANTLING_TREE_QUERY_RESULT_PREFIX + taskId, treeVO, 97, TimeUnit.MINUTES);
    }

    public void removeDismantlingTreeCache(String taskId) {
        redisCacheService.delete(IndicatorConstant.DISMANTLING_TREE_QUERY_RESULT_PREFIX + taskId);
    }

    public Integer getProgress(String taskId) {
        if (redisCacheService.hasKey(IndicatorConstant.DISMANTLING_TREE_QUERY_PROGRESS_PREFIX + taskId)) {
            Integer progress = redisCacheService.get(IndicatorConstant.DISMANTLING_TREE_QUERY_PROGRESS_PREFIX + taskId, Integer.class);
            progress = progress == null ? IndicatorConstant.PROGRESS_INITIALIZATION : progress;
            return progress;
        }

        return -1;
    }

    public void setProgress(String taskId, Integer progress) {
        redisCacheService.put(IndicatorConstant.DISMANTLING_TREE_QUERY_PROGRESS_PREFIX + taskId, progress, 97, TimeUnit.MINUTES);
    }

    public void removeProgress(String taskId) {
        redisCacheService.delete(IndicatorConstant.DISMANTLING_TREE_QUERY_PROGRESS_PREFIX + taskId);
    }

    public boolean hasSomeTree(long spaceId, String measCode) {
        return dismantlingTreeService.hasSomeTree(spaceId, measCode);
    }

    public DismantlingConfigTree buildDismantlingConfigTree(long spaceId, String measCode) {

        Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();
        if (allMeasureCodeMap.containsKey(measCode)) {
            Measure measure = allMeasureCodeMap.get(measCode);
            List<Dimension> dimensions = this.getDimensions(spaceId, measCode);
            if (dimensions == null || dimensions.isEmpty()) {
                throw new BusinessWarnException(ResultCode.SUCCESS, "不存在关联维度");
            }
            Dimension dimension = dimensions.get(0); // 默认取第一个

            DismantlingConfigTree dismantlingConfigTree = new DismantlingConfigTree();
            dismantlingConfigTree.setSpaceId(spaceId);
            dismantlingConfigTree.setRootMeasCode(measCode);
            dismantlingConfigTree.setName(measure.getCnName());
            dismantlingConfigTree.setMeasCodes(Collections.singleton(measCode));
            dismantlingConfigTree.setDimCodes(Collections.singleton(dimension.getCode()));
            dismantlingConfigTree.setFloors(this.buildDismantlingConfigTreeFloor(spaceId, measure, dimension));
            return dismantlingConfigTree;
        } else {
            throw new BusinessWarnException(ResultCode.SUCCESS, "指标编码不存在");
        }
    }

    private List<Dimension> getDimensions(long spaceId, String measCode) {
        RelatedCodeSet relatedCodeSet = new RelatedCodeSet();
        relatedCodeSet.setSpaceId(spaceId);
        relatedCodeSet.setMeasureSet(Collections.singleton(measCode));
        RelatedSet relatedSet = convert(relatedCodeSet);
        RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);

        if (resultRelatedSet != null) {

            MetadataCache metadataCache = cacheManager.getMetadataCache();
            Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
            List<Dimension> dimensions = allDimensionMap.values().stream().filter(d -> resultRelatedSet.getDimensionSet().contains(d.getId())).filter(d -> Objects.equals(d.getViewType(), ViewType.CHARACTER.getValue())).collect(Collectors.toList());
            List<Dimension> dimensionsViaDimensionValueCount = dimensions.stream().filter(d -> dimensionManager.getDimensionValueCount(d.getCode()) != null && dimensionManager.getDimensionValueCount(d.getCode()) <= 30 && dimensionManager.getDimensionValueCount(d.getCode()) >= 2).collect(Collectors.toList());

            if (CollectionUtils.isEmpty(dimensionsViaDimensionValueCount)) {
                throw new BusinessWarnException(ResultCode.SUCCESS, "没有可分析的维度");
            }

            return dimensionsViaDimensionValueCount;
        }

        return null;
    }

    public List<DismantlingConfigTreeFloor> buildDismantlingConfigTreeFloor(long spaceId, Measure measure, Dimension dimension) {
        List<DismantlingConfigTreeFloor> configTreeFloors = new ArrayList<>();
        // 默认创建两层
        DismantlingConfigTreeFloor configTreeFloor1 = new DismantlingConfigTreeFloor();
        DismantlingConfigTreeFloor configTreeFloor2 = new DismantlingConfigTreeFloor();
        // 默认每层仅一个region
        List<DismantlingConfigTreeRegion> configTreeRegions1 = new ArrayList<>();
        List<DismantlingConfigTreeRegion> configTreeRegions2 = new ArrayList<>();
        // 第一层第一个region
        DismantlingConfigTreeRegion configTreeRegion1 = new DismantlingConfigTreeRegion();
        configTreeRegion1.setRegionType(DismantlingConfigTreeRegionType.MEASURE_DISMANTLING);
        DismantlingConfigTreeNode node1 = new DismantlingConfigTreeNode();
        node1.setQueryMeasCode(measure.getCode());
        node1.setIsRatio(false);
        node1.setType(DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
        configTreeRegion1.setNodes(Collections.singletonList(node1));
        // 第二层第一个region
        DismantlingConfigTreeRegion configTreeRegion2 = new DismantlingConfigTreeRegion();
        configTreeRegion2.setRegionType(DismantlingConfigTreeRegionType.DIMENSION_DRILL_DOWN);
        configTreeRegion2.setOperatorType(OperatorType.ADDITION);
        configTreeRegion2.setDrillDownDimensionCodes(Collections.singletonList(dimension.getCode()));
        DimensionQueryParam dimensionQueryParam = new DimensionQueryParam();
        // dimensionQueryParam.setSpaceId(spaceId);
        dimensionQueryParam.setCode(dimension.getCode());
        dimensionQueryParam.setCacheStrategy(CacheStrategy.QUERY_UPDATE);
        PageData pageDataDimensionValues = dimensionQueryService.execQueryDimensionValues(dimensionQueryParam);
        if (pageDataDimensionValues != null && !pageDataDimensionValues.getCellList().isEmpty()) {
            List<Cell> cells = pageDataDimensionValues.getCellList().stream().flatMap(List::stream).collect(Collectors.toList());
            configTreeRegion2.setDisplayDimensionValues(cells.stream().map(p -> p.getId()).collect(Collectors.toSet()));
        }
        String fingerprints = "FIN#MEASCODE#" + measure.getCode() + "#NODETYPE#0";
        configTreeRegion2.setParentFingerprints(Collections.singleton(fingerprints));
        DismantlingConfigTreeNode node2 = new DismantlingConfigTreeNode();
        node2.setQueryMeasCode(measure.getCode());
        node2.setIsRatio(false);
        node2.setType(DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
        configTreeRegion2.setNodes(Collections.singletonList(node2));
        // set
        configTreeRegions1.add(configTreeRegion1);
        configTreeRegions2.add(configTreeRegion2);
        configTreeFloor1.setRegions(configTreeRegions1);
        configTreeFloor2.setRegions(configTreeRegions2);
        configTreeFloors.add(configTreeFloor1);
        configTreeFloors.add(configTreeFloor2);

        return configTreeFloors;
    }

    public void assemble(DismantlingConfigTree dismantlingConfigTree) {
        List<DismantlingConfigTreeFloor> floors = dismantlingConfigTree.getFloors();
        for (DismantlingConfigTreeFloor floor : floors) {
            List<DismantlingConfigTreeRegion> regions = floor.getRegions();
            Map<String, DismantlingConfigTreeRegion> regionMap = new LinkedHashMap<>();
            for (DismantlingConfigTreeRegion region : regions) {
                if (regionMap.containsKey(region.generateUUID())) {
                    DismantlingConfigTreeRegion newRegion = regionMap.get(region.generateUUID());
                    newRegion.getDisplayDimensionValues().addAll(region.getDisplayDimensionValues());
                    newRegion.getParentFingerprints().addAll(region.getParentFingerprints());
                } else {
                    regionMap.put(region.generateUUID(), region);
                }
            }
            Collection<DismantlingConfigTreeRegion> values = regionMap.values();
            floor.getRegions().clear();
            floor.getRegions().addAll(values);
        }
    }
}
