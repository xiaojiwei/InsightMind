package com.graphinsight.indicator.service;

import com.graphinsight.indicator.enums.DataSetType;

public interface SqlQueryStrategy {

    /**
     * 根据查询的数据类型获取数据查询服务
     * @param dataSetType
     * @return
     */
    DataQueryService getSqlQueryMethod(DataSetType dataSetType);

}
