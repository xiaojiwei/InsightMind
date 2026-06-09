package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "request", comment="请求明细")
public class Request extends BaseModel {


    @Column(columnDefinition = "text COMMENT '请求内容'")
    private String request;

    /**
     * 请求总数
     */
    @Column(columnDefinition = "varchar(255) COMMENT '请求总数'")
    private String hits;

    @Column(columnDefinition = "text COMMENT '完整请求'")
    private String completeRequest;

    @Column(columnDefinition = "varchar(255) COMMENT 'duration'")
    private String duration;

    @Column(columnDefinition = "varchar(255) COMMENT 'durationMean'")
    private String durationMean;

    @Column(columnDefinition = "varchar(255) COMMENT 'cpu'")
    private String cpu;

    @Column(columnDefinition = "varchar(255) COMMENT 'cpuMean'")
    private String cpuMean;

    @Column(columnDefinition = "varchar(255) COMMENT 'statTime'")
    private String statTime;

    @Column(columnDefinition = "varchar(255) COMMENT 'endTime'")
    private String endTime;

    @Column(columnDefinition = "varchar(255) COMMENT '使用的内存'")
    private String allocatedKBytesSum;

    @Column(columnDefinition = "varchar(255) COMMENT '返回大小'")
    private String responseSizeMean;
}
