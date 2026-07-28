package com.graphinsight.indicator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.ReloadCache;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.doris.entity.Schemata;
import com.graphinsight.indicator.doris.entity.Tables;
import com.graphinsight.indicator.doris.mapper.ColumnsMapper;
import com.graphinsight.indicator.doris.mapper.SchemataMapper;
import com.graphinsight.indicator.doris.mapper.TablesMapper;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.cache.DwTableCache;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.model.vo.MetadataPageQueryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @Description: Doris元数据控制器
 * @Date: 2021/11/17
 */
@RestController
@RequestMapping("/metadata")
public class MetadataController {

    @Autowired
    private SchemataMapper schemataMapper;
    @Autowired
    private TablesMapper tablesMapper;
    @Autowired
    private ColumnsMapper columnsMapper;
    @Autowired
    private CacheManager cacheManager;

    private static final List<String> IGNORE_SCHEMA = Arrays.asList("eps_test","_statistics_","information_schema");

    @GetMapping("/list/schema")
    @ReloadCache
    public Response<List<Schemata>> listSchema(@RequestParam(value = "traceId",required = false) String traceId){
        List<Schemata> schematas = schemataMapper.selectList(Wrappers.<Schemata>lambdaQuery().notIn(Schemata::getSchemaName,IGNORE_SCHEMA));
        return Response.ok(schematas);
    }

    @PostMapping("/list/tables")
    @ReloadCache
    public Response<PageVO<Tables>> listTables(@Validated @RequestBody MetadataPageQueryVO metadataPageQueryVO){
        Page<Tables> tablesPage = tablesMapper.selectPage(new Page<>(metadataPageQueryVO.getPageNo(), metadataPageQueryVO.getPageSize()),
                Wrappers.<Tables>lambdaQuery()
                        .eq(Tables::getTableSchema, metadataPageQueryVO.getSchemaName())
                        .like(StringUtils.hasLength(metadataPageQueryVO.getTableName()),Tables::getTableName,metadataPageQueryVO.getTableName())
                        .orderByAsc(Tables::getTableName));

        return Response.ok(tablesPage);
    }

    @PostMapping("/list/columns")
    public Response<PageVO<Columns>> listColumns(@Validated @RequestBody MetadataPageQueryVO metadataPageQueryVO){
        Page<Columns> columnsPage = columnsMapper.selectPage(new Page<>(metadataPageQueryVO.getPageNo(), metadataPageQueryVO.getPageSize()),
                Wrappers.<Columns>lambdaQuery()
                        .eq(Columns::getTableSchema,metadataPageQueryVO.getSchemaName())
                        .eq(Columns::getTableName,metadataPageQueryVO.getTableName())
                        .notIn(Columns::getDataType, IndicatorConstant.DATA_TYPE_BLACK_LIST)
                        .like(StringUtils.hasLength(metadataPageQueryVO.getColumnName()),Columns::getColumnName,metadataPageQueryVO.getColumnName())
                        .orderByAsc(Columns::getColumnName));

        return Response.ok(columnsPage);
    }


    @PostMapping("/list/columns/{measCode}")
    @CheckCacheVersion
    public Response<List<Columns>> listDetailTableColumns(@PathVariable("measCode") String measCode){
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        Measure measure = allMeasureCodeMap.get(measCode);
        MeasureCache measureCache = cacheManager.getMeasureCache(measure.getId());
        if (measureCache == null){
            return Response.ok();
        }
        List<Integer> detailDwTableIds = measureCache.getDetailDwTableIds();
        if (CollectionUtils.isEmpty(detailDwTableIds)){
            return Response.ok();
        }

        Integer tableId = detailDwTableIds.get(0);
        DwTableCache dwTableCache = cacheManager.getDwTableCache(tableId);
        if (dwTableCache == null){
            return Response.ok();
        }

        List<Columns> columns = columnsMapper.selectList(
                Wrappers.<Columns>lambdaQuery()
                        .eq(Columns::getTableSchema,dwTableCache.getDwTable().getSchemaName())
                        .eq(Columns::getTableName,dwTableCache.getDwTable().getTableName())
                        .orderByAsc(Columns::getColumnName));

        return Response.ok(columns);
    }
}
