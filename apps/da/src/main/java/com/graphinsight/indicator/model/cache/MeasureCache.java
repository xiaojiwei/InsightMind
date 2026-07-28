package com.graphinsight.indicator.model.cache;

import com.graphinsight.indicator.auto.entity.DataSource;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.Widget;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Description: 指标的缓存信息
 * @Date: 2021/11/22
 */
@Data
public class MeasureCache implements Serializable {

    private Integer id;

    private String code;

    private String cnName;

    /**
     * 指标基本信息
     */
    private Measure measure;

    /**
     * 指标应用信息
     */
    private Set<Integer> measureAppIds = new HashSet<>();

    /**
     * 指标相关维度
     */
    private Set<Integer> relatedDimensionIds = new HashSet<>();


    /**
     * 指标相关维度
     */
    private List<String> relatedDimensionCnNames = new ArrayList<>();

    /**
     * 指标相关模型
     */
    private Set<Integer> relatedDwTableIds = new HashSet<>();

    /**
     * 指标的应用信息
     */
    private List<MeasureApplicationCache> measureApplicationCacheList = new ArrayList<>();


    /**
     * 指标对应的明细表列表
     */
    private List<Integer> detailDwTableIds = new ArrayList<>();

    /**
     * 指标的相关数据集
     */
    private Set<DataSource> relatedDataSources = new HashSet<>();

    /**
     * 指标的相关组件
     */
    private Set<Widget> relatedWidgets = new HashSet<>();


}
