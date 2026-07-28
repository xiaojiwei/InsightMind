package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Date: 2023/10/30
 * Desc:
 */
@Data
public class PageQueryVO extends BaseVO {

    private Integer pageSize;

    private Integer pageNo;
}
