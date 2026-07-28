package com.graphinsight.indicator.model.cache;

import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.auto.entity.DwTable;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * @Description:
 * @Date: 2021/12/11
 */
@Data
public class DwTableCache {

    /**
     * 主键
     */
    private Integer id;

    /**
     * 模型的相关维度
     */
    private Set<Integer> relatedDimensionIds;


    /**
     * 模型的相关指标
     */
    private Set<Integer> relatedMeasureIds;

    /**
     * 模型的列
     */
    private List<DwColumn> dwColumnList;

    /**
     * doris所有的列
     */
    private List<DwColumn> dorisColumnList;

    /**
     * 表信息
     */
    private DwTable dwTable;
}
