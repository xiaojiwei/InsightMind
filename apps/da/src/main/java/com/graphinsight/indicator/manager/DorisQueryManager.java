package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.doris.service.IColumnsService;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Date: 2022/3/10
 * Desc:
 */
@Service
@Slf4j
public class DorisQueryManager {
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    MeasureManager measureManager;
    @Autowired
    ChartQueryService chartQueryService;
    @Resource
    JdbcTemplate secondJdbcTemplate;
    @Resource
    IColumnsService columnsService;
    @Resource
    CacheManager cacheManager;

    public List<Columns> listColumns(List<String> schemas, List<String> tableNames) {
        if (CollectionUtils.isEmpty(schemas) || CollectionUtils.isEmpty(tableNames)) {
            return Collections.EMPTY_LIST;
        }
        List<String> sps = schemas.stream().map(s -> "'" + s + "'").collect(Collectors.toList());
        List<String> tps = tableNames.stream().map(s -> "'" + s + "'").collect(Collectors.toList());
        String sp = sps.stream().collect(Collectors.joining(","));
        String tp = tps.stream().collect(Collectors.joining(","));
        String sql = "select `COLUMN_COMMENT`,`COLUMN_NAME`,`COLUMN_TYPE`,`TABLE_NAME`,`TABLE_SCHEMA` from `information_schema`.`columns` where table_schema in ( " + sp + " ) and table_name in ( " + tp + " ) ";
        List<Map<String, Object>> maps = secondJdbcTemplate.queryForList(sql);
        if (CollectionUtils.isEmpty(maps)){
            return Collections.EMPTY_LIST;
        }
        List<Columns> result = maps.stream().map(map -> {
            Columns columns = new Columns();
            columns.setColumnComment(map.get("COLUMN_COMMENT") == null ? null : map.get("COLUMN_COMMENT").toString());
            columns.setColumnName(map.get("COLUMN_NAME").toString());
            columns.setColumnType(map.get("COLUMN_TYPE").toString());
            columns.setTableName(map.get("TABLE_NAME").toString());
            columns.setTableSchema(map.get("TABLE_SCHEMA").toString());
            return columns;
        }).collect(Collectors.toList());
        return result;
    }

    public DataSource buildDataSource(Long spaceId, Set<String> measCodes, Set<String> dimCodes, Ratio ratio, List<Filter> filters) {
        DataSource dataSource = new DataSource();
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(spaceId);
        dataSource.setChartType(ChartType.LINE);
        dataSource.setUsername("system");
        List<BaseConfigure> configureList = new LinkedList<>();
        List<BaseConfigure> measConfigs = measCodes.stream().map(code -> {
            BaseConfigure measureConfigure = new BaseConfigure();
            measureConfigure.setCode(code);
            if (ratio != null) {
                measureConfigure.getRatioList().add(ratio);
            }
            return measureConfigure;
        }).collect(Collectors.toList());
        configureList.addAll(measConfigs);

        List<BaseConfigure> dimConfigs = dimCodes.stream().map(code -> {
            BaseConfigure dimensionConfigure = new BaseConfigure();
            dimensionConfigure.setCode(code);
            return dimensionConfigure;
        }).collect(Collectors.toList());
        configureList.addAll(dimConfigs);
        List<Filter> filterList = new ArrayList<>();
        filters.forEach(filter -> {
            Filter clone = (Filter) SerializationUtils.clone(filter);
            filterList.add(clone);
        });
        dataSource.getFilterList().addAll(filterList);
        dataSource.setConfigureList(configureList);
        dataSource.setPageable(false);
        return dataSource;
    }

    private DataSource buildDataSource(Long spaceId, String measCode, String dimCode, Ratio ratio, List<Filter> filters, List<DimWithValues> dimGroup) {
        DataSource dataSource = new DataSource();
        dataSource.setUsername("system");
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(spaceId);
        dataSource.setChartType(ChartType.TABLE);
        dataSource.setPageNo(1);
        dataSource.setPageSize(2000);

        List<BaseConfigure> configureList = new LinkedList<>();
        BaseConfigure dimensionConfigure = new BaseConfigure();
        dimensionConfigure.setCode(dimCode);
        BaseConfigure measureConfigure = new BaseConfigure();
        measureConfigure.setCode(measCode);
        if (ratio != null) {
            //指标同环比配置
            measureConfigure.getRatioList().add(ratio);
            String alias = buildMeasureAlias(measCode,ratio);
            measureConfigure.setAlias(alias);
        }
        dimGroup.forEach(e->{
            BaseConfigure dimConfigure = new BaseConfigure();
            dimConfigure.setCode(e.getDimensionCode());
            configureList.add(dimConfigure);
        });


        configureList.add(measureConfigure);
        configureList.add(dimensionConfigure);
        dataSource.getFilterList().addAll(filters);
        dataSource.setConfigureList(configureList);
        return dataSource;
    }

    //指标同环比查询生成别名
    public String buildMeasureAlias(String measCode, Ratio ratio){
        try {
            StringBuilder alias = new StringBuilder();
            Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();
            Measure measure = allMeasureCodeMap.get(measCode);
            alias.append(measure.getCnName());
            alias.append("_");
            alias.append(ratio.getRatioType().getDesc());
            alias.append("增长率");
            return alias.toString();
        }catch (Exception e){
            log.error("构造指标同环比别名异常");
        }
        return "指标预警同环比查询";
    }

    public PageData ratioQuery(Long spaceId, String measCode, String dimCode, Ratio ratio, List<Filter> filters, List<DimWithValues> dimGroup) {
        DataSource dataSource = buildDataSource(spaceId, measCode, dimCode, ratio, filters, dimGroup);

        try {

            ObjectMapper jsonMapper = new ObjectMapper();
            log.info("xq，dataSource:{}", jsonMapper.writeValueAsString(dataSource));
        }catch (Exception e){

        }
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        PageData pageData = chartQueryService.execQuery(dataSource);
        return pageData;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String runTest(DwTable dwTable, List<MeasureApplication> measureApplications) {
        String sql = measureManager.buildeOriginMeasureQuerySql(dwTable, measureApplications);
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        jdbcTemplate.execute(sql);
        return sql;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public <T> T exec(String sql, Class<T> clazz) {
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        T t = jdbcTemplate.queryForObject(sql, clazz);
        return t;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void execTest(String sql) {
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        jdbcTemplate.execute(sql);
    }


    public void sortTest(Long num) {
        long start = System.currentTimeMillis();
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        String sql = "select visit_num,city_name,city_code from eps_service.ads_asm_customer_connect_df limit " + num;
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
        long end = System.currentTimeMillis();
        log.info("查询耗时:{}ms", end - start);

        List<Demo> list = new ArrayList<>();
        maps.forEach(map -> {
            Long visit_num = map.get("visit_num") == null ? 0 : Long.valueOf(map.get("visit_num").toString());
            String city_name = map.get("city_name") == null ? "" : map.get("city_name").toString();
            String city_code = map.get("city_code") == null ? "" : map.get("city_code").toString();
            Demo demo = new Demo();
            demo.setCity_code(city_code);
            demo.setCity_name(city_name);
            demo.setVisit_num(visit_num);
            list.add(demo);
        });

        long start1 = System.currentTimeMillis();

        list.stream().sorted(Comparator.comparing(Demo::getVisit_num).reversed()).collect(Collectors.toList());
        end = System.currentTimeMillis();
        log.info("排序耗时:{}ms", end - start1);
        log.info("总耗时:{}ms", end - start);


    }

    @Data
    class Demo {
        Long visit_num;
        String city_name;
        String city_code;
    }

}
