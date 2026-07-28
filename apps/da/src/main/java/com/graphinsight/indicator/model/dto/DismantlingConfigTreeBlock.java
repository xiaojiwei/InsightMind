package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.DismantlingConfigTreeCalUnitType;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * Date: 2022/11/3
 * Desc:
 */
@Data
public class DismantlingConfigTreeBlock {

    /**
     * 块内节点
     */
    private List<DismantlingConfigTreeNode> nodes = new LinkedList<>();


    /**
     * 计算单元类型
     */
    private DismantlingConfigTreeCalUnitType type;



}
