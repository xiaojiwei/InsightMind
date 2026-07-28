package com.graphinsight.indicator.model.cache;

import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Hierarchy;
import com.graphinsight.indicator.auto.entity.Level;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.entity.MeasureNaturalDateMapping;
import com.graphinsight.indicator.model.Filter;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description:
 * @Date: 2021/11/23
 */
@Data
public class MetadataCache {

    private Map<Integer, Measure> allMeasureMap;

    private Map<Integer, Dimension> allDimensionMap;

    private Map<String, Measure> allMeasureCodeMap;

    private Map<String, Dimension> allDimensionCodeMap;

    private Map<Integer,DwTable> dwTableMap;

    private Map<Integer,MeasureApplication> measureApplicationMap;

    private Map<Integer, Department> departmentMap;

    /**
     * key: DimensionApplication.id
     */
    private Map<Integer,DimensionApplication> dimensionApplicationMap;

    private Map<Integer, Category> categoryMap;

    /**
     * 维度维表关联信息
     * key:dimId
     */
    private Map<Integer, DimensionDimtableConnect> dimIdDimtableConnectMap;


    private Map<Integer, Level> dimIdLevelMap;

    private Map<Integer, List<Level>> hierarchyIdLevelMap;

    private Map<Integer, Hierarchy> hierarchyMap;

    /**
     * 指标依赖树
     */
    List<MeasureDependencyTreeInfo> complexMeasureDependencyTrees;

    /**
     * 指标依赖树
     */
    Map<Integer,MeasureDependencyTreeInfo> measIdComplexMeasureDependencyTreeMap;

    Map<Integer, List<MeasureApplication>> measIdAppList;

    /**
     * 派生指标的维度过滤器
     */
    private Map<Integer, List<Filter>> measAppIdFiltersMap = new HashMap<>();

    /**
     * 空间上下文信息
     */
    private Map<Long,SpaceContext> spaceContextMap = new HashMap<>();

    /**
     * 自然类型日期维度配置信息
     */
    private Map<Long, List<MeasureNaturalDateMapping>> naturalDimIdNaturalDateMappingMap = new HashMap<>();

}
