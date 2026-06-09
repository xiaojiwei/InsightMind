package com.graphinsight.indicator.model;


import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.LineStatus;
import lombok.Data;

/**
 * 查询对象
 */
@Data
public class AuthSearchText extends SearchText {

    /**
     * 授权元素对象、指标或维度
     */
    private AuthElementType authElementType = AuthElementType.MEASURE;



}
