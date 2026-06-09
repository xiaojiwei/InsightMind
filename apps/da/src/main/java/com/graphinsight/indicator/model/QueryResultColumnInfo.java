package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.CellType;
import com.graphinsight.indicator.enums.DimType;
import lombok.Data;

@Data
public class QueryResultColumnInfo {
    /**
     * 内容类型 维度 0、指标 1
     */
    private CellType type;

    /**
     * 维度或指标的code
     */
    private String code;

    /**
     * 维度或指标的名称
     */
    private String name;

    /**
     * 维度类别 0-退化维,1-标准维无维表,2-标准维有维表
     * @see DimType
     */
    private DimType dimType;

    /**
     * 数据
     */
    private String data;

}
