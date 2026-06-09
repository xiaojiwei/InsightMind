package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.DismantlingConfigTreeCalUnitType;
import com.graphinsight.indicator.enums.DismantlingConfigTreeRegionType;
import com.graphinsight.indicator.enums.OperatorType;
import lombok.Data;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/11/3
 * Desc:
 */
@Data
public class DismantlingConfigTreeRegion {

    /**
     * region中的计算节点
     */
    private List<DismantlingConfigTreeNode> nodes = new LinkedList<>();

    /**
     * 匹配上层节点的指纹
     */
    private Set<String> parentFingerprints = new HashSet<>();


    /**
     * 维度下钻时要显示的维值
     */
    private Set<String> displayDimensionValues = new HashSet<>();

    /**
     * region配置的方式
     */
    private DismantlingConfigTreeRegionType regionType;

    /**
     * 如果是维度拆解，根据region生成的节点之间需要存在运算类型
     */
    private OperatorType operatorType;
    //
    // /**
    //  * 当前region将要下钻的维度code
    //  */
    // private String regionDrillDownDimensionCode;

    /**
     * 当前层的下钻维度集合
     */
    private List<String> drillDownDimensionCodes = new LinkedList<>();

    public String generateUUID(){
        String s = "";
        for (DismantlingConfigTreeNode node : nodes) {
            if (Objects.equals(node.getType(), DismantlingConfigTreeCalUnitType.OPERATOR)){
                s += node.getOperatorType().getCode();
            } else {
                s += node.getQueryMeasCode() + node.getType();
            }
        }
        s += this.displayDimensionValues.stream().collect(Collectors.joining(""));
        return s;
    }



}
