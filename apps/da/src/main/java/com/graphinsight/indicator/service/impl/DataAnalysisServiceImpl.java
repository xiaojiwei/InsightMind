package com.graphinsight.indicator.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.enums.ResultCode;
import com.graphinsight.indicator.exception.BusinessWarnException;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.TSpace;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.enums.CacheStrategy;
import com.graphinsight.indicator.enums.CellType;
import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.enums.TimeRange;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.manager.SpaceManager;
import com.graphinsight.indicator.model.BaseConfigure;
import com.graphinsight.indicator.model.Cell;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.Operator;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.Space;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.vo.AttributionAnalysisRequestVO;
import com.graphinsight.indicator.model.vo.AttributionAnalysisResponseVO;
import com.graphinsight.indicator.model.vo.DimensionValueTrendResponseVO;
import com.graphinsight.indicator.model.vo.AttributionAnalysisDimensionResponseVO;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.IDataAnalysisService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;

@Service
public class DataAnalysisServiceImpl implements IDataAnalysisService {

    @Resource
    private MeasureManager measureManager;
    @Resource
    private CacheManager cacheManager;
    @Resource
    BloodManager bloodManager;
    @Resource
    private DimensionManager dimensionManager;
    @Resource
    private SpaceManager spaceManager;
    @Resource
    private ChartQueryService chartQueryService;
    @Resource
    private IDimensionService dimensionService;
    @Resource
    private ITSpaceService spaceService;

    @Override
    public AttributionAnalysisResponseVO attributionOverview(AttributionAnalysisRequestVO attributionAnalysis) {
        this.checkParams(attributionAnalysis);
        AttributionAnalysisResponseVO responseVO = new AttributionAnalysisResponseVO();

        Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();
        Measure measure = allMeasureCodeMap.get(attributionAnalysis.getMeasureCode());
        Space space = this.getDefaultSpace();

        List<Dimension> dimensions = this.getDimensions(space.getId(), attributionAnalysis.getMeasureCode(), attributionAnalysis.getFilterDimCode());
        if (dimensions != null && dimensions.size() > 0) {
            responseVO.setDimensions(new ArrayList<>());
            int i = 0;
            for (Dimension dimension : dimensions) {
                AttributionAnalysisDimensionResponseVO dimensionResponseVO = new AttributionAnalysisDimensionResponseVO();
                dimensionResponseVO.setOrder(i++);
                dimensionResponseVO.setDimensionCode(dimension.getCode());
                dimensionResponseVO.setDimensionName(dimension.getCnName());
                responseVO.getDimensions().add(dimensionResponseVO);
            }

            responseVO.setMeasureCode(measure.getCode());
            responseVO.setMeasureName(measure.getCnName());
            responseVO.setBasePeriod(attributionAnalysis.getBasePeriod());
            responseVO.setCurrentPeriod(attributionAnalysis.getCurrentPeriod());

            String currentValue = chartQueryService.execOnlySingleMeasure(attributionAnalysis.getMeasureCode(), attributionAnalysis.getFilterDimCode(), attributionAnalysis.getCurrentPeriod(), new HashSet<>(), UserThreadLocalUtil.getUserName(), space.getId());
            String baseValue = chartQueryService.execOnlySingleMeasure(attributionAnalysis.getMeasureCode(), attributionAnalysis.getFilterDimCode(), attributionAnalysis.getBasePeriod(), new HashSet<>(), UserThreadLocalUtil.getUserName(), space.getId());

            BigDecimal currentDecimal = new BigDecimal(currentValue.replace(",", ""));
            BigDecimal baseDecimal = new BigDecimal(baseValue.replace(",", ""));
            BigDecimal deltDecimal = currentDecimal.subtract(baseDecimal).setScale(3, RoundingMode.HALF_UP);
            BigDecimal deltPercentDecimal = deltDecimal.divide(baseDecimal, 3, RoundingMode.HALF_UP).setScale(3, RoundingMode.HALF_UP);

            responseVO.setCurrentValue(currentDecimal);
            responseVO.setBaseValue(baseDecimal);
            responseVO.setDelta(deltDecimal);
            responseVO.setDeltaPercent(deltPercentDecimal);

            int comparisonResult = deltDecimal.compareTo(BigDecimal.ZERO);
            if (comparisonResult < 0) {
                // 下降
                responseVO.setTrend(-1);
            } else if (comparisonResult > 0) {
                // 上升
                responseVO.setTrend(1);
            } else {
                // 持平
                responseVO.setTrend(0);
            }
        }

        return responseVO;
    }

