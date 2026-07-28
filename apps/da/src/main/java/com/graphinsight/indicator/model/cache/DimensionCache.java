package com.graphinsight.indicator.model.cache;

import com.graphinsight.indicator.auto.entity.DataSource;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Widget;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Description:
 * @Date: 2021/11/22
 */
@Data
public class DimensionCache {


    private Integer id;

    private String code;

    private String cnName;

    private Integer dimValueCount;

    /**
     * 维度自身的应用表
     */
    private Set<Integer> selfAppIds = new HashSet<>();

    /**
     * 维度基本信息
     */
    private Dimension dimension;

    /**
     * 维度应用信息
     * 包含级联的维度
     */
    private Set<Integer> dimensionAppIds = new HashSet<>();

    /**
     * 维度相关指标
     */
    private Set<Integer> relatedMeasureIds = new HashSet<>();

    /**
     * 维度相关指标
     */
    private List<String> relatedMeasureCnNames;

    /**
     * 维度相关模型
     */
    private Set<Integer> relatedDwTableIds;

    /**
     * 维度的相关数据集
     */
    private Set<DataSource> relatedDataSources = new HashSet<>();

    /**
     * 维度的相关组件
     */
    private Set<Widget> relatedWidgets = new HashSet<>();


}
