package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/7/26
 * Desc:
 */
@Data
public class IndicatorOperateTree {

    private String name;

    private String parentCode;

    private String code;

    private Integer deptType;

    private List<IndicatorOperateTree> children;

}
