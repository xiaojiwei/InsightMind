package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Joiner;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskDetailService;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.CacheStrategy;
import com.graphinsight.indicator.enums.CellType;
import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.ContributionCalculationType;
import com.graphinsight.indicator.enums.DimensionAnalysisTaskDetailStatusType;
import com.graphinsight.indicator.enums.DimensionAnalysisTaskStatusType;
import com.graphinsight.indicator.enums.SortType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.enums.TimeRange;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.BaseConfigure;
import com.graphinsight.indicator.model.Cell;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.Operator;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.*;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.util.NumberFormatUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.graphinsight.indicator.util.contribution.ContributionStrategy;
import com.graphinsight.indicator.util.contribution.ContributionStrategyHolder;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationResult;
import com.graphinsight.indicator.util.contribution.bean.ContributionOriginQueryParam;
import com.graphinsight.indicator.util.contribution.bean.RatioMeasureCalculationParam;
import com.graphinsight.indicator.util.gini.GiniCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/7/5
 * Desc: 多维分析模块
 */
@Slf4j
@Service
@DS("mysql")
public class DimensionAnalysisManager {

    @Autowired
    private IDimensionAnalysisTaskService taskService;
    @Autowired
    private IDimensionAnalysisTaskDetailService detailService;
    @Autowired
    private BloodManager bloodManager;
    @Autowired
    private IDimensionService dimensionService;
    @Autowired
    private IMeasureService measureService;
    @Autowired
    private RedisCacheService redisCacheService;
    @Autowired
    CacheManager cacheManager;
    @Value("${redisKeyPrefix}")
    String redisKeyPrefix;
    @Autowired
    UserManager userManager;

    public void memSortDemo(Set<String> dimCodes, String measCode) {
        DataSource dataSource = new DataSource();
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(100L);
        dataSource.setChartType(ChartType.LINE);
        dataSource.setUsername(UserThreadLocalUtil.getUserName());
        List<BaseConfigure> configureList = new LinkedList<>();
        BaseConfigure measureConfigure = new BaseConfigure();
        measureConfigure.setCode(measCode);
        // TODO ORDER配置
        configureList.add(measureConfigure);
        dimCodes.forEach(code -> {
            BaseConfigure dimensionConfigure = new BaseConfigure();
            dimensionConfigure.setCode(code);
            configureList.add(dimensionConfigure);
        });
        dataSource.setPageable(false);
        dataSource.setConfigureList(configureList);
        log.info("开始查询");
        long l2 = System.currentTimeMillis();
        PageData pageData = chartQueryService.execQuery(dataSource);
        long l3 = System.currentTimeMillis();
        log.info("查询结束,耗时:{} s", (l3 - l2) / 1000);

        log.info("查询前参数准备");
        long l4 = System.currentTimeMillis();
        List<List<Cell>> cellList = pageData.getCellList();
        List<Cell> measCells = new ArrayList<>();
        cellList.forEach(cells -> {
            Cell cell = cells.stream().filter(c -> Objects.equals(CellType.MEASURE, c.getType())).findFirst().orElse(defaultCell(measCode));
            measCells.add(cell);
        });
        long l5 = System.currentTimeMillis();
        log.info("参数准备结束,耗时:{} s", (l5 - l4) / 1000);

        log.info("开始排序");
        long l = System.currentTimeMillis();
        measCells.stream().sorted(Comparator.comparing(Cell::getData)).collect(Collectors.toList());
        long l1 = System.currentTimeMillis();
        log.info("排序结束,耗时:{} s", (l1 - l) / 1000);
        log.info("方法结束,总耗时:{} s", (l1 - l2) / 1000);

    }

    public List<DimensionAnalysisTask> listExistedTask(String measCode, String dimCode, String baseDate, String currentDate, Long spaceId, String username) {
        QueryWrapper<DimensionAnalysisTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dim_code", dimCode).eq("meas_code", measCode).eq("base_period", baseDate).eq("current_period", currentDate).eq("space_id", spaceId);
        List<DimensionAnalysisTask> dimensionAnalysisTasks = taskService.list(queryWrapper);
        if (CollectionUtils.isEmpty(dimensionAnalysisTasks)) {
            return Collections.EMPTY_LIST;
        }
        return dimensionAnalysisTasks;
    }

    public DimensionAnalysisDetailVO detail(String measCode) {
        List<DimensionAnalysisTask> dimensionAnalysisTasks = taskService.getByMeasCode(measCode);
        if (dimensionAnalysisTasks != null && dimensionAnalysisTasks.size() > 0) {
            DimensionAnalysisTask dimensionAnalysisTask;
            Optional<DimensionAnalysisTask> optional = dimensionAnalysisTasks.stream().filter(p -> p.getSpaceId() == 4).findFirst();
            if (optional.isPresent()) {
                dimensionAnalysisTask = optional.get();
            } else {
                dimensionAnalysisTask = dimensionAnalysisTasks.get(0);
            }

            // 检查指标权限
            UserContext userContext = userManager.getUserContext(dimensionAnalysisTask.getSpaceId(), UserThreadLocalUtil.getUserName());
            List<Measure> authMeasures = userContext.getAuthMeasures();
            Set<String> authMeasCodes = authMeasures.stream().map(Measure::getCode).collect(Collectors.toSet());
            if (authMeasCodes.contains(measCode)) {
                String cacheKey = String.format("dimension_detail_%s_%s", measCode, dimensionAnalysisTask.getId());
                if (redisCacheService.hasKey(cacheKey)) {
                    return redisCacheService.get(cacheKey, DimensionAnalysisDetailVO.class);
                }

                DimensionAnalysisDetailVO result = this.detail(dimensionAnalysisTask.getId());
                redisCacheService.put(cacheKey, result, 30, TimeUnit.DAYS);
                return result;
            }
        }

        return null;
    }

