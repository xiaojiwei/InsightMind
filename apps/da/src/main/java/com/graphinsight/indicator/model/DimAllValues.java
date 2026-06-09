package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.ViewType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * 维度以及维度值的列表
 */
@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "dim_all_values", comment="值列表")
public class DimAllValues extends BaseModel {

    /**
     * 数据源名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '数据源名称'")
    private String dimCode;

    /**
     * 维度名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '维度名称'")
    private String dimName;

    /**
     * 维度key
     */
    @Column(columnDefinition = "varchar(255) COMMENT '维度key'")
    private String valueKey;

    /**
     * 唯一访问标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT '唯一访问标识'")
    private String valueText;

    /**
     * 词性
     */
    @Column(columnDefinition = "varchar(10) COMMENT '词性'")
    private String nature;

    /**
     * 维度使用等级
     */
    @Transient
    private Integer useLevel;

    /**
     * viewType
     */
    @Transient
    private ViewType viewType;

    /**
     * viewType
     */
    @Transient
    private boolean fromValue;

    @Transient
    private String dimOrg;

    @Transient
    private String dimFilterValue;

    @Transient
    private String sTime;
    @Transient
    private String eTime;

}