    @Override
    public AttributionAnalysisResponseVO attributionDetail(AttributionAnalysisRequestVO attributionAnalysis) {
        this.checkParams(attributionAnalysis);
        Space space = this.getDefaultSpace();

        // 本期
        DataSource currentDataSource = this.buildDataSource(attributionAnalysis, space);
        List<Filter> currentFilterList = new LinkedList<>();
        Filter currentFilter = this.buildFilter(attributionAnalysis.getFilterDimCode(), Arrays.asList(attributionAnalysis.getCurrentPeriod()));
        currentFilterList.add(currentFilter);
        currentDataSource.setFilterList(currentFilterList);
        PageData currentPageData = chartQueryService.execQuery(currentDataSource);
        if (currentPageData == null || currentPageData.getCellList() == null || currentPageData.getCellList().isEmpty()) {
            throw new BusinessWarnException(ResultCode.SUCCESS, "未查询到本期数据");
        }
        Map<String, List<Cell>> currentCellsMap = buildMeasureValueMap(currentPageData.getCellList());

        // 基期
        Map<String, List<String>> baseColDimFilterMap = new HashMap<>();
        List<List<Cell>> cellList = currentPageData.getCellList();
        for (List<Cell> cells : cellList) {
            List<Cell> dimCells = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.DIMENSION)).collect(Collectors.toList());
            dimCells.forEach(cell -> {
                if (!baseColDimFilterMap.containsKey(cell.getCode())) {
                    baseColDimFilterMap.put(cell.getCode(), new ArrayList<>());
                }
                baseColDimFilterMap.get(cell.getCode()).add(cell.getId());
            });
        }
        DataSource baseDataSource = this.buildDataSource(attributionAnalysis, space);
        List<Filter> baseFilterList = new LinkedList<>();
        Filter baseDateFilter = this.buildFilter(attributionAnalysis.getFilterDimCode(), Arrays.asList(attributionAnalysis.getBasePeriod()));
        baseFilterList.add(baseDateFilter);
        baseColDimFilterMap.keySet().forEach(code -> {
            Filter filter = this.buildFilter(code, baseColDimFilterMap.get(code));
            baseFilterList.add(filter);
        });
        baseDataSource.setFilterList(baseFilterList);
        PageData basePageData = chartQueryService.execQuery(baseDataSource);
        if (basePageData == null || basePageData.getCellList() == null || basePageData.getCellList().isEmpty()) {
            throw new BusinessWarnException(ResultCode.SUCCESS, "未查询到基期数据");
        }
        Map<String, List<Cell>> baseCellsMap = buildMeasureValueMap(basePageData.getCellList());

        // 构建结果数据
        AttributionAnalysisResponseVO responseVO = new AttributionAnalysisResponseVO();
        responseVO.setBasePeriod(attributionAnalysis.getBasePeriod());
        responseVO.setCurrentPeriod(attributionAnalysis.getCurrentPeriod());
        responseVO.setMeasureCode(attributionAnalysis.getMeasureCode());
        responseVO.setTrend(attributionAnalysis.getTrend());
        responseVO.setDimensions(new ArrayList<>());

        // 计算
        AttributionAnalysisDimensionResponseVO dimensionResponseVO = new AttributionAnalysisDimensionResponseVO();
        dimensionResponseVO.setSameTrend(new ArrayList<>());
        dimensionResponseVO.setOppositeTrend(new ArrayList<>());
        for (Map.Entry<String, List<Cell>> entry : currentCellsMap.entrySet()) {
            String key = entry.getKey();
            List<Cell> cells = entry.getValue();

            Cell currentDimCell = cells.get(0); // 本期：维度、维值
            Cell currentBizCell = cells.get(1); // 本期：指标、数值

            if (baseCellsMap.containsKey(key)) {
                // Cell baseDimCell = baseCellsMap.get(key).get(0); // 基期：维度、维值
                Cell baseBizCell = baseCellsMap.get(key).get(1); // 基期：指标、数值

                BigDecimal currentValue = new BigDecimal(currentBizCell.getData().replace(",", ""));
                BigDecimal baseValue = new BigDecimal(baseBizCell.getData().replace(",", ""));
                BigDecimal deltDecimal = currentValue.subtract(baseValue).setScale(3, RoundingMode.HALF_UP);
                BigDecimal deltPercentDecimal = deltDecimal.divide(baseValue, 3, RoundingMode.HALF_UP).setScale(3, RoundingMode.HALF_UP);

                // 构建结果数据
                this.buildDimensionValueTrend(currentDimCell.getData(), currentValue, baseValue, deltDecimal, deltPercentDecimal, dimensionResponseVO, attributionAnalysis.getTrend());
            }
        }

        // 趋势排序
        this.trendSorted(dimensionResponseVO, attributionAnalysis.getTrend());

        responseVO.getDimensions().add(dimensionResponseVO);

        return responseVO;
    }

    private void checkParams(AttributionAnalysisRequestVO attributionAnalysis) {
        // 1.日期大的默认算本期；
        // 2.只有标准维可以出现在归因分析中；

        int comparison = attributionAnalysis.getBasePeriod().compareTo(attributionAnalysis.getCurrentPeriod());
        if (comparison > 0) {
            throw new BusinessWarnException(ResultCode.SUCCESS, "本期必须大于基期");
        }
        if (comparison == 0) {
            throw new BusinessWarnException(ResultCode.SUCCESS, "本期与基期不能相等");
        }

        boolean hasMeasure = cacheManager.getMetadataCache().getAllMeasureCodeMap().containsKey(attributionAnalysis.getMeasureCode());
        if (!hasMeasure) {
            throw new BusinessWarnException(ResultCode.SUCCESS, "指标不存在");
        }
    }

    private List<Dimension> getDimensions(long spaceId, String measCode, String dimCode) {
        int measId = cacheManager.getMetadataCache().getAllMeasureCodeMap().get(measCode).getId();
        Set<Integer> relatedDimensionIds = measureManager.getDimensionByMeas(measId);
        if (relatedDimensionIds == null || relatedDimensionIds.isEmpty()) {
            throw new BusinessWarnException(ResultCode.SUCCESS, "指定指标没有可分析的维度");
        }
        List<Dimension> dimensions = new ArrayList<>();
        for (Integer dimId : relatedDimensionIds) {
            DimensionCache dimensionCache = cacheManager.getDimensionCache(dimId);
            if (dimensionCache != null) {
                dimensions.add(dimensionCache.getDimension());
            }
        }

        // Set<Dimension> dimensions = bloodManager.listRelatedDimensions(measCode, dimCode, spaceId);
        List<Dimension> dimensionList = dimensions.stream() // 流
                .filter(d -> Objects.equals(d.getViewType(), ViewType.CHARACTER.getValue())) // 仅保留字符串
                // .filter(d -> Objects.equals(d.getDimType(), 2)) // 有维表
                .filter(d -> Objects.equals(d.getOnline(), 1)) // 在线
                .filter(d -> Objects.equals(d.getIsDelete(), 0)) // 未删除
                // .filter(d -> {
                //     Integer cnt = dimensionManager.getDimensionValueCount(d.getCode());
                //     if (cnt != null && cnt > 0) {
                //         return true;
                //     } else {
                //         return false;
                //     }
                // }) // 有维值
                .sorted(Comparator.comparing(Dimension::getEnName)) // 固定顺序
                .limit(4) // 仅取4个
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(dimensionList)) {
            throw IndicatorParamNotValidException.error("没有可分析的维度");
        }

        // 固定顺序，仅取4个
        // dimensionList = dimensionList.stream().sorted(Comparator.comparing(Dimension::getEnName)).limit(4).collect(Collectors.toList());

        return dimensionList;
    }

    private Space getDefaultSpace() {
        TSpace tSpace = spaceService.getAiSpaceById();

        if (tSpace == null) {
            throw new BusinessWarnException(ResultCode.SUCCESS, "没有可分析的空间");
        }

        Space space = new Space();
        BeanUtils.copyProperties(tSpace, space);

        return space;
    }

    private DataSource buildDataSource(AttributionAnalysisRequestVO attributionAnalysis, Space space) {
        DataSource dataSource = new DataSource();
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(space.getId());
        dataSource.setChartType(ChartType.LINE);
        dataSource.setUsername(UserThreadLocalUtil.getUserName());
        List<BaseConfigure> configureList = new LinkedList<>();

        BaseConfigure measureConfigure = new BaseConfigure();
        measureConfigure.setCode(attributionAnalysis.getMeasureCode());
        configureList.add(measureConfigure);

        BaseConfigure dimensionConfigure = new BaseConfigure();
        dimensionConfigure.setCode(attributionAnalysis.getDimensionCode());
        configureList.add(dimensionConfigure);

        dataSource.setConfigureList(configureList);
        return dataSource;
    }

    private Filter buildFilter(String filterCode, List<String> dataList) {
        Filter dimFilter = new Filter();

        dimFilter.setCode(filterCode);
        List<Operator> operatorList = new LinkedList<>();
        Operator operator = new Operator();
        Dimension dimension = dimensionService.getOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode, filterCode));
        if (Objects.nonNull(dimension)) {
            if (!Objects.equals(ViewType.CHARACTER.getValue(), dimension.getViewType())) {
                operator.setTimeRange(TimeRange.DATE);
            }
        }
        // operator.setCode(filterCode);
        operator.setDataList(dataList);
        operator.setSqlOprType(SqlOprType.IN);
        operatorList.add(operator);
        dimFilter.setInternal(true);
        dimFilter.setOperatorList(operatorList);
        return dimFilter;
    }

    private Map<String, List<Cell>> buildMeasureValueMap(List<List<Cell>> cellList) {
        Map<String, List<Cell>> map = new LinkedHashMap<>();
        for (List<Cell> cells : cellList) {
            // 找到所有的维度类型cell
            List<Cell> dimCells = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.DIMENSION)).collect(Collectors.toList());
            String key = dimCells.stream().map(Cell::getId).sorted(Comparator.comparing(id -> id))// 按照ID值排序，保证key的组成顺序一致
                    .collect(Collectors.joining("-"));
            map.put(key, cells);
        }
        return map;
    }

    private void buildDimensionValueTrend(String dimValue, BigDecimal currentValue, BigDecimal baseValue, BigDecimal deltDecimal, BigDecimal deltPercentDecimal, AttributionAnalysisDimensionResponseVO dimensionResponseVO, int trend) {
        // 正增长
        DimensionValueTrendResponseVO positiveGrowth = null;
        // 负增长
        DimensionValueTrendResponseVO negativeGrowth = null;
        int comparisonResult = deltDecimal.compareTo(BigDecimal.ZERO);
        if (comparisonResult < 0) {
            negativeGrowth = new DimensionValueTrendResponseVO();
            negativeGrowth.setBaseValue(baseValue);
            negativeGrowth.setCurrentValue(currentValue);
            negativeGrowth.setDelta(deltDecimal);
            negativeGrowth.setDeltaPercent(deltPercentDecimal);
            negativeGrowth.setDimensionValue(dimValue);
        } else if (comparisonResult > 0) {
            positiveGrowth = new DimensionValueTrendResponseVO();
            positiveGrowth.setBaseValue(baseValue);
            positiveGrowth.setCurrentValue(currentValue);
            positiveGrowth.setDelta(deltDecimal);
            positiveGrowth.setDeltaPercent(deltPercentDecimal);
            positiveGrowth.setDimensionValue(dimValue);
        } else {
            // 持平，过滤。（不展示）
        }

        // 趋势为增长或持平
        if (trend > 0 || trend == 0) {
            // 同向：正增长，反向：负增长
            if (positiveGrowth != null) {
                dimensionResponseVO.getSameTrend().add(positiveGrowth);
            }
            if (negativeGrowth != null) {
                dimensionResponseVO.getOppositeTrend().add(negativeGrowth);
            }
        }
        // 趋势为下降
        if (trend == -1) {
            // 同向：负增长，反向：正增长
            if (negativeGrowth != null) {
                dimensionResponseVO.getSameTrend().add(negativeGrowth);
            }
            if (positiveGrowth != null) {
                dimensionResponseVO.getOppositeTrend().add(positiveGrowth);
            }
        }
    }

    private void trendSorted(AttributionAnalysisDimensionResponseVO dimensionResponseVO, int trend) {
        // 排序规则：全部升序。
        // 正数：反转取前三；
        // 负数：直接取前三。

        // 增长趋势：同向是正数，反转取前三。
        if (trend >= 0) {
            if (dimensionResponseVO.getSameTrend() != null && dimensionResponseVO.getSameTrend().size() > 0) {
                List<DimensionValueTrendResponseVO> temp = dimensionResponseVO.getSameTrend().stream().sorted(Comparator.comparing(DimensionValueTrendResponseVO::getDelta).reversed()).limit(3).collect(Collectors.toList());
                dimensionResponseVO.setSameTrend(temp);
            }
            if (dimensionResponseVO.getOppositeTrend() != null && dimensionResponseVO.getOppositeTrend().size() > 0) {
                List<DimensionValueTrendResponseVO> temp = dimensionResponseVO.getOppositeTrend().stream().sorted(Comparator.comparing(DimensionValueTrendResponseVO::getDelta)).limit(3).collect(Collectors.toList());
                dimensionResponseVO.setOppositeTrend(temp);
            }
        }
        // 下降趋势：反向是正数，反转取前三。
        else {
            if (dimensionResponseVO.getSameTrend() != null && dimensionResponseVO.getSameTrend().size() > 0) {
                List<DimensionValueTrendResponseVO> temp = dimensionResponseVO.getSameTrend().stream().sorted(Comparator.comparing(DimensionValueTrendResponseVO::getDelta)).limit(3).collect(Collectors.toList());
                dimensionResponseVO.setSameTrend(temp);
            }
            if (dimensionResponseVO.getOppositeTrend() != null && dimensionResponseVO.getOppositeTrend().size() > 0) {
                List<DimensionValueTrendResponseVO> temp = dimensionResponseVO.getOppositeTrend().stream().sorted(Comparator.comparing(DimensionValueTrendResponseVO::getDelta).reversed()).limit(3).collect(Collectors.toList());
                dimensionResponseVO.setOppositeTrend(temp);
            }
        }
    }
}
