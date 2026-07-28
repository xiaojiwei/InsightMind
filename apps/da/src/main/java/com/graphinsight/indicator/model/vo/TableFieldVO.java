package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Set;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class TableFieldVO extends BaseVO{

    @ApiModelProperty(value = "字段主键",example = "100")
    private Integer id;

    @ApiModelProperty(value = "字段英文名",required = true)
    private String enName;

    @ApiModelProperty(value = "字段中文名",required = true,example = "订单总量",notes = "指标字段中文名")
    private String cnName;

    @ApiModelProperty(value = "字段属性 1-指标 2-维度")
    private Integer type;

    @ApiModelProperty(value = "字段类型")
    private Set<String> dataType;

    @ApiModelProperty(value = "是否同步过")
    private boolean sync;

    @ApiModelProperty(value = "是否可以切换类型")
    private boolean switchType;

    @ApiModelProperty(value = "中文名是否重复")
    private boolean cnNameRepeat;

    @ApiModelProperty(value = "英文名是否重复")
    private boolean enNameRepeat;

    private Integer leafCategoryId;

    /**
     * 显示类型 0 字符；1 日；2 周；3 月；4 季；5 年；6 小时
     */
    private Integer viewType;




    /**
     * 指标的业务描述
     */
    @ApiModelProperty(value = "业务描述",required = true,example = "所有订单数量总和")
    private String description;


}
