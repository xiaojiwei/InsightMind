package com.graphinsight.indicator.controller;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.TSuperAdmin;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.mapper.TSpaceMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.vo.AiContextInfoVo;
import com.graphinsight.indicator.model.vo.AiSplitTextVo;
import com.graphinsight.indicator.model.vo.DataQueryVO;
import com.graphinsight.indicator.model.vo.RelatedCodeSet;
import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.util.IDaaSValidateUtilOntest;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.TempThreadLocalUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/agent")
@Slf4j
public class SmartAgentController {


    @Autowired
    private IndicatorService indicatorService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    ChartQueryService chartQueryService;

    @Autowired
    KeyWord2Service keyWord2Service;

    @Autowired
    DimMeasRelationController dimMeasRelationController;

    static Map<String, String> measureMap;

    static Map<String, String> dateDimensionMap;

    static Map<String, String> dimensionMap;

    static {
        measureMap = new HashMap<>();
        measureMap.put("净锁单量", "MEAS_d93e71a5fde84f968e3e2e6696297f6c");
        measureMap.put("试驾量", "MEAS_9a602790a4b9426398d35b41c44ff23b");

        //自然日期
        dateDimensionMap = new HashMap<>();
        dateDimensionMap.put("月", "DIM_4e41a99d4b964cc0a66dd7c02356c473");
        dateDimensionMap.put("年", "DIM_0a61b0022ae241e7a400399e97dc1e63");
        dateDimensionMap.put("周", "DIM_72d474475d53466b935b850bbefad6c7");
        dateDimensionMap.put("日", "DIM_a2ad283d29b5446ba4b5ae757231a0b3");

        //一般类型维度
        dimensionMap = new HashMap<>();
        dimensionMap.put("车型", "DIM_ca691958ba3147699d1dd8dc0f724113");
        dimensionMap.put("城市", "DIM_4745b16077644996917bb9d92c636b3d");
        dimensionMap.put("省份", "DIM_2448e67c8ebf4917846fbc6ae3a0b7b7");
    }

    @Resource
    CacheManager cacheManager;

    public static String replaceKey(String key) {

        if (key == null) {
            return null;
        }

        if (key.indexOf("销量") >= 0) {
            return "净锁单量";
        }

        if (key.indexOf("帝都") >= 0) {
            return "北京";
        }

        if (key.indexOf("魔都") >= 0) {
            return "上海";
        }


        return key;


    }


    private Set<String> getMeasureCode(List<String> measureNames) {
        //获取全部指标
        List<Measure> allMeasureList = this.indicatorService.listAllMeasure();
        //处理指标数据
        Set<String> measureCode = new HashSet<>();
        for (String measureName : measureNames) {
            measureName = replaceKey(measureName);
            //先走人工
            if (measureMap.containsKey(measureName)) {
                measureCode.add(measureMap.get(measureName));
                continue;
            }
            //再去匹配
            for (Measure measure : allMeasureList) {
                if (measure.getName().contains(measureName)) {
                    measureCode.add(measure.getCode());
                    break;
                }
            }
        }
        return measureCode;
    }

    private Set<String> getDimensionCode(List<String> dimensionNames, Set<String> dimensionCodes, Set<String> measureCodes) {
        Set<String> res = new HashSet<>();
        RelatedCodeSet set = new RelatedCodeSet();
        set.setMeasureSet(measureCodes);
        set.setDimensionSet(dimensionCodes);
        Response<RelatedCodeSet> response = dimMeasRelationController.listRelatedSet(set);
        Set<String> dimCodeSet = response.getData().getDimensionSet();
        Map<String, Dimension> dimensions = new HashMap<>();
        //获取维度
        List<Dimension> allDimensionList = this.indicatorService.listAllDimension();
        for (Dimension dimension : allDimensionList) {
            if (dimCodeSet.contains(dimension.getCode())) {
                dimensions.put(dimension.getCode(), dimension);
            }
        }

        for (String dimensionName : dimensionNames) {
            if (dimensionMap.containsKey(dimensionName)) {
                if (dimensions.containsKey(dimensionMap.get(dimensionName))) {
                    res.add(dimensionMap.get(dimensionName));
                    continue;
                }
            }
            if (dateDimensionMap.containsKey(dimensionName)) {
                if (dimensions.containsKey(dateDimensionMap.get(dimensionName))) {
                    res.add(dateDimensionMap.get(dimensionName));
                    continue;
                }
            }
            for (String key : dimensions.keySet()) {
                if (dimensions.get(key).getName().contains(dimensionName)) {
                    res.add(dimensions.get(key).getCode());
                    break;
                }
            }
        }
        return res;
    }


