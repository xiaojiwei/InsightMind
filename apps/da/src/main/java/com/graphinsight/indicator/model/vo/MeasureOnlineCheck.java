package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.Map;

/**
 * Date: 2023/8/7
 * Desc:
 */
@Data
public class MeasureOnlineCheck {

    private Boolean onlineable;

    private Map<Integer, ComplexMeasureBaseVO> listMap;
}
