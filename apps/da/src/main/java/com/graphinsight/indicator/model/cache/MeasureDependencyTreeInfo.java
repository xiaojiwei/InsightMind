package com.graphinsight.indicator.model.cache;

import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/2/17
 * Desc:
 */
@Data
public class MeasureDependencyTreeInfo {

    private Integer measId;

    private String code;

    private List<MeasureApplicationDependency> measureApplicationDependencyList;

    public MeasureDependencyTreeInfo() {
    }

    public MeasureDependencyTreeInfo(Integer measId, List<MeasureApplicationDependency> measureApplicationDependencyList) {
        this.measId = measId;
        this.measureApplicationDependencyList = measureApplicationDependencyList;
        // if(! CollectionUtils.isEmpty(measureApplicationDependencyList)){
        //     allDependencyBaseMeasIds = new HashSet<>();
        //     allDependencyBaseDimIds = new HashSet<>();
        //     measureApplicationDependencyList.forEach(i -> {
        //         if(! CollectionUtils.isEmpty(i.getDependencyBaseDimIds())){
        //             allDependencyBaseDimIds.addAll(i.getDependencyBaseDimIds());
        //         }
        //
        //         if(! CollectionUtils.isEmpty(i.getDependencyBaseMeasIds())){
        //             allDependencyBaseMeasIds.addAll(i.getDependencyBaseMeasIds());
        //         }
        //     });
        // }
    }

    // /**
    //  * 依赖的所有基础指标
    //  */
    // private Set<Integer> allDependencyBaseMeasIds;
    //
    // /**
    //  * 依赖的所有基础维度
    //  */
    // private Set<Integer> allDependencyBaseDimIds;



}