    private List<Filter> getFilters(Map<String, List<String>> queryFilter, Set<String> dimensionCode, Set<String> measureCode) {
        if (queryFilter == null || queryFilter.size() == 0) {
            return new LinkedList<>();
        }
        List<Filter> res = new LinkedList<>();
        Set<String> dateDims = new HashSet<>();
        List<String> dims = new LinkedList<>();
        for (String key : queryFilter.keySet()) {
            if (dateDimensionMap.containsKey(key)) {
                dateDims.add(key);
            } else {
                dims.add(key);
            }
        }

        //分开处理日期类型筛选器和一般维度筛选器
        if (dateDims.contains("年") && dateDims.contains("月")) {
            Filter dateFilter = new Filter();
            dateFilter.setViewType(ViewType.MONTH);
            dateFilter.setInternal(true);
            dateFilter.setCode(dateDimensionMap.get("月"));
            Operator operator = new Operator();
            operator.setSqlOprType(SqlOprType.IN);

            List<String> yearStr = queryFilter.get("年");
            List<String> monthStr = queryFilter.get("月");
            if ((yearStr != null && yearStr.size() > 0) && (monthStr != null && monthStr.size() > 0)) {
                Integer year = getNumber(yearStr.get(0));
                Integer monthNum = getNumber(monthStr.get(0));
                String month = monthNum >= 10 ? String.valueOf(monthNum) : "0" + String.valueOf(monthNum);
                String data = year + month;
                operator.getDataList().add(data);
                operator.setTimeRange(TimeRange.DATE);
                dateFilter.getOperatorList().add(operator);
                res.add(dateFilter);
            }

        } else if (dateDims.contains("年")) {

            Filter dateFilter = new Filter();
            dateFilter.setViewType(ViewType.YEAR);
            dateFilter.setInternal(true);
            dateFilter.setCode(dateDimensionMap.get("年"));
            Operator operator = new Operator();
            operator.setSqlOprType(SqlOprType.IN);
            List<String> yearStr = queryFilter.get("年");
            for (String s : yearStr) {
                Integer year = getNumber(s);
                String data = year + "";
                operator.getDataList().add(data);
                break;
            }
            operator.setTimeRange(TimeRange.DATE);
            dateFilter.getOperatorList().add(operator);
            dimensionCode.add(dateFilter.getCode());
            res.add(dateFilter);

        } else if (dateDims.contains("月")) {
            Filter dateFilter = new Filter();
            dateFilter.setViewType(ViewType.MONTH);
            dateFilter.setInternal(true);
            dateFilter.setCode(dateDimensionMap.get("月"));
            Operator operator = new Operator();
            operator.setSqlOprType(SqlOprType.IN);
            LocalDate localDate = LocalDate.now();
            Integer year = localDate.getYear();
            List<String> monthStr = queryFilter.get("月");
            for (String s : monthStr) {
                Integer monthNum = getNumber(s);
                String month = monthNum >= 10 ? String.valueOf(monthNum) : "0" + String.valueOf(monthNum);
                String data = year + month;
                operator.getDataList().add(data);
                break;
            }

            operator.setTimeRange(TimeRange.DATE);
            dateFilter.getOperatorList().add(operator);
            dimensionCode.add(dateFilter.getCode());
            res.add(dateFilter);
        }


        Map<String, List<String>> dimAndValue = new HashMap<>();
        for (String s : dims) {

            List<String> dimNames = new LinkedList<>();
            dimNames.add(this.replaceKey(s));
            List<String> dimCodes = new ArrayList<>(getDimensionCode(dimNames, dimensionCode, measureCode));
            if (!dimCodes.isEmpty()) {
                String dimCode = dimCodes.get(0);
                List<String> valueStr = queryFilter.get(s);
                List<String> values = new LinkedList<>();
                for (String value : valueStr) {
                    value = processProvinceAndCity(dimCode, value);
                    String hql = "select dav From DimAllValues as dav where dav.dimCode = " + "'" + dimCode + "'" + "and dav.valueText like '%" + value + "%'";
                    Query query = this.entityManager.createQuery(hql);
                    List<DimAllValues> list = query.getResultList();
                    if (null != list && list.size() > 0) {
                        List<String> valueList = list.stream().map(e -> e.getValueKey()).collect(Collectors.toList());
                        values.addAll(valueList);
                    }
                }
                dimAndValue.put(dimCode, values);
                dimensionCode.add(dimCode);
            }
        }
        for (String code : dimAndValue.keySet()) {
            Filter filter = new Filter();
            filter.setCode(code);
            List<Operator> operatorList = new LinkedList<>();
            Operator operator = new Operator();
            List<String> dataList = dimAndValue.get(code);
            operator.setDataList(dataList);
            operator.setSqlOprType(SqlOprType.IN);
            operatorList.add(operator);
            filter.setOperatorList(operatorList);
            res.add(filter);
        }

        return res;
    }

