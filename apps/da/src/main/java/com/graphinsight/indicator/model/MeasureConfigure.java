package com.graphinsight.indicator.model;


public class MeasureConfigure extends BaseConfigure {

    private MeasureConfigure() {
    }

    public static MeasureConfigure build(BaseConfigure baseConfigure) {

        MeasureConfigure measConfig = new MeasureConfigure();
        measConfig.setCode(baseConfigure.getCode());
        measConfig.setName(baseConfigure.getName());
        measConfig.setAlias(baseConfigure.getAlias());
        measConfig.setValueFormat(baseConfigure.getValueFormat());
        measConfig.setIndex(baseConfigure.getIndex());
        measConfig.setOrder(baseConfigure.getOrder());

        measConfig.setMeasureType(baseConfigure.getMeasureType());
        measConfig.setColumn(baseConfigure.getColumn());
        measConfig.setAggFun(baseConfigure.getAggFun());
        measConfig.setRatioList(baseConfigure.getRatioList());
        measConfig.setExpression(baseConfigure.getExpression());
        measConfig.setRatioColumnType(baseConfigure.getRatioColumnType());
        measConfig.setRatioType(baseConfigure.getRatioType());

        measConfig.setIsHide(baseConfigure.getIsHide());
        return measConfig;

    }

}
