package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.MemberType;
import com.graphinsight.indicator.enums.RatioColumnType;
import com.graphinsight.indicator.enums.RatioType;
import com.graphinsight.indicator.enums.RatioValueType;
import lombok.Data;
import org.springframework.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * 成员
 */
@Data
public class CubeMember extends BaseModel {

    /**
     * 深度
     */
    int depth = 1;

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
    private CubeMember parentMember;

    /**
     * 是否为all
     */
    private boolean all;

    /**
     * 指标计算列
     */
    private boolean calculated;

    /**
     * 比率结果要值还是率,默认为率
     */
    private RatioValueType ratioValueType = RatioValueType.RATIO;

    /**
     * 同环比类型
     */
    private RatioType ratioType;

    /**
     * 下级成员
     */
    private List<CubeMember> childMemberList = new LinkedList<>();

    public void setParentMember(CubeMember parentMember) {
        //设置子对象时，父对象深度加1
        parentMember.addDepth(this.depth);
        this.parentMember = parentMember;
    }

    public void addDepth(Integer childDepth) {

        //如果当前层集小于child层集,则为父对象加1。
        if (this.depth <= childDepth) {
            this.depth = childDepth + 1;
            //如果还存在父级，继续累进。
            if (null != this.parentMember) {
                this.parentMember.addDepth(this.depth);
            }
        }

    }

    public Integer getChildSum() {
        Integer sum = 0;
        if (!CollectionUtils.isEmpty(this.childMemberList)) {
            sum = Axis.addDeep(this.childMemberList, sum, 1);
        } else if (this.depth == 1) {
            sum = 1;
        }
        return sum;
    }

    @Override
    public String toString() {
        return "CubeMember{" +
                "depth=" + depth +
                ", value='" + value + '\'' +
                ", all=" + all +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CubeMember that = (CubeMember) o;
        return  Objects.equals(this.getCode(), that.getCode()) &&
                ratioValueType == that.ratioValueType &&
                ratioType == that.ratioType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value, ratioValueType, ratioType);
    }
}
