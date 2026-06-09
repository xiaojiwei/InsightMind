package com.graphinsight.indicator.service;

import com.graphinsight.indicator.doris.entity.Columns;

import java.util.List;

public interface MetadataSevice {

    List<Columns> listColumns(String shchemaName, String tableName);
}
