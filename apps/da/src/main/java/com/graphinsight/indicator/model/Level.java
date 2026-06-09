package com.graphinsight.indicator.model;

import lombok.Data;

/**
 * 层次
 */
@Data
public class Level extends BaseModel {

    /**
     * 级别所在都位置
     */
    private Integer sequence;

    /**
     * 层次
     */
    private String hierarchyCode;

}
