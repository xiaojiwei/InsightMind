package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.auto.entity.DimensionHistogram;
import com.graphinsight.indicator.auto.entity.TableHistogram;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2023/1/4
 * Desc:
 */
@Data
public class HistogramCache {

    private List<TableHistogram> tableHistograms = new ArrayList<>();

    private List<DimensionHistogram> dimensionHistograms =  new ArrayList<>();
}