    private Response query(DataQueryVO dataQueryVO) {
        Set<String> measureCode = getMeasureCode(dataQueryVO.getMeasure());
        Set<String> dimensionCode = getDimensionCode(dataQueryVO.getDimension(), new HashSet<>(), measureCode);
        List<Filter> filterList = getFilters(dataQueryVO.getFilter(), new HashSet<>(dimensionCode), measureCode);
        User user = UserThreadLocalUtil.get();
        List<Long> spaceId = getSpaceId(user, measureCode, dimensionCode);


        //构造查数对象
        DataSource dataSource = new DataSource();
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(4l);
        dataSource.setChartType(ChartType.TABLE);
        dataSource.setUsername(user.getUsername());
        dataSource.setPageNo(1);
        dataSource.setPageSize(dataQueryVO.getLimit());

        List<BaseConfigure> configureList = new LinkedList<>();
        for (String code : measureCode) {
            BaseConfigure measureConfigure = new BaseConfigure();
            if ("asc".equals(dataQueryVO.getSort())) {
                Order order = new Order();
                order.setSortType(SortType.ASC);
                measureConfigure.setOrder(order);
            }
            if ("desc".equals(dataQueryVO.getSort())) {
                Order order = new Order();
                order.setSortType(SortType.DESC);
                measureConfigure.setOrder(order);
            }
            measureConfigure.setCode(code);
            configureList.add(measureConfigure);
        }
        for (String code : dimensionCode) {
            BaseConfigure dimensionConfigure = new BaseConfigure();
            com.graphinsight.indicator.auto.entity.Dimension dimension = dimensionMapper.selectByCode(code);
            if (StringUtils.isBlank(dataQueryVO.getSort())) {
                if (ViewType.isDate(dimension.getViewType())) {
                    Order order = new Order();
                    order.setSortType(SortType.ASC);
                    dimensionConfigure.setOrder(order);
                }
            }
            dimensionConfigure.setCode(code);
            configureList.add(dimensionConfigure);
        }

        dataSource.setConfigureList(configureList);
        dataSource.getFilterList().addAll(filterList);
        postHandle(dataSource);
        PageData pageData = chartQueryService.execQuery(dataSource);
        // todo 临时处理一下
        for (Filter filter : dataSource.getFilterList()) {
            for (Operator operator : filter.getOperatorList()) {
                if (operator.getSqlOprType() == SqlOprType.BETEEN && operator.getDataList().size() == 1) {
                    operator.getDataList().addAll(operator.getDataList());
                }
            }
        }
        pageData.setDataSource(dataSource);
        pageData.setBaseInfoMap(buildBaseInfo(dataSource));
        pageData.setSpaceId(spaceId);
        pageData.setLoginUserName(user.getUsername());
        pageData.setFromDeveloper("xueqi");
        return Response.ok(pageData);
    }


