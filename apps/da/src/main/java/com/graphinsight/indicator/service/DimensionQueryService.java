package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.DimensionQueryParam;
import com.graphinsight.indicator.model.PageData;

/**
 * 维度查询服务类
 */
public interface DimensionQueryService {

    /**
     * 查询维度下拉列表值
     * @param dimQueryParam
     * @return
     */
    PageData execQueryDimensionValues(DimensionQueryParam dimQueryParam);

    /**
     * 查询维度下拉列表值
     * @param dimQueryParam
     * @param isSyncUpdate
     * @return
     */
    PageData execQueryDimensionValues(DimensionQueryParam dimQueryParam, Boolean isSyncUpdate);

    /**
     * 构建退化维维度表
     * @param dim
     */
    void buildDegenerateTable(Dimension dim);

    /**
     * 返回维度值
     * @param dimCode
     * @return
     */
    Integer getDimCount(String dimCode);

}
