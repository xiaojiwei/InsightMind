package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.Set;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class TableFieldVO extends BaseVO{

    private Integer id;

    private String enName;

    private String cnName;

    private Integer type;

    private Set<String> dataType;

    private boolean sync;

    private boolean switchType;

    private boolean cnNameRepeat;

    private boolean enNameRepeat;

    private Integer leafCategoryId;

    /**
     * 显示类型 0 字符；1 日；2 周；3 月；4 季；5 年；6 小时
     */
    private Integer viewType;




    /**
     * 指标的业务描述
     */
    private String description;


}
