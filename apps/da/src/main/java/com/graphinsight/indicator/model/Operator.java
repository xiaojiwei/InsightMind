package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.SqlLogicalType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.enums.TimeRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "operator", comment="操作类型")
public class Operator extends BaseModel {

    /**
     * 时间范围，如果ViewType为时间时处理
     */
    @Column(columnDefinition = "int(11) COMMENT '时间范围，如果ViewType为时间时处理'")
    private TimeRange timeRange = TimeRange.NULL;

    /**
     * 逻辑运算符 默认逻辑与
     */
    @Column(columnDefinition = "int(11) COMMENT '逻辑运算符 默认逻辑与'")
    private SqlLogicalType sqlLogicalType = SqlLogicalType.AND;

    /**
     * 操作类型
     */
    @Column(columnDefinition = "int(11) COMMENT '操作类型'")
    private SqlOprType sqlOprType;

    /**
     * in、not in的数据集
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "operator_data_list", joinColumns = @JoinColumn(columnDefinition = "bigint(10) COMMENT '操作id'", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT)), foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Column(columnDefinition = "varchar(255) COMMENT '值'")
    private List<String> dataList = new ArrayList<String>();

    @Transient
    private List<String> dataValueList = new ArrayList<String>();

    /**
     * 开始范围
     */
    @Column(columnDefinition = "varchar(255) COMMENT '开始范围'")
    private String begin;

    /**
     * 完结范围
     */
    @Column(columnDefinition = "varchar(255) COMMENT '完结范围'")
    private String end;

}
