package com.graphinsight.indicator.lax.filter.function.mode;

import com.graphinsight.indicator.model.Filter;
import lombok.Data;

import java.util.List;

@Data
public class CalculateParam {

    /**
     * 目标指标code
     */
    private String measCode;

    /**
     * 层次级别lod表达式，以及相关维度
     */
    private LodDim lodDim;

    /**
     * 筛选过滤器
     */
    private List<Filter> filterList;

    /**
     * 是否含有默认false
     */
    private Boolean hasLodFilter = false;

}
