package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.enums.RatioColumnType;
import com.graphinsight.indicator.enums.RatioType;
import com.graphinsight.indicator.enums.RatioValueType;
import lombok.Data;

import java.util.*;

@Data
public class Measure extends BaseModel {

    /**
     * 指标实例名
     */
    private String name;

    /**
     * 别名
     */
    private String alias;

    /**
     * 指标展示名
     */
    private String caption;

    /**
     * 指标SQL定义或表达式 字符型
     */
    private String expression;

    /**
     * 指标维度在不同事实表上的列信息
     */
    private List<DimMeasTableColumn> dimMeasTableColumnList = new ArrayList<>();

    /**
     * 基础指标、衍生指标、派生指标的定义公式。
     */
    private List<OperationItem> expList = new LinkedList<>();

    /**
     * 比率结果要值还是率,默认为率
     */
    private RatioValueType ratioValueType = RatioValueType.RATIO;

    /**
     * 新增列
     */
    private RatioColumnType ratioColumnType = RatioColumnType.NEW;

    /**
     * 同环比类型
     */
    private RatioType ratioType;

    /**
     * 指标同环比信息
     */
    private List<Ratio> ratioList = new LinkedList<>();

    /**
     * 指标类型
     *     ORIGIN(0, "原生指标"),
     *     DERIVED(1, "衍生指标"),
     *     EXTENDED(2, "派生指标");
     */
    private MeasureType measType;

    /**
     * 指标定义-文字描述
     */
    private String definition;

    /**
     * 指标描述
     */
    private String description;

    /**
     * 是否有权限
     */
    private boolean isPrivileged;

    /**
     * 是否有效
     */
    private boolean isValid;

    /**
     * 是否在线（false 表示已下线）。图谱模式下从 ind:available 读取。
     */
    private boolean online = true;

    /**
     * 格式化
     */
    private ValueFormat valueFormat = new ValueFormat();

    /**
     * 指标所属事实表
     */
    private List<Table> factTable = new ArrayList<>();;

    /**
     * 构建查询时使用的事实表
     */
    private Table useTempFactTable;

    /**
     * 派生指标、衍生指标所依赖的下级指标
     */
    private Set<Measure> hasAllMeasureSet = new LinkedHashSet<>();

    /**
     * 派生指标所依赖的所有维度
     */
    private Set<Dimension> hasAllDimensionSet = new LinkedHashSet<>();

    /**
     * 指标排序
     */
    private Order order;

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Measure measure = (Measure) o;
        boolean codeEq = Objects.equals(this.getCode(), measure.getCode()) && Objects.equals(this.getAlias(), measure.getAlias());
        boolean ratioTypeEq = true;

        if (null == ratioType && null == measure.getRatioType()) {

        } else if (null != ratioType) {
            ratioTypeEq = ratioType.equals(measure.getRatioType());
        } else if (null != measure.getRatioType()) {
            ratioTypeEq = measure.getRatioType().equals(ratioType);
        }

        return codeEq && ratioTypeEq;

    }

    @Override
    public int hashCode() {
        return Objects.hash(this.code, ratioType);
    }
}
