package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/10/12
 * Desc:
 */
@Data
public class RatioQuery {

    private Long spaceId;

    private String measCode;

    private String dimCode;

    private int ratioType;
}
