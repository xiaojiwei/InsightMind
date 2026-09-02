package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/11/28
 * Desc: 授权对象
 */
@Data
public class Grant extends BaseVO{

    @NotEmpty(message = "授权参数不能为空")
    List<GrantAuth> grantAuths;

    /**
     * 空间ID
     */
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

}
