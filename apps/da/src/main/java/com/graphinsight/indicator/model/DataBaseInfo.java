package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.SourceType;
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
 * 当数据源为非指标平台类型时，需要提供数据源信息，此类进行描述。
 */
@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "data_base_info", comment="当数据源为非指标平台类型时，需要提供数据源信息，此类进行描述")
public class DataBaseInfo extends BaseModel {

    /**
     * @see SourceType
     *     MYSQL(0, "MySQL"),
     *     DORIS(1, "Doris");
     * 数据源类型
     */
    @Column(columnDefinition = "int(11) COMMENT '数据源类型'")
    private SourceType sourceType;

    /**
     * 数据库别名
     */
    @Column(columnDefinition = "varchar(255) COMMENT '数据库别名'")
    private String dataName;

    /**
     * 真实db名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '真实db名称'")
    private String realDbName;

    /**
     * 数据库表
     */
    @Column(columnDefinition = "varchar(255) COMMENT '数据库表'")
    private String dataTable;

    /**
     * 数据sql
     */
    @Column(columnDefinition = "varchar(255) COMMENT '数据sql'")
    private String dataSql;

}
