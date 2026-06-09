package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 数据矩阵轴
 */
@Data
public class Axis extends BaseModel {

    /**
     * Code
     */
//    protected String code;

    /**
     * 轴成员
     */
    List<CubeMember> memberList = new LinkedList<>();

    /**
     * 顺序
     */
    Integer ordinal;

    public static Axis buildNull() {
        Axis axis = new Axis();
        axis.setOrdinal(0);
        return axis;
    }

    /**
     * 根据row、column轴进行定位CubeMember.
     * @param row
     * @param column
     * @return
     */
    public CubeMember get(Integer row, Integer column) {
        CubeMember cubeMember = null;

        return cubeMember;
    }

    public void initMemberMatrix() {

        Integer height = this.getMaxDeep();
        Integer width = this.getAllDeepesMember();


    }

    /**
     * 深
     * @return
     */
    public Integer getMaxDeep() {

        Integer maxDeep = 0;

        for (CubeMember cubeMember : this.memberList) {
            maxDeep = cubeMember.getDepth();
            break;
        }

        return maxDeep;

    }

    /**
     * 获取轴下所有最细粒度的数量
     * @return
     */
    public Integer getAllDeepesMember() {
        Integer sum = 0;
        sum = addDeep(this.memberList, sum, 1);
        return sum;
    }

    /**
     * 统计指定层集的总个数
     * @param memberList
     * @param sum
     * @param taget
     */
    public static Integer addDeep(List<CubeMember> memberList, Integer sum, Integer taget) {

        for (CubeMember cubeMember : memberList) {
            Integer depth = cubeMember.getDepth();
            if (taget.equals(depth)) {
                sum++;
            }
            sum = addDeep(cubeMember.getChildMemberList(), sum, taget);
        }

        return sum;

    }
}
