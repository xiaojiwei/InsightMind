package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.mapper.MeasureMonitorDimGroupDetailMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMonitorDimGroupMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMonitorRuleFilterMapper;
import com.graphinsight.indicator.auto.service.IMeasureMonitorReceiverService;
import com.graphinsight.indicator.auto.service.IMeasureMonitorRuleDetailService;
import com.graphinsight.indicator.auto.service.IMeasureMonitorRuleService;
import com.graphinsight.indicator.auto.service.IMeasureMonitorService;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.service.*;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.dao.FilterDao;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.MeasureMonitorDimGroupQueryResult;
import com.graphinsight.indicator.model.dto.MeasureMonitorResult;
import com.graphinsight.indicator.model.dto.MeasureMonitorRuleQueryResult;
import com.graphinsight.indicator.model.feishu.ChatGroup;
import com.graphinsight.indicator.model.feishu.FeishuCardMessage;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.schedule.ScheduleManager;
import com.graphinsight.indicator.util.CronUtil;
import com.graphinsight.indicator.util.IndicatorAssert;
import com.graphinsight.indicator.util.NumberFormatUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.quartz.SchedulerException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/10/11
 * Desc:
 */
@Slf4j
@Component
public class MeasureMonitorManager {

    @Resource
    IMeasureMonitorService measureMonitorService;
    @Resource
    IMeasureMonitorRuleService measureMonitorRuleService;
    @Resource
    IMeasureMonitorRuleDetailService measureMonitorRuleDetailService;
    @Resource
    DorisQueryManager dorisQueryManager;
    @Resource
    CacheManager cacheManager;
    @Resource
    ScheduleManager scheduleManager;

    @Autowired
    MeasureMonitorDimGroupMapper dimGroupMapper;

    @Autowired
    MeasureMonitorRuleFilterMapper ruleFilterMapper;

    @Autowired
    MeasureMonitorDimGroupDetailMapper dimGroupDetailMapper;

    @Resource
    IMeasureMonitorConfigDescService measureMonitorConfigDescService;

    @Value("${url}")
    private String url;

    public void registJob() {
        List<MeasureMonitor> measureMonitors = measureMonitorService.list();
        measureMonitors.forEach(this::createMeasureMonitorJob);
    }

    public List<MeasureMonitorResult> executeMonitor(Long monitorId) {
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        List<MeasureMonitorResult> result = new ArrayList<>();
//        MeasureMonitorResult result = new MeasureMonitorResult();
        MeasureMonitor measureMonitor = measureMonitorService.getById(monitorId);
        if (measureMonitor == null) {
            return result;
        }
        List<MeasureMonitorRule> rules = measureMonitorRuleService.list(Wrappers.<MeasureMonitorRule>lambdaQuery().eq(MeasureMonitorRule::getMonitorId, measureMonitor.getId()));
        if (CollectionUtils.isEmpty(rules)) {
            return result;
        }

        List<MeasureMonitorRuleQueryResult> results = rules.stream()
                .map(r -> executeSingleRule(r.getId(), measureMonitor.getSpaceId()))
                .collect(Collectors.toList());
        return convertResult(results, measureMonitor);
    }


    private List<MeasureMonitorResult> convertResult(List<MeasureMonitorRuleQueryResult> measureMonitorRuleQueryResults, MeasureMonitor measureMonitor) {
        // TODO 多个结果需要根据逻辑关系拼装成一个最终结果，目前只取第一个
//        MeasureMonitorRuleQueryResult measureMonitorRuleQueryResult = results.get(0);
        List<MeasureMonitorResult> measureMonitorResultList = new ArrayList<>();
        int level = 11;
        try {
            if (measureMonitor.getLogicType() == 1) {
                for (MeasureMonitorRuleQueryResult measureMonitorRuleQueryResult : measureMonitorRuleQueryResults) {
                    if (measureMonitorRuleQueryResult.getRule().getLevel() < level && measureMonitorRuleQueryResult.getTrigger()) {
                        level = measureMonitorRuleQueryResult.getRule().getLevel();
                    }
                }
                if (level < 11) {
                    for (MeasureMonitorRuleQueryResult measureMonitorRuleQueryResult : measureMonitorRuleQueryResults) {
                        if (measureMonitorRuleQueryResult.getRule() != null && measureMonitorRuleQueryResult.getRule().getLevel() == level && measureMonitorRuleQueryResult.getTrigger()) {
                            MeasureMonitorResult result = new MeasureMonitorResult();
                            BeanUtils.copyProperties(measureMonitorRuleQueryResult, result);
                            if (measureMonitorRuleQueryResult.getRule() != null) {
                                result.setRuleId(measureMonitorRuleQueryResult.getRule().getId());
                            }
                            measureMonitorResultList.add(result);
                        }
                    }
                }
            } else {
                for (MeasureMonitorRuleQueryResult measureMonitorRuleQueryResult : measureMonitorRuleQueryResults) {
                    if (!measureMonitorRuleQueryResult.getTrigger()) {
                        level = 11;
                        break;
                    }
                    if (measureMonitorRuleQueryResult.getRule().getLevel() < level) {
                        level = measureMonitorRuleQueryResult.getRule().getLevel();
                    }
                }
                if (level < 11) {
                    for (MeasureMonitorRuleQueryResult measureMonitorRuleQueryResult : measureMonitorRuleQueryResults) {
                        if (measureMonitorRuleQueryResult.getRule() != null && measureMonitorRuleQueryResult.getRule().getLevel() == level && measureMonitorRuleQueryResult.getTrigger()) {
                            MeasureMonitorResult result = new MeasureMonitorResult();
                            BeanUtils.copyProperties(measureMonitorRuleQueryResult, result);
                            if (measureMonitorRuleQueryResult.getRule() != null) {
                                result.setRuleId(measureMonitorRuleQueryResult.getRule().getId());
                            }
                            measureMonitorResultList.add(result);
                        }
                    }
                }
            }
        } catch (Exception e) {
            MeasureMonitorRuleQueryResult measureMonitorRuleQueryResult = measureMonitorRuleQueryResults.get(0);
            MeasureMonitorResult result = new MeasureMonitorResult();
            BeanUtils.copyProperties(measureMonitorRuleQueryResult, result);
            if (measureMonitorRuleQueryResult.getRule() != null) {
                result.setRuleId(measureMonitorRuleQueryResult.getRule().getId());
            }
            measureMonitorResultList.add(result);
        }
        return measureMonitorResultList;
    }

