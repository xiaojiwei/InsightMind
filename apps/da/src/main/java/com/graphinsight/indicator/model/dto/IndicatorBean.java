package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.FieldType;
import lombok.Data;

/**
 * Date: 2023/8/3
 * Desc:
 */
@Data
public class IndicatorBean {

    private String code;

    private FieldType type;
}
