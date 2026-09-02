package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import com.graphinsight.indicator.enums.SqlAggFunType;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class OriginMeasureCreateVO extends BaseVO{

    private Integer id;

    @NotNull(message = "指标英文名不能为空")
    private String enName;

    @NotNull(message = "指标中文名不能为空")
    private String cnName;

    @NotNull(message = "字段名不能为空")
    private String columnEnName;


    @NotNull(message = "模型ID不能为空")
    private Integer modelId;

    private Integer measAppId;

    private String whereCondition;

    @NotNull(message = "聚合函数不能为空")
    private SqlAggFunType sqlAggFunType;

    private String description;

    @LeafCategoryId
    Integer leafCategoryId;

    private List<NaturalDimConfigVO> naturalDimConfig;
}
