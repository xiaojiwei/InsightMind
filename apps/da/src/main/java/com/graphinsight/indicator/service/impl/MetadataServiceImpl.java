package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.doris.mapper.ColumnsMapper;
import com.graphinsight.indicator.service.MetadataSevice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @Author: lixiaolong
 * @Description: 元数据服务
 * @Date: 2021/11/19
 */
@Service
public class MetadataServiceImpl implements MetadataSevice {

    @Autowired
    private ColumnsMapper columnsMapper;

    @Override
    public List<Columns> listColumns(String shchemaName, String tableName) {
        List<Columns> columns = columnsMapper.selectList(Wrappers.<Columns>lambdaQuery()
                .eq(Columns::getTableSchema, shchemaName)
                .eq(Columns::getTableName, tableName));
        return columns;
    }
}
