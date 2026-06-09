package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.vo.AiFrontFormatVo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Table;
import javax.persistence.*;
import java.util.*;

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "base_configure", comment="基础ITEM模型类")
public class BaseConfigure extends BaseModel {

    /**
     * 维度唯一名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '维度唯一名称'")
    private String name;

    /**
     * 维度/指标 别名
     */
    @Column(columnDefinition = "varchar(255) COMMENT '维度/指标 别名'")
    private String alias;

    /**
     * 指标SQL定义或表达式 字符型
     */
    @Column(columnDefinition = "varchar(5000) COMMENT '指标SQL定义或表达式'")
    private String expression;

    /**
     * 数据显示格式
     */
    @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private ValueFormat valueFormat;

    /**
     * 在集合中的顺序
     */
    @Column(name = "v_index", columnDefinition = "int(11) COMMENT '在集合中的顺序'")
    private Integer index;

    /**
     * 维度、指标分组所在的轴
     */
    @Column(columnDefinition = "int(11) COMMENT '维度、指标分组所在的轴'")
    private AxisType axisType = AxisType.ROW;


    /**
     * 同环比操作，内嵌，还是新增。
     *  默认新增列
     */
    @Column(columnDefinition = "int(11) COMMENT '同环比操作，内嵌，还是新增'")
    private RatioColumnType ratioColumnType =  RatioColumnType.NEW;

    @Column(columnDefinition = "int(11) COMMENT '当ratioColumnType为new时有效，同环比取值还是率'")
    private RatioValueType ratioValueType = RatioValueType.RATIO;

    @Column(columnDefinition = "int(11) COMMENT '同环比设置'")
    private RatioType ratioType = RatioType.DEFAULT;

    /**
     * 透视表时的位置深度
     */
    @Column(columnDefinition = "int(11) COMMENT '透视表时的位置深度'")
    private Integer ordinal;

    /**
     * 同环比信息
     */
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @Fetch(FetchMode.SUBSELECT)
    @JoinColumn(name="base_configura_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private List<Ratio> ratioList = new LinkedList<>();

    /**
     * 排序类型
     * @see Order
     */
    @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Order order;

    public Order getOrder() {
        if (null != this.order) {
            this.order.setCode(this.getCode());
        }
        return this.order;
    }

    //维度特有
    /**
     * 维度类型
     */
    @Column(columnDefinition = "int(11) COMMENT '维度类型'")
    private DimType dimType;

    /**
     * 指标类型
     */
    @Column(columnDefinition = "int(11) COMMENT '指标类型'")
    private MeasureType measureType;

    /**
     * @see ViewType
     */
    @Column(columnDefinition = "int(11) COMMENT 'viewType'")
    private ViewType viewType;

    //指标特有
    /**
     * 列信息（非指标平台数据源有效）
     */
    @Column(name = "v_column", columnDefinition = "varchar(255) COMMENT '列信息（非指标平台数据源有效）'")
    private String column;

    /**
     * 聚合函数（非指标平台数据源有效)
     */
    @Column(columnDefinition = "varchar(255) COMMENT '聚合函数（非指标平台数据源有效)）'")
    private String aggFun;

    /**
     * 是否含有小计(只有在透视表、交叉表时有效)
     */
    @Column(columnDefinition = "int(1) COMMENT '是否含有小计(只有在透视表、交叉表时有效)'")
    private Boolean hasSubtotal = false;

    /**
     * 分组小计别名
     */
    @Column(columnDefinition = "varchar(255) COMMENT '分组小计别名'")
    private String subtotalAlias;

    /**
     * 指标分组下包含的指标
     */
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @Fetch(FetchMode.SUBSELECT)
    @JoinColumn(name="meas_group_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private List<BaseConfigure> measGroupSet = new LinkedList<>();

    /**
     * 识别的名称
     */
    @Transient
    private String wordName;

    @Transient
    private String measureTypeFlag = "normal";


    @Transient
    private String codeAlias = "";

    @Transient
    private String dimCodeAlias = "";

    @Transient
    private AiFrontFormatVo format;

    /**
     * 一般表示从表达式中分析出来的原子指标。
     */
    @Transient
    private Boolean isHide = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseConfigure)) return false;
        if (!super.equals(o)) return false;
        BaseConfigure that = (BaseConfigure) o;
        return Objects.equals(getCode(), that.getCode()) &&
                ratioValueType == that.ratioValueType &&
                ratioType == that.ratioType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getCode(), ratioValueType, ratioType);
    }
}
