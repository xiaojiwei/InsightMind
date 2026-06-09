package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.MemberType;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 成员
 */
@Data
public class CubeMeasure extends BaseModel {

    /**
     * 深度
     */
    int depth;

    /**
     * 成员类型
     * @see MemberType
     */
    private MemberType memberType;

    /**
     * 当memberType为DIMENSION时存在.
     */
    private Dimension dimension;

    /**
     * 当memberType为MEASURE时存在.
     */
    private Measure measure;

    /**
     * 当memberType为MEASURE_GROUP时存在.
     */
    private BaseConfigure measureGroup;

    /**
     * 值
     */
    private String value;

    /**
     * 格式化后的显示
     */
    private String formattedValue;

    /**
     * 上级成员
     */
    private CubeMeasure parentMember;

    /**
     * 是否为all
     */
    private boolean all;

    /**
     * 指标计算列
     */
    private boolean calculated;


    /**
     * 下级成员
     */
    private List<CubeMeasure> childMemberList = new LinkedList<>();

}
