package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.enums.IndicatorAuthObjectType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Date: 2022/11/28
 * Desc: 授权对象
 */
@Data
public class AiCollectInfoVo extends AiSearchInfo {


    private String showType;

    private Integer searchId;

}
