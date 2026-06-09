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

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "user_data_log", comment="JPA操作记录表")
public class UserDataLog extends BaseModel {

    /**
     * 用户ID
     */
    @Column(name = "user_id", columnDefinition = "varchar(255) COMMENT '用户id'")
    private String userId;

    @Column(name = "log_id", columnDefinition = "varchar(255) COMMENT '日志追踪'")
    private String logId;

    @Column(name = "fields", columnDefinition = "varchar(255) COMMENT '属性列'")
    private String fields;

    @Column(name = "before_values", columnDefinition = "varchar(800) COMMENT '修改之前数据'")
    private String beforeValues;

    @Column(name = "after_values", columnDefinition = "varchar(800) COMMENT '修改之后数据'")
    private String afterValues;

    @Column(name = "simple_name", columnDefinition = "varchar(255) COMMENT '对象名称'")
    private String simpleName;

    @Column(name = "object_key", columnDefinition = "varchar(255) COMMENT '对象key'")
    private String objectKey;

    @Column(name = "operation", columnDefinition = "varchar(255) COMMENT '操作类型'")
    private String operation;

}
