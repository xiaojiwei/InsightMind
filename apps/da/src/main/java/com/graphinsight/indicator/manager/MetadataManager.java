package com.graphinsight.indicator.manager;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.service.IDwColumnService;
import com.graphinsight.indicator.auto.service.IDwTableService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.doris.service.IColumnsService;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/8/17
 * Desc:
 */
@Component
@Slf4j
public class MetadataManager {
    @Autowired
    IColumnsService columnsService;
    @Autowired
    IDwColumnService dwColumnService;
    @Autowired
    IDwTableService dwTableService;

    public List<Columns> listColumns(String schemaName, String tableName) {
        List<Columns> columns = columnsService.list(
                Wrappers.<Columns>lambdaQuery()
                        .eq(Columns::getTableSchema, schemaName)
                        .eq(Columns::getTableName, tableName)
                        .notIn(Columns::getDataType, IndicatorConstant.DATA_TYPE_BLACK_LIST));
        return columns;
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncColumns(Long tableId,List<Columns> columns){
        DwTable dwTable = dwTableService.getById(tableId);
        if (dwTable != null){
            List<DwColumn> dwColumns = columns.stream().map(c -> {
                DwColumn dwColumn = new DwColumn();
                BeanUtils.copyProperties(c, dwColumn);
                dwColumn.setDwTableId(tableId.intValue());
                dwColumn.setName(c.getColumnName());
                dwColumn.setDescription(c.getColumnComment());
                return dwColumn;
            }).collect(Collectors.toList());
            dwColumnService.remove(Wrappers.<DwColumn>lambdaQuery().eq(DwColumn::getDwTableId,tableId));
            dwColumnService.saveBatch(dwColumns);
        }
    }

    @Resource
    DorisQueryManager dorisQueryManager;

    public Map<Integer,List<DwColumn>> listAllColumns(List<DwTable> dwTables) {
        Map<Integer,List<DwColumn>> map = new HashMap<>();
        if (CollectionUtils.isEmpty(dwTables)){
            return map;
        }
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            List<String> schemas = dwTables.stream().map(DwTable::getSchemaName).collect(Collectors.toList());
            List<String> tableNames = dwTables.stream().map(DwTable::getTableName).collect(Collectors.toList());
            Map<String, List<DwTable>> tableMap = dwTables.stream().collect(Collectors.groupingBy(table -> table.getSchemaName() + "." + table.getTableName()));
            List<Columns> columns = dorisQueryManager.listColumns(schemas, tableNames);
            Map<String, List<Columns>> listMap = columns.stream().collect(Collectors.groupingBy(col -> col.getTableSchema() + "." + col.getTableName()));
            tableMap.forEach((k,v) -> {
                List<Columns> columnsList = listMap.get(k);
                if (! CollectionUtils.isEmpty(columnsList)){
                    List<DwColumn> dwColumns = columnsList.stream().map(col -> {
                        DwColumn dwColumn = new DwColumn();
                        dwColumn.setName(col.getColumnName());
                        dwColumn.setDescription(col.getColumnComment());
                        dwColumn.setDataType(col.getDataType());
                        dwColumn.setDwTableId(v.get(0).getId());
                        return dwColumn;
                    }).collect(Collectors.toList());
                    map.put(v.get(0).getId(),dwColumns);
                }
            });
        } catch (Exception e) {
            log.warn("从 Doris 加载列信息失败（Doris 不可用），将使用空列信息: {}", e.getMessage());
        }
        return map;
    }

}
