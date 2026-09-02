package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.SortBy;
import com.graphinsight.indicator.enums.SortType;
import lombok.Data;

/**
 * Date: 2022/8/3
 * Desc:
 */
@Data
public class OrderVO {

    private SortBy sortBy;

    private String code;

    private Integer sortType = SortType.DESC.getCode();

}
