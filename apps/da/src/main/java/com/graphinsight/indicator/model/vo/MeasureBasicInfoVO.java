package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Date: 2022/3/2
 * Desc:
 */
@Data
public class MeasureBasicInfoVO {
    /**
     * 指标Id
     */
    private Integer id;

    /**
     * 指标Code
     */
    private String code;

    /**
     * 指标名称
     */
    private String cnName;

    public MeasureBasicInfoVO(Integer id, String code, String cnName) {
        this.id = id;
        this.code = code;
        this.cnName = cnName;
    }

    public MeasureBasicInfoVO() {
    }
}
