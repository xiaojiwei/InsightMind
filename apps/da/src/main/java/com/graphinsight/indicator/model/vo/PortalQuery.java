package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/10/11
 * Desc:
 */
@Data
public class PortalQuery {

    private Long spaceId;

    private Boolean isMine = false;

    private String keyword;
}