    private boolean nameRepeat(MeasureMonitorVO measureMonitorVO) {
        List<MeasureMonitor> monitors = measureMonitorService.list(Wrappers.<MeasureMonitor>lambdaQuery()
                .eq(MeasureMonitor::getName, measureMonitorVO.getName())
                .eq(MeasureMonitor::getSpaceId, measureMonitorVO.getSpaceId())
                .ne(Objects.nonNull(measureMonitorVO.getId()), MeasureMonitor::getId, measureMonitorVO.getId()));
        return CollectionUtils.isNotEmpty(monitors);
    }

    public MeasureMonitorRuleQueryResult executeSingleRule(Long ruleId, Long spaceId) {
        MeasureMonitorRuleQueryResult result = new MeasureMonitorRuleQueryResult();
        MeasureMonitorRule rule = measureMonitorRuleService.getById(ruleId);
        if (rule == null) {
            return result;
        }

        List<MeasureMonitorRuleDetail> details = measureMonitorRuleDetailService.list(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().eq(MeasureMonitorRuleDetail::getRuleId, rule.getId()));
        if (CollectionUtils.isEmpty(details)) {
            return result;
        }

        // TODO 目前一个rule只有一个detail
        MeasureMonitorRuleDetail detail = details.get(0);

        String dimCode = detail.getDimCode();
        String measCode = detail.getMeasCode();

        //同环比查询类型
        Integer ratioType = detail.getRatioType();
        Ratio ratio = getRatio(ratioType, dimCode);
        //时间类型过滤器
        List<Filter> dateFilter = buildDateTypeFilter(detail);


        MeasureMonitorRuleDetailVO detailVO = convert(detail);

        //过滤器
        List<Filter> filters = detailVO.getFilters();

        //维度分组
        List<DimWithValues> dimGroup = detailVO.getDimGroup();
        List<Filter> dimGroupFilters = buildDimGroupFilters(dimGroup);

        Boolean compare = false;

        List<MeasureMonitorDimGroupQueryResult> groupQueryResults = new LinkedList<>();

        List<Filter> queryFilter = new LinkedList<>();
        queryFilter.addAll(dimGroupFilters);
        queryFilter.addAll(filters);
        dateFilter.get(0).getOperatorList().get(0).setSqlOprType(SqlOprType.IN);
        queryFilter.add(dateFilter.get(0));
        Map<String, String> queryResult = queryRealValue(spaceId, measCode, dimCode, ratio, queryFilter, dimGroup);

        for (String key : queryResult.keySet()) {
            MeasureMonitorDimGroupQueryResult groupQueryResult = new MeasureMonitorDimGroupQueryResult();
            String realValue = queryResult.get(key);
            BigDecimal real = NumberFormatUtil.format(realValue);
            if (real == null) {
                continue;
            }
            Boolean dimGroupCompare = CompareWayEnum.compare(real, detail.getThresholdValue(), detail.getCompareWay());
            if (dimGroupCompare) {
                compare = true;
            }
            groupQueryResult.setTrigger(dimGroupCompare);
            groupQueryResult.setRealValue(realValue);
            groupQueryResult.setDimGroupKey(key);
            groupQueryResults.add(groupQueryResult);
        }


        //同环比查询，获取基期值和本期值
        if (!ratioType.equals(RatioType.DEFAULT.getCode())) {
            Ratio fixRatio = getRatio(0, null);
            //查询基期值
            List<Filter> queryBaseFilter = new LinkedList<>();
            queryBaseFilter.addAll(dimGroupFilters);
            queryBaseFilter.addAll(filters);
            dateFilter.get(1).getOperatorList().get(0).setSqlOprType(SqlOprType.IN);
            queryBaseFilter.add(dateFilter.get(1));
            Map<String, String> realBase = queryRealValue(spaceId, measCode, dimCode, fixRatio, queryBaseFilter, dimGroup);
            //查询本期值
            List<Filter> queryCurFilter = new LinkedList<>();
            queryCurFilter.addAll(dimGroupFilters);
            queryCurFilter.addAll(filters);
            dateFilter.get(2).getOperatorList().get(0).setSqlOprType(SqlOprType.IN);
            queryCurFilter.add(dateFilter.get(2));
            Map<String, String> realCur = queryRealValue(spaceId, measCode, dimCode, fixRatio, queryCurFilter, dimGroup);
            log.info("realCur：{}", JSON.toJSONString(realCur));
            log.info("realBase：{}", JSON.toJSONString(realBase));
            log.info("queryResult：{}", JSON.toJSONString(queryResult));
            //本期时间和基期时间
            String curDate = dateFilter.get(2).getOperatorList().get(0).getDataList().get(0);
            String baseDate = dateFilter.get(1).getOperatorList().get(0).getDataList().get(0);
            //生成最后结果，同环比、本周期、基期值
            groupQueryResults.forEach(e -> {
                String base = realBase.get(e.getDimGroupKey());
                String cur = realCur.get(e.getDimGroupKey());
                String[] values = new String[]{base, cur};
                e.setValues(values);
                e.setDates(new String[]{baseDate, curDate});
            });
        } else {
            String curDate = dateFilter.get(0).getOperatorList().get(0).getDataList().get(0);
            groupQueryResults.forEach(e -> {
                e.setValues(new String[]{e.getRealValue(), null});
                e.setDates(new String[]{curDate, null});
            });
        }

        Map<String, Dimension> allDimensionCodeMap = cacheManager.getMetadataCache().getAllDimensionCodeMap();
        Dimension dimension = allDimensionCodeMap.get(dimCode);

        Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();
        Measure measure = allMeasureCodeMap.get(measCode);
        String thresholdValue = detail.getThresholdValue();
        CompareWayEnum compareWayEnum = CompareWayEnum.getByCode(detail.getCompareWay());
        IndicatorRatioType indicatorRatioType = IndicatorRatioType.getTypeByCode(ratioType);
        StatPeriodEnum statPeriodEnum = StatPeriodEnum.getByCode(detail.getStatPeriod());

        result = new MeasureMonitorRuleQueryResult(
                compare, measure, dimension, thresholdValue, groupQueryResults, filters
                , compareWayEnum, indicatorRatioType, statPeriodEnum, rule, rule.getParentId());

        log.info("MeasureMonitorRuleQueryResult: {}", JSON.toJSONString(result));

        return result;
    }


