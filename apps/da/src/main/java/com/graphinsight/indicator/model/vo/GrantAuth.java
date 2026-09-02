package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.IndicatorAuthType;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/11/28
 * Desc: 授权对象
 */
@Data
public class GrantAuth {

    /**
     * 资源对象
     */
    private IndicatorAuthElement authElement;

    /**
     * 授权对象
     */
    @NotNull
    private AuthObject authObject;

    /**
     * 权限是否是继承自父节点
     */
    private Boolean inherit = false;

    /**
     * 权限类型
     */
    private List<IndicatorAuthType> authTypes;



}
