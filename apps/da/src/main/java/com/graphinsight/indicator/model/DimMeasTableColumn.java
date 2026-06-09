package com.graphinsight.indicator.model;

import lombok.Data;

@Data
public class DimMeasTableColumn extends BaseModel {

    /**
     * 指标关联的自然日期维度Code
     */
    private String dimCode;

    /**
     * schema
     */
    private String schemaName;

    /**
     * 表明
     */
    private String table;


    /**
     * 列名
     * 指指标所在的事实表中具体用哪一个列作为查询基础关联的自然日
     * 相当于事实表最细粒度的日期维度字段
     * 比如事实表最细粒度是日，即使是拖了自然月，那改字段传的还是事实表的日
     */
    private String column;

}
