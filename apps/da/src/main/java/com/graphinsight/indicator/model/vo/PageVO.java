package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageVO<T> extends BaseVO {
    @ApiModelProperty(value = "总条数",required = true,example = "100")
    private Long total;
    @ApiModelProperty(value = "分页数据",required = true)
    private List<T> data;
}
