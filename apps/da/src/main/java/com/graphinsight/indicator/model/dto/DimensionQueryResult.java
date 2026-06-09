package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.model.Cell;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2022/11/24
 * Desc:
 */
@Data
public class DimensionQueryResult {

    private String fingerprintsPrefix;

    private List<Cell> cells = new ArrayList<>();

    private Map<String, String> dimensionValueMap = new LinkedHashMap<>();

    private List<String> dimensionKeys = new LinkedList<>();
}
