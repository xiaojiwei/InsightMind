package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/8/9
 * Desc: 维度基础信息
 */
@Data
public class DimensionMatrix {

    private Integer id;

    private String code;

    private String cnName;

    private List<FactTable> factTableList;

    private FactTable dimTable;


}