    @Autowired
    UserMapper userMapper;

    @Autowired
    IDaaSValidateService iDaaSValidateService;

    @PostMapping("/query/datasource/frontend")
    public Response queryDatasource(@RequestBody DataQueryVO dataQueryVO, HttpServletRequest request) {
        try {
            String userName = iDaaSValidateService.validate(request.getHeader("Authorization"),"query:api");
            dataQueryVO.setUsername(userName);
        }catch (Exception e){
            log.error("鉴权失败",e);
            return Response.error("鉴权失败"+e.getMessage());
        }
        if (dataQueryVO.getUsername() != null) {
            User user = userMapper.selectByUsername(dataQueryVO.getUsername());
            UserThreadLocalUtil.set(user);
        }

        try {
            String word = dataQueryVO.getWord();
            PageData pageData = keyWord2Service.doAction2(dataQueryVO, word, true);
            return Response.ok(pageData.getDataSource());
        } catch (Exception e) {
            Response.error("nlp方式查询报错：" + e.getMessage() + "\n");
            log.error("nlp方式查询报错", e);
        }
        return Response.ok();
    }

    @Autowired
    ITSuperAdminService itSuperAdminService;
    private boolean isSuperAdmin(String username){
        boolean res = false;
        try {
            List<TSuperAdmin> list = itSuperAdminService.list();
            Set<String> names = list.stream().map(TSuperAdmin::getEmpCode).collect(Collectors.toSet());
            if (names.contains(username)){
                res = true;
            }
        }catch (Exception e){
            log.error("超级管理员查询失败",e);
        }
        return res;
    }


    @PostMapping("/query/data/frontend")
    public Response queryData(@RequestBody DataSource dataSource, HttpServletRequest request){
        try {
            String userName = iDaaSValidateService.validate(request.getHeader("Authorization"),"query:api");
            dataSource.setUsername(userName);
        }catch (Exception e){
            log.error("鉴权失败",e);
            return Response.error("鉴权失败"+e.getMessage());
        }
        Long begin = System.currentTimeMillis();
        //获取用户名
        String userName = dataSource.getUsername();
        if (StringUtil.isEmpty(userName)) {
            userName = UserThreadLocalUtil.getUserName();
            dataSource.setUsername(userName);
        }

        PageData pageData = null;
        Response<PageData> response = null;

        Long spaceId = dataSource.getSpaceId();
        if(null == spaceId){
            spaceId = 4L;
        }
        if (null == spaceId && !isSuperAdmin(userName)) {

            log.error("调用异常:", "spaceId is null");
            response = Response.error("查询失败,spaceId为null，请联系开发.");

            response.setErrorType(ResponseErrorType.SYSTEM);
            response.setErrorOwner("doulinxu1");//系统级错误先指定开发

            response.setErrorMessage("spaceId is null");

        } else {

            try {

                DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
                //正常查询都走直查
                dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
                pageData = this.chartQueryService.execQuery(dataSource);

                pageData.setCost(System.currentTimeMillis() - begin);
                response = Response.ok("查询成功", pageData);

                this.chartQueryService.addQueryLog(dataSource, pageData);
                pageData.setLoginUserName(userName);

            } catch (Exception ex) {
                ex.printStackTrace();
                log.error("调用异常:",ex);
                response = Response.error("查询失败");

                if (ex instanceof IllegalArgumentException) {
                    String owenr = String.valueOf(TempThreadLocalUtil.get("owner"));
                    response.setErrorOwner(owenr);
                    response.setErrorType(ResponseErrorType.DATA);
                } else {
                    response.setErrorType(ResponseErrorType.SYSTEM);
                    response.setErrorOwner("xiaojiwei");//系统级错误先指定开发
                }

                response.setErrorStackTrace(ex.getStackTrace());
                response.setErrorMessage(ex.toString());
            }

        }

        return response;
    }

