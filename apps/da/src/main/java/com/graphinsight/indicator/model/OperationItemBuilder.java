package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.SqlAggFunType;

/**
 * @Description:
 * @Date: 2021/11/25
 */
public class OperationItemBuilder {

    public static OperationItem builder(){
        // TODO 一期暂时写死
        OperationItem operationItem = new OperationItem();
        operationItem.setOperator("sum");
        operationItem.setOperatingType("operator");
        return operationItem;
    }

    public static OperationItem originMeasureBuilde(SqlAggFunType sqlAggFunType){
        // TODO 一期暂时写死
        OperationItem operationItem = new OperationItem();
        operationItem.setOperator(sqlAggFunType.getDesc());
        operationItem.setOperatingType("operator");
        return operationItem;
    }
}
