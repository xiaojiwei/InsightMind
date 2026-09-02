package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.codehaus.jackson.map.Serializers;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.stereotype.Component;

import javax.persistence.*;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Entity
@Component
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "java_info", comment="java监控表")
public class JavaInfo extends BaseModel {

    /**
     * 线程名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '线程名称'")
    private String threadName;

    @Column(columnDefinition = "varchar(255) COMMENT 'Host'")
    private String host;

    /**
     * 调用栈
     */
    @Column(columnDefinition = "text COMMENT '调用栈'")
    private String threadStack;

    /**
     * 当前执行方法
     */
    @Column(columnDefinition = "varchar(600) COMMENT '当前执行方法'")
    private String execMethod;

    @Column(columnDefinition = "int(11) COMMENT '线程ID'")
    private Long threadId;

    /**
     * 持续时间
     */
    @Column(columnDefinition = "int(11) COMMENT '持续时间'")
    private Integer duration;

    @Column(columnDefinition = "varchar(255) COMMENT '使用内存'")
    private String startAllocatedBytes;

    /**
     * 请求调用集合
     */
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name="java_info_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private List<Request> requestList = new LinkedList<Request>();

}
