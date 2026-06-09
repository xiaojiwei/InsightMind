package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.CacheReloadStatus;
import com.graphinsight.indicator.enums.MVType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "cache_reload_task", comment="缓存重新调度表")
public class CacheReloadTask  extends BaseModel {

    /**
     * md5Key 或 唯一key值
     */
    @Column(name = "v_key", columnDefinition = "varchar(255) COMMENT 'md5Key 或 唯一key值'")
    private String key;

    /**
     * 任务状态
     */
    @Column(columnDefinition = "int(11) COMMENT '任务状态'")
    private CacheReloadStatus cacheReloadStatus;

    /**
     * 物化类型
     */
    @Column(columnDefinition = "int(11) COMMENT '物化类型'")
    private MVType mvType;

    /**
     * 查询结果名
     */
    @Column(columnDefinition = "varchar(255) COMMENT '查询结果名'")
    private String mvTableName;

    /**
     * 上次完成修改时间
     */
    @Column(columnDefinition = "datetime COMMENT '上次完成修改时间'")
    private Date beforUpdateDate;

    /**
     * 执行信息
     */
    @Column(name = "v_meassage", length = 6000, columnDefinition = "varchar(800) COMMENT '执行信息'")
    private String meassage;

}
