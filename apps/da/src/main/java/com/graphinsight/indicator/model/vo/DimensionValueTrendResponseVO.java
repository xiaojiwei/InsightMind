package com.graphinsight.indicator.model.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class DimensionValueTrendResponseVO {
    private int order;
    private String dimensionValue;
    private BigDecimal baseValue;
    private BigDecimal currentValue;
    private BigDecimal delta;
    private BigDecimal deltaPercent;
}