    public DimensionAnalysisDetailVO detail(Long taskId) {
        DimensionAnalysisTask analysisTask = taskService.getById(taskId);
        if (Objects.isNull(analysisTask)) {
            throw IndicatorParamNotValidException.error("任务不存在");
        }
        DimensionAnalysisDetailVO detailVO = convert2Detail(analysisTask);
        List<DimensionAnalysisTaskDetail> analysisTaskDetails = detailService.list(Wrappers.<DimensionAnalysisTaskDetail>lambdaQuery().eq(DimensionAnalysisTaskDetail::getTaskId, taskId));
        if (!CollectionUtils.isEmpty(analysisTaskDetails)) {
            List<DimensionAnalysisTaskDetail> sortedList = analysisTaskDetails.stream().filter(d -> Objects.nonNull(d.getGiniValue())).filter(d -> dimensionService.getOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode, d.getDimCode())) != null).sorted(Comparator.comparing(DimensionAnalysisTaskDetail::getGiniValue).reversed()).collect(Collectors.toList());
            List<DimensionSubAnalysisVO> subAnalysisVOList = sortedList.stream().map(d -> convert(d)).collect(Collectors.toList());
            detailVO.setDimensionSubAnalysisList(subAnalysisVOList);
        }
        return detailVO;
    }

    private DimensionSubAnalysisVO convert(DimensionAnalysisTaskDetail detail) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();
        DimensionSubAnalysisVO vo = new DimensionSubAnalysisVO();
        Dimension dimension = allDimensionCodeMap.get(detail.getDimCode());
        if (Objects.nonNull(dimension)) {
            vo.setDimCnName(dimension.getCnName());
            vo.setDimId(dimension.getId());
            vo.setViewType(dimension.getViewType());
        }
        vo.setDimCode(detail.getDimCode());
        vo.setGini(detail.getGiniValue().setScale(2, BigDecimal.ROUND_DOWN).toString());
        return vo;
    }

    private DimensionAnalysisDetailVO convert2Detail(DimensionAnalysisTask analysisTask) {
        DimensionAnalysisDetailVO detailVO = new DimensionAnalysisDetailVO();
        BeanUtils.copyProperties(analysisTask, detailVO);
        String currentValue = analysisTask.getCurrentValue();
        String baseValue = analysisTask.getBaseValue();
        BigDecimal current = NumberFormatUtil.format(currentValue);
        BigDecimal base = NumberFormatUtil.format(baseValue);
        if (Objects.nonNull(current) && Objects.nonNull(base)) {
            if (BigDecimal.ZERO.doubleValue() == base.doubleValue()) {
                detailVO.setDeltaValueRate("-");
            } else {
                BigDecimal rate = current.subtract(base);
                boolean ratio = measureManager.isRatio(analysisTask.getMeasCode());
                if (!ratio) {
                    rate = rate.divide(base, 4, BigDecimal.ROUND_DOWN);
                }
                detailVO.setDeltaValueRate(rate.toString());
            }
        }

        detailVO.setBaseDate(analysisTask.getBasePeriod());
        detailVO.setCurrentDate(analysisTask.getCurrentPeriod());
        return detailVO;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long taskId) {
        taskService.removeById(taskId);
        detailService.remove(Wrappers.<DimensionAnalysisTaskDetail>lambdaQuery().eq(DimensionAnalysisTaskDetail::getTaskId, taskId));
        removeProgress(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long taskId) {
        DimensionAnalysisTask analysisTask = taskService.getById(taskId);
        if (analysisTask == null) {
            throw IndicatorParamNotValidException.error("任务不存在,ID: " + taskId);
        }
        analysisTask.setStatus(DimensionAnalysisTaskStatusType.INITIAL.getCode());
        taskService.updateById(analysisTask);
        List<DimensionAnalysisTaskDetail> analysisTaskDetails = detailService.list(Wrappers.<DimensionAnalysisTaskDetail>lambdaQuery().eq(DimensionAnalysisTaskDetail::getTaskId, taskId));
        if (!CollectionUtils.isEmpty(analysisTaskDetails)) {
            analysisTaskDetails.forEach(detail -> initialDetail(detail));
            detailService.updateBatchById(analysisTaskDetails);
        }
        removeProgress(taskId);
    }

    private void initialDetail(DimensionAnalysisTaskDetail detail) {
        detail.setStatus(DimensionAnalysisTaskDetailStatusType.INITIAL.getCode());
        detail.setErrorMessage(null);
        detail.setGiniValue(null);
    }

    public void retryTask(Long taskId) {
        DimensionAnalysisTask analysisTask = taskService.getById(taskId);
        if (analysisTask == null) {
            throw IndicatorParamNotValidException.error("任务不存在,ID: " + taskId);
        }
        analysisTask.setStatus(DimensionAnalysisTaskStatusType.INITIAL.getCode());
        taskService.updateById(analysisTask);
        redisCacheService.lpush(redisKeyPrefix + IndicatorConstant.DIMENSION_ANALYSIS_TASK_QUEUE, taskId.toString());
    }

    public List<DimensionAnalysisVO> listProcressingTask(Long spaceId) {
        List<DimensionAnalysisTask> dimensionAnalysisTasks = processingTask(spaceId);
        if (CollectionUtils.isEmpty(dimensionAnalysisTasks)) {
            return Collections.EMPTY_LIST;
        }
        return dimensionAnalysisTasks.stream().map(task -> convert(task)).collect(Collectors.toList());
    }

    public Boolean hasProcessingTask(Long spaceId) {
        List<DimensionAnalysisTask> dimensionAnalysisTasks = processingTask(spaceId);
        return !CollectionUtils.isEmpty(dimensionAnalysisTasks);
    }

    private List<DimensionAnalysisTask> processingTask(Long spaceId) {
        QueryWrapper<DimensionAnalysisTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("space_id", spaceId).in("status", Arrays.asList(DimensionAnalysisTaskStatusType.INITIAL.getCode(), DimensionAnalysisTaskStatusType.PROCESSION.getCode(), DimensionAnalysisTaskStatusType.PART_COMPLETED.getCode(), DimensionAnalysisTaskStatusType.FAILED.getCode()));
        List<DimensionAnalysisTask> dimensionAnalysisTasks = taskService.list(queryWrapper);
        return dimensionAnalysisTasks;
    }

    public PageVO<DimensionAnalysisVO> listTask(DimensionAnalysisTaskQueryVO queryVO) {
        PageVO<DimensionAnalysisVO> pageVO = new PageVO<>();
        QueryWrapper<DimensionAnalysisTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("space_id", queryVO.getSpaceId()).in("status", Arrays.asList(DimensionAnalysisTaskStatusType.COMPLETED.getCode(), DimensionAnalysisTaskStatusType.PART_COMPLETED.getCode())).orderByDesc("create_time");

        if (StringUtils.hasLength(queryVO.getSearchText())) {
            queryWrapper.like("report_name", queryVO.getSearchText());
        }
        if (queryVO.isMine()) {
            String userName = UserThreadLocalUtil.getUserName();
            queryWrapper.eq("creator", userName);
        }
        Page<DimensionAnalysisTask> page = taskService.page(new Page<>(queryVO.getPageNo(), queryVO.getPageSize()), queryWrapper);
        pageVO.setTotal(page.getTotal());
        List<DimensionAnalysisTask> dimensionAnalysisTasks = page.getRecords();
        if (CollectionUtils.isEmpty(dimensionAnalysisTasks)) {
            pageVO.setData(Collections.EMPTY_LIST);
        }
        List<DimensionAnalysisVO> dimensionAnalysisVOS = dimensionAnalysisTasks.stream().map(task -> convert(task)).collect(Collectors.toList());
        pageVO.setData(dimensionAnalysisVOS);
        return pageVO;
    }

    private DimensionAnalysisVO convert(DimensionAnalysisTask task) {
        DimensionAnalysisVO vo = new DimensionAnalysisVO();
        BeanUtils.copyProperties(task, vo);
        vo.setBaseDate(task.getBasePeriod());
        vo.setCurrentDate(task.getCurrentPeriod());
        if (Objects.equals(DimensionAnalysisTaskStatusType.PROCESSION.getCode(), task.getStatus())) {
            vo.setProgress(getProgress(task.getId()));
        } else if (Objects.equals(DimensionAnalysisTaskStatusType.INITIAL.getCode(), task.getStatus())) {
            vo.setProgress(0);
        } else {
            vo.setProgress(100);
        }

        return vo;
    }

    public Integer getProgress(Long taskId) {
        Integer procress = redisCacheService.get(IndicatorConstant.DIMENSION_ANALYSIS_TASK_QUEUE_PROGESS_PREFIX + taskId, Integer.class);
        procress = procress == null ? 0 : procress;
        return procress;
    }

    public void setProgress(Long taskId, Integer progress) {
        redisCacheService.put(IndicatorConstant.DIMENSION_ANALYSIS_TASK_QUEUE_PROGESS_PREFIX + taskId, progress, 7, TimeUnit.DAYS);
    }

    public void removeProgress(Long taskId) {
        redisCacheService.delete(IndicatorConstant.DIMENSION_ANALYSIS_TASK_QUEUE_PROGESS_PREFIX + taskId);
    }

    /**
     * k:各个维度维值字符串用-连接
     * 用于合并基期和本期的数据
     */
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

    public PageDataVO multiDimensionalChartQuery(MultiDimensionQueryVO multiDimensionQueryVO) {
        // 当期数据查询参数准备
        DataSource currentDataSource = convert2DataSource(multiDimensionQueryVO);
        List<Filter> currentFilterList = new LinkedList<>();
        Filter currentFilter = buildFilter(multiDimensionQueryVO.getFilterDimCode(), Arrays.asList(multiDimensionQueryVO.getCurrentDate()));
        currentFilterList.add(currentFilter);
        currentFilterList.addAll(multiDimensionQueryVO.getFilterList());
        currentDataSource.setFilterList(currentFilterList);
        // 查询当期数据
        // log.info(JSON.toJSONString(currentDataSource));
        PageData currentPage = chartQueryService.execQuery(currentDataSource);
        Map<String, List<String>> baseColDimFilterMap = new HashMap<>();
        PageDataVO result = new PageDataVO();
        BeanUtils.copyProperties(currentPage, result);
        // 获取总条数
        // String queryCountId = currentPage.getPageInfo().getQueryCountId();
        // currentDataSource.setQueryCountId(queryCountId);
        // PageData countQuery = chartQueryService.execCountQuery(currentDataSource);
        // PageInfo pageInfo = countQuery.getPageInfo();
        List<List<Cell>> cellList = result.getCellList();
        for (List<Cell> cells : cellList) {
            // 找到所有的维度类型cell
            List<Cell> dimCells = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.DIMENSION)).collect(Collectors.toList());
            dimCells.forEach(cell -> {
                // 准备基期的过滤条件
                if (!baseColDimFilterMap.containsKey(cell.getCode())) {
                    baseColDimFilterMap.put(cell.getCode(), new ArrayList<>());
                }
                baseColDimFilterMap.get(cell.getCode()).add(cell.getId());
            });
        }
        Map<String, List<Cell>> currentCellsMap = buildMeasureValueMap(result.getCellList());

        // 基期数据查询参数准备
        DataSource baseDataSource = convert2DataSource(multiDimensionQueryVO);
        List<Filter> baseFilterList = new LinkedList<>();
        Filter baseDateFilter = buildFilter(multiDimensionQueryVO.getFilterDimCode(), Arrays.asList(multiDimensionQueryVO.getBaseDate()));
        baseFilterList.add(baseDateFilter);
        baseColDimFilterMap.keySet().forEach(code -> {
            Filter filter = buildFilter(code, baseColDimFilterMap.get(code));
            baseFilterList.add(filter);
        });
        baseFilterList.addAll(multiDimensionQueryVO.getFilterList());
        baseDataSource.setFilterList(baseFilterList);
        // 查询本期数据
        PageData basePageData = chartQueryService.execQuery(baseDataSource);
        Map<String, List<Cell>> baseCellsMap = buildMeasureValueMap(basePageData.getCellList());
        // 整合数据
        Map<String, List<Cell>> resultCellsMap = merge(currentCellsMap, baseCellsMap);
        result.setTotal(Long.valueOf(resultCellsMap.size()));
        // 计算贡献度、幅度 考虑到指标权限和行列权限，只能现查
        String currentValue = null;
        String baseValue = null;
        currentValue = chartQueryService.execOnlySingleMeasure(multiDimensionQueryVO.getMeasCode(), multiDimensionQueryVO.getFilterDimCode(), multiDimensionQueryVO.getCurrentDate(), new HashSet<>(), UserThreadLocalUtil.getUserName(), multiDimensionQueryVO.getSpaceId());
        baseValue = chartQueryService.execOnlySingleMeasure(multiDimensionQueryVO.getMeasCode(), multiDimensionQueryVO.getFilterDimCode(), multiDimensionQueryVO.getBaseDate(), new HashSet<>(), UserThreadLocalUtil.getUserName(), multiDimensionQueryVO.getSpaceId());
        DimensionAnalysisTask task = taskService.getById(multiDimensionQueryVO.getTaskId());
        // if (Objects.nonNull(task) && Objects.nonNull(currentValue) && Objects.nonNull(baseValue)) {
        //     currentValue = task.getCurrentValue();
        //     baseValue = task.getBaseValue();
        // } else {
        //     currentValue = chartQueryService.execOnlySingleMeasure(
        //             multiDimensionQueryVO.getMeasCode(),
        //             multiDimensionQueryVO.getFilterDimCode(),
        //             multiDimensionQueryVO.getCurrentDate(),
        //             new HashSet<>(),
        //             UserThreadLocalUtil.getUserName(),
        //             multiDimensionQueryVO.getSpaceId());
        //     baseValue = chartQueryService.execOnlySingleMeasure(
        //             multiDimensionQueryVO.getMeasCode(),
        //             multiDimensionQueryVO.getFilterDimCode(),
        //             multiDimensionQueryVO.getBaseDate(),
        //             new HashSet<>(),
        //             UserThreadLocalUtil.getUserName(),
        //             multiDimensionQueryVO.getSpaceId());
        // }

        calculateContribution(task, resultCellsMap, NumberFormatUtil.format(baseValue), NumberFormatUtil.format(currentValue), multiDimensionQueryVO);
        // 分页&排序
        List<List<Cell>> sortAndPage = sortAndPage(resultCellsMap, multiDimensionQueryVO);
        // 返回给前端pageData
        result.setCellList(sortAndPage);
        return result;
    }

    private List<List<Cell>> sortAndPage(Map<String, List<Cell>> resultMap, MultiDimensionQueryVO queryVO) {

        LinkedList<OrderVO> orderList = queryVO.getOrderList();
        if (!CollectionUtils.isEmpty(orderList)) {
            List<List<Cell>> result = resultMap.values().stream().sorted(Comparator.comparing(cells -> cells, (x_cells, y_cells) -> sort(x_cells, y_cells, orderList, 0))).skip(queryVO.getPageSize() * (queryVO.getPageNo() - 1)).limit(queryVO.getPageSize()).collect(Collectors.toList());
            return result;
        } else {
            List<List<Cell>> result = resultMap.values().stream().skip(queryVO.getPageSize() * (queryVO.getPageNo() - 1)).limit(queryVO.getPageSize()).collect(Collectors.toList());
            return result;
        }
    }

    private int sort(List<Cell> x_cells, List<Cell> y_cells, LinkedList<OrderVO> orderList, int orderIndex) {
        OrderVO orderVO = orderList.get(orderIndex);
        String code = orderVO.getCode();
        Cell x_cell = x_cells.stream().filter(cell -> Objects.equals(code, cell.getCode())).findFirst().orElse(null);
        Cell y_cell = y_cells.stream().filter(cell -> Objects.equals(code, cell.getCode())).findFirst().orElse(null);
        if (Objects.isNull(x_cell) || Objects.isNull(y_cell)) {
            return 0;
        }
        String x_data = x_cell.getData();
        String y_data = y_cell.getData();

        // x、y不是数字类型，则按照ASCII码进行排序
        if (!NumberFormatUtil.isNumbericWithComma(x_data) || !NumberFormatUtil.isNumbericWithComma(y_data)) {
            if (Objects.equals(orderVO.getSortType(), SortType.ASC.getCode())) {
                // 正序返回 -1
                return x_data.compareToIgnoreCase(y_data);
            } else {
                // 倒序和默认返回 1
                return -x_data.compareToIgnoreCase(y_data);
            }
        }
        // if (! NumberFormatUtil.isNumbericWithComma(y_data)){
        //     if (Objects.equals(orderVO.getSortType(), SortType.ASC.getCode())){
        //         // 正序返回 -1
        //         return - x_data.compareToIgnoreCase(y_data);
        //     } else {
        //         // 倒序和默认返回 1
        //         return x_data.compareToIgnoreCase(y_data);
        //     }
        // }
        Double x = NumberFormatUtil.format(x_cell.getData()).doubleValue();
        Double y = NumberFormatUtil.format(y_cell.getData()).doubleValue();

        if (x.compareTo(y) == 1) {
            // x大于y
            if (Objects.equals(orderVO.getSortType(), SortType.ASC.getCode())) {
                // 正序返回 1
                return 1;
            } else {
                // 倒序和默认返回 -1
                return -1;
            }
        } else if (x.compareTo(y) == 0) {
            // x等于y，进行下一个维度排序
            if (orderIndex < orderList.size() - 1) {
                return sort(x_cells, y_cells, orderList, ++orderIndex);
            }
            return 0;
        } else {
            // x小于y
            if (Objects.equals(orderVO.getSortType(), SortType.ASC.getCode())) {
                // 正序返回 -1
                return -1;
            } else {
                // 倒序和默认返回 1
                return 1;
            }
        }
    }

    private Map<String, RatioMeasureCalculationParam> queryMeasureDismanling(DimensionAnalysisTask task, RatioMeasureDismanling dismanling, MultiDimensionQueryVO multiDimensionQueryVO) {
        String molecularCode = dismanling.getMolecularCode();
        String denominatorCode = dismanling.getDenominatorCode();
        PageData molecularCurrentPageData = chartQueryService.execMetaSingleMeasure(molecularCode, task.getDimCode(), task.getCurrentPeriod(), multiDimensionQueryVO.getColDimCodes(), task.getCreator(), task.getSpaceId());
        PageData denominatorCurrentPageData = chartQueryService.execMetaSingleMeasure(denominatorCode, task.getDimCode(), task.getCurrentPeriod(), multiDimensionQueryVO.getColDimCodes(), task.getCreator(), task.getSpaceId());
        PageData molecularBasePageData = chartQueryService.execMetaSingleMeasure(molecularCode, task.getDimCode(), task.getBasePeriod(), multiDimensionQueryVO.getColDimCodes(), task.getCreator(), task.getSpaceId());
        PageData denominatorBasePageData = chartQueryService.execMetaSingleMeasure(denominatorCode, task.getDimCode(), task.getBasePeriod(), multiDimensionQueryVO.getColDimCodes(), task.getCreator(), task.getSpaceId());

        Map<String, List<Cell>> molecularCurrentMap = buildMeasureValueMap(molecularCurrentPageData.getCellList());
        Map<String, List<Cell>> denominatorCurrentMap = buildMeasureValueMap(denominatorCurrentPageData.getCellList());
        Map<String, List<Cell>> mmolecularBaseMap = buildMeasureValueMap(molecularBasePageData.getCellList());
        Map<String, List<Cell>> denominatorBaseMap = buildMeasureValueMap(denominatorBasePageData.getCellList());

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(molecularCurrentMap.keySet());
        allKeys.addAll(denominatorCurrentMap.keySet());
        allKeys.addAll(mmolecularBaseMap.keySet());
        allKeys.addAll(denominatorBaseMap.keySet());

        BigDecimal b_current_total = BigDecimal.ZERO;
        BigDecimal b_base_total = BigDecimal.ZERO;
        BigDecimal a_current_total = BigDecimal.ZERO;
        BigDecimal a_base_total = BigDecimal.ZERO;

        Map<String, RatioMeasureCalculationParam> ratioParamMap = new HashMap<>();
        for (String key : allKeys) {
            a_base_total = a_base_total.add(getValue(key, mmolecularBaseMap));
            a_current_total = a_current_total.add(getValue(key, molecularCurrentMap));
            b_base_total = b_base_total.add(getValue(key, denominatorBaseMap));
            b_current_total = b_current_total.add(getValue(key, denominatorCurrentMap));
        }

        for (String key : allKeys) {
            RatioMeasureCalculationParam param = new RatioMeasureCalculationParam();
            param.setA_baseValue(getValue(key, mmolecularBaseMap));
            param.setB_baseValue(getValue(key, denominatorBaseMap));
            param.setA_currentValue(getValue(key, molecularCurrentMap));
            param.setB_currentValue(getValue(key, denominatorCurrentMap));

            param.setA_base_total(a_base_total);
            param.setA_current_total(a_current_total);
            param.setB_base_total(b_base_total);
            param.setB_current_total(b_current_total);
            ratioParamMap.put(key, param);
        }

        return ratioParamMap;
    }

    private BigDecimal getValue(String key, Map<String, List<Cell>> cellsMap) {
        List<Cell> cells = cellsMap.get(key);
        if (CollectionUtils.isEmpty(cells)) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.MEASURE)).findFirst().map(cell -> NumberFormatUtil.format(cell.getData())).orElse(BigDecimal.ZERO);

        return value;
    }

    private void calculateContribution(DimensionAnalysisTask task, Map<String, List<Cell>> resultCellsMap, BigDecimal upperLayerPreviousPeriodValue, BigDecimal upperLayerCurrentPeriodValue, MultiDimensionQueryVO multiDimensionQueryVO) {
        Map<String, RatioMeasureCalculationParam> ratioMeasureCalculationParamMap = new HashMap<>();
        Integer contributionCalType = task.getContributionCalType();
        if (Objects.isNull(contributionCalType)) {
            // 历史数据默认采用加法
            contributionCalType = ContributionCalculationType.ADDITION.getCode();
        }
        ContributionCalculationType calculationType = ContributionCalculationType.getByCode(contributionCalType);
        if (Objects.equals(ContributionCalculationType.TWO_FACTOR, calculationType)) {
            // 双因素拆解 获取分子、分母指标
            RatioMeasureDismanling dismanling = measureManager.getRatioMeasureDismanling(task.getMeasCode());
            if (!dismanling.isComputable()) {
                // 比率型指标不符合计算规则
                calculationType = ContributionCalculationType.DEFAULT;
            } else {
                ratioMeasureCalculationParamMap = queryMeasureDismanling(task, dismanling, multiDimensionQueryVO);
            }
        }
        String baseValue = task.getBaseValue();
        BigDecimal totalBaseValue = NumberFormatUtil.format(baseValue);

        for (Map.Entry<String, List<Cell>> entry : resultCellsMap.entrySet()) {
            List<Cell> cells = entry.getValue();
            String key = entry.getKey();
            Cell currentCell = cells.get(cells.size() - 2);
            Cell baseCell = cells.get(cells.size() - 1);
            ContributionCalculationParam.ContributionCalculationParamBuilder builder = ContributionCalculationParam.builder();
            builder.contributionCalculationType(calculationType);
            builder.currentPeriodValue(NumberFormatUtil.format(currentCell.getData()));
            builder.previousPeriodValue(NumberFormatUtil.format(baseCell.getData()));
            builder.upperLayerPreviousPeriodValue(upperLayerPreviousPeriodValue);
            builder.upperLayerCurrentPeriodValue(upperLayerCurrentPeriodValue);

            ContributionOriginQueryParam param = new ContributionOriginQueryParam();
            param.setMeasCode(currentCell.getCode());
            builder.originQueryParam(param);
            ContributionCalculationParam calculationParam = builder.build();
            ContributionStrategy strategy = ContributionStrategyHolder.getStrategy(calculationType);
            if (Objects.equals(calculationType, ContributionCalculationType.TWO_FACTOR)) {
                RatioMeasureCalculationParam ratioParam = ratioMeasureCalculationParamMap.get(key);
                ratioParam.setY0(totalBaseValue);
                calculationParam.setRatioParam(ratioParam);
            }
            ContributionCalculationResult calculate = strategy.calculate(calculationParam);
            String deltaValueRate = calculate.getDeltaValueRate() == null ? "-" : calculate.getDeltaValueRate().setScale(2, BigDecimal.ROUND_DOWN).toString();
            String contributionValue = calculate.getContributionValue() == null ? "-" : calculate.getContributionValue().setScale(6, BigDecimal.ROUND_DOWN).toString();
            String contributionValueRate = calculate.getContributionValueRate() == null ? "-" : calculate.getContributionValueRate().setScale(6, BigDecimal.ROUND_DOWN).toString();
            String contributionAbsValueRate = calculate.getContributionValueRate() == null ? "-" : calculate.getContributionValueRate().abs().setScale(6, BigDecimal.ROUND_DOWN).toString();
            cells.add(getCell("变化幅度", deltaValueRate, IndicatorConstant.DELTA_VALUE_RATE_CODE));
            cells.add(getCell("贡献", contributionValue, IndicatorConstant.CONTRIBUION_CODE));
            cells.add(getCell("贡献占比", contributionValueRate, IndicatorConstant.CONTRIBUION_RATE_CODE));
            cells.add(getCell("贡献占比绝对值", contributionAbsValueRate, IndicatorConstant.CONTRIBUION_ABS_RATE_CODE));
        }
    }

    private Cell getCell(String name, String data, String code) {
        Cell cell = new Cell();
        cell.setName(name);
        cell.setData(data);
        cell.setCode(code);
        return cell;
    }

    private Map<String, List<Cell>> merge(Map<String, List<Cell>> currentCellsMap, Map<String, List<Cell>> baseCellsMap) {

        Set<String> currentKyes = currentCellsMap.keySet();
        Set<String> currentDiffKeys = new HashSet();
        currentDiffKeys.addAll(baseCellsMap.keySet());
        currentDiffKeys.removeAll(currentKyes);
        Map<String, List<Cell>> allCurrentMap = new HashMap<>();
        allCurrentMap.putAll(currentCellsMap);
        /**
         * currentDiffKeys 是基期有值，但是本期没值的key，为了展示全部数据，需要给本期数据补0
         * 这一步本质上就是实现full join的操作
         */
        currentDiffKeys.forEach(key -> {
            List<Cell> cells = baseCellsMap.get(key);
            List<Cell> currentCells = new LinkedList<>();
            for (Cell cell : cells) {
                Cell c = new Cell();
                BeanUtils.copyProperties(cell, c);
                currentCells.add(c);
            }
            currentCells.forEach(cell -> {
                if (Objects.equals(cell.getType(), CellType.MEASURE)) {
                    cell.setData("0");
                }
            });
            allCurrentMap.put(key, currentCells);
        });
        // 把本期值cell合并到当期值
        allCurrentMap.keySet().forEach(key -> {
            List<Cell> currentCells = allCurrentMap.get(key);
            Cell currentMeasCell = currentCells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.MEASURE)).findFirst().orElse(defaultCell(null));
            currentMeasCell.setName("指标本期值");
            currentMeasCell.setCode(IndicatorConstant.CURRENT_VALUE_CODE);
            List<Cell> baseCells = baseCellsMap.get(key);
            Cell measureCell = CollectionUtils.isEmpty(baseCells) ? defaultCell(null) : baseCells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.MEASURE)).findFirst().orElse(defaultCell(null));
            measureCell.setName("指标基期值");
            measureCell.setCode(IndicatorConstant.BASE_VALUE_CODE);
            currentCells.add(measureCell);
        });
        return allCurrentMap;
    }

    private Cell defaultCell(String code) {
        Cell cell = new Cell();
        cell.setCode(code);
        cell.setData("0");
        return cell;
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

    private DataSource convert2DataSource(MultiDimensionQueryVO multiDimensionQueryVO) {
        DataSource dataSource = new DataSource();
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(multiDimensionQueryVO.getSpaceId());
        dataSource.setChartType(ChartType.LINE);
        dataSource.setUsername(UserThreadLocalUtil.getUserName());
        List<BaseConfigure> configureList = new LinkedList<>();
        BaseConfigure measureConfigure = new BaseConfigure();
        measureConfigure.setCode(multiDimensionQueryVO.getMeasCode());
        configureList.add(measureConfigure);
        multiDimensionQueryVO.getColDimCodes().forEach(code -> {
            // Order order = new Order();
            // order.setSortType(SortType.DESC);
            // order.setSortScope(SortScope.GROUP);
            // order.setCode(code);
            BaseConfigure dimensionConfigure = new BaseConfigure();
            dimensionConfigure.setCode(code);
            // dimensionConfigure.setOrder(order);
            configureList.add(dimensionConfigure);
        });

        if (!multiDimensionQueryVO.isDownloadFile()) {
            dataSource.setPageable(true);
            dataSource.setPageNo(multiDimensionQueryVO.getPageNo());
            dataSource.setPageSize(multiDimensionQueryVO.getPageSize());
        }
        dataSource.setConfigureList(configureList);
        return dataSource;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createTask(String measCode, String dimCode, String currentDate, String baseDate, Long spaceId) {
        DimensionAnalysisTask dimensionAnalysisTask = new DimensionAnalysisTask();
        dimensionAnalysisTask.initCreate();
        dimensionAnalysisTask.setCurrentPeriod(currentDate);
        dimensionAnalysisTask.setBasePeriod(baseDate);
        dimensionAnalysisTask.setDimCode(dimCode);
        dimensionAnalysisTask.setMeasCode(measCode);
        dimensionAnalysisTask.setSpaceId(spaceId);
        dimensionAnalysisTask.setStatus(DimensionAnalysisTaskStatusType.INITIAL.getCode());
        Measure measure = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, measCode));
        if (Objects.isNull(measure)) {
            throw IndicatorParamNotValidException.error("指标不存在");
        }
        Dimension dimension = dimensionService.getOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode, dimCode));
        if (Objects.isNull(dimension)) {
            throw IndicatorParamNotValidException.error("维度不存在");
        }
        dimensionAnalysisTask.setReportName(generateTaskName(measure.getCnName(), dimension.getCnName(), currentDate, baseDate));
        taskService.save(dimensionAnalysisTask);

        // 保存到Redis队列
        redisCacheService.lpush(redisKeyPrefix + IndicatorConstant.DIMENSION_ANALYSIS_TASK_QUEUE, dimensionAnalysisTask.getId().toString());
    }

    @Autowired
    private DimensionManager dimensionManager;

    private List<DimensionAnalysisTaskDetail> createDetail(List<Dimension> dimensionList, Long taskId) {
        return dimensionList.stream().map(dimension -> {
            DimensionAnalysisTaskDetail detail = new DimensionAnalysisTaskDetail();
            detail.setDimCode(dimension.getCode());
            detail.setStatus(DimensionAnalysisTaskDetailStatusType.INITIAL.getCode());
            detail.setTaskId(taskId);
            return detail;
        }).collect(Collectors.toList());
    }

    private String generateTaskName(String measName, String dimName, String currentDate, String baseDate) {
        Joiner on = Joiner.on("_");
        String join = on.join(measName, dimName, currentDate, baseDate);
        return join;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<GiniCalculateParam> executeTask(DimensionAnalysisTask dimensionAnalysisTask) {
        // 创建子任务
        // 获取与当前指标有交叉的维度列表
        setProgress(dimensionAnalysisTask.getId(), 15);
        Set<Dimension> dimensions = bloodManager.listRelatedDimensions(dimensionAnalysisTask.getMeasCode(), dimensionAnalysisTask.getDimCode(), dimensionAnalysisTask.getSpaceId());
        // 过滤掉日期及退化维
        List<Dimension> dimensionList = dimensions.stream().filter(d ->
        // 只要字符串类型且非退化维的维度
        Objects.equals(d.getViewType(), ViewType.CHARACTER.getValue())).filter(d -> dimensionManager.getDimensionValueCount(d.getCode()) != null && dimensionManager.getDimensionValueCount(d.getCode()) <= 50).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(dimensionList)) {
            throw IndicatorParamNotValidException.error("没有可分析的维度");
        }
        // 创建子任务
        List<DimensionAnalysisTaskDetail> detailList = createDetail(dimensionList, dimensionAnalysisTask.getId());
        setProgress(dimensionAnalysisTask.getId(), 25);

        detailService.remove(Wrappers.<DimensionAnalysisTaskDetail>lambdaQuery().eq(DimensionAnalysisTaskDetail::getTaskId, dimensionAnalysisTask.getId()));
        detailService.saveBatch(detailList);

        List<GiniCalculateParam> result = new ArrayList<>();
        Long taskId = dimensionAnalysisTask.getId();
        // 查询基期值,本期值
        String currentValue = chartQueryService.execOnlySingleMeasure(dimensionAnalysisTask.getMeasCode(), dimensionAnalysisTask.getDimCode(), dimensionAnalysisTask.getCurrentPeriod(), new HashSet<>(), dimensionAnalysisTask.getCreator(), dimensionAnalysisTask.getSpaceId());

        String baseValue = chartQueryService.execOnlySingleMeasure(dimensionAnalysisTask.getMeasCode(), dimensionAnalysisTask.getDimCode(), dimensionAnalysisTask.getBasePeriod(), new HashSet<>(), dimensionAnalysisTask.getCreator(), dimensionAnalysisTask.getSpaceId());
        dimensionAnalysisTask.setCurrentValue(currentValue);
        dimensionAnalysisTask.setBaseValue(baseValue);
        taskService.updateById(dimensionAnalysisTask);
        // 打点
        setProgress(dimensionAnalysisTask.getId(), 35);
        AtomicBoolean taskSuccess = new AtomicBoolean(true);
        if (!CollectionUtils.isEmpty(detailList)) {
            Integer detailProgress = 60;
            Integer setupSize = detailProgress / detailList.size();
            AtomicInteger initProgress = new AtomicInteger(35);
            for (DimensionAnalysisTaskDetail detail : detailList) {
                GiniCalculateParam param = new GiniCalculateParam();
                try {
                    Dimension dimension = dimensionService.getOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode, detail.getDimCode()));
                    DimensionAnalysisTaskDetailDorisQueryResult dorisQueryResult = queryFromDoris(detail, dimensionAnalysisTask);
                    param = buildParam(dorisQueryResult.getCurrentPageData(), dorisQueryResult.getPreviousPageData(), dimension);
                    if (Objects.isNull(dimensionAnalysisTask.getContributionCalType())) {
                        ContributionCalculationType contributionCalculationType = getContributionCalculationType(dimensionAnalysisTask, dorisQueryResult);
                        dimensionAnalysisTask.setContributionCalType(contributionCalculationType.getCode());
                    }
                    double gini = GiniCalculator.calculateGini(param);
                    detail.setGiniValue(BigDecimal.valueOf(gini));
                    detail.setStatus(DimensionAnalysisTaskDetailStatusType.COMPLETED.getCode());
                } catch (Exception e) {
                    log.error("报告生成异常:", e);
                    detail.setErrorMessage(e.getMessage());
                    detail.setStatus(DimensionAnalysisTaskDetailStatusType.FAILED.getCode());
                    // 有一个失败即为部分成功
                    taskSuccess.set(false);
                }
                detailService.updateById(detail);
                result.add(param);
                // 打点
                initProgress.addAndGet(setupSize);
                setProgress(dimensionAnalysisTask.getId(), initProgress.get());
            }
            if (taskSuccess.get()) {
                dimensionAnalysisTask.setStatus(DimensionAnalysisTaskStatusType.COMPLETED.getCode());
            } else {
                dimensionAnalysisTask.setStatus(DimensionAnalysisTaskStatusType.PART_COMPLETED.getCode());
            }
        }
        // 更新任务状态
        taskService.updateById(dimensionAnalysisTask);
        removeProgress(dimensionAnalysisTask.getId());
        return result;
    }

    @Autowired
    private ChartQueryService chartQueryService;

    public DimensionAnalysisTaskDetailDorisQueryResult queryFromDoris(DimensionAnalysisTaskDetail detail, DimensionAnalysisTask task) {
        String colDimCode = detail.getDimCode();
        Set<String> dimSet = new HashSet<>();
        dimSet.add(colDimCode);
        PageData currentPageData = chartQueryService.execMetaSingleMeasure(task.getMeasCode(), task.getDimCode(), task.getCurrentPeriod(), dimSet, task.getCreator(), task.getSpaceId());
        PageData previousPageData = chartQueryService.execMetaSingleMeasure(task.getMeasCode(), task.getDimCode(), task.getBasePeriod(), dimSet, task.getCreator(), task.getSpaceId());
        DimensionAnalysisTaskDetailDorisQueryResult result = new DimensionAnalysisTaskDetailDorisQueryResult();
        result.setCurrentPageData(currentPageData);
        result.setPreviousPageData(previousPageData);
        return result;
    }

    public GiniCalculateParam buildParam(PageData currentPageData, PageData basePageData, Dimension dimension) {
        GiniCalculateParam param = new GiniCalculateParam();
        param.setDimCode(dimension.getCode());
        param.setDimName(dimension.getCnName());
        Map<String, GiniSubOption> subOptionMap = new HashMap<>();
        for (List<Cell> cells : currentPageData.getCellList()) {
            if (CollectionUtils.isEmpty(cells) || !Objects.equals(cells.size(), 2)) {
                throw IndicatorParamNotValidException.error("维度：" + dimension.getCnName() + "分组查询异常，cellReslut: " + cells);
            }
            Cell measCell = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.MEASURE)).findFirst().orElseThrow(() -> IndicatorParamNotValidException.error("cells不存在指标类型"));
            Cell dimCell = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.DIMENSION)).findFirst().orElseThrow(() -> IndicatorParamNotValidException.error("cells不存在维度类型"));

            String dimKey = dimCell.getId();
            if (!subOptionMap.containsKey(dimKey)) {
                subOptionMap.put(dimKey, new GiniSubOption());
            }
            GiniSubOption giniSubOption = subOptionMap.get(dimKey);
            giniSubOption.setCurrentValue(NumberFormatUtil.formatExceptionWithZero(measCell.getData()).doubleValue());
            giniSubOption.setDimValueId(dimKey);
            giniSubOption.setDimValueName(dimCell.getData());
        }

        for (List<Cell> cells : basePageData.getCellList()) {
            if (CollectionUtils.isEmpty(cells) || !Objects.equals(cells.size(), 2)) {
                throw IndicatorParamNotValidException.error("维度：" + dimension.getCnName() + "分组查询异常，cellReslut: " + cells);
            }
            Cell measCell = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.MEASURE)).findFirst().orElseThrow(() -> IndicatorParamNotValidException.error("cells不存在指标类型"));
            Cell dimCell = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.DIMENSION)).findFirst().orElseThrow(() -> IndicatorParamNotValidException.error("cells不存在维度类型"));

            String dimKey = dimCell.getId();
            if (!subOptionMap.containsKey(dimKey)) {
                subOptionMap.put(dimKey, new GiniSubOption());
            }
            GiniSubOption giniSubOption = subOptionMap.get(dimKey);
            giniSubOption.setBaseValue(NumberFormatUtil.formatExceptionWithZero(measCell.getData()).doubleValue());
            giniSubOption.setDimValueId(dimKey);
            giniSubOption.setDimValueName(dimCell.getData());
        }
        List<GiniSubOption> giniSubOptionList = new ArrayList<>(subOptionMap.values());
        param.setGiniSubOptionList(giniSubOptionList);
        return param;
    }

    @Autowired
    MeasureManager measureManager;

    public ContributionCalculationType getContributionCalculationType(DimensionAnalysisTask dimensionAnalysisTask, DimensionAnalysisTaskDetailDorisQueryResult dorisQueryResult) {
        PageData previousPageData = dorisQueryResult.getPreviousPageData();
        if (Objects.isNull(previousPageData)) {
            return ContributionCalculationType.DEFAULT;
        }
        if (CollectionUtils.isEmpty(previousPageData.getCellList())) {
            return ContributionCalculationType.DEFAULT;
        }
        if (Objects.isNull(dimensionAnalysisTask.getBaseValue())) {
            return ContributionCalculationType.DEFAULT;
        }
        boolean ratio = measureManager.isRatio(dimensionAnalysisTask.getMeasCode());
        if (ratio) {
            return ContributionCalculationType.TWO_FACTOR;
        }
        if (!NumberFormatUtil.isNumbericWithComma(dimensionAnalysisTask.getBaseValue())) {
            return ContributionCalculationType.DEFAULT;
        }
        double total = 0.0D;
        for (List<Cell> cells : previousPageData.getCellList()) {
            Cell measureCell = cells.stream().filter(cell -> Objects.equals(cell.getType(), CellType.MEASURE)).findFirst().orElse(defaultCell(null));
            if (NumberFormatUtil.isNumbericWithComma(measureCell.getData())) {
                total += NumberFormatUtil.format(measureCell.getData()).doubleValue();
            }
        }
        double baseValue = NumberFormatUtil.format(dimensionAnalysisTask.getBaseValue()).doubleValue();
        if (baseValue == total) {
            return ContributionCalculationType.ADDITION;
        }
        return ContributionCalculationType.DEFAULT;
    }
}
