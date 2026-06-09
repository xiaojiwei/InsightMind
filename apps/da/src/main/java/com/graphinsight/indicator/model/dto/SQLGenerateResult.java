package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.LinkedList;

/**
 * Author: lixiaolong
 * Date: 2022/8/8
 * Desc:
 */
@Data
public class SQLGenerateResult {

    /**
     * root层sql
     */
    private String rootSql;

    /**
     * 宽表层sql
     */
    private String largeWideSql;

    /**
     * 结果层sql
     */
    private String resultSql;


    private LinkedList<SingleMeasureRootSql> singleMeasureRootSqlLinkedList = new LinkedList<>();

}
