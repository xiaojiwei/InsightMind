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
import javax.persistence.Index;
import java.util.Date;

/**
 * 查询信息
 * 记录查询cache的md5key，根据此key可以定位缓存DataSource内容；
 *
 * 记录查询的最后发生时间；
 * 记录查询的最后发生耗时；
 * 统计查询的累计频次；
 * 统计查询的累计总耗时；
 *
 * 记录从缓存取数据最后发生时间；
 * 记录从缓存取数据最后耗时；
 * 统计缓存返回结果的总频次；
 * 统计缓存返回结果的累计耗时；
 *
 * 记录更新查询结果数据的最后发生时间；
 * 记录更新查询结果数据的最后耗时；
 * 统计更新结果的累计频次；
 * 统计更新结果的总耗时；
 *
 */
@Entity
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "query_plan", comment="查询方案")
public class QueryPlan extends BaseModel {

    /**
     * 记录查询cache的md5key，根据此key可以定位缓存DataSource内容；
     */
    @Column(name = "v_key", unique = true, columnDefinition = "varchar(255) COMMENT '记录查询cache的md5key，根据此key可以定位缓存DataSource内容'")
    private String key;

    /**
     * 记录查询的最后发生时间；
     */
    @Column(columnDefinition = "datetime COMMENT '记录查询的最后发生时间'")
    private Date lastQueryTime;

    /**
     * 记录查询的最后发生耗时；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '记录查询的最后发生耗时'")
    private Long lastQueryCost = Long.valueOf(0);

    /**
     * 统计查询的累计频次；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '统计查询的累计频次'")
    private Long queryCnt = Long.valueOf(0);

    /**
     * 统计查询的累计总耗时；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '统计查询的累计总耗时'")
    private Long querySumCost = Long.valueOf(0);

    /**
     * 统计缓存返回结果的总频次；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '统计缓存返回结果的总频次'")
    private Long queryCacheCnt = Long.valueOf(0);

    /**
     * 统计缓存返回结果的累计耗时；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '统计缓存返回结果的累计耗时'")
    private Long queryCacheSumCost = Long.valueOf(0);

    /**
     * 记录从缓存取数据最后发生时间；
     */
    @Column(columnDefinition = "datetime COMMENT '记录从缓存取数据最后发生时间'")
    private Date lastQueryCacheTime;

    /**
     * 记录从缓存取数据最后耗时；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '记录从缓存取数据最后耗时'")
    private Long lastQueryCacheCost = Long.valueOf(0);

    /**
     * 记录更新查询结果数据的最后发生时间；
     */
    @Column(columnDefinition = "datetime COMMENT '记录更新查询结果数据的最后发生时间'")
    private Date lastUpdateQueryTime;

    /**
     * 记录更新查询结果数据的最后耗时；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '记录更新查询结果数据的最后耗时'")
    private Long lastUpdateCacheCost = Long.valueOf(0);

    /**
     * 统计更新结果的累计频次；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '统计更新结果的累计频次'")
    private Long updateCacheCnt = Long.valueOf(0);

    /**
     * 统计更新结果的总耗时；
     */
    @Column(columnDefinition = "bigint(20) COMMENT '统计更新结果的总耗时'")
    private Long updaeCacheSumCost = Long.valueOf(0);

    /**
     * 总耗时
     */
    @Column(columnDefinition = "bigint(20) COMMENT '总耗时'")
    private Long sumCost = Long.valueOf(0);

}
