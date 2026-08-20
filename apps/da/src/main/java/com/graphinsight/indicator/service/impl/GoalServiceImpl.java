package com.graphinsight.indicator.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.Goal;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.GoalMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.exception.GoalNotUniqueException;
import com.graphinsight.indicator.exception.GoalValidateException;
import com.graphinsight.indicator.exception.QueryRealNumForGoalException;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.manager.DorisQueryManager;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.GoalDTO;
import com.graphinsight.indicator.model.dto.GoalDateDimDTO;
import com.graphinsight.indicator.model.dto.GoalMeasureBaseInfo;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.GoalService;
import com.graphinsight.indicator.util.NumberFormatUtil;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@DS("mysql")
@Service
@Slf4j
public class GoalServiceImpl implements GoalService {

    @Autowired
    GoalMapper goalMapper;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    DimensionMapper dimensionMapper;

    @Autowired
    MeasureMapper measureMapper;

    @Autowired
    BloodManager bloodManager;

    @Autowired
    DorisQueryManager dorisQueryManager;

    @Autowired
    ChartQueryService chartQueryService;

    private String getCnName(String code){
        if(!code.startsWith("MEAS")){
            Dimension dimension = dimensionMapper.selectOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode,code));
            if (dimension==null) return "该维度已被删除";
            return dimension.getCnName();
        }else {
            Measure measure = measureMapper.selectOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode,code));
            if (measure==null) return "该指标已被删除";
            return measure.getCnName();
        }
    }


    @Override
    public List<GoalDateDimDTO> getDateDim(Integer spaceId, String measureCode){
        LinkedList<GoalDateDimDTO> res = new LinkedList<>();
        LambdaQueryWrapper<Goal> wrapper = Wrappers.<Goal>lambdaQuery().eq(Goal::getSpaceId,spaceId);
        if (!StringUtil.isEmpty(measureCode)) wrapper.eq(Goal::getMeasureCode,measureCode);
        wrapper.isNull(Goal::getParentId);
        List<Goal> list = goalMapper.selectList(wrapper);
        Map<String,Goal> map = new HashMap<>();
        for (Goal goal:list){
            if(ViewType.isDate(goal.getDimViewType()) && !map.containsKey(goal.getDimensionCode())) map.put(goal.getDimensionCode(),goal);
        }
        for (String key:map.keySet()){
            Goal goal = map.get(key);
            GoalDateDimDTO obj = new GoalDateDimDTO(goal.getDimViewType(),goal.getDimensionCode(),ViewType.findByInt(goal.getDimViewType()).get().getName());
            if(getCnName(goal.getDimensionCode()).equals("月")){
                res.addFirst(obj);
            }else{
                res.add(obj);
            }
        }
        return res;
    }

    @Override
    public List<GoalMeasureBaseInfo> getMeasure(Integer spaceId, Integer dimViewType, String dimensionValue){
        List<GoalMeasureBaseInfo> res = new LinkedList<>();
        LambdaQueryWrapper<Goal> wrapper = Wrappers.<Goal>lambdaQuery().eq(Goal::getSpaceId,spaceId);
        if(dimViewType!=null) wrapper.eq(Goal::getDimViewType,dimViewType);
        if (!StringUtil.isEmpty(dimensionValue)) wrapper.eq(Goal::getDimensionValue,dimensionValue);
        wrapper.isNull(Goal::getParentId);
        List<Goal> list = goalMapper.selectList(wrapper);
        Map<String,Goal> map = new HashMap<>();
        for (Goal goal:list){
            if(!map.containsKey(goal.getMeasureCode())) map.put(goal.getMeasureCode(),goal);
        }
        for (String key:map.keySet()){
            Goal goal = map.get(key);
            res.add(new GoalMeasureBaseInfo(getCnName(goal.getMeasureCode()),goal.getMeasureCode()));
        }
        return res;
    }



    @Override
    public GoalDTO update(GoalDTO root) throws GoalValidateException {
        if (root.getValidate()) {
            String res = validate(root);
            if(res !=null){
                throw new GoalValidateException("校验失败，维度"+res+"下子目标加和不等于父目标的目标值");
            }
        }

        Integer diffRateAlgo = root.getDiffRateAlgo();
        LinkedList<GoalDTO> list = new LinkedList<>();
        list.add(root);
        while (CollectionUtils.isNotEmpty(list)) {
            GoalDTO goalDTO = list.poll();
            if(goalDTO.getChildren()!=null) list.addAll(goalDTO.getChildren());
            Goal goal = goalMapper.selectById(goalDTO.getId());
            goal.setTargetNum(NumberFormatUtil.formatExceptionWithZero(goalDTO.getTargetNum()));
            goal.setStatus(goalDTO.getStatus());
            goal.setDiffRateAlgo(diffRateAlgo);
            goal.setValidate(goalDTO.getValidate());
            goal.setRemark(goalDTO.getRemark());
            goal.setUpdater(UserThreadLocalUtil.get().getUsername());
            goal.updateById();
        }
        GoalDTO res = loadAndCompute(goalMapper.selectById(root.getId()));
        return res;
    }


    public String validate(GoalDTO goalDTO) {
        if(goalDTO == null || goalDTO.getChildren()==null) return null;
        Double rootTargetNum = NumberFormatUtil.formatExceptionWithZero(goalDTO.getTargetNum()).doubleValue();
        HashMap<String,Double> map = new HashMap<>();
        List<GoalDTO> childGoal = goalDTO.getChildren();
        for (GoalDTO goal:childGoal){
            String key = getCnName(goal.getDimensionCode());
            if (!map.containsKey(key)){
                map.put(key,NumberFormatUtil.formatExceptionWithZero(goal.getTargetNum()).doubleValue());
            }else{
                map.put(key,map.get(key)+NumberFormatUtil.formatExceptionWithZero(goal.getTargetNum()).doubleValue());
            }
        }
        for (String key:map.keySet()){
            Double childNum = map.get(key);
            if(!childNum.equals(rootTargetNum)){
                return key;
            }
        }
        return null;
    }



    @Override
    public List<GoalDTO> add(GoalAddVO goalAddVo) throws GoalNotUniqueException {
        List<GoalDTO> goalList = new LinkedList<>();
        List<BaseDimValue> dimensionValueInfo = goalAddVo.getDimensionValueInfo();
        if(goalAddVo.getParentId()!=null){
            Goal parentGoal = goalMapper.selectById(goalAddVo.getParentId());
            goalAddVo.setDiffRateAlgo(parentGoal.getDiffRateAlgo());
        }
        //批量或单独添加目标
        for(BaseDimValue baseDimValue:dimensionValueInfo){

            //目标唯一性校验
            LambdaQueryWrapper<Goal> wrapper = Wrappers.<Goal>lambdaQuery()
                    .eq(Goal::getMeasureCode,goalAddVo.getMeasureCode())
                    .eq(Goal::getDimensionCode,goalAddVo.getDimensionCode())
                    .eq(Goal::getDimensionValue,baseDimValue.getData())
                    .eq(Goal::getSpaceId,goalAddVo.getSpaceId());
            if(goalAddVo.getParentId()==null){
                wrapper.isNull(Goal::getParentId);
            }else{
                wrapper.eq(Goal::getParentId,goalAddVo.getParentId());
            }
            Goal valid = goalMapper.selectOne(wrapper);
            if (valid!=null) throw new GoalNotUniqueException("目标重复设定，请检查后再次提交，重复的目标："+getCnName(goalAddVo.getMeasureCode()) + " " + baseDimValue.getData());
            //设置目标参数
            Goal goal = new Goal();
            BeanUtils.copyProperties(goalAddVo,goal);
            goal.setTargetNum(NumberFormatUtil.format(goalAddVo.getTargetNum()));
            goal.setDimensionValue(baseDimValue.getData());
            goal.setDimensionValueId(baseDimValue.getId());
            goal.setCreator(UserThreadLocalUtil.get().getUsername());
            goalMapper.insert(goal);
            goalList.add(compute(goal));
        }
        return goalList;
    }

    @Override
    public List<GoalDTO> list(Integer spaceId) {
        List<Goal> rootGoals = goalMapper.selectList(Wrappers.<Goal>lambdaQuery().eq(Goal::getSpaceId,spaceId).isNull(Goal::getParentId));
        List<GoalDTO> res = new LinkedList<>();
        for (Goal goal:rootGoals){
            res.add(loadAndCompute(goal));
        }
        return res;
    }

    @Override
    public GoalDTO detail(Long goalId) {
        Goal goal = goalMapper.selectById(goalId);
        if (goal == null) return null;
        GoalDTO result = loadAndCompute(goal);
        if (StringUtil.isEmpty(result.getFiltersJson())) {
            List<Map<String,Object>> filters = new LinkedList<>();
            Goal node = goal;
            while (node != null) {
                if (!ViewType.isDate(node.getDimViewType())) {
                    Map<String,Object> filter = new LinkedHashMap<>();
                    filter.put("member", node.getDimensionCode());
                    filter.put("operator", "equals");
                    filter.put("values", Collections.singletonList(
                            StringUtil.isEmpty(node.getDimensionValueId())
                                    ? node.getDimensionValue() : node.getDimensionValueId()));
                    filters.add(filter);
                }
                node = node.getParentId() == null ? null : goalMapper.selectById(node.getParentId());
            }
            result.setFiltersJson(JSON.toJSONString(filters));
        }
        return result;
    }

    private GoalDTO loadAndCompute(Goal goal){
        if(goal == null) return null;
        GoalDTO goalDto = compute(goal);
        LinkedList<GoalDTO> parent = new LinkedList<>();
        parent.add(goalDto);
        while (parent != null && !parent.isEmpty()){
            LinkedList<GoalDTO> childs = new LinkedList<>();
            while (!parent.isEmpty()){
                GoalDTO goal1 = parent.poll();
                List<Goal> childGoals = goalMapper.selectList(Wrappers.<Goal>lambdaQuery().eq(Goal::getParentId, goal1.getId()));
                LinkedList<GoalDTO> childGoalDTOs = new LinkedList<>();
                for (Goal childGoal:childGoals){
                    GoalDTO element = null;
                    try {
                        element = compute(childGoal);
                        childGoalDTOs.addFirst(element);
                    }catch (Exception e){
                        log.error("目标计算异常，异常的目标：{}",childGoal);
                    }
                }
                goal1.setChildren(childGoalDTOs);
                childs.addAll(childGoalDTOs);
            }
            parent = childs;
        }
        return goalDto;
    }

    public String getGoalName(Goal goal){
        if(goal==null) return "-";
        String goalName = getCnName(goal.getMeasureCode());
        if(!ViewType.isDate(goal.getDimViewType())) goalName = goal.getDimensionValue() + " " + goalName;
        return goalName;
    }

    public GoalDTO compute(Goal goal){
        GoalDTO res = new GoalDTO();
        BeanUtils.copyProperties(goal,res);
        //生成目标名
        String goalName = getCnName(goal.getMeasureCode());
        if(!ViewType.isDate(goal.getDimViewType())) goalName = goal.getDimensionValue() + " " + goalName;
        res.setGoalName(goalName);
        //生成日期类型，日期值字段
        Goal node = goal;
        while (node != null){
            if(ViewType.isDate(node.getDimViewType())){
                ViewType viewType = ViewType.findByInt(node.getDimViewType()).get();
                res.setDateType(viewType.getName());
                res.setDateValue(node.getDimensionValue());
                res.setDateValueId(node.getDimensionValueId());
                res.setDateDimCode(node.getDimensionCode());
                break;
            }else {
                node = goalMapper.selectById(node.getParentId());
            }
        }
        try {
            //目标值
            res.setTargetNum(NumberFormatUtil.format(goal.getTargetNum()));
            //实际值
            BigDecimal realNum = queryRealNum(goal,false);
            res.setRealNum(NumberFormatUtil.format(realNum));
            //差异值
            BigDecimal targetNum = new BigDecimal(goal.getTargetNum().toString());
            BigDecimal diff = realNum.subtract(targetNum).setScale(2, RoundingMode.HALF_UP);
            res.setDiff(NumberFormatUtil.format(diff));
            if (targetNum.compareTo(new BigDecimal(0))!=0){
                //达成率
                BigDecimal achieveRate = realNum.divide(targetNum,4,BigDecimal.ROUND_CEILING);
                res.setAchieveRate(NumberFormatUtil.toPercent(achieveRate));
                //差异率
                if(goal.getDiffRateAlgo() == 1){
                    BigDecimal diffRate = diff.divide(targetNum,4,BigDecimal.ROUND_CEILING);
                    res.setDiffRate(NumberFormatUtil.toPercent(diffRate));
                }else {
                    res.setDiffRate(diff.toString());
                }
                //默认目标达成状态
                if (res.getStatus()==null){
                    if(achieveRate.compareTo(new BigDecimal(1.0)) >= 0) {
                        res.setStatus(1);
                    }else if(achieveRate.compareTo(new BigDecimal(0.9)) < 0){
                        res.setStatus(0);
                    }else{
                        res.setStatus(2);
                    }
                }
            }else{
                res.setAchieveRate("-");
                String diffRate = goal.getDiffRateAlgo() == 0?diff.toString():"-";
                res.setDiffRate(diffRate);
            }
        }catch (Exception e){
            log.error("目标计算异常，异常的目标：{}",goal);
        }finally {
            return res;
        }
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void syncRealNum(){
        log.info("目标管理实际值查询任务，开始执行");
        List<Goal> allGoals = new Goal().selectAll();
        for (Goal goal:allGoals){
            try {
                BigDecimal realNum = queryRealNum(goal,true);
                goal.setRealNum(realNum);
                goal.updateById();
            } catch (Exception e) {
                log.info("目标管理实际值查询任务，查询实际值失败，goal：{}",goal);
            }
        }
        log.info("目标管理实际值查询任务，执行完成");
    }

    private DataSource buildDataSource(Goal goal){
        DataSource dataSource = new DataSource();
        dataSource.setChartType(ChartType.TABLE);
        dataSource.setSourceType(DataSourceType.INDICATOR);
        dataSource.setOperaType(DataOprType.AGGREGATION_TABLE_OPERATION);
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(goal.getSpaceId());
        BaseConfigure baseConfigure = new BaseConfigure();
        baseConfigure.setCode(goal.getMeasureCode());
        List<BaseConfigure> configList = new LinkedList<>();
        configList.add(baseConfigure);
        dataSource.setConfigureList(configList);
        List<Filter> filters = new LinkedList<>();
        while (goal!=null){
            String code = goal.getDimensionCode();
            String valueId = goal.getDimensionValueId();
            if (goal.getDimViewType()==2) valueId = valueId.replace("-","");
            List<Operator> operatorList = new LinkedList<>();
            Operator operator = new Operator();
            if(ViewType.isDate(goal.getDimViewType())){
                operator.setTimeRange(TimeRange.DATE);
            }else{
                operator.setTimeRange(TimeRange.NULL);
            }
            operator.setSqlOprType(SqlOprType.IN);
            operator.setSqlLogicalType(SqlLogicalType.AND);
            List<String> dataList = new LinkedList<>();
            dataList.add(valueId);
            operator.setDataList(dataList);
            operatorList.add(operator);
            Filter filter = new Filter();
            filter.setCode(code);
            filter.setInternal(false);
            filter.setViewType(ViewType.findByInt(goal.getDimViewType()).get());
            filter.setOperatorList(operatorList);
            filters.add(filter);
            if(goal.getParentId()!=null){
                goal = goalMapper.selectOne(Wrappers.<Goal>lambdaQuery().eq(Goal::getId,goal.getParentId()));
            }else {
                break;
            }
        }
        dataSource.setFilterList(filters);
        return dataSource;
    }

    public BigDecimal queryRealNum(Goal goal,boolean isSync){
        if(goal.getRealNum()!=null && !isSync) {
            return goal.getRealNum();
        }
        DataSource dataSource = buildDataSource(goal);
        PageData pageData = null;
        try{
            pageData = chartQueryService.execQuery(dataSource);
        }catch (BadSqlGrammarException e){
            log.error("捕获到sql语法异常,e：{}",e.getStackTrace());
            return new BigDecimal(0);
        }

        List<List<Cell>> lists = pageData.getCellList();
        String realNum = lists.get(0).get(0).getData();

        BigDecimal realValue = NumberFormatUtil.format(realNum);
        goal.setRealNum(realValue);
        goal.updateById();
        return realValue;
    }


    @Override
    @Transactional
    public boolean delete(Long goalId) {
        List<Long> deleteIds = new ArrayList<>();
        List<Long> parentIds = new ArrayList<>();
        deleteIds.add(goalId);
        parentIds.add(goalId);
        while (!parentIds.isEmpty()) {
            List<Long> childIds = new ArrayList<>();
            for (Long id : parentIds) {
                List<Goal> childGoal = goalMapper.selectList(Wrappers.<Goal>lambdaQuery().eq(Goal::getParentId, id));
                for (Goal goal : childGoal) {
                    childIds.add(goal.getId());
                }
                deleteIds.add(id);
            }
            parentIds = childIds;
        }
        goalMapper.deleteBatchIds(deleteIds);
        return true;
    }

    @Override
    public List<GoalDTO> query(GoalQueryVO goalQueryVO) {
        List<Goal> treeGoal = goalMapper.selectList(Wrappers.<Goal>lambdaQuery()
                .eq(Goal::getSpaceId,goalQueryVO.getSpaceId())
                .eq(StringUtil.isNotEmpty(goalQueryVO.getMeasureCode()),Goal::getMeasureCode,goalQueryVO.getMeasureCode())
                .eq(goalQueryVO.getDimViewType()!=null,Goal::getDimViewType,goalQueryVO.getDimViewType())
                .eq(StringUtil.isNotEmpty(goalQueryVO.getDimensionValue()),Goal::getDimensionValue,goalQueryVO.getDimensionValue())
                .isNull(Goal::getParentId)
        );
        LinkedList<GoalDTO> res = new LinkedList<>();
        for (Goal goal:treeGoal){
            res.addFirst(loadAndCompute(goal));
        }
        return res;
    }

    @Autowired
    DimensionManager dimensionManager;

    @Override
    public List<BaseInfo> getDimForSubGoal(Integer goalId) {
        Goal goal = goalMapper.selectById(goalId);
        if(goal == null) return null;
        Integer measureId = measureMapper.selectOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode,goal.getMeasureCode())).getId();
        Set<Integer> measureIdSet = new HashSet<>();
        measureIdSet.add(measureId);
        RelatedSet relatedSet = new RelatedSet();
        relatedSet.setMeasureSet(measureIdSet);
        RelatedSet relatedSet1 = bloodManager.listRelatedSet(relatedSet);
        Set<Integer> dimIds = relatedSet1.getDimensionSet();

        List<Dimension> naturalList = dimensionManager.listNaturalDimension();
        Set<String> naturalDateSet = new HashSet<>();
        for (Dimension dimension:naturalList){
            naturalDateSet.add(dimension.getCode());
        }

        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer,Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        List<Dimension> list = new LinkedList<>();

        for (Integer dimId:dimIds){
            Dimension dimension = allDimensionMap.get(dimId);
            if(!ViewType.isDate(dimension.getViewType())) {
                list.add(dimension);
            }else if (naturalDateSet.contains(dimension.getCode())){
                list.add(dimension);
            }
        }

        ViewType minDate = ViewType.YEAR;
        Set<String> usedDimCodes = new HashSet<>();
        while (goal!=null){
            if (ViewType.isDate(goal.getDimViewType()) && goal.getDimViewType()<minDate.getValue()) minDate = ViewType.findByInt(goal.getDimViewType()).get();
            usedDimCodes.add(goal.getDimensionCode());
            goal = goalMapper.selectById(goal.getParentId());
        }

        //规则：子目标到总目标，出现过的维度不能再出现，时间类型维度只能出现更细粒度
        List<BaseInfo> res = new LinkedList<>();
        for (Dimension dimension:list){
            BaseInfo info = new BaseInfo();
            BeanUtils.copyProperties(dimension,info);
            if(!usedDimCodes.contains(info.getCode())) {
                if(ViewType.isDate(dimension.getViewType())){
                    if (dimension.getViewType()<minDate.getValue()) res.add(info);
                }else {
                    res.add(info);
                }
            }
        }
        return res;
    }
}