    private Map<String, String> queryRealValue(Long spaceId, String measCode, String dimCode, Ratio ratio, List<Filter> dimGroupFilter, List<DimWithValues> dimGroup) {
        Map<String, String> res = new HashMap<>();
        PageData pageData = dorisQueryManager.ratioQuery(spaceId, measCode, dimCode, ratio, dimGroupFilter, dimGroup);
        log.info("指标预警查数结果：{}", pageData);

        Map<String, Dimension> allDimensionCodeMap = cacheManager.getMetadataCache().getAllDimensionCodeMap();

        List<List<Cell>> cellList = pageData.getCellList();
        for (List<Cell> cells : cellList) {
            cells.sort(new Comparator<Cell>() {
                @Override
                public int compare(Cell o1, Cell o2) {
                    return o1.getCode().compareTo(o2.getCode());
                }
            });

            String key = "";
            String realValue = "";
            for (Cell cell : cells) {

                String code = cell.getCode();
                if (Objects.equals(measCode, code)) {
                    realValue = cell.getData();
                } else {
                    Dimension dimension = allDimensionCodeMap.get(code);
                    if (!ViewType.isDate(dimension.getViewType())) {
                        key += cell.getName();
                        key += "为";
                        key += cell.getData();
                        key += " ";
                    }
                }
            }
            if (!NumberFormatUtil.isNumbericWithComma(realValue)) {
                log.error("指标预警查数格式异常，realValue：{}, dim：{}", realValue, key);
            } else {
                res.put(key, realValue);
            }
        }
        return res;
    }

    private String getRealValue(PageData pageData, String measCode) {
        log.info("指标预警查数结果：{}", pageData);
        List<List<Cell>> cellList = pageData.getCellList();
        for (List<Cell> cells : cellList) {
            for (Cell cell : cells) {
                String code = cell.getCode();
                if (Objects.equals(measCode, code)) {
                    return cell.getData();
                }
            }
        }
        return null;
    }

    private List<Filter> buildDimGroupFilters(List<DimWithValues> dimWithValuesList) {
        List<Filter> res = new LinkedList<>();
        if (dimWithValuesList == null || dimWithValuesList.size() == 0) return res;
        for (DimWithValues dimWithValues : dimWithValuesList) {
            Filter filter = new Filter();
            filter.setCode(dimWithValues.getDimensionCode());
            List<Operator> operatorList = new LinkedList<>();
            Operator operator = new Operator();
            List<String> dataList = dimWithValues.getValues().stream().map(e -> e.getData()).collect(Collectors.toList());
            operator.setDataList(dataList);
            operator.setSqlOprType(SqlOprType.IN);
            operatorList.add(operator);
            filter.setOperatorList(operatorList);
            res.add(filter);
        }
        return res;
    }


    private List<Filter> buildDateTypeFilter(MeasureMonitorRuleDetail detail) {

        String dimCode = detail.getDimCode();
        Integer ratioType = detail.getRatioType();

        //若为同环比查询，生成3个filter，同环比、基期值、本期值查询
        //固定值查询，生成1个
        List<Filter> filters = new LinkedList<>();

        //根据日期类型和统计周期，生成基期和本期时间
        Integer viewType = getViewType(detail.getDimCode());
        String[] dates = ViewType.getDefaultTime(viewType, detail.getStatPeriod(), ratioType);

        //根据查询类型，生成过滤器
        if (ratioType.equals(RatioType.DEFAULT.getCode())) {
            String[] date = new String[]{dates[1]};
            Filter filter = getDateFilter(date, viewType, dimCode);
            filters.add(filter);
        } else {

            Filter baseFilter = getDateFilter(new String[]{dates[0]}, viewType, dimCode);
            Filter curFilter = getDateFilter(new String[]{dates[1]}, viewType, dimCode);
            Filter ratioFilter = getDateFilter(new String[]{dates[1]}, viewType, dimCode);
            filters.add(ratioFilter);
            filters.add(baseFilter);
            filters.add(curFilter);

        }
        return filters;
    }

    private Filter getDateFilter(String[] dates, Integer viewType, String dimCode) {
        Filter filter = new Filter();
        filter.setCode(dimCode);
        List<Operator> operatorList = new LinkedList<>();
        Operator operator = new Operator();
        operator.setTimeRange(TimeRange.DATE);
        List<String> dataList = Arrays.asList(dates);
        operator.setDataList(dataList);
        operator.setSqlOprType(SqlOprType.IN);
        operatorList.add(operator);
        filter.setViewType(ViewType.findByInt(viewType).orElseThrow(() -> IndicatorParamNotValidException.error("维度类型不合法")));
        filter.setInternal(true);
        filter.setOperatorList(operatorList);
        return filter;
    }


    private Filter ratioFilterTest(MeasureMonitorRuleDetail detail) {
        Filter filter = new Filter();
        filter.setCode(detail.getMeasCode());
        List<Operator> operatorList = new LinkedList<>();
        Operator operator = new Operator();
        operator.setSqlOprType(SqlOprType.EQUAL);
        List<String> dataList = Arrays.asList("0.0138");
        operator.setDataList(dataList);
        operatorList.add(operator);
        filter.setInternal(true);
        filter.setOperatorList(operatorList);
        return filter;
    }

    private Integer getViewType(String dimCode) {
        Map<String, Dimension> allDimensionCodeMap = cacheManager.getMetadataCache().getAllDimensionCodeMap();
        Dimension dimension = allDimensionCodeMap.get(dimCode);
        Integer viewType = dimension.getViewType();
        return viewType;
    }


