package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.DataSourceType;
import com.graphinsight.indicator.enums.ExecutorPlatform;
import lombok.Data;

@Data
public class CountInfo {

    private String cntSql;

    /**
     * 执行引擎
     */
    private ExecutorPlatform platform;

    /**
     * 数据源类型
     */
    private DataSourceType sourceType;

    /**
     * 数据库名称
     */
    private String dbName;
    /**
     * 数据库类型
     */
    private Integer dbType;

    private Integer pageSize;

    private Integer pageNo;

    private Integer count;

    private String userName;

}