    @PostMapping("/query")
    public Response queryData(@RequestBody DataQueryVO dataQueryVO) {

        String error = "";
        if (dataQueryVO.getUsername() != null) {
            User user = userMapper.selectByUsername(dataQueryVO.getUsername());
            UserThreadLocalUtil.set(user);
        }
        // todo 临时替换下顺序
        try {
            String word = dataQueryVO.getWord();
            PageData pageData = keyWord2Service.doAction2(dataQueryVO, word, dataQueryVO.getIsData());
            if (pageData.getCellList().isEmpty()) {
                return Response.ok(pageData);
            } else {
                return Response.ok(pageData);
            }

        } catch (Exception e) {

            error += "nlp方式查询报错：" + e.getMessage() + "\n";
            log.error("nlp方式查询报错", e);
        }

        //return Response.error("后端查询失败，errorMsg: \n" + error);
        return Response.ok();
    }


    @PostMapping("/queryRecommend")
    public Response queryRecommend(@RequestBody DataSource dataSource) {
        PageData pageData = new PageData();
        try {
            dataSource.getConfigureList().removeIf(baseInfo -> baseInfo.getCode().contains("DIM"));
            List<String> measureList = dataSource.getConfigureList().stream().map(BaseConfigure::getCode).collect(Collectors.toList());
            pageData = keyWord2Service.doRecommendData(dataSource);
            return Response.ok(pageData);
        } catch (Exception e) {
            String error = "推荐查询方式查询报错：" + e.getMessage() + "\n";
            log.error("推荐方式查询报错", e);
            return Response.ok(pageData);
        }
    }

    //特殊处理：只有一个指标时，默认带一个时间维度，日维度取最近15天
    private void postHandle(DataSource dataSource) {
        List<BaseConfigure> configureList = dataSource.getConfigureList();
        if (configureList.size() == 1 && configureList.get(0).getCode().startsWith("MEAS")) {
            String measureCode = configureList.get(0).getCode();
            Set<String> measureCodeSet = new HashSet<>();
            measureCodeSet.add(measureCode);
            RelatedCodeSet set = new RelatedCodeSet();
            set.setMeasureSet(measureCodeSet);
            Response<RelatedCodeSet> response = dimMeasRelationController.listRelatedSet(set);
            Set<String> dimCodeSet = response.getData().getDimensionSet();
            List<Dimension> allDimensionList = this.indicatorService.listAllDimension();
            for (Dimension dimension : allDimensionList) {
                if (dimCodeSet.contains(dimension.getCode()) && Objects.equals(dimension.getViewType().getValue(), ViewType.MONTH.getValue())) {
                    BaseConfigure baseConfigure = new BaseConfigure();
                    baseConfigure.setCode(dimension.getCode());
                    Order order = new Order();
                    order.setSortType(SortType.DESC);
                    baseConfigure.setOrder(order);
                    configureList.add(baseConfigure);
                    return;
                }
                if (dimCodeSet.contains(dimension.getCode()) && Objects.equals(dimension.getViewType().getValue(), ViewType.YEAR.getValue())) {
                    BaseConfigure baseConfigure = new BaseConfigure();
                    baseConfigure.setCode(dimension.getCode());
                    Order order = new Order();
                    order.setSortType(SortType.DESC);
                    baseConfigure.setOrder(order);
                    configureList.add(baseConfigure);
                    return;
                }
                if (dimCodeSet.contains(dimension.getCode()) && Objects.equals(dimension.getViewType().getValue(), ViewType.DAY.getValue())) {
                    BaseConfigure baseConfigure = new BaseConfigure();
                    baseConfigure.setCode(dimension.getCode());
                    Order order = new Order();
                    order.setSortType(SortType.DESC);
                    baseConfigure.setOrder(order);
                    configureList.add(baseConfigure);
                    dataSource.getFilterList().add(getDateFilter(dimension.getCode()));
                    return;
                }
            }
        }
    }

