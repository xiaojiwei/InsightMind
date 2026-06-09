package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.FormatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 数据格式
 */

@Data
@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "value_format", comment="文件夹")
public class ValueFormat extends BaseModel {

    /**
     * 显示格式
     */
    @Column(columnDefinition = "int(11) COMMENT '显示格式'")
    private FormatType formatType = FormatType.THOUSANDTH;

    /**
     * 小数位数
     */
    @Column(columnDefinition = "int(11) COMMENT '小数位数'")
    private Integer value = Integer.valueOf(0);

}
