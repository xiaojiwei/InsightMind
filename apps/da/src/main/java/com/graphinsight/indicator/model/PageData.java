package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.DimType;
import lombok.Data;

import javax.persistence.Transient;
import java.util.*;

/**
 * 前端页面查询结果数据
 */
@Data
public class PageData {

    /**
     * 登录用户
     */
    private String loginUserName;

    /**
     * 前端所有的配置信息
     */
    Set<BaseConfigure> allConfigureSet;

    /**
     * 前端传入的所有维度集合
     */
    Set<DimensionConfigure> dimConfigSet;

    /**
     * 前端传入的所有指标集合
     */
    Set<MeasureConfigure> measureConfigureSet;

    /**
     * 数据集合
     */
    List<List<Cell>> cellList = new LinkedList<>();

    /**
     * 原始数据
     */
    List<Map<String, Object>> rowList;

    /**
     * 分页数据
     */
    PageInfo pageInfo;

    /**
     * 最终执行的逻辑sql
     */
    String reviewSql;

    /**
     * 最终执行的逻辑sql
     */
    String formatViewSql;

    Boolean recordSuccess = true;

    public String getReviewSql() {
        return reviewSql;
    }

    public void setReviewSql(String reviewSql) {
        this.reviewSql = reviewSql;
//        this.formatViewSql = FormatStyle.BASIC.getFormatter().format(reviewSql);
    }

    /**
     * 分页信息缓存key
     */
    String cacheKey;

    /**
     * 此次查询结果是否是缓存数据
     */
    boolean useCache;

    /**
     * 下载唯一标识
     */
    String downloadId;

    /**
     * 查询统计信息
     */
    QueryPlan queryPlan;

    /**
     * 维度类型
     */
    DimType dimType;

    String messageInfo;

    DataSource dataSource;

    Long cost;

    /**
     * 明细下钻受控权限
     */
    List<Filter> authDetailFilters;

    /**
     * 授权信息
     */
    String authDetailMessage;

    /**
     * 指标和维度的基本信息，给前端AIChat功能回显用
     */
    Map<String, Object> baseInfoMap;

    /**
     * spaceId，给前端AIChat功能回显用
     */
    List<Long> spaceId;

    /**
     * fromDeveloper，用于aiChat问题定位
     */
    String fromDeveloper;

    @Transient
    private Boolean dataRange = true;

    @Transient
    private Boolean dataAllRange = true;

    @Transient
    private Object rangeInfo;
    @Transient
    private String boardUrl = "";
    @Transient
    private String chatText = "";

    String explainTemplate = "";

}