    private Ratio getRatio(Integer type, String dimCode) {
        RatioType ratioType = IndicatorRatioType.getRatioTypeByCode(type);
        if (ratioType == null || ratioType.equals(RatioType.DEFAULT)) {
            return null;
        }
        Ratio ratio = new Ratio();
        ratio.setRatioType(ratioType);
        ratio.setDimCode(dimCode);
        return ratio;
    }

    @Transactional(rollbackFor = Exception.class)
    public void on(Long id) {
        MeasureMonitor monitor = measureMonitorService.getById(id);
        IndicatorAssert.indicatorAssert(monitor == null, "预警不存在");
        monitor.setStatus(MeasureMonitorStatusType.ON.getCode());
        measureMonitorService.updateById(monitor);

        // 启用调度任务
        try {
            recreateJob(monitor);
        } catch (SchedulerException e) {
            log.error("任务启用失败：", e);
            throw IndicatorParamNotValidException.error("任务启用失败");
        }
        // 异步发送提醒
        CompletableFuture.runAsync(() -> {
            sendTips(monitor);
        });
    }


    @Resource
    FeiShuMsgManager feiShuMsgManager;
    @Resource
    UserManager userManager;

    private void sendTips(MeasureMonitor measureMonitor) {
        Long id = measureMonitor.getId();
//        List<MeasureMonitorConfigDesc> measureMonitorConfigDescList = measureMonitorConfigDescService.list(Wrappers.<MeasureMonitorConfigDesc>lambdaQuery().eq(MeasureMonitorConfigDesc::getMonitorId, id));
        StringBuilder builder = new StringBuilder();
        builder.append("创建人: 【" + measureMonitor.getCreator()
                + "】为您订阅 " + "【" + measureMonitor.getName() + "】的预警监控，将在预警发生时立即发送");
        builder.append("\n");
//        for(MeasureMonitorConfigDesc measureMonitorConfigDesc : measureMonitorConfigDescList) {
//            builder.append("[" + measureMonitorConfigDesc.getMeasure() +"_" + measureMonitorConfigDesc.getDismantlingTree() + "]" + "(" + url + measureMonitor.getSpaceId() + "/analysis/decomposition-tree)");
////            builder.append("{\"[" + urlName + "]" + "(" + url + measureMonitor.getSpaceId() + "/analysis/decomposition-tree)\"}");
//            builder.append("\n");
//        }
        builder.append("预警时间: " + DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.now()));
        String context = builder.toString();
        String title = "预警订阅成功通知";
        FeishuCardMessage cardMessage = feiShuMsgManager.buildMsg(title, context);
        List<MeasureMonitorReceiver> list = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                .eq(MeasureMonitorReceiver::getMonitorId, id)
                .eq(MeasureMonitorReceiver::getSendTips, YesNoType.NO.getCode()));
        List<MeasureMonitorReceiver> receivers = list.stream().filter(r -> Objects.equals(r.getSendFeishu(), YesNoType.YES.getCode())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(receivers)) {
            receivers.forEach(r -> {

                try {
                    if (r.getReceiverType().equals(0)) {
                        User user = userManager.getUserByName(r.getReceiverCode());
                        feiShuMsgManager.sendTextMessageByEmail(user.getEmail(), JSON.toJSONString(cardMessage), false);
                    } else if (r.getReceiverType().equals(2)) {
                        feiShuMsgManager.sendMsgToFeiShuChatGroup(r.getReceiverCode(), JSON.toJSONString(cardMessage), false);
                    }

                    r.setSendTips(YesNoType.YES.getCode());
                    measureMonitorReceiverService.updateById(r);
                } catch (Exception e) {
                    log.error("飞书消息发送失败:", e);
                }
            });
        }

    }

    private void createMeasureMonitorJob(MeasureMonitor measureMonitor) {
        try {
            scheduleManager.createCronJob(IndicatorConstant.MEASURE_MONITOR_JOB_KEY,
                    measureMonitor.getId().toString(),
                    measureMonitor.getId().toString(),
                    IndicatorConstant.MEASURE_MONITOR_JOB_GROUP,
                    measureMonitor.getCron(), measureMonitor.getStatus().intValue() == MeasureMonitorStatusEnum.ON.getCode().intValue());
        } catch (SchedulerException e) {
            log.error("启用调度任务异常: ", e);
            throw IndicatorParamNotValidException.error("启用调度任务异常");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void off(Long id) {
        if (id == null) {
            throw IndicatorParamNotValidException.error("id不能为空");
        }

        /**
         * 为了避免脏数据出现，这个接口先行关闭调度任务
         */
        // 关闭调度任务
        try {
            scheduleManager.del(id.toString(), IndicatorConstant.MEASURE_MONITOR_JOB_GROUP);
        } catch (SchedulerException e) {
            log.error("关闭任务失败：", e);
            throw IndicatorParamNotValidException.error("关闭任务失败");
        }
        MeasureMonitor monitor = measureMonitorService.getById(id);
        IndicatorAssert.indicatorAssert(monitor == null, "预警不存在");
        monitor.setStatus(MeasureMonitorStatusType.OFF.getCode());
        measureMonitorService.updateById(monitor);


    }

    /**
     * 查询预警列表,只有基础信息，不包含详情
     *
     * @param queryVO
     * @return
     */
    public List<MeasureMonitorVO> list(MeasureMonitorQuery queryVO) {
        QueryWrapper<MeasureMonitor> wrapper = new QueryWrapper<>();
        wrapper.eq("space_id", queryVO.getSpaceId())
                .eq(queryVO.getIsMine(), "creator", UserThreadLocalUtil.getUserName())
                .and(StringUtils.hasLength(queryVO.getKeyword()), query -> query.like("name", queryVO.getKeyword()));
        List<MeasureMonitor> monitors = measureMonitorService.list(wrapper);
        if (CollectionUtils.isEmpty(monitors)) {
            return Collections.EMPTY_LIST;
        }
        List<MeasureMonitorVO> vos = monitors.stream().map(m -> convert(m)).collect(Collectors.toList());
        return vos;
    }

    public MeasureMonitorVO detail(Long id) {
        MeasureMonitor measureMonitor = measureMonitorService.getById(id);
        IndicatorAssert.indicatorAssert(measureMonitor == null, "预警不存在");
        MeasureMonitorVO monitorVO = convert(measureMonitor);
        List<MeasureMonitorRule> measureMonitorRules = measureMonitorRuleService.list(Wrappers.<MeasureMonitorRule>lambdaQuery().eq(MeasureMonitorRule::getMonitorId, id));
        if (CollectionUtils.isNotEmpty(measureMonitorRules)) {
            List<MeasureMonitorRuleVO> ruleVOS = measureMonitorRules.stream().map(r -> convert(r)).collect(Collectors.toList());
            List<MeasureMonitorRuleVO> rootRules = ruleVOS.stream().filter(r -> r.getParentId() == null).collect(Collectors.toList());
            rootRules.forEach(r -> findChildren(r, ruleVOS));
            monitorVO.setRules(rootRules);
        }
        List<MeasureMonitorConfigDesc> measureMonitorConfigDescList = measureMonitorConfigDescService.list(Wrappers.<MeasureMonitorConfigDesc>lambdaQuery().eq(MeasureMonitorConfigDesc::getMonitorId, id));
        if (CollectionUtils.isNotEmpty(measureMonitorConfigDescList)) {
            monitorVO.setMeasureMonitorConfigDescList(measureMonitorConfigDescList);
        }
        return monitorVO;
    }

    private void findChildren(MeasureMonitorRuleVO target, List<MeasureMonitorRuleVO> ruleVOS) {
        loadDetail(target);
        List<MeasureMonitorRuleVO> children = ruleVOS.stream().filter(r -> Objects.equals(r.getParentId(), target.getId())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(children)) {
            children.forEach(c -> {
                findChildren(c, ruleVOS);
            });
        }
        target.getChildren().addAll(children);
    }

    private void loadDetail(MeasureMonitorRuleVO target) {
        // TODO 查找Detail目前不支持树状结构，有多个detail只取第一条,如果后续支持多个detail，需要递归成树状结构给前端
        List<MeasureMonitorRuleDetail> list = measureMonitorRuleDetailService.list(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().eq(MeasureMonitorRuleDetail::getRuleId, target.getId()));
        if (CollectionUtils.isNotEmpty(list)) {
            List<MeasureMonitorRuleDetailVO> details = list.stream().map(d -> convert(d)).collect(Collectors.toList());
            target.setDetails(details);
        }
    }

    private MeasureMonitorRuleDetailVO convert(MeasureMonitorRuleDetail detail) {
        MeasureMonitorRuleDetailVO vo = new MeasureMonitorRuleDetailVO();
        BeanUtils.copyProperties(detail, vo);

        //装配过滤器
        List<Long> filterIds = ruleFilterMapper.selectList(Wrappers.<MeasureMonitorRuleFilter>lambdaQuery().eq(MeasureMonitorRuleFilter::getRuleDetailId, detail.getId()))
                .stream().map(MeasureMonitorRuleFilter::getFilterId).collect(Collectors.toList());
        List<Filter> filters = filterDao.findAllById(filterIds);
        vo.setFilters(filters);

        //装配维度分组
        List<DimWithValues> dims = new LinkedList<>();
        List<MeasureMonitorDimGroup> dimGroups = dimGroupMapper.selectList(Wrappers.<MeasureMonitorDimGroup>lambdaQuery().eq(MeasureMonitorDimGroup::getRuleDetailId, detail.getId()));
        dimGroups.stream().forEach(e -> {
            List<MeasureMonitorDimGroupDetail> list = dimGroupDetailMapper.selectList(Wrappers.<MeasureMonitorDimGroupDetail>lambdaQuery().eq(MeasureMonitorDimGroupDetail::getGroupId, e.getId()));
            List<BaseDimValue> values = list.stream().map(a -> {
                BaseDimValue value = new BaseDimValue();
                value.setId(a.getDimensionValueId());
                value.setData(a.getDimensionValue());
                return value;
            }).collect(Collectors.toList());
            DimWithValues dimWithValues = new DimWithValues();
            dimWithValues.setDimensionCode(e.getDimensionCode());
            dimWithValues.setSeq(e.getSeq());
            dimWithValues.setValues(values);
            dims.add(dimWithValues);
        });
        vo.setDimGroup(dims);


        return vo;
    }

    private MeasureMonitorRuleVO convert(MeasureMonitorRule rule) {
        MeasureMonitorRuleVO vo = new MeasureMonitorRuleVO();
        BeanUtils.copyProperties(rule, vo);
        return vo;

    }

    private MeasureMonitorVO convert(MeasureMonitor measureMonitor) {
        MeasureMonitorVO monitorVO = new MeasureMonitorVO();
        BeanUtils.copyProperties(measureMonitor, monitorVO);
        monitorVO.setCreator(userManager.getUserByName(measureMonitor.getCreator()));
        monitorVO.setUpdater(userManager.getUserByName(measureMonitor.getUpdater()));
        monitorVO.setUpdateTime(Timestamp.valueOf(measureMonitor.getUpdateTime()).getTime());
        monitorVO.setCreateTime(Timestamp.valueOf(measureMonitor.getCreateTime()).getTime());
        monitorVO.setLastTriggerTime(measureMonitor.getLastTriggerTime() == null ? null : Timestamp.valueOf(measureMonitor.getLastTriggerTime()).getTime());
        monitorVO.setTaskSchedule(JSON.parseObject(measureMonitor.getTaskSchedule(), TaskScheduleVO.class));
        List<MeasureMonitorReceiver> receivers = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery().eq(MeasureMonitorReceiver::getMonitorId, measureMonitor.getId()).eq(MeasureMonitorReceiver::getReceiverType, 0));
        if (CollectionUtils.isNotEmpty(receivers)) {
            List<User> users = receivers.stream().map(r -> userManager.getUserByName(r.getReceiverName())).collect(Collectors.toList());
            monitorVO.setReceiver(users);
        }

        List<MeasureMonitorReceiver> groupReceivers = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery().eq(MeasureMonitorReceiver::getMonitorId, measureMonitor.getId()).eq(MeasureMonitorReceiver::getReceiverType, 2));
        List<ChatGroup> groups = groupReceivers.stream().map(e -> {
            ChatGroup chatGroup = new ChatGroup();
            chatGroup.setDescription(e.getDescription());
            chatGroup.setChatId(e.getReceiverCode());
            chatGroup.setName(e.getReceiverName());
            chatGroup.setAvatar(e.getAvatar());
            return chatGroup;
        }).collect(Collectors.toList());
        monitorVO.setReceiveChatGroup(groups);

        return monitorVO;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        measureMonitorService.removeById(id);
        measureMonitorReceiverService.remove(Wrappers.<MeasureMonitorReceiver>lambdaQuery().eq(MeasureMonitorReceiver::getMonitorId, id));
        removeRules(id);
        removeConfigDescList(id);
        // 关闭调度任务
        try {
            scheduleManager.del(id.toString(), IndicatorConstant.MEASURE_MONITOR_JOB_GROUP);
        } catch (SchedulerException e) {
            log.error("调度任务删除异常：", e);
            throw IndicatorParamNotValidException.error("调度任务删除失败");
        }
    }

    /**
     * 保存或新增方法
     *
     * @param measureMonitorVO
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(MeasureMonitorVO measureMonitorVO) {
        boolean nameRepeat = nameRepeat(measureMonitorVO);
        if (nameRepeat) {
            throw IndicatorParamNotValidException.error("名称【" + measureMonitorVO.getName() + "】重复");
        }
        MeasureMonitor measureMonitor = null;
        if (measureMonitorVO.getId() == null) {
            // 创建
            measureMonitor = new MeasureMonitor();
            measureMonitor.initCreate();
            BeanUtils.copyProperties(measureMonitorVO, measureMonitor);
        } else {
            // 更新
            measureMonitor = measureMonitorService.getById(measureMonitorVO.getId());
            IndicatorAssert.indicatorAssert(measureMonitor == null, "预警不存在");
            measureMonitor.initUpdate();
            BeanUtils.copyProperties(measureMonitorVO, measureMonitor);
            // 删除原有规则及详情
            removeRules(measureMonitorVO.getId());
            //删除原有配置解读
            removeConfigDescList(measureMonitorVO.getId());
        }
        // 保存预警配置
        measureMonitor.setCron(getCron(measureMonitorVO.getTaskSchedule()));
        measureMonitor.setCronDesc(getCronDesc(measureMonitorVO.getTaskSchedule()));
        measureMonitor.setTaskSchedule(JSON.toJSONString(measureMonitorVO.getTaskSchedule()));
        //TODO level暂时写死
        measureMonitor.setLevel(1);
        measureMonitor.setLogicType(measureMonitorVO.getLogicType());
        measureMonitorService.saveOrUpdate(measureMonitor);
        // 保存接收人
        saveReciivers(measureMonitorVO, measureMonitor.getId());
        // 保存规则
        saveRules(measureMonitorVO.getRules(), null, measureMonitor.getId());
        //保存配置解读
        saveConfigDescList(measureMonitorVO.getMeasureMonitorConfigDescList(), measureMonitor.getId());

        // 注册job
        try {
            // 重新创建job
            recreateJob(measureMonitor);
            if (measureMonitor.getStatus() == MeasureMonitorStatusEnum.ON.getCode()) {
                // 开启监控
                on(measureMonitor.getId());
            } else {
                // 关闭监控
                off(measureMonitor.getId());
            }
        } catch (SchedulerException e) {
            log.error("重置任务失败:", e);
            throw IndicatorParamNotValidException.error("重置任务失败");
        }

    }

    private void recreateJob(MeasureMonitor measureMonitor) throws SchedulerException {
        scheduleManager.del(measureMonitor.getId().toString(), IndicatorConstant.MEASURE_MONITOR_JOB_GROUP);
        createMeasureMonitorJob(measureMonitor);
        // // 异步发送提醒
        // CompletableFuture.runAsync(() -> {
        //     sendTips(measureMonitor);
        // });
    }

    @Resource
    IMeasureMonitorReceiverService measureMonitorReceiverService;

    private void saveReciivers(MeasureMonitorVO measureMonitorVO, Long monitorId) {

        List<MeasureMonitorReceiver> oldReceivers = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                .eq(MeasureMonitorReceiver::getMonitorId, monitorId).eq(MeasureMonitorReceiver::getReceiverType, 0));
        List<User> users = measureMonitorVO.getReceiver();
        if (CollectionUtils.isNotEmpty(oldReceivers)) {
            Set<String> usernames = users.stream().map(User::getUsername).collect(Collectors.toSet());
            List<Long> ids = oldReceivers.stream().filter(r -> !usernames.contains(r.getReceiverCode())).map(r -> r.getId()).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(ids)) {
                measureMonitorReceiverService.removeByIds(ids);
            }
        }

        if (CollectionUtils.isNotEmpty(users)) {
            users.stream().forEach(user -> {
                MeasureMonitorReceiver receiver = new MeasureMonitorReceiver();
                receiver.setReceiverCode(user.getUsername());
                receiver.setReceiverName(user.getUsername());
                receiver.setSendFeishu(YesNoType.YES.getCode());
                //TODO 本期只有个人
                receiver.setReceiverType(0);
                receiver.setMonitorId(monitorId);
                measureMonitorReceiverService.saveOrUpdate(receiver, Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                        .eq(MeasureMonitorReceiver::getReceiverCode, user.getUsername())
                        .eq(MeasureMonitorReceiver::getMonitorId, monitorId));
            });
        }

        List<MeasureMonitorReceiver> oldGroups = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                .eq(MeasureMonitorReceiver::getMonitorId, monitorId).eq(MeasureMonitorReceiver::getReceiverType, 2));

        List<ChatGroup> groups = measureMonitorVO.getReceiveChatGroup();
        if (CollectionUtils.isNotEmpty(oldGroups)) {
            Set<String> groupCode = groups.stream().map(ChatGroup::getChatId).collect(Collectors.toSet());
            List<Long> ids = oldGroups.stream().filter(e -> !groupCode.contains(e.getReceiverCode())).map(e -> e.getId()).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(ids)) {
                measureMonitorReceiverService.removeByIds(ids);
            }
        }

        if (CollectionUtils.isNotEmpty(groups)) {
            groups.stream().forEach(e -> {
                MeasureMonitorReceiver receiver = new MeasureMonitorReceiver();
                receiver.setReceiverCode(e.getChatId());
                receiver.setReceiverName(e.getName());
                receiver.setSendFeishu(YesNoType.YES.getCode());
                receiver.setReceiverType(2);
                receiver.setMonitorId(monitorId);
                receiver.setAvatar(e.getAvatar());
                receiver.setDescription(e.getDescription());
                measureMonitorReceiverService.saveOrUpdate(receiver, Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                        .eq(MeasureMonitorReceiver::getReceiverType, 2)
                        .eq(MeasureMonitorReceiver::getReceiverCode, e.getChatId())
                        .eq(MeasureMonitorReceiver::getMonitorId, monitorId));
            });
        }
    }

    private String getCron(Integer cronType) {
        CronEnum cronEnum = CronEnum.findByInt(cronType);
        if (cronEnum == null) {
            throw IndicatorParamNotValidException.error("cron表达式类型不合法");
        }
        return cronEnum.getCron();
    }

    private String getCron(TaskScheduleVO taskScheduleVO) {
        try {
            String cron = CronUtil.createCronExpression(taskScheduleVO);
            return cron;
        } catch (Exception e) {
            throw IndicatorParamNotValidException.error("推送日期配置不合法");
        }
    }

    private String getCronDesc(TaskScheduleVO taskScheduleVO) {
        try {
            String cronDesc = CronUtil.createDescription(taskScheduleVO);
            return cronDesc;
        } catch (Exception e) {
            throw IndicatorParamNotValidException.error("推送日期配置不合法");
        }
    }


    private void saveRules(List<MeasureMonitorRuleVO> rules, Long parentId, Long monitorId) {
        if (CollectionUtils.isNotEmpty(rules)) {
            for (int i = 0; i < rules.size(); i++) {
                MeasureMonitorRuleVO rule = rules.get(i);
                MeasureMonitorRule monitorRule = convert(rule);
                monitorRule.setMonitorId(monitorId);
                monitorRule.setParentId(parentId);
                monitorRule.setSeq(i);
                monitorRule.setLevel(rule.getLevel());
                measureMonitorRuleService.save(monitorRule);
                List<MeasureMonitorRuleDetailVO> details = rule.getDetails();
                saveDetails(details, null, monitorRule.getId());
                if (CollectionUtils.isNotEmpty(rule.getChildren())) {
                    // 保存子节点
                    saveRules(rule.getChildren(), monitorRule.getId(), monitorId);
                }
            }
        }
    }

    private void saveDetails(List<MeasureMonitorRuleDetailVO> details, Long parentId, Long ruleId) {
        if (CollectionUtils.isNotEmpty(details)) {
            for (int i = 0; i < details.size(); i++) {
                MeasureMonitorRuleDetailVO vo = details.get(i);
                // 保存之前先检查参数格式
                vo.check();
                MeasureMonitorRuleDetail detail = convert(vo);
                detail.setRuleId(ruleId);
                detail.setParentId(parentId);
                detail.setSeq(i);
                measureMonitorRuleDetailService.save(detail);
                //保存维度分组和过滤器
                //这里拿到的detail id有问题
                saveDimGroupAndFilter(vo, detail.getId());
                if (CollectionUtils.isNotEmpty(vo.getChildren())) {
                    saveDetails(vo.getChildren(), detail.getParentId(), ruleId);
                }
            }
        }
    }

    @Autowired
    FilterDao filterDao;

    private void saveDimGroupAndFilter(MeasureMonitorRuleDetailVO detail, Long ruleDetailId) {
        List<Filter> filters = detail.getFilters();
        if (CollectionUtils.isNotEmpty(filters)) {

            for (int i = 0; i < filters.size(); i++) {
                Filter filter = filters.get(i);
                filter.setId(null);
                List<Operator> operatorList = filter.getOperatorList();
                operatorList.forEach(e -> {
                    e.setId(null);
                });
                filterDao.save(filter);
                MeasureMonitorRuleFilter ruleFilter = new MeasureMonitorRuleFilter();
                ruleFilter.setFilterId(filter.getId());
                ruleFilter.setRuleDetailId(ruleDetailId);
                ruleFilter.setSeq(i);
                ruleFilter.insert();
            }
        }

        List<DimWithValues> dimGroups = detail.getDimGroup();
        if (CollectionUtils.isNotEmpty(dimGroups)) {
            dimGroups.stream().forEach(e -> {
                MeasureMonitorDimGroup dimGroup = new MeasureMonitorDimGroup();
                BeanUtils.copyProperties(e, dimGroup);
                dimGroup.setRuleDetailId(ruleDetailId);
                dimGroup.insert();
                List<BaseDimValue> values = e.getValues();
                values.stream().forEach(value -> {
                    MeasureMonitorDimGroupDetail valueDetail = new MeasureMonitorDimGroupDetail();
                    valueDetail.setGroupId(dimGroup.getId());
                    valueDetail.setDimensionValue(value.getData());
                    valueDetail.setDimensionValueId(value.getId());
                    valueDetail.insert();
                });
            });
        }
    }

    private void saveConfigDescList(List<MeasureMonitorConfigDesc> measureMonitorConfigDescList, Long monitorId) {
        if (CollectionUtils.isNotEmpty(measureMonitorConfigDescList)) {
            for (int i = 0; i < measureMonitorConfigDescList.size(); ++i) {
                MeasureMonitorConfigDesc measureMonitorConfigDesc = measureMonitorConfigDescList.get(i);
                measureMonitorConfigDesc.setMonitorId(monitorId);
                measureMonitorConfigDescService.save(measureMonitorConfigDesc);
            }
        }
    }

    private MeasureMonitorRuleDetail convert(MeasureMonitorRuleDetailVO vo) {
        MeasureMonitorRuleDetail detail = new MeasureMonitorRuleDetail();
        BeanUtils.copyProperties(vo, detail);
        return detail;
    }


    private MeasureMonitorRule convert(MeasureMonitorRuleVO vo) {
        MeasureMonitorRule measureMonitorRule = new MeasureMonitorRule();
        BeanUtils.copyProperties(vo, measureMonitorRule);
        return measureMonitorRule;
    }


    /**
     * 删除所有的规则和详情
     *
     * @param monitorId
     */
    private void removeRules(Long monitorId) {
        List<MeasureMonitorRule> monitorRules = measureMonitorRuleService.list(Wrappers.<MeasureMonitorRule>lambdaQuery().eq(MeasureMonitorRule::getMonitorId, monitorId));
        if (CollectionUtils.isNotEmpty(monitorRules)) {
            List<Long> ruleIds = monitorRules.stream().map(MeasureMonitorRule::getId).collect(Collectors.toList());
            List<MeasureMonitorRuleDetail> details = measureMonitorRuleDetailService.list(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().in(MeasureMonitorRuleDetail::getRuleId, ruleIds));
            List<Long> detailIds = details.stream().map(MeasureMonitorRuleDetail::getId).collect(Collectors.toList());

            measureMonitorRuleService.removeByIds(ruleIds);
            measureMonitorRuleDetailService.remove(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().in(MeasureMonitorRuleDetail::getRuleId, ruleIds));


            //删除维度分组和过滤器
            removeFilterAndDimGroup(detailIds);
        }

    }


    public void removeFilterAndDimGroup(List<Long> detailIds) {

        //删除维度分组
        LambdaQueryWrapper<MeasureMonitorDimGroup> wrapper = new LambdaQueryWrapper<MeasureMonitorDimGroup>().in(MeasureMonitorDimGroup::getRuleDetailId, detailIds);
        List<Long> groupIds = dimGroupMapper.selectList(wrapper).stream().map(MeasureMonitorDimGroup::getId).collect(Collectors.toList());
        dimGroupMapper.delete(wrapper);
        if (groupIds.size() != 0)
            dimGroupDetailMapper.delete(Wrappers.<MeasureMonitorDimGroupDetail>lambdaQuery().in(MeasureMonitorDimGroupDetail::getGroupId, groupIds));

        //删除filter
        List<Long> filterIds = ruleFilterMapper.selectList(Wrappers.<MeasureMonitorRuleFilter>lambdaQuery().in(MeasureMonitorRuleFilter::getRuleDetailId, detailIds))
                .stream().map(e -> e.getFilterId()).collect(Collectors.toList());
        if (filterIds.size() != 0) {
            filterDao.deleteAllById(filterIds);
            ruleFilterMapper.delete(Wrappers.<MeasureMonitorRuleFilter>lambdaQuery().in(MeasureMonitorRuleFilter::getRuleDetailId, detailIds));
        }
    }


    private void removeConfigDescList(Long monitorId) {
        List<MeasureMonitorConfigDesc> measureMonitorConfigDescList = measureMonitorConfigDescService.list(Wrappers.<MeasureMonitorConfigDesc>lambdaQuery().eq(MeasureMonitorConfigDesc::getMonitorId, monitorId));
        if (CollectionUtils.isNotEmpty(measureMonitorConfigDescList)) {
            List<Long> configIds = measureMonitorConfigDescList.stream().map(MeasureMonitorConfigDesc::getId).collect(Collectors.toList());
            measureMonitorConfigDescService.removeByIds(configIds);
        }
    }

    public void sendTips2() {
        MeasureMonitorConfigDesc measureMonitorConfigDesc = new MeasureMonitorConfigDesc();
        measureMonitorConfigDesc.setMeasure("测试指标");
        measureMonitorConfigDesc.setDismantlingTree("测试拆解树");
        String urlName = "指标名称";
        MeasureMonitor measureMonitor = new MeasureMonitor();
        measureMonitor.setSpaceId(79L);
        StringBuilder builder = new StringBuilder();
        builder.append("创建人: 【" + "measureMonitor.getCreator()"
                + "】为您订阅 " + "【" + "measureMonitor.getName()" + "】的预警监控，将在预警发生时立即发送");
//        builder.append("创建人: 【" + measureMonitor.getCreator()
//                + "】为您订阅 " + "【" + measureMonitor.getName() + "】的预警监控，将在预警发生时立即发送");
        builder.append("\n");
        builder.append("[" + measureMonitorConfigDesc.getMeasure() + "_" + measureMonitorConfigDesc.getDismantlingTree() + "]" + "(" + url + measureMonitor.getSpaceId() + "/analysis/decomposition-tree)");
        builder.append("\n");
        builder.append("预警时间: " + DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.now()));
        String context = builder.toString();
        String title = "预警订阅成功通知";
        FeishuCardMessage cardMessage = feiShuMsgManager.buildMsg(title, context);

        Long id = (long) 1;
        List<MeasureMonitorReceiver> list = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                .eq(MeasureMonitorReceiver::getMonitorId, id)
                .eq(MeasureMonitorReceiver::getSendTips, YesNoType.NO.getCode()));
        List<MeasureMonitorReceiver> receivers = list.stream().filter(r -> Objects.equals(r.getSendFeishu(), YesNoType.YES.getCode())).collect(Collectors.toList());
        try {
            feiShuMsgManager.sendTextMessageByEmail("user.getEmail()", JSON.toJSONString(cardMessage), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

//        if (CollectionUtils.isNotEmpty(receivers)) {
//            receivers.forEach(r -> {
//                User user = userManager.getUserByName(r.getReceiverCode());
//                try {
//                    feiShuMsgManager.sendTextMessageByEmail(user.getEmail(), JSON.toJSONString(cardMessage), false);
//                    r.setSendTips(YesNoType.YES.getCode());
//                    measureMonitorReceiverService.updateById(r);
//                } catch (Exception e) {
//                    log.error("飞书消息发送失败:", e);
//                }
//            });
//        }

    }

}
