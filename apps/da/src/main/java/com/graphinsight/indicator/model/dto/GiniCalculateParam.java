package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/7/6
 * Desc: 基尼系数计算参数
 */
@Data
public class GiniCalculateParam {

    /**
     * 维度Code
     */
    private String dimCode;
    /**
     * 维度名称
     */
    private String dimName;

    private List<GiniSubOption> giniSubOptionList;

}
