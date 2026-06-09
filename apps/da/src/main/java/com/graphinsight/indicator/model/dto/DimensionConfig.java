package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DwTable;
import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/8/1
 * Desc:
 */
@Data
public class DimensionConfig {

    /**
     * 维度基本信息
     */
    private Dimension dimension;

    /**
     * 主维度(最细粒度维度)
     */
    private Dimension masterDimension;

    /**
     * 是否需要类型转换
     */
    private Boolean switchType;


    /**
     * 主维度格式化函数
     */
    private String masterDiemnsionParttern;

    /**
     * 维度字段在事实表中的数据类型
     */
    private String dataType;

    /**
     * 维度字段在事实表中的原始字段名
     */
    private String originFactColumnName;

    /**
     * 维度所在事实表表名
     */
    private String factTableName;

    /**
     * 维度字段经过初步函数加工后列名
     */
    private String factColumnName;

    /**
     * 是否是新增维度
     */
    private Boolean createDimension;

    /**
     * 维度要关联的事实表
     */
    private DwTable dwTable;



    private Integer viewType;


}

