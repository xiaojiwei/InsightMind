package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.model.BaseConfigure;
import com.graphinsight.indicator.model.Cell;
import com.graphinsight.indicator.model.DimensionConfigure;
import com.graphinsight.indicator.model.MeasureConfigure;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.PageInfo;
import com.graphinsight.indicator.model.QueryPlan;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Date: 2022/7/25
 * Desc:
 */
@Data
public class PageDataVO extends PageData {
    private Long total;

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

}
