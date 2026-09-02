package com.graphinsight.indicator.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description:
 * @Date: 2021/11/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryQueryVO extends BaseVO{

    /**
     * 是否适用于指标
     */
    private boolean meas;
    /**
     * 是否适用于模型
     */
    private boolean model;

    /**
     * 适用于维度
     */
    private boolean dim;


    /**
     * 空间ID
     */
    private Long spaceId;

    private Boolean currentSpace = true;


}
