package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.DismantlingConfigTreeCalUnitType;
import com.graphinsight.indicator.enums.OperatorType;
import lombok.Data;

/**
 * Date: 2022/11/3
 * Desc:
 */
@Data
public class DismantlingConfigTreeNode {

    /**
     * 查询依赖的指标code
     */
    private String queryMeasCode;

    /**
     * 是否是比率型指标 如果是，需要去查询分子分母指标
     */
    private Boolean isRatio = false;

    /**
     * 分子指标Code
     */
    private String molecularCode;

    /**
     * 分母指标Code
     */
    private String denominatorCode;

    // /**
    //  * 当前节点下钻的维度集合
    //  */
    // private List<String> drillDownDimCodes = new LinkedList<>();

    /**
     * 运算符
     * 当nodeType为运算符时，必填
     */
    private OperatorType operatorType;

    /**
     * 当前节点指纹
     */
    private String fingerprint = "";


    /**
     * 节点类型
     */
    private DismantlingConfigTreeCalUnitType type;




}
