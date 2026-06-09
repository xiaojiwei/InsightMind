package com.graphinsight.indicator.model;

/**
 * 维度配置信息，承载筛选项、别名、顺序....
 */
public class DimensionConfigure extends BaseConfigure {

    private DimensionConfigure() {
    }

    public static DimensionConfigure build(BaseConfigure baseConfigure) {

        DimensionConfigure dimConfig = new DimensionConfigure();
        dimConfig.setCode(baseConfigure.getCode());
        dimConfig.setName(baseConfigure.getName());
        dimConfig.setAlias(baseConfigure.getAlias());
        dimConfig.setValueFormat(baseConfigure.getValueFormat());
        dimConfig.setIndex(baseConfigure.getIndex());
        dimConfig.setOrder(baseConfigure.getOrder());
        dimConfig.setDimType(baseConfigure.getDimType());
        dimConfig.setViewType(baseConfigure.getViewType());
        dimConfig.setColumn(baseConfigure.getColumn());

        return dimConfig;

    }

}
