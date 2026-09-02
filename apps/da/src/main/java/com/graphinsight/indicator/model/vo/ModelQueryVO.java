package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
public class ModelQueryVO extends BaseVO{
    /**
     * cnName
     */
    private String keyword;

    private Integer categoryId;

    private Integer measId;

    private Integer dimId;

}
