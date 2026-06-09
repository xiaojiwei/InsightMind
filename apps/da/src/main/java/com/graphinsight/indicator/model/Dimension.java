package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.ViewType;
import lombok.Data;

import java.util.*;

@Data
public class Dimension extends BaseModel {

    /**
     * 维度实例名
     */
    private String name;

    /**
     * 维度别名
     */
    private String alias;

    /**
     * 维度展示名
     */
    private String caption;

    /**
     * @see DimType
     */
    private DimType dimType;

    /**
     * 维度定义
     */
    private String definition;

    /**
     * 维度描述
     */
    private String description;

    /**
     * 标准维-有维表-该维度的维度级 true 主维度； false 次维度。
     */
    private boolean isMaster;

    /**
     * 维值列表
     */
    private List<Map<String, String>> dimValues = new LinkedList<>();

    /**
     * 是否有权限
     */
    private boolean isPrivileged;

    /**
     * 是否有效
     */
    private boolean isValid;

    /**
     * 维度否需要在root层join
     */
    private boolean isRootJoin;

    /**
     * 所属层次
     */
    private Level level;

    /**
     * 维度所属事实表
     */
    private List<Table> factTableList = new LinkedList<>();;

    /**
     * 维度所属的维度表
     */
    private List<Table> dimTableList = new LinkedList<>();

    /**
     * 维度类型
     * @see ViewType
     *     CHARACTER(0, "字符"),
     *     DAY(1, "日"),
     *     WEEK(2, "周"),
     *     MONTH(3, "月"),
     *     SEASON(4, "季节"),
     *     YEAR(5, "年"),
     *     HOUR(6, "小时");
     */
    private ViewType viewType;

    /**
     * 是否是“全部”选项
     */
    private boolean all;

    /**
     * 退化维
     */
    private boolean degDim;

    /**
     * 派生维度必要指标
     */
    private boolean extended;

    /**
     * column和columnName相同
     */
    private boolean selfDim;

    public String getHierCode() {

        String hierCode = "0";

        if (null != this.level) {
            hierCode = this.level.getHierarchyCode();
        }

        return hierCode;

    }

    /**
     * 自定义分组
     */
    private List<GroupColumn> groupColumnList = new LinkedList<>();

    /**
     * 衍生维度所依赖的其它维度
     */
    private Set<Dimension> hasAllDimensionSet = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Dimension dimension = (Dimension) o;

        return (super.equalsStr(this.alias, dimension.alias)
                && equalsStr(this.getCode(), dimension.getCode()));

    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), alias, getCode());
    }
}
