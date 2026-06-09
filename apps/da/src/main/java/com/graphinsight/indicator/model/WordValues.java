package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * 同义词列表
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
@org.hibernate.annotations.Table(appliesTo = "word_values", comment="同义词库")
public class WordValues extends BaseModel {

    /**
     * 数据源名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '数据源名称'")
    private String key;

    /**
     * 维度名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '维度名称'")
    private String value;

}
