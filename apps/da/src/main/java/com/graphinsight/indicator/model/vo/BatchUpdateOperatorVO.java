package com.graphinsight.indicator.model.vo;


import lombok.Data;

/**
 * Date: 2022/3/28
 * Desc:
 */
@Data
public class BatchUpdateOperatorVO {

    private UpdateItem modelUpdateItem;

    private UpdateItem measureUpdateItem;

    private UpdateItem dimensionUpdateItem;
}
