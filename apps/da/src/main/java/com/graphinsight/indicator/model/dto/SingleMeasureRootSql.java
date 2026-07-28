package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.auto.entity.DwTable;
import lombok.Data;

/**
 * Date: 2022/8/8
 * Desc:
 */
@Data
public class SingleMeasureRootSql {

    /**
     * 指标别名
     */
    private String alias;

    /**
     * 列名
     */
    private String column;

    /**
     * 事实表
     */
    private DwTable factTable;

    /**
     * sql
     */
    private String rootSql;

}
