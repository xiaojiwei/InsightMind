package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.*;
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
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "filter", comment="筛选项")
public class Filter extends BaseModel {

    /**
     * 所属维度的层次Id
     */
    @Transient
    private Long hierarchyId;

    /**
     * 维度授权类型、精确值匹配、上下文匹配。
     */
    @Transient
    private AuthFilterParamType authFilterParamType;

    /**
     * 处理维度时的类型
     */
    @Transient
    private DimType dimType;

    /**
     * 是否是明细查询
     */
    @Transient
    private Boolean isDetail = false;

    /**
     * 事实表别名，后端赋予
     */
    @Column(columnDefinition = "varchar(255) COMMENT '事实表别名，后端赋予'")
    private String alias;

    /*
    * 过滤器名称
     */
    @Column(name = "name",columnDefinition = "varchar(255) COMMENT '过滤器名称'")
    private String name;


    /**
     * 逻辑运算符 默认逻辑与
     */
    @Column(columnDefinition = "int(11) COMMENT '逻辑运算符 默认逻辑与'")
    private SqlLogicalType sqlLogicalType = SqlLogicalType.AND;

    /**
     * 事实表列ID，后端赋予
     */
    @Transient
    private String columnId;

    public String getColumnId() {
        if (null == columnId) {
            return column;
        }
        return columnId;
    }

    /**
     * 事实表列名，后端赋予
     */
    @Column(name = "v_column", columnDefinition = "varchar(255) COMMENT '事实表列名，后端赋予'")
    private String column;

    public void setColumn(String column) {
        this.column = column;
        if (this.orgColumn == null) {
            //保存原始column
            this.orgColumn = column;
        }
    }

    /**
     * 表列原始column
     */
    @Transient
    private String orgColumn;

    /**
     * 关联维度表别名，后端赋予
     */
    @Column(columnDefinition = "varchar(255) COMMENT '关联维度表别名，后端赋予'")
    private String dimAlias;

    /**
     * 关联维度表列名称，后端赋予用于like操作
     */
    @Column(columnDefinition = "varchar(255) COMMENT '关联维度表列名称，后端赋予用于like操作'")
    private String dimColumnName;

    /**
     * 维度类型
     */
    @Column(columnDefinition = "int(11) COMMENT '维度类型'")
    private ViewType viewType = ViewType.CHARACTER;


    /**
     * hidden visible只用于数据源存储时供前端定位使用，可以同时为true，但不可以同时为false。
     * 既相同维度可以既有默认值，也可以拥有显示值。
     * 是否默认项,作用于默认筛选。
     */
    @Column(columnDefinition = "int(1) COMMENT '是否默认项,作用于默认筛选'")
    private boolean internal;

    /**
     * 是否显示于单图
     */
    @Column(columnDefinition = "int(1) COMMENT '是否显示于单图'")
    private boolean visible;

    /**
     * 是否应用过
     */
    @Transient
    private boolean use = false;

    @Transient
    private String wordName;

    /**
     * 操作数据集合
     */
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name="filter_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Fetch(FetchMode.SUBSELECT)
    private List<Operator> operatorList = new LinkedList<>();

    /**
     * 如果存在则代表此筛选项只应用与指标集合相关，相当于派生指标，并不会合并到全局搜索条件中。
     */
    @Transient
    private Set<AuthElementMeasure> authElementMeasureSet = new HashSet<>();


    public String getFilterKey() {

        StringBuilder filterKey = new StringBuilder();
        //code
        filterKey.append(":code").append(this.code);
        filterKey.append(":visible").append(this.visible);

        for (Operator operator : this.operatorList) {

            SqlOprType sqlOprType = operator.getSqlOprType();
            filterKey.append(":sqlOprType").append(sqlOprType);
            filterKey.append(":data");

            List<String> dataList = operator.getDataList();

            for (String data : dataList) {
                filterKey.append("-").append(data);
            }

        }

        return filterKey.toString();

    }

}
