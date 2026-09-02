package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
public class MeasureQueryVO extends BaseVO{
    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    private String enName;

    /**
     * 指标中文名，全局唯一
     */
    private String cnName;

    private Integer categoryId;


    private String keyword;

    /**
     * 指标的业务描述
     */
    private String description;

    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    private Integer pageSize = 20;

    @Min(value = 1,message = "当前页不能小于1")
    private Integer pageNo = 1;

    private List<Integer> deptLevels;

}
