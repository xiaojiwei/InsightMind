package com.graphinsight.indicator.model.dto;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/5/27
 * Desc:
 */

@Data
public class BaseInfoDTO {

    private String code;

    private String enName;

    private String cnName;

    /**
     * 显示类型 0 字符；1 日；2 周；3 月；4 季；5 年；6 小时
     */
    private Integer viewType;

}