    @Autowired
    MeasureMapper measureMapper;

    @Autowired
    DimensionMapper dimensionMapper;

    private Map<String, Object> buildBaseInfo(DataSource dataSource) {
        Map<String, Object> baseInfoMap = new HashMap<>();
        List<BaseConfigure> configures = dataSource.getConfigureList();
        for (BaseConfigure baseConfigure : configures) {
            if (baseConfigure.getCode().startsWith("MEAS")) {
                baseInfoMap.put(baseConfigure.getCode(), measureMapper.selectByCode(baseConfigure.getCode()));
            } else {
                baseInfoMap.put(baseConfigure.getCode(), dimensionMapper.selectByCode(baseConfigure.getCode()));
            }
        }
        List<Filter> filters = dataSource.getFilterList();
        for (Filter filter : filters) {
            baseInfoMap.put(filter.getCode(), dimensionMapper.selectByCode(filter.getCode()));
        }
        return baseInfoMap;
    }

    private Filter getDateFilter(String dimCode) {
        DateTimeFormatter format = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTime curDay = DateTime.now();
        String cur = format.print(curDay);
        String base = format.print(curDay.plusDays(-15));
        Filter filter = new Filter();
        filter.setCode(dimCode);
        List<Operator> operatorList = new LinkedList<>();
        Operator operator = new Operator();
        operator.setTimeRange(TimeRange.DATE);
        List<String> dataList = new LinkedList<>();
        dataList.add(base);
        dataList.add(cur);
        operator.setDataList(dataList);
        operator.setSqlOprType(SqlOprType.BETEEN);
        operatorList.add(operator);
        filter.setViewType(ViewType.DAY);
        filter.setInternal(true);
        filter.setOperatorList(operatorList);
        return filter;
    }

    private Integer getNumber(String s) {
        Integer num = 0;
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(s);
        String number = m.replaceAll("").trim();
        num = Integer.valueOf(number);
        return num;
    }

    private String processProvinceAndCity(String dimCode, String s) {

        s = s.replace("市", "");
        s = s.replace("省", "");

        return s;
    }

    @Autowired
    SpaceEmployeeService spaceEmployeeService;

    @Autowired
    SpaceService spaceService;

    @Autowired
    ITSuperAdminService superAdminService;

    @Autowired
    TSpaceMapper tSpaceMapper;

    private List<Long> getSpaceId(User user, Set<String> measureCodes, Set<String> dimensionCodes) {
        List<Long> res = new LinkedList<>();
        List<TSuperAdmin> superAdmins = superAdminService.list(Wrappers.<TSuperAdmin>lambdaQuery().eq(TSuperAdmin::getEmpCode, user.getUsername()));
        Set<String> adminUserNames = superAdmins.stream().map(TSuperAdmin::getEmpCode).collect(Collectors.toSet());
        List<Long> spaceIds = null;
        if (adminUserNames.contains(user.getUsername())) {
            return res;
        } else {
            res.add(4l);
            /*
            Query query = entityManager.createQuery("select SE from SpaceEmployee as SE where SE.employeeCode = " + "'" + user.getUsername() + "'");
            List<SpaceEmployee> spaceEmployeeList = query.getResultList();
            spaceIds = spaceEmployeeList.stream().map(e->e.getSpace().getId()).collect(Collectors.toList());
             */
            return res;
        }

        /*

        for (Long spaceId : spaceIds){
            Set<AuthElement> authElementBySpaceId = spaceService.getAuthElementBySpaceId(spaceId, user.getUsername());
            Set<String> codes = authElementBySpaceId.stream().map(e->e.getCode()).collect(Collectors.toSet());
            boolean flag = true;
            for (String measureCode : measureCodes){
                if (!codes.contains(measureCode)){
                    flag = false;
                }
            }
            for (String dimensionCode : dimensionCodes){
                if (!codes.contains(dimensionCode)){
                    flag = false;
                }
            }
            if (flag){
                res.add(spaceId);
            }
        }

         */
    }

