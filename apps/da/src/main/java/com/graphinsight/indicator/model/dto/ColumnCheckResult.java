package com.graphinsight.indicator.model.dto;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2023/2/21
 * Desc:
 */
@Data
public class ColumnCheckResult {

    private String cnName;

    private String name;

    private Integer appId;

    private String message;

    private String createUser;
}