    @Autowired
    private CacheReloadScheduleTaskService cacheReloadScheduleTaskService;

    @PostMapping(value = "/dimvalue/build")
    public Response buildAllDimData() {

        Response response = null;

        try {
            this.cacheReloadScheduleTaskService.buildAllDimData();
            response = Response.ok("查询成功");

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @GetMapping(value = "/dimvalue/build/{dimName}")
    public Response buildDimData(@PathVariable("dimName") String dimName) {
        Set<String> dimNames = new HashSet<>();
        dimNames.add(dimName);
        Response response = null;

        try {
            this.cacheReloadScheduleTaskService.buildDimData(dimNames);
            response = Response.ok("查询成功");
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;
    }

    @PostMapping(value = "/ai/split/query")
    public Response splitText(@RequestBody AiSplitTextVo aiSplitTextVo) {

        return Response.ok(keyWord2Service.getSplitWordInfo(aiSplitTextVo));
    }

    @Autowired
    private AiSearchService aiSearchService;

    @PostMapping(value = "/gent/ai/search/history/save")
    public Response contextSave(@RequestBody AiContextInfoVo aiContextInfoVo) {
        aiSearchService.contextSave(aiContextInfoVo);
        return Response.ok();
    }

    @GetMapping(value = "/ai/data/collect")
    public Response dataUserCollect(@RequestParam(defaultValue = "0", value = "pageNum") Integer offset,
                                    @RequestParam(defaultValue = "10", value = "limit") Integer limit) {

        return Response.ok(aiSearchService.userCollect(offset, limit));
    }

    @GetMapping(value = "/ai/search/info/recommend")
    public Response searchInfoRecommend() {
        return Response.ok(aiSearchService.searchInfoRecommend());
    }

    @GetMapping(value = "/ai/data/search/operate")
    public Response dataUserCollectOperate(@RequestParam("searchId") Integer searchId, @RequestParam("opType") Integer opType,@RequestParam("contentCode") String contentCode) {
        aiSearchService.userCollectOperate(searchId, opType, contentCode);
        return Response.ok();
    }

    @GetMapping(value = "/ai/search/histtory")
    public Response searchHistory(@RequestParam(defaultValue = "0", value = "pageNum") Integer offset,
                                  @RequestParam(defaultValue = "10", value = "limit") Integer limit) {

        return Response.ok(aiSearchService.userHistory(offset, limit));
    }

    @GetMapping(value = "/ai/search/hot/{viewType}")
    public Response searchHot(@PathVariable("viewType") Integer viewType, @RequestParam(defaultValue = "0", value = "pageNum") Integer offset,
                              @RequestParam(defaultValue = "10", value = "limit") Integer limit) {

        return Response.ok(aiSearchService.searchHot(viewType, offset, limit));
    }

    @GetMapping(value = "/ai/search/histtory/del/{id}")
    public Response userHistoryDel(@PathVariable("id") Integer seatchId) {
        aiSearchService.userHistoryDel(seatchId);
        return Response.ok();
    }

    @GetMapping(value = "/like/query/{keywords}")
    public Response likeQuery(@PathVariable("keywords") String keywords) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Collection<com.graphinsight.indicator.auto.entity.Dimension> dimensions = metadataCache.getAllDimensionMap().values();
        Collection<com.graphinsight.indicator.auto.entity.Measure> measures = metadataCache.getAllMeasureMap().values();
        List<Object> res = new ArrayList<>();
        for (com.graphinsight.indicator.auto.entity.Measure measure : measures) {
            String cnName = measure.getCnName();
            if (cnName.contains(keywords) && res.size() < 10) {
                res.add(measure);
            }
        }

        if (res.size() < 10) {
            for (com.graphinsight.indicator.auto.entity.Dimension dimension : dimensions) {
                String cnName = dimension.getCnName();
                if (cnName.contains(keywords) && res.size() < 10) {
                    res.add(dimension);
                }
            }
        }
        return Response.ok(res);
    }


}